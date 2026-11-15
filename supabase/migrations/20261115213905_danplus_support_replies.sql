begin;

create schema danplus_support;
revoke all on schema danplus_support from public, anon, authenticated, service_role;

create table danplus_support.admins (
    user_id uuid primary key references auth.users(id) on delete cascade
);

create table danplus_support.cases (
    report_id uuid primary key references public.danplus_reports(id) on delete cascade,
    account_id uuid,
    status text not null default 'received' check (status in
        ('received', 'read', 'cause_found', 'fixed', 'not_danplus', 'closed')),
    updated_at timestamptz not null default now()
);

create index danplus_support_cases_account on danplus_support.cases (account_id, report_id)
    where account_id is not null;

create table danplus_support.events (
    id bigint generated always as identity primary key,
    report_id uuid not null references danplus_support.cases(report_id) on delete cascade,
    actor_id uuid not null,
    request_id uuid not null,
    created_at timestamptz not null default now(),
    status text,
    reply text,
    note text,
    unique (actor_id, request_id)
);

create index danplus_support_events_report on danplus_support.events (report_id, id desc);

alter table danplus_support.admins enable row level security;
alter table danplus_support.cases enable row level security;
alter table danplus_support.events enable row level security;

create function danplus_support.require_admin()
returns uuid language plpgsql stable set search_path = '' as $$
declare
    actor uuid := auth.uid();
begin
    if actor is null or coalesce(auth.jwt()->>'is_anonymous', 'false') <> 'false' or not exists (
        select 1 from danplus_support.admins a where a.user_id = actor
    ) then
        raise exception 'DANPLUS_SUPPORT_FORBIDDEN' using errcode = '42501';
    end if;
    return actor;
end;
$$;

create function public.danplus_support_queue(
    p_kind text default null, p_status text default null,
    p_before_time timestamptz default null, p_before_id uuid default null, p_protocol uuid default null
) returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare result jsonb;
begin
    perform danplus_support.require_admin();
    if (p_before_time is null) <> (p_before_id is null) then
        raise exception 'DANPLUS_SUPPORT_INVALID_CURSOR' using errcode = '22023';
    end if;
    select coalesce(jsonb_agg(to_jsonb(q) order by q.received_at desc, q.id desc), '[]'::jsonb) into result
    from (
        select r.id, r.received_at, r.kind, r.summary, r.app_version, r.device_model,
               coalesce(c.status, 'received') as status, c.account_id is not null as can_reply
        from public.danplus_reports r left join danplus_support.cases c on c.report_id = r.id
        where (p_protocol is null or r.id = p_protocol)
          and (p_kind is null or r.kind = p_kind)
          and (p_status is null or coalesce(c.status, 'received') = p_status)
          and (p_before_time is null or (r.received_at, r.id) < (p_before_time, p_before_id))
        order by r.received_at desc, r.id desc limit 50
    ) q;
    return result;
end;
$$;

create function public.danplus_support_thread(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare result jsonb;
begin
    perform danplus_support.require_admin();
    select jsonb_build_object(
        'id', r.id, 'kind', r.kind, 'received_at', r.received_at, 'summary', r.summary,
        'app_version', r.app_version, 'device_model', r.device_model, 'diagnostic', r.payload,
        'status', coalesce(c.status, 'received'), 'can_reply', c.account_id is not null,
        'events', (select coalesce(jsonb_agg(to_jsonb(e) order by e.id desc), '[]'::jsonb) from (
            select id, actor_id, created_at, status, reply, note from danplus_support.events
            where report_id = r.id order by id desc limit 100
        ) e)
    ) into result from public.danplus_reports r
    left join danplus_support.cases c on c.report_id = r.id where r.id = p_id;
    if result is null then raise exception 'DANPLUS_SUPPORT_NOT_FOUND' using errcode = 'P0002'; end if;
    return result;
end;
$$;

create function public.danplus_support_update(
    p_id uuid, p_request uuid, p_status text, p_reply text, p_note text
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
    actor uuid := danplus_support.require_admin();
    target_account uuid;
    reply_text text := nullif(btrim(p_reply), '');
    note_text text := nullif(btrim(p_note), '');
    written bigint;
begin
    if p_request is null or (p_status is not null and p_status not in
        ('received', 'read', 'cause_found', 'fixed', 'not_danplus', 'closed')) or
        (p_status is null and reply_text is null and note_text is null) or
        coalesce(char_length(reply_text), 0) > 4000 or coalesce(char_length(note_text), 0) > 4000 then
        raise exception 'DANPLUS_SUPPORT_INVALID_UPDATE' using errcode = '22023';
    end if;
    if not exists (select 1 from public.danplus_reports where id = p_id) then
        raise exception 'DANPLUS_SUPPORT_NOT_FOUND' using errcode = 'P0002';
    end if;
    insert into danplus_support.cases(report_id) values (p_id) on conflict do nothing;
    select account_id into target_account from danplus_support.cases where report_id = p_id for update;
    if reply_text is not null and target_account is null then
        raise exception 'DANPLUS_SUPPORT_LEGACY_NO_RECIPIENT' using errcode = '22023';
    end if;
    insert into danplus_support.events(report_id, actor_id, request_id, status, reply, note)
    values (p_id, actor, p_request, p_status, reply_text, note_text)
    on conflict (actor_id, request_id) do nothing returning id into written;
    if written is not null then
        update danplus_support.cases set status = coalesce(p_status, status), updated_at = now()
        where report_id = p_id;
    elsif not exists (select 1 from danplus_support.events
        where actor_id = actor and request_id = p_request and report_id = p_id
          and status is not distinct from p_status and reply is not distinct from reply_text
          and note is not distinct from note_text) then
        raise exception 'DANPLUS_SUPPORT_REQUEST_REUSED' using errcode = '22023';
    end if;
    return public.danplus_support_thread(p_id);
end;
$$;

create function public.danplus_support_receive_account(
    p_account uuid, p_install uuid, p_route text, p_hash text, p_payload jsonb
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
    result jsonb;
    receipt uuid;
begin
    if p_account is null then raise exception 'DANPLUS_SUPPORT_ACCOUNT_REQUIRED' using errcode = '22023'; end if;
    -- Reuse the intake lock. Never attach a pre-existing unowned receipt.
    perform pg_advisory_xact_lock(92246, 1);
    if exists (
        select 1 from public.danplus_reports r
        where r.install_id = p_install and r.route = p_route and r.fingerprint = p_hash
          and not exists (select 1 from danplus_support.cases c where c.report_id = r.id and c.account_id = p_account)
    ) then
        raise exception 'DANPLUS_SUPPORT_ACCOUNT_MISMATCH' using errcode = '42501';
    end if;
    result := public.receive_danplus_report(p_install, p_route, p_hash, p_payload);
    receipt := (result->>'reportId')::uuid;
    if receipt is not null then
        insert into danplus_support.cases(report_id, account_id) values (receipt, p_account)
        on conflict (report_id) do nothing;
        if not exists (select 1 from danplus_support.cases where report_id = receipt and account_id = p_account) then
            raise exception 'DANPLUS_SUPPORT_ACCOUNT_MISMATCH' using errcode = '42501';
        end if;
    end if;
    return result;
end;
$$;

create function public.danplus_support_my_reports(p_account uuid)
returns jsonb language sql stable security definer set search_path = '' as $$
    select coalesce(jsonb_agg(to_jsonb(q) order by q.received_at desc, q.id desc), '[]'::jsonb)
    from (
        select r.id, r.kind, r.received_at, c.status, c.updated_at,
               (select coalesce(jsonb_agg(jsonb_build_object('text', e.reply, 'at', e.created_at)
                   order by e.id desc), '[]'::jsonb) from (
                    select id, reply, created_at from danplus_support.events
                    where report_id = r.id and reply is not null order by id desc limit 30
                ) e) as replies
        from danplus_support.cases c join public.danplus_reports r on r.id = c.report_id
        where c.account_id = p_account
        order by r.received_at desc, r.id desc limit 50
    ) q;
$$;

revoke all on function danplus_support.require_admin() from public, anon, authenticated, service_role;
revoke all on function public.danplus_support_queue(text,text,timestamptz,uuid,uuid),
    public.danplus_support_thread(uuid), public.danplus_support_update(uuid,uuid,text,text,text)
    from public, anon, authenticated, service_role;
grant execute on function public.danplus_support_queue(text,text,timestamptz,uuid,uuid),
    public.danplus_support_thread(uuid), public.danplus_support_update(uuid,uuid,text,text,text) to authenticated;

revoke all on function public.danplus_support_receive_account(uuid,uuid,text,text,jsonb),
    public.danplus_support_my_reports(uuid) from public, anon, authenticated, service_role;
grant execute on function public.danplus_support_receive_account(uuid,uuid,text,text,jsonb),
    public.danplus_support_my_reports(uuid) to service_role;

commit;
