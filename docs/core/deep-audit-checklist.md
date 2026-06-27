# Deep Audit Checklist

이 문서는 프로젝트를 URL/URI부터 DB, 수집, 추천, 운영까지 한 단계씩 깊게 확인하기 위한 진행 기록이다.

## 1. URL / URI / 라우팅

상태: 1차 코드 점검 완료, 보완 후보 있음.

### 확인한 기준 파일

- 프론트 라우터: `frontend/src/router/index.jsx`
- API 클라이언트: `frontend/src/lib/axios.js`
- 내부 이동 방어: `frontend/src/lib/safeNavigation.js`
- 백엔드 보안 매핑: `backend/src/main/java/com/example/welfare/global/config/SecurityConfig.java`
- origin 방어 필터: `backend/src/main/java/com/example/welfare/global/config/TrustedOriginFilter.java`
- 운영 edge 설정: `deploy/nginx/youth-welfare.conf`, `deploy/nginx/youth-welfare.bootstrap.conf`
- 운영 env 예시: `env.production.example`

### 프론트 페이지 라우트

- 공개 라우트: `/`, `/guide`, `/support`, `/login`, `/signup`, `/terms`, `/privacy`, `/reset-password`, `/notifications/unsubscribe`, `/policies`, `/policies/:id`
- 로그인 필요 라우트: `/alerts`, `/mypage`, `/chat`
- 관리자 필요 라우트: `/admin/dashboard`
- 확인 결과: 보호 라우트는 `RequireLogin` 또는 `RequireAdmin`으로 감싸져 있다.
- 보완 완료: React Router 마지막에 `*` catch-all을 추가했고, `NotFoundPage`에서 홈/정책 검색 복구 CTA를 제공한다.

### API base URL / origin 계약

- 프론트는 `VITE_API_BASE_URL`이 비어 있으면 same-origin 상대 경로로 `/api/...`를 호출한다.
- 개발 서버는 `frontend/vite.config.js`에서 `/api`를 `VITE_API_PROXY_TARGET` 또는 `VITE_API_BASE_URL` 또는 `http://127.0.0.1:8082`로 proxy한다.
- axios는 `configuredApiOrigin()`과 `/api/` prefix를 모두 만족하는 요청에만 Bearer token을 붙인다.
- 확인 결과: 외부 URL로 Authorization header가 새는 구조는 아니다.
- 확인 결과: `frontend/.env.example`은 `VITE_API_BASE_URL`을 비워 same-origin 기본값으로 두고, nginx는 `/api/`를 `127.0.0.1:8082`로 proxy한다.
- 보완 완료: nginx CSP `connect-src`와 edge 검증 스크립트는 `self`, `https://youthmoa.kr`, `https://www.youthmoa.kr` 를 모두 확인한다. 별도 API origin을 쓰면 `VITE_API_BASE_URL`, backend CORS, nginx CSP를 같이 바꿔야 한다.

### 내부 이동 / redirect 방어

- `resolveSafeInternalPath()`는 빈 값, 2048자 초과, control character, `//evil.example`, 다른 origin URL을 거부한다.
- `resolveSafeRouteTarget()`는 `pathname/search/hash/state`를 재검증하고, 중첩 `from/chatFrom`은 depth 3까지만 허용한다.
- 알림 deeplink는 `Header`, `MyPage`, `AlertsPage`에서 `resolveSafeInternalPath()`를 거친다.
- 확인 결과: 로그인 후 복귀 경로와 알림 deeplink의 open redirect 방어가 있다.
- 보완 완료: `safeNavigation.test.js`에서 외부 origin, protocol-relative URL, control character, oversized path, nested route state depth, post-login action sanitizer를 단위 테스트로 고정했다.

### 백엔드 API 매핑

- 공개 인증 API: `POST /api/auth/check-email`, `/email-verification/send`, `/email-verification/verify`, `/signup`, `/password-reset/request`, `/password-reset/confirm`, `/login`, `/refresh`, `/logout`
- 공개 정책 API: `GET /api/policies`, `/api/policies/{id}`, `/api/policies/search/trending`, `/api/policies/ranking`, `POST /api/policies/search`, `/api/policies/search/suggestions`
- 로그인 필요 API: `/api/users/me/**`, `/api/recommendations/**`, `/api/chat/sessions/**`, `/api/notifications/me/**`, push subscription, 북마크, 정책 오류 신고
- 관리자 API: `/api/admin/**`, Swagger 문서
- 확인 결과: 프론트에서 호출하는 주요 API와 백엔드 매핑은 일치한다.
- 확인할 점: 프론트에서 현재 사용하지 않는 관리자 API도 문서화된 운영 runbook과 맞는지 별도 점검이 필요하다.

### path/query parameter 검증

- 정책 공개 API는 `@Min`, `@Max`, `@Size`, `validatePublicPageable()`와 rate limit이 들어가 있다.
- 관리자 수집의 `sourceKey`는 `@Size(max=40)`와 `^[a-z0-9-]+$` 패턴 검증이 있다.
- 알림 `subscriptionId`, `alertId`는 컨트롤러에 `@Min(1)`을 추가했고, 서비스에서도 `findByIdAndUserKey`로 소유자 조건을 강제한다.
- 사용자 동의 `consentType`은 서비스에서 enum으로 파싱하고 허용되지 않는 동의 철회는 거부한다.
- 공식 코드북 `codeSetKey`는 서비스의 in-memory map 조회 실패 시 `REFERENCE_NOT_FOUND`를 반환한다.
- 보완 완료: 컨트롤러 숫자 path variable은 정책/추천/채팅/알림 경로에서 `@Min(1)`을 사용한다. 관리자 수집 path variable은 문자열 `sourceKey`라 `@Size`/`@Pattern` 검증 대상이다.

### CORS / CSRF 성격의 origin 방어

- CORS는 allow credentials가 켜져 있고, `security.cors.allowed-origins` 값만 허용한다.
- `TrustedOriginFilter`는 cookie가 관여하는 `POST /api/auth/refresh`, `/api/auth/logout`, `/api/notifications/unsubscribe`에 Origin/Referer allowlist를 요구한다.
- 확인 결과: refresh/logout/unsubscribe에 대한 browser-origin 방어 테스트가 있다.
- 보완 완료: refresh cookie는 `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/auth` 기준이고, 운영 preflight는 `AUTH_REFRESH_COOKIE_SECURE=true` 를 fail-fast 한다.
- 보완 완료: prod profile은 `server.forward-headers-strategy=framework` 를 쓰며 nginx proxy는 `X-Forwarded-Proto`, `X-Forwarded-Port`, `X-Forwarded-Host` 를 명시한다.
- 보완 후보: 로그인/회원가입/문의 등 공개 mutation은 CSRF보다 abuse/rate-limit 성격이 크므로 현재 rate limit 점검 대상이다. 인증/인가 섹션에서 이어서 본다.

### 운영 도메인 불일치

- nginx, 운영 env 예시, 대부분의 운영 문서는 `https://youthmoa.kr`를 기준으로 한다.
- 보완 완료: 루트 `.env.example`, `NotificationMessageService` 기본값, 알림/web push 테스트 fixture를 `https://youthmoa.kr` 기준으로 통일했다.
- 확인할 점: 실제 운영 secret 파일도 같은 값을 쓰는지 배포 전 `preflight-runtime-cutover-env.sh`와 edge smoke에서 확인한다.

### 다음 단계로 넘길 항목

- 인증/인가 섹션에서 refresh cookie, admin role, public mutation rate limit을 이어서 확인.

## 2. 인증 / 인가

상태: 1차 코드 점검 완료, 보완 후보 있음.

### 확인한 기준 파일

- 인증 컨트롤러: `backend/src/main/java/com/example/welfare/user/controller/AuthController.java`
- JWT 유틸: `backend/src/main/java/com/example/welfare/global/util/JwtUtil.java`
- JWT 필터: `backend/src/main/java/com/example/welfare/global/config/JwtAuthenticationFilter.java`
- 토큰 발급/회전/폐기: `backend/src/main/java/com/example/welfare/user/service/AuthTokenService.java`
- 세션 폐기: `backend/src/main/java/com/example/welfare/user/service/UserSessionRevocationService.java`
- access token revoke: `backend/src/main/java/com/example/welfare/user/service/AccessTokenRevocationService.java`
- 관리자 권한: `backend/src/main/java/com/example/welfare/user/service/AuthAdminRoleService.java`, `backend/src/main/java/com/example/welfare/user/service/AdminAccessAuthorityService.java`
- 프론트 세션 상태: `frontend/src/store/authStore.js`, `frontend/src/components/AuthBootstrap.jsx`, `frontend/src/lib/axios.js`

### 토큰 저장 / 쿠키 계약

- access token은 프론트 zustand store의 메모리 상태에만 저장된다.
- zustand persist는 `filterSettings`만 저장하고, access token과 user 정보는 persist하지 않는다.
- refresh token은 서버가 `HttpOnly`, `Secure`, `SameSite=Strict`, path `/api/auth` 쿠키로 내려준다.
- `POST /api/auth/refresh`는 refresh token을 헤더가 아니라 cookie에서만 읽는다.
- 확인 결과: localStorage token 잔존 제거, refresh cookie 전용 수신, cookie secure 기본값이 구현되어 있다.

### refresh token 회전 / reuse 감지

- refresh 시 Redis에 저장된 refresh token hash와 presented token을 비교한다.
- 일치하면 새 access/refresh token을 발급하고 Redis 값을 새 hash로 교체한다.
- 저장값이 없거나 다르면 reuse로 간주하고 userKey의 refresh token key들을 삭제한다.
- legacy raw refresh token은 1회 허용 후 hash 저장으로 이관한다.
- 확인 결과: `AuthTokenServiceTest`, `AuthRedisIntegrationTest`가 회전, legacy 이관, reuse 거부를 검증한다.

### access token 폐기

- access token에는 `iatm` claim이 들어간다.
- logout/forced logout/탈퇴 시 userKey 기준 access cutoff를 Redis에 저장하고, 기존 refresh key도 삭제한다.
- 현재 요청에 제시된 access token은 hash key로 별도 revoke된다.
- `JwtAuthenticationFilter`는 JWT 서명/만료 검증 후 `UserSessionRevocationService.isAccessAllowed()`를 통과해야 SecurityContext를 세팅한다.
- 확인 결과: user 단위 cutoff와 개별 token revoke가 함께 있다.

### 관리자 권한

- 로그인/refresh 시 `security.admin-emails`를 email lookup hash로 계산해 `ROLE_ADMIN`을 부여한다.
- 요청 처리 시 `AdminAccessAuthorityService`가 token의 `ROLE_ADMIN` claim을 그대로 믿지 않고, 현재 `auth_users` 활성 상태와 현재 admin allowlist를 다시 확인한다.
- 확인 결과: 운영 중 admin allowlist에서 빠진 사용자는 refresh 후 admin role이 사라지고, 기존 admin token도 요청 시 admin 권한이 필터링된다.

### 공개 mutation / abuse 방어

- 로그인, 이메일 중복 확인, 이메일 인증 코드 발송, 비밀번호 재설정 요청은 fingerprint 기반 Redis rate limit이 있다.
- 로그인 실패는 계정별 실패 횟수 기준으로 5회 실패 후 30분 잠금된다.
- 공개 회원가입은 관리자 예약 이메일 가입을 거부하고, 이메일 인증 완료 여부를 요구한다.
- 공개 서비스 문의는 별도 `SupportInquiryRateLimitService`가 있다.
- 확인 결과: 공개 mutation은 대부분 rate limit 또는 인증 코드/잠금 정책을 가진다.

### 프론트 인증 흐름

- 앱 bootstrap 시 `POST /api/auth/refresh`로 세션 복원 후 `/api/users/me`를 best-effort로 hydrate한다.
- axios는 401을 받으면 auth endpoint가 아닌 요청에 한해 refresh를 1회 시도하고, 동시에 실패한 요청은 queue로 묶는다.
- refresh 실패 시 local session을 지우고 `authSessionEvents.notifyAuthExpired()`로 앱 레벨 만료 이벤트를 발행한다.
- `AuthExpiryHandler`는 `subscribeAuthExpired()`로 이벤트를 구독하고 로그인 페이지로 이동한다.
- profile hydrate 실패는 세션을 유지하되 `authStore.profileHydration`에 `status=failed`, HTTP status/code/message, failed timestamp를 남긴다.
- `RequireLogin`은 로그인 필요 경로를 `/login`으로 보내고, `RequireAdmin`은 비관리자를 `/`로 보낸다.
- 확인 결과: 보호 라우트와 API 401 복구 흐름은 일관되어 있고, axios refresh queue는 단위 테스트로 고정했다.

### 보완 후보

- refresh cookie `SameSite=Strict`는 same-origin 배포에는 적합하다. 별도 API 도메인을 쓰는 구조로 바뀌면 `SameSite=None; Secure`, CORS, `VITE_API_BASE_URL`, CSP를 함께 재검토해야 한다.
- 로그인 rate limit은 fingerprint 기준이라 NAT/공유망에서 같은 fingerprint로 묶이는 사용자가 있으면 오탐 가능성이 있다. 현재는 계정 잠금과 조합되어 있으므로 운영 로그로 튜닝한다.

### 관련 테스트

- `AuthControllerWebMvcTest`
- `AuthTokenServiceTest`
- `AuthSessionServiceTest`
- `UserSessionRevocationServiceTest`
- `AccessTokenRevocationServiceTest`
- `AdminAccessAuthorityServiceTest`
- `AuthRedisIntegrationTest`
- `AdminSecurityIntegrationTest`
- `UserWithdrawAccessTokenBaselineIntegrationTest`

### 다음 단계로 넘길 항목

- 운영 CORS/CSP와 refresh cookie 정책은 URL/URI 섹션의 도메인 정리와 같이 본다.
- DB 섹션에서 `auth_users`, `users`, `user_pii`, refresh/userKey 기준 데이터 분리와 탈퇴 cascade를 이어서 확인한다.

## 3. DB 구조 / 스키마 / 마이그레이션

상태: 1차 코드 점검 완료, 운영 drift 확인은 별도 런타임 필요.

### 확인한 기준 파일

- fresh schema: `backend/src/main/resources/db/schema.sql`
- active migrations: `backend/src/main/resources/db/migration/*.sql`
- draft migration: `backend/src/main/resources/db/migration-draft/*.sql`
- local compose init: `docker-compose.yml`
- runtime role init: `deploy/postgres/init/z90-create-runtime-db-users.sh`
- RDS bootstrap: `deploy/postgres/bootstrap-rds-runtime.sh`
- runtime contract tests: `PostgresRuntimeContractTest`, `PostgresRuntimeScriptContractTest`, `SecondaryDataSourceSchemaGuardTest`

### 현재 DB 운영 모델

- 메인라인 DB는 PostgreSQL이다.
- fresh local DB는 `schema.sql`이 `/docker-entrypoint-initdb.d/01-schema.sql`로 최초 volume 생성 시 적용된다.
- Spring JPA는 `ddl-auto=validate`로 런타임 스키마 불일치를 부팅 시 잡는다.
- `db/migration` SQL은 존재하지만, 코드상 Flyway/Liquibase 자동 실행 의존성은 보이지 않는다.
- 의미: 기존 Docker volume/RDS에는 `schema.sql` 변경이 자동 재적용되지 않는다. 운영 반영은 migration/patch/bootstrap 절차가 별도로 필요하다.

### 주요 스키마 경계

- 사용자 core/legacy: `users`
- 인증 projection: `auth_users`
- 프로필 projection: `user_profiles`
- PII 분리 schema: `youth_welfare_pii.user_pii`
- PII sync queue: `user_pii_sync_queue`
- 정책 core: `welfare_services`
- 수집 raw/log: `raw_api_payloads`, `api_sync_logs`, `collect_runtime_statuses`, `collect_list_snapshots`, `collect_execution_locks`
- 정책 정규화: `service_taxonomies`, `service_taxonomy_terms`, `service_facts`, `service_taxonomy_summary_slots`, `normalization_code_sets`, `normalization_codes`
- 추천/로그: `user_recommendations`, `recommendation_logs`, `recommendation_run_logs`
- 알림: `notifications`, `user_alerts`, `web_push_subscriptions`, `notification_services`, `notification_attempt_logs`
- 채팅: `chat_sessions`, `chat_messages`, `chat_retrieval_snapshots`
- 관리자 검수: `policy_error_reports`, `policy_duplicate_review_records`, `policy_link_review_records`, `policy_region_corrections`, `policy_field_corrections`, `support_inquiries`

### PII 분리

- 회원가입 시 `users.email`은 실제 이메일 대신 shadow 값으로 저장된다. 안전한 테스트 도메인은 예외적으로 원문을 허용한다.
- 실제 이메일/name/birthDate는 `UserCoreSyncService`에서 AES 암호화 후 `user_pii_sync_queue`에 enqueue된다.
- queue 처리 후 `youth_welfare_pii.user_pii`에 upsert된다.
- `UserCoreSyncService`는 sync 후 `user.clearPlainProfilePii()`를 호출해 legacy 평문 프로필 값을 지운다.
- notification email read는 `notification_pii_ro`가 `youth_welfare_pii.user_pii`의 `user_key`, `email_enc`만 읽는 구조다.
- 보완 완료: `SYNCED` queue row는 `UserPiiSyncQueueRetentionService`가 기본 30일 보존 후 삭제한다. `FAILED/PENDING`은 replay와 운영 triage를 위해 유지한다.
- 남은 점검: 실패 row 샘플 노출, 관리자 접근 범위, AES key rotation, backup 접근 범위는 운영 정책으로 계속 확인한다.

### 권한/역할 분리

- 기본 app role: `app_core_rw`
- PII 쓰기 role: `app_pii_rw`
- 알림 PII 읽기 role: `notification_pii_ro`
- 관리자 read-only role: `admin_dashboard_ro`
- 전용 command/cleanup role: `recommendation_review_gate_command_rw`, `recommendation_persistence_command_rw`, `chat_session_cleanup_rw`, `cluster_ai_cleanup_rw`, `recommendation_retention_cleanup_rw`, `collect_execution_lock_cleanup_rw`, `web_push_subscription_cleanup_rw`
- migration role: `migration_admin`
- runtime script는 `app_core_rw`에서 일부 DELETE/검수 테이블 권한을 회수하고 전용 role에 부여한다.
- `deploy/postgres/verify-rds-runtime-privileges.sh`는 role login, app_core_rw cleanup DELETE revoke, admin_dashboard_ro read-only, command/cleanup role 권한, app_core_rw PII 접근 차단, notification_pii_ro column-level PII 최소권한을 확인한다.
- 확인 결과: 최소 권한 방향이 코드, 운영 verify script, contract test에 반영되어 있다.
- 남은 운영 확인: 실제 RDS 권한은 로컬 파일만으로 확정할 수 없다. 운영에서는 `deploy/postgres/verify-rds-runtime-privileges.sh` 또는 동등 쿼리를 운영 DB에 대해 실행해야 한다.

### 제약조건 / cascade

- `welfare_services`는 `(source_type, source_id)` unique로 외부 정책 중복 저장을 막는다.
- 정책 하위 정규화/상세/태그/지역/채팅 snapshot 일부는 `welfare_services` 또는 `chat_sessions` FK와 cascade를 가진다.
- `chat_retrieval_snapshots.session_id`는 `chat_sessions(id) ON DELETE CASCADE`로 정리된다.
- `user_consents`는 `users(user_key) ON DELETE CASCADE`가 있다.
- 많은 사용자 활동 테이블은 `user_key` 문자열만 들고 FK를 두지 않는다. 탈퇴/강제정리는 서비스 레벨 cleanup에 의존한다.
- 보완 후보: 탈퇴 시 `user_key` 기반 활동 테이블 cleanup 범위는 DB FK가 아니라 서비스/통합 테스트로 계속 보장해야 한다.

### 인덱스

- 정책 검색: FTS GIN + trigram GIN 인덱스가 title/description/support_content/keyword/search_document에 있다.
- 정책 필터: status, category, sourceType, age, end/applyEnd, collectedAt, viewCount 인덱스가 있다.
- 추천: userKey/finalScore, recommendedAt, bookmark 인덱스가 있다.
- 알림: userKey/status/createdAt, retry, web push endpoint unique가 있다.
- 채팅: session별 message createdAt, userKey별 session lastMessage/createdAt 인덱스가 있다.
- 보완 후보: 실제 쿼리 plan은 데이터 분포에 따라 달라진다. 운영/로컬 snapshot에서 `policy_search_keyword_ilike`, ranking, admin dashboard heavy read explain을 주기적으로 봐야 한다.

### migration / drift 방어

- `PostgresRuntimeContractTest`는 MySQL URL/문법 유입, migration version 중복, prod compose DB 포함 여부를 막는다.
- `PostgresRuntimeScriptContractTest`는 runtime role 생성/권한 회수와 chat snapshot cascade migration/patch 정합성을 본다.
- `SecondaryDataSourceSchemaGuardTest`는 PII secondary datasource가 `youth_welfare_pii` schema URL인지 검증한다.
- 보완 후보: `schema.sql`과 `db/migration` 간 완전한 자동 diff 검증은 없다. 운영 반영 누락은 `ddl-auto=validate` 부팅 실패나 smoke에서 발견될 수 있다.

### 다음 단계로 넘길 항목

- 정책 수집/정규화 섹션에서 `raw_api_payloads` -> `welfare_services` -> `service_*` 테이블 저장 경로를 확인한다.
- 보안/개인정보 섹션에서 `users` legacy 컬럼 shadowing, PII failed sample 노출, 로그 redaction을 다시 확인한다.
- 운영 설정 섹션에서 RDS privilege verification과 migration/patch 적용 절차를 확인한다.

## 4. 정책 수집 / 정규화

상태: 1차 코드 점검 완료, 운영 관측/외부 API smoke는 별도 런타임 필요.

### 확인한 기준 파일

- 관리자 수동 수집: `backend/src/main/java/com/example/welfare/collect/controller/CollectAdminController.java`
- 수집 실행 진입점: `CollectBatchService`, `CollectAdminService`, `CollectSourceExecutionService`, `CollectAsyncJobService`
- 전역 실행 lock: `CollectExecutionGuard`
- list adapter 공통 흐름: `AbstractListCollectSourceAdapter`, `CollectItemSaver`
- 외부 API client: `YouthApiClient`, `Gov24Client`, `BokjiroScraperClient`
- raw/log/diff: `RawApiPayloadService`, `ApiSyncLogService`, `CollectListDiffService`, `CollectListChangePolicy`, `CollectListResponsePolicy`
- 상세 수집: `YouthDetailCollectService`, `BokjiroDetailCollectService`, `Gov24DetailCollectService`, `Gov24SupportConditionsCollectService`
- 정규화/sidecar 반영: `CollectPolicyAggregateApplyService`, `WelfareServiceMapper`, `NormalizedPolicySidecarWriter`
- 수집 설정: `backend/src/main/resources/application.yml`

### 실행 진입점 / 권한

- 전체 수동 수집은 `POST /api/admin/collect/all`, 소스별 수집은 `POST /api/admin/collect/{sourceKey}`다.
- 컨트롤러는 `/api/admin/**` 보안 경계 안에 있고, 관리자 operation rate limit을 적용한다.
- `sourceKey`는 40자 이하와 `^[a-z0-9-]+$` 패턴으로 제한된다.
- `sourceId`는 100자 이하와 `^[A-Za-z0-9_-]+$` 패턴으로 제한된다.
- `maxCallsPerRun`, `limit`, `rounds`, `maxCallsPerRound`는 상한을 둔다.
- 확인 결과: 수동 수집 API는 외부 입력을 그대로 source enum/detail id에 넘기지 않고, 관리자 권한과 rate limit, parameter validation을 거친다.
- 정리한 점: `CollectAdminController` 주석이 "prod 비활성화"라고 되어 있었지만 실제로는 profile disable이 아니라 관리자 권한/rate limit 보호 구조라 주석을 수정했다.

### 배치 실행 순서 / 중복 방어

- 정기 배치는 `collect.schedule.cron` 기본값 기준 매일 02:00 `Asia/Seoul`에 돈다.
- `CollectBatchService.collectAllNow()`는 `CollectExecutionGuard`의 `collect-global` DB lock을 잡고 실행한다.
- 실행 순서는 `CollectSource.executionOrder()`를 따른다.
- 현재 실행 순서에는 list source뿐 아니라 `GOV24_DETAIL`, `GOV24_SUPPORT_CONDITIONS`, `BOKJIRO_DETAIL`, `BOKJIRO_DETAIL_REFRESH`도 포함된다.
- list 수집 후 diff threshold를 넘으면 강제 detail 수집을 추가로 실행하고, 요일별 detail rotation도 별도로 실행한다.
- 확인 결과: 상세 수집 서비스들은 기존 raw/detail 존재 여부와 call budget으로 중복 호출을 줄인다.
- 보완 후보: detail source가 기본 실행 순서와 forced/rotation 경로 양쪽에 걸려 있어, 실제 운영 call 수와 rate limit 영향은 `api_sync_logs`로 계속 확인해야 한다.

### 전역 lock / async job

- `CollectExecutionGuard`는 DB 기반 global lock, heartbeat, release를 제공한다.
- 이미 실행 중이면 `COLLECT_ALREADY_RUNNING`으로 막는다.
- async 수집은 `CollectAsyncJobService`가 in-memory snapshot으로 active job을 추적하고, 최근 상태는 `api_sync_logs`에서도 조회한다.
- 확인 결과: 애플리케이션 프로세스 안에서는 중복 async 실행을 막고, 배치 전체는 DB lock으로 보호한다.
- 보완 후보: async snapshot은 메모리 상태라 재시작하면 현재 job snapshot은 사라진다. 최근 로그 fallback은 있지만, 운영 UI/알림에서 `RUNNING` stale 상태를 어떻게 보여줄지 확인이 필요하다.

### list 수집 흐름

- `AbstractListCollectSourceAdapter`는 fetch -> non-empty 검증 -> field stats 기록 -> raw payload 저장 -> raw field validation -> 수집 필요 여부 판단 -> item 저장 순서로 처리한다.
- `CollectItemSaver`는 item 단위 `REQUIRES_NEW` transaction으로 저장한다.
- lock/deadlock 계열 예외는 최대 3회 재시도한다.
- 한 item 실패가 전체 batch transaction rollback으로 번지지 않고, result의 failed count로 기록된다.
- 확인 결과: list 수집은 raw payload와 정규화 저장을 분리하고, item 실패를 격리한다.
- 주의점: `RawApiPayloadService.save()`가 실패하면 정규화 저장도 skip된다. replayability 관점에서는 타당하지만, raw table 장애가 나면 수집 실패가 크게 늘 수 있으므로 failed count alert 기준이 필요하다.

### raw payload / sync log / diff guard

- raw payload는 source type/id, payload type, endpoint, payload hash, collectedAt을 저장한다.
- `ApiSyncLogService`는 stale `RUNNING` 로그를 닫고, 시작/완료/실패와 saved/skipped/failed count를 남긴다.
- list snapshot은 fingerprint를 저장하고 이전 snapshot과 added/removed count, count drop ratio를 비교한다.
- `CollectListResponsePolicy`는 list source의 빈 응답을 실패로 간주한다.
- `CollectListChangePolicy`는 급격한 count drop과 threshold 기반 forced detail 수집을 판단한다.
- 확인 결과: 외부 API가 빈 목록/대량 누락/응답 형태 변경을 보낼 때 조용히 정상 처리되는 구조는 아니다.
- 보완 후보: raw 저장 실패의 상세 원인은 로그에만 있고 sync metadata에는 집계되지 않는다. 운영 alert에는 failed count와 에러 로그를 같이 봐야 한다.

### 외부 API client / endpoint

- Youth API client는 `youth-api.base-url` 설정값과 URI builder로 list/detail URI를 생성한다.
- `application.yml`의 `youth-api.base-url` 기본값은 현재 유효 endpoint인 `https://www.youthcenter.go.kr/go/ythip/getPlcy` 기준이다.
- Gov24 client는 `gov24.base-url`을 설정에서 주입받고, `api.odcloud.kr` API path를 조합한다.
- HTTP retry executor는 재시도 가능 상태와 비재시도 상태를 분리하고, backoff/jitter를 적용한다.
- retry 로그는 `serviceKey`, `apiKey`, `key`, `token` 등 민감 label을 redaction한다.
- 확인 결과: Gov24와 Youth 모두 설정 기반 base URL을 사용한다.
- 보완 완료: `YouthApiClientTest`에서 list/detail URI가 `youth-api.base-url` 설정값을 기준으로 생성되는 계약을 고정했다.

### 상세 수집

- Youth detail은 기존 detail 존재 여부를 보고 skip하며, 최대 호출 수를 둔다.
- Bokjiro detail은 중앙/지자체 대상, refresh/gap-fill 모드, consecutive 429 cutoff, round/call budget을 가진다.
- Gov24 detail/support conditions는 기존 raw 존재 여부를 보고 skip하고, sourceId 단건 실행과 maxCallsPerRun을 지원한다.
- 수동 `CollectAdminService`는 Gov24 list 이후 detail/support condition 수집까지 이어서 실행한다.
- scheduled batch는 list source 전체 실행 후 list diff forced detail을 실행하고, 마지막에 요일별 rotation detail을 실행한다.
- forced detail은 list diff 변화량 대응이고, rotation detail은 backlog/refresh 보강이다. 같은 run에서 같은 detail lane이 한 번 더 결과에 잡힐 수 있지만, detail service의 기존 raw/detail 존재 확인과 maxCallsPerRun으로 외부 호출량을 제한한다.
- 확인 결과: 상세 수집은 외부 호출 비용을 줄이기 위한 skip/budget 장치가 있고, `CollectBatchServiceTest`가 list -> forced detail -> rotation 순서를 고정한다.
- 남은 운영 확인: 실제 `api_sync_logs`에서 detail/support saved/skipped/failed 비율과 외부 API call 시간을 주기적으로 본다.

### 정규화 / sidecar / 검색 반영

- `CollectPolicyAggregateApplyService`는 detail/fact/summary sidecar를 저장하고, 검색 청년 관련도와 embedding refresh 요청까지 이어서 처리한다.
- list upsert 후 region/tag 교체와 aggregate apply가 같은 item 저장 흐름 안에서 실행된다.
- detail path는 detail 저장, fallback service 보강, sidecar, relevance, embedding refresh를 수행한다.
- sidecar writer는 테이블 준비 상태를 확인하고, schema가 없으면 안전하게 skip한다.
- 확인 결과: 수집 결과가 `welfare_services`에만 머무르지 않고 검색/추천에 쓰는 sidecar와 embedding 갱신 요청까지 연결된다.
- 보완 후보: sidecar table readiness cache는 런타임 중 schema가 바뀌는 상황을 전제로 하지 않는다. migration 후에는 app restart를 전제로 운영한다.

### 다음 단계로 넘길 항목

- 검색/추천/랭킹 섹션에서 수집 후 갱신되는 `searchYouthRelevanceService`, embedding refresh queue, ranking query를 확인한다.
- 알림 섹션에서 수집된 deadline/applyEnd 값이 deadline reminder와 digest에 어떤 조건으로 들어가는지 확인한다.
- 운영 설정 섹션에서 `api_sync_logs` 기반 수집 실패율, skipped 비율, 외부 API call 수 dashboard/alert 기준을 확인한다.

## 5. 검색 / 추천 / 랭킹

상태: 1차 코드 점검 완료, 운영 쿼리 plan과 실제 추천 품질 샘플링은 별도 데이터 필요.

### 확인한 기준 파일

- 공개 정책 API: `backend/src/main/java/com/example/welfare/policy/controller/PolicyController.java`
- 검색 서비스/SQL: `PolicySearchService`, `WelfareServiceSearchRepositoryImpl`, `WelfareServiceRepository`
- 랭킹 서비스: `PolicyRankingService`, `PolicyRankingReadRepositoryImpl`
- 추천 API: `RecommendationController`
- 추천 생성: `RecommendationGenerationService`, `RetrievalService`, `RuleScoringService`, `AiScoringService`, `ReRankingService`
- 추천 저장/조회: `RecommendationPersistenceService`, `RecommendationAccessService`, `RecommendationResultReadService`
- 수집 후 검색 연결: `SearchYouthRelevanceService`, `PolicyEmbeddingRefreshRequestService`
- 채팅 검색 공유 경로: `PolicyExplorationService`, `ChatSemanticSearchService`

### 공개 검색 API / 입력 제한

- `POST /api/policies/search`는 `PolicySearchRequest`에서 keyword 필수, keyword 100자 이하, page 0~1000, size 1~100을 검증한다.
- controller에서 fingerprint/user 기준 search rate limit을 적용한다.
- `PolicySearchService`는 keyword trim, normalize, token 10개 이하를 한 번 더 검증한다.
- status는 `ACTIVE`, `UPCOMING`, `CLOSED`만 허용한다.
- statusFilter는 기본 `ACTIVE_ONLY`, 명시값은 `ALL`, `EXPIRED_ONLY`를 허용하고 그 외 값은 기본 `ACTIVE_ONLY`로 접는다.
- sort는 `RELEVANCE`, `VIEWS`, `LATEST`, `DEADLINE`, 호환용 `NAME`만 허용한다.
- Gov24 필터 값은 관리 코드 support helper로 normalize되고, 미관리 값은 `INVALID_INPUT`으로 거부한다.
- 확인 결과: 검색 API는 페이징/키워드/필터 입력의 상한과 enum 성격 검증이 있다.
- 보완 후보: statusFilter의 알 수 없는 값은 에러가 아니라 `ACTIVE_ONLY`로 접는다. 공개 API 호환성에는 유리하지만, 잘못된 클라이언트 요청을 조기에 발견하기는 어렵다.

### 검색 SQL / 정렬 / 캐시

- 검색 SQL은 `to_tsvector/to_tsquery`, title/keyword LIKE, title/keyword trigram similarity를 함께 사용한다.
- 기본 검색은 `ws.search_youth_relevant IS TRUE`를 필수 조건으로 둔다.
- `ACTIVE_ONLY`는 `ACTIVE/UPCOMING`이면서 `apply_end_date`가 없거나 오늘 이후인 정책만 노출한다.
- relevance 정렬은 ts_rank, title LIKE, keyword LIKE, trigram 점수를 합산한다.
- 지역 필터가 있으면 해당 지역 정책을 우선 노출하고, 명시 지역이 없는 전국 정책도 포함한다.
- 첫 페이지 relevance 검색에서 Gov24 결과가 전혀 없으면 더 큰 후보 창에서 첫 Gov24 정책을 탐색 슬롯으로 끼워 넣는다.
- 비로그인 공개 검색은 30초 TTL, 최대 256개 key의 in-memory cache를 쓴다.
- 확인 결과: 검색은 성능 보호용 짧은 캐시와 청년 관련성/지역/상태 필터를 중심으로 구성되어 있다.
- 보완 후보: cache는 프로세스 local memory라 다중 인스턴스에서 cache hit 양상이 다르고, 배포 직후/수집 직후 최대 30초 동안 이전 결과가 보일 수 있다.

### 검색 로그 / 추천 연결

- 검색 요청은 `PolicySearchLogService.record()`로 keyword, resultCount, 필터, page/size를 기록한다.
- trending/suggestion도 fingerprint/user 기준 rate limit을 적용한다.
- 검색 응답이 150ms 이상이거나 total 500건 이상이면 info log, 500ms 이상이면 warn log를 남긴다.
- 수집 저장 경로는 `SearchYouthRelevanceService.refreshForService()`로 `search_youth_relevant`를 갱신한다.
- 수집 후 `PolicyEmbeddingRefreshRequestService`가 serviceId를 모아 embedding refresh를 요청한다.
- 확인 결과: 수집 결과가 공개 검색, 채팅 검색, 추천 후보에 연결되는 핵심 스위치는 `search_youth_relevant`와 embedding refresh다.
- 보완 후보: `search_youth_relevant` 계산 로직이 과하게 보수적이면 검색/채팅 fallback/추천 후보가 동시에 줄어든다. backfill 결과의 relevant/excluded 비율을 운영 지표로 본다.

### 정책 랭킹

- `GET /api/policies/ranking`은 size 1~100으로 제한하고 ranking rate limit을 적용한다.
- 랭킹 후보는 `ACTIVE`, `UPCOMING` 상태만 포함한다.
- 점수는 최근 7일 unique view, 내부 view count, source별 외부 api view count, freshness를 정규화해 가중합한다.
- 트래픽이 적을 때는 raw view 비중을 높이고, unique view가 쌓이면 unique view 비중을 높인다.
- 외부 조회수가 없는 source는 external weight를 다른 weight로 재분배한다.
- size가 10 이상이면 최근 14일 정책 exploration slot을 최대 2개 넣는다.
- 랭킹 결과도 30초 TTL in-memory cache를 쓴다.
- 확인 결과: 랭킹은 cold-start와 최신 정책 노출을 고려한 구조다.
- 보완 완료: 랭킹 후보와 7일 unique-view 집계 모두 `apply_end_date`가 오늘 이전인 정책을 제외한다. 검색 `ACTIVE_ONLY`와 같은 만료 기준으로 맞췄다.

### 추천 API / 실행 보호

- `GET /api/recommendations`는 저장된 최신 추천만 반환하고 실시간 AI 호출을 하지 않는다.
- `POST /api/recommendations/refresh`가 추천 파이프라인을 실행한다.
- refresh는 userKey 기준 Redis lock으로 중복 실행을 막고, 이미 실행 중이면 짧게 polling한 뒤 저장된 결과 fallback을 시도한다.
- personal/shared refresh는 각각 Redis rate limit을 가진다.
- controller size는 1~100으로 제한된다.
- 확인 결과: 추천 조회와 생성이 분리되어 있고, 사용자별 중복 실행과 반복 refresh가 제한된다.

### 추천 후보 조회 / 필터

- 기본 후보는 나이, 소득, 상태 `ACTIVE/UPCOMING` 조건으로 조회한다.
- 지역 정보가 있으면 regionCode 또는 sido 기반 후보를 더 넓게 조회한다.
- 지역 후보에서는 지역 일치, BOKJIRO_LOCAL, GOV24 지역 텍스트 매칭을 우선순위로 둔다.
- `RetrievalService`는 기본 후보 50개와 최신 후보 5개를 병합한다.
- projection/tags를 한 번에 로드해 청년 관련성, 나이 constraint keyword, source diversity를 후처리한다.
- priority가 없는 사용자는 source round-robin과 no-priority top-band 다양화가 적용된다.
- 확인 결과: 추천은 DB 조건과 후처리 필터를 조합하고, 단일 source가 상위 후보를 독점하는 상황을 완화한다.
- 보완 후보: non-region 기본 후보 query 자체에는 `search_youth_relevant` 조건이 없고 후처리에서 걸러진다. 정확도에는 유리하지만 후보 fetch window가 작으면 청년 무관 후보가 창을 차지해 유효 후보 수가 줄 수 있다.

### 추천 점수 / AI fallback / 저장

- rule score는 청년 관련성, 관심분야, target group, housing profile, 특수 대상, 마감 임박, priority mismatch 등을 반영한다.
- 특수 대상 신호가 있지만 사용자와 맞지 않는 후보는 post scoring filter에서 제거된다.
- AI score는 cluster cache hit율이 50% 이상이면 cache를 쓰고, 그렇지 않으면 gateway 호출 후 cache에 저장한다.
- personal refresh의 `youth_all` cluster는 cache를 쓰지 않고 개인 프로필 기반 실시간 AI 호출을 유지한다.
- AI 점수가 없거나 실패하면 rerank에서 rule-only fallback으로 final score를 만든다.
- 최종 저장은 service 존재 여부를 다시 확인하고, 기존 사용자 추천을 replace한다.
- 추천 로그와 run log는 후처리로 기록되며, run log에는 outcome, 후보 수, AI status counts, duration이 남는다.
- 정리한 점: `AiScoringService`의 cache hit 로그가 SLF4J placeholder 문법과 맞지 않아 hitRate percent가 값으로 찍히지 않을 수 있어 수정했다.

### 다음 단계로 넘길 항목

- 알림/푸시/이메일 섹션에서 추천 결과가 digest 알림으로 들어가는 조건과 deadline reminder의 applyEnd 해석을 확인한다.
- 운영 설정 섹션에서 검색 slow log, zero-result 검색, 추천 run log, AI cache hit율 dashboard 기준을 확인한다.
- 테스트 우선순위 섹션에서 `ACTIVE`지만 `apply_end_date`가 지난 정책의 랭킹 포함 여부를 정책 결정 또는 테스트로 고정한다.

## 6. 알림 / 푸시 / 이메일

상태: 1차 코드 점검 완료, 실제 SMTP/Web Push provider smoke는 별도 운영 설정 필요.

### 확인한 기준 파일

- 사용자 알림 API: `backend/src/main/java/com/example/welfare/notification/controller/NotificationController.java`
- 스케줄러/lock: `NotificationScheduleService`, `NotificationExecutionGuard`
- 추천 digest: `NotificationDispatchService`, `NotificationRecommendationService`, `NotificationSlotSelector`
- deadline reminder: `DeadlineReminderDispatchService`, `DeadlineReminderContentService`
- 이력/인앱 알림: `NotificationHistoryService`, `UserAlertCommandService`, `UserAlertReadService`
- retry: `NotificationRetryService`, `NotificationRetryReadService`, `NotificationRetryCommandService`
- web push: `WebPushSubscriptionCommandService`, `WebPushDispatchService`, `WebPushEndpointPolicyService`, `WebPushKeyValidator`
- 이메일/PII: `UserNotificationReadService`, `NotificationGateway`, `EmailClient`
- unsubscribe: `NotificationUnsubscribeTokenService`, `NotificationUnsubscribeRequest`

### 사용자 API / 입력 제한

- 알림 목록은 `GET /api/notifications/me`, unread count는 `GET /api/notifications/me/unread-count`다.
- push public key는 공개 조회이고, 개인 push subscription 목록/등록/삭제/test send는 로그인 필요 API다.
- unsubscribe는 `POST /api/notifications/unsubscribe`로 token을 받는다.
- `subscriptionId`, `alertId` path variable은 컨트롤러에서 `@Min(1)`로 보강했다.
- `WebPushSubscriptionRequest`는 endpoint 500자, p256dh/auth 255자, userAgent 500자, deviceLabel 100자로 제한한다.
- test push title/body/url은 각각 100/500/500자 제한이 있다.
- 확인 결과: 사용자 입력은 DTO와 path variable 레벨에서 기본 상한을 가진다.

### 스케줄 / 중복 실행 방어

- 일간 추천 digest는 매일 08:00 `Asia/Seoul`에 실행된다.
- 주간 추천 digest는 매주 월요일 08:00 `Asia/Seoul`에 실행된다.
- deadline reminder는 매일 08:30 `Asia/Seoul`에 실행된다.
- 실패 알림 retry는 30분마다 실행된다.
- 각 스케줄은 `NotificationExecutionGuard`의 Redis lock을 잡고 실행하며, lock lease 기본값은 180분이다.
- digest 자체도 dispatch window와 dispatch reservation으로 중복 발송을 한 번 더 막는다.
- 확인 결과: scheduler 중복 실행, 같은 dispatch window 중복 발송, DB reservation conflict가 계층적으로 방어된다.

### 추천 digest 대상 / 내용

- 대상자는 `UserNotificationReadService.getNotificationTargets(period)`에서 period와 알림 channel 설정 기준으로 조회된다.
- 이메일은 notification read repository에서 암호문을 읽고 AES 복호화한다.
- 이메일이 없어도 in-app 또는 web push가 켜져 있으면 발송 대상이 될 수 있다.
- 추천 후보 pool은 최소 50개 또는 사용자 displayCount 이상을 읽는다.
- `NotificationSlotSelector`가 minScore/displayCount 조건으로 노출 후보를 고른다.
- 추천 digest는 이메일, in-app alert, web push fan-out을 각각 처리한다.
- 확인 결과: 추천 생성 API와 알림 발송 API가 분리되어 있고, digest는 저장된 추천 결과를 기반으로 발송된다.
- 보완 후보: 저장된 추천이 오래된 상태에서도 digest 대상이 될 수 있다. 최신 추천 freshness 기준이 필요하면 `recommendedAt` max age 정책을 추가로 정해야 한다.

### deadline reminder

- deadline reminder는 북마크된 추천 결과에서 `applyEndDate`가 오늘 이상, `today + days` 이하인 정책을 고른다.
- 후보는 마감일 오름차순, service id 오름차순으로 정렬하고 최대 3개만 보낸다.
- 기본 days는 `notification.deadline-reminder.days` 설정값이고, 테스트 dispatch는 1~30으로 제한한다.
- dispatch key는 `deadline:{userKey}:{today}:{days}`라 같은 날 같은 window의 중복을 막는다.
- 확인 결과: 마감 지난 정책은 제외하고, 임박 북마크 정책만 알린다.
- 보완 후보: search/ranking 쪽과 마찬가지로 `applyEndDate` 의미가 source별로 다를 수 있다. 수집 정규화에서 신청 마감일 fact key와 legacy `apply_end_date`의 의미를 계속 맞춰야 한다.

### 이력 / 인앱 알림

- `NotificationHistoryService.reserveDispatch()`는 `PENDING` notification을 먼저 만들고 reservation을 시도한다.
- 성공/실패 결과 저장 시 messageText, item count, notification items를 함께 저장한다.
- 실패하면 초기 retry 시각을 30분 뒤로 잡는다.
- in-app이 켜져 있으면 recommendation digest 또는 deadline reminder alert를 만든다.
- alert는 `eventKey=dispatchKey` 중복 여부를 확인하고, `findByIdAndUserKey`로 read/hide 소유자 조건을 강제한다.
- 확인 결과: 이메일 발송 결과와 인앱 알림 생성이 같은 notification 이력에 묶인다.

### retry

- retry job은 due failed notification id를 조회한 뒤 claim을 잡고 발송한다.
- claim TTL은 30분이고, 재시도 간격은 120분이다.
- 최대 retry count는 2다.
- retry는 notification의 userKey로 현재 알림 이메일을 다시 조회해 발송한다.
- 확인 결과: 실패 당시 이메일을 저장해서 재사용하지 않고, 재시도 시점의 최신 PII 이메일을 사용한다.
- 보완 후보: retry attempt는 `NotificationAttemptLogService`를 직접 기록하지 않는다. scheduler summary와 notification 상태는 남지만, initial send와 같은 channel/kind/outcome 단위의 attempt log 일관성은 부족하다.

### web push

- endpoint는 HTTPS, userInfo/fragment 금지, port 443만 허용한다.
- 허용 host suffix는 FCM, Mozilla, Apple, Windows push endpoint 기본값으로 제한된다.
- DNS resolve 후 private/loopback/link-local/multicast/CGNAT 등 내부 주소를 막는다.
- p256dh/auth key는 `WebPushKeyValidator`로 검증한다.
- endpoint 탈취 방지를 위해 기존 endpoint가 다른 userKey에 묶여 있으면 key가 일치하지 않는 cross-account takeover를 거부한다.
- 사용자별 subscription 수는 기본 10개로 제한한다.
- web push test URL은 내부 path 또는 `app.base-url`과 same-origin인 HTTP/HTTPS URL만 허용하고, payload에는 path와 absolute URL을 같이 넣는다.
- 확인 결과: web push endpoint SSRF/내부망 접근과 cross-account endpoint takeover에 대한 방어가 있다.

### unsubscribe / origin 방어

- unsubscribe token은 32바이트 random opaque token이며, Redis key에는 token SHA-256 hash만 쓴다.
- token은 consume 시 삭제되어 1회성으로 동작한다.
- 기본 만료는 30일이다.
- `TrustedOriginFilter`는 `POST /api/notifications/unsubscribe`에 Origin/Referer allowlist를 요구한다.
- 확인 결과: 메일 링크 token은 opaque하고, POST unsubscribe는 browser origin 방어가 있다.
- 보완 완료: 메일 본문 URL 기본값은 `https://youthmoa.kr`로 통일했다.

### 다음 단계로 넘길 항목

- 보안/개인정보 섹션에서 notification PII read role, attempt log의 userKey hash, endpoint host logging 범위를 확인한다.
- 운영 설정 섹션에서 SMTP/Web Push provider 설정, VAPID key, retry/failed/disabled subscription alert 기준을 확인한다.
- 테스트 우선순위 섹션에서 retry attempt log 일관성과 notification message 기본 도메인 드리프트를 고정할지 결정한다.

## 7. 보안 / 개인정보

상태: 1차 코드 점검 완료, 실제 운영 secret/권한 검증은 런타임 환경 필요.

### 확인한 기준 파일

- Spring Security: `backend/src/main/java/com/example/welfare/global/config/SecurityConfig.java`
- origin 방어: `TrustedOriginFilter`
- 공통 요청 로그: `ApiRequestLoggingFilter`, `GlobalExceptionHandler`
- 암호화: `AesProperties`, `AesEncryptUtil`
- PII sync: `UserCoreSyncService`, `UserPiiSyncQueueService`, `UserPiiSyncProcessor`, `UserPiiBackfillService`
- PII datasource guard: `SecondaryDataSourceSchemaGuard`
- 알림 PII read: `UserNotificationReadService`, `NotificationTargetReadRepositoryImpl`
- env 예시: `.env.example`, `env.production.example`, `backend/src/main/resources/application.yml`

### 인증/인가 경계

- Spring Security는 stateless session과 JWT 필터를 사용한다.
- `/api/admin/**`와 Swagger는 `ROLE_ADMIN`이 필요하다.
- 알림 test dispatch API는 admin 전용이다.
- 공개 API는 인증/회원가입/비밀번호 재설정/정책 검색/정책 조회/ranking/reference 일부로 제한된다.
- admin role은 인증 섹션에서 확인한 것처럼 token claim만 믿지 않고 현재 DB 사용자와 admin allowlist로 재검증된다.
- 확인 결과: 관리자/공개/로그인 필요 API 경계가 SecurityConfig에 명시되어 있다.

### CORS / CSRF 성격 방어

- CORS는 allow credentials를 켜고 `security.cors.allowed-origins`만 허용한다.
- `TrustedOriginFilter`는 cookie가 관여하는 `POST /api/auth/refresh`, `/api/auth/logout`, `/api/notifications/unsubscribe`에 Origin 또는 Referer allowlist를 요구한다.
- Origin/Referer가 모두 없으면 보호 대상 POST는 거부된다.
- 확인 결과: refresh/logout/unsubscribe는 SameSite cookie만 믿지 않고 서버 allowlist도 확인한다.
- 보완 후보: 로그인/회원가입/비밀번호 재설정 같은 공개 mutation은 Origin 필터 대상은 아니고 rate limit 중심이다. abuse dashboard에서 IP/fingerprint 분포를 봐야 한다.

### 공통 요청 로그 / 예외 응답

- `ApiRequestLoggingFilter`는 `/api/**`에 대해 method, path, status, duration, requestId, userKeyHash, clientFingerprint, errorCode, errorType, refererPresent만 찍는다.
- query string, request body, Authorization header, cookie, email 원문은 공통 요청 로그에 남기지 않는다.
- request id는 허용 문자만 남기고 80자로 자른다.
- `GlobalExceptionHandler`는 validation/custom error는 정해진 메시지로 응답하고, 일반 예외는 내부 메시지를 클라이언트에 노출하지 않는다.
- 정리한 점: `UserCoreSyncService` 내부 예외 메시지에 raw userKey가 들어갈 수 있어 userKeyHash만 남기도록 수정했다.
- 정리한 점: `LogSanitizer`를 추가해 backend 로그용 문자열의 JWT/Bearer/Authorization/Cookie/JDBC URL userinfo/query secret/key-value secret/direct identifier를 redaction한다.
- 정리한 점: `GlobalExceptionHandler`의 unhandled exception error 로그는 throwable 원문 stacktrace 대신 errorType과 sanitizer를 통과한 단일 라인 메시지만 남긴다.
- 정리한 점: `ChatSessionContextStateService`의 JSON parse 실패 로그는 raw context JSON snippet이 섞일 수 있는 throwable을 싣지 않고 errorType만 남긴다.
- 정리한 점: `PolicySearchLogService`, `ChatRetrievalSnapshotService`, `AdminPolicyRegionAuditService`의 best-effort/스케줄 실패 로그도 throwable stacktrace 없이 기존 집계 필드와 errorType만 남긴다.

### AES / PII 암호화

- `AesProperties`는 AES secret이 비어 있거나 UTF-8 기준 32바이트가 아니면 부팅을 실패시킨다.
- 현재 암호화는 `v2:` prefix와 AES/GCM/NoPadding, 12바이트 nonce, 128-bit tag를 사용한다.
- legacy AES/CBC payload도 복호화할 수 있고, `UserPiiBackfillService.rotateLegacyEncryptedFields()`로 v2 재암호화가 가능하다.
- AES encrypt/decrypt 실패 로그는 stacktrace 없이 errorType만 남기고 평문/암호문을 직접 출력하지 않는다.
- 확인 결과: 신규 PII 암호문은 authenticated encryption으로 저장된다.
- 보완 후보: AES key rotation은 legacy cipher rotation과 다르다. 실제 key 교체 절차는 별도 dual-read/reencrypt 전략이 필요하다.

### PII 분리 / sync queue

- 회원가입/프로필 sync 시 이메일/name/birthDate는 AES 암호화 후 `user_pii_sync_queue`에 저장된다.
- queue processor가 `youth_welfare_pii.user_pii`로 upsert하고 queue 상태를 `SYNCED` 또는 `FAILED`로 바꾼다.
- sync 실패 시 `lastError`는 `PII sync failed (ErrorType)` 형태라 암호문/평문을 저장하지 않는다.
- 탈퇴 sync는 queue row와 PII row를 삭제하고 legacy plain PII를 clear한다.
- `SecondaryDataSourceSchemaGuard`는 PII datasource URL이 PostgreSQL이고 `currentSchema=youth_welfare_pii`인지 부팅 시 확인한다.
- 확인 결과: PII 저장 schema와 datasource 경계가 코드에서 검증된다.
- 보완 후보: `user_pii_sync_queue`는 public schema에 암호문을 담는다. 암호문이더라도 failed/pending retention, 관리자 접근, backup 접근 범위를 운영 정책으로 정해야 한다.

### 알림 PII read

- 알림 대상 조회는 user profile projection에서 userKey와 channel 설정을 가져오고, `notification_pii_ro` 경로로 email_enc만 읽는다.
- `UserNotificationReadService`는 email_enc를 복호화해 발송에 사용하고, 로그에는 이메일 원문을 남기지 않는다.
- notification attempt log는 userKeyHash와 endpointHost 중심으로 기록한다.
- web push endpoint full URL은 로그에 남기지 않고 host만 남긴다.
- `WebPushDispatchService`는 provider error message를 notification attempt log에 남기기 전 `LogSanitizer`와 단일 라인/길이 제한을 통과시킨다.
- 확인 결과: 알림 발송 경로는 최소 이메일 읽기와 host 수준 관측으로 제한된다.

### 관리자 PII 운영 API

- 사용자 관리자 API에는 PII backfill, legacy 암호문 rotation, PII sync replay/status가 있다.
- replay는 단건 userKey 또는 bulk limit을 받으며 admin mutation rate limit을 적용한다.
- PII sync status는 pending/failed/synced count와 failed sample을 보여준다.
- 보완 완료: status 응답은 `oldestPendingUserKeyHash`, `oldestFailedUserKeyHash`, failed sample `userKeyHash`만 내려준다. replay/forced logout처럼 운영자가 명시적으로 raw `userKey`를 입력해야 하는 command API는 별도 경로로 유지한다.

### secret / env 기본값

- `JWT_SECRET`, `AES_SECRET_KEY`는 application.yml에서 기본값 없이 env 주입을 요구한다.
- `AUTH_EMAIL_VERIFICATION_CODE_HMAC_SECRET`는 없으면 `JWT_SECRET`을 fallback으로 쓴다.
- 운영 예시 `env.production.example`은 `APP_BASE_URL=https://youthmoa.kr`, CORS도 `youthmoa.kr` 기준이다.
- 루트 `.env.example`도 `APP_BASE_URL=https://youthmoa.kr`, CORS `youthmoa.kr` 기준으로 정리했다.
- 확인 결과: 핵심 secret은 운영 env 주입 전제다.
- 보완 완료: 운영 profile, RDS bootstrap/verify, runtime cutover preflight는 recommendation command role env 누락을 `DB_URL`/`DB_PASSWORD` fallback으로 흡수하지 않는다. 로컬 개발용 fallback은 `application.yml`/bootRun 경계에만 남긴다.

### 다음 단계로 넘길 항목

- 프론트 UX/상태 섹션에서 token persist 여부, 로그인 만료 redirect, 알림 deeplink, push permission UX를 확인한다.
- 운영 설정 섹션에서 runtime env allowlist, RDS role password 분리, CORS/domain 값, secret rotation runbook을 확인한다.
- notification retry attempt log는 `sent`, `rescheduled`, `terminal_failed` outcome으로 고정했다.

## 8. 프론트 UX / 상태

상태: 1차 코드 점검 완료, 브라우저 실기기 push 권한/서비스워커 smoke는 별도 환경 필요.

### 확인한 기준 파일

- 라우터: `frontend/src/router/index.jsx`
- auth state: `frontend/src/store/authStore.js`, `frontend/src/components/AuthBootstrap.jsx`
- axios/refresh: `frontend/src/lib/axios.js`
- 보호 라우트: `RequireLogin`, `RequireAdmin`, `AuthExpiryHandler`
- 안전 이동: `frontend/src/lib/safeNavigation.js`
- 정책 화면: `PoliciesPage.jsx`, `PolicyDetailPage.jsx`
- 알림 화면: `AlertsPage.jsx`, `Header.jsx`, `MyPage.jsx`
- web push helper: `frontend/src/lib/webPush.js`
- unsubscribe 화면: `NotificationUnsubscribePage.jsx`
- e2e smoke: `frontend/e2e/smoke.spec.js`

### auth 상태 / token 저장

- access token은 zustand memory state에만 저장된다.
- persist 대상은 `filterSettings`뿐이고 access token/user는 저장하지 않는다.
- legacy localStorage key `token`, `auth-store`는 앱 로드 시 삭제한다.
- 부팅 시 `AuthBootstrap`이 `/api/auth/refresh`를 호출해 refresh cookie 기반으로 access token을 복원한다.
- profile hydrate는 `/api/users/me`를 best-effort로 호출하고 실패해도 세션 자체는 유지한다.
- 확인 결과: 브라우저 저장소에 access token을 남기지 않는 구조다.
- profile hydrate 실패는 `authStore.profileHydration`에 마지막 실패 메타데이터로 남긴다. 사용자명/이메일 표시만 비는 상황을 세션 만료와 구분해서 볼 수 있다.

### axios / refresh queue

- axios는 same-origin 또는 configured API origin의 `/api/**` 요청에만 Authorization header를 붙인다.
- `/api/auth/refresh`와 auth endpoint 요청에는 기존 Authorization header를 제거한다.
- 401을 받으면 refresh를 1회 시도하고, refresh 중 들어온 요청은 queue로 묶어 새 token으로 재시도한다.
- refresh 실패 시 session을 지우고 `authSessionEvents` 만료 이벤트를 발행한다.
- `AuthExpiryHandler`는 해당 이벤트를 구독해 `/login`으로 이동하고 `reason=expired` state를 넘긴다.
- 네트워크 응답이 없으면 `networkStore.serverDown`을 true로 둔다.
- 확인 결과: refresh 폭주와 외부 URL token 누출 방어가 있고, Node 단위 테스트로 동시 401 queue/refresh 실패/auth endpoint 예외/외부 origin header 제거를 고정했다.

### 라우팅 / 보호 화면

- 공개 라우트는 메인, 가이드, 지원, 로그인, 회원가입, 약관, 개인정보, 비밀번호 재설정, unsubscribe, 정책 목록/상세다.
- `/alerts`, `/mypage`, `/chat`은 `RequireLogin`으로 보호된다.
- `/admin/dashboard`는 `RequireAdmin`으로 보호된다.
- 로그인 필요 시 현재 location을 `buildSafeReturnLocation()`으로 정리해 `/login` state에 넣는다.
- 확인 결과: 로그인 필요/관리자 필요 화면 경계는 라우터에서 분리되어 있다.
- 보완 완료: catch-all route와 404 화면을 추가했다.

### 안전한 deeplink / 외부 링크

- `resolveSafeInternalPath()`는 빈 값, 2048자 초과, control character, `//evil`, 다른 origin URL을 거부한다.
- 알림 deeplink는 Header, MyPage, AlertsPage에서 safe path 검증 후 navigate한다.
- 정책 상세 외부 링크는 http/https만 허용하고 `window.open(..., "noopener,noreferrer")`를 사용한다.
- unsubscribe page는 hash/query token을 읽은 뒤 `history.replaceState()`로 URL에서 token을 제거한다.
- 확인 결과: 주요 redirect/deeplink/open URL 경로에 안전장치가 있다.
- 보완 완료: `safeNavigation` 순수 로직 단위 테스트를 추가했다.

### 정책 목록 / 상세 UX 상태

- 정책 목록은 URL search param과 route state를 읽어 검색어, sort, statusFilter, source, 지역, Gov24 필터를 초기화한다.
- 검색 state keyword는 100자로 자른다.
- 소득/지역/Gov24 필터는 프론트 option list로 제한하고, 백엔드에서도 다시 검증한다.
- 정책 상세는 HTML entity decode, 외부 URL normalization, source별 badge, 마감/상태 포맷을 가진다.
- 상세의 오류 제보/북마크/추천 클릭은 로그인 필요 시 safe return location과 post-login action을 사용한다.
- 확인 결과: 프론트 필터와 백엔드 검증이 대체로 같은 방향으로 맞춰져 있다.
- 보완 완료: 정책 필터 option을 `policyFilterOptions.js`로 분리했고, Gov24 서비스분야/사용자구분/지원유형, sourceType, sort, statusFilter 계약을 프론트/백엔드 단위 테스트로 고정했다.

### 알림 / push UX

- Header는 알림 메뉴가 열렸을 때 최근 알림을 가져오고, 클릭 시 unread를 read로 바꾼 뒤 safe deeplink로 이동한다.
- AlertsPage/MyPage는 읽음/숨김/일괄 읽음/일괄 숨김을 제공한다.
- web push helper는 secure context, Notification, serviceWorker, PushManager 지원 여부를 확인한다.
- VAPID public key는 길이/첫 바이트와 WebCrypto import로 검증한다.
- service worker 등록, 권한 요청, 기존 구독 조회, 구독 생성, 서버 저장 단계가 각각 timeout과 단계 메시지를 가진다.
- 현재 브라우저 연결 해제 시 브라우저 subscription unsubscribe 후 서버 subscription도 삭제한다.
- 확인 결과: push 연결 실패 지점이 사용자에게 비교적 구체적으로 드러난다.
- 보완 후보: 실제 iOS/Safari/Chrome Android 권한 UX는 브라우저별 차이가 커서 수동 smoke matrix가 필요하다.

### 테스트 현황

- 프론트 package에는 `lint`, `build`, `test:unit`, `test:e2e` script가 있다.
- e2e smoke는 Playwright 기반이고 auth/login/policy/admin dashboard 흐름을 포함한다.
- Node unit test는 `safeNavigation`, axios refresh queue, service worker push/click sanitizer를 고정한다.
- 확인 결과: 핵심 URL/인증 복구/web push helper 경계는 단위 테스트로 보강했다.

### 다음 단계로 넘길 항목

- 운영 설정 섹션에서 Vite env, nginx SPA fallback, CSP/connect-src, service worker 배포 경로를 확인한다.
- 테스트 우선순위 섹션에서 주요 화면 데이터 변환 helper와 운영 smoke 계약을 계속 보강한다.

## 9. 운영 설정 / 배포 / 관측

상태: 1차 파일 점검 완료, 실제 EC2/RDS/nginx 상태는 운영 host에서 smoke 필요.

### 확인한 기준 파일

- nginx TLS/edge: `deploy/nginx/youth-welfare.conf`, `deploy/nginx/youth-welfare.bootstrap.conf`
- production compose: `docker-compose.prod.yml`
- local compose: `docker-compose.yml`
- backend image: `backend/Dockerfile`
- runtime env allowlist: `deploy/env/render-app-runtime-env.sh`
- cutover preflight: `deploy/smoke/preflight-runtime-cutover-env.sh`
- application config: `backend/src/main/resources/application.yml`
- env 예시: `env.production.example`, `.env.example`
- service worker: `frontend/public/sw.js`
- edge baseline: `deploy/nginx/verify-edge-baseline.sh`

### nginx / edge

- 운영 nginx는 `youthmoa.kr`, `www.youthmoa.kr`를 기준으로 한다.
- HTTP는 ACME challenge를 제외하고 HTTPS로 redirect한다.
- HTTPS server는 HSTS, nosniff, frame deny, referrer policy, permissions policy, CSP를 설정한다.
- `/api/`는 `127.0.0.1:8082`로 proxy한다.
- `/actuator`, `/swagger-ui`, `/v3/api-docs`는 trailing slash 유무와 관계없이 nginx 레벨에서 loopback만 허용한다.
- SPA fallback은 `try_files $uri $uri/ /index.html`이다.
- HTTPS와 bootstrap conf 모두 dotfile, env/log/sql/archive류 확장자, `wp-login.php`/`xmlrpc.php`/`cgi-bin` 스캐너 경로를 404로 막는다.
- 확인 결과: edge 보안 header와 admin/dev 문서 경로 guard가 있다.
- 보완 완료: CSP `connect-src`는 `self`, `https://youthmoa.kr`, `https://www.youthmoa.kr` 기준이고 `verify-edge-baseline.sh`가 live header와 민감 blocked path status도 확인한다.

### runtime env / secret 주입

- `render-app-runtime-env.sh`는 `.env.production` 전체를 컨테이너에 넣지 않고 allowlist key만 `.env.runtime.production`으로 렌더링한다.
- render는 기본 strict 모드에서 운영 app runtime 필수 key 누락을 target 파일 생성 전에 실패시킨다.
- target env file은 symlink를 거부하고 mode 600으로 설치한다.
- 핵심 secret `JWT_SECRET`, `AES_SECRET_KEY`, DB password, API key, SMTP/Web Push key는 allowlist에 포함된다.
- `AUTH_REFRESH_COOKIE_SECURE` 도 allowlist와 preflight 대상이며 운영에서는 `true` 만 허용한다.
- production compose는 `${APP_RUNTIME_ENV_FILE:-.env.runtime.production}`만 app env_file로 읽는다.
- `run-prod-cutover-verification.sh`는 실제 runtime env 파일을 덮어쓰지 않고 임시 0600 파일로 strict render 가능성을 먼저 확인한 뒤 preflight/RDS/edge verify를 실행한다.
- 확인 결과: 운영 컨테이너에는 의도한 runtime key만 들어가도록 설계되어 있다.
- 보완 후보: allowlist에 새 운영 env key를 추가하지 않으면 application.yml에 설정이 있어도 prod compose에는 들어가지 않는다. 새 설정 추가 시 render allowlist와 preflight를 같이 갱신한다.

### compose / container hardening

- `docker-compose.prod.yml`은 app과 redis만 띄우고 DB를 포함하지 않는다.
- app은 non-root user `10001:10001`, read_only root filesystem, `/tmp` tmpfs, `no-new-privileges`, `cap_drop: ALL`, pids/memory limit을 가진다.
- redis도 read_only, tmpfs, non-root user, cap drop, pids/memory limit을 가진다.
- app runtime secret은 compose에 직접 inline하지 않고 `${APP_RUNTIME_ENV_FILE:-.env.runtime.production}` 렌더 결과만 읽는다.
- app healthcheck는 `/actuator/health`를 loopback에서 확인한다.
- local `docker-compose.yml`은 PostgreSQL/pgvector DB를 포함하지만 `ALLOW_LOCAL_DOCKER_DB` guard label이 있다.
- 확인 결과: 운영 compose는 RDS 전제이고 컨테이너 권한을 줄이는 방향이다. `ProdComposeHardeningContractTest`가 app/redis service set, loopback bind, read-only/tmpfs/cap drop/pids/memory limit, runtime env file 분리를 고정한다.

### DB/RDS preflight

- preflight는 DB URL이 PostgreSQL이고 database가 `youth_welfare`인지 확인한다.
- core datasource는 public schema 또는 currentSchema 없음만 허용한다.
- PII datasource는 `currentSchema=youth_welfare_pii`를 요구한다.
- runtime username은 `app_core_rw`, `migration_admin`, `admin_dashboard_ro`, `app_pii_rw`, `notification_pii_ro`, 각 command/cleanup role 이름과 일치해야 한다.
- secondary datasource URL이 `DB_URL`과 완전히 같으면 실패한다.
- 운영 profile은 admin-ro, recommendation command, cleanup, PII datasource env를 no-default로 다시 선언해 누락 시 부팅/preflight에서 실패한다.
- runtime DB role password는 기본적으로 서로 달라야 한다. password 재사용은 실패하며, 로컬/예외 상황에서만 `ALLOW_SHARED_RUNTIME_DB_PASSWORDS=true`로 우회한다.
- 확인 결과: 운영 cutover 전에 DB target/schema/role 이름/password drift를 잡는 장치가 있다.

### application 운영 기본값

- JPA는 `ddl-auto=validate`라 스키마 불일치 시 부팅에서 잡힌다.
- Springdoc API docs와 Swagger UI는 기본 false다.
- actuator exposure는 health만 포함한다.
- mail health는 false로 꺼져 있다.
- rate limit 값은 policy/chat/recommend/support/auth 영역별 env로 조정 가능하다.
- collect/detail/rotation, notification web push, observability log retention도 env로 조정 가능하다.
- 확인 결과: 운영에서 직접 조정해야 하는 값들은 대부분 env 기반이다.
- 보완 완료: `NotificationMessageService` 코드 기본값과 루트 `.env.example` 도메인은 `youthmoa.kr` 기준으로 통일했다.

### service worker / web push 배포

- web push helper는 `/sw.js`를 등록한다.
- nginx SPA fallback보다 먼저 실제 정적 파일이 있으면 `/sw.js`가 그대로 제공된다.
- service worker는 push payload title/body를 길이 제한하고, click URL은 same-origin path만 허용한다.
- 운영 기록 문서에는 `https://youthmoa.kr/sw.js`가 `application/javascript`로 200 응답한 검증 이력이 있다.
- 확인 결과: web push click path도 same-origin으로 제한된다.
- 보완 완료: `serviceWorker.test.js`에서 same-origin URL 정규화, 외부/protocol-relative/비정상 URL fallback, notification body 길이 제한, click target fallback을 고정했다.

### 관측 / retention

- nginx access log는 request time, upstream response time, request id, upstream status를 남긴다.
- backend 공통 API log는 request id, userKeyHash, fingerprint, errorCode를 남긴다.
- operational log retention은 recommendation run log와 notification attempt log cleanup을 가진다.
- PII sync queue retention은 `SYNCED` row를 기본 30일 후 삭제한다. 설정은 `USER_PII_SYNC_RETENTION_ENABLED`, `USER_PII_SYNC_RETENTION_SYNCED_DAYS`, `USER_PII_SYNC_RETENTION_CRON`, `USER_PII_SYNC_RETENTION_ZONE`로 조정한다.
- deploy/performance와 deploy/smoke 아래에 edge baseline, app log 관측, ops observation wrapper, policy/recommend/chat/notification smoke가 있다.
- auth observation/session, admin dashboard/collect/recommendation/attention, ops baseline/observation smoke는 cleanup에서 `smoke_sanitize_artifacts` 를 호출한다. 보존 아티팩트에 남는 cookie jar는 삭제하고 token/password/API key/email/userKey 계열 값은 redaction한다.
- recommendation/chat/collect/Gov24/real-user 진단 smoke는 단독 실행 cleanup과 `latest` publish 직전에 같은 sanitizer를 다시 호출한다.
- nightly/active-baseline/current-priority 상위 handoff wrapper도 child stdout과 latest handoff를 publish하기 전에 sanitizer를 호출한다.
- smoke 공통 sanitize/publish 경로는 retained artifact directory를 `700`, artifact file을 `600`으로 제한한다. runtime env render는 `umask 077` 과 `install -m 600` 으로 `.env.runtime.production` 계열 파일을 만든다.
- 공통 smoke 실패 출력(`smoke_assert_status`, health wait, admin login)은 응답 body를 stderr로 내보내기 전에 token/password/API key/cookie/JDBC URL/email/userKey 계열 값을 redaction한다.
- `run-prod-cutover-verification.sh` 는 단계 stdout/stderr를 redaction한 뒤 artifact로 tee하고, 단계 종료와 cleanup에서 보존 artifact sanitizer를 다시 적용한다.
- nginx `youth_welfare_timed` access log는 `$request`/`$request_uri` 대신 `$request_method $uri $server_protocol` 을 기록해 request query string을 남기지 않는다. `Referer`도 query/fragment를 제거한 `$youth_welfare_safe_referer` map 값만 기록한다.
- performance artifact publish 공통 경로도 `smoke_sanitize_artifacts` 를 먼저 호출하므로, reload 전 access log tail에 token/code/API key query가 섞여도 latest artifact publish 전에 redaction된다.
- log alert baseline은 app/nginx raw tail을 artifact 파일에 쓰기 전에 redaction하고, evaluator stdout과 webhook payload도 `smoke_redact_stream_for_log` 를 통과한다.
- backend application log는 `LogSanitizer`와 sanitized exception summary 계약으로 secret-like 값과 직접 식별자가 error/attempt 로그에 원문으로 남는 경계를 줄인다.
- 확인 결과: 운영 관측은 smoke script 중심으로 꽤 많이 갖춰져 있다.
- 남은 운영 확인: 실제 운영 secret 값 자체는 로컬 점검에서 읽지 않았으므로, 운영 host에서는 같은 wrapper/preflight를 실행해 redacted artifact를 증적으로 남긴다.

### 다음 단계로 넘길 항목

- 테스트 우선순위 섹션에서 지금까지 발견한 후보를 위험도/효율 순으로 정렬한다.
- 운영 실행 단계는 `render-app-runtime-env.sh`, `preflight-runtime-cutover-env.sh`, RDS privilege verify, app/redis health, nginx edge baseline, runtime API smoke 순으로 `docs/deployment.md`에 runbook화했다.

## 10. 테스트 / 검증 우선순위

상태: 1차 우선순위 정리 완료.

### 바로 추가할 가치가 큰 테스트

1. `safeNavigation` 프론트 단위 테스트
   - 상태: 완료.
   - 이유: 로그인 복귀, 알림 deeplink, post-login action이 모두 의존한다.
   - 케이스: `//evil`, `https://evil.example`, control character, 2048자 초과, 정상 query/hash, 중첩 `from/chatFrom` depth 제한, invalid postLoginAction 제거.

2. axios refresh queue 테스트
   - 상태: 완료.
   - 이유: access token 만료 시 사용자 요청을 한 번에 복구하는 핵심 흐름이다.
   - 케이스: 동시 401 queue, refresh 성공 후 원 요청 재시도, refresh 실패 시 `clearSession`과 `authSessionEvents` 알림, auth endpoint에는 retry 미적용, 외부 origin에는 Authorization 미첨부.

3. 정책 랭킹의 마감 지난 ACTIVE 정책 처리 테스트
   - 상태: 코드 정책 완료. 랭킹 후보와 unique-view 집계에서 만료 정책을 제외한다.
   - 이유: 검색 `ACTIVE_ONLY`는 `apply_end_date < today`를 제외하지만 ranking은 현재 `ACTIVE/UPCOMING`만 본다.
   - 남은 점검: 실제 DB 쿼리 검증은 integration/runtime DB에서 확인한다.

4. notification retry attempt log 테스트
   - 상태: 완료.
   - 이유: initial send는 attempt log가 남지만 retry는 notification 상태 summary 중심이라 관측 일관성이 약하다.
   - 케이스: retry `sent`/`rescheduled`/`terminal_failed` 각각 `kind=notification_retry`, `channel=email`, `userKeyHash` 중심 attempt log를 남긴다.

5. PII sync status 응답 정책 테스트
   - 상태: 완료.
   - 이유: 관리자 응답에 raw userKey가 포함된다.
   - 결정: status 조회 응답은 hash만 노출하고, raw userKey가 필요한 replay/forced logout은 별도 command API 입력값으로 유지한다.

### 운영 smoke로 확인해야 하는 항목

1. 수집 외부 API smoke
   - 확인: `api_sync_logs` saved/skipped/failed 비율, detail source 중복 실행 비용, forced detail/rotation 호출량, raw payload 저장 실패율.

2. 검색/추천 품질 smoke
   - 확인: zero-result keyword, slow search, `search_youth_relevant` relevant/excluded 비율, AI cache hit율, recommendation run outcome 분포.

3. 알림 채널 smoke
   - 확인: SMTP 실제 발송, web push public key, `/sw.js` MIME, 실제 subscription test-send, retry backlog, disabled subscription 비율.

4. RDS privilege verification
   - 상태: verify script/contract 보강 완료.
   - 확인: `app_core_rw`, `app_pii_rw`, `notification_pii_ro`, admin/command/cleanup role 권한이 기대와 일치하는지. 실제 DB 검증은 운영 host에서 실행한다.

5. edge baseline
   - 확인: HSTS/CSP/security headers, `/actuator`/Swagger 외부 차단, SPA fallback, static `/sw.js`, nginx access/error log 이상 징후.

### 설정/문서 드리프트 정리 후보

1. 공식 도메인 기본값 통일
   - 상태: 완료.
   - 대상: 루트 `.env.example`, `NotificationMessageService` 기본값, 일부 테스트 fixture.
   - 기준: 운영 공식 도메인 `https://youthmoa.kr`.

2. Youth API base-url 설정 사용 여부 정리
   - 상태: 완료.
   - 결정: `YouthApiClient`가 `youth-api.base-url` 설정값을 기준으로 목록/상세 URI를 생성한다.

3. 수집 detail source 실행 순서 명시
   - 상태: 완료.
   - 결정: scheduled `collect/all` 은 list snapshot 전체 실행 후 forced detail, 마지막에 요일별 rotation detail을 실행한다.
   - 호출량/중복 해석: 같은 detail lane이 forced phase와 rotation phase에서 한 run 결과에 두 번 보일 수 있으며, 이는 list diff 대응과 backlog rotation이 겹친 의도된 상태다. 호출량 기준은 `docs/collect/collect-detail-execution-contract.md`에 고정했다.
   - 검증: `bash deploy/smoke/verify-collect-detail-execution-contract.sh`, `CollectDetailExecutionContractTest`, `CollectBatchServiceTest`가 순서/문서 계약을 확인한다.

4. role별 password 분리 preflight
   - 상태: 완료.
   - 결정: 운영 preflight는 runtime DB role password 재사용을 기본 실패 처리한다. 로컬/예외 상황은 `ALLOW_SHARED_RUNTIME_DB_PASSWORDS=true`로만 우회한다.

5. web push service worker helper 테스트
   - 상태: 완료.
   - 결정: 실제 배포 대상인 `frontend/public/sw.js`를 Node VM에서 로드해 push payload URL/text sanitizer와 notification click target을 검증한다.

6. unsubscribe query token 지원 여부
   - 상태: 완료.
   - 결정: 생성 링크/API 계약과 맞춰 프론트 수신거부 페이지도 hash token만 처리한다. query/hash에 token이 있으면 mount 직후 path만 남기지만, query token은 처리 토큰으로 쓰지 않는다.

### 장기 개선 후보

1. 프론트 unit test 기반 추가
   - 현재 프론트는 Playwright e2e와 Node unit test를 함께 가진다.
   - 정책 필터 option/codebook 정합성은 완료했다.
   - 주요 화면 데이터 변환 helper 1차도 완료했다.
   - `ChatPage`의 session/message/reference/action link 매핑은 `chatDisplay.js`와 `chatDisplay.test.js`로 고정했다.
   - `PolicyDetailPage`의 contact/reference URL/related policy 후보 매핑은 `policyDetailDisplay.js`와 `policyDetailDisplay.test.js`로 고정했다.
   - `AdminDashboardPage`의 summary 숫자/상태/날짜/마스킹/route 표시 helper는 `adminDashboardDisplay.js`와 `adminDashboardDisplay.test.js`로 고정했다.
   - 다음 후보는 Admin dashboard alert surface와 evaluator 기준 일치 확인이다.

2. Auth expired event 구조화
   - 상태: 완료.
   - 결정: `window.__authExpired` 전역 단일 handler를 제거하고, `frontend/src/lib/authSessionEvents.js`의 `subscribeAuthExpired` / `notifyAuthExpired` app-level event로 정리했다.
   - 검증: `authSessionEvents.test.js`와 `axios.test.js`가 refresh 실패 시 session clear와 만료 이벤트 발행을 고정한다.

3. search/recommend/filter codebook 동기화
   - 상태: 완료.
   - 프론트 option list와 백엔드 managed support 값은 Gov24 정책 필터 기준 contract test로 고정했다.
   - 결정: shared codebook runtime API는 즉시 구현하지 않는다. 현재 값은 stable curated UX label/managed token이고, `/api/reference/official-codes`는 admin/reference 공식 코드북 조회로 둔다.
   - 다음 후보: 중복 제거가 필요하면 먼저 build-time generated constants를 검토하고, runtime에 따라 option이 달라질 때만 `GET /api/policies/filter-codebooks` 공개 API 후보를 다시 연다.
   - 검증: `docs/policy/policy-filter-codebook-sync-plan.md`, `bash deploy/smoke/verify-policy-filter-codebook-sync-plan.sh`, `PolicyFilterCodebookSyncPlanContractTest`, `Gov24PolicyFilterSupportTest`, `policyFilterOptions.test.js`.

4. PII key rotation runbook
   - 상태: 완료.
   - 결정: `docs/core/pii-key-rotation-runbook.md`에 현재 지원되는 legacy cipher rotation과 금지된 직접 `AES_SECRET_KEY` 교체 경계를 고정했다.
   - 검증: `bash deploy/smoke/verify-pii-key-rotation-runbook.sh`와 `PiiKeyRotationRunbookContractTest`가 runbook 계약을 확인한다.

5. 운영 dashboard alert threshold 명문화
   - 상태: 완료.
   - 결정: 수집 실패율, 검색 zero-result, 추천 run failure, notification retry backlog, web push disabled ratio의 warning/critical 기준은 `docs/core/log-alert-thresholds.md`의 `alert_id` 표로 고정했다.
   - 검증: `bash deploy/smoke/verify-operational-alert-thresholds.sh`와 `OperationalAlertThresholdContractTest`가 기준 누락을 확인한다.

6. 운영 alert threshold evaluator 연결
   - 상태: 완료.
   - 결정: `docs/core/log-alert-thresholds.md`의 alert_id 기준을 `deploy/smoke/evaluate-operational-alert-thresholds.sh`에서 실제 artifact 평가로 연결했다.
   - 범위: ops observation, notification backlog, chat observability artifact는 기본 평가하고, recommendation run/web push는 `deploy/smoke/generate-operational-alert-db-summaries.sh`가 생성한 DB summary artifact로 평가할 수 있다.
   - 검증: `OperationalAlertThresholdContractTest`, `OperationalAlertEvaluatorScriptTest`, `OperationalAlertDbSummaryScriptContractTest`, `bash deploy/smoke/evaluate-operational-alert-thresholds.sh`.

7. Admin dashboard alert surface 확인
   - 상태: 완료.
   - 결정: alert status source of truth는 evaluator이고, admin dashboard는 raw triage surface로 둔다.
   - 매핑: collect/search/recommendation/notification/web push alert_id별 dashboard API, section id, raw field는 `docs/core/admin-dashboard-alert-surface-contract.md`에 고정했다.
   - 검증: `bash deploy/smoke/verify-admin-dashboard-alert-surface.sh`, `AdminDashboardAlertSurfaceContractTest`.

8. 로컬 DB runtime drift 정리
   - 상태: 완료.
   - 결정: 기존 로컬 PostgreSQL volume에는 `web_push_subscriptions`, `recommendation_run_logs`, `notification_attempt_logs`가 없을 수 있으므로, `apply-local-runtime-schema-patch.sh`가 적용하는 idempotent patch 목록에 세 테이블을 포함한다.
   - 검증: `bash deploy/postgres/apply-local-runtime-schema-patch.sh`, `bash deploy/smoke/generate-operational-alert-db-summaries.sh`, `PostgresRuntimeScriptContractTest`.

9. 수집 외부 API smoke
   - 상태: 완료.
   - 결정: 외부 API를 재호출하지 않고 `api_sync_logs`, `raw_api_payloads`, `collect_runtime_statuses`, `collect_execution_locks`를 읽는 post-run smoke를 추가한다.
   - 로컬 Docker DB 판정: 수집 테이블은 있지만 `api_sync_logs=0`, `raw_api_payloads=0`이라 `NO_COLLECT_HISTORY`로 분리한다.
   - 앱 runtime RDS 판정: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres` 기준 `BASELINE_HEALTHY`, 최근 14일 `api_sync_logs=66 -> bounded 후 67`, `raw_api_payloads=44601 -> 44602`, open circuit/active lock 없음.
   - bounded 확인: `POST /api/admin/collect/gov24-details?maxCallsPerRun=1` 결과 `requested=1 saved=1 skipped=282 failed=0`.
   - 검증: `bash deploy/smoke/run-local-collect-external-api-smoke.sh`, `CollectExternalApiSmokeContractTest`.

10. Auth/profile hydrate 실패 관측
   - 상태: 완료.
   - 결정: refresh cookie로 access token을 복원한 뒤 `/api/users/me` hydrate가 실패하면 세션은 유지하고 `authStore.profileHydration`에 실패 메타데이터를 남긴다.
   - 검증: `authStore.test.js`가 실패 상태 기록과 `clearSession` reset을 확인한다.
