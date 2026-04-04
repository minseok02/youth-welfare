# DB 스키마 설계

> 기준 문서: SRS v2.9 / API 데이터 분석 / 확장형 MVP 방향

---

## 0. API 필드 → DB 컬럼 매핑 요약

### 복지로 상세 → 저장할 필드

리스트에 없는 고유 필드만 `welfare_service_details`에 저장. 나머지는 리스트와 중복이므로 제외.

| 상세 필드 | 지자체 | 중앙 | 저장 여부 | 이유 |
|---|---|---|---|---|
| `sprtTrgtCn` / `tgtrDtlCn` | ✓ | ✓ | **저장** | 지원대상 상세. 리스트에 없음 |
| `alwServCn` | ✓ | ✓ | **저장** | 지원내용 상세. 리스트에 없음 |
| `aplyMtdCn` / `applmetList` | ✓ | ✓ | **저장** | 신청방법 상세. 리스트에 없음 |
| `slctCritCn` | ✓ | ✓ | 저장 (선택) | 선정기준. 빈값 多 |
| `inqplCtadrList` | ✓ | ✓ | 저장 | 문의처. JSON 배열 |
| `inqplHmpgReldList` | ✓ | ✓ | 저장 | 홈페이지 URL |
| `basfrmList` | ✓ | - | 저장 (선택) | 신청서식 파일. JSON 배열 |
| `baslawList` | ✓ | ✓ | 저장 (선택) | 근거법령명 |
| `enfcBgngYmd/enfcEndYmd` | ✓ | - | **제외** | 시행시작/종료일과 동일 |
| `servDgst`, `servNm` 등 | ✓ | ✓ | **제외** | 리스트와 완전 동일 |
| `lifeNmArray`, `sprtCycNm` 등 | ✓ | ✓ | **제외** | 리스트와 완전 동일 |
| `crtrYr`, `wlfareInfoOutlCn` | - | ✓ | **제외** | servDgst와 사실상 동일 |

### 3개 API 필드 → DB 컬럼 매핑

| 의미 | 온통청년 | 복지로 중앙 | 복지로 지자체 | DB 컬럼 |
|---|---|---|---|---|
| 정책 ID | `정책번호` | `servId` | `servId` | `source_id` |
| 정책명 | `정책명` | `servNm` | `servNm` | `title` |
| 요약 설명 | `정책소개내용` | `servDgst` | `servDgst` | `description` |
| 지원 내용 | `정책지원내용` | - | - | `support_content` |
| 대분류 | `정책대부류명` | - | - | `category_main` |
| 중분류 | `정책중부류명` | - | - | `category_sub` |
| 키워드 | `정책키워드명` | - | - | `keyword` |
| **통합 분류** | 대분류 매핑 | intrsThema 매핑 | intrsThema 매핑 | `unified_category` |
| 주관기관 | `주관기관명` | `jurMnofNm` | - | `host_org` |
| 담당/이행기관 | `이행기관명` | `jurOrgNm` | `bizChrDeptNm` | `operating_org` |
| 생애주기 | - | `lifeArray` | `lifeNmArray` | `life_stage` |
| 관심주제 | - | `intrsThemaArray` | `intrsThemaNmArray` | → `service_tags` |
| 대상자 유형 | - | `trgterIndvdlArray` | `trgterIndvdlNmArray` | → `service_tags` |
| 지원주기 | - | `sprtCycNm` | `sprtCycNm` | `support_cycle` |
| 제공유형 | - | `srvPvsnNm` | `srvPvsnNm` | `provision_type` |
| 온라인신청 | - | `onapPsbltYn` | - | `is_online_apply` |
| 신청방법명 | `신청방법` | - | `aplyMtdNm` | `apply_method_name` |
| 연락처 | - | `rprsCtadr` | - | `contact` |
| 상세링크 | - | `servDtlLink` | `servDtlLink` | `detail_url` |
| 나이 범위 | `최소나이`, `최대나이` | - | - | `min_age`, `max_age` |
| 소득 범위 | `최소소득`, `최대소득` | - | - | `min_income`, `max_income` |
| 조회수 | `조회수` | `inqNum` | `inqNum` | `api_view_count` |
| 신청기간 | `신청기간` | - | - | `apply_start_date`, `apply_end_date` |
| 사업기간 | `사업시작일`, `사업종료일` | - | - | `start_date`, `end_date` |
| 등록일 | `등록일` | `svcfrstRegTs` | - | `registered_at` |
| 수정일 | `수정일` | - | `lastModYmd` | `last_modified_at` |
| 지역 | `지역코드` | - | `ctpvNm`, `sggNm` | → `service_regions` |

### unified_category 매핑 규칙

| unified_category | 온통청년 대분류 | 복지로 intrsThema |
|---|---|---|
| `일자리` | 일자리 | 일자리 |
| `주거` | 주거 | 주거 |
| `교육·직업훈련` | 교육·직업훈련 | 교육 |
| `금융·생활지원` | 금융·복지·문화 | 민간금융, 생활지원 |
| `문화·여가` | 금융·복지·문화 (일부) | 문화·여가 |
| `건강·의료` | - | 신체건강, 정신건강 |
| `가족·돌봄` | - | 보육, 보호·돌봄, 임신·출산 |
| `안전·위기` | - | 안전·위기 |
| `참여·기회` | 참여·기회 | - |
| `기타` | 나머지 | 나머지 |

---

## ⚠️ AI 점수 위치에 대한 설계 결정

### ❌ 잘못된 구조 (이전 버전)

```sql
-- welfare_services에 ai_score 존재
ai_score DECIMAL(5,2)  -- 서비스 공통값 → 잘못됨
```

**왜 잘못되는가:**
- AI 점수는 "이 서비스가 **이 특정 유저에게** 얼마나 맞는가"를 나타냄
- 유저 A와 유저 B가 같은 ai_score를 공유하는 구조 → 개인화 불가
- 즉, 서비스 테이블에 ai_score를 두는 건 "모두에게 동일한 추천"이 되어버림

### ✅ 올바른 구조

| 위치 | 컬럼 | 의미 |
|---|---|---|
| `user_recommendations` | `ai_score` | **유저 × 서비스** 단위 점수 → 1차 구현 |
| `user_recommendations` | `ai_reason` | AI가 이 유저에게 이 정책을 추천한 이유 |
| `cluster_ai_results` (2차) | `ai_score` | **군집 × 서비스** 단위 점수 (Batch API) |
| `cluster_ai_results` (2차) | `ai_reason` | 군집 기준 추천 이유 |
| `welfare_services` | ai_score **없음** ← | 서비스 자체에는 AI 점수 없음 |

---

## 🧊 Cold Start 전략 → 가중치 테이블리드 추천

### 문제

서비스 초기엔 사용자 행동 데이터가 없어 AI 추천의 신뢰도가 낮음 (Data Sparsity).
AI에 높은 가중치를 주면 오히려 추천 품질이 떨어짐.

### 해결: 점진적 가중치 조정

```
초기  → Rule 가중치 높음, AI 가중치 낮음
성장  → 데이터 축적에 따라 AI 가중치 점진적 증가
```

| 단계 | 기준 (`recommendation_logs` count) | rule 가중치 | ai 가중치 | 비고 |
|---|---|---|---|---|
| COLD_START | 0 ~ 99건 | **0.80** | 0.20 | AI 점수 불신뢰 구간 |
| GROWTH | 100 ~ 499건 | **0.60** | 0.40 | 점진적 AI 반영 |
| STABLE | 500건 이상 | 0.40 | **0.60** | SRS 목표값: AI 우선 |

### DB 반영 → `score_weights` 설정 테이블 (1차에 추가)

```sql
CREATE TABLE score_weights (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    weight_key      VARCHAR(50)     NOT NULL,   -- 'COLD_START', 'GROWTH', 'STABLE'
    rule_weight     DECIMAL(3,2)    NOT NULL,   -- 0.00 ~ 1.00
    ai_weight       DECIMAL(3,2)    NOT NULL,   -- 0.00 ~ 1.00
    min_log_count   INT UNSIGNED    NOT NULL,   -- 이 가중치가 적용되는 최소 추천 이력 수
    is_active       TINYINT(1)      NOT NULL DEFAULT 1,
    description     VARCHAR(200),
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_sw_key (weight_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 초기 데이터
INSERT INTO score_weights (weight_key, rule_weight, ai_weight, min_log_count, description) VALUES
('COLD_START', 0.80, 0.20,   0, '추천 이력 100건 미만: rule 우선'),
('GROWTH',     0.60, 0.40, 100, '추천 이력 100건 이상: AI 점진 반영'),
('STABLE',     0.40, 0.60, 500, 'SRS 목표값: AI 우선');
```

### 가중치 적용 로직 (애플리케이션)

```java
// ReRankingService.java

ScoreWeight weight = scoreWeightRepository.findActiveWeight(totalLogCount);
// totalLogCount = recommendation_logs 전체 행 수 (전역 기준)

double finalScore;
if (aiScore != null) {
    double normRule = normalize(ruleWeightedScore);
    double normAi   = normalize(aiScore);
    finalScore = normRule * weight.getRuleWeight()
               + normAi   * weight.getAiWeight();
} else {
    // ai_score NULL → rule만 사용 (NULL-safe)
    finalScore = normalize(ruleWeightedScore);
}
```

---

## 1. 테이블 목록

### 1차 구현 (11개) → 지금 바로 만들 것

| # | 테이블 | 역할 |
|---|---|---|
| 1 | `users` | 회원 기본정보 |
| 2 | `user_attributes` | 관심분야·대상 선택 |
| 3 | `user_priorities` | 우선순위 설정 |
| 4 | `priority_options` | 선택지 마스터 |
| 5 | `welfare_services` | 정책 통합 메인 (FULLTEXT) |
| 6 | `welfare_service_details` | 정책 상세 (상세 API 고유 필드) |
| 7 | `service_regions` | 정책-지역 다대다 |
| 8 | `service_tags` | 정책 태그 (관심주제·대상·생애주기) |
| 9 | `user_recommendations` | 추천 결과 (rule + ai_score + ai_reason) |
| 10 | `recommendation_logs` | 추천 클릭 추적 |
| 11 | `score_weights` | Cold Start 대응 가중치 설정 |

### 2차 확장 (9개) → 나중에 추가할 것

| # | 테이블 | 역할 | 추가 시점 |
|---|---|---|---|
| 12 | `user_clusters` | 사용자↔군집 매핑 | 군집화 구현 시 |
| 13 | `cluster_ai_results` | 군집×정책 AI 결과 (7일 TTL) | Batch AI 전환 시 |
| 14 | `batch_jobs` | Batch 제출 이력·상태 | Batch AI 전환 시 |
| 15 | `normalization_stats` | 배치별 p5·p95 | 정규화 고도화 시 |
| 16 | `notifications` | 알림 헤더 | 알림 시스템 구현 시 |
| 17 | `notification_services` | 알림-정책 매핑 | 알림 시스템 구현 시 |
| 18 | `api_sync_logs` | 수집 배치 이력 | 배치 안정화 후 |
| 19 | `search_logs` | 검색 키워드 | 검색 기능 안정화 후 |
| 20 | `service_view_logs` | 조회수 중복 방지 | 조회수 정교화 시 |

---

## 2. DDL — 1차 구현 (11개)

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
CREATE TABLE user_attributes (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    attr_type   VARCHAR(30)  NOT NULL,   -- ENUM 대신 VARCHAR: 향후 LIFE_STAGE·학력 등 추가 시 ALTER 불필요
                                         -- 유효값: 'INTEREST_FIELD', 'TARGET_TYPE', ...
                                         -- 유효성 검증은 애플리케이션 레이어(Enum 클래스)에서 담당
    attr_value  VARCHAR(100) NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ua_user      (user_id),
    KEY idx_ua_attr_type (attr_type),
    CONSTRAINT fk_ua_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3. priority_options

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
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    user_id             BIGINT          NOT NULL,
    priority_option_id  TINYINT UNSIGNED NOT NULL,
    priority_rank       TINYINT UNSIGNED NOT NULL,          -- 1~5
    weight              DECIMAL(3,1)    NOT NULL,           -- 2.0/1.6/1.3/1.1/1.0
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_up_user_option (user_id, priority_option_id),
    KEY idx_up_user (user_id),
    CONSTRAINT fk_up_user   FOREIGN KEY (user_id)            REFERENCES users(id)           ON DELETE CASCADE,
    CONSTRAINT fk_up_option FOREIGN KEY (priority_option_id) REFERENCES priority_options(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 5. welfare_services

> ⚠️ `ai_score` 없음. AI 점수는 유저×서비스 단위인 `user_recommendations`에만 존재.

```sql
CREATE TABLE welfare_services (
    id              BIGINT      NOT NULL AUTO_INCREMENT,

    source_type     ENUM('YOUTH','BOKJIRO_CENTRAL','BOKJIRO_LOCAL') NOT NULL,
    source_id       VARCHAR(50) NOT NULL,

    title           VARCHAR(255) NOT NULL,
    description     TEXT,

    -- 온통청년 전용
    support_content TEXT,
    category_main   VARCHAR(100),
    category_sub    VARCHAR(100),
    keyword         VARCHAR(200),
    min_age         TINYINT UNSIGNED,
    max_age         TINYINT UNSIGNED,
    min_income      INT UNSIGNED,
    max_income      INT UNSIGNED,
    apply_start_date DATE,
    apply_end_date   DATE,
    start_date      DATE,
    end_date        DATE,

    -- 복지로 공통
    life_stage      VARCHAR(200),
    support_cycle   VARCHAR(50),
    provision_type  VARCHAR(100),
    apply_method_name VARCHAR(200),
    is_online_apply TINYINT(1)  DEFAULT 0,

    -- 기관
    host_org        VARCHAR(200),
    operating_org   VARCHAR(200),
    contact         VARCHAR(100),
    detail_url      VARCHAR(500),

    -- 필터 UI용 통합 분류
    unified_category VARCHAR(50),

    -- 추천 가점용 플래그
    is_youth_specific TINYINT(1) NOT NULL DEFAULT 0,

    -- 상태
    status          ENUM('ACTIVE','UPCOMING','CLOSED') NOT NULL DEFAULT 'ACTIVE',

    -- 조회수
    api_view_count  INT UNSIGNED NOT NULL DEFAULT 0,
    view_count      INT UNSIGNED NOT NULL DEFAULT 0,

    -- ← ai_score 없음: AI 점수는 유저별이므로 user_recommendations에만 존재

    -- 수집 추적
    collected_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    registered_at   DATETIME,
    last_modified_at DATETIME,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

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

    target_detail       TEXT,       -- 지원대상 상세 (sprtTrgtCn / tgtrDtlCn) ✓
    support_detail      TEXT,       -- 지원내용 상세 (alwServCn) ✓
    apply_method_detail TEXT,       -- 신청방법 상세 (aplyMtdCn / applmetList) ✓

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
CREATE TABLE service_tags (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    service_id  BIGINT       NOT NULL,
    tag_type    ENUM('INTEREST_THEME','TARGET_GROUP','LIFE_STAGE','KEYWORD') NOT NULL,
    tag_value   VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    -- 동일 정책에 동일 태그 중복 삽입 요청 차단
    -- 누락 시: rule_base_score 계산 시 태그 점수 중복 합산 → 추천 결과 왜곡
    UNIQUE KEY uq_st       (service_id, tag_type, tag_value),
    KEY        idx_st_tag  (tag_type, tag_value),
    CONSTRAINT fk_st_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 9. user_recommendations

> AI 점수와 추천 이유는 여기에만 존재. 유저 × 서비스 단위.
> `ai_score` NULL-safe: NULL이면 rule만으로 final_score 계산.

```sql
CREATE TABLE user_recommendations (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    service_id          BIGINT       NOT NULL,
    -- batch_date DATE → recommended_at DATETIME으로 변경
    -- 이유: 실시간 방식에서 DATE(날짜)만으로 중복 구분 불가, 이 단위 필요
    -- 2차 Batch 전환 후에도 DATETIME이 배치 실행 시간도 포괄하므로 하위호환 유지
    recommended_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Rule 점수
    rule_base_score     DECIMAL(7,2),
    rule_weighted_score DECIMAL(7,2),

    -- AI 점수 (유저×서비스 단위. NULL = AI 미실행 → rule만 사용)
    ai_score            DECIMAL(5,2),
    ai_reason           VARCHAR(500),

    -- 최종 점수
    rule_weight_used    DECIMAL(3,2),
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
CREATE TABLE recommendation_logs (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    service_id      BIGINT      NOT NULL,
    notification_id BIGINT,                     -- 2차에서 FK 추가 예정
    final_score     DECIMAL(6,5),
    rule_weight_used DECIMAL(3,2),              -- 발송 시점 적용 가중치 기록
    ai_weight_used   DECIMAL(3,2),
    is_fallback     TINYINT(1)  NOT NULL DEFAULT 0,
    is_clicked      TINYINT(1)  NOT NULL DEFAULT 0,
    sent_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    clicked_at      DATETIME,
    PRIMARY KEY (id),
    KEY idx_rl_user    (user_id),
    KEY idx_rl_service (service_id),
    CONSTRAINT fk_rl_user    FOREIGN KEY (user_id)    REFERENCES users(id)           ON DELETE CASCADE,
    CONSTRAINT fk_rl_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 영구 보관 (CTR 분석 + 가중치 변화 이력 분석)
```

### 11. score_weights

```sql
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

INSERT INTO score_weights (weight_key, rule_weight, ai_weight, min_log_count, description) VALUES
('COLD_START', 0.80, 0.20,   0, '추천 이력 100건 미만: rule 우선'),
('GROWTH',     0.60, 0.40, 100, '추천 이력 100건 이상: AI 점진 반영'),
('STABLE',     0.40, 0.60, 500, 'SRS 목표값: AI 우선');
```

---

## 3. DDL — 2차 확장 (9개, 참고용)

```sql
-- 12. user_clusters
CREATE TABLE user_clusters (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    cluster_key  VARCHAR(30) NOT NULL,
    age_group    VARCHAR(20) NOT NULL,
    income_group VARCHAR(20) NOT NULL,
    assigned_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_uc_user (user_id),
    KEY idx_uc_cluster (cluster_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 13. cluster_ai_results (ai_score + ai_reason 군집 단위)
CREATE TABLE cluster_ai_results (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    cluster_key  VARCHAR(30)  NOT NULL,
    service_id   BIGINT       NOT NULL,
    ai_score     DECIMAL(5,2) NOT NULL,
    ai_reason    VARCHAR(500),
    batch_job_id BIGINT,
    expires_at   DATETIME     NOT NULL,         -- 7일 TTL
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

-- 15. normalization_stats
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

-- 16. notifications
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

-- 17. notification_services
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
    user_id      BIGINT,
    keyword      VARCHAR(255) NOT NULL,
    result_count INT UNSIGNED NOT NULL DEFAULT 0,
    searched_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sl_keyword  (keyword),
    KEY idx_sl_searched (searched_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 20. service_view_logs
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

## 4. 수집 전처리 로직

### 온통청년 → welfare_services

```
정책번호           → source_id       (source_type='YOUTH')
정책명             → title
정책소개내용        → description
정책지원내용        → support_content
정책대부류명        → category_main
정책중부류명        → category_sub
정책키워드명        → keyword
                   → service_tags (KEYWORD, 콤마 분해)
대분류             → unified_category (매핑 테이블)
주관기관명         → host_org
이행기관명         → operating_org
최소/최대나이       → min_age / max_age
최소/최대소득       → min_income / max_income
사업시작/종료일    → start_date / end_date
신청기간           → apply_start_date / apply_end_date
신청방법           → apply_method_name
지역코드 (콤마분해) → service_regions (region_code 별 insert)
조회수             → api_view_count
등록일             → registered_at
수정일             → last_modified_at
```

### 복지로 중앙 → welfare_services

```
servId               → source_id       (source_type='BOKJIRO_CENTRAL')
servNm               → title
servDgst             → description
jurMnofNm            → host_org
jurOrgNm             → operating_org
lifeArray            → life_stage, service_tags (LIFE_STAGE)
intrsThemaArray      → service_tags (INTEREST_THEME), unified_category
trgterIndvdlArray    → service_tags (TARGET_GROUP)
sprtCycNm            → support_cycle
srvPvsnNm            → provision_type
onapPsbltYn          → is_online_apply (Y→1)
rprsCtadr            → contact
servDtlLink          → detail_url
inqNum               → api_view_count
svcfrstRegTs         → registered_at
-- 지역: 전국 단위 → service_regions 미삽입 (또는 sido_name='전국' 1건)
```

### 복지로 지자체 → welfare_services

```
servId               → source_id       (source_type='BOKJIRO_LOCAL')
servNm               → title
servDgst             → description
bizChrDeptNm         → operating_org
lifeNmArray          → life_stage, service_tags (LIFE_STAGE)
intrsThemaNmArray    → service_tags (INTEREST_THEME), unified_category
trgterIndvdlNmArray  → service_tags (TARGET_GROUP)
sprtCycNm            → support_cycle
srvPvsnNm            → provision_type
aplyMtdNm            → apply_method_name
servDtlLink          → detail_url
inqNum               → api_view_count
lastModYmd           → last_modified_at
ctpvNm + sggNm       → service_regions (sido_name, sgg_name)
```

### 복지로 상세 → welfare_service_details

```
sprtTrgtCn / tgtrDtlCn → target_detail      ✓
alwServCn              → support_detail      ✓
aplyMtdCn / applmetList→ apply_method_detail ✓
slctCritCn             → selection_criteria
inqplCtadrList         → contact_list (JSON)
inqplHmpgReldList      → homepage_url (첫 번째)
basfrmList             → form_files (JSON)
baslawList             → related_law (첫 번째)
```

---

## 5. 추천 파이프라인 (1차)

```
RetrievalService
 ├── SQL WHERE (나이/지역/소득/취업상태 pass/fail)
 └── 상위 K건 (youth_all 고정)

RuleScoringService
 ├── if-else 가점: 청년전용+20, 지원금100만+15, 온라인+10, 지역+10, 관심분야+10, 마감임박+5
 ├── 우선순위 가중치 적용 → rule_weighted_score
 └── min-max 정규화 → norm_rule

AiScoringService (AiRecommendationGateway)
 ├── 상위 N개 실시간 호출 → ai_score, ai_reason 저장
 └── NULL-safe: ai_score 없으면 건너뜀

ReRankingService
 ├── score_weights 테이블에서 현재 단계 조회
 │   (recommendation_logs 전체 count → COLD_START / GROWTH / STABLE 결정)
 ├── ai_score 있으면:
 │   final_score = norm_rule × rule_weight + norm_ai × ai_weight
 └── ai_score NULL이면:
     final_score = norm_rule  (rule만)

RecommendationPersistenceService
 └── user_recommendations 저장
     (recommended_at = 현재 시간, rule_weight_used, ai_weight_used 함께 기록)
```

---

## 6. 1차 → 2차 확장 대응표

| 항목 | 1차 | 2차 | 확장 방법 |
|---|---|---|---|
| 군집 | youth_all 고정 | 나이대×소득 2D | `user_clusters` + `ClusterService.assignCluster()` 교체 |
| AI 호출 | 실시간 1회 | Batch API | `BatchAiGateway` 교체 |
| AI 캐시 | 없음 | `cluster_ai_results` | Batch 결과 군집단위 캐시 |
| 정규화 | 단순 min-max | p5~p95 + Min-Max | `ScoreNormalizer` + `normalization_stats` |
| 알림 후보 | top 3 | A/B 타입 + 슬롯 | `selectNotificationCandidates()` 교체 + `notifications` |
| 가중치 | `score_weights` 테이블 이미 존재 | 값만 튜닝 | INSERT/UPDATE만 하면 됨 |
| 배치 상태 | 없음 | `batch_jobs` | 테이블 + `BatchJobMonitor` |

---

## 7. 인덱스 & 성능 포인트

| 쿼리 패턴 | 인덱스 |
|---|---|
| 정책 목록 (status + 정렬) | `idx_ws_status` + `view_count DESC` |
| 키워드 검색 | `ft_ws_search` (FULLTEXT ngram) |
| 통합 카테고리 필터 | `idx_ws_unified_cat` |
| 나이/지역 필터 | `idx_ws_age`, `idx_sr_sido`, `idx_sr_region_code` |
| 마감임박 필터 | `idx_ws_apply_end` |
| 신규 정책 감지 (24시간) | `idx_ws_collected` |
| 추천 목록 조회 | `idx_ur_user_score` |

---

## 8. 데이터 보존 정책

| 테이블 | 보존 정책 |
|---|---|
| `welfare_services` | 영구 (CLOSED 상태로 보관) |
| `welfare_service_details` | welfare_services와 동일 |
| `user_recommendations` | 30일 + 미북마크 삭제 |
| `recommendation_logs` | 영구 보관 (CTR + 가중치 변화 이력 분석) |
| `score_weights` | 영구 보관 (이력 관리) |
| `search_logs` (2차) | 3개월 후 user_id → NULL |
| `cluster_ai_results` (2차) | 7일 TTL |
| `batch_jobs` (2차) | 30일 |
| `normalization_stats` (2차) | 90일 |
| `api_sync_logs` (2차) | 90일 |
| 탈퇴 users | 비식별화 보존 (email→'withdrawn', 개인정보 NULL) |
