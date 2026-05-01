-- ============================================================
-- 청년복지 통합 플랫폼 — 현재 스키마 (main + pii split tables 포함)
-- MySQL 8.0+ / FULLTEXT ngram
-- 실행 순서: FK 의존성 고려
-- ============================================================

CREATE DATABASE IF NOT EXISTS youth_welfare_pii
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- 1. priority_options (마스터, 의존 없음)
CREATE TABLE IF NOT EXISTS priority_options (
    id          TINYINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(30)  NOT NULL,
    label       VARCHAR(50)  NOT NULL,
    description VARCHAR(200),
    PRIMARY KEY (id),
    UNIQUE KEY uq_po_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. users
CREATE TABLE IF NOT EXISTS users (
    id                      BIGINT           NOT NULL AUTO_INCREMENT,
    user_key                CHAR(32)         NOT NULL DEFAULT (REPLACE(UUID(), '-', '')),
    email                   VARCHAR(255)     NOT NULL,
    password_hash           VARCHAR(255)     NOT NULL,
    name                    VARCHAR(50),
    birth_date              DATE,
    phone_enc               VARCHAR(512),
    sido                    VARCHAR(50),
    sgg                     VARCHAR(50),
    region_code             VARCHAR(20),
    income_level            TINYINT UNSIGNED,
    household_type          VARCHAR(30),
    employment_status       VARCHAR(30),
    is_active               TINYINT(1)       NOT NULL DEFAULT 1,
    notification_yn         TINYINT(1)       NOT NULL DEFAULT 0,
    notification_period     ENUM('DAILY','WEEKLY','NONE') DEFAULT 'NONE',
    notification_min_score  DOUBLE           DEFAULT 0.5,
    notification_consent_at DATETIME,
    login_fail_count        INT NOT NULL DEFAULT 0,
    locked_until            DATETIME,
    display_count           INT NOT NULL DEFAULT 10,
    profile_completeness    INT DEFAULT 0,
    withdrawn_at            DATETIME,
    created_at              DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_user_key (user_key),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. user_attributes
-- attr_type: VARCHAR(30), ENUM 아님. 유효성 검증은 Java Enum으로.
CREATE TABLE IF NOT EXISTS auth_users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_key          CHAR(32)     NOT NULL,
    email_lookup_hash CHAR(64)     NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    is_active         TINYINT(1)   NOT NULL DEFAULT 1,
    login_fail_count  INT          NOT NULL DEFAULT 0,
    locked_until      DATETIME,
    withdrawn_at      DATETIME,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_au_user_key (user_key),
    UNIQUE KEY uq_au_email_lookup_hash (email_lookup_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_profiles (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    user_key                CHAR(32)     NOT NULL,
    age                     INT,
    age_band                VARCHAR(20),
    age_calculated_at       DATETIME,
    sido                    VARCHAR(50),
    sgg                     VARCHAR(50),
    region_code             VARCHAR(20),
    income_level            TINYINT UNSIGNED,
    household_type          VARCHAR(30),
    employment_status       VARCHAR(30),
    notification_yn         TINYINT(1)   NOT NULL DEFAULT 0,
    notification_period     ENUM('DAILY','WEEKLY','NONE') DEFAULT 'NONE',
    notification_min_score  DOUBLE       DEFAULT 0.5,
    notification_consent_at DATETIME,
    display_count           INT          NOT NULL DEFAULT 10,
    profile_completeness    INT          DEFAULT 0,
    has_name                TINYINT(1)   NOT NULL DEFAULT 0,
    has_birth_date          TINYINT(1)   NOT NULL DEFAULT 0,
    has_phone               TINYINT(1)   NOT NULL DEFAULT 0,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_upf_user_key (user_key),
    KEY idx_upf_notification (notification_yn, notification_period),
    KEY idx_upf_region (sido, sgg),
    KEY idx_upf_income_employment (income_level, employment_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS youth_welfare_pii.user_pii (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_key       CHAR(32)     NOT NULL,
    email_enc      VARCHAR(512),
    name_enc       VARCHAR(512),
    birth_date_enc VARCHAR(128),
    phone_enc      VARCHAR(512),
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_upii_user_key (user_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_pii_sync_queue (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_key         CHAR(32)     NOT NULL,
    email_enc        VARCHAR(512),
    name_enc         VARCHAR(512),
    birth_date_enc   VARCHAR(128),
    phone_enc        VARCHAR(512),
    status           ENUM('PENDING','SYNCED','FAILED') NOT NULL DEFAULT 'PENDING',
    attempt_count    INT          NOT NULL DEFAULT 0,
    last_enqueued_at DATETIME,
    last_attempt_at  DATETIME,
    last_synced_at   DATETIME,
    last_error       VARCHAR(500),
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_upsq_user_key (user_key),
    KEY idx_upsq_status_enqueued (status, last_enqueued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_attributes (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    user_key   CHAR(32),
    attr_type  VARCHAR(30)  NOT NULL,
    attr_value VARCHAR(100) NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ua_user      (user_id),
    KEY idx_ua_user_key  (user_key),
    KEY idx_ua_attr_type (attr_type),
    KEY idx_ua_user_key_attr_type (user_key, attr_type),
    CONSTRAINT fk_ua_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. user_priorities
CREATE TABLE IF NOT EXISTS user_priorities (
    id                 BIGINT           NOT NULL AUTO_INCREMENT,
    user_id            BIGINT           NOT NULL,
    user_key           CHAR(32),
    priority_option_id TINYINT UNSIGNED NOT NULL,
    priority_rank      INT NOT NULL,
    weight             DOUBLE           NOT NULL,
    created_at         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_up_user_option (user_id, priority_option_id),
    KEY idx_up_user (user_id),
    KEY idx_up_user_key_rank (user_key, priority_rank),
    CONSTRAINT fk_up_user   FOREIGN KEY (user_id)            REFERENCES users(id)           ON DELETE CASCADE,
    CONSTRAINT fk_up_option FOREIGN KEY (priority_option_id) REFERENCES priority_options(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. welfare_services
-- ⚠️ ai_score 없음. AI 점수는 user_recommendations에만 존재.
CREATE TABLE IF NOT EXISTS welfare_services (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    source_type       ENUM('YOUTH','BOKJIRO_CENTRAL','BOKJIRO_LOCAL') NOT NULL,
    source_id         VARCHAR(50) NOT NULL,
    title             VARCHAR(255) NOT NULL,
    description       TEXT,
    support_content   TEXT,
    category_main     VARCHAR(100),
    category_sub      VARCHAR(100),
    keyword           VARCHAR(200),
    min_age           INT,
    max_age           INT,
    min_income        INT,
    max_income        INT,
    apply_start_date  DATE,
    apply_end_date    DATE,
    start_date        DATE,
    end_date          DATE,
    life_stage        VARCHAR(200),
    support_cycle     VARCHAR(50),
    provision_type    VARCHAR(100),
    apply_method_name TEXT,
    is_online_apply   TINYINT(1)  DEFAULT 0,
    host_org          VARCHAR(200),
    operating_org     VARCHAR(200),
    contact           VARCHAR(100),
    detail_url        VARCHAR(500),
    unified_category  VARCHAR(50),
    is_youth_specific TINYINT(1)  NOT NULL DEFAULT 0,
    search_youth_relevant TINYINT(1) NOT NULL DEFAULT 1,
    status            ENUM('ACTIVE','UPCOMING','CLOSED') NOT NULL DEFAULT 'ACTIVE',
    api_view_count    BIGINT NOT NULL DEFAULT 0,
    view_count        INT UNSIGNED NOT NULL DEFAULT 0,
    collected_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    registered_at     DATETIME,
    last_modified_at  DATETIME,
    created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ws_source    (source_type, source_id),
    KEY idx_ws_status          (status),
    KEY idx_ws_unified_cat     (unified_category),
    KEY idx_ws_search_youth    (search_youth_relevant),
    KEY idx_ws_source_type     (source_type),
    KEY idx_ws_age             (min_age, max_age),
    KEY idx_ws_end_date        (end_date),
    KEY idx_ws_apply_end       (apply_end_date),
    KEY idx_ws_collected       (collected_at),
    KEY idx_ws_view_count      (view_count DESC),
    FULLTEXT KEY ft_ws_search  (title, description, support_content, keyword)
        WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- raw_api_payloads
-- 원문 API payload 보관용. 정규화 규칙 변경 시 원본 재호출 없이 재처리할 수 있다.
CREATE TABLE IF NOT EXISTS raw_api_payloads (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    source_type  ENUM('YOUTH','BOKJIRO_CENTRAL','BOKJIRO_LOCAL') NOT NULL,
    source_id    VARCHAR(50) NOT NULL,
    api_category ENUM('LIST','DETAIL') NOT NULL,
    payload_json LONGTEXT NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    fetched_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_raw_source (source_type, source_id, api_category),
    KEY idx_raw_fetched (fetched_at),
    KEY idx_raw_hash (payload_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- api_sync_logs
-- 수집 실행 결과 보관용. source별 성공/부분성공/실패와 저장 건수를 추적한다.
CREATE TABLE IF NOT EXISTS api_sync_logs (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    job_name        VARCHAR(50) NOT NULL,
    status          VARCHAR(30) NOT NULL,
    started_at      DATETIME    NOT NULL,
    finished_at     DATETIME,
    requested_count INT         NOT NULL DEFAULT 0,
    saved_count     INT         NOT NULL DEFAULT 0,
    skipped_count   INT         NOT NULL DEFAULT 0,
    filtered_count  INT         NOT NULL DEFAULT 0,
    failed_count    INT         NOT NULL DEFAULT 0,
    error_code      VARCHAR(50),
    error_message   VARCHAR(1000),
    metadata_json   LONGTEXT,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_api_sync_job_started (job_name, started_at),
    KEY idx_api_sync_status_started (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. welfare_service_details (1:1)
CREATE TABLE IF NOT EXISTS welfare_service_details (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    service_id          BIGINT NOT NULL,
    target_detail       TEXT,
    support_detail      TEXT,
    apply_method_detail TEXT,
    selection_criteria  TEXT,
    contact_list        JSON,
    support_cycle       VARCHAR(100),
    provision_type      VARCHAR(100),
    homepage_url        VARCHAR(500),
    related_law         VARCHAR(500),
    form_files          JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_wsd_service (service_id),
    CONSTRAINT fk_wsd_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. service_regions
CREATE TABLE IF NOT EXISTS service_regions (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    service_id  BIGINT      NOT NULL,
    region_code VARCHAR(10),
    sido_name   VARCHAR(50),
    sgg_name    VARCHAR(50),
    PRIMARY KEY (id),
    KEY idx_sr_service     (service_id),
    KEY idx_sr_region_code (region_code),
    KEY idx_sr_sido        (sido_name),
    KEY idx_sr_service_sido_sgg (service_id, sido_name, sgg_name),
    KEY idx_sr_service_region_code (service_id, region_code),
    CONSTRAINT fk_sr_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. service_tags
-- ⚠️ 삽입 시 반드시 UPSERT. 중복 삽입 → rule_base_score 이중합산 버그.
CREATE TABLE IF NOT EXISTS service_tags (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    service_id BIGINT       NOT NULL,
    tag_type   ENUM('INTEREST_THEME','TARGET_GROUP','LIFE_STAGE','KEYWORD') NOT NULL,
    tag_value  VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_st     (service_id, tag_type, tag_value),
    KEY        idx_st_tag (tag_type, tag_value),
    CONSTRAINT fk_st_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. score_weights (Cold Start 가중치)
CREATE TABLE IF NOT EXISTS score_weights (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    weight_key    VARCHAR(50)  NOT NULL,
    rule_weight   DECIMAL(3,2) NOT NULL,
    ai_weight     DECIMAL(3,2) NOT NULL,
    min_log_count INT NOT NULL,
    is_active     TINYINT(1)   NOT NULL DEFAULT 1,
    description   VARCHAR(200),
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_sw_key (weight_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. user_recommendations
-- AI 점수·이유는 여기에만 존재. ai_score NULL = AI 미실행 → rule만 사용.
CREATE TABLE IF NOT EXISTS user_recommendations (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_key            CHAR(32)     NOT NULL,
    service_id          BIGINT       NOT NULL,
    recommended_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rule_base_score     DECIMAL(7,2),
    rule_weighted_score DECIMAL(7,2),
    ai_score            DECIMAL(5,2),
    ai_reason           VARCHAR(500),
    rule_weight_used    DECIMAL(3,2),
    ai_weight_used      DECIMAL(3,2),
    final_score         DECIMAL(6,5) NOT NULL DEFAULT 0,
    is_bookmarked       TINYINT(1)   NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ur_user_key_service_time (user_key, service_id, recommended_at),
    KEY idx_ur_user_key_score (user_key, final_score DESC),
    KEY idx_ur_recommended (recommended_at),
    KEY idx_ur_user_key_bookmark (user_key, is_bookmarked),
    CONSTRAINT fk_ur_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 11. recommendation_logs (영구 보관 — CTR + 가중치 단계 분석)
CREATE TABLE IF NOT EXISTS recommendation_logs (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_key         CHAR(32)    NOT NULL,
    service_id       BIGINT      NOT NULL,
    notification_id  BIGINT,
    final_score      DECIMAL(6,5),
    rule_weight_used DECIMAL(3,2),
    ai_weight_used   DECIMAL(3,2),
    is_fallback      TINYINT(1)  NOT NULL DEFAULT 0,
    is_clicked       TINYINT(1)  NOT NULL DEFAULT 0,
    sent_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    clicked_at       DATETIME,
    PRIMARY KEY (id),
    KEY idx_rl_user_key_sent (user_key, sent_at),
    KEY idx_rl_service (service_id),
    CONSTRAINT fk_rl_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 12. service_view_logs (조회수 중복 방지용 로그)
CREATE TABLE IF NOT EXISTS service_view_logs (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    service_id         BIGINT       NOT NULL,
    user_key           CHAR(32),
    client_fingerprint VARCHAR(64)  NOT NULL,
    viewed_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_svl_service_viewed (service_id, viewed_at),
    KEY idx_svl_user_key_service_viewed (user_key, service_id, viewed_at),
    KEY idx_svl_fp_service_viewed (client_fingerprint, service_id, viewed_at),
    CONSTRAINT fk_svl_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 13. notifications (알림 발송 이력 헤더)
CREATE TABLE IF NOT EXISTS notifications (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_key       CHAR(32)     NOT NULL,
    channel        ENUM('email','kakao') NOT NULL,
    period_type    ENUM('daily','weekly','manual') NOT NULL,
    status         ENUM('sent','failed') NOT NULL,
    subject        VARCHAR(200) NOT NULL,
    message_text   TEXT,
    total_services INT          NOT NULL DEFAULT 0,
    sent_at        DATETIME,
    error_message  VARCHAR(500),
    retry_count    INT          NOT NULL DEFAULT 0,
    next_retry_at  DATETIME,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_noti_user_key_created (user_key, created_at),
    KEY idx_noti_status_created (status, created_at),
    KEY idx_noti_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 14. notification_services (알림-정책 매핑)
CREATE TABLE IF NOT EXISTS notification_services (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    notification_id       BIGINT       NOT NULL,
    service_id            BIGINT       NOT NULL,
    recommendation_log_id BIGINT,
    rank_order            INT          NOT NULL,
    final_score           DECIMAL(6,5),
    service_title         VARCHAR(255),
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ns_notification (notification_id),
    KEY idx_ns_service (service_id),
    KEY idx_ns_log (recommendation_log_id),
    CONSTRAINT fk_ns_notification FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE,
    CONSTRAINT fk_ns_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE,
    CONSTRAINT fk_ns_log FOREIGN KEY (recommendation_log_id) REFERENCES recommendation_logs(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 15. cluster_ai_results (군집별 AI 점수 캐시 — 2차)
CREATE TABLE IF NOT EXISTS cluster_ai_results (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    cluster_id    VARCHAR(50)     NOT NULL,
    service_id    BIGINT          NOT NULL,
    ai_score      DECIMAL(5,2)    NOT NULL,
    ai_reason     VARCHAR(500)    NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_car (cluster_id, service_id),
    KEY idx_car_cluster (cluster_id),
    CONSTRAINT fk_car_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 16. chat_sessions (챗 세션 헤더)
CREATE TABLE IF NOT EXISTS chat_sessions (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_key        CHAR(32)     NOT NULL,
    title           VARCHAR(100),
    last_message_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_cs_user_key_last_message (user_key, last_message_at),
    KEY idx_cs_user_key_created (user_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 17. chat_messages (챗 세션 메시지)
CREATE TABLE IF NOT EXISTS chat_messages (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    session_id             BIGINT       NOT NULL,
    role                   ENUM('USER','ASSISTANT','SYSTEM') NOT NULL,
    content                TEXT         NOT NULL,
    referenced_service_ids JSON,
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_cm_session_created (session_id, created_at),
    CONSTRAINT fk_cm_session FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 초기 데이터
-- ============================================================

INSERT IGNORE INTO priority_options (code, label) VALUES
('HOUSING',       '주거'),
('JOB',           '일자리'),
('EDUCATION',     '교육·직업훈련'),
('FINANCE',       '금융·생활'),
('CULTURE',       '문화·여가'),
('DEADLINE',      '마감임박'),
('PARTICIPATION', '참여·기회'),
('FAMILY',        '가족·돌봄');

INSERT IGNORE INTO score_weights (weight_key, rule_weight, ai_weight, min_log_count, description) VALUES
('COLD_START', 0.80, 0.20,   0, '추천 이력 100건 미만: rule 우선'),
('GROWTH',     0.60, 0.40, 100, '추천 이력 100건 이상: AI 점진 반영'),
('STABLE',     0.40, 0.60, 500, 'SRS 목표값: AI 우선');
