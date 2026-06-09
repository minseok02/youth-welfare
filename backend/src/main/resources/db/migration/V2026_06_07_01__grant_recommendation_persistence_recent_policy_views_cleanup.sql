DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'recommendation_persistence_command_rw')
       AND to_regclass('public.recent_policy_views') IS NOT NULL THEN
        GRANT DELETE ON TABLE public.recent_policy_views TO recommendation_persistence_command_rw;
        GRANT SELECT (user_key) ON TABLE public.recent_policy_views TO recommendation_persistence_command_rw;
    END IF;
END $$;
