DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'recommendation_review_gate_command_username') THEN
        EXECUTE format(
                'ALTER ROLE %I LOGIN PASSWORD %L',
                :'recommendation_review_gate_command_username',
                :'recommendation_review_gate_command_password'
        );
    ELSE
        EXECUTE format(
                'CREATE ROLE %I LOGIN PASSWORD %L',
                :'recommendation_review_gate_command_username',
                :'recommendation_review_gate_command_password'
        );
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO :"recommendation_review_gate_command_username";

DO $$
BEGIN
    IF to_regclass('public.recommendation_review_gate_promotion_approvals') IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_core_rw') THEN
            REVOKE ALL PRIVILEGES ON TABLE public.recommendation_review_gate_promotion_approvals FROM app_core_rw;
        END IF;
        EXECUTE format(
                'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.recommendation_review_gate_promotion_approvals TO %I',
                :'recommendation_review_gate_command_username'
        );
    END IF;
END
$$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'migration_username') THEN
        GRANT ALL PRIVILEGES
            ON recommendation_review_gate_promotion_approvals
            TO :"migration_username";
    END IF;
END
$$;
