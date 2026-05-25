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

### 3. edge nginx hardening

배포 템플릿 기준 현재 원칙:

- `Strict-Transport-Security`, `X-Frame-Options`, `X-Content-Type-Options` 는 edge nginx가 단일 책임으로 내려줍니다.
- `/api/`, `/swagger-ui/`, `/v3/api-docs/`, `/actuator/` 프록시 경로에서는 upstream Spring이 내려준 같은 헤더를 `proxy_hide_header` 로 숨깁니다.
- `/actuator/` 는 `127.0.0.1`, `::1`, 명시 허용 IP 외에는 `deny all` 입니다.

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
- `curl -sS -D - -o /dev/null https://youthmoa.kr/actuator/health`

기대값:

- 외부 `/actuator/health` 는 `403` 또는 내부망 only
- `Strict-Transport-Security`, `X-Frame-Options`, `X-Content-Type-Options` 는 각각 1개만 존재

## 지금 남은 리스크

1. 저장소 템플릿 수정과 실제 운영 nginx 반영은 별개입니다.
2. `SecurityConfig` 는 여전히 `/actuator/health` 를 앱 레벨에서 `permitAll` 로 둡니다.
   - 현재 설계는 “외부 차단은 nginx, 내부 직접 포트 health는 허용”입니다.
3. admin forced logout full smoke는 비밀번호가 없으면 실행 자체는 못 합니다.
4. live external curl 결과는 저장소 수정이 아니라 현재 운영 반영 상태를 보여 줍니다.

## 관련 기록

- 진행 기록: [phase-plan.md](../phase-plan.md)
- 문제/해결 로그: [troubleshooting-log.md](./troubleshooting-log.md)
