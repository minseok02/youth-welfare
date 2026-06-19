CREATE TABLE IF NOT EXISTS policy_field_corrections (
    id                   BIGSERIAL PRIMARY KEY,
    service_id           BIGINT NOT NULL,
    correction_type      VARCHAR(40) NOT NULL,
    original_json        TEXT NOT NULL DEFAULT '{}',
    correction_json      TEXT NOT NULL DEFAULT '{}',
    reason_report_id     BIGINT,
    correction_note      VARCHAR(1000),
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_key  VARCHAR(100) NOT NULL,
    updated_by_user_key  VARCHAR(100) NOT NULL,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pfc_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE,
    CONSTRAINT fk_pfc_reason_report FOREIGN KEY (reason_report_id) REFERENCES policy_error_reports(id) ON DELETE SET NULL,
    CONSTRAINT chk_pfc_type CHECK (correction_type IN ('APPLICATION_PERIOD', 'DETAIL_URL', 'ELIGIBILITY', 'DUPLICATE_POLICY'))
);

CREATE INDEX IF NOT EXISTS idx_pfc_service_active ON policy_field_corrections (service_id, active);
CREATE INDEX IF NOT EXISTS idx_pfc_reason_report ON policy_field_corrections (reason_report_id);
CREATE INDEX IF NOT EXISTS idx_pfc_updated_at ON policy_field_corrections (updated_at DESC);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_core_rw') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.policy_field_corrections TO app_core_rw;
        GRANT USAGE, SELECT ON SEQUENCE public.policy_field_corrections_id_seq TO app_core_rw;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'admin_dashboard_ro') THEN
        GRANT SELECT ON TABLE public.policy_field_corrections TO admin_dashboard_ro;
        GRANT SELECT ON SEQUENCE public.policy_field_corrections_id_seq TO admin_dashboard_ro;
    END IF;
END $$;
