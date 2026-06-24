CREATE TABLE IF NOT EXISTS recommendation_run_logs (
    id                    BIGSERIAL PRIMARY KEY,
    user_key              VARCHAR(32) NOT NULL,
    personal              BOOLEAN NOT NULL DEFAULT FALSE,
    outcome               VARCHAR(40) NOT NULL,
    cluster_id            VARCHAR(80),
    retrieved_count       INTEGER NOT NULL DEFAULT 0,
    rule_scored_count     INTEGER NOT NULL DEFAULT 0,
    post_filter_count     INTEGER NOT NULL DEFAULT 0,
    reranked_count        INTEGER NOT NULL DEFAULT 0,
    saved_count           INTEGER NOT NULL DEFAULT 0,
    ai_status_counts_json TEXT,
    duration_ms           BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rrl_created ON recommendation_run_logs (created_at);
CREATE INDEX IF NOT EXISTS idx_rrl_outcome_created ON recommendation_run_logs (outcome, created_at);
CREATE INDEX IF NOT EXISTS idx_rrl_user_created ON recommendation_run_logs (user_key, created_at);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_core_rw') THEN
        GRANT SELECT, INSERT, DELETE ON TABLE recommendation_run_logs TO app_core_rw;
        GRANT USAGE, SELECT ON SEQUENCE recommendation_run_logs_id_seq TO app_core_rw;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'admin_dashboard_ro') THEN
        GRANT SELECT ON TABLE recommendation_run_logs TO admin_dashboard_ro;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'migration_admin') THEN
        GRANT ALL PRIVILEGES ON TABLE recommendation_run_logs TO migration_admin;
        GRANT ALL PRIVILEGES ON SEQUENCE recommendation_run_logs_id_seq TO migration_admin;
    END IF;
END $$;
