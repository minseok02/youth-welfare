# 서버 런타임 Drift 체크리스트

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
docker compose up -d --build app
curl -s http://127.0.0.1:8082/actuator/health
```

기대값:
- `{"status":"UP"}`

## 2. 기존 volume이면 migration 자동 적용을 기대하지 말 것

현재 서버/기존 volume에서는 `flyway_schema_history` 부재 때문에 새 migration이 자동 적용되지 않을 수 있습니다.
이 경우 app은 schema validation 단계에서 바로 죽습니다.

최근 실제로 수동 적용이 필요했던 파일:

```text
backend/src/main/resources/db/migration/V2026_05_15_02__add_user_alerts.sql
backend/src/main/resources/db/migration/V2026_05_15_03__add_web_push_subscriptions.sql
backend/src/main/resources/db/migration/V2026_05_16_01__add_notification_channel_flags.sql
backend/src/main/resources/db/migration/V2026_05_17_01__add_ai_status_to_user_recommendations.sql
backend/src/main/resources/db/migration/V2026_05_17_01__add_user_account_origin.sql
```

대표 증상:
- `missing table [user_alerts]`
- `missing column [user_profiles.notification_*]`
- `missing column [user_recommendations.ai_status]`
- `missing column [users.account_origin]`

주의:
- `2026-05-17` 서버 재기동에서도 실제로 `users.account_origin` 누락 때문에 첫 부팅이 실패했다.
- 따라서 기존 volume에서는 `V2026_05_17_01__add_ai_status_to_user_recommendations.sql` 만이 아니라 `V2026_05_17_01__add_user_account_origin.sql` 도 함께 봐야 한다.

수동 적용 후에는 다시:

```bash
docker compose up -d --build app
```

## 3. 최소 schema/grant 확인

대표 확인 쿼리:

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

`ai_status` 기대값:
- `NOT NULL`
- default = `NOT_REQUESTED`

## 4. admin smoke 자격증명 우선순위

현재 direct admin smoke와 `run-local-validation-from-env.sh` 는 같은 우선순위를 따릅니다.

1. 이미 export된 `ADMIN_EMAIL`, `ADMIN_PASSWORD`
2. `/tmp/youth-welfare-admin-smoke-email`, `/tmp/youth-welfare-admin-smoke-password`
3. `.env` 의 `SECURITY_ADMIN_EMAILS`
   - email만 보조로 사용
   - password는 따로 필요

중요:
- 서버에서는 local 기본값 `<local admin email>/<local admin password>` 를 기대하지 않습니다.
- 운영 기준으로는 **서버 allowlist admin 이메일 + 그 계정 비밀번호**를 명시해서 실행합니다.

예:

```bash
ADMIN_EMAIL='<server allowlist admin email>' \
ADMIN_PASSWORD='<that password>' \
VALIDATION_APP_BASE_URL=http://127.0.0.1:8082 \
bash deploy/smoke/run-local-validation-from-env.sh --full
```

직접 admin smoke도 같은 방식으로 맞춥니다.

```bash
ADMIN_EMAIL='<server allowlist admin email>' \
ADMIN_PASSWORD='<that password>' \
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

## 7. 현재 기준 빠른 재확인 묶음

기본 broader baseline:

```bash
ADMIN_EMAIL='<admin email>' \
ADMIN_PASSWORD='<admin password>' \
VALIDATION_APP_BASE_URL=http://127.0.0.1:8082 \
bash deploy/smoke/run-local-validation-from-env.sh --full

bash deploy/smoke/run-local-notification-channel-smoke.sh
bash deploy/smoke/run-local-deadline-reminder-smoke.sh
bash deploy/smoke/run-local-gov24-recommendation-suite.sh
```

현재 known-good 해석:
- `full validation` green
- `notification channel smoke` green
- `deadline reminder smoke` green
- `gov24 recommendation suite` green

## 8. 한 줄 요약

1. 기존 volume이면 migration 자동 적용을 기대하지 말고 missing table/column부터 본다.
2. server smoke는 `.env` 만 보지 말고 `ADMIN_EMAIL`/`ADMIN_PASSWORD` 주입 우선순위를 같이 본다.
3. deadline reminder는 host 날짜가 아니라 DB `CURRENT_DATE` 기준으로 읽는다.
