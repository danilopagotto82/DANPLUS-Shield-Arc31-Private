create role anon;
create role authenticated;
create schema auth;
create table auth.users (id uuid primary key);
create table auth.sessions (id uuid primary key, user_id uuid references auth.users(id), not_after timestamptz);
create function auth.uid() returns uuid language sql stable as $$
    select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;
create function auth.jwt() returns jsonb language sql stable as $$
    select coalesce(nullif(current_setting('request.jwt.claims', true), '')::jsonb, '{}'::jsonb)
$$;
create function public.get_sync_owner() returns uuid language sql stable as $$ select auth.uid() $$;
create table public.profiles (
    id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id), profile_index integer not null,
    pin_enabled boolean not null, pin_hash text, pin_updated_at timestamptz, created_at timestamptz not null default now(),
    profile_id integer, name text, avatar_color_hex text,
    unique (user_id, profile_index)
);
create table public.watched_items (
    user_id uuid not null, profile_id integer not null, content_id text not null, content_type text not null,
    season integer, episode integer, created_at timestamptz default now(), watched_at bigint not null
);
-- Fixture only. The deployed function uses the native Arc PIN verifier and attempt limits.
create function public.verify_profile_pin(p_profile_id integer, p_pin text)
returns table(unlocked boolean, retry_after_seconds integer) language sql stable as $$
    select coalesce((select p.pin_hash = p_pin or p.pin_hash = extensions.crypt(p_pin, p.pin_hash) from public.profiles p
        where p.user_id = auth.uid() and p.profile_index = p_profile_id), false), 0
$$;
