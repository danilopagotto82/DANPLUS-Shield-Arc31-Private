-- Disable the added interfaces without deleting any reports, responses or audit entries.
begin;
revoke execute on function public.danplus_support_queue(text,text,timestamptz,uuid,uuid),
    public.danplus_support_thread(uuid), public.danplus_support_update(uuid,uuid,text,text,text) from authenticated;
revoke execute on function public.danplus_support_receive_account(uuid,uuid,text,text,jsonb),
    public.danplus_support_my_reports(uuid) from service_role;
commit;
