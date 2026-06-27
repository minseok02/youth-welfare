CREATE TABLE IF NOT EXISTS schema_migration_history (
    id            BIGSERIAL PRIMARY KEY,
    version       VARCHAR(64) NOT NULL,
    description   VARCHAR(255) NOT NULL,
    script_name   VARCHAR(255) NOT NULL,
    script_sha256 VARCHAR(64) NOT NULL,
    applied_by    VARCHAR(128) NOT NULL DEFAULT CURRENT_USER,
    applied_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note          TEXT,
    CONSTRAINT uq_smh_script_name UNIQUE (script_name)
);

CREATE INDEX IF NOT EXISTS idx_smh_applied_at
    ON schema_migration_history (applied_at DESC);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'migration_admin') THEN
        GRANT ALL PRIVILEGES ON TABLE public.schema_migration_history TO migration_admin;
        GRANT ALL PRIVILEGES ON SEQUENCE public.schema_migration_history_id_seq TO migration_admin;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'admin_dashboard_ro') THEN
        GRANT SELECT ON TABLE public.schema_migration_history TO admin_dashboard_ro;
        GRANT SELECT ON SEQUENCE public.schema_migration_history_id_seq TO admin_dashboard_ro;
    END IF;
END $$;
