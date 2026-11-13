begin;
drop function if exists public.receive_danplus_report(uuid, text, text, jsonb);
-- Preserve the private inbox and its already received reports.
commit;
