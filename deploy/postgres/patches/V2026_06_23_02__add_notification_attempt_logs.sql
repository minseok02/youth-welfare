CREATE TABLE IF NOT EXISTS notification_attempt_logs (
    id              BIGSERIAL PRIMARY KEY,
    user_key_hash   VARCHAR(64),
    channel         VARCHAR(20) NOT NULL,
    kind            VARCHAR(60) NOT NULL,
    outcome         VARCHAR(60) NOT NULL,
    item_count      INTEGER NOT NULL DEFAULT 0,
    endpoint_host   VARCHAR(160),
    error_type      VARCHAR(160),
    duration_ms     BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_nal_created ON notification_attempt_logs (created_at);
CREATE INDEX IF NOT EXISTS idx_nal_outcome_created ON notification_attempt_logs (outcome, created_at);
CREATE INDEX IF NOT EXISTS idx_nal_channel_kind_created ON notification_attempt_logs (channel, kind, created_at);
CREATE INDEX IF NOT EXISTS idx_nal_user_created ON notification_attempt_logs (user_key_hash, created_at);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_core_rw') THEN
        GRANT SELECT, INSERT, DELETE ON TABLE notification_attempt_logs TO app_core_rw;
        GRANT USAGE, SELECT ON SEQUENCE notification_attempt_logs_id_seq TO app_core_rw;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'admin_dashboard_ro') THEN
        GRANT SELECT ON TABLE notification_attempt_logs TO admin_dashboard_ro;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'migration_admin') THEN
        GRANT ALL PRIVILEGES ON TABLE notification_attempt_logs TO migration_admin;
        GRANT ALL PRIVILEGES ON SEQUENCE notification_attempt_logs_id_seq TO migration_admin;
    END IF;
END $$;
