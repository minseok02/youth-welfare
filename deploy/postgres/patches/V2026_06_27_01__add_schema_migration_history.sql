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

SELECT format('GRANT ALL PRIVILEGES ON TABLE public.schema_migration_history TO %I', :'migration_username')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'migration_username') \gexec

SELECT format('GRANT ALL PRIVILEGES ON SEQUENCE public.schema_migration_history_id_seq TO %I', :'migration_username')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'migration_username') \gexec

SELECT format('GRANT SELECT ON TABLE public.schema_migration_history TO %I', :'admin_ro_username')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'admin_ro_username') \gexec

SELECT format('GRANT SELECT ON SEQUENCE public.schema_migration_history_id_seq TO %I', :'admin_ro_username')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'admin_ro_username') \gexec
