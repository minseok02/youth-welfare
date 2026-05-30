DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_core_rw')
        AND to_regclass('public.recommendation_review_gate_promotion_approvals') IS NOT NULL THEN
        REVOKE ALL PRIVILEGES ON TABLE public.recommendation_review_gate_promotion_approvals FROM app_core_rw;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'recommendation_review_gate_command_rw')
        AND to_regclass('public.recommendation_review_gate_promotion_approvals') IS NOT NULL THEN
        GRANT SELECT, INSERT, UPDATE, DELETE
            ON recommendation_review_gate_promotion_approvals
            TO recommendation_review_gate_command_rw;
    END IF;
END
$$;
