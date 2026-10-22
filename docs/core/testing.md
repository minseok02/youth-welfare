# 테스트 실행 기준

명령은 저장소 루트를 기준으로 설명합니다. [환경 안내](environment-guide.md)에서 로컬 개발 DB와 서버/RDS를 구분합니다. 단위 테스트·통합 fixture·브라우저 검증·실제 외부 발송은 서로 다른 검사입니다.

## 백엔드 단위 테스트

```bash
cd backend
DB_USERNAME=app_core_rw ./gradlew test --no-daemon
```

기본 `test`는 `backend/src/test/java/com/example/welfare/integration` 아래 통합 테스트를 제외합니다. SMTP smoke는 명시적으로 켜지 않는 한 비활성화됩니다.

현재 Gradle은 구성 단계에서 bootRun용 설정도 평가하므로, 루트 `.env`의 `DB_USERNAME=root` 같은 값이 단위 테스트 시작을 막을 수 있습니다. 위 inline 값은 설정 검사 충돌을 피하기 위한 것이며 DB에 role을 만들거나 운영 설정을 바꾸는 명령이 아닙니다. env 파일 전체를 shell에 source하지 않습니다.

문서의 경로나 내용을 바꿨다면 해당 `*ContractTest`도 확인합니다. 현재 문서는 Gradle test의 변경 입력으로 추적되지 않을 수 있어, 코드가 그대로면 `UP-TO-DATE`로 검사가 생략됩니다. 문서/스크립트 계약을 실제 재검증할 때는 `backend/`에서 다음처럼 test task를 재실행합니다 (현재 Gradle 8.7 기준).

```bash
DB_USERNAME=app_core_rw ./gradlew test --tests '*ContractTest' --rerun --no-daemon
```

관련 테스트를 더 좁힐 수 있으면 해당 클래스를 지정합니다. Markdown만 변경했더라도 관련 계약 검사를 `git diff --check`나 캐시된 성공으로 대신하지 않습니다. 전체 테스트나 의존 task까지 재실행하는 `--rerun-tasks`는 변경 영향·실패 원인상 필요한 경우 사용합니다.

[RuntimeSmokeExecutionContractTest](../../backend/src/test/java/com/example/welfare/global/config/RuntimeSmokeExecutionContractTest.java)는 실제 검증 스크립트를 임시 디렉터리에 복사하고 HTTP/DB/credential helper를 대체해 실행합니다. 추천 클릭 GET/POST·DB 증적, 성능 검색 POST JSON·HTTP 실패 판정, 복구 런북의 대상/Redis guard를 확인합니다. Bash/Python이 필요하며 실제 Docker·DB·Redis·네트워크는 사용하지 않습니다. 이것은 실제 앱 smoke나 브라우저 검증의 대체가 아닙니다.

## 프론트 단위와 정적 검사

```bash
cd frontend
npm run test:unit
npm run lint
npm run build
```

브라우저·백엔드 없이 실행하는 범위입니다. 로그인·API 연동·운영 브라우저 흐름까지 검증한 결과는 아닙니다.

## 로컬 통합 테스트

통합 테스트는 계정·추천·알림 등 fixture 데이터를 변경합니다. 기본 대상은 로컬 PostgreSQL `127.0.0.1:5433`, Redis `127.0.0.1:6379`입니다. 운영 RDS에 fixture를 쓰지 않습니다.

개발 컴퓨터에서만 다음 순서로 준비합니다.

```bash
ALLOW_LOCAL_DOCKER_DB=true docker compose --env-file .env -f docker-compose.yml up -d db redis
bash deploy/smoke/preflight-integration-runtime.sh
cd backend
DB_USERNAME=app_core_rw ./gradlew integrationTest --no-daemon
```

설정은 [application-integration.yml](../../backend/src/test/resources/application-integration.yml)과 [build.gradle](../../backend/build.gradle)의 `INTEGRATION_*` 처리를 확인합니다. Gradle preflight는 포트뿐 아니라 목적별 datasource 로그인도 검사합니다. 운영 `DB_URL`은 통합 테스트 URL의 fallback이 아니며 명시된 통합 URL도 로컬 PostgreSQL 주소만 허용합니다.

WSL에서 Docker Desktop을 사용하는 경우 WSL integration이 필요합니다. Docker/DB/Redis를 사용할 수 없으면 통합 검증 미실행으로 보고하며 성공으로 처리하지 않습니다. preflight 우회를 일반 검증 절차로 사용하지 않습니다.

기존 볼륨의 schema/grant 문제는 [DB 변경 절차](db-migration.md)를 확인합니다. schema patch나 볼륨 삭제를 테스트 실행에 자동으로 포함하지 않습니다.

## 브라우저 검증

```bash
cd frontend
npm run test:e2e
```

실행 모드와 앱 대상은 [프론트 QA 목차](../frontend/frontend-qa-docs-index.md), [환경 안내](environment-guide.md)를 확인합니다. 가입·비밀번호 변경·프로필·북마크·클릭 등의 상태 변경이 있을 수 있습니다. 배포 origin을 지정한 검증을 로컬 테스트라고 부르지 않습니다.

관리자 검증은 명시적 credential과 대상 권한을 확인하고, 비로그인/일반 사용자 차단도 검증합니다.

## 외부 발송과 운영 점검

SMTP는 별도 opt-in 검사입니다. **다음 명령은 실제 메일을 발송**하므로 발송 계정과 수신 대상을 먼저 확인합니다.

```bash
cd backend
DB_USERNAME=app_core_rw MAIL_HOST=smtp.gmail.com MAIL_PORT=587 \
MAIL_USERNAME='<smtp username>' MAIL_PASSWORD='<app password>' \
SMTP_SMOKE_TO='<test recipient>' RUN_SMTP_SMOKE=true \
./gradlew test --tests com.example.welfare.notification.gateway.SmtpSmokeTest --rerun-tasks
```

SES는 [전환 런북](mail-ses-migration-runbook.md)을 따릅니다. 실제 OpenAI·수집·SMTP·웹푸시·부하 시험과 운영 smoke는 일반 단위·문서 검증에 포함하지 않으며, 요청된 검증 범위와 대상·영향이 확인된 경우 실행합니다.

운영 조회/점검 절차는 [배포](../deployment.md), [운영 체크리스트](final-ops-closeout-checklist.md#attention과-backlog-판정), [배포 후 점검](final-ops-closeout-checklist.md)을 따릅니다. `run-local-*` 이름이어도 설정에 따라 운영 앱/DB에 연결될 수 있습니다.

## 결과 기록

실행 명령, 커밋, 환경·대상, 통과/실패/skip, 미실행 이유를 함께 보고합니다. 변경 작업의 검증은 [phase-plan.md](../phase-plan.md)에, 장기적으로 재사용할 원인·해결만 [troubleshooting-log.md](troubleshooting-log.md)에 남깁니다. 변경 없는 검토의 기록 범위는 [작업 가이드](../work-guide.md#완료와-기록)를 따릅니다.
