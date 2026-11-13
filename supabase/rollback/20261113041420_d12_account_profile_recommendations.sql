begin;
drop function public.recommendations_v1(text, uuid, jsonb, uuid);
drop function danplus_d12.request(text, uuid, jsonb, uuid);
drop table danplus_d12.unlocks;
drop table danplus_d12.recommendations;
drop schema danplus_d12;
commit;
