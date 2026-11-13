begin;

create function danplus_d12.create_profile(profile_name text, avatar_color text, profile_pin text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller uuid := auth.uid();
    session_id uuid := nullif(auth.jwt()->>'session_id', '')::uuid;
    profile_slot integer;
    profile_uuid uuid;
begin
    if caller is null or session_id is null or
        coalesce((auth.jwt()->>'is_anonymous')::boolean, false) then
        raise exception 'D12_SESSION_REQUIRED' using errcode = '42501';
    end if;
    perform 1 from auth.sessions s where s.id = session_id and s.user_id = caller
        and (s.not_after is null or s.not_after > now()) for share;
    if not found then
        raise exception 'D12_SESSION_REQUIRED' using errcode = '42501';
    end if;
    if public.get_sync_owner() is distinct from caller then
        raise exception 'D12_SESSION_REQUIRED' using errcode = '42501';
    end if;
    if profile_name is null or char_length(btrim(profile_name)) not between 1 and 60 or
        avatar_color is null or avatar_color !~ '^#[a-fA-F0-9]{6}$' or
        (profile_pin is not null and profile_pin !~ '^[0-9]{4}$') then
        raise exception 'D12_INVALID_PROFILE' using errcode = '22023';
    end if;

    perform pg_advisory_xact_lock(hashtextextended(caller::text, 0));
    select slot into profile_slot from generate_series(1, 6) as slots(slot)
    where not exists (select 1 from public.profiles p where p.user_id = caller and p.profile_index = slot)
    order by slot limit 1;
    if profile_slot is null then
        raise exception 'D12_PROFILE_LIMIT' using errcode = '22023';
    end if;

    insert into public.profiles (user_id, profile_index, profile_id, name, avatar_color_hex, pin_enabled, pin_hash, pin_updated_at)
    values (caller, profile_slot, profile_slot, btrim(profile_name), avatar_color, profile_pin is not null,
            case when profile_pin is not null then extensions.crypt(profile_pin, extensions.gen_salt('bf', 10)) end,
            case when profile_pin is not null then now() end)
    returning id into profile_uuid;
    return profile_uuid;
end;
$$;

revoke all on function danplus_d12.create_profile(text, text, text) from public, anon, authenticated;
grant execute on function danplus_d12.create_profile(text, text, text) to authenticated;

create function public.d12_create_profile(p_name text, p_avatar_color text, p_pin text default null)
returns uuid
language sql
security invoker
set search_path = ''
as $$ select danplus_d12.create_profile(p_name, p_avatar_color, p_pin) $$;

revoke all on function public.d12_create_profile(text, text, text) from public, anon, authenticated;
grant execute on function public.d12_create_profile(text, text, text) to authenticated;

commit;
