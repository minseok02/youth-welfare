# 테스트 실행 기준

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
docker compose up -d db redis

cd backend
./gradlew integrationTest
```

통합 테스트는 `integration` 프로필을 사용하며, 기본 연결 정보는
[application-integration.yml](../backend/src/test/resources/application-integration.yml)에 정의되어 있습니다.

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
