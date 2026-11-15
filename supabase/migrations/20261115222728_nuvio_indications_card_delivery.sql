-- A send can reach multiple recipients, each with its own retry/request ID.
alter table danplus_nuvio.recommendations
    add column if not exists send_id uuid,
    add column if not exists backdrop_path text
        check (backdrop_path ~ '^/[a-zA-Z0-9_.-]+$' and char_length(backdrop_path) <= 160);

create or replace function public.nuvio_indications_v1(
    owner uuid,
    caller_session uuid,
    viewer_profile_id uuid,
    verified_profiles jsonb,
    operation text,
    payload jsonb,
    proof uuid
) returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
#variable_conflict use_variable
declare
    actor jsonb;
    target uuid;
    targets uuid[];
    item uuid;
    mutation uuid;
    delivery uuid;
    kind text;
    media text;
    episodes text[];
    box text;
    response jsonb;
    cursor_id uuid;
    cursor_time timestamptz;
begin
    if owner is null or caller_session is null or viewer_profile_id is null then
        raise exception 'D12_SESSION_REQUIRED' using errcode = '42501';
    end if;
    perform pg_advisory_xact_lock(hashtextextended(owner::text, 0));
    select p.value into actor from jsonb_array_elements(verified_profiles) p
    where (p.value->>'id')::uuid = viewer_profile_id;
    if actor is null then
        raise exception 'D12_PROFILE_UNAVAILABLE' using errcode = '42501';
    end if;

    if operation = 'unlock' then
        if actor->>'pin_enabled' = 'false' then
            return jsonb_build_object('unlocked', true);
        end if;
        if nullif(actor->>'updated_at', '') is null then
            raise exception 'D12_PROFILE_LOCKED' using errcode = '42501';
        end if;
        delete from danplus_nuvio.unlocks u
        where (u.session_id = caller_session and u.profile_id = viewer_profile_id)
            or (u.session_id = caller_session and u.expires_at <= now());
        insert into danplus_nuvio.unlocks(session_id, profile_id, profile_version, expires_at)
        values (caller_session, viewer_profile_id, actor->>'updated_at', now() + interval '10 minutes')
        returning token into item;
        return jsonb_build_object('unlocked', true, 'proof', item);
    end if;

    if actor->>'pin_enabled' is distinct from 'false' and not exists (
        select 1 from danplus_nuvio.unlocks u where u.token = proof and u.session_id = caller_session
            and u.profile_id = viewer_profile_id and u.expires_at > now()
            and u.profile_version = actor->>'updated_at'
    ) then
        raise exception 'D12_PROFILE_LOCKED' using errcode = '42501';
    end if;

    if operation = 'send' then
        mutation := (payload->>'request_id')::uuid;
        delivery := nullif(payload->>'send_id', '')::uuid;
        media := payload->>'media_id';
        kind := payload->>'media_type';
        select array_agg(distinct value::uuid order by value::uuid) into targets
            from jsonb_array_elements_text(payload->'recipients');
        select coalesce(array_agg(distinct value), '{}'::text[]) into episodes
            from jsonb_array_elements_text(payload->'episode_keys');
        if mutation is null or cardinality(targets) is null or cardinality(targets) not between 1 and 5
            or viewer_profile_id = any(targets) or array_position(targets, null) is not null
            or kind is null or kind not in ('movie', 'series')
            or media is null or btrim(media) <> media or char_length(media) not between 1 and 120
            or coalesce(char_length(btrim(payload->>'title')), 0) not between 1 and 350
            or (kind = 'movie' and cardinality(episodes) <> 0)
            or exists (select 1 from unnest(episodes) e where e is null or e !~ '^[0-9]{1,4}:[0-9]{1,5}$') then
            raise exception 'D12_INVALID_SEND' using errcode = '22023';
        end if;
        if exists (
            select 1 from unnest(targets) t(id)
            where not exists (select 1 from jsonb_array_elements(verified_profiles) p where (p.value->>'id')::uuid = t.id)
        ) then
            raise exception 'D12_PROFILE_UNAVAILABLE' using errcode = '42501';
        end if;
        if exists (
            select 1 from danplus_nuvio.recommendations r where r.sender_id = viewer_profile_id
                and r.request_id = mutation and (r.media_id, r.media_type) is distinct from (media, kind)
        ) then
            raise exception 'D12_REQUEST_REUSED' using errcode = '22023';
        end if;
        foreach target in array targets loop
            insert into danplus_nuvio.recommendations(
                owner_id, sender_id, recipient_id, request_id, send_id, media_id, media_type,
                title, poster_path, backdrop_path, synopsis, episode_keys
            ) values (
                owner, viewer_profile_id, target, mutation, delivery, media, kind, btrim(payload->>'title'),
                nullif(payload->>'poster_path', ''), nullif(payload->>'backdrop_path', ''),
                nullif(payload->>'synopsis', ''), episodes
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
            select r.id, r.sender_id, r.recipient_id, r.media_id, r.media_type, r.title, r.poster_path,
                r.backdrop_path, r.synopsis, case when box = 'sent' then r.send_id end as send_id,
                r.episode_keys, r.created_at, r.read_at,
                false as watched
            from danplus_nuvio.recommendations r
            where r.owner_id = owner
                and exists (select 1 from jsonb_array_elements(verified_profiles) p where (p.value->>'id')::uuid = r.sender_id)
                and exists (select 1 from jsonb_array_elements(verified_profiles) p where (p.value->>'id')::uuid = r.recipient_id)
                and (
                (box = 'received' and r.recipient_id = viewer_profile_id and r.recipient_dismissed_at is null)
                or (box = 'sent' and r.sender_id = viewer_profile_id and r.sender_dismissed_at is null)
            ) and (cursor_id is null or (r.created_at, r.id) < (cursor_time, cursor_id))
            order by r.created_at desc, r.id desc
            limit 50
        ) rows;
        return response;
    end if;

    item := (payload->>'id')::uuid;
    if operation = 'read' then
        update danplus_nuvio.recommendations r set read_at = coalesce(r.read_at, now())
        where r.id = item and r.owner_id = owner and r.recipient_id = viewer_profile_id and r.recipient_dismissed_at is null;
    elsif operation = 'dismiss' then
        update danplus_nuvio.recommendations r set
            sender_dismissed_at = case when r.sender_id = viewer_profile_id then coalesce(r.sender_dismissed_at, now()) else r.sender_dismissed_at end,
            recipient_dismissed_at = case when r.recipient_id = viewer_profile_id then coalesce(r.recipient_dismissed_at, now()) else r.recipient_dismissed_at end
        where r.id = item and r.owner_id = owner and viewer_profile_id in (r.sender_id, r.recipient_id);
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
