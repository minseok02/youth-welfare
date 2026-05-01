-- DRAFT ONLY
-- canonical summary slot row를 병행 저장하기 위한 초안.
-- 아직 writer dual-write / read-model 전환이 완료되지 않았으므로 실제 migration 경로(db/migration)에는 넣지 않는다.

USE youth_welfare;

CREATE TABLE IF NOT EXISTS service_taxonomy_summary_slots (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    service_id    BIGINT      NOT NULL,
    slot_key      VARCHAR(64) NOT NULL,
    code_set_key  VARCHAR(64),
    slot_code     VARCHAR(64) NOT NULL DEFAULT '',
    slot_label    TEXT        NOT NULL,
    source_field  VARCHAR(100),
    authority     ENUM('OFFICIAL','SYSTEM_DERIVED','AI_ENRICHED') NOT NULL,
    confidence    DECIMAL(4,3),
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_service_summary_slot (service_id, slot_key, slot_code, authority),
    KEY idx_stss_service_slot (service_id, slot_key),
    KEY idx_stss_slot_code (slot_key, slot_code),
    KEY idx_stss_code_set (code_set_key),
    CONSTRAINT fk_stss_service
        FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE,
    CONSTRAINT fk_stss_code_set
        FOREIGN KEY (code_set_key) REFERENCES normalization_code_sets(code_set_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
