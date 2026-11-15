-- The highlight is independent of collections_json and has one revision per profile.
create table if not exists danplus_nuvio.profile_special_highlights (
    owner_id uuid not null,
    profile_id uuid not null,
    revision bigint not null check (revision > 0),
    mutation_id uuid not null,
    selection jsonb,
    primary key (owner_id, profile_id)
);

alter table danplus_nuvio.profile_special_highlights enable row level security;
revoke all on danplus_nuvio.profile_special_highlights from public, anon, authenticated;
grant select, insert, update on danplus_nuvio.profile_special_highlights to service_role;

create or replace function public.danplus_special_highlight_v1(
    owner uuid, profile uuid, payload jsonb
) returns jsonb
language plpgsql security invoker set search_path = ''
as $$
declare
    h danplus_nuvio.profile_special_highlights%rowtype;
    expected bigint;
    mutation uuid;
    choice jsonb;
begin
    if owner is null or profile is null or jsonb_typeof(payload) is distinct from 'object' then
        raise exception 'D12_INVALID_HIGHLIGHT' using errcode = '22023';
    end if;

    -- A revision check and its update must use the same lock even before the first write.
    perform pg_advisory_xact_lock(hashtextextended(owner::text || ':' || profile::text, 1));
    select existing.* into h from danplus_nuvio.profile_special_highlights existing
        where existing.owner_id = owner and existing.profile_id = profile;

    if payload ? 'mutation' then
        mutation := (payload->>'mutation')::uuid;
        expected := (payload->>'expected_revision')::bigint;
        choice := nullif(payload->'selection', 'null'::jsonb);
        if mutation is null or expected is null or expected < 0 or not (payload ? 'selection') then
            raise exception 'D12_INVALID_HIGHLIGHT' using errcode = '22023';
        end if;

        if choice is not null then
            if jsonb_typeof(choice) is distinct from 'object'
                or jsonb_typeof(choice->'id') is distinct from 'string'
                or char_length(choice->>'id') not between 1 and 160
                or (choice->>'id') ~ '[[:cntrl:]]'
                or coalesce(choice->>'style', '') not in ('SOLID', 'GRADIENT')
                or choice - array['id','style','solid','gradient'] <> '{}'::jsonb
                or (choice ? 'solid' and (choice->'solid' <> 'null'::jsonb and
                    (jsonb_typeof(choice->'solid') <> 'number' or (choice->>'solid') !~ '^-?[0-9]{1,10}$')))
                or (choice ? 'gradient' and (choice->'gradient' <> 'null'::jsonb and
                    (jsonb_typeof(choice->'gradient') <> 'number' or (choice->>'gradient') !~ '^-?[0-9]{1,10}$')))
            then raise exception 'D12_INVALID_HIGHLIGHT' using errcode = '22023';
            end if;
            if (choice->>'solid')::bigint not between -2147483648 and 2147483647
                or (choice->>'gradient')::bigint not between -2147483648 and 2147483647
            then raise exception 'D12_INVALID_HIGHLIGHT' using errcode = '22023';
            end if;
        end if;

        -- A retry reuses the same mutation ID. A stale revision returns the live state.
        if expected = coalesce(h.revision, 0) and mutation is distinct from h.mutation_id then
            insert into danplus_nuvio.profile_special_highlights as target
                (owner_id, profile_id, revision, mutation_id, selection)
            values (owner, profile, expected + 1, mutation, choice)
            on conflict (owner_id, profile_id) do update
                set revision = excluded.revision, mutation_id = excluded.mutation_id, selection = excluded.selection
            returning target.* into h;
        end if;
    end if;
    return jsonb_build_object('revision', coalesce(h.revision, 0), 'selection', h.selection);
end;
$$;

revoke all on function public.danplus_special_highlight_v1(uuid, uuid, jsonb) from public, anon, authenticated;
grant execute on function public.danplus_special_highlight_v1(uuid, uuid, jsonb) to service_role;
