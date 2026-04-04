# 개발 Phase 계획

> 작업 단위별 구현 순서. 각 Phase 완료 후 체크.
> 선행 조건 Phase가 완료되어야 다음 Phase 진행 가능.

---

## 진행 현황

| Phase | 제목 | 상태 |
|-------|------|:----:|
| 0 | 프로젝트 초기 설정 | ✅ |
| 1-A | global 패키지 | ✅ |
| 1-B | DB 스키마 | ✅ |
| 2-A | user 도메인 Entity·Repository | ✅ |
| 2-B | user 도메인 Service | ✅ |
| 2-C | user 도메인 Controller·DTO | ✅ |
| 3-A | policy 도메인 Entity·Repository | ✅ |
| 3-B | policy 도메인 Service·Controller | ✅ |
| 4-A | collect 도메인 DTO·Gateway | ✅ |
| 4-B | collect 도메인 Mapper·Service | ✅ |
| 5-A | recommend 도메인 Entity·Repository | ✅ |
| 5-B | recommend 파이프라인 1단계 | ✅ |
| 5-C | recommend AI 게이트웨이 | ✅ |
| 5-D | recommend 점수 계산·저장 | ✅ |
| 5-E | recommend Facade·Controller | ✅ |
| 6 | notification 도메인 | ✅ |
| 7 | 보안·비기능 요구사항 검증 | ✅ |
| 8 | 통합 검증·배포 | 🔄 진행 중 |

---

## PHASE 0 — 프로젝트 초기 설정

**선행 조건**: 없음

### 작업 목록
- [ ] Spring Boot 3.x 프로젝트 생성 (`com.example.welfare`)
- [ ] `build.gradle` 의존성 추가
  - Spring Web, Spring Data JPA, Spring Security
  - Spring Validation, Spring Mail
  - MySQL Connector, Lombok
  - jjwt (JWT), jackson-dataformat-xml (XXE 비활성화용)
  - Jsoup (HTML strip)
  - Spring Boot Actuator
- [ ] `application.yml` 작성
  - DB 연결 (`${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`)
  - JWT secret (`${JWT_SECRET}`)
  - OpenAI API key (`${OPENAI_API_KEY}`)
  - CoolSMS key (`${COOLSMS_API_KEY}`)
  - Gmail SMTP (`${GMAIL_USERNAME}`, `${GMAIL_PASSWORD}`)
  - JPA ddl-auto: validate
- [ ] `.env.example` 파일 생성 (실제 값 없이 키 목록만)
- [ ] `docker-compose.yml` 작성 (Spring + MySQL 2컨테이너)
- [ ] `Dockerfile` 작성 (ARM Graviton2 호환)

### 산출물
```
backend/
├── build.gradle
├── Dockerfile
├── src/main/resources/
│   ├── application.yml
│   └── application-local.yml   ← .gitignore 적용
.env.example
docker-compose.yml
```

---

## PHASE 1-A — global 패키지

**선행 조건**: PHASE 0

### 작업 목록
- [ ] `ApiResponse<T>` — 공통 응답 래퍼 (success, data, message, errorCode)
- [ ] `ErrorCode` enum — 에러 코드·메시지 정의
- [ ] `CustomException` — ErrorCode를 받는 런타임 예외
- [ ] `GlobalExceptionHandler` — `@RestControllerAdvice`, CustomException → ApiResponse 변환
- [ ] `BaseTimeEntity` — `@MappedSuperclass`, createdAt·updatedAt (`@CreatedDate`, `@LastModifiedDate`)
- [ ] `JwtUtil` — Access Token 생성(30분) / Refresh Token 생성(7일) / 검증
- [ ] `AesEncryptUtil` — AES-256 암호화·복호화 (전화번호용)

### 패키지 위치
```
com.example.welfare.global/
├── config/
├── exception/   ← ErrorCode, CustomException, GlobalExceptionHandler
├── response/    ← ApiResponse
├── entity/      ← BaseTimeEntity
└── util/        ← JwtUtil, AesEncryptUtil
```

---

## PHASE 1-B — DB 스키마

**선행 조건**: PHASE 0

### 작업 목록
- [ ] MySQL 컨테이너 기동 확인
- [ ] 1차 11개 테이블 DDL 실행 (순서 중요 — FK 의존성)
  1. `priority_options`
  2. `users`
  3. `user_attributes`
  4. `user_priorities`
  5. `welfare_services`
  6. `welfare_service_details`
  7. `service_regions`
  8. `service_tags`
  9. `score_weights`
  10. `user_recommendations`
  11. `recommendation_logs`
- [ ] `score_weights` 초기 데이터 INSERT (COLD_START / GROWTH / STABLE)
- [ ] `priority_options` 초기 데이터 INSERT (7개)
- [ ] `src/main/resources/db/migration/` 또는 `schema.sql`에 DDL 보관

> DDL 전문: [`docs/db-schema-design.md`](db-schema-design.md)

---

## PHASE 2-A — user 도메인 Entity·Repository

**선행 조건**: PHASE 1-A, 1-B

### 작업 목록
- [ ] `User` Entity
  - `login_fail_count`, `locked_until` (로그인 잠금)
  - `profile_completeness` (완성도 %)
  - `withdrawn_at` (탈퇴 비식별화)
  - `notification_yn`, `notification_period`, `notification_min_score`
- [ ] `UserAttribute` Entity — `attr_type` VARCHAR(30), Java Enum으로 유효성 검증
- [ ] `UserPriority` Entity
- [ ] `PriorityOption` Entity
- [ ] `UserRepository`, `UserAttributeRepository`, `UserPriorityRepository`, `PriorityOptionRepository`

---

## PHASE 2-B — user 도메인 Service

**선행 조건**: PHASE 2-A

### 작업 목록
- [ ] `AuthService`
  - 회원가입 (BCrypt 해싱, 중복 이메일 검증)
  - 로그인 (실패 5회 → 30분 잠금)
  - JWT Access Token 발급 (30분)
  - Refresh Token 발급·저장 (7일, HttpOnly 쿠키)
  - Refresh Token Rotation (재발급 시 기존 무효화)
  - Reuse Detection (이미 사용된 토큰 재사용 시 전체 무효화)
  - 로그아웃 (Refresh Token 무효화)
- [ ] `UserService`
  - 프로필 조회·수정
  - `profile_completeness` 계산 (필수/선택 필드 입력률)
  - 회원탈퇴 비식별화 (`email → 'withdrawn'`, 개인정보 전체 NULL)
  - `user_attributes`, `user_priorities` 즉시 삭제

---

## PHASE 2-C — user 도메인 Controller·DTO

**선행 조건**: PHASE 2-B

### 작업 목록
- [ ] `AuthController` — `/api/auth`
  - `POST /signup` — 회원가입
  - `POST /login` — 로그인
  - `POST /refresh` — Access Token 재발급
  - `POST /logout` — 로그아웃
- [ ] `UserController` — `/api/users`
  - `GET /me` — 프로필 조회
  - `PUT /me` — 프로필 수정
  - `PUT /me/priorities` — 우선순위 설정
  - `DELETE /me` — 회원탈퇴
- [ ] 요청·응답 DTO 전체 (Validation 어노테이션 포함)

---

## PHASE 3-A — policy 도메인 Entity·Repository

**선행 조건**: PHASE 1-B

### 작업 목록
- [ ] `WelfareService` Entity
  - `ai_score` 컬럼 **없음**
  - `unified_category` 포함
  - FULLTEXT index (`title`, `description`, `support_content`, `keyword`)
- [ ] `WelfareServiceDetail` Entity (1:1)
- [ ] `ServiceRegion` Entity (1:N)
- [ ] `ServiceTag` Entity — UNIQUE KEY `uq_st (service_id, tag_type, tag_value)`
- [ ] 각 Repository (`WelfareServiceRepository`, `WelfareServiceDetailRepository`, `ServiceRegionRepository`, `ServiceTagRepository`)
- [ ] `WelfareServiceRepository` — FULLTEXT 검색 쿼리, 필터 쿼리

---

## PHASE 3-B — policy 도메인 Service·Controller

**선행 조건**: PHASE 3-A

### 작업 목록
- [ ] `PolicyService`
  - 목록 조회 (20건 페이징, unified_category·지역·상태·출처 필터, 정렬)
  - 상세 조회 (`welfare_service_details` JOIN, 실시간 API 호출 없음)
  - 조회수 증가 (`view_count` 자체 / `api_view_count` 원본 API 분리)
  - 30분 이내 재조회 시 조회수 미증가
  - 북마크 추가·해제
- [ ] `PolicySearchService` — FULLTEXT ngram 키워드 검색, 관련도+조회수 정렬
- [ ] `PolicyController` — `/api/policies`
  - `GET /` — 목록
  - `GET /{id}` — 상세 (`?log_id=` 파라미터로 클릭 추적)
  - `GET /search` — 검색
  - `POST /{id}/bookmark` — 북마크
  - `GET /ranking` — 조회수 랭킹

---

## PHASE 4-A — collect 도메인 DTO·Gateway

**선행 조건**: PHASE 3-A

### 작업 목록
- [ ] `YouthApiDto` — JSON 응답 매핑
- [ ] `BokjiroCentralDto` — XML 응답 매핑
- [ ] `BokjiroLocalDto` — XML 응답 매핑
- [ ] `YouthApiClient` — WebClient, JSON
- [ ] `BokjiroCentralClient` — WebClient, XML, **XXE 비활성화 필수**
  ```java
  xmlMapper.configure(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
  xmlMapper.configure(XMLInputFactory.SUPPORT_DTD, false);
  ```
- [ ] `BokjiroLocalClient` — WebClient, XML, **XXE 비활성화 필수**
- [ ] API 키 절대 하드코딩 금지 → `${YOUTH_API_KEY}`, `${BOKJIRO_API_KEY}` 환경변수

> API 필드 매핑: [`docs/api-mapping.md`](api-mapping.md)

---

## PHASE 4-B — collect 도메인 Mapper·Service

**선행 조건**: PHASE 4-A

### 작업 목록
- [ ] `WelfareServiceMapper`
  - 3종 DTO → `WelfareService` Entity 공통 변환
  - `unified_category` 매핑 (10개 규칙 — `docs/api-mapping.md` 참조)
  - Jsoup HTML strip 후 저장 (`dangerouslySetInnerHTML` 절대 금지)
  - `service_tags` UPSERT (`ON DUPLICATE KEY UPDATE`)
  - `service_regions` 분기 (온통청년: 콤마 분해 / 복지로중앙: 전국 1건 / 복지로지자체: ctpvNm+sggNm)
- [ ] `CollectService` — `@Scheduled(cron = "0 0 2 * * *")`
  - API별 독립 실행 (하나 실패가 다른 API에 영향 없음)
  - 실패 시 1시간 후 재시도, 최대 2회
  - welfare_services UPSERT (`source_type + source_id`)
- [ ] `StatusUpdateService` — `@Scheduled(cron = "0 0 3 * * *")`
  - 만료 정책 `status = CLOSED`
  - 해당 정책의 `user_recommendations.ai_score = NULL` 리셋

---

## PHASE 5-A — recommend 도메인 Entity·Repository

**선행 조건**: PHASE 1-B

### 작업 목록
- [ ] `UserRecommendation` Entity
  - `recommended_at DATETIME` (DATE 아님)
  - `ai_score` nullable
  - `ai_reason`
  - `rule_weight_used`, `ai_weight_used`
- [ ] `RecommendationLog` Entity
  - `is_fallback`, `is_clicked`
  - `rule_weight_used`, `ai_weight_used`
  - 영구 보관 (삭제 배치 없음)
- [ ] `ScoreWeight` Entity (COLD_START / GROWTH / STABLE)
- [ ] `UserRecommendationRepository`, `RecommendationLogRepository`, `ScoreWeightRepository`

---

## PHASE 5-B — recommend 파이프라인 1단계

**선행 조건**: PHASE 5-A, 3-A

### 작업 목록
- [ ] `ClusterService`
  - 1차: `assignCluster(User)` → 항상 `"youth_all"` 반환
- [ ] `RetrievalService`
  - SQL WHERE 필터: 나이 / 지역 / 소득 / 취업상태 (NULL이면 통과)
  - 상위 K=50건 선별 (`view_count DESC`)
  - 신규 정책 강제 포함: 수집 후 24시간 이내 + `rule_base_score` 최솟값 M=5건
- [ ] `RuleScoringService`
  - if-else 기본 가점 6항목 → `rule_base_score`
    - 청년전용 +20 / 지원금 100만+ +15 / 온라인신청 +10
    - 지역일치 +10 / 관심분야 +10 / 마감임박(7일) +5
  - 우선순위 가중치 적용 → `rule_weighted_score`
    - 복수 매칭 시 최고 배율 1개만 적용 (이중합산 방지)

---

## PHASE 5-C — recommend AI 게이트웨이

**선행 조건**: PHASE 5-B

### 작업 목록
- [ ] `AiRecommendationGateway` 인터페이스
  ```java
  List<AiScoreResult> score(String clusterId, List<PolicyCandidate> candidates, UserProfile profile);
  ```
- [ ] `RealtimeAiGateway` — 1차 구현체
  - OpenAI GPT-4o-mini 실시간 호출
  - 군집 범주값만 전송 (개인 식별 정보 절대 제외 — NFR-02-12)
  - 응답: `ai_score` 0~100, `ai_reason` 1문장
- [ ] `AiScoringService`
  - Gateway 호출
  - NULL-safe: 실패·타임아웃 시 `null` 반환, 이후 rule만 사용

---

## PHASE 5-D — recommend 점수 계산·저장

**선행 조건**: PHASE 5-C

### 작업 목록
- [ ] `ScoreWeightService`
  - `recommendation_logs` 전체 count 조회
  - `score_weights` 테이블에서 해당 단계 조회
  - **가중치 절대 하드코딩 금지**
- [ ] `ScoreNormalizer`
  - 1차: 단순 min-max
  - `max == min` 이면 `0.5` 반환
- [ ] `ReRankingService`
  - `ai_score` 있음: `final_score = norm_rule × rule_weight + norm_ai × ai_weight`
  - `ai_score` NULL: `final_score = norm_rule`
- [ ] `RecommendationPersistenceService`
  - `user_recommendations` INSERT
  - `recommended_at`, `rule_weight_used`, `ai_weight_used` 반드시 함께 기록
- [ ] `RecommendationLogService`
  - `recommendation_logs` INSERT (`is_fallback` 포함)
  - `markClicked(logId)` — `is_clicked = TRUE`, `clicked_at` 업데이트

---

## PHASE 5-E — recommend Facade·Controller

**선행 조건**: PHASE 5-D

### 작업 목록
- [ ] `RecommendationFacade` — 파이프라인 오케스트레이터
  ```
  ① ClusterService.assignCluster()
  ② RetrievalService.retrieve()
  ③ RuleScoringService.score()
  ④ AiScoringService.score()
  ⑤ ScoreWeightService.getActiveWeight()
  ⑥ ReRankingService.rerank()
  ⑦ RecommendationPersistenceService.save()
  ```
  - Controller가 개별 Service 직접 호출 **금지**
- [ ] `RecommendationController` — `/api/recommendations`
  - `POST /` — 추천 실행 (Facade 호출)
  - `GET /` — 추천 목록 조회 (DB만, AI 재호출 없음)
  - 응답: `final_score` + `ai_reason` + 우선순위 태그 + 신규 뱃지

---

## PHASE 6 — notification 도메인

**선행 조건**: PHASE 5-E

### 작업 목록
- [ ] `KakaoAlimtalkClient` — CoolSMS SDK 래핑
  - 정책명 + `ai_reason` 템플릿 삽입
  - API 키 환경변수 (`${COOLSMS_API_KEY}`)
- [ ] `EmailClient` — Spring Mail + Gmail SMTP
  - API 키 환경변수 (`${GMAIL_USERNAME}`, `${GMAIL_PASSWORD}`)
- [ ] `NotificationService` — `@Scheduled(cron = "0 0 8 * * *")`
  - 1차: `notification_yn = 1` 유저 대상 top 3 발송
  - 발송 실패 시 30분 / 2시간 후 최대 2회 재시도
  - 카카오 실패 → 이메일 폴백
  - 수신거부 링크 포함
  - 발송 시 `recommendation_logs` INSERT 연동

---

## PHASE 7 — 보안·비기능 요구사항 검증

**선행 조건**: PHASE 6

### 체크리스트
- [ ] JWT Refresh Token Rotation + Reuse Detection 동작 확인
- [ ] 로그인 실패 5회 → 30분 잠금 동작 확인
- [ ] XML XXE 비활성화 설정 확인 (BokjiroClient 2종)
- [ ] Jsoup HTML strip 적용 확인 / `dangerouslySetInnerHTML` 코드 없음 확인
- [ ] 전화번호 AES-256 암호화·복호화 확인
- [ ] CORS: React origin만 허용 확인
- [ ] API 키 `.env`에만 존재, 코드에 하드코딩 없음 확인
- [ ] `profile_completeness` 계산 로직 확인
- [ ] AI 프롬프트에 개인 식별 정보 없음 확인 (군집 범주값만)
- [ ] `service_tags` UPSERT (중복 삽입 방지) 확인
- [ ] `score_weights` 하드코딩 없음 확인

---

## PHASE 8 — 통합 검증·배포

**선행 조건**: PHASE 7

### 작업 목록
- [ ] E2E 추천 파이프라인 테스트
  - Cold Start (logs 0~99건) → rule 0.8 / ai 0.2 확인
  - GROWTH (100~499건) → rule 0.6 / ai 0.4 확인
  - STABLE (500건~) → rule 0.4 / ai 0.6 확인
- [ ] CTR 분석 쿼리 실행
  ```sql
  -- AI vs Fallback CTR
  SELECT is_fallback, COUNT(*), SUM(is_clicked),
         ROUND(SUM(is_clicked)*100.0/COUNT(*),1) AS ctr_pct
  FROM recommendation_logs GROUP BY is_fallback;

  -- 가중치 단계별 CTR
  SELECT rule_weight_used, ai_weight_used,
         ROUND(SUM(is_clicked)*100.0/COUNT(*),1) AS ctr_pct
  FROM recommendation_logs GROUP BY rule_weight_used, ai_weight_used;
  ```
- [ ] Docker Compose EC2 t4g.large (ARM Graviton2) 배포
- [ ] HTTPS 설정
- [ ] 데모 시나리오 실행 (CTR 분석 결과 포함)

---

## 병렬 작업 가능 구간

```
PHASE 0 완료 후:
  ┌── PHASE 1-A (global)
  └── PHASE 1-B (DB 스키마)   ← 동시 진행 가능

PHASE 1-A + 1-B 완료 후:
  ┌── PHASE 2-A/B/C (user)
  └── PHASE 3-A (policy Entity)   ← 동시 진행 가능

PHASE 3-A 완료 후:
  ┌── PHASE 3-B (policy Service)
  ├── PHASE 4-A (collect Gateway)
  └── PHASE 5-A (recommend Entity)   ← 동시 진행 가능
```
