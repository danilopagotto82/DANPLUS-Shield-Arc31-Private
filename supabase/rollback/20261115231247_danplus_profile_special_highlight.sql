-- Stop synchronization without deleting saved highlights.
drop function if exists public.danplus_special_highlight_v1(uuid, uuid, jsonb);
revoke select, insert, update on danplus_nuvio.profile_special_highlights from service_role;
