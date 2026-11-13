begin;

-- Android uses the native profile/PIN RPCs. The table must not expose PIN hashes
-- or let clients replace the PIN state, UUID or login approval directly.
revoke all on table public.profiles, public.tv_login_sessions
    from public, anon, authenticated;

notify pgrst, 'reload schema';
commit;
