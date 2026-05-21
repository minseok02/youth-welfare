# 사용자 데이터 분리 설계

전체 cross-cutting 구조/데이터 문서 진입점은 [system-docs-index.md](./system-docs-index.md)를 먼저 봅니다.

이 문서는 **사용자 데이터 분리 cut-over 설계와 당시 배경 문제를 보존하는 설계 문서**다.
아래 `users` 중심 단일 구조 문제 진술은 split 이전 배경을 설명하는 부분이며,
현재 runtime truth와 최종 반영 상태는 이 문서의 `현재 반영 상태` 섹션과
[current-state.md](../current-state.md) 를 우선해서 읽는다.

## 왜 당시 구조가 문제였나

분리 작업 이전 프로젝트는 사용자 핵심 데이터가 `users` 한 테이블에 집중되어 있었다.

- 인증 정보: `email`, `password_hash`, `login_fail_count`, `locked_until`
- 고위험 개인정보: `name`, `birth_date`, `phone_enc`
- 추천용 프로필: `sido`, `sgg`, `region_code`, `income_level`, `household_type`, `employment_status`
- 개인 설정: `notification_yn`, `notification_period`, `notification_min_score`, `display_count`

이 구조는 다음 문제가 있다.

1. 한 DB 계정 또는 한 테이블이 유출되면 인증 정보와 개인정보와 서비스 설정이 한 번에 노출된다.
2. 추천과 알림 기능이 민감정보 원문을 직접 가진 `User` 엔티티에 의존한다.
3. 같은 `user_id`를 기준으로 추천 로그, 알림 로그, 프로필이 쉽게 재결합된다.

분리 작업 당시 코드 기준으로도 `User`는 너무 많은 책임을 가지고 있었다.

- 인증과 회원가입은 [AuthLoginService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/service/AuthLoginService.java:22), [AuthSignupService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/service/AuthSignupService.java:13), [AuthSessionService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/service/AuthSessionService.java:10) 로 나뉘었지만, 여전히 최종 상태 저장은 `User` 엔티티 하나를 기준으로 sync된다.
- 프로필 수정과 전화번호 복호화, 알림 설정, 회원탈퇴는 [UserProfileCommandService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/service/UserProfileCommandService.java:23), [UserProfileReadService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/service/UserProfileReadService.java:16), [UserAccountCommandService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/service/UserAccountCommandService.java:17) 로 나뉘었지만, 여전히 `User` 엔티티 하나에 인증/프로필/알림 설정 상태가 함께 모여 있다.
- 추천 파이프라인은 [RecommendationGenerationService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/recommend/service/RecommendationGenerationService.java:18) 에서 `User` 와 추천 snapshot을 함께 읽어 사용한다.
- 실제 `User` 엔티티도 [User](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/entity/User.java:16) 한 클래스에 인증, PII, 추천 프로필, 알림 설정이 함께 있다.

## 목표

목표는 "테이블 분리"가 아니라 "PII와 나머지를 분리하고, 최소권한으로 접근시키는 것"이다.

원칙은 아래로 잡는다.

1. 추천 엔진은 실명, 전화번호, 전체 생년월일을 몰라도 동작해야 한다.
2. 알림 스케줄러는 이메일 발송 대상만 얻으면 되고, 추천 세부 프로필 원문을 몰라도 된다.
3. 인증 계층은 이름, 전화번호, 관심사, 우선순위를 몰라도 로그인/토큰 발급이 가능해야 한다.
4. 하나의 저장소가 유출되어도 다른 저장소 없이는 재식별이 어렵게 해야 한다.
5. 사람 계정과 서비스 계정, 그리고 각 계정의 접근 권한을 분리해야 한다.

## 이번 작업에서 확정한 이행 범위

이번 단계에서 확정하는 범위는 "최종 이상형"이 아니라 현재 코드베이스에 바로 적용 가능한 cut-over 순서다.

확정안은 아래와 같다.

1. 최종 구조는 `2 schema` 유지
   - `youth_welfare`: 인증 해시, 추천 프로필, 추천/알림/수집/로그
   - `youth_welfare_pii`: 이메일 원문, 이름, 생년월일, 전화번호 원문
2. 공용 사용자 식별자는 `Long id`에서 바로 없애지 않고 `user_key`를 먼저 추가
   - 기존 `users.id`는 호환용 내부 PK로 잠시 유지
   - 새 테이블과 새 JWT 기준 식별자는 `user_key`를 사용
3. 이행은 `schema 준비 -> dual-write -> read cut-over -> legacy 제거` 4단계로 진행
4. 추천/검색/알림 로직은 원문 PII 대신 `user_profiles`의 파생 프로필만 읽도록 고정
5. 알림 발송만 `user_pii`의 이메일 복호화 권한을 가진 별도 경로를 사용

즉, 이번 프로젝트의 1차 목표는 "JPA 엔티티를 한 번에 갈아엎기"가 아니라 아래 두 가지를 먼저 안정화하는 것이다.

- `users` 중심 단일 엔티티를 목적별 저장소로 분해할 발판 만들기
- 런타임 경로 중 추천/알림/프로필이 어떤 PII를 정말 필요로 하는지 경계를 고정하기

## 현재 반영 상태

2026-04-28 기준 반영 상태는 아래와 같다.

- 완료
  - `users.user_key` 및 하위 테이블 `user_key` migration/backfill
  - `auth_users`, `user_profiles`, `user_pii` 생성과 core backfill
  - 회원가입/프로필/비밀번호/회원탈퇴 dual-write
  - 로그인/비밀번호 재설정 조회의 `auth_users` 전환
  - `user_pii.email_enc/name_enc/birth_date_enc` 앱 레벨 암호화 backfill
  - 프로필 조회/추천/알림 read path의 `user_profiles + user_pii` 전환
  - `user_attributes`, `user_priorities` 의 `user_key` write sync 및 backfill
  - access token / refresh token / notification unsubscribe token 의 JWT subject 를 `user_key` 로 전환
  - JWT authentication principal 을 custom principal 로 전환하고 controller 인증 경로에서 raw `Long` principal 의존 제거
  - Redis refresh token key, password reset latest-token key 를 `user_key` 기준으로 전환
  - `notifications`, `recommendation_logs`, `service_view_logs`, `chat_sessions` 의 `user_key` write 경로 반영
  - `chat_sessions`, `notifications`, `recommendation_logs` 의 `ManyToOne User` 제거와 `user_key` 기준 read/cleanup 전환
  - `user_recommendations` 의 `ManyToOne User` 제거와 추천/북마크 read path 의 `user_key` 전환
  - legacy runtime 테이블의 `user_id` 호환 컬럼 drop 대상과 migration 순서 설계
  - `user_attributes`, `user_priorities` 의 write/delete 경로를 `user_key` 기준으로 전환하고 `ManyToOne User` 제거
  - runtime 테이블 legacy `user_id` drop migration SQL 작성과 로컬 Docker DB 리허설
  - Docker Compose 신규 볼륨 기준 앱 datasource의 `root` 제거 및 런타임/마이그레이션/PII 계정 시드 추가
  - 알림 대상 이메일 조회를 `notification_pii_ro` secondary datasource로 분리
  - 프로필 조회와 비밀번호 재설정 수신 주소 조회를 `app_pii_rw` secondary datasource로 분리
  - `user_pii` admin backfill write 경로를 `app_pii_rw` secondary datasource로 분리
  - 요청 경로 `user_pii` sync 를 primary `user_pii_sync_queue` 적재 + after-commit `app_pii_rw` upsert 구조로 전환
  - `user_pii_sync_queue` admin replay API 추가
  - `user_pii_sync_queue` fixed-delay 자동 retry 경로 추가
  - `user_pii_sync_queue` status endpoint와 운영 모니터링 기준 정리
  - 기본 datasource의 `user_pii` 직접 접근 제거 (`UserPii` JPA 엔티티 / `UserPiiRepository` 제거)
- 남은 작업
  - 기존 운영 DB에 `app_core_rw`, `app_pii_rw`, `notification_pii_ro`, `migration_admin` 계정 생성 후 앱 datasource 전환
  - 운영 DB에 `V2026_04_28_02__add_user_pii_sync_queue.sql` 적용 및 request dual-write smoke 검증
  - `app_core_rw` 의 `youth_welfare_pii.user_pii` DML 권한 회수
  - `app_core_rw` 권한 회수 후 init/runbook SQL grant 세트 축소
  - 운영 DB에 `V2026_04_28_01__drop_runtime_legacy_user_id.sql` 적용 및 배포 smoke 검증

## 현재 권한 구조의 문제

현재 배포/보안 구조는 이전보다 나아졌지만 권한 경계가 아직 완전히 닫히지는 않았다.

- DB 접속은 [application.yml](/home/minseok/youth-welfare/backend/src/main/resources/application.yml:1) 기준 기본 JPA datasource + `app_pii_rw` + `notification_pii_ro` 보조 datasource 3개를 함께 사용한다.
- 현재 [docker-compose.yml](/home/minseok/youth-welfare/docker-compose.yml:1)은 앱 컨테이너가 `DB_USERNAME` 계정으로 접속하고, 신규 볼륨 초기화 시 `app_core_rw`, `app_pii_rw`, `notification_pii_ro`, `migration_admin` 계정을 함께 생성한다.
- 프로필 조회와 비밀번호 재설정 수신 주소 조회는 `app_pii_rw` 보조 datasource를 통해 `user_pii` 를 읽는다.
- `user_pii` admin backfill 은 primary `users` source 조회와 `app_pii_rw` 의 누락 암호문 조회/수정 2단계로 수행한다.
- 요청 경로 `user_pii` sync 는 primary `user_pii_sync_queue` 에 적재된 뒤 after-commit listener 가 `app_pii_rw` 로 upsert 한다.
- 운영자는 `/api/admin/users/pii-sync-replay` 로 특정 `userKey` 또는 실패/대기 queue batch를 수동 replay 할 수 있다.
- `UserPiiSyncRetryScheduler` 는 `user.pii-sync.retry.*` 설정값으로 `FAILED` 우선, 이후 `PENDING` queue batch를 fixed-delay 재처리한다.
- 운영자는 `/api/admin/users/pii-sync-status` 로 queue 적체 count, oldest pending/failed row, 최근 sync 시각, failed sample 목록을 조회할 수 있다.
- 알림 발송 대상 이메일 조회와 재시도 단건 조회는 `notification_pii_ro` 보조 datasource를 통해 `user_pii(user_key, email_enc)` 만 읽는다.
- API 권한은 [SecurityConfig](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/global/config/SecurityConfig.java:44) 에서 이미 `/api/admin/** -> hasRole("ADMIN")` 으로 막혀 있다.
- 기본 datasource/JPA persistence unit은 더 이상 `user_pii` 를 직접 읽거나 쓰지 않는다.
- 신규 init 스크립트와 계정 템플릿은 이미 `app_core_rw` 의 `user_pii` 권한을 제거한 상태다.
- 남은 일은 기존 운영 DB에서 같은 revoke SQL을 실제 적용하고, 운영 smoke/query 계정 경로를 `migration_admin` 기준으로 확인하는 것이다.
- 관리자 권한은 여전히 `SECURITY_ADMIN_EMAILS` allowlist + `users.email` 조합에 의존하고, DB 안의 역할 테이블이나 서비스 계정 분리는 아직 없다.

즉, 저장소 분리보다 먼저 "누가 무엇에 접근할 수 있는지"를 구조적으로 나눠야 한다.

## 계정 분리 원칙

이 프로젝트에서 분리해야 할 계정은 4종류다.

1. 최종 사용자 계정
   - 일반 회원 로그인 계정
   - 마이페이지, 추천, 북마크만 접근
2. 운영자 사람 계정
   - 수집 재실행, 정책 백필, 장애 대응 같은 운영용
   - 일반 회원 계정과 절대 공유하지 않음
3. 서비스 계정
   - API 서버, 추천 워커, 알림 워커, 수집 워커 같은 머신 계정
   - 사람이 직접 로그인하지 않음
4. 인프라/DB 관리자 계정
   - DB 유지보수, 백업, 스키마 변경 전용
   - 앱 런타임에서 사용 금지

## 권장 분리안

현재 프로젝트에는 `2 schema`가 더 맞다.

- `youth_welfare`
  - 인증
  - 프로필
  - 추천
  - 알림
  - 수집
  - 일반 서비스 데이터
- `youth_welfare_pii`
  - 재식별 가능한 원문 개인정보

핵심 판단:

- 지금 단계에서 `auth` 와 `profile` 을 다른 schema로 나눠도 실질 보안 경계가 거의 생기지 않는다.
- 반면 `PII vs non-PII` 분리는 목적이 명확하고 운영 복잡도도 낮다.
- 따라서 지금은 `2 schema + 테이블 책임 분리 + 계정/권한 분리`가 가장 현실적이다.

## Schema 1. `youth_welfare`

일반 서비스 데이터와 비-PII 사용자 데이터를 저장한다.

권장 테이블:

- `auth_users`
  - `user_key CHAR(32)` UUID hex 문자열
  - `email_lookup_hash CHAR(64)` unique
  - `password_hash`
  - `is_active`
  - `login_fail_count`
  - `locked_until`
  - `withdrawn_at`
  - `created_at`
  - `updated_at`
- `user_profiles`
- `user_key`
  - `age`
  - `age_band`
  - `sido`
  - `sgg`
  - `region_code`
  - `income_level`
  - `household_type`
  - `employment_status`
  - `notification_yn`
  - `notification_period`
  - `notification_min_score`
  - `display_count`
  - `profile_completeness`
  - `has_name`
  - `has_birth_date`
  - `has_phone`
  - `created_at`
  - `updated_at`
- `user_attributes`
- `user_key`
  - `attr_type`
  - `attr_value`
- `user_priorities`
- `user_key`
  - `priority_option_id`
  - `priority_rank`
  - `weight`

추가로 아래 도메인 테이블도 같은 schema에 둔다.

- `welfare_services`
- `welfare_service_details`
- `service_regions`
- `service_tags`
- `score_weights`
- `user_recommendations`
- `recommendation_logs`
- `notifications`
- `notification_services`
- `raw_api_payloads`
- `api_sync_logs`

포인트:

- 추천은 `birth_date` 원문이 아니라 `age` 또는 `age_band`만 사용한다.
- `profile_completeness` 계산에 이름/전화번호/생년월일 원문이 꼭 필요하지 않다. `has_*` 플래그만 있으면 된다.
- 인증 테이블과 프로필 테이블은 같은 schema에 두되, 테이블 책임은 분리한다.

## Schema 2. `youth_welfare_pii`

재식별 가능한 원문 개인정보를 저장한다.

권장 테이블:

- `user_pii`
- `user_key`
  - `email_enc`
  - `name_enc`
  - `birth_date_enc`
  - `phone_enc`
  - `created_at`
  - `updated_at`
- 선택: `user_pii_audit`

포인트:

- 이메일도 여기서는 암호화 원문으로만 둔다.
- 회원가입 중복 체크용 해시는 `youth_welfare.auth_users` 에, 실제 연락처 원문은 `youth_welfare_pii.user_pii` 에 둔다.
- `AesEncryptUtil` 수준에서 끝내지 말고 가능하면 키는 DB 밖 KMS/Vault 또는 최소한 별도 키 관리 정책으로 분리한다.
- 현재 1차 core table migration에서는 `user_pii` 테이블과 `phone_enc` seed만 먼저 만들고, `email_enc/name_enc/birth_date_enc` 백필은 앱 레벨 암호화 dual-write 단계에서 채운다.

## 현재 프로젝트 기준 테이블 재배치

### `youth_welfare` 로 이동

- `users.email` -> `auth_users.email_lookup_hash` 와 인증 상태 필드
- `users.password_hash`
- `users.is_active`
- `users.login_fail_count`
- `users.locked_until`
- `users.withdrawn_at`
- `users.sido`
- `users.sgg`
- `users.region_code`
- `users.income_level`
- `users.household_type`
- `users.employment_status`
- `users.notification_yn`
- `users.notification_period`
- `users.notification_min_score`
- `users.display_count`
- `users.profile_completeness`
- `user_attributes`
- `user_priorities`

### `youth_welfare_pii` 로 이동

- `users.name`
- `users.birth_date`
- `users.phone_enc`
- 가능하면 `users.email` 원문도 여기로 이동

## 현재 코드 기준 영향 범위

실제 cut-over 시 바로 영향받는 코드는 아래다.

- 인증/계정
  - [AuthSignupService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/service/AuthSignupService.java:18)
  - [AuthLoginService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/service/AuthLoginService.java:21)
  - [AuthSessionService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/service/AuthSessionService.java:24)
  - [UserProfileCommandService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/service/UserProfileCommandService.java:34)
  - [UserAccountCommandService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/service/UserAccountCommandService.java:24)
  - [User](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/entity/User.java:16)
- 추천
  - [RecommendationGenerationService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/recommend/service/RecommendationGenerationService.java:18)
  - [RecommendationAccessService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/recommend/service/RecommendationAccessService.java:13)
  - [RuleScoringService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/recommend/service/RuleScoringService.java:24)
  - `UserRecommendation`, `RecommendationLog`
- 알림
  - [NotificationScheduleService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/notification/service/NotificationScheduleService.java:14)
  - [NotificationDispatchService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/notification/service/NotificationDispatchService.java:18)
  - `Notification`
- 프로필/관심사
  - `UserAttribute`, `UserPriority`
- 기타 사용자 종속 로그/세션
  - `ChatSession`
  - `ServiceViewLog`

핵심 결합 지점은 아래 두 가지다.

1. 다수 엔티티가 `@ManyToOne User` 로 직접 묶여 있다.
2. 서비스 레이어가 `users` 한 row에서 인증 정보와 추천 프로필과 PII를 동시에 읽는다.

따라서 분리 작업의 실제 난점은 schema 생성 자체가 아니라 "기존 `user_id` 연관을 어떻게 compatibility window 동안 유지할 것인가"에 있다.

## 핵심 설계 포인트

### 1. `Long userId` 대신 `user_key`

현재는 여러 테이블과 JWT가 내부 증가형 PK에 강하게 묶여 있다.

- 증가형 ID는 추측 가능하다.
- 저장소를 나누면 cross-schema FK와 JPA 연관관계 유지가 오히려 발목을 잡는다.

따라서 공용 식별자는 `user_key` 하나로 통일하고, 각 테이블의 내부 PK는 로컬 PK로만 사용한다.

### 2. 추천은 원문 생년월일 대신 파생값 사용

현재 추천은 `birthDate`로 나이를 계산한다. 이건 굳이 PII 원문을 직접 읽을 이유가 없다.

권장 방식:

- `user_pii.birth_date_enc` 저장
- 동시에 `user_profiles.age`, `age_band`, `age_calculated_at` 갱신
- 매일 새벽 1회 `age` 재계산 배치 실행

이렇게 하면 추천/AI에는 full DOB가 노출되지 않는다.

### 3. 알림은 이메일 원문을 직접 조인하지 말고 배치 조회

현재 알림 경로는 `NotificationScheduleService` / `NotificationDispatchService` 로 나뉘었고, 더 이상 `User` 엔티티 하나에서 이메일과 알림 설정을 같이 보지 않게 정리하는 방향으로 간다. 분리 후에는 이렇게 바꾼다.

1. `youth_welfare.user_profiles` 에서 발송 대상 `user_key` 목록 조회
2. `youth_welfare_pii.user_pii` 에서 해당 `user_key` 의 `email_enc`만 배치 조회 후 복호화
3. 발송

이 구조면 일반 추천/프로필 영역에서 이메일 원문 접근 권한이 필요 없다.

### 4. JPA 엔티티 직접 연관관계 제거

분리 이후에는 아래 패턴을 버리는 것이 맞다.

- `Notification.user`
- `UserRecommendation.user`
- `RecommendationLog.user`
- `UserAttribute.user`
- `UserPriority.user`

대신:

- 저장 테이블에는 `user_key` 컬럼만 둔다.
- 서비스 계층에서 필요한 경우 목적별 DTO를 조합한다.

즉, `User` 중심 엔티티 그래프에서 `UserSnapshot`, `NotificationTarget`, `ProfileView` 같은 DTO 조합 방식으로 바꿔야 한다.

### 5. 최소권한 DB 계정 분리

같은 PostgreSQL 인스턴스 안에 schema만 나누고 앱이 broad 권한으로 전부 붙으면 보안 이점이 거의 없다.

최소한 아래는 필요하다.

- `app_core_rw`
  - `youth_welfare` read/write
  - `youth_welfare_pii` 접근 금지
- `app_pii_rw`
  - `youth_welfare_pii.user_pii` read/write
  - 일반 API에서는 최소 사용
- `notification_pii_ro`
  - `youth_welfare_pii` 에서 `email_enc` 만 읽는 뷰 또는 컬럼 제한 경로
- `migration_job`
  - 배포 시점에만 임시 사용
  - DDL 권한 보유
  - 런타임 앱 컨테이너에서는 사용 금지

secondary datasource URL도 권한 모델과 같이 맞춰야 한다. `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL` 이 같은 host를 쓰더라도 기본 database/schema 이름은 `youth_welfare_pii` 여야 하며, `youth_welfare` 로 남겨 두면 `app_pii_rw` / `notification_pii_ro` 가 연결 단계에서 거부된다.
최신 코드에는 이 조건을 startup validation으로 넣어 두었으므로, 잘못된 URL은 운영 요청 중이 아니라 애플리케이션 부팅 시점에 바로 드러난다.

가능하면 장기적으로는 `youth_welfare_pii` 를 별도 인스턴스로 이동하는 것이 더 낫다.

## 사람 계정 분리

사람 계정도 서비스 계정과 별개로 설계해야 한다.

### 1. 일반 사용자 계정

- `ROLE_USER`
- 자기 프로필, 추천, 북마크, 알림설정만 접근
- 관리자 API 접근 금지

### 2. 운영자 계정

- `ROLE_ADMIN_COLLECT`
- `ROLE_ADMIN_POLICY`
- `ROLE_ADMIN_SUPPORT`
- 필요한 역할만 부여

운영자 계정은 일반 사용자 계정과 분리해야 한다.

- 같은 이메일을 일반 사용자와 관리자에 같이 쓰지 않음
- 운영자 계정은 MFA 필수
- 장기 세션 금지
- 접속 IP 제한 또는 VPN 강제

### 3. 보안/감사 계정

- `ROLE_SECURITY_AUDITOR`
- 개인정보 원문 조회권이 아니라 감사 로그 조회권 중심
- 읽기 전용

### 4. 비상 대응 계정

- break-glass 계정 1개만 별도 보관
- 평소 비활성화
- 사용 시 사유, 시간, 작업자 기록 강제

## API 접근 권한 분리

현재 `/api/admin/**` 는 공개 상태가 아니라 `ROLE_ADMIN` 이 필요하다.
문제는 "공개 여부"보다 "관리자/서비스 계정 모델이 DB 권한 분리와 연결되어 있지 않다"는 점이다.

권장 API 권한은 아래와 같다.

### 공개 API

- `/api/auth/signup`
- `/api/auth/login`
- `/api/auth/refresh`
- `/api/policies`
- `/api/policies/search`
- `/api/policies/ranking`
- `/api/policies/{id}`
- `/api/notifications/unsubscribe`

### 로그인 사용자 API

- `/api/users/me`
- `/api/users/me/profile`
- `/api/users/me/priorities`
- `/api/recommendations/**`
- `/api/bookmarks/**`

요건:

- `ROLE_USER` 이상 필요
- 본인 `user_key`만 접근 가능

### 관리자 API

- `/api/admin/collect/**`
  - `ROLE_ADMIN_COLLECT`
- `/api/admin/policies/**`
  - `ROLE_ADMIN_POLICY`

요건:

- 현재의 `ROLE_ADMIN` 보호 유지
- 운영자 JWT 또는 사내 SSO 연동
- 감사 로그 필수
- rate limit 보다 먼저 인증/인가 수행

## 엔드포인트별 최소 권한 매트릭스

| 기능 | 호출 주체 | youth_welfare | youth_welfare_pii |
|------|-----------|---------------|-------------------|
| 회원가입 | public api | RW | W |
| 로그인 | public api | RW | - |
| 프로필 조회 | user api | R | R |
| 프로필 수정 | user api | RW | 일부 RW |
| 추천 생성 | recommendation worker | R/W | - |
| 알림 발송 | notification worker | R/W | email만 R |
| 정책 수집 | collect worker | RW | - |
| 관리자 수집 실행 | admin api | collect 제어만 | - |

핵심은 프로필 조회 같은 경우에도 앱 내부에서 각 저장소를 목적별로 읽고, 직접 조인 권한을 한 계정에 몰아주지 않는 것이다.

## Spring Security / 계정 모델 변경안

현재 [SecurityConfig](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/global/config/SecurityConfig.java:44) 는 `ROLE_ADMIN` / 일반 사용자 구분은 있다.
하지만 역할이 DB 테이블이 아니라 환경변수 allowlist에서 유도되고, 서비스 계정/사람 계정/감사 계정이 별도 모델로 분리되어 있지 않다.
데이터 분리와 함께 인증/인가 모델도 아래 방향으로 확장하는 것이 맞다.

권장 테이블:

- `auth_users`
- `user_key`
  - `account_type` = `END_USER | ADMIN | SERVICE`
  - `status`
- `auth_roles`
  - `role_code`
- `auth_user_roles`
- `user_key`
  - `role_code`

권장 원칙:

- 일반 사용자와 관리자 계정은 별도 row
- 서비스 계정은 password 로그인 대신 client credential 또는 mTLS 사용
- 관리자 권한은 JWT claim에 role 포함
- DB 권한과 API role은 별도 관리

즉, `ROLE_ADMIN` 이 있다고 해서 DB에 직접 붙는 것은 아니고, 관리자 API를 호출할 수 있다는 뜻이어야 한다.

## 서비스 분리 기준 권한 모델

현재는 백엔드가 단일 Spring Boot 프로세스다. 그래도 논리적으로는 아래 경계로 나눠 설계해야 한다.

### `public-api`

- 회원/로그인/마이페이지
- 사용자 JWT 처리
- DB 계정
  - `app_core_rw`
  - 필요한 경우에만 `app_pii_rw`

### `recommendation-worker`

- 스케줄 기반 추천 생성 또는 갱신
- 사용자 JWT 없음
- DB 계정
  - `app_core_rw`

### `notification-worker`

- 이메일 발송 전용
- DB 계정
  - `app_core_rw`
  - `notification_pii_ro`

### `collect-worker`

- 공공 API 수집과 백필
- DB 계정
  - `app_core_rw`

이렇게 나누면 같은 코드베이스를 쓰더라도 실행 프로파일과 datasource credential을 다르게 가져갈 수 있다.

## 운영 접근 권한 분리

사람이 직접 DB를 만지는 경로도 분리해야 한다.

- 개발자
  - 운영 DB 직접 접근 금지
  - staging까지만 허용
- 운영자
  - admin API 또는 배치 콘솔만 사용
  - DB 직접 접근은 원칙적으로 금지
- DBA
  - 스키마 작업, 백업, 복구만 수행
  - 애플리케이션 사용자 데이터 조회는 사유 승인 필요
- 보안 담당자
  - 감사 로그 조회만 가능

권장 통제:

- 운영 DB bastion host 경유
- 개인별 계정 사용, 공용 계정 금지
- 쿼리 감사 로그 저장
- `SELECT *` 수준의 광범위 조회 차단 또는 승인 기반

## 감사 로그

계정과 권한을 나누면 누가 어떤 데이터에 접근했는지도 남겨야 한다.

최소한 아래 로그는 필요하다.

- 관리자 로그인 성공/실패
- 관리자 API 호출
- PII 조회 이벤트
- 이메일/전화번호 복호화 이벤트
- 권한 변경 이벤트
- break-glass 계정 사용 이벤트

권장 필드:

- `actor_user_key`
- `actor_type`
- `role_code`
- `action`
- `target_user_key`
- `resource_type`
- `result`
- `ip_address`
- `user_agent`
- `occurred_at`

## 이 프로젝트에서 바로 수정해야 할 보안 항목

설계와 별개로 현재 코드 기준 즉시 고쳐야 할 항목은 아래다.

1. 런타임 DB 계정에서 `root` 사용 중단
2. `users` 단일 테이블에 `user_key` 를 먼저 추가하고 하위 테이블 backfill 경로 확보
3. 관리자 계정/역할 테이블 또는 최소한 운영자 사람 계정 전용 저장 구조 도입
4. 수집 워커와 사용자 API의 datasource credential 분리
5. 알림 발송용 프로세스가 `youth_welfare_pii` 전체를 읽지 못하게 column/뷰 단위 제한

## 추천 구현 순서

현재 구조에 가장 덜 위험하게 적용하려면 아래 순서가 좋다.

1. `users` 및 사용자 종속 테이블에 `user_key` 추가
2. `auth_users`, `user_profiles`, `user_pii` 생성 후 dual-write 도입
3. 추천/알림/프로필 조회를 신규 테이블 read path로 전환
4. JWT subject, Redis key, 로그/세션 참조를 `user_key` 로 전환
5. DB 계정을 `root` 에서 기능별 계정으로 전환
6. 워커 프로세스별 datasource credential 분리
7. 감사 로그 추가

## Spring Boot 구조 변경안

현재는 기본 JPA datasource 하나와 보조 datasource 일곱(`admin_dashboard_ro`, `cluster_ai_cleanup_rw`, `recommendation_retention_cleanup_rw`, `collect_execution_lock_cleanup_rw`, `web_push_subscription_cleanup_rw`, `app_pii_rw`, `notification_pii_ro`)을 함께 쓴다. 런타임 `root`는 제거했고 프로필/비밀번호 재설정/알림/backfill/request sync는 목적별 datasource로 분리됐으며, 기본 datasource는 더 이상 `user_pii` 를 직접 다루지 않는다. 신규 init 스크립트와 계정 템플릿에서도 `app_core_rw` 의 `user_pii` DML 권한을 제거했고, admin dashboard read 경로는 `admin_dashboard_ro` `SELECT` 전용 계정으로, `cluster_ai_results` TTL cleanup delete는 `cluster_ai_cleanup_rw` 전용 계정으로, 30일 지난 미북마크 추천 정리 delete는 `recommendation_retention_cleanup_rw` 전용 계정으로, collect execution lock release delete는 `collect_execution_lock_cleanup_rw` 전용 계정으로, 웹푸시 구독 해제 delete는 `web_push_subscription_cleanup_rw` 전용 계정으로 떼었다. 또 `users.name`, `users.birth_date` 는 더 이상 source of truth로 쓰지 않고, 가입/프로필 수정/채팅 나이대 계산은 `user_pii` 암호문과 `user_profiles.age_band` 기준으로 읽는다. 로컬 기존 PostgreSQL 볼륨에는 `V2026_05_21_06__null_duplicated_user_plain_profile_pii.sql` patch를 적용해, `user_pii` 에 암호문이 이미 있는 row의 plain `name` / `birth_date` 를 null 처리한다. 현재 public `users` 에 남는 직접식별자는 login/refresh/admin role resolution이 아직 기대는 `email` 뿐이고, 남은 작업은 이 email 의존을 줄이는 것과 기존 운영 DB에 같은 권한 회수를 실제 반영하는 것, 그리고 `app_core_rw` 의 public schema write 권한을 기능별로 더 잘게 자르는 추가 최소권한화다.

분리 후에는 최소 2개 datasource를 둔다.

- `app.datasource.core`
- `app.datasource.admin-ro`
- `app.datasource.pii`

패키지도 책임 기준으로 나누는 편이 좋다.

- `auth`
  - `AuthUserEntity`
  - `AuthUserRepository`
  - `AuthAvailabilityService`
  - `AuthSignupService`
  - `AuthLoginService`
  - `AuthSessionService`
- `profile`
  - `UserProfileEntity`
  - `UserAttributeEntity`
  - `UserPriorityEntity`
  - `UserProfileReadService`
  - `UserProfileCommandService`
  - `UserAccountCommandService`
- `privateinfo`
  - `UserPiiEntity`
  - `UserNotificationReadService`
  - `UserPiiCommandService`

현재 `user` 패키지는 너무 넓기 때문에 유지하면 다시 결합된다.

## 요청 흐름 변경안

### 회원가입

1. API가 `normalized_email` 생성
2. `youth_welfare.auth_users` 에 `email_lookup_hash`, `password_hash`, `user_key` 저장
3. `youth_welfare_pii.user_pii` 에 `email_enc`, `name_enc`, `birth_date_enc` 저장
4. `youth_welfare.user_profiles` 에 `age`, `sido`, `sgg`, `income_level`, `employment_status`, `household_type`, 초기 설정 저장
5. 실패 복구를 위해 outbox 이벤트 또는 가입 보상 트랜잭션 사용

### 로그인

1. 이메일 정규화
2. `youth_welfare.auth_users.email_lookup_hash` 로 사용자 조회
3. 비밀번호 검증
4. JWT `sub = user_key`

### 프로필 조회

1. `youth_welfare` 에서 설정/추천 프로필 조회
2. `youth_welfare_pii` 에서 `name`, `birth_date`, `phone`, `email` 조회
3. 응답 DTO 조합

### 추천 생성

1. `youth_welfare` 에서 `RecommendationUserSnapshot` 조회
2. `user_attributes`, `user_priorities` 조회
3. 추천 계산
4. 추천 결과 저장 시 `user_key`만 기록

### 알림 발송

1. `youth_welfare` 에서 알림 대상과 설정 조회
2. `youth_welfare_pii` 에서 이메일만 조회
3. 발송 후 `notifications`에 `user_key` 기록

## 이 프로젝트에서 가장 먼저 바꿔야 할 것

우선순위는 아래가 맞다.

1. `users.user_key` 와 하위 테이블 `user_key` 컬럼을 추가해 신규 공용 식별자를 심는다.
2. `auth_users`, `user_profiles`, `user_pii` 를 만들고 회원가입/프로필 수정에 dual-write 를 넣는다.
3. `birth_date` 원문 의존을 `age` / `age_band` 파생값 의존으로 바꾼다.
4. 추천/알림/로그 테이블의 `ManyToOne User` 연관을 `user_key` 스칼라 컬럼으로 바꾼다.
5. 마지막에 JWT subject와 datasource를 `user_key`, `core/pii` 기준으로 전환한다.

## 점진적 마이그레이션 단계

### 1단계: 식별자 준비

- `users` 에 `user_key CHAR(32)` 추가, 기존 row 전체 backfill
- `user_attributes`, `user_priorities`, `user_recommendations`, `recommendation_logs`, `notifications`, `chat_sessions`, `service_view_logs` 에 `user_key` nullable 컬럼 추가
- 기존 `user_id -> users.user_key` 기준으로 backfill
- 이 단계에서는 기존 FK와 `user_id` 컬럼을 유지

이 단계가 먼저 끝나야 이후 테이블 분리 중에도 기존 API와 로그가 끊기지 않는다.

### 2단계: 논리 분리 + dual-write

- 현 DB 내에 `youth_welfare` 와 `youth_welfare_pii` schema 생성
- `auth_users`, `user_profiles`, `user_pii` 작성
- 기존 `users`에서 신규 테이블로 1회 이관 배치 작성
- 회원가입, 프로필 수정, 회원탈퇴에 legacy `users` + 신규 테이블 dual-write 도입
- `user_profiles` 에 `age`, `age_band`, `has_name`, `has_birth_date`, `has_phone` 계산 저장

### 3단계: read path 전환

- 로그인/비밀번호 재설정은 `auth_users` 기준 조회
- 프로필 조회 API는 `user_profiles + user_pii` 조합 DTO로 응답
- 추천은 `RecommendationUserSnapshot` 을 `user_profiles + user_attributes + user_priorities` 기준으로 조회
- 알림 발송은 대상 조회는 `user_profiles`, 이메일 조회는 `user_pii` 로 분리
- `NotificationScheduleService` / `NotificationDispatchService` 와 추천 관련 서비스에서 `User` 전체 엔티티 직접 의존 제거

현재 상태:

- 로그인/비밀번호 재설정 조회 전환 완료
- 프로필 조회/추천/알림 read path 전환 완료
- `user_attributes`, `user_priorities` 의 `user_key` write sync/backfill 완료
- 따라서 이 단계는 운영 호환 조인 없이 `user_key` 직독 기준으로 정리됐다

### 4단계: identity cut-over

- JWT `sub`, refresh token Redis key, 비밀번호 재설정 토큰 사용자 키를 `Long userId`에서 `user_key`로 전환
- `UserRecommendation`, `RecommendationLog`, `Notification`, `ChatSession`, `UserAttribute`, `UserPriority` 의 `ManyToOne User` 제거
- 각 테이블의 `user_key NOT NULL` 제약 확정 후 `user_id` FK 제거

현재 상태:

- access token / refresh token / notification unsubscribe token subject, Redis key, read path 전환은 완료
- `chat_sessions`, `notifications`, `recommendation_logs`, `user_recommendations` 는 `user_key` 기준 read/write 로 정리 완료
- `user_attributes`, `user_priorities` 도 저장/삭제 경로를 `user_key` 기준으로 전환했고 `ManyToOne User` 제거를 반영했다
- 따라서 남은 범위는 runtime 테이블의 실제 `user_id` 인덱스/FK/컬럼 drop SQL 작성과 적용이다

### 4.5단계: legacy `user_id` drop 설계

현재 코드 기준 drop 대상은 한 번에 묶지 않고 아래처럼 나눠야 한다.

- 1차 drop 후보
  - `user_recommendations`
    - 교체 대상: `uq_ur_user_service_time`, `idx_ur_user_score`, `idx_ur_bookmark`, `fk_ur_user`, `user_id`
    - 목표 기준: `(user_key, service_id, recommended_at)`, `(user_key, final_score DESC)`, `(user_key, is_bookmarked)`
  - `recommendation_logs`
    - 교체 대상: `idx_rl_user`, `fk_rl_user`, `user_id`
    - 목표 기준: `idx_rl_user_key_sent` 유지, `user_key NOT NULL`
  - `notifications`
    - 교체 대상: `idx_noti_user_created`, `fk_noti_user`, `user_id`
    - 목표 기준: `idx_noti_user_key_created` 유지, `user_key NOT NULL`
  - `chat_sessions`
    - 교체 대상: `idx_cs_user_last_message`, `idx_cs_user_created`, `fk_cs_user`, `user_id`
    - 목표 기준: `idx_cs_user_key_last_message`, `idx_cs_user_key_created` 유지, `user_key NOT NULL`
  - `service_view_logs`
    - 교체 대상: `idx_svl_user_service_viewed`, `user_id`
    - 목표 기준: 로그인 사용자는 `(user_key, service_id, viewed_at)` dedup, 비로그인은 기존 `client_fingerprint` 기준 유지
- 유지 대상
  - `users.id`
    - 아직 서비스 내부 PK, 일부 DTO, `uid` claim, 운영 추적용 숫자 식별자로 쓰인다
    - 따라서 이번 drop 범위는 하위 runtime 테이블의 호환 `user_id` 컬럼/FK까지만 본다

### 5단계: 물리 분리와 정리

- 필요 시 `youth_welfare_pii` 를 별도 인스턴스로 이동
- 별도 계정/방화벽/백업 정책 적용
- legacy `users` 의 PII 컬럼과 중복 데이터 제거
- 회원탈퇴 시 각 저장소에 scrub event 발행
- PII 데이터는 즉시 또는 정책 시간 경과 후 영구 삭제
- 일반 서비스 테이블은 최소 식별 정보만 남기고 비식별화

## 릴리스별 산출물

각 단계에서 실제로 만들어야 할 산출물도 고정한다.

### Release A

- migration SQL: `users.user_key` + 하위 테이블 `user_key`
- backfill 스크립트 또는 SQL
- 무결성 검증 쿼리

### Release B

- `auth_users`, `user_profiles`, `user_pii` schema/migration
- `AuthSignupService`, `UserProfileCommandService`, `UserAccountCommandService` dual-write
- `ProfileResponse` 조합 DTO 경로 준비
- 기존 `users.email/name/birth_date` -> `user_pii.email_enc/name_enc/birth_date_enc` 앱 레벨 암호화 backfill

### Release C

- 추천/알림/프로필 read path 전환
- `NotificationScheduleService` / `NotificationDispatchService` 이메일 조회 분리
- 나이 파생값 배치 또는 저장 시 재계산 로직

현재 상태:

- 프로필 조회/추천/알림 read path 전환 완료
- `NotificationScheduleService` / `NotificationDispatchService` 이메일 조회 분리 완료
- 나이 파생값은 `UserCoreSyncService` 저장 시 재계산 경로로 반영 중
- `user_attributes/user_priorities.user_key` write sync/backfill 까지 완료

### Release D

- JWT `user_key` 전환
- `ManyToOne User` 제거
- legacy `users` 의 읽기 전용화

현재 상태:

- JWT `user_key` 전환, custom principal 전환, runtime 주요 테이블의 `ManyToOne User` 제거는 완료
- runtime 테이블 legacy `user_id` drop migration SQL과 로컬 Docker DB 리허설까지 완료
- 남은 범위는 운영 DB 적용과 배포 smoke 검증이다

### Release E

- runtime 테이블의 legacy `user_id` 인덱스/FK/컬럼 제거
- `schema.sql` 과 엔티티 `@Table(indexes=...)` 메타데이터를 `user_key` 기준으로 동시 정리
- 운영 DB에 drop SQL 적용 후 무결성/성능 재검증

## 이 설계에서 주의할 점

1. 추천 로그와 알림 로그는 `user_key`만 있어도 장기적으로는 행동 패턴 재식별 위험이 있다.
2. 따라서 로그성 테이블은 TTL 또는 보관기간 정책을 별도로 둬야 한다.
3. `sido + sgg + income_level + employment_status` 조합도 준식별자다. 그래서 이 데이터는 "민감하지 않다"가 아니라 "원문 PII보다 낮은 민감도"로 다뤄야 한다.
4. 같은 애플리케이션 프로세스가 두 schema에 모두 항상 접근 가능하면 분리만으로는 한계가 있다. 워커/API별 권한 분리도 같이 가야 한다.

## 결론

이 프로젝트에 맞는 현재 답은 `3 schema` 가 아니라 아래처럼 `2 schema` 로 단순화하는 것이다.

- `youth_welfare`: 인증, 프로필, 추천, 알림, 수집 등 일반 서비스 데이터
- `youth_welfare_pii`: 이름, 이메일 원문, 전화번호, 생년월일 같은 고위험 PII

그리고 설계의 핵심은 아래 두 가지다.

1. 모든 도메인에서 `Long userId` 대신 공용 `user_key`를 쓰기
2. 추천/알림은 원문 PII가 아니라 파생 프로필과 목적별 DTO만 쓰게 바꾸기

이번 작업 기준으로는 여기에 한 가지를 더 고정했다.

3. `user_key` 선행 도입 없이 곧바로 `users -> auth/profile/pii` 분리를 시도하지 않는다.

지금 단계에선 이 구조가 보안 목표와 운영 복잡도 사이에서 가장 현실적이다.
