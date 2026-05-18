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

PostgreSQL과 Redis까지 포함한 실제 흐름을 확인할 때 실행합니다.

```bash
deploy/smoke/preflight-integration-runtime.sh
docker compose up -d db redis

cd backend
./gradlew integrationTest
```

통합 테스트는 `integration` 프로필을 사용하며, 기본 연결 정보는
[application-integration.yml](../backend/src/test/resources/application-integration.yml)에 정의되어 있습니다.
현재 main 기준 integration runtime 기대값은 `127.0.0.1:5433` PostgreSQL과 `127.0.0.1:6379` Redis 입니다.
`integrationTest` 는 실행 전에 `integrationRuntimePreflight` 를 먼저 태워 이 runtime 이 없으면 개별 통합 테스트가 연쇄로 쏟아지기 전에 즉시 실패합니다.
WSL에서 Docker Desktop을 쓰는 경우 `docker` 명령이 안 보이면 먼저 Docker Desktop의 WSL integration을 켜야 합니다.
실행 전에 shell 기준 진단만 빠르게 보고 싶으면 `deploy/smoke/preflight-integration-runtime.sh` 를 먼저 실행합니다.

로컬 Docker PostgreSQL 볼륨을 오래 재사용해 `schema-validation` 실패나 누락 컬럼 문제처럼
현재 `schema.sql` 과 drift 된 상태가 보이면, 볼륨을 지우기 전에 아래 patch 스크립트로 현재 기준선을 먼저 맞춥니다.

```bash
deploy/postgres/apply-local-runtime-schema-patch.sh
docker compose up -d db redis

cd backend
./gradlew integrationTest
```

이 스크립트는 기존 PostgreSQL volume을 지우지 않고, 현재 main 기준에서 확인된 drift patch
(`chat_messages.references_json`, `chat_retrieval_snapshots.needs_clarification` 등)를 재적용합니다.
현재 main 기준 로컬 계정/권한은 Docker Compose init 경로에서 맞춰지므로,
`deploy/mysql/**` 아래 계정 복구 스크립트는 legacy MySQL history로만 봅니다.

## Gmail SMTP smoke test

실제 SMTP 설정으로 테스트 메일 1건을 발송할 때만 실행합니다.
기본 테스트에서는 비활성화되어 있으며, `RUN_SMTP_SMOKE=true`를 명시해야 동작합니다.

```bash
cd backend
MAIL_HOST=smtp.gmail.com \
MAIL_PORT=587 \
MAIL_USERNAME=your-account@gmail.com \
MAIL_PASSWORD=your-app-password \
SMTP_SMOKE_TO=receiver@example.com \
RUN_SMTP_SMOKE=true ./gradlew test --tests com.example.welfare.notification.gateway.GmailSmtpSmokeTest --rerun-tasks
```

`.env` 전체를 shell `source` 하지 말고, 필요한 SMTP 관련 값만 inline env 또는 `export`로 넘깁니다.
수신 주소를 발신 계정과 다르게 지정하려면 `.env`의 `SMTP_SMOKE_TO` 값을 그대로 넘기면 됩니다.

```bash
RUN_SMTP_SMOKE=true ./gradlew test --tests com.example.welfare.notification.gateway.GmailSmtpSmokeTest --rerun-tasks
```

## 언제 무엇을 실행할까

- 서비스/컨트롤러/리포지토리 단위 변경: `./gradlew test`
- 로그인, refresh token, Redis 저장 흐름 변경: `./gradlew integrationTest`
- 북마크, 추천 refresh/get 전체 흐름 변경: `./gradlew integrationTest`
- SMTP 계정/비밀번호 검증: `RUN_SMTP_SMOKE=true ./gradlew test --tests com.example.welfare.notification.gateway.GmailSmtpSmokeTest --rerun-tasks`
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
