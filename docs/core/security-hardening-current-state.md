# Security Hardening Current State

문서군 진입점: [system-docs-index.md](./system-docs-index.md)

관련 코드/설정:

- [backend/build.gradle](../../backend/build.gradle)
- [backend/src/main/java/com/example/welfare/user/service/AuthTokenService.java](../../backend/src/main/java/com/example/welfare/user/service/AuthTokenService.java)
- [backend/src/main/java/com/example/welfare/user/service/UserSessionRevocationService.java](../../backend/src/main/java/com/example/welfare/user/service/UserSessionRevocationService.java)
- [backend/src/main/java/com/example/welfare/global/util/JwtUtil.java](../../backend/src/main/java/com/example/welfare/global/util/JwtUtil.java)
- [deploy/nginx/youth-welfare.conf](../../deploy/nginx/youth-welfare.conf)
- [deploy/nginx/youth-welfare.bootstrap.conf](../../deploy/nginx/youth-welfare.bootstrap.conf)
- [deploy/smoke/run-local-runtime-api-smoke.sh](../../deploy/smoke/run-local-runtime-api-smoke.sh)
- [deploy/smoke/run-local-admin-forced-logout-smoke.sh](../../deploy/smoke/run-local-admin-forced-logout-smoke.sh)
- [docs/auth/auth-session-revocation-current-state.md](../auth/auth-session-revocation-current-state.md)
- [docs/deployment.md](../deployment.md)

## 목적

최근 보안 점검 follow-up의 현재 상태를 한 문서에서 바로 확인할 수 있게 정리합니다.

이 문서는 아래를 빠르게 답하기 위한 active 요약입니다.

1. 어떤 보안 이슈를 이미 코드로 고쳤는가
2. 운영 nginx/배포에 아직 반영이 필요한 것은 무엇인가
3. 지금 다시 검증하려면 어떤 명령을 먼저 돌려야 하는가

## 지금 먼저 볼 항목

### 앱 코드 계약

- access token 저장/세션 revoke: [auth-session-revocation-current-state.md](../auth/auth-session-revocation-current-state.md)
- 운영 배포/헬스체크/edge 경계: [deployment.md](../deployment.md)

### 바로 다시 실행할 검증

- backend: `cd backend && ./gradlew test --no-daemon`
- frontend: `cd frontend && npm ci && npm audit && npm run lint && npm run build`
- runtime logout smoke:
  - `APP_BASE_URL=http://127.0.0.1:8082 APP_HEALTH_URL=http://127.0.0.1:8082/actuator/health REQUIRE_RECOMMENDATIONS=false bash deploy/smoke/run-local-runtime-api-smoke.sh`
- admin forced logout smoke:
  - `bash deploy/smoke/run-local-admin-forced-logout-smoke.sh`
  - `ADMIN_PASSWORD` 또는 `/tmp/youth-welfare-admin-smoke-password` 필요

현재 CI 기준선([.github/workflows/ci.yml](../../.github/workflows/ci.yml))도 아래를 자동으로 다시 확인합니다.

- `frontend npm audit`
- `runtime logout smoke`
- `admin forced logout smoke`
- `frontend lint/build/e2e`

## 현재 구현 상태 요약

### 1. dependency hardening

현재 코드 기준 resolved dependency:

- Spring Boot `3.5.14`
- Tomcat `10.1.55`
- Spring WebMVC/WebFlux `6.2.18`
- Spring Security `6.5.10`
- PostgreSQL JDBC `42.7.11`
- Bouncy Castle `1.84`

주의:

- Tomcat `10.1.55` 는 [backend/build.gradle](../../backend/build.gradle) 의 `ext['tomcat.version']` override로 강제합니다.
- 운영 서버가 예전 `main` 을 보고 있으면 여전히 `10.1.54` 결과가 나올 수 있으니, 실제 해석 버전은 항상 `runtimeClasspath` 로 다시 확인합니다.

Web Push 전이 의존성도 현재 기준선에 포함합니다.

- `nl.martijndwars:web-push 5.1.2`
- `org.asynchttpclient:async-http-client 2.15.0`
- `org.bitbucket.b_c:jose4j 0.9.6`

### 2. access token/session hardening

현재 logout 계약:

- refresh token 삭제
- user-wide access cutoff 기록
- 요청에 실린 presented access token exact revoke
- older login token도 logout 뒤에는 `401 / A006`

핵심 구현:

- [AuthTokenService.java](../../backend/src/main/java/com/example/welfare/user/service/AuthTokenService.java)
- [UserSessionRevocationService.java](../../backend/src/main/java/com/example/welfare/user/service/UserSessionRevocationService.java)
- [JwtUtil.java](../../backend/src/main/java/com/example/welfare/global/util/JwtUtil.java)

edge case:

- cutoff와 같은 millisecond에 새 access token이 발급되면 false-negative가 날 수 있어, 새 access token `iatm` 은 필요 시 `cutoff + 1ms` 로 밀어냅니다.

추가 저장소 hardening:

- unsubscribe 메일 링크는 이제 JWT query token 대신 opaque token을 우선 사용합니다.
- Redis에는 `sha256(unsubscribeToken)` / `sha256(accessToken)` 기반 key만 저장하고, 원문 token/JWT는 key에 남기지 않습니다.

### 3. edge nginx hardening

배포 템플릿 기준 현재 원칙:

- `Strict-Transport-Security`, `X-Frame-Options`, `X-Content-Type-Options` 는 edge nginx가 단일 책임으로 내려줍니다.
- `Content-Security-Policy` 도 edge nginx가 단일 책임으로 내려줍니다.
- `/api/`, `/swagger-ui/`, `/v3/api-docs/`, `/actuator/` 프록시 경로에서는 upstream Spring이 내려준 같은 헤더를 `proxy_hide_header` 로 숨깁니다.
- `/actuator/` 는 `127.0.0.1`, `::1`, 명시 허용 IP 외에는 `deny all` 입니다.
- `server_tokens off;` 로 nginx 버전 노출을 줄입니다.
- dotfile, `.env/.sql/.log/.bak`, `wp-admin`, `wp-login.php`, `xmlrpc.php`, `cgi-bin` 같은 스캐너 경로는 edge에서 바로 차단합니다.

주의:

- 이 문서가 맞아도 실제 운영 nginx가 아직 reload되지 않았다면 외부 `https://youthmoa.kr/actuator/health` 는 여전히 `200` 일 수 있습니다.
- live external curl은 항상 “현재 운영 상태” 확인용으로 읽고, 저장소 템플릿과는 분리해서 봅니다.

### 4. admin forced logout smoke 입력값

현재 스크립트는 아래 우선순위로 admin 자격을 찾습니다.

1. shell env `ADMIN_EMAIL`, `ADMIN_PASSWORD`
2. `/tmp/youth-welfare-admin-smoke-email`, `/tmp/youth-welfare-admin-smoke-password`
3. `ENV_FILE` 또는 기본 `.env` 의 `ADMIN_EMAIL`, `ADMIN_PASSWORD`
4. `SECURITY_ADMIN_EMAILS` 의 첫 이메일

값이 없으면 이제 더 직접적인 fail-fast 메시지를 출력합니다.
로그 증거는 기본적으로 `docker logs ${APP_CONTAINER_NAME}` 를 읽고, CI/bootRun 경로에서는 `APP_LOG_FILE` 이 주어지면 그 파일을 우선 읽습니다.

### 5. input validation / manual dispatch hardening

- `AuthController` 는 `check-email`, `email-verification/send`, `email-verification/verify` 에서 입력 검증을 더 강하게 적용합니다.
- request parameter 누락/타입 오류는 `GlobalExceptionHandler` 에서 `400 / INVALID_INPUT` 으로 통일합니다.
- `NotificationController` 의 `digest-test-dispatch`, `deadline-test-dispatch`, `push-test-send` 는 이제 admin 전용 manual dispatch 경계입니다.
- `deadline-test-dispatch?days=` 는 `1..30` 범위만 허용합니다.

### 6. container / prod runtime hardening

- prod app 컨테이너는 non-root `appuser` 로 실행합니다.
- `docker-compose.prod.yml` 은 `read_only`, `tmpfs`, `no-new-privileges`, `pids_limit`, `mem_limit` 를 적용합니다.
- prod compose logging은 `json-file` rotation cap을 둡니다.

### 7. DB 최소권한 2차 progress

- `app_core_rw` 는 더 이상 `chat_sessions`, `cluster_ai_results`, `collect_execution_locks`, `web_push_subscriptions`, `recent_policy_views` 에서 `DELETE` 를 직접 갖지 않습니다.
- 해당 delete는 각각 `chat_session_cleanup_rw`, `cluster_ai_cleanup_rw`, `collect_execution_lock_cleanup_rw`, `web_push_subscription_cleanup_rw` 전용 경계가 맡습니다.
- `recent_policy_views` 는 앱 경로상 `INSERT ... ON CONFLICT DO UPDATE` 와 read만 사용하므로, 별도 cleanup role을 추가하지 않고 `DELETE` 만 걷어내는 bounded step으로 닫았습니다.
- `recommendation_review_gate_promotion_approvals` 는 admin 전용 upsert/delete 단일 테이블이라 `recommendation_review_gate_command_rw` 전용 command 경계로 분리했습니다. 이제 `app_core_rw` 는 이 테이블에 대한 `SELECT/INSERT/UPDATE/DELETE` 를 직접 갖지 않습니다.
- `user_recommendations` refresh replace 경로도 `recommendation_persistence_command_rw` 전용 command 경계로 분리했습니다. 이제 `app_core_rw` 는 이 테이블의 `DELETE` 를 직접 갖지 않고, refresh replace의 `DELETE + INSERT` 는 command datasource 한 트랜잭션으로 수행합니다. retention cleanup은 기존 `recommendation_retention_cleanup_rw` 전용 경계를 유지합니다.
- 위 분리 뒤에도 기본 앱 `@Transactional` 경로는 계속 primary JPA `transactionManager` 를 사용해야 합니다. 현재 기준선은 [PrimaryDataSourceConfig.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/global/config/PrimaryDataSourceConfig.java:1) 에 `transactionManager` / `primaryTransactionManager` bean을 명시해 signup/login 같은 일반 런타임 API가 secondary manager 추가 때문에 깨지지 않는 상태입니다.
- fresh init은 [z90-create-runtime-db-users.sh](../../deploy/postgres/init/z90-create-runtime-db-users.sh), 기존 volume drift 복구는 [V2026_05_29_01__tighten_app_core_cleanup_delete_grants.sql](../../deploy/postgres/patches/V2026_05_29_01__tighten_app_core_cleanup_delete_grants.sql) 기준으로 맞춥니다.
- `recent_policy_views` revoke drift 복구는 [V2026_05_30_03__tighten_recent_policy_views_delete_grant.sql](../../deploy/postgres/patches/V2026_05_30_03__tighten_recent_policy_views_delete_grant.sql) 기준으로 맞춥니다.
- `recommendation_review_gate_promotion_approvals` command role drift 복구는 [V2026_05_30_04__add_recommendation_review_gate_command_role.sql](../../deploy/postgres/patches/V2026_05_30_04__add_recommendation_review_gate_command_role.sql) 기준으로 맞춥니다.
- `user_recommendations` command role drift 복구는 [V2026_05_30_05__add_recommendation_persistence_command_role.sql](../../deploy/postgres/patches/V2026_05_30_05__add_recommendation_persistence_command_role.sql) 기준으로 맞춥니다.
- local 재검증 기준 `ChatSessionApi/AuthRedis/ChatMessage/ChatRepository/UserWithdraw` integration 세트와 `run-local-runtime-api-smoke.sh`, `run-local-admin-forced-logout-smoke.sh` 가 다시 통과했습니다.

### 8. query/input cost guard

- `GET /api/recommendations?size=` 는 `1..100` 만 허용합니다.
- `PolicyRankingService` 도 랭킹 size를 내부에서 `MAX_SIZE=100` 으로 clamp 합니다.
- collect/policy admin 수동 경로는 `maxCallsPerRun <= 5000`, `limitPerSource <= 1000`, `rounds <= 10`, `maxCallsPerRound <= 1000` 상한을 둡니다.

### 9. RDS bootstrap hardening

- runtime DB role bootstrap은 PostgreSQL 식별자를 quoting해서 하이픈/대소문자/특수문자 섞인 role 이름도 안전하게 처리합니다.
- migration role 권한 보존 로직도 같이 정리돼, bootstrap 과정에서 runtime role만 다시 만들다가 migration 경계를 깨지 않게 맞춥니다.
- 운영 bootstrap 현재 기준선에는 `chat_session_cleanup_rw` 도 포함되고, bootstrap 뒤에는 [verify-rds-runtime-privileges.sh](../../deploy/postgres/verify-rds-runtime-privileges.sh) 로 실제 login/grant를 다시 확인합니다.

## 운영 반영 시 체크할 것

### 앱 재배포 뒤

- `curl http://127.0.0.1:8082/actuator/health`
- `APP_BASE_URL=http://127.0.0.1:8082 APP_HEALTH_URL=http://127.0.0.1:8082/actuator/health REQUIRE_RECOMMENDATIONS=false bash deploy/smoke/run-local-runtime-api-smoke.sh`

기대값:

- `presented_after_logout=401/A006`
- `older_login_token_after_logout=401/A006`

### nginx 반영 뒤

- `nginx -t`
- `sudo systemctl reload nginx`
- `PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/nginx/verify-edge-baseline.sh`

기대값:

- 외부 `/actuator/health` 는 `403` 또는 내부망 only
- `Strict-Transport-Security`, `X-Frame-Options`, `X-Content-Type-Options` 는 각각 1개만 존재

### 운영 cutover 묶음 확인

- `ENV_FILE=.env.production PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-prod-cutover-verification.sh`

이 wrapper는 env preflight, RDS privilege verify, nginx edge verify를 순서대로 태웁니다.

## 지금 남은 리스크

1. 저장소 템플릿 수정과 실제 운영 nginx 반영은 별개입니다.
2. `SecurityConfig` 는 여전히 `/actuator/health` 를 앱 레벨에서 `permitAll` 로 둡니다.
   - 현재 설계는 “외부 차단은 nginx, 내부 직접 포트 health는 허용”입니다.
3. admin forced logout full smoke는 비밀번호가 없으면 실행 자체는 못 합니다.
4. live external curl 결과는 저장소 수정이 아니라 현재 운영 반영 상태를 보여 줍니다.

## 관련 기록

- 진행 기록: [phase-plan.md](../phase-plan.md)
- 문제/해결 로그: [troubleshooting-log.md](./troubleshooting-log.md)
