# DB 스키마 — 전체 DDL

> MySQL 8.0+. FULLTEXT ngram 사용.
> **1차 11개 즉시 생성 / 2차 9개는 1차에서 의존 금지.**

---

## 1차 구현 (11개)

### 1. users
```sql
CREATE TABLE users (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    email                   VARCHAR(255)    NOT NULL,
    password_hash           VARCHAR(255)    NOT NULL,               -- BCrypt
    name                    VARCHAR(50),
    birth_date              DATE,
    phone_enc               VARCHAR(512),                           -- AES-256 암호화
    sido                    VARCHAR(50),
    sgg                     VARCHAR(50),
    region_code             VARCHAR(20),                            -- 온통청년 지역코드 매핑용
    income_level            TINYINT UNSIGNED,                       -- 1~10분위
    household_type          VARCHAR(30),
    employment_status       VARCHAR(30),
    is_active               TINYINT(1)      NOT NULL DEFAULT 1,
    notification_yn         TINYINT(1)      NOT NULL DEFAULT 0,
    notification_period     ENUM('DAILY','WEEKLY','NONE') DEFAULT 'NONE',
    notification_min_score  DECIMAL(4,3)    DEFAULT 0.500,
    notification_consent_at DATETIME,
    login_fail_count        TINYINT UNSIGNED NOT NULL DEFAULT 0,
    locked_until            DATETIME,
    display_count           TINYINT UNSIGNED NOT NULL DEFAULT 10,
    profile_completeness    TINYINT UNSIGNED DEFAULT 0,
    withdrawn_at            DATETIME,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2. user_attributes
```sql
-- attr_type: VARCHAR(30), ENUM 아님. 유효성은 Java Enum으로 검증.
-- 유효값 예시: 'INTEREST_FIELD', 'TARGET_TYPE'
CREATE TABLE user_attributes (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    attr_type   VARCHAR(30)  NOT NULL,
    attr_value  VARCHAR(100) NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ua_user      (user_id),
    KEY idx_ua_attr_type (attr_type),
    CONSTRAINT fk_ua_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3. priority_options (마스터 데이터)
```sql
CREATE TABLE priority_options (
    id          TINYINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(30) NOT NULL,
    label       VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    PRIMARY KEY (id),
    UNIQUE KEY uq_po_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO priority_options (code, label) VALUES
('HOUSING',    '주거'),
('AMOUNT',     '금액'),
('ONLINE',     '온라인신청'),
('YOUTH_ONLY', '청년전용'),
('EDU_JOB',    '교육·취업'),
('CULTURE',    '문화·여가'),
('DEADLINE',   '마감임박');
```

### 4. user_priorities
```sql
CREATE TABLE user_priorities (
    id                  BIGINT           NOT NULL AUTO_INCREMENT,
    user_id             BIGINT           NOT NULL,
    priority_option_id  TINYINT UNSIGNED NOT NULL,
    priority_rank       TINYINT UNSIGNED NOT NULL,    -- 1~5
    weight              DECIMAL(3,1)     NOT NULL,    -- 2.0/1.6/1.3/1.1/1.0
    created_at          DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_up_user_option (user_id, priority_option_id),
    KEY idx_up_user (user_id),
    CONSTRAINT fk_up_user   FOREIGN KEY (user_id)            REFERENCES users(id)           ON DELETE CASCADE,
    CONSTRAINT fk_up_option FOREIGN KEY (priority_option_id) REFERENCES priority_options(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 5. welfare_services
```sql
-- ⚠️ ai_score 컬럼 없음. AI 점수는 user_recommendations에만 존재.
CREATE TABLE welfare_services (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    source_type      ENUM('YOUTH','BOKJIRO_CENTRAL','BOKJIRO_LOCAL') NOT NULL,
    source_id        VARCHAR(50) NOT NULL,
    title            VARCHAR(255) NOT NULL,
    description      TEXT,
    support_content  TEXT,           -- 온통청년 전용
    category_main    VARCHAR(100),
    category_sub     VARCHAR(100),
    keyword          VARCHAR(200),
    min_age          TINYINT UNSIGNED,
    max_age          TINYINT UNSIGNED,
    min_income       INT UNSIGNED,
    max_income       INT UNSIGNED,
    apply_start_date DATE,
    apply_end_date   DATE,
    start_date       DATE,
    end_date         DATE,
    life_stage       VARCHAR(200),
    support_cycle    VARCHAR(50),
    provision_type   VARCHAR(100),
    apply_method_name VARCHAR(200),
    is_online_apply  TINYINT(1)  DEFAULT 0,
    host_org         VARCHAR(200),
    operating_org    VARCHAR(200),
    contact          VARCHAR(100),
    detail_url       VARCHAR(500),
    unified_category VARCHAR(50),    -- 필터 UI용 통합 분류 (매핑 규칙 → api-mapping.md)
    is_youth_specific TINYINT(1) NOT NULL DEFAULT 0,
    status           ENUM('ACTIVE','UPCOMING','CLOSED') NOT NULL DEFAULT 'ACTIVE',
    api_view_count   INT UNSIGNED NOT NULL DEFAULT 0,
    view_count       INT UNSIGNED NOT NULL DEFAULT 0,
    collected_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    registered_at    DATETIME,
    last_modified_at DATETIME,
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ws_source   (source_type, source_id),
    KEY idx_ws_status         (status),
    KEY idx_ws_unified_cat    (unified_category),
    KEY idx_ws_source_type    (source_type),
    KEY idx_ws_age            (min_age, max_age),
    KEY idx_ws_end_date       (end_date),
    KEY idx_ws_apply_end      (apply_end_date),
    KEY idx_ws_collected      (collected_at),
    KEY idx_ws_view_count     (view_count DESC),
    FULLTEXT KEY ft_ws_search (title, description, support_content, keyword)
        WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 6. welfare_service_details
```sql
CREATE TABLE welfare_service_details (
    id                  BIGINT  NOT NULL AUTO_INCREMENT,
    service_id          BIGINT  NOT NULL,
    target_detail       TEXT,       -- 지원대상 상세 (sprtTrgtCn / tgtrDtlCn)
    support_detail      TEXT,       -- 지원내용 상세 (alwServCn)
    apply_method_detail TEXT,       -- 신청방법 상세 (aplyMtdCn / applmetList)
    selection_criteria  TEXT,
    contact_list        JSON,       -- [{name, phone}, ...]
    homepage_url        VARCHAR(500),
    related_law         VARCHAR(500),
    form_files          JSON,       -- [{name, url}, ...]
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_wsd_service (service_id),
    CONSTRAINT fk_wsd_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 7. service_regions
```sql
CREATE TABLE service_regions (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    service_id  BIGINT      NOT NULL,
    region_code VARCHAR(10),
    sido_name   VARCHAR(50),
    sgg_name    VARCHAR(50),
    PRIMARY KEY (id),
    KEY idx_sr_service     (service_id),
    KEY idx_sr_region_code (region_code),
    KEY idx_sr_sido        (sido_name),
    CONSTRAINT fk_sr_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 8. service_tags
```sql
-- ⚠️ 삽입 시 반드시 UPSERT. 중복 삽입 → rule_base_score 이중합산 버그.
CREATE TABLE service_tags (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    service_id  BIGINT       NOT NULL,
    tag_type    ENUM('INTEREST_THEME','TARGET_GROUP','LIFE_STAGE','KEYWORD') NOT NULL,
    tag_value   VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_st      (service_id, tag_type, tag_value),
    KEY        idx_st_tag (tag_type, tag_value),
    CONSTRAINT fk_st_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 9. user_recommendations
```sql
-- AI 점수와 이유가 여기에만 존재 (유저×서비스 단위)
-- ai_score NULL = AI 미실행 → rule만으로 final_score 계산
CREATE TABLE user_recommendations (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    service_id          BIGINT       NOT NULL,
    recommended_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- DATE 아님, DATETIME
    rule_base_score     DECIMAL(7,2),
    rule_weighted_score DECIMAL(7,2),
    ai_score            DECIMAL(5,2),        -- NULL 가능 (AI 미실행 또는 CLOSED 리셋)
    ai_reason           VARCHAR(500),
    rule_weight_used    DECIMAL(3,2),        -- 발송 시점 적용 가중치 기록
    ai_weight_used      DECIMAL(3,2),
    final_score         DECIMAL(6,5) NOT NULL DEFAULT 0,
    is_bookmarked       TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ur_user_service_time (user_id, service_id, recommended_at),
    KEY idx_ur_user_score   (user_id, final_score DESC),
    KEY idx_ur_recommended  (recommended_at),
    KEY idx_ur_bookmark     (user_id, is_bookmarked),
    CONSTRAINT fk_ur_user    FOREIGN KEY (user_id)    REFERENCES users(id)           ON DELETE CASCADE,
    CONSTRAINT fk_ur_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 10. recommendation_logs
```sql
-- 영구 보관 (CTR 분석 + 가중치 단계별 품질 분석용)
CREATE TABLE recommendation_logs (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    service_id       BIGINT      NOT NULL,
    notification_id  BIGINT,                     -- 2차에서 FK 추가 예정
    final_score      DECIMAL(6,5),
    rule_weight_used DECIMAL(3,2),
    ai_weight_used   DECIMAL(3,2),
    is_fallback      TINYINT(1)  NOT NULL DEFAULT 0,   -- TRUE=rule만, FALSE=AI포함
    is_clicked       TINYINT(1)  NOT NULL DEFAULT 0,
    sent_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    clicked_at       DATETIME,
    PRIMARY KEY (id),
    KEY idx_rl_user    (user_id),
    KEY idx_rl_service (service_id),
    CONSTRAINT fk_rl_user    FOREIGN KEY (user_id)    REFERENCES users(id)           ON DELETE CASCADE,
    CONSTRAINT fk_rl_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 11. score_weights
```sql
-- Cold Start 대응 가중치 설정. 하드코딩 금지, 반드시 이 테이블 조회.
CREATE TABLE score_weights (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    weight_key    VARCHAR(50)     NOT NULL,   -- 'COLD_START', 'GROWTH', 'STABLE'
    rule_weight   DECIMAL(3,2)    NOT NULL,
    ai_weight     DECIMAL(3,2)    NOT NULL,
    min_log_count INT UNSIGNED    NOT NULL,   -- 이 단계 적용 최소 추천 이력 수
    is_active     TINYINT(1)      NOT NULL DEFAULT 1,
    description   VARCHAR(200),
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_sw_key (weight_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 초기 데이터 (서버 최초 실행 시 INSERT)
INSERT INTO score_weights (weight_key, rule_weight, ai_weight, min_log_count, description) VALUES
('COLD_START', 0.80, 0.20,   0, '추천 이력 100건 미만: rule 우선'),
('GROWTH',     0.60, 0.40, 100, '추천 이력 100건 이상: AI 점진 반영'),
('STABLE',     0.40, 0.60, 500, 'SRS 목표값: AI 우선');
```

---

## 2차 확장 (9개) — 참고용, 1차에서 의존 금지

```sql
-- 12. user_clusters
CREATE TABLE user_clusters (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    cluster_key  VARCHAR(30) NOT NULL,   -- 예: "age20s_early_low"
    age_group    VARCHAR(20) NOT NULL,
    income_group VARCHAR(20) NOT NULL,
    assigned_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_uc_user (user_id),
    KEY idx_uc_cluster (cluster_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 13. cluster_ai_results (군집×정책 단위 AI 결과, 7일 TTL)
CREATE TABLE cluster_ai_results (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    cluster_key  VARCHAR(30)  NOT NULL,
    service_id   BIGINT       NOT NULL,
    ai_score     DECIMAL(5,2) NOT NULL,
    ai_reason    VARCHAR(500),
    batch_job_id BIGINT,
    expires_at   DATETIME     NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_car_cluster_service (cluster_key, service_id),
    KEY idx_car_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 14. batch_jobs
CREATE TABLE batch_jobs (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    cluster_key     VARCHAR(30) NOT NULL,
    openai_batch_id VARCHAR(100),
    status          ENUM('SUBMITTED','IN_PROGRESS','COMPLETED','FAILED','EXPIRED','FALLBACK') NOT NULL,
    is_fallback     TINYINT(1)  NOT NULL DEFAULT 0,
    submitted_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    DATETIME,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_bj_status  (status),
    KEY idx_bj_cluster (cluster_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 15. normalization_stats (p5·p95 기록)
CREATE TABLE normalization_stats (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    batch_date   DATE         NOT NULL,
    cluster_key  VARCHAR(30)  NOT NULL,
    score_type   ENUM('RULE','AI') NOT NULL,
    p5           DECIMAL(7,2) NOT NULL,
    p95          DECIMAL(7,2) NOT NULL,
    sample_count INT UNSIGNED NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ns (batch_date, cluster_key, score_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 16. notifications (알림 헤더)
CREATE TABLE notifications (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    channel       ENUM('KAKAO','EMAIL') NOT NULL,
    status        ENUM('PENDING','SENT','FAILED') NOT NULL DEFAULT 'PENDING',
    sent_at       DATETIME,
    retry_count   TINYINT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_at DATETIME,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_noti_user   (user_id),
    KEY idx_noti_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 17. notification_services (알림-정책 매핑)
CREATE TABLE notification_services (
    id              BIGINT           NOT NULL AUTO_INCREMENT,
    notification_id BIGINT           NOT NULL,
    service_id      BIGINT           NOT NULL,
    slot_type       ENUM('A','B')    NOT NULL,
    slot_order      TINYINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ns_notification (notification_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 18. api_sync_logs
CREATE TABLE api_sync_logs (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    source_type    ENUM('YOUTH','BOKJIRO_CENTRAL','BOKJIRO_LOCAL') NOT NULL,
    status         ENUM('SUCCESS','FAILED','PARTIAL') NOT NULL,
    total_fetched  INT UNSIGNED NOT NULL DEFAULT 0,
    upserted_count INT UNSIGNED NOT NULL DEFAULT 0,
    error_message  TEXT,
    retry_count    TINYINT UNSIGNED NOT NULL DEFAULT 0,
    started_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at    DATETIME,
    PRIMARY KEY (id),
    KEY idx_asl_source  (source_type),
    KEY idx_asl_started (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 19. search_logs
CREATE TABLE search_logs (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT,                      -- 3개월 후 NULL로 익명화
    keyword      VARCHAR(255) NOT NULL,
    result_count INT UNSIGNED NOT NULL DEFAULT 0,
    searched_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sl_keyword  (keyword),
    KEY idx_sl_searched (searched_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 20. service_view_logs (조회수 중복 방지)
CREATE TABLE service_view_logs (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    service_id BIGINT   NOT NULL,
    user_id    BIGINT,
    viewed_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_svl_service_user (service_id, user_id, viewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 주요 인덱스 활용 패턴

| 쿼리 패턴 | 사용 인덱스 |
|-----------|-------------|
| 정책 목록 (status + 정렬) | `idx_ws_status` + `view_count DESC` |
| 키워드 검색 | `ft_ws_search` (FULLTEXT ngram) |
| 통합 카테고리 필터 | `idx_ws_unified_cat` |
| 나이/지역 필터 | `idx_ws_age`, `idx_sr_sido` |
| 마감임박 필터 | `idx_ws_apply_end` |
| 신규 정책 감지 (24시간) | `idx_ws_collected` |
| 추천 목록 조회 | `idx_ur_user_score` |
| 태그 검색 | `idx_st_tag` |

---

## 데이터 보존 정책

| 테이블 | 보존 |
|--------|------|
| `welfare_services` | 영구 (CLOSED 상태로 보관) |
| `user_recommendations` | 30일 + 미북마크 삭제 |
| `recommendation_logs` | 영구 (CTR + 가중치 단계 분석) |
| `score_weights` | 영구 (이력 관리) |
| `cluster_ai_results` (2차) | 7일 TTL |
| `batch_jobs` (2차) | 30일 |
| `normalization_stats` (2차) | 90일 |
| `search_logs` (2차) | 3개월 후 user_id → NULL |
| 탈퇴 users | 비식별화 보존 (email→'withdrawn', 개인정보 NULL) |
