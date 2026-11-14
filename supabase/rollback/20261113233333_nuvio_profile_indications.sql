begin;
drop function public.nuvio_indications_v1(uuid, uuid, uuid, jsonb, text, jsonb, uuid);
-- Keep the private tables and records for a later redeploy or export.
commit;
