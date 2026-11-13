begin;

create schema danplus_d12;
revoke all on schema danplus_d12 from public, anon, authenticated;

create table danplus_d12.unlocks (
    token uuid primary key default gen_random_uuid(),
    session_id uuid not null references auth.sessions(id) on delete cascade,
    profile_id uuid not null references public.profiles(id) on delete cascade,
    pin_updated_at timestamptz,
    expires_at timestamptz not null
);
create index d12_unlocks_session_idx on danplus_d12.unlocks(session_id, expires_at);
create index d12_unlocks_profile_idx on danplus_d12.unlocks(profile_id);

create table danplus_d12.recommendations (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    sender_id uuid not null references public.profiles(id) on delete cascade,
    recipient_id uuid not null references public.profiles(id) on delete cascade,
    request_id uuid not null,
    media_id text not null check (char_length(media_id) between 1 and 120),
    media_type text not null check (media_type in ('movie', 'series')),
    title text not null check (char_length(title) between 1 and 350),
    poster_path text check (poster_path ~ '^/[a-zA-Z0-9_.-]+$' and char_length(poster_path) <= 160),
    synopsis text check (char_length(synopsis) <= 2000),
    episode_keys text[] not null default '{}' check (cardinality(episode_keys) <= 1500),
    created_at timestamptz not null default now(),
    read_at timestamptz,
    sender_dismissed_at timestamptz,
    recipient_dismissed_at timestamptz,
    check (sender_id <> recipient_id),
    unique (sender_id, recipient_id, request_id)
);
create unique index d12_active_media_idx on danplus_d12.recommendations(sender_id, recipient_id, media_type, media_id)
where sender_dismissed_at is null or recipient_dismissed_at is null;
create index d12_owner_idx on danplus_d12.recommendations(owner_id);
create index d12_sent_idx on danplus_d12.recommendations(sender_id, created_at desc, id desc);
create index d12_inbox_idx on danplus_d12.recommendations(recipient_id, created_at desc, id desc);

alter table danplus_d12.unlocks enable row level security;
alter table danplus_d12.recommendations enable row level security;
revoke all on all tables in schema danplus_d12 from public, anon, authenticated;

create function danplus_d12.request(
    operation text,
    viewer_profile_id uuid,
    payload jsonb,
    proof uuid
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller uuid := auth.uid();
    caller_session uuid := nullif(auth.jwt()->>'session_id', '')::uuid;
    owner uuid;
    actor public.profiles%rowtype;
    target uuid;
    targets uuid[];
    item uuid;
    mutation uuid;
    kind text;
    media text;
    episodes text[];
    box text;
    response jsonb;
    pin_valid boolean;
    retry_seconds integer;
    cursor_id uuid;
    cursor_time timestamptz;
begin
    if caller is null or caller_session is null or
        coalesce((auth.jwt()->>'is_anonymous')::boolean, false) then
        raise exception 'D12_SESSION_REQUIRED' using errcode = '42501';
    end if;
    perform 1 from auth.sessions s where s.id = caller_session and s.user_id = caller
        and (s.not_after is null or s.not_after > now()) for share;
    if not found then
        raise exception 'D12_SESSION_REQUIRED' using errcode = '42501';
    end if;
    owner := public.get_sync_owner();
    perform pg_advisory_xact_lock(hashtextextended(owner::text, 0));
    select p.* into actor from public.profiles p where p.id = viewer_profile_id and p.user_id = owner for update;
    if not found then
        raise exception 'D12_PROFILE_UNAVAILABLE' using errcode = '42501';
    end if;

    if operation = 'unlock' then
        if not actor.pin_enabled then
            return jsonb_build_object('unlocked', true, 'proof', null);
        end if;
        if actor.pin_hash is null or coalesce(payload->>'pin', '') !~ '^[0-9]{4}$' then
            return jsonb_build_object('unlocked', false, 'retry_after_seconds', 0);
        end if;
        select v.unlocked, v.retry_after_seconds into pin_valid, retry_seconds
        from public.verify_profile_pin(actor.profile_index, payload->>'pin') v;
        if pin_valid is distinct from true then
            return jsonb_build_object('unlocked', false, 'retry_after_seconds', coalesce(retry_seconds, 0));
        end if;
        delete from danplus_d12.unlocks u where u.session_id = caller_session
            and (u.profile_id = actor.id or u.expires_at <= now());
        insert into danplus_d12.unlocks(session_id, profile_id, pin_updated_at, expires_at)
        values (caller_session, actor.id, actor.pin_updated_at, now() + interval '10 minutes')
        returning token into item;
        return jsonb_build_object('unlocked', true, 'proof', item, 'expires_in', 600);
    end if;

    if actor.pin_enabled and not exists (
        select 1 from danplus_d12.unlocks u where u.token = proof
            and u.session_id = caller_session and u.profile_id = actor.id and u.expires_at > now()
            and u.pin_updated_at is not distinct from actor.pin_updated_at
    ) then
        raise exception 'D12_PROFILE_LOCKED' using errcode = '42501';
    end if;

    if operation = 'send' then
        mutation := (payload->>'request_id')::uuid;
        media := payload->>'media_id';
        kind := payload->>'media_type';
        select array_agg(distinct value::uuid order by value::uuid) into targets
            from jsonb_array_elements_text(payload->'recipients');
        select coalesce(array_agg(distinct value), '{}'::text[]) into episodes
            from jsonb_array_elements_text(payload->'episode_keys');
        if mutation is null or cardinality(targets) is null or cardinality(targets) not between 1 and 5
            or actor.id = any(targets) or array_position(targets, null) is not null
            or kind is null or kind not in ('movie', 'series')
            or media is null or btrim(media) <> media or char_length(media) not between 1 and 120
            or coalesce(char_length(btrim(payload->>'title')), 0) not between 1 and 350
            or (kind = 'movie' and cardinality(episodes) <> 0)
            or exists (select 1 from unnest(episodes) e where e is null or e !~ '^[0-9]{1,4}:[0-9]{1,5}$') then
            raise exception 'D12_INVALID_SEND' using errcode = '22023';
        end if;
        perform 1 from public.profiles p where p.user_id = owner and p.id = any(targets)
            order by p.id for share;
        if (select count(*) from public.profiles p where p.user_id = owner and p.id = any(targets)) <> cardinality(targets) then
            raise exception 'D12_PROFILE_UNAVAILABLE' using errcode = '42501';
        end if;
        if exists (
            select 1 from danplus_d12.recommendations r where r.sender_id = actor.id
                and r.request_id = mutation and (r.media_id, r.media_type) is distinct from (media, kind)
        ) then
            raise exception 'D12_REQUEST_REUSED' using errcode = '22023';
        end if;
        foreach target in array targets loop
            insert into danplus_d12.recommendations(
                owner_id, sender_id, recipient_id, request_id, media_id, media_type, title, poster_path, synopsis, episode_keys
            ) values (
                owner, actor.id, target, mutation, media, kind, btrim(payload->>'title'),
                nullif(payload->>'poster_path', ''), nullif(payload->>'synopsis', ''), episodes
            ) on conflict do nothing;
        end loop;
        return jsonb_build_object('accepted', cardinality(targets));
    end if;

    if operation = 'list' then
        box := payload->>'box';
        cursor_id := (payload->>'cursor_id')::uuid;
        cursor_time := (payload->>'cursor_time')::timestamptz;
        if box is null or box not in ('received', 'sent') or ((cursor_id is null) <> (cursor_time is null)) then
            raise exception 'D12_INVALID_LIST' using errcode = '22023';
        end if;
        select coalesce(jsonb_agg(to_jsonb(rows) order by rows.created_at desc, rows.id desc), '[]') into response
        from (
            select r.id, r.sender_id, r.recipient_id, r.media_id, r.media_type, r.title, r.poster_path, r.synopsis,
                r.episode_keys, r.created_at, r.read_at,
                case when recipient.pin_enabled and recipient.id <> actor.id then false
                    when r.media_type = 'movie' then exists (
                        select 1 from public.watched_items w where w.user_id = owner and w.profile_id = recipient.profile_index
                            and w.content_id = r.media_id and w.content_type = 'movie' and w.season is null and w.episode is null
                            and w.watched_at >= (extract(epoch from recipient.created_at) * 1000)::bigint
                    )
                    when cardinality(r.episode_keys) > 0 then not exists (
                        select 1 from unnest(r.episode_keys) e where not exists (
                            select 1 from public.watched_items w where w.user_id = owner and w.profile_id = recipient.profile_index
                                and w.content_id = r.media_id and w.content_type in ('series', 'tv', 'show')
                                and w.watched_at >= (extract(epoch from recipient.created_at) * 1000)::bigint
                                and concat(w.season, ':', w.episode) = e
                        )
                    ) else false end as watched
            from danplus_d12.recommendations r
            join public.profiles recipient on recipient.id = r.recipient_id and recipient.user_id = owner
            join public.profiles sender on sender.id = r.sender_id and sender.user_id = owner
            where r.owner_id = owner and (
                (box = 'received' and r.recipient_id = actor.id and r.recipient_dismissed_at is null)
                or (box = 'sent' and r.sender_id = actor.id and r.sender_dismissed_at is null)
            ) and (cursor_id is null or (r.created_at, r.id) < (cursor_time, cursor_id))
            order by r.created_at desc, r.id desc
            limit 50
        ) rows;
        return response;
    end if;

    item := (payload->>'id')::uuid;
    if operation = 'read' then
        update danplus_d12.recommendations r set read_at = coalesce(r.read_at, now())
        where r.id = item and r.owner_id = owner and r.recipient_id = actor.id and r.recipient_dismissed_at is null;
    elsif operation = 'dismiss' then
        update danplus_d12.recommendations r set
            sender_dismissed_at = case when r.sender_id = actor.id then coalesce(r.sender_dismissed_at, now()) else r.sender_dismissed_at end,
            recipient_dismissed_at = case when r.recipient_id = actor.id then coalesce(r.recipient_dismissed_at, now()) else r.recipient_dismissed_at end
        where r.id = item and r.owner_id = owner and actor.id in (r.sender_id, r.recipient_id);
    else
        raise exception 'D12_INVALID_OPERATION' using errcode = '22023';
    end if;
    if not found then
        if operation = 'dismiss' then
            return jsonb_build_object('ok', true);
        end if;
        raise exception 'D12_RECOMMENDATION_UNAVAILABLE' using errcode = '42501';
    end if;
    return jsonb_build_object('ok', true);
end;
$$;

revoke all on function danplus_d12.request(text, uuid, jsonb, uuid) from public, anon, authenticated;
grant usage on schema danplus_d12 to authenticated;
grant execute on function danplus_d12.request(text, uuid, jsonb, uuid) to authenticated;

create function public.recommendations_v1(
    p_operation text,
    p_profile_id uuid,
    p_payload jsonb default '{}'::jsonb,
    p_proof uuid default null
) returns jsonb
language sql
security invoker
set search_path = ''
as $$
    select danplus_d12.request(p_operation, p_profile_id, p_payload, p_proof);
$$;
revoke all on function public.recommendations_v1(text, uuid, jsonb, uuid) from public, anon, authenticated;
grant execute on function public.recommendations_v1(text, uuid, jsonb, uuid) to authenticated;

commit;
