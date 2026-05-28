CREATE TABLE IF NOT EXISTS recent_policy_views (
    id             BIGSERIAL PRIMARY KEY,
    user_key       VARCHAR(32) NOT NULL,
    service_id     BIGINT NOT NULL,
    last_viewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_recent_policy_views_user_service UNIQUE (user_key, service_id),
    CONSTRAINT fk_rpv_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_rpv_user_key_last_viewed
    ON recent_policy_views (user_key, last_viewed_at DESC);

CREATE INDEX IF NOT EXISTS idx_rpv_service
    ON recent_policy_views (service_id);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_core_rw') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE
            ON recent_policy_views
            TO app_core_rw;
        GRANT USAGE, SELECT
            ON SEQUENCE recent_policy_views_id_seq
            TO app_core_rw;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'migration_admin') THEN
        GRANT ALL PRIVILEGES
            ON recent_policy_views
            TO migration_admin;
        GRANT ALL PRIVILEGES
            ON SEQUENCE recent_policy_views_id_seq
            TO migration_admin;
    END IF;
END
$$;
