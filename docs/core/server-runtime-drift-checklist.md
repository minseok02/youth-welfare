# 서버 런타임 Drift 체크리스트

> 서버 RDS와 로컬 기존 Docker 볼륨은 별도 대상입니다. [환경 안내](environment-guide.md)와 [DB 변경 절차](db-migration.md)를 먼저 확인합니다. 아래 복구·schema patch·forced logout·알림 smoke는 조회만 하는 작업이 아니며 운영에 자동 실행하지 않습니다.

문서군 진입점: [local-validation-docs-index.md](./local-validation-docs-index.md)

이 문서는 최근 서버/기존 PostgreSQL volume에서 반복된 drift만 모아 둔 운영 체크리스트입니다.
새 기능 설명이 아니라, `git pull -> app rebuild -> smoke` 사이에서 자주 틀리는 지점을 빠르게 다시 맞추는 용도입니다.

## 언제 먼저 보는가

- 서버에서 최신 `main` 으로 app을 재기동했는데 schema validation으로 부팅 실패할 때
- same code인데 local은 되는데 server smoke만 깨질 때
- admin smoke가 `403/C003` 또는 `ADMIN_PASSWORD is empty` 로 실패할 때
- deadline reminder smoke가 `NO_CANDIDATES` 로 오판할 때

## 1. 최신 코드 반영

```bash
git pull --ff-only origin main
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build app
curl -s http://127.0.0.1:8082/actuator/health
```

기대값:
- `{"status":"UP"}`

## 2. 기존 volume이면 migration 자동 적용을 기대하지 말 것

현재 빌드에는 Flyway 자동 실행이 구성되어 있지 않습니다. `flyway_schema_history`가 없어서만이 아니라 migration 자동 실행 경로 자체가 없으므로, SQL 파일 추가나 앱 재빌드만으로 기존 DB가 갱신되지 않습니다.
JPA가 매핑한 table/column 누락은 schema validation 단계에서 부팅을 막을 수 있습니다. JDBC 전용 경로의 필수 테이블은 기동 뒤 해당 기능에서만 실패할 수도 있어, health `UP`만으로 schema가 모두 맞다고 판단하지 않습니다.

최근 실제로 수동 적용이 필요했던 파일:

```text
backend/src/main/resources/db/migration/V2026_05_15_02__add_user_alerts.sql
backend/src/main/resources/db/migration/V2026_05_15_03__add_web_push_subscriptions.sql
backend/src/main/resources/db/migration/V2026_05_16_01__add_notification_channel_flags.sql
backend/src/main/resources/db/migration/V2026_05_17_01__add_ai_status_to_user_recommendations.sql
backend/src/main/resources/db/migration/V2026_05_17_02__add_user_account_origin.sql
```

대표 증상:
- `missing table [user_alerts]`
- `missing column [user_profiles.notification_*]`
- `missing column [user_recommendations.ai_status]`
- `missing column [users.account_origin]`

주의:
- `2026-05-17` 서버 재기동에서도 실제로 `users.account_origin` 누락 때문에 첫 부팅이 실패했다.
- 따라서 기존 volume에서는 `V2026_05_17_01__add_ai_status_to_user_recommendations.sql` 만이 아니라 `V2026_05_17_02__add_user_account_origin.sql` 도 함께 봐야 한다.

수동 적용 후에는 다시:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build app
```

## 3. 최소 schema/grant 확인

다음 쿼리는 **로컬 Docker DB 전용**입니다. RDS에는 `youth-welfare-db` 컨테이너가 없으므로 앱과 같은 DB 대상을 확인하고 [RDS 권한 검증](../../deploy/postgres/verify-rds-runtime-privileges.sh) 및 승인된 직접 조회 절차를 사용합니다. 로컬 앱 재빌드는 `-f docker-compose.yml`을 명시하며 위 서버 Compose 명령과 구분합니다.

로컬 확인 쿼리:

```bash
docker exec -it youth-welfare-db psql -U postgres -d youth_welfare -c "\d user_alerts"
docker exec -it youth-welfare-db psql -U postgres -d youth_welfare -c "\d web_push_subscriptions"
docker exec -it youth-welfare-db psql -U postgres -d youth_welfare -c "\d user_profiles"
docker exec -it youth-welfare-db psql -U postgres -d youth_welfare -c "\d user_recommendations"
```

최근 기준으로 꼭 보여야 하는 것:
- `user_alerts`
- `web_push_subscriptions`
- `users.account_origin`
- `user_profiles.notification_email_yn`
- `user_profiles.notification_in_app_yn`
- `user_profiles.notification_web_push_yn`
- `user_recommendations.ai_status`
- 수집 diff용 `collect_list_snapshots`, `collect_list_snapshot_items`: health와 별도로 존재·권한 확인

`ai_status` 기대값:
- `NOT NULL`
- default = `NOT_REQUESTED`

2026-10-21 로컬 관측에서는 앱이 `UP`인데 두 수집 snapshot 테이블이 없었습니다. relation 조회와 SQL 경로의 누락은 [DB 변경 절차의 snapshot 진단](db-migration.md#수집-snapshot-테이블-누락)을 따릅니다. 로컬 patch 스크립트가 모든 `db/migration/` 파일을 적용한다고 가정하지 않습니다.

## 4. admin smoke 자격증명 우선순위

현재 direct admin smoke와 `run-local-validation-from-env.sh` 는 같은 우선순위를 따릅니다.

1. 이미 export된 `ADMIN_EMAIL`, `ADMIN_PASSWORD`
2. `/tmp/youth-welfare-admin-smoke-email`, `/tmp/youth-welfare-admin-smoke-password`
3. `.env` 의 `ADMIN_EMAIL`, `ADMIN_PASSWORD`
4. `.env` 의 `SECURITY_ADMIN_EMAILS`
   - email만 보조로 사용
   - password는 따로 필요

중요:
- 서버에서는 local 기본값 `<local admin email>/<local admin password>` 를 기대하지 않습니다.
- 운영 기준으로는 **서버 allowlist admin 이메일 + 그 계정 비밀번호**를 명시해서 실행합니다.

자격증명은 [배포 문서의 숨김 입력 서브셸](../deployment.md#9-운영-smoke-최소-순서)처럼 준비합니다. 실제 비밀번호를 명령 문자열에 쓰지 않고, 같은 서브셸에서 필요한 검사만 선택합니다. 다음 full wrapper는 조회 전용이 아니므로 승인된 변경 범위가 있을 때만 실행합니다.

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
ADMIN_EMAIL="${ADMIN_EMAIL:?admin email required}" \
ADMIN_PASSWORD="${ADMIN_PASSWORD:?use hidden input first}" \
VALIDATION_APP_BASE_URL=http://127.0.0.1:8082 \
bash deploy/smoke/run-local-validation-from-env.sh --full
```

직접 admin smoke도 같은 방식으로 맞춥니다.

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
ADMIN_EMAIL="${ADMIN_EMAIL:?admin email required}" \
ADMIN_PASSWORD="${ADMIN_PASSWORD:?use hidden input first}" \
APP_BASE_URL=http://127.0.0.1:8082 \
bash deploy/smoke/run-local-admin-forced-logout-smoke.sh
```

## 5. full wrapper가 `ADMIN_PASSWORD is empty` 로 깨질 때

이건 기능 회귀보다 자격증명 주입 문제일 가능성이 큽니다.

확인 순서:
1. `ADMIN_PASSWORD` export 여부
2. `/tmp/youth-welfare-admin-smoke-password` 존재 여부
3. `.env` 에는 `SECURITY_ADMIN_EMAILS` 만 있고 password는 없다는 점

즉 `.env` 만으로는 admin password가 채워지지 않습니다.

## 6. deadline reminder smoke 날짜 기준

`run-local-deadline-reminder-smoke.sh` 는 이제 host shell `date` 가 아니라
**DB `CURRENT_DATE + days`** 기준으로 `apply_end_date` 를 강제합니다.

이유:
- host 날짜와 app/DB 날짜가 하루 어긋나면
- `DeadlineReminderDispatchService` 의 `today ~ today+days` inclusive 필터와 mismatch가 나서
- `NO_CANDIDATES` 오탐이 생길 수 있습니다.

같은 문제를 수동으로 재현/디버깅할 때도 DB 날짜를 먼저 봅니다.

```bash
docker exec -it youth-welfare-db psql -U postgres -d youth_welfare -c "SELECT CURRENT_DATE;"
```

추가 주의:

- 이 smoke는 `welfare_services.apply_end_date/status` 를 잠깐 바꿉니다.
- 로컬 Docker DB에서는 trap으로 원복됩니다.
- RDS 같은 direct postgres mode에서는 기본 차단되며, 정말 돌려야 하면 `ALLOW_DIRECT_DB_MUTATION=true` 를 명시해야 합니다.

## 6-1. RDS / direct postgres mode

DB 컨테이너가 없는 EC2 + RDS 런타임에서는 아래처럼 direct postgres mode를 명시합니다.

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL=http://127.0.0.1:8082 \
ADMIN_EMAIL="${ADMIN_EMAIL:?admin email required}" \
ADMIN_PASSWORD="${ADMIN_PASSWORD:?use hidden input first}" \
bash deploy/smoke/run-local-admin-forced-logout-smoke.sh
```

기본 query 계정은 `.env.production` 의 `DB_MIGRATION_USERNAME` / `DB_MIGRATION_PASSWORD` 를 사용합니다.
다른 계정을 강제하려면 `DB_QUERY_USERNAME`, `DB_QUERY_PASSWORD`, 필요 시 `DB_DIRECT_URL` 을 직접 넘깁니다.

## 7. 현재 기준 빠른 재확인 묶음

아래는 기존 재현용 검사 묶음입니다. 알림·추천·계정 상태와 정책 날짜를 변경할 수 있으므로 서버의 정기 조회 명령으로 사용하지 않습니다. 조회만 필요한 경우 [ops baseline](ops-baseline-runbook.md)을 사용합니다.

기존 broader baseline에 포함된 경로는 다음과 같습니다. 한 번에 복사해 서버에서 실행하는 명령 묶음이 아닙니다.

- `deploy/smoke/run-local-validation-from-env.sh --full`: 인증·사용자·추천 등 상태 변경 포함. 위 숨김 입력과 앱/DB 지정이 필요합니다.
- `deploy/smoke/run-local-notification-channel-smoke.sh`: 알림 설정·발송 경계 확인. 대상 계정과 실제 외부 발송 여부를 먼저 검토합니다.
- `deploy/smoke/run-local-deadline-reminder-smoke.sh`: 정책 날짜를 변경하므로 격리된 로컬 fixture 대상과 direct DB mutation 차단을 확인합니다.
- `deploy/smoke/run-local-gov24-recommendation-suite.sh`: 수집/추천 관련 선택 단계와 DB 변경·외부 호출 영향을 확인합니다.

과거 문서의 known-good 기록이며 현재 서버에서 재검증한 결과가 아님:
- `full validation` green
- `notification channel smoke` green
- `deadline reminder smoke` green
- `gov24 recommendation suite` green

## 8. 한 줄 요약

1. 기존 volume이면 migration 자동 적용을 기대하지 말고 missing table/column부터 본다.
2. server smoke는 `.env` 만 보지 말고 `ADMIN_EMAIL`/`ADMIN_PASSWORD` 주입 우선순위를 같이 본다.
3. deadline reminder는 host 날짜가 아니라 DB `CURRENT_DATE` 기준으로 읽는다.
