DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_core_rw')
        AND to_regclass('public.recent_policy_views') IS NOT NULL THEN
        REVOKE DELETE ON TABLE public.recent_policy_views FROM app_core_rw;
    END IF;
END
$$;
