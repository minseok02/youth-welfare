CREATE TABLE IF NOT EXISTS policy_region_corrections (
    id                   BIGSERIAL PRIMARY KEY,
    service_id           BIGINT NOT NULL UNIQUE,
    correction_scope     VARCHAR(20) NOT NULL,
    regions_json         TEXT NOT NULL DEFAULT '[]',
    original_regions_json TEXT NOT NULL DEFAULT '[]',
    reason_report_id     BIGINT,
    correction_note      VARCHAR(1000),
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_key  VARCHAR(100) NOT NULL,
    updated_by_user_key  VARCHAR(100) NOT NULL,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_prc_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE,
    CONSTRAINT fk_prc_reason_report FOREIGN KEY (reason_report_id) REFERENCES policy_error_reports(id) ON DELETE SET NULL,
    CONSTRAINT chk_prc_scope CHECK (correction_scope IN ('REGIONS', 'NATIONWIDE'))
);

CREATE INDEX IF NOT EXISTS idx_prc_active_service ON policy_region_corrections (active, service_id);
CREATE INDEX IF NOT EXISTS idx_prc_reason_report ON policy_region_corrections (reason_report_id);
