# 청년복지 통합 플랫폼 — CLAUDE.md

> Claude Code가 코드 생성 시 참조하는 컨텍스트 문서.
> 상세 설계는 `docs/` 하위 파일 참조.

---

## 1. 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Spring Boot 3.x (단독, FastAPI 없음) |
| Frontend | React + MUI + React Query + Zustand |
| Database | MySQL 8.0+ (FULLTEXT ngram) |
| AI API | OpenAI GPT-4o-mini (1차: 실시간 단건 / 2차: Batch API) |
| 인증 | JWT + HttpOnly 쿠키 (Access 30분 / Refresh 7일 Rotation) |
| 알림 | 카카오 알림톡(CoolSMS) + Gmail SMTP 폴백 |
| 배포 | EC2 t4g.medium 이상 (ARM Graviton2, 4GB+) + Docker Compose 3컨테이너 |
| XML 파싱 | jackson-dataformat-xml (XXE 비활성화 필수) |
| HTML 정제 | Jsoup strip |

---

## 2. 패키지 구조 (도메인형)

```
com.example.welfare
├── global/
│   ├── config/          # SecurityConfig, WebClientConfig, JacksonConfig
│   ├── exception/       # GlobalExceptionHandler, CustomException 클래스들
│   ├── response/        # ApiResponse<T> 공통 래퍼
│   ├── entity/          # BaseTimeEntity (created_at, updated_at)
│   └── util/            # JwtUtil, AesEncryptUtil
│
├── user/
│   ├── controller/      # AuthController, UserController
│   ├── service/         # AuthService, UserService
│   ├── repository/
│   ├── entity/          # User, UserAttribute, UserPriority
│   └── dto/
│
├── policy/              # welfare_services 조회·검색·랭킹
│   ├── controller/      # PolicyController
│   ├── service/         # PolicyService, PolicySearchService
│   ├── repository/
│   ├── entity/          # WelfareService, WelfareServiceDetail, ServiceRegion, ServiceTag
│   └── dto/
│
├── collect/             # 공공API 수집 배치
│   ├── service/         # CollectService, StatusUpdateService
│   ├── gateway/         # YouthApiClient, BokjiroCentralClient, BokjiroLocalClient
│   ├── mapper/          # WelfareServiceMapper (3종 DTO → Entity)
│   └── dto/             # YouthApiDto, BokjiroCentralDto, BokjiroLocalDto
│
├── recommend/           # 추천 파이프라인 핵심
│   ├── controller/      # RecommendationController
│   ├── facade/          # RecommendationFacade  ← 유일한 진입점
│   ├── service/
│   │   ├── RetrievalService.java
│   │   ├── RuleScoringService.java
│   │   ├── AiScoringService.java
│   │   ├── ScoreWeightService.java
│   │   ├── ReRankingService.java
│   │   ├── RecommendationPersistenceService.java
│   │   ├── RecommendationLogService.java
│   │   ├── ClusterService.java       # 1차: 항상 "youth_all" 반환
│   │   └── ScoreNormalizer.java      # 1차: min-max
│   ├── gateway/
│   │   ├── AiRecommendationGateway.java   (인터페이스)
│   │   └── RealtimeAiGateway.java         (1차 구현체)
│   ├── repository/
│   ├── entity/          # UserRecommendation, RecommendationLog, ScoreWeight
│   └── dto/
│
└── notification/        # 알림 발송
    ├── service/         # NotificationService (1차: top 3 발송)
    ├── gateway/         # KakaoAlimtalkClient, EmailClient
    └── entity/          # (2차: Notification, NotificationService 테이블)
```

> 상세 서비스 클래스 설명 → [`docs/architecture.md`](docs/architecture.md)

### 배포 메모

- 현재 기본 배포 형태는 `app + db + redis`를 같은 EC2에서 Docker Compose로 운영하는 단일 서버 구조
- 2026-04-25 로컬 Docker 실측 기준 `idle`에서 `app` 약 `766MiB`, `db` 약 `393MiB`
- 조회 부하와 수집 배치 구간에서 `app`은 약 `1.12GiB`, `db`는 약 `498MiB`까지 관측됨
- 따라서 현재 Compose 3컨테이너 구조의 권장 최소 사양은 `t4g.medium`
- DB를 RDS로 분리하는 경우에만 `t4g.small` 재검토 가능

---

## 3. DB 테이블 요약

### 1차 구현 (11개) — 지금 바로 사용

| # | 테이블 | 핵심 컬럼 |
|---|--------|-----------|
| 1 | `users` | id, email, password_hash, birth_date, sido/sgg, income_level, employment_status, notification_yn, notification_period |
| 2 | `user_attributes` | user_id, attr_type **VARCHAR(30)**, attr_value |
| 3 | `user_priorities` | user_id, priority_option_id, priority_rank(1~5), weight(2.0/1.6/1.3/1.1/1.0) |
| 4 | `priority_options` | code, label (HOUSING/AMOUNT/ONLINE/YOUTH_ONLY/EDU_JOB/CULTURE/DEADLINE) |
| 5 | `welfare_services` | source_type, source_id, title, description, unified_category, status(ACTIVE/UPCOMING/CLOSED), min/max_age, min/max_income, **ai_score 컬럼 없음** |
| 6 | `welfare_service_details` | service_id(1:1), target_detail, support_detail, apply_method_detail, contact_list(JSON) |
| 7 | `service_regions` | service_id, region_code, sido_name, sgg_name |
| 8 | `service_tags` | service_id, tag_type(INTEREST_THEME/TARGET_GROUP/LIFE_STAGE/KEYWORD), tag_value, **UNIQUE KEY uq_st** |
| 9 | `user_recommendations` | user_id, service_id, recommended_at(DATETIME), rule_base_score, rule_weighted_score, ai_score(NULL가능), ai_reason, rule_weight_used, ai_weight_used, final_score |
| 10 | `recommendation_logs` | user_id, service_id, final_score, rule_weight_used, ai_weight_used, is_fallback, is_clicked, sent_at, clicked_at |
| 11 | `score_weights` | weight_key(COLD_START/GROWTH/STABLE), rule_weight, ai_weight, min_log_count |

### 2차 확장 (9개) — 1차에서 의존 코드 작성 금지

| # | 테이블 | 추가 시점 |
|---|--------|-----------|
| 12 | `user_clusters` | 2D 군집화 구현 시 |
| 13 | `cluster_ai_results` | Batch AI 전환 시 |
| 14 | `batch_jobs` | Batch AI 전환 시 |
| 15 | `normalization_stats` | p5~p95 정규화 고도화 시 |
| 16 | `notifications` | 알림 시스템 구현 시 |
| 17 | `notification_services` | 알림 시스템 구현 시 |
| 18 | `api_sync_logs` | 배치 안정화 후 |
| 19 | `search_logs` | 검색 기능 안정화 후 |
| 20 | `service_view_logs` | 조회수 정교화 시 |

> 전체 DDL → [`backend/src/main/resources/db/schema.sql`](backend/src/main/resources/db/schema.sql)

---

## 4. 현재 구현 단계 (1차)

| 항목 | 1차 구현 | 2차 예정 |
|------|----------|----------|
| 군집 | `youth_all` 고정 | 나이대×소득 2D |
| AI 호출 | 실시간 단건 (RealtimeAiGateway) | Batch API |
| 정규화 | 단순 min-max | p5~p95 + min-max |
| 알림 후보 | top 3 발송 | [A, A, B?] 슬롯 배치 |
| 가중치 | score_weights 테이블 이미 존재 | 값만 튜닝 |

> 추천 파이프라인 상세 → [`docs/recommendation/recommendation-pipeline.md`](docs/recommendation/recommendation-pipeline.md)

---

## 5. 절대 어기면 안 되는 설계 원칙

### 아키텍처
- **Controller는 얇게.** 비즈니스 로직은 반드시 Service로
- **추천 로직 진입점은 RecommendationFacade 하나뿐.** Controller가 개별 Service 직접 호출 금지
- **외부 API는 반드시 Gateway/Client 클래스로 감싸기.** OpenAI·공공API·알림톡 직접 호출 금지

### AI 점수
- `ai_score`는 `welfare_services`에 없음. **`user_recommendations`에만 존재** (유저×서비스 단위)
- `ai_score NULL-safe`: NULL이면 `final_score = norm_rule` (rule만 사용)

### DB 무결성
- `service_tags` 삽입은 **반드시 UPSERT** (INSERT IGNORE 또는 ON DUPLICATE KEY UPDATE). 중복 삽입 시 rule_base_score 이중합산 버그
- `user_attributes.attr_type`은 **VARCHAR(30), ENUM 아님**. 유효성 검증은 Java Enum으로 애플리케이션 레이어에서 처리
- `user_recommendations` 저장 시 `recommended_at(DATETIME)`, `rule_weight_used`, `ai_weight_used` **반드시 함께 기록**

### 점수 계산
- `final_score` 계산 시 **반드시 `score_weights` 테이블 조회**해서 가중치 적용. 하드코딩 금지
- 가중치 단계: `recommendation_logs` 전체 건수 기준 → COLD_START(0~99건): rule 0.8/ai 0.2 → GROWTH(100~499건): 0.6/0.4 → STABLE(500건~): 0.4/0.6

### 데이터 수집
- 수집 시 **`unified_category` 매핑 반드시 포함**
- **`dangerouslySetInnerHTML` 절대 사용 금지.** 수집 데이터는 Jsoup strip 후 저장

### 1차/2차 경계
- **2차 테이블(`user_clusters`, `batch_jobs`, `cluster_ai_results` 등)에 의존하는 코드를 1차에서 작성 금지**

---

## 6. NULL-safe 설계 규칙

| 상황 | 처리 |
|------|------|
| `ai_score` NULL | `final_score = normalize(rule_weighted_score)` (rule만) |
| cluster 없음 | `"youth_all"` fallback |
| 우선순위 미설정 항목 | weight = 1.0 |
| 선택 프로필 미입력 | 해당 가점 건너뜀 (0점 부여하지 않음) |
| CLOSED 정책 ai_score | NULL로 리셋 (매일 새벽 3시 배치) |

---

## 7. 코딩 컨벤션

```java
// 응답 래퍼
ResponseEntity<ApiResponse<T>>

// 예외 처리
throw new CustomException(ErrorCode.XXX);
// GlobalExceptionHandler가 ApiResponse로 변환

// Entity 공통
@MappedSuperclass
public abstract class BaseTimeEntity {
    @CreatedDate LocalDateTime createdAt;
    @LastModifiedDate LocalDateTime updatedAt;
}

// 환경변수 — API 키 절대 하드코딩 금지
// application.yml에서 ${ENV_VAR} 참조
```

---

## 8. 참조 문서

| 문서 | 내용 |
|------|------|
| [`docs/architecture.md`](docs/architecture.md) | 서비스 클래스 역할·호출 흐름 |
| [`docs/recommendation/recommendation-pipeline.md`](docs/recommendation/recommendation-pipeline.md) | 추천 파이프라인 단계별 상세 |
| [`docs/core/api-mapping.md`](docs/core/api-mapping.md) | 공공API 3종 → DB 컬럼 매핑표 |
| [`docs/core/srs-v2.10.md`](docs/core/srs-v2.10.md) | 전체 기능·비기능 요구사항 (FR/NFR) |
| [`docs/archive/project-plan-v11.md`](docs/archive/project-plan-v11.md) | 13주 마일스톤·역할분담·1차/2차 전략·챗봇 모듈 설계 |
