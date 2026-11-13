select
    to_regclass('auth.sessions') is not null as session_store,
    to_regclass('public.profiles') is not null as profiles,
    to_regclass('public.watched_items') is not null as watched_items,
    to_regprocedure('public.get_sync_owner()') is not null as sync_owner,
    to_regprocedure('public.sync_pull_profiles()') is not null as profile_pull,
    to_regprocedure('public.verify_profile_pin(integer,text)') is not null as pin_verifier,
    exists (
        select 1 from pg_catalog.pg_proc p join pg_catalog.pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'public' and p.proname in ('start_device_login_session', 'start_tv_login_session')
    ) as qr_start,
    exists (
        select 1 from pg_catalog.pg_proc p join pg_catalog.pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'public' and p.proname = 'poll_tv_login_session'
    ) as qr_poll,
    to_regprocedure('public.approve_device_login_session(text)') is not null as qr_approve,
    to_regprocedure('public.approve_tv_login_session(text)') is not null as qr_approve_legacy;
