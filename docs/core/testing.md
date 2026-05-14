# 테스트 실행 기준

문서군 진입점: [local-validation-docs-index.md](./local-validation-docs-index.md)

## 기본 테스트

DB나 Redis 없이 빠르게 확인할 때 실행합니다.

```bash
cd backend
./gradlew test
```

기본 `test` 태스크는 `backend/src/test/java/com/example/welfare/integration` 아래 통합 테스트를 제외합니다.

## 통합 테스트

MySQL과 Redis까지 포함한 실제 흐름을 확인할 때 실행합니다.

```bash
deploy/smoke/preflight-integration-runtime.sh
docker compose up -d db redis

cd backend
./gradlew integrationTest
```

통합 테스트는 `integration` 프로필을 사용하며, 기본 연결 정보는
[application-integration.yml](../backend/src/test/resources/application-integration.yml)에 정의되어 있습니다.
현재 main 기준 integration runtime 기대값은 `127.0.0.1:5433` PostgreSQL과 `127.0.0.1:6379` Redis 입니다.
`integrationTest` 는 실행 전에 `integrationRuntimePreflight` 를 먼저 태워 이 runtime 이 없으면 개별 테스트 62건을 쏟기 전에 즉시 실패합니다.
WSL에서 Docker Desktop을 쓰는 경우 `docker` 명령이 안 보이면 먼저 Docker Desktop의 WSL integration을 켜야 합니다.
실행 전에 shell 기준 진단만 빠르게 보고 싶으면 `deploy/smoke/preflight-integration-runtime.sh` 를 먼저 실행합니다.

로컬 Docker DB 볼륨을 오래 재사용해 `Access denied for user 'app_core_rw'` 같은 split-account 인증 실패가 나면,
볼륨을 지우기 전에 아래 복구 스크립트로 계정을 현재 `.env` 기준으로 다시 맞춥니다.

```bash
ENV_FILE=.env deploy/mysql/reconcile-local-runtime-db-accounts.sh
docker compose up -d db redis

cd backend
./gradlew integrationTest
```

이 스크립트는 기존 `mysql_data` volume을 지우지 않고, recovery MySQL을 `--skip-grant-tables` 로 잠깐 띄워
`root`, `app_core_rw`, `app_pii_rw`, `notification_pii_ro`, `migration_admin` 계정과 grant를 현재 split-account 기본값으로 재정렬합니다.
현재 로컬 `.env` 가 아직 `DB_USERNAME=root` 여도, 이 스크립트는 대상 계정을 고정된 split-account 이름으로 맞춥니다.
`.env` 를 shell `source` 하지 않고 직접 파싱하므로 JDBC URL의 `&` 때문에 깨지지 않습니다.

## Gmail SMTP smoke test

실제 Gmail SMTP 설정으로 테스트 메일 1건을 발송할 때만 실행합니다.
기본 테스트에서는 비활성화되어 있으며, `RUN_SMTP_SMOKE=true`를 명시해야 동작합니다.

```bash
cd backend
GMAIL_USERNAME=your-account@gmail.com \
GMAIL_PASSWORD=your-app-password \
SMTP_SMOKE_TO=receiver@example.com \
RUN_SMTP_SMOKE=true ./gradlew test --tests com.example.welfare.notification.gateway.GmailSmtpSmokeTest --rerun-tasks
```

`.env` 전체를 shell `source` 하지 말고, 필요한 Gmail 관련 값만 inline env 또는 `export`로 넘깁니다.
수신 주소를 발신 계정과 다르게 지정하려면 `.env`의 `SMTP_SMOKE_TO` 값을 그대로 넘기면 됩니다.

```bash
RUN_SMTP_SMOKE=true ./gradlew test --tests com.example.welfare.notification.gateway.GmailSmtpSmokeTest --rerun-tasks
```

## 언제 무엇을 실행할까

- 서비스/컨트롤러/리포지토리 단위 변경: `./gradlew test`
- 로그인, refresh token, Redis 저장 흐름 변경: `./gradlew integrationTest`
- 북마크, 추천 refresh/get 전체 흐름 변경: `./gradlew integrationTest`
- Gmail SMTP 계정/앱 비밀번호 검증: `RUN_SMTP_SMOKE=true ./gradlew test --tests com.example.welfare.notification.gateway.GmailSmtpSmokeTest --rerun-tasks`
- 배포 전 최종 확인: `./gradlew test` 실행 후 `docker compose up -d db redis` 상태에서 `./gradlew integrationTest`

## integration preflight 우회

기본값으로는 우회하지 않는 것이 맞습니다. 다만 runtime availability와 무관한 실험이 필요하면 아래 둘 중 하나로 preflight만 끌 수 있습니다.

```bash
cd backend
SKIP_INTEGRATION_RUNTIME_PREFLIGHT=true ./gradlew integrationTest
```

```bash
cd backend
./gradlew integrationTest -PskipIntegrationRuntimePreflight=true
```
