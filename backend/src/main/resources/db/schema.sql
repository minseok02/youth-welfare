-- ============================================================
-- Youth Welfare local PostgreSQL schema
-- Phase 1 base: boot + core CRUD recovery
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE SCHEMA IF NOT EXISTS youth_welfare_pii;

CREATE TABLE IF NOT EXISTS priority_options (
    id          SMALLSERIAL PRIMARY KEY,
    code        VARCHAR(30) NOT NULL,
    label       VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    CONSTRAINT uq_po_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS users (
    id                      BIGSERIAL PRIMARY KEY,
    user_key                VARCHAR(32) NOT NULL DEFAULT replace(gen_random_uuid()::text, '-', ''),
    email                   VARCHAR(255) NOT NULL,
    account_origin          VARCHAR(40) NOT NULL DEFAULT 'REAL_USER',
    password_hash           VARCHAR(255) NOT NULL,
    name                    VARCHAR(50),
    birth_date              DATE,
    phone_enc               VARCHAR(512),
    sido                    VARCHAR(50),
    sgg                     VARCHAR(50),
    region_code             VARCHAR(20),
    income_level            SMALLINT,
    household_type          VARCHAR(30),
    employment_status       VARCHAR(30),
    house_tenure_code       VARCHAR(20),
    housing_type_code       VARCHAR(20),
    basic_living_recipient_type_code VARCHAR(20),
    disability_grade_code   VARCHAR(20),
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    notification_yn         BOOLEAN NOT NULL DEFAULT FALSE,
    notification_email_yn   BOOLEAN NOT NULL DEFAULT TRUE,
    notification_in_app_yn  BOOLEAN NOT NULL DEFAULT TRUE,
    notification_web_push_yn BOOLEAN NOT NULL DEFAULT FALSE,
    notification_period     VARCHAR(10) NOT NULL DEFAULT 'NONE',
    notification_min_score  DOUBLE PRECISION DEFAULT 0.5,
    notification_consent_at TIMESTAMP,
    login_fail_count        INTEGER NOT NULL DEFAULT 0,
    locked_until            TIMESTAMP,
    display_count           INTEGER NOT NULL DEFAULT 10,
    profile_completeness    INTEGER DEFAULT 0,
    withdrawn_at            TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_user_key UNIQUE (user_key),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS auth_users (
    id                BIGSERIAL PRIMARY KEY,
    user_key          VARCHAR(32) NOT NULL,
    email_lookup_hash VARCHAR(64) NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    login_fail_count  INTEGER NOT NULL DEFAULT 0,
    locked_until      TIMESTAMP,
    withdrawn_at      TIMESTAMP,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_au_user_key UNIQUE (user_key),
    CONSTRAINT uq_au_email_lookup_hash UNIQUE (email_lookup_hash)
);

CREATE TABLE IF NOT EXISTS user_profiles (
    id                      BIGSERIAL PRIMARY KEY,
    user_key                VARCHAR(32) NOT NULL,
    age                     INTEGER,
    age_band                VARCHAR(20),
    age_calculated_at       TIMESTAMP,
    sido                    VARCHAR(50),
    sgg                     VARCHAR(50),
    region_code             VARCHAR(20),
    income_level            SMALLINT,
    household_type          VARCHAR(30),
    employment_status       VARCHAR(30),
    house_tenure_code       VARCHAR(20),
    housing_type_code       VARCHAR(20),
    basic_living_recipient_type_code VARCHAR(20),
    disability_grade_code   VARCHAR(20),
    notification_yn         BOOLEAN NOT NULL DEFAULT FALSE,
    notification_email_yn   BOOLEAN NOT NULL DEFAULT TRUE,
    notification_in_app_yn  BOOLEAN NOT NULL DEFAULT TRUE,
    notification_web_push_yn BOOLEAN NOT NULL DEFAULT FALSE,
    notification_period     VARCHAR(10) NOT NULL DEFAULT 'NONE',
    notification_min_score  DOUBLE PRECISION DEFAULT 0.5,
    notification_consent_at TIMESTAMP,
    display_count           INTEGER NOT NULL DEFAULT 10,
    profile_completeness    INTEGER DEFAULT 0,
    has_name                BOOLEAN NOT NULL DEFAULT FALSE,
    has_birth_date          BOOLEAN NOT NULL DEFAULT FALSE,
    has_phone               BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_upf_user_key UNIQUE (user_key)
);

CREATE INDEX IF NOT EXISTS idx_upf_notification ON user_profiles (notification_yn, notification_period);
CREATE INDEX IF NOT EXISTS idx_upf_region ON user_profiles (sido, sgg);
CREATE INDEX IF NOT EXISTS idx_upf_income_employment ON user_profiles (income_level, employment_status);

CREATE TABLE IF NOT EXISTS youth_welfare_pii.user_pii (
    id             BIGSERIAL PRIMARY KEY,
    user_key       VARCHAR(32) NOT NULL,
    email_enc      VARCHAR(512),
    name_enc       VARCHAR(512),
    birth_date_enc VARCHAR(128),
    phone_enc      VARCHAR(512),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_upii_user_key UNIQUE (user_key)
);

CREATE TABLE IF NOT EXISTS user_pii_sync_queue (
    id               BIGSERIAL PRIMARY KEY,
    user_key         VARCHAR(32) NOT NULL,
    email_enc        VARCHAR(512),
    name_enc         VARCHAR(512),
    birth_date_enc   VARCHAR(128),
    phone_enc        VARCHAR(512),
    status           VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count    INTEGER NOT NULL DEFAULT 0,
    last_enqueued_at TIMESTAMP,
    last_attempt_at  TIMESTAMP,
    last_synced_at   TIMESTAMP,
    last_error       VARCHAR(500),
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_upsq_user_key UNIQUE (user_key)
);

CREATE INDEX IF NOT EXISTS idx_upsq_status_enqueued ON user_pii_sync_queue (status, last_enqueued_at);

CREATE TABLE IF NOT EXISTS user_attributes (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    user_key   VARCHAR(32),
    attr_type  VARCHAR(30) NOT NULL,
    attr_value VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ua_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ua_user ON user_attributes (user_id);
CREATE INDEX IF NOT EXISTS idx_ua_user_key ON user_attributes (user_key);
CREATE INDEX IF NOT EXISTS idx_ua_attr_type ON user_attributes (attr_type);
CREATE INDEX IF NOT EXISTS idx_ua_user_key_attr_type ON user_attributes (user_key, attr_type);

CREATE TABLE IF NOT EXISTS user_priorities (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    user_key           VARCHAR(32),
    priority_option_id SMALLINT NOT NULL,
    priority_rank      INTEGER NOT NULL,
    weight             DOUBLE PRECISION NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_up_user_option UNIQUE (user_id, priority_option_id),
    CONSTRAINT fk_up_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_up_option FOREIGN KEY (priority_option_id) REFERENCES priority_options(id)
);

CREATE INDEX IF NOT EXISTS idx_up_user ON user_priorities (user_id);
CREATE INDEX IF NOT EXISTS idx_up_user_key_rank ON user_priorities (user_key, priority_rank);

CREATE TABLE IF NOT EXISTS welfare_services (
    id                   BIGSERIAL PRIMARY KEY,
    source_type          VARCHAR(20) NOT NULL,
    source_id            VARCHAR(50) NOT NULL,
    title                VARCHAR(255) NOT NULL,
    description          TEXT,
    support_content      TEXT,
    category_main        VARCHAR(100),
    category_sub         VARCHAR(100),
    keyword              VARCHAR(200),
    min_age              INTEGER,
    max_age              INTEGER,
    min_income           INTEGER,
    max_income           INTEGER,
    apply_start_date     DATE,
    apply_end_date       DATE,
    start_date           DATE,
    end_date             DATE,
    life_stage           VARCHAR(200),
    support_cycle        VARCHAR(50),
    provision_type       VARCHAR(100),
    apply_method_name    TEXT,
    is_online_apply      BOOLEAN DEFAULT FALSE,
    host_org             VARCHAR(200),
    operating_org        VARCHAR(200),
    contact              VARCHAR(100),
    detail_url           VARCHAR(2048),
    unified_category     VARCHAR(50),
    is_youth_specific    BOOLEAN NOT NULL DEFAULT FALSE,
    search_youth_relevant BOOLEAN NOT NULL DEFAULT TRUE,
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    api_view_count       BIGINT NOT NULL DEFAULT 0,
    view_count           INTEGER NOT NULL DEFAULT 0,
    collected_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    registered_at        TIMESTAMP,
    last_modified_at     TIMESTAMP,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ws_source UNIQUE (source_type, source_id)
);

CREATE INDEX IF NOT EXISTS idx_ws_status ON welfare_services (status);
CREATE INDEX IF NOT EXISTS idx_ws_unified_cat ON welfare_services (unified_category);
CREATE INDEX IF NOT EXISTS idx_ws_search_youth ON welfare_services (search_youth_relevant);
CREATE INDEX IF NOT EXISTS idx_ws_source_type ON welfare_services (source_type);
CREATE INDEX IF NOT EXISTS idx_ws_age ON welfare_services (min_age, max_age);
CREATE INDEX IF NOT EXISTS idx_ws_end_date ON welfare_services (end_date);
CREATE INDEX IF NOT EXISTS idx_ws_apply_end ON welfare_services (apply_end_date);
CREATE INDEX IF NOT EXISTS idx_ws_collected ON welfare_services (collected_at);
CREATE INDEX IF NOT EXISTS idx_ws_view_count ON welfare_services (view_count DESC);
CREATE INDEX IF NOT EXISTS idx_ws_search_document_fts
    ON welfare_services
    USING GIN (to_tsvector('simple', lower(
        coalesce(title, '')
        || ' ' || coalesce(description, '')
        || ' ' || coalesce(support_content, '')
        || ' ' || coalesce(keyword, '')
    )));
CREATE INDEX IF NOT EXISTS idx_ws_title_trgm
    ON welfare_services
    USING GIN (lower(title) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_ws_description_trgm
    ON welfare_services
    USING GIN (lower(description) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_ws_support_content_trgm
    ON welfare_services
    USING GIN (lower(support_content) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_ws_keyword_trgm
    ON welfare_services
    USING GIN (lower(keyword) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_ws_search_document_trgm
    ON welfare_services
    USING GIN (lower(
        coalesce(title, '')
        || ' ' || coalesce(description, '')
        || ' ' || coalesce(support_content, '')
        || ' ' || coalesce(keyword, '')
    ) gin_trgm_ops);

CREATE TABLE IF NOT EXISTS raw_api_payloads (
    id           BIGSERIAL PRIMARY KEY,
    source_type  VARCHAR(20) NOT NULL,
    source_id    VARCHAR(50) NOT NULL,
    api_category VARCHAR(10) NOT NULL,
    payload_json TEXT NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    fetched_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_raw_source UNIQUE (source_type, source_id, api_category)
);

CREATE INDEX IF NOT EXISTS idx_raw_fetched ON raw_api_payloads (fetched_at);
CREATE INDEX IF NOT EXISTS idx_raw_hash ON raw_api_payloads (payload_hash);

CREATE TABLE IF NOT EXISTS api_sync_logs (
    id              BIGSERIAL PRIMARY KEY,
    job_name        VARCHAR(50) NOT NULL,
    status          VARCHAR(30) NOT NULL,
    started_at      TIMESTAMP NOT NULL,
    finished_at     TIMESTAMP,
    requested_count INTEGER NOT NULL DEFAULT 0,
    saved_count     INTEGER NOT NULL DEFAULT 0,
    skipped_count   INTEGER NOT NULL DEFAULT 0,
    filtered_count  INTEGER NOT NULL DEFAULT 0,
    failed_count    INTEGER NOT NULL DEFAULT 0,
    error_code      VARCHAR(50),
    error_message   VARCHAR(1000),
    metadata_json   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_api_sync_job_started ON api_sync_logs (job_name, started_at);
CREATE INDEX IF NOT EXISTS idx_api_sync_status_started ON api_sync_logs (status, started_at);

CREATE TABLE IF NOT EXISTS collect_runtime_statuses (
    circuit_key VARCHAR(50) PRIMARY KEY,
    open_until  TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS welfare_service_details (
    id                  BIGSERIAL PRIMARY KEY,
    service_id          BIGINT NOT NULL,
    target_detail       TEXT,
    support_detail      TEXT,
    apply_method_detail TEXT,
    selection_criteria  TEXT,
    contact_list        TEXT,
    support_cycle       VARCHAR(100),
    provision_type      VARCHAR(100),
    homepage_url        VARCHAR(500),
    related_law         VARCHAR(500),
    form_files          TEXT,
    reference_urls_json TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_wsd_service UNIQUE (service_id),
    CONSTRAINT fk_wsd_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS service_regions (
    id          BIGSERIAL PRIMARY KEY,
    service_id  BIGINT NOT NULL,
    region_code VARCHAR(10),
    sido_name   VARCHAR(50),
    sgg_name    VARCHAR(50),
    CONSTRAINT fk_sr_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sr_service ON service_regions (service_id);
CREATE INDEX IF NOT EXISTS idx_sr_region_code ON service_regions (region_code);
CREATE INDEX IF NOT EXISTS idx_sr_sido ON service_regions (sido_name);
CREATE INDEX IF NOT EXISTS idx_sr_service_sido_sgg ON service_regions (service_id, sido_name, sgg_name);
CREATE INDEX IF NOT EXISTS idx_sr_service_region_code ON service_regions (service_id, region_code);

CREATE TABLE IF NOT EXISTS policy_chunks (
    id         BIGSERIAL PRIMARY KEY,
    service_id BIGINT NOT NULL,
    chunk_type VARCHAR(50) NOT NULL,
    chunk_order INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    embedding vector(256),
    embedding_model VARCHAR(100),
    embedding_text_hash VARCHAR(64),
    embedding_updated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_policy_chunk_scope UNIQUE (service_id, chunk_type, chunk_order),
    CONSTRAINT fk_pc_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pc_service_chunk_order ON policy_chunks (service_id, chunk_order);
CREATE INDEX IF NOT EXISTS idx_pc_embedding_updated_at ON policy_chunks (embedding_updated_at);

CREATE TABLE IF NOT EXISTS service_tags (
    id         BIGSERIAL PRIMARY KEY,
    service_id BIGINT NOT NULL,
    tag_type   VARCHAR(20) NOT NULL,
    tag_value  VARCHAR(100) NOT NULL,
    CONSTRAINT uq_st UNIQUE (service_id, tag_type, tag_value),
    CONSTRAINT fk_st_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_st_tag ON service_tags (tag_type, tag_value);

CREATE TABLE IF NOT EXISTS normalization_code_sets (
    id            BIGSERIAL PRIMARY KEY,
    code_set_key  VARCHAR(64) NOT NULL,
    domain_type   VARCHAR(32) NOT NULL,
    source_system VARCHAR(32) NOT NULL,
    description   VARCHAR(255),
    version_label VARCHAR(50),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ncs_code_set_key UNIQUE (code_set_key)
);

CREATE TABLE IF NOT EXISTS normalization_codes (
    id           BIGSERIAL PRIMARY KEY,
    code_set_key VARCHAR(64) NOT NULL,
    code         VARCHAR(64) NOT NULL,
    label        VARCHAR(255) NOT NULL,
    parent_code  VARCHAR(64),
    sort_order   INTEGER NOT NULL DEFAULT 0,
    extra_json   JSONB,
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_norm_code UNIQUE (code_set_key, code),
    CONSTRAINT fk_norm_code_set
        FOREIGN KEY (code_set_key) REFERENCES normalization_code_sets(code_set_key)
);

CREATE INDEX IF NOT EXISTS idx_norm_code_parent ON normalization_codes (code_set_key, parent_code);

CREATE TABLE IF NOT EXISTS service_taxonomies (
    service_id                    BIGINT PRIMARY KEY,
    primary_source_system         VARCHAR(32) NOT NULL,
    compat_unified_category_code  VARCHAR(64),
    compat_unified_category_label VARCHAR(100),
    youth_major_code              VARCHAR(64),
    youth_major_label             VARCHAR(100),
    youth_mid_code                VARCHAR(64),
    youth_mid_label               VARCHAR(100),
    gov24_service_field_code      VARCHAR(64),
    gov24_service_field_label     VARCHAR(100),
    gov24_user_type_code          VARCHAR(64),
    gov24_user_type_label         VARCHAR(100),
    gov24_benefit_type_code       VARCHAR(64),
    gov24_benefit_type_label      VARCHAR(100),
    provision_method_code         VARCHAR(64),
    provision_method_label        TEXT,
    authority                     VARCHAR(32) NOT NULL,
    confidence                    DECIMAL(4, 3),
    created_at                    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stx_service
        FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_stx_compat_category ON service_taxonomies (compat_unified_category_code);
CREATE INDEX IF NOT EXISTS idx_stx_youth_major ON service_taxonomies (youth_major_code);
CREATE INDEX IF NOT EXISTS idx_stx_gov24_field ON service_taxonomies (gov24_service_field_code);

CREATE TABLE IF NOT EXISTS service_taxonomy_terms (
    id           BIGSERIAL PRIMARY KEY,
    service_id   BIGINT NOT NULL,
    term_group   VARCHAR(64) NOT NULL,
    code_set_key VARCHAR(64),
    term_code    VARCHAR(64) NOT NULL DEFAULT '',
    term_label   VARCHAR(255) NOT NULL,
    source_field VARCHAR(100) NOT NULL DEFAULT '',
    authority    VARCHAR(32) NOT NULL,
    sort_order   INTEGER NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_service_term UNIQUE (service_id, term_group, term_code, term_label, authority),
    CONSTRAINT fk_stt_service
        FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE,
    CONSTRAINT fk_stt_code_set
        FOREIGN KEY (code_set_key) REFERENCES normalization_code_sets(code_set_key)
);

CREATE INDEX IF NOT EXISTS idx_stt_service_group ON service_taxonomy_terms (service_id, term_group);
CREATE INDEX IF NOT EXISTS idx_stt_group_code ON service_taxonomy_terms (term_group, term_code);
CREATE INDEX IF NOT EXISTS idx_stt_code_set ON service_taxonomy_terms (code_set_key);

CREATE TABLE IF NOT EXISTS service_facts (
    id                BIGSERIAL PRIMARY KEY,
    service_id        BIGINT NOT NULL,
    fact_group        VARCHAR(64) NOT NULL,
    fact_code_set_key VARCHAR(64),
    fact_code         VARCHAR(64),
    fact_merge_key    VARCHAR(128) NOT NULL,
    fact_label        VARCHAR(255) NOT NULL,
    operator          VARCHAR(16) NOT NULL,
    value_type        VARCHAR(16) NOT NULL,
    bool_value        BOOLEAN,
    int_value         INTEGER,
    decimal_value     DECIMAL(12, 2),
    text_value        VARCHAR(255),
    date_value        DATE,
    range_min_int     INTEGER,
    range_max_int     INTEGER,
    unit              VARCHAR(32),
    source_field      VARCHAR(100) NOT NULL DEFAULT '',
    authority         VARCHAR(32) NOT NULL,
    confidence        DECIMAL(4, 3),
    raw_value         VARCHAR(255),
    evidence_text     TEXT,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_service_fact_merge UNIQUE (service_id, fact_merge_key),
    CONSTRAINT fk_sfact_service
        FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE,
    CONSTRAINT fk_sfact_code_set
        FOREIGN KEY (fact_code_set_key) REFERENCES normalization_code_sets(code_set_key)
);

CREATE INDEX IF NOT EXISTS idx_sfact_service_group ON service_facts (service_id, fact_group);
CREATE INDEX IF NOT EXISTS idx_sfact_group_code ON service_facts (fact_group, fact_code);
CREATE INDEX IF NOT EXISTS idx_sfact_authority_group_code ON service_facts (authority, fact_group, fact_code);
CREATE INDEX IF NOT EXISTS idx_sfact_group_range ON service_facts (fact_group, range_min_int, range_max_int);
CREATE INDEX IF NOT EXISTS idx_sfact_code_set ON service_facts (fact_code_set_key);

CREATE TABLE IF NOT EXISTS service_taxonomy_summary_slots (
    id           BIGSERIAL PRIMARY KEY,
    service_id   BIGINT NOT NULL,
    slot_key     VARCHAR(64) NOT NULL,
    code_set_key VARCHAR(64),
    slot_code    VARCHAR(64) NOT NULL DEFAULT '',
    slot_label   TEXT NOT NULL,
    source_field VARCHAR(100) NOT NULL DEFAULT '',
    authority    VARCHAR(32) NOT NULL,
    confidence   DECIMAL(4, 3),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_service_summary_slot UNIQUE (service_id, slot_key, slot_code, authority),
    CONSTRAINT fk_stss_service
        FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE,
    CONSTRAINT fk_stss_code_set
        FOREIGN KEY (code_set_key) REFERENCES normalization_code_sets(code_set_key)
);

CREATE INDEX IF NOT EXISTS idx_stss_service_slot ON service_taxonomy_summary_slots (service_id, slot_key);
CREATE INDEX IF NOT EXISTS idx_stss_slot_code ON service_taxonomy_summary_slots (slot_key, slot_code);
CREATE INDEX IF NOT EXISTS idx_stss_code_set ON service_taxonomy_summary_slots (code_set_key);

CREATE TABLE IF NOT EXISTS score_weights (
    id            BIGSERIAL PRIMARY KEY,
    weight_key    VARCHAR(50) NOT NULL,
    rule_weight   DECIMAL(3, 2) NOT NULL,
    ai_weight     DECIMAL(3, 2) NOT NULL,
    min_log_count INTEGER NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    description   VARCHAR(200),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_sw_key UNIQUE (weight_key)
);

CREATE TABLE IF NOT EXISTS user_recommendations (
    id                  BIGSERIAL PRIMARY KEY,
    user_key            VARCHAR(32) NOT NULL,
    service_id          BIGINT NOT NULL,
    recommended_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rule_base_score     DECIMAL(7, 2),
    rule_weighted_score DECIMAL(7, 2),
    ai_score            DECIMAL(5, 2),
    ai_reason           VARCHAR(500),
    ai_status           VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUESTED',
    rule_weight_used    DECIMAL(3, 2),
    ai_weight_used      DECIMAL(3, 2),
    final_score         DECIMAL(6, 5) NOT NULL DEFAULT 0,
    is_bookmarked       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ur_user_key_service_time UNIQUE (user_key, service_id, recommended_at),
    CONSTRAINT fk_ur_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ur_user_key_score ON user_recommendations (user_key, final_score DESC);
CREATE INDEX IF NOT EXISTS idx_ur_recommended ON user_recommendations (recommended_at);
CREATE INDEX IF NOT EXISTS idx_ur_user_key_bookmark ON user_recommendations (user_key, is_bookmarked);

CREATE TABLE IF NOT EXISTS recommendation_logs (
    id               BIGSERIAL PRIMARY KEY,
    user_key         VARCHAR(32) NOT NULL,
    service_id       BIGINT NOT NULL,
    notification_id  BIGINT,
    final_score      DECIMAL(6, 5),
    rule_weight_used DECIMAL(3, 2),
    ai_weight_used   DECIMAL(3, 2),
    is_fallback      BOOLEAN NOT NULL DEFAULT FALSE,
    is_clicked       BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    clicked_at       TIMESTAMP,
    CONSTRAINT fk_rl_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_rl_user_key_sent ON recommendation_logs (user_key, sent_at);
CREATE INDEX IF NOT EXISTS idx_rl_service ON recommendation_logs (service_id);

CREATE TABLE IF NOT EXISTS service_view_logs (
    id                 BIGSERIAL PRIMARY KEY,
    service_id         BIGINT NOT NULL,
    user_key           VARCHAR(32),
    client_fingerprint VARCHAR(64) NOT NULL,
    viewed_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_svl_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_svl_service_viewed ON service_view_logs (service_id, viewed_at);
CREATE INDEX IF NOT EXISTS idx_svl_viewed_service ON service_view_logs (viewed_at, service_id);
CREATE INDEX IF NOT EXISTS idx_svl_user_key_service_viewed ON service_view_logs (user_key, service_id, viewed_at);
CREATE INDEX IF NOT EXISTS idx_svl_fp_service_viewed ON service_view_logs (client_fingerprint, service_id, viewed_at);

CREATE TABLE IF NOT EXISTS recent_policy_views (
    id             BIGSERIAL PRIMARY KEY,
    user_key       VARCHAR(32) NOT NULL,
    service_id     BIGINT NOT NULL,
    last_viewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_recent_policy_views_user_service UNIQUE (user_key, service_id),
    CONSTRAINT fk_rpv_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_rpv_user_key_last_viewed ON recent_policy_views (user_key, last_viewed_at);
CREATE INDEX IF NOT EXISTS idx_rpv_service ON recent_policy_views (service_id);

CREATE TABLE IF NOT EXISTS policy_error_reports (
    id          BIGSERIAL PRIMARY KEY,
    policy_id   BIGINT NOT NULL,
    user_id     BIGINT,
    user_key    VARCHAR(32),
    reason_code VARCHAR(40) NOT NULL,
    note        VARCHAR(1000),
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_per_policy FOREIGN KEY (policy_id) REFERENCES welfare_services(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_per_policy_created ON policy_error_reports (policy_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_per_status_created ON policy_error_reports (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_per_user_key_created ON policy_error_reports (user_key, created_at DESC);

CREATE TABLE IF NOT EXISTS collect_execution_locks (
    lock_name    VARCHAR(100) PRIMARY KEY,
    owner_token  VARCHAR(64) NOT NULL,
    locked_until TIMESTAMP NOT NULL,
    acquired_at  TIMESTAMP NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cel_locked_until ON collect_execution_locks (locked_until);

CREATE TABLE IF NOT EXISTS search_logs (
    id                 BIGSERIAL PRIMARY KEY,
    user_key           VARCHAR(32),
    client_fingerprint VARCHAR(64) NOT NULL,
    keyword            VARCHAR(255) NOT NULL,
    result_count       BIGINT NOT NULL,
    status_filter      VARCHAR(16),
    include_closed     BOOLEAN NOT NULL DEFAULT FALSE,
    category           VARCHAR(64),
    source_type        VARCHAR(32),
    online_apply       BOOLEAN,
    sido               VARCHAR(64),
    sgg                VARCHAR(64),
    sort_key           VARCHAR(16),
    page_number        INTEGER NOT NULL,
    page_size          INTEGER NOT NULL,
    searched_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sl_searched ON search_logs (searched_at);
CREATE INDEX IF NOT EXISTS idx_sl_user_key_searched ON search_logs (user_key, searched_at);
CREATE INDEX IF NOT EXISTS idx_sl_keyword_searched ON search_logs (keyword, searched_at);

CREATE TABLE IF NOT EXISTS notifications (
    id             BIGSERIAL PRIMARY KEY,
    user_key       VARCHAR(32) NOT NULL,
    dispatch_key   VARCHAR(80),
    channel        VARCHAR(16) NOT NULL,
    period_type    VARCHAR(16) NOT NULL,
    status         VARCHAR(16) NOT NULL,
    subject        VARCHAR(200) NOT NULL,
    message_text   TEXT,
    total_services INTEGER NOT NULL DEFAULT 0,
    sent_at        TIMESTAMP,
    error_message  VARCHAR(500),
    retry_count    INTEGER NOT NULL DEFAULT 0,
    next_retry_at  TIMESTAMP,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_noti_dispatch_key UNIQUE (dispatch_key)
);

CREATE INDEX IF NOT EXISTS idx_noti_user_key_created ON notifications (user_key, created_at);
CREATE INDEX IF NOT EXISTS idx_noti_status_created ON notifications (status, created_at);
CREATE INDEX IF NOT EXISTS idx_noti_retry ON notifications (status, next_retry_at);

CREATE TABLE IF NOT EXISTS user_alerts (
    id           BIGSERIAL PRIMARY KEY,
    user_key     VARCHAR(32) NOT NULL,
    event_key    VARCHAR(120),
    kind         VARCHAR(32) NOT NULL,
    status       VARCHAR(16) NOT NULL,
    title        VARCHAR(200) NOT NULL,
    body         TEXT,
    deeplink_url VARCHAR(500),
    metadata_json TEXT,
    read_at      TIMESTAMP,
    hidden_at    TIMESTAMP,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_alert_event_key UNIQUE (event_key)
);

CREATE INDEX IF NOT EXISTS idx_ua_user_key_created ON user_alerts (user_key, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ua_user_key_status_created ON user_alerts (user_key, status, created_at DESC);

CREATE TABLE IF NOT EXISTS web_push_subscriptions (
    id                 BIGSERIAL PRIMARY KEY,
    user_key           VARCHAR(32) NOT NULL,
    endpoint           VARCHAR(500) NOT NULL,
    p256dh             VARCHAR(255) NOT NULL,
    auth_secret        VARCHAR(255) NOT NULL,
    user_agent         VARCHAR(500),
    device_label       VARCHAR(100),
    enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at       TIMESTAMP,
    last_sent_at       TIMESTAMP,
    last_error_at      TIMESTAMP,
    last_error_message VARCHAR(500),
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_wps_endpoint UNIQUE (endpoint)
);

CREATE INDEX IF NOT EXISTS idx_wps_user_key_enabled_created
    ON web_push_subscriptions (user_key, enabled, created_at DESC);

CREATE TABLE IF NOT EXISTS notification_services (
    id                    BIGSERIAL PRIMARY KEY,
    notification_id       BIGINT NOT NULL,
    service_id            BIGINT NOT NULL,
    recommendation_log_id BIGINT,
    rank_order            INTEGER NOT NULL,
    final_score           DECIMAL(6, 5),
    service_title         VARCHAR(255),
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ns_notification FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE,
    CONSTRAINT fk_ns_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE,
    CONSTRAINT fk_ns_log FOREIGN KEY (recommendation_log_id) REFERENCES recommendation_logs(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_ns_notification ON notification_services (notification_id);
CREATE INDEX IF NOT EXISTS idx_ns_service ON notification_services (service_id);
CREATE INDEX IF NOT EXISTS idx_ns_log ON notification_services (recommendation_log_id);

CREATE TABLE IF NOT EXISTS cluster_ai_results (
    id         BIGSERIAL PRIMARY KEY,
    cluster_id VARCHAR(50) NOT NULL,
    service_id BIGINT NOT NULL,
    ai_score   DECIMAL(5, 2) NOT NULL,
    ai_reason  VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_car UNIQUE (cluster_id, service_id),
    CONSTRAINT fk_car_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_car_cluster ON cluster_ai_results (cluster_id);

CREATE TABLE IF NOT EXISTS chat_sessions (
    id              BIGSERIAL PRIMARY KEY,
    user_key        VARCHAR(32) NOT NULL,
    title           VARCHAR(100),
    last_message_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    context_state_json TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cs_user_key_last_message ON chat_sessions (user_key, last_message_at);
CREATE INDEX IF NOT EXISTS idx_cs_user_key_created ON chat_sessions (user_key, created_at);

CREATE TABLE IF NOT EXISTS chat_messages (
    id                     BIGSERIAL PRIMARY KEY,
    session_id             BIGINT NOT NULL,
    role                   VARCHAR(16) NOT NULL,
    content                TEXT NOT NULL,
    referenced_service_ids TEXT,
    references_json        TEXT,
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cm_session FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_cm_session_created ON chat_messages (session_id, created_at);

CREATE TABLE IF NOT EXISTS chat_retrieval_snapshots (
    id                          BIGSERIAL PRIMARY KEY,
    snapshot_type               VARCHAR(20) NOT NULL,
    scenario_key                VARCHAR(100),
    session_id                  BIGINT,
    user_key                    VARCHAR(32),
    question                    TEXT NOT NULL,
    normalized_keyword          VARCHAR(500),
    search_keyword              VARCHAR(500),
    branch_key                  VARCHAR(100),
    preferred_category          VARCHAR(100),
    preferred_terms_json        TEXT,
    branch_suggestion_keys_json TEXT,
    fts_service_ids_json        TEXT,
    semantic_service_ids_json   TEXT,
    merged_service_ids_json     TEXT,
    fallback_strategy           VARCHAR(40),
    needs_clarification         BOOLEAN,
    result_count                INTEGER NOT NULL DEFAULT 0,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_crs_snapshot_type_created
    ON chat_retrieval_snapshots (snapshot_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_crs_scenario_key_created
    ON chat_retrieval_snapshots (scenario_key, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_crs_user_key_created
    ON chat_retrieval_snapshots (user_key, created_at DESC);

INSERT INTO priority_options (code, label) VALUES
('HOUSING', '주거'),
('JOB', '일자리'),
('EDUCATION', '교육·직업훈련'),
('FINANCE', '금융·생활'),
('CULTURE', '문화·여가'),
('DEADLINE', '마감임박'),
('PARTICIPATION', '참여·기회'),
('FAMILY', '가족·돌봄')
ON CONFLICT (code) DO NOTHING;

INSERT INTO score_weights (weight_key, rule_weight, ai_weight, min_log_count, description) VALUES
('COLD_START', 0.80, 0.20, 0, '추천 이력 100건 미만: rule 우선'),
('GROWTH', 0.60, 0.40, 100, '추천 이력 100건 이상: AI 점진 반영'),
('STABLE', 0.40, 0.60, 500, 'SRS 목표값: AI 우선')
ON CONFLICT (weight_key) DO NOTHING;

INSERT INTO normalization_code_sets (
    code_set_key,
    domain_type,
    source_system,
    description,
    version_label,
    is_active
) VALUES
('SYSTEM_COMPAT_UNIFIED_CATEGORY', 'TAXONOMY', 'SYSTEM', '현재 추천/응답 unifiedCategory 호환 코드', 'draft-2026-04-30', TRUE),
('YOUTH_MAJOR', 'TAXONOMY', 'YOUTH', '온통청년 정책 대분류', 'draft-2026-04-30', TRUE),
('YOUTH_MID', 'TAXONOMY', 'YOUTH', '온통청년 정책 중분류', 'draft-2026-04-30', TRUE),
('YOUTH_KEYWORD', 'TAXONOMY', 'YOUTH', '온통청년 정책 키워드', 'draft-2026-04-30', TRUE),
('GOV24_SERVICE_FIELD', 'TAXONOMY', 'GOV24', 'Gov24 서비스 분야', 'draft-2026-04-30', TRUE),
('GOV24_USER_TYPE', 'TAXONOMY', 'GOV24', 'Gov24 사용자 구분', 'draft-2026-04-30', TRUE),
('GOV24_BENEFIT_TYPE', 'TAXONOMY', 'GOV24', 'Gov24 지원 유형', 'draft-2026-04-30', TRUE),
('GOV24_USER_TYPE_TOKEN', 'TAXONOMY', 'GOV24', 'Gov24 사용자 구분 token', 'draft-2026-05-18', TRUE),
('GOV24_BENEFIT_TYPE_TOKEN', 'TAXONOMY', 'GOV24', 'Gov24 지원 유형 token', 'draft-2026-05-18', TRUE),
('GOV24_SUPPORT_CONDITION', 'FACT', 'GOV24', 'Gov24 지원 조건 코드', 'draft-2026-04-30', TRUE),
('YOUTH_PROVIDER_GROUP', 'TAXONOMY', 'YOUTH', '온통청년 제공기관 그룹코드', 'draft-2026-04-30-youth-codeinfo', TRUE),
('YOUTH_PROVISION_METHOD', 'TAXONOMY', 'YOUTH', '온통청년 정책제공방법코드', 'draft-2026-04-30-youth-codeinfo', TRUE),
('YOUTH_EMPLOYMENT_REQUIREMENT', 'FACT', 'YOUTH', '온통청년 정책취업요건코드', 'draft-2026-04-30-youth-codeinfo', TRUE),
('YOUTH_EDUCATION_REQUIREMENT', 'FACT', 'YOUTH', '온통청년 정책학력요건코드', 'draft-2026-04-30-youth-codeinfo', TRUE),
('YOUTH_SPECIAL_REQUIREMENT', 'FACT', 'YOUTH', '온통청년 정책특화요건코드', 'draft-2026-04-30-youth-codeinfo', TRUE),
('YOUTH_MARITAL_STATUS', 'FACT', 'YOUTH', '온통청년 결혼상태코드', 'draft-2026-04-30-youth-codeinfo', TRUE),
('YOUTH_INCOME_CONDITION_TYPE', 'FACT', 'YOUTH', '온통청년 소득조건구분코드', 'draft-2026-04-30-youth-codeinfo', TRUE),
('LOCAL_HOUSE_TENURE_TYPE', 'TAXONOMY', 'LOCAL_OFFICIAL_CODEBOOK', '사용자 제공 가옥(주거형태) 코드', 'local-user-provided-2026-06-02', TRUE),
('LOCAL_FAMILY_RELATIONSHIP', 'TAXONOMY', 'LOCAL_OFFICIAL_CODEBOOK', '사용자 제공 가족관계 코드', 'local-user-provided-2026-06-02', TRUE),
('LOCAL_BUILDING_USAGE', 'TAXONOMY', 'LOCAL_OFFICIAL_CODEBOOK', '사용자 제공 건물용도 코드', 'local-user-provided-2026-06-02', TRUE),
('LOCAL_LEGAL_BASIS_TYPE', 'TAXONOMY', 'LOCAL_OFFICIAL_CODEBOOK', '사용자 제공 근거법령 코드', 'local-user-provided-2026-06-02', TRUE),
('LOCAL_BASIC_LIVING_RECIPIENT_TYPE', 'FACT', 'LOCAL_OFFICIAL_CODEBOOK', '사용자 제공 기초생활수급권자 코드', 'local-user-provided-2026-06-02', TRUE),
('LOCAL_VETERAN_TARGET_TYPE', 'FACT', 'LOCAL_OFFICIAL_CODEBOOK', '사용자 제공 보훈대상자 코드', 'local-user-provided-2026-06-02', TRUE),
('LOCAL_DISABILITY_GRADE', 'FACT', 'LOCAL_OFFICIAL_CODEBOOK', '사용자 제공 장애등급 코드', 'local-user-provided-2026-06-02', TRUE),
('LOCAL_HOUSING_TYPE', 'TAXONOMY', 'LOCAL_OFFICIAL_CODEBOOK', '사용자 제공 주택유형구분 코드', 'local-user-provided-2026-06-02', TRUE),
('LOCAL_JOB_GROUP', 'TAXONOMY', 'LOCAL_OFFICIAL_CODEBOOK', '사용자 제공 직군 코드', 'local-user-provided-2026-06-02', TRUE),
('LOCAL_JOB_SERIES', 'TAXONOMY', 'LOCAL_OFFICIAL_CODEBOOK', '사용자 제공 직렬 코드', 'local-user-provided-2026-06-02', TRUE),
('LOCAL_JOB_TYPE', 'TAXONOMY', 'LOCAL_OFFICIAL_CODEBOOK', '사용자 제공 직종 코드', 'local-user-provided-2026-06-02', TRUE),
('LOCAL_JOB_SUBTYPE', 'TAXONOMY', 'LOCAL_OFFICIAL_CODEBOOK', '사용자 제공 직종세분류 코드', 'local-user-provided-2026-06-02', TRUE),
('LOCAL_LEGAL_DISTRICT', 'TAXONOMY', 'LOCAL_OFFICIAL_CODEBOOK', '사용자 제공 법정동 전체자료 reference', 'local-user-provided-2026-06-02', TRUE),
('LOCAL_ADMIN_INSTITUTION', 'TAXONOMY', 'LOCAL_OFFICIAL_CODEBOOK', '사용자 제공 기관코드 전체자료 reference', 'local-user-provided-2026-06-02', TRUE),
('LOCAL_ADMIN_INSTITUTION_WITH_TYPE_MEANING', 'TAXONOMY', 'LOCAL_OFFICIAL_CODEBOOK', '사용자 제공 기관코드 전체자료(유형분류 의미추가) reference', 'local-user-provided-2026-06-02', TRUE)
ON CONFLICT (code_set_key) DO UPDATE SET
    domain_type = EXCLUDED.domain_type,
    source_system = EXCLUDED.source_system,
    description = EXCLUDED.description,
    version_label = EXCLUDED.version_label,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;

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
