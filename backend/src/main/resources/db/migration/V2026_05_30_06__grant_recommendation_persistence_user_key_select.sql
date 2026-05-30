DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'recommendation_persistence_command_rw')
       AND to_regclass('public.user_recommendations') IS NOT NULL THEN
        GRANT SELECT (user_key) ON TABLE public.user_recommendations TO recommendation_persistence_command_rw;
    END IF;
END
$$;
