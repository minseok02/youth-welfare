CREATE TABLE IF NOT EXISTS recommendation_review_gate_promotion_approvals (
    approval_key         VARCHAR(100) PRIMARY KEY,
    approval_status      VARCHAR(50) NOT NULL,
    approval_scope       VARCHAR(100) NOT NULL,
    approval_note        TEXT,
    approved_by_user_key VARCHAR(32) NOT NULL,
    approved_at          TIMESTAMP NOT NULL,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rrgpa_scope_approved_at
    ON recommendation_review_gate_promotion_approvals (approval_scope, approved_at DESC);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_core_rw') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE
            ON recommendation_review_gate_promotion_approvals
            TO app_core_rw;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'migration_admin') THEN
        GRANT ALL PRIVILEGES
            ON recommendation_review_gate_promotion_approvals
            TO migration_admin;
    END IF;
END
$$;
