# 아키텍처

이 문서는 모듈 구조와 의존 방향을 빠르게 확인하기 위한 문서입니다.
전체 cross-cutting 구조 문서 진입점은 [system-docs-index.md](./system-docs-index.md)를 봅니다.
추천 점수 계산의 세부 흐름은 [recommendation-pipeline.md](./recommendation-pipeline.md)를 봅니다.

## 전체 구조

```text
React
  -> Spring Boot API
      -> MySQL
      -> Redis
      -> 공공 API
      -> OpenAI API
      -> Gmail SMTP
```

## 배포 토폴로지

현재 기본 운영 토폴로지는 아래와 같다.

```text
EC2 1대
  -> Docker Compose
      -> app
      -> db
      -> redis
  -> nginx(선택)
```

사이징 메모:

- `app + db + redis` 동시 운영을 한 호스트에서 유지할 경우 권장 최소 사양은 `t4g.medium`(4GB RAM)
- 2026-04-25 로컬 Docker 실측에서 조회 부하 + 수집 배치 구간 기준 `app`은 약 `1.12GiB`, `db`는 약 `498MiB`까지 관측됨
- `t4g.small`은 DB까지 같은 호스트에 둘 때 메모리 여유가 부족하므로 비권장
- DB를 RDS로 분리한 `app-only` 구조라면 더 작은 인스턴스를 다시 검토할 수 있음

## 백엔드 모듈

| 모듈 | 역할 |
|------|------|
| `global` | 공통 응답, 예외, 보안, JWT, 암호화, 설정 |
| `user` | 회원, 프로필, 우선순위, 인증 관련 사용자 상태 |
| `policy` | 정책 조회, 검색, 상세, 랭킹, 조회 로그 |
| `collect` | 공공 API 수집, 원문 저장, 수집 실행 로그 |
| `recommend` | 추천 후보 검색, 점수 계산, AI 호출, 추천 저장 |
| `notification` | 이메일 알림, 발송 이력, 실패 재시도 |

## 요청 흐름

### 인증

```text
AuthController
  -> AuthAvailabilityService / AuthSignupService / AuthLoginService / AuthSessionService
      -> AuthIdentityReadService
      -> UserRegistrationService
      -> AuthTokenService
```

### 정책 조회

```text
PolicyController
  -> PolicyListService / PolicyDetailService / PolicyBookmarkCommandService
  -> PolicySearchService / PolicyRankingService
      -> WelfareServiceReadRepository
      -> PolicyPresentationReadService
      -> PolicyDetailReadService
      -> PolicyLookupService
```

### 추천

```text
RecommendationController
  -> RecommendationGenerationService
  -> RecommendationAccessService
  -> RecommendationBookmarkCommandService
      -> ClusterService
      -> RetrievalService
      -> RuleScoringService
      -> AiScoringService
      -> ScoreWeightService
      -> ReRankingService
      -> RecommendationPersistenceService
```

세부 계산 규칙은 [recommendation-pipeline.md](./recommendation-pipeline.md)에 둡니다.

### 수집

```text
CollectAdminController / Scheduler
  -> CollectBatchService / CollectAdminService
      -> CollectSourceExecutionService
      -> YouthApiClient
      -> BokjiroCentralClient
      -> BokjiroLocalClient
      -> BokjiroDetailCollectService
      -> CollectItemSaver
      -> RawApiPayloadService
      -> ApiSyncLogService
```

수집 운영 기준은 [collect-ops.md](./collect-ops.md)에 둡니다.

### 알림

```text
NotificationScheduleService
  -> NotificationDispatchService
  -> NotificationRetryService
NotificationDispatchService
  -> NotificationRecommendationService
  -> NotificationMessageService
```

### 관리자 대시보드

```text
AdminDashboardController
  -> AdminDashboardSummaryService
  -> AdminDashboardSearchService
  -> AdminDashboardRecommendationService
  -> AdminDashboardCollectService
      -> AdminDashboard*ReadRepository
```

## 주요 설계 원칙

- Controller는 도메인 Service 또는 Facade만 호출합니다.
- 외부 API 호출은 gateway/client 계층에 둡니다.
- 추천 API 상단은 생성/조회/북마크 command service로 나누고, 계산 세부 로직은 recommendation 서비스들 아래에 둡니다.
- 수집은 source별 부분 성공을 허용하고, 실행 결과는 `api_sync_logs`에 남깁니다.
- 2차 기능은 1차 구현체를 직접 흔들지 않고 내부 구현체 교체로 붙입니다.

## 1차와 2차 경계

| 영역 | 1차 | 2차 |
|------|-----|-----|
| 추천 군집 | `youth_all` 고정 + 개인화 추천 결과 직접 계산 | 사용자 규모가 충분히 커질 때만 나이대 x 소득분위 군집 재검토 |
| AI 호출 | 실시간 호출 | Batch API |
| 정규화 | 단순 min-max | p5~p95 클리핑 |
| 알림 | 이메일 `[A, A, B?]` 슬롯 발송 | 추가 채널은 운영 자격/규모 충족 시 재검토. 카카오 알림톡은 현재 blocked |
| 챗봇 | 제외 | 별도 모듈 |
