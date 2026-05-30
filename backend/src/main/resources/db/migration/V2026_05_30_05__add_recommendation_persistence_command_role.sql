DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_core_rw')
       AND to_regclass('public.user_recommendations') IS NOT NULL THEN
        REVOKE DELETE ON TABLE public.user_recommendations FROM app_core_rw;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'recommendation_persistence_command_rw') THEN
        IF to_regclass('public.user_recommendations') IS NOT NULL THEN
            GRANT INSERT, DELETE ON TABLE public.user_recommendations TO recommendation_persistence_command_rw;
        END IF;
        IF to_regclass('public.user_recommendations_id_seq') IS NOT NULL THEN
            GRANT USAGE, SELECT ON SEQUENCE public.user_recommendations_id_seq TO recommendation_persistence_command_rw;
        END IF;
    END IF;
END
$$;
