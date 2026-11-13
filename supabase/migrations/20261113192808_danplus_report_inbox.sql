begin;

create table public.danplus_reports (
    id uuid primary key default gen_random_uuid(),
    received_at timestamptz not null default now(),
    route text not null check (route in ('app', 'playback', 'auth')),
    kind text not null,
    summary text,
    app_version text,
    device_model text,
    payload jsonb not null,
    install_id uuid not null,
    fingerprint text not null
);

alter table public.danplus_reports enable row level security;
revoke all on public.danplus_reports from public, anon, authenticated, service_role;
create index danplus_reports_received on public.danplus_reports (received_at desc);
create index danplus_reports_install on public.danplus_reports (install_id, received_at desc);
create unique index danplus_reports_fingerprint on public.danplus_reports (install_id, route, fingerprint);

create function public.receive_danplus_report(p_install uuid, p_route text, p_hash text, p_payload jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    receipt uuid;
    category text;
    report_summary text;
begin
    if p_install is null or p_route is null or p_route not in ('app', 'playback', 'auth') or p_hash is null or
        p_hash !~ '^[0-9a-f]{64}$' or jsonb_typeof(p_payload) is distinct from 'object' or
        p_payload->>'schemaVersion' is distinct from '1' or octet_length(p_payload::text) > 900000 then
        raise exception 'DANPLUS_INVALID_REPORT' using errcode = '22023';
    end if;
    if p_route = 'app' then
        category := p_payload->>'kind';
        if category is null or category not in ('bug', 'idea', 'crash') then
            raise exception 'DANPLUS_INVALID_REPORT' using errcode = '22023';
        end if;
        report_summary := coalesce(p_payload->>'note', p_payload->>'where', p_payload#>>'{crash,reason}');
    elsif p_route = 'playback' then
        category := 'playback';
        report_summary := coalesce(p_payload#>>'{viewer,note}', p_payload#>>'{error,kind}', p_payload#>>'{content,title}');
    else
        category := 'auth';
        report_summary := p_payload#>>'{terminal,reason}';
    end if;

    perform pg_advisory_xact_lock(92246, 1);
    select id into receipt from public.danplus_reports
    where install_id = p_install and route = p_route and fingerprint = p_hash;
    if receipt is not null then
        return jsonb_build_object('reportId', receipt);
    end if;
    if (select count(*) from public.danplus_reports where install_id = p_install and received_at >= now() - interval '1 day') >= 12 or
       (select count(*) from public.danplus_reports where received_at >= now() - interval '1 day') >= 300 then
        return jsonb_build_object('rateLimited', true);
    end if;

    insert into public.danplus_reports (route, kind, summary, app_version, device_model, payload, install_id, fingerprint)
    values (p_route, category, left(report_summary, 1000), left(p_payload#>>'{app,versionName}', 100),
        left(p_payload#>>'{device,model}', 100), p_payload, p_install, p_hash)
    returning id into receipt;
    return jsonb_build_object('reportId', receipt);
end;
$$;

revoke all on function public.receive_danplus_report(uuid, text, text, jsonb) from public, anon, authenticated;
grant execute on function public.receive_danplus_report(uuid, text, text, jsonb) to service_role;

commit;
