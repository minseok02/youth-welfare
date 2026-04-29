-- DRAFT ONLY
-- 아직 backend writer/entity/read-model 이 완성되지 않았으므로 실제 migration 경로(db/migration)에는 넣지 않는다.
-- canonical sidecar 스키마 초안 확인 및 수동 리허설용으로만 유지한다.

USE youth_welfare;

CREATE TABLE IF NOT EXISTS normalization_code_sets (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    code_set_key  VARCHAR(64) NOT NULL,
    domain_type   ENUM('TAXONOMY','FACT') NOT NULL,
    source_system ENUM('YOUTH','GOV24','SYSTEM') NOT NULL,
    description   VARCHAR(255),
    version_label VARCHAR(50),
    is_active     TINYINT(1)  NOT NULL DEFAULT 1,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ncs_code_set_key (code_set_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS normalization_codes (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    code_set_key VARCHAR(64) NOT NULL,
    code         VARCHAR(64) NOT NULL,
    label        VARCHAR(255) NOT NULL,
    parent_code  VARCHAR(64),
    sort_order   INT         NOT NULL DEFAULT 0,
    extra_json   JSON,
    is_active    TINYINT(1)  NOT NULL DEFAULT 1,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_norm_code (code_set_key, code),
    KEY idx_norm_code_parent (code_set_key, parent_code),
    CONSTRAINT fk_norm_code_set
        FOREIGN KEY (code_set_key) REFERENCES normalization_code_sets(code_set_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS service_taxonomies (
    service_id                          BIGINT      NOT NULL,
    primary_source_system               ENUM('YOUTH','BOKJIRO','GOV24','SYSTEM') NOT NULL,
    compat_unified_category_code        VARCHAR(64),
    compat_unified_category_label       VARCHAR(100),
    youth_major_code                    VARCHAR(64),
    youth_major_label                   VARCHAR(100),
    youth_mid_code                      VARCHAR(64),
    youth_mid_label                     VARCHAR(100),
    gov24_service_field_code            VARCHAR(64),
    gov24_service_field_label           VARCHAR(100),
    gov24_user_type_code                VARCHAR(64),
    gov24_user_type_label               VARCHAR(100),
    gov24_benefit_type_code             VARCHAR(64),
    gov24_benefit_type_label            VARCHAR(100),
    provision_method_code               VARCHAR(64),
    provision_method_label              VARCHAR(100),
    authority                           ENUM('OFFICIAL','SYSTEM_DERIVED','AI_ENRICHED') NOT NULL,
    confidence                          DECIMAL(4,3),
    created_at                          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (service_id),
    KEY idx_stx_compat_category (compat_unified_category_code),
    KEY idx_stx_youth_major (youth_major_code),
    KEY idx_stx_gov24_field (gov24_service_field_code),
    CONSTRAINT fk_stx_service
        FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS service_taxonomy_terms (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    service_id    BIGINT      NOT NULL,
    term_group    VARCHAR(64) NOT NULL,
    code_set_key  VARCHAR(64),
    term_code     VARCHAR(64) NOT NULL DEFAULT '',
    term_label    VARCHAR(255) NOT NULL,
    source_field  VARCHAR(100) NOT NULL,
    authority     ENUM('OFFICIAL','SYSTEM_DERIVED','AI_ENRICHED') NOT NULL,
    sort_order    INT         NOT NULL DEFAULT 0,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_service_term (service_id, term_group, term_code, term_label, authority),
    KEY idx_stt_service_group (service_id, term_group),
    KEY idx_stt_group_code (term_group, term_code),
    KEY idx_stt_code_set (code_set_key),
    CONSTRAINT fk_stt_service
        FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE,
    CONSTRAINT fk_stt_code_set
        FOREIGN KEY (code_set_key) REFERENCES normalization_code_sets(code_set_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS service_facts (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    service_id        BIGINT      NOT NULL,
    fact_group        VARCHAR(64) NOT NULL,
    fact_code_set_key VARCHAR(64),
    fact_code         VARCHAR(64),
    fact_merge_key    VARCHAR(128) NOT NULL,
    fact_label        VARCHAR(255) NOT NULL,
    operator          ENUM('EQ','GTE','LTE','RANGE','FLAG','MEMBER') NOT NULL,
    value_type        ENUM('BOOLEAN','INTEGER','DECIMAL','STRING','DATE') NOT NULL,
    bool_value        TINYINT(1),
    int_value         INT,
    decimal_value     DECIMAL(12,2),
    text_value        VARCHAR(255),
    date_value        DATE,
    range_min_int     INT,
    range_max_int     INT,
    unit              VARCHAR(32),
    source_field      VARCHAR(100) NOT NULL,
    authority         ENUM('OFFICIAL','SYSTEM_DERIVED','RULE_DERIVED','AI_ENRICHED') NOT NULL,
    confidence        DECIMAL(4,3),
    raw_value         VARCHAR(255),
    evidence_text     TEXT,
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_service_fact_merge (service_id, fact_merge_key),
    KEY idx_sfact_service_group (service_id, fact_group),
    KEY idx_sfact_group_code (fact_group, fact_code),
    KEY idx_sfact_authority_group_code (authority, fact_group, fact_code),
    KEY idx_sfact_group_range (fact_group, range_min_int, range_max_int),
    KEY idx_sfact_code_set (fact_code_set_key),
    CONSTRAINT fk_sfact_service
        FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE,
    CONSTRAINT fk_sfact_code_set
        FOREIGN KEY (fact_code_set_key) REFERENCES normalization_code_sets(code_set_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
