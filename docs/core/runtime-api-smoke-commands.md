# 런타임 API Smoke 명령 모음

문서군 진입점: [local-validation-docs-index.md](./local-validation-docs-index.md)

이 문서는 현재 로컬 런타임에서 바로 실행할 최소 API smoke 명령 모음입니다.
운영 cutover 전제는 없고, `docker compose` 로 띄운 app/db/redis 또는 수동 로컬 기동 상태에서 그대로 복사해 쓸 수 있게 정리했습니다.

반복 검증은 수동 curl 대신 아래 스크립트를 우선 사용합니다.

```bash
deploy/smoke/run-local-runtime-api-smoke.sh
```

이 스크립트는 `signup -> login -> refresh -> recommendations refresh -> bookmark -> bookmarks -> logout -> refresh invalidation -> presented access revoke` 를 한 번에 확인합니다.
앱 재기동 직후 startup race가 있으면 `HEALTH_RETRY_COUNT`, `HEALTH_RETRY_DELAY_SECONDS` 로 health check 재시도 횟수를 늘릴 수 있습니다.

추천 클릭 추적 반복 검증은 아래 스크립트를 우선 사용합니다.

```bash
deploy/smoke/run-local-recommendation-click-smoke.sh
```

이 스크립트는 `signup -> login -> recommendations refresh -> first recommendation detail(serviceId + logId) -> recommendation_logs.is_clicked=1` 을 한 번에 확인합니다.
앱 재기동 직후 startup race가 있으면 `HEALTH_RETRY_COUNT`, `HEALTH_RETRY_DELAY_SECONDS` 로 health check 재시도 횟수를 늘릴 수 있습니다.

admin forced logout 반복 검증은 아래 스크립트를 우선 사용합니다.

```bash
deploy/smoke/run-local-admin-forced-logout-smoke.sh
```

이 스크립트는 `admin login -> forced logout -> old access deny(401/A006) -> old refresh deny(401/A003) -> relogin recovery(200)` 를 한 번에 확인합니다.
앱 재기동 직후 startup race가 있으면 `HEALTH_RETRY_COUNT`, `HEALTH_RETRY_DELAY_SECONDS` 로 health check 재시도 횟수를 늘릴 수 있습니다.

withdraw 반복 검증은 아래 스크립트를 우선 사용합니다.

```bash
deploy/smoke/run-local-withdraw-smoke.sh
```

이 스크립트는 `signup -> login -> refresh -> withdraw -> old access deny(401/A006) -> stale refresh deny(410/U003) -> withdrawn email mask` 를 한 번에 확인합니다.
앱 재기동 직후 startup race가 있으면 `HEALTH_RETRY_COUNT`, `HEALTH_RETRY_DELAY_SECONDS` 로 health check 재시도 횟수를 늘릴 수 있습니다.

admin dashboard 반복 검증은 아래 스크립트를 우선 사용합니다.

```bash
deploy/smoke/run-local-admin-dashboard-smoke.sh
```

이 스크립트는 `admin login -> ROLE_ADMIN 확인 -> /api/admin/dashboard/summary -> summary window + trend window + recommendation weight progress 계약` 을 한 번에 확인합니다.
로컬 Docker app이 `SECURITY_ADMIN_EMAILS` 없이 떠 있으면 `admin@example.com` 이 `ROLE_ADMIN` 없이 로그인될 수 있으므로, 이 경우에는 아래처럼 다시 띄웁니다.

```bash
SECURITY_ADMIN_EMAILS=admin@example.com docker compose up -d --force-recreate app
```

기본 summary window는 `7`, 기본 trend window는 `1,7,30` 입니다. 다른 기간을 보고 싶으면 `SUMMARY_WINDOW_DAYS`, `TREND_WINDOW_DAYS_CSV` 로 덮어씁니다.

```bash
SUMMARY_WINDOW_DAYS=14 TREND_WINDOW_DAYS_CSV=3,14 deploy/smoke/run-local-admin-dashboard-smoke.sh
```

앱 재기동 직후 startup race가 있으면 아래 재시도 env를 같이 조절할 수 있습니다.

```bash
HEALTH_RETRY_COUNT=30 HEALTH_RETRY_DELAY_SECONDS=1 deploy/smoke/run-local-admin-dashboard-smoke.sh
```

공개 정책 탐색 + 프로필/우선순위 + 챗 CRUD 반복 검증은 아래 스크립트를 우선 사용합니다.

```bash
deploy/smoke/run-local-public-profile-chat-smoke.sh
```

이 스크립트는 `public policies list -> public search -> public detail -> signup -> login -> profile get -> priorities update -> chat create/list/send/get/delete` 를 한 번에 확인합니다.
`PUBLIC_SEARCH_KEYWORD`, `CHAT_MESSAGE_CONTENT` 로 검색어와 챗 질문을 바꿀 수 있습니다.

북마크 상태가 메인 추천/정책 검색/정책 상세/마이페이지 북마크 목록에서 같은 서비스 ID 기준으로 일관되게 보이는지 확인할 때는 아래 스크립트를 우선 사용합니다.

```bash
deploy/smoke/run-local-bookmark-consistency-smoke.sh
```

이 스크립트는 `signup -> login -> priorities update -> recommendations refresh -> first recommendation bookmark on/off -> recommendations/search/detail/bookmarks` 를 모두 재조회해 토글 전후 상태 일관성을 검증합니다.

auth/session revoke 세 개를 연속으로 돌릴 때는 아래 wrapper를 우선 사용합니다.

```bash
deploy/smoke/run-local-auth-session-smoke.sh
```

기본 순서:

1. runtime logout smoke
2. withdraw smoke
3. admin forced logout smoke

서버나 개인 로컬의 `.env` 값이 기본 smoke 값과 다를 때는 실제 값을 명령줄에 직접 반복해서 쓰지 말고,
아래 wrapper를 우선 사용합니다. 이 wrapper는 `.env`를 읽어 `DB_QUERY_PASSWORD`,
`DB_ROOT_PASSWORD`, `ADMIN_EMAIL` 등 검증용 override만 현재 shell에 주입하고 값은 출력하지 않습니다.
또한 `.env` 의 `APP_BASE_URL` 이 프론트 origin(`5173` 등)을 가리켜도, wrapper는 로컬 API smoke용 `APP_BASE_URL`
을 기본 `http://127.0.0.1:8082` 로 다시 고정합니다. 다른 API endpoint를 쓰려면 `VALIDATION_APP_BASE_URL` 로 덮어씁니다.
로컬 admin smoke 계정 파일(`/tmp/youth-welfare-admin-smoke-email`,
`/tmp/youth-welfare-admin-smoke-password`)이 있으면 해당 값을 우선 사용합니다.

```bash
deploy/smoke/run-local-validation-from-env.sh --quick
deploy/smoke/run-local-validation-from-env.sh --full --skip-replay
deploy/smoke/run-local-validation-from-env.sh --only dashboard
VALIDATION_APP_BASE_URL=http://127.0.0.1:8082 deploy/smoke/run-local-validation-from-env.sh --quick
```

로컬 기준선을 한 번에 다시 확인할 때도 기본 진입점은 위 `run-local-validation-from-env.sh` 입니다.
`.env`, local admin smoke 계정 파일, API base URL을 같이 정규화하므로 현재 로컬 환경에서 가장 덜 틀리게 재현됩니다.

`run-local-validation-suite.sh` 는
- env를 이미 명시적으로 정규화한 경우
- wrapper 없이 raw suite를 호출해야 하는 경우
의 보조 진입점으로 봅니다.

기본 순서:

1. auth/session smoke wrapper
2. public policy + profile/priorities + chat smoke
3. bookmark consistency smoke
4. recommendation click smoke
5. admin dashboard smoke
6. education priority replay smoke

주의:

- replay smoke는 DB/app 재기동이 섞일 수 있어 항상 마지막에 둡니다.
- replay smoke는 독립 `bootRun` 인스턴스를 기본 `18082` 포트로 띄우므로, 상위 wrapper가 `APP_BASE_URL=http://127.0.0.1:8082` 를 쓰더라도 replay 단계에서는 이를 넘기지 않습니다.
- replay를 다른 포트/URL로 강제하려면 `REPLAY_APP_BASE_URL` 을 명시합니다.
- local Docker app에서 admin 검증이 필요하면 `SECURITY_ADMIN_EMAILS=admin@example.com` 상태로 app이 떠 있어야 합니다.

빠른 재검증만 할 때는 `quick` 프로필을 사용합니다.

```bash
VALIDATION_PROFILE=quick deploy/smoke/run-local-validation-suite.sh
REPLAY_APP_BASE_URL=http://127.0.0.1:18082 deploy/smoke/run-local-validation-suite.sh --full
```

`quick` 은 `auth/session -> public policy/profile/chat -> bookmark consistency -> recommendation click -> admin dashboard` 까지만 돌고 replay는 건너뜁니다.
기본 `full` 프로필은 replay까지 포함합니다.
wrapper 끝에는 `suite_duration_seconds`, `step_duration_seconds=<label>|<seconds>` 형태의 요약이 같이 출력됩니다.
실패 시에는 `failed_step=<label>`, `elapsed_before_failure_seconds=<n>` 도 같이 출력됩니다.

실행 전에 현재 프로필/override 기준 어떤 단계가 켜질지만 보고 싶으면:

```bash
deploy/smoke/run-local-validation-suite.sh --print-plan
```

짧은 사용법은:

```bash
deploy/smoke/run-local-validation-suite.sh --help
```

env를 직접 쓰기 싫으면 CLI shortcut도 씁니다.

```bash
deploy/smoke/run-local-validation-suite.sh --quick --print-plan
deploy/smoke/run-local-validation-suite.sh --full --skip-replay
deploy/smoke/run-local-validation-suite.sh --only dashboard --print-plan
deploy/smoke/run-local-validation-suite.sh --only replay
deploy/smoke/run-local-validation-suite.sh --only replay --keep-artifacts
```

`--only` 를 쓰면 plan/failure 출력에도 `only_step=...` 가 같이 찍혀서 단일 단계 실행 의도가 바로 보입니다.

bounded runtime quality/audit baseline을 다시 확인할 때는 curl 수동 조합보다 아래 wrapper/runbook을 먼저 봅니다.

```bash
deploy/smoke/run-local-policy-quality-summary.sh
deploy/smoke/run-local-gov24-quality-audit.sh
deploy/smoke/run-local-ctr-readiness-audit.sh
deploy/smoke/run-local-notification-channel-smoke.sh
deploy/smoke/run-local-deadline-reminder-smoke.sh
```

- policy retrieval/category baseline: [policy-quality-summary-runbook.md](../policy/policy-quality-summary-runbook.md)
- Gov24 closeout/deferred inventory audit: [policy-gov24-runtime-audit-runbook.md](../policy/policy-gov24-runtime-audit-runbook.md)
- bounded admin runtime baseline: [policy-admin-runtime-runbook.md](../policy/policy-admin-runtime-runbook.md)
- recommendation CTR readiness baseline: [recommendation-ctr-readiness-runbook.md](../recommendation/recommendation-ctr-readiness-runbook.md)
- notification digest channel fan-out baseline: [notification-channel-expansion-checklist.md](./notification-channel-expansion-checklist.md)
- deadline reminder manual dispatch baseline: [notification-channel-expansion-checklist.md](./notification-channel-expansion-checklist.md)

알림 기능을 수동 API 기준으로 얇게 확인할 때는 아래 두 경로를 사용합니다.

```bash
POST /api/notifications/digest-test-dispatch
POST /api/notifications/deadline-test-dispatch?days=3
```

- `digest-test-dispatch`: 현재 로그인 사용자의 추천 digest fan-out 확인
- `deadline-test-dispatch`: 현재 로그인 사용자의 bookmarked 정책 중 마감 임박 후보 fan-out 확인

현재 단계에서 deadline reminder runtime은 별도 `WEEKLY` 경로를 열지 않았고, `NotificationScheduleService.sendDailyDeadlineReminders()` 의 `DAILY` entry만 active 범위입니다. 서버 기준선도 duplicate 기존 사용자는 `reservation conflict` 로 skip하고, summary line과 새 DAILY 대상 delta가 남는지 확인하는 방식으로만 닫았습니다.

북마크 마감 임박 알림을 수동 조합 대신 한 번에 확인할 때는 아래 wrapper를 우선 사용합니다.

```bash
deploy/smoke/run-local-deadline-reminder-smoke.sh
```

이 스크립트는 `signup -> login -> recommendations refresh -> first recommendation bookmark -> bookmarked service apply_end_date 강제 조정 -> deadline-test-dispatch -> notifications/user_alerts delta` 를 한 번에 검증합니다.

전제:

- 앱 base URL은 `APP_BASE_URL` 로 둡니다.
- 로그인 계정은 일반 사용자 1개, admin 확인이 필요하면 관리자 계정 1개를 따로 준비합니다.
- `python3` 는 로그인/refresh 응답에서 `accessToken` 을 뽑는 용도로 사용합니다.

## referenceUrlsJson rebuild 런북

`POST /api/admin/policies/reference-urls/rebuild` 는 기존 `raw_api_payloads` DETAIL snapshot을 다시 읽어
`welfare_service_details.reference_urls_json` 을 메우는 admin 경로입니다.
로컬 테스트 서비스 기준으로는 과거 적재 row를 재수집 없이 보강할 때 우선 사용합니다.

기본 동작:

- 기본 `missingOnly=true`
- 이미 `reference_urls_json` 이 있는 row는 건너뜀
- 대상 source: `YOUTH`, `BOKJIRO_CENTRAL`, `BOKJIRO_LOCAL`

### 0. 사전 확인

앱/DB/Redis 기동:

```bash
SECURITY_ADMIN_EMAILS=admin@example.com docker compose up -d db redis app
```

health 확인:

```bash
curl -sS http://127.0.0.1:8082/actuator/health
```

로컬 DB 현재 값 확인:

```bash
docker exec youth-welfare-db psql -U postgres -d youth_welfare -c "
SELECT COUNT(*) AS total_details,
       COUNT(reference_urls_json) AS filled_reference_urls
FROM welfare_service_details;
"
```

### 1. admin access token 준비

이미 준비된 admin 계정이 있으면 로그인합니다.
이 로컬 테스트 서비스는 실제 유저가 없으므로, email verification이나 admin allowlist 때문에 일반 signup이 막히면
로컬 smoke용 admin row를 직접 seed해도 됩니다. 직접 seed가 필요했던 사례와 주의점은
[troubleshooting-log.md](./troubleshooting-log.md) `585)` 를 봅니다.

```bash
export APP_BASE_URL="http://127.0.0.1:8082"
export ADMIN_EMAIL="admin@example.com"
export ADMIN_PASSWORD="password123!"
export ADMIN_LOGIN_RESPONSE="$(mktemp)"

curl -sS \
  -H 'Content-Type: application/json' \
  -X POST "$APP_BASE_URL/api/auth/login" \
  -d "{
    \"email\": \"$ADMIN_EMAIL\",
    \"password\": \"$ADMIN_PASSWORD\"
  }" | tee "$ADMIN_LOGIN_RESPONSE"
```

```bash
export ADMIN_ACCESS_TOKEN="$(python3 - "$ADMIN_LOGIN_RESPONSE" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    data = json.load(fp)

print(data["data"]["accessToken"])
PY
)"
```

현재 local smoke baseline에서는 admin runtime 검증용 비밀번호 예시를 `password123!` 로 둡니다.
다만 shell의 `ADMIN_PASSWORD` 나 `run-local-validation-from-env.sh` 가 읽는 `/tmp/youth-welfare-admin-smoke-password` 파일이 있으면 그 값을 우선합니다.

### 2. 기본 안전 실행

기본은 비어 있는 row만 채우는 실행입니다.

```bash
curl -sS \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -X POST "$APP_BASE_URL/api/admin/policies/reference-urls/rebuild"
```

응답 확인 포인트:

- `success=true`
- `missingOnly=true`
- `failedCount=0`
- `updatedCount > 0` 또는 이미 채워진 상태라면 `skippedCount > 0`

실행 예시:

```json
{
  "success": true,
  "data": {
    "scope": "all-detail-sources",
    "sourceTypes": ["YOUTH", "BOKJIRO_CENTRAL", "BOKJIRO_LOCAL"],
    "limitPerSource": 0,
    "missingOnly": true,
    "scannedCount": 1356,
    "skippedCount": 0,
    "updatedCount": 1356,
    "missingServiceCount": 0,
    "failedCount": 0
  }
}
```

### 3. 선택 실행

특정 source만 보고 싶으면:

```bash
curl -sS \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -X POST "$APP_BASE_URL/api/admin/policies/reference-urls/rebuild?sourceType=YOUTH&sourceType=BOKJIRO_LOCAL"
```

일부만 샘플 실행하고 싶으면:

```bash
curl -sS \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -X POST "$APP_BASE_URL/api/admin/policies/reference-urls/rebuild?limitPerSource=20"
```

이미 값이 있어도 전체 재적용을 강제로 보고 싶으면:

```bash
curl -sS \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -X POST "$APP_BASE_URL/api/admin/policies/reference-urls/rebuild?missingOnly=false"
```

### 4. 실행 후 확인

row count 재확인:

```bash
docker exec youth-welfare-db psql -U postgres -d youth_welfare -c "
SELECT COUNT(*) AS total_details,
       COUNT(reference_urls_json) AS filled_reference_urls
FROM welfare_service_details;
"
```

샘플 payload 확인:

```bash
docker exec youth-welfare-db psql -U postgres -d youth_welfare -c "
SELECT service_id,
       LEFT(reference_urls_json, 200) AS reference_urls_preview
FROM welfare_service_details
WHERE reference_urls_json IS NOT NULL
LIMIT 5;
"
```

확인 기준:

- `filled_reference_urls` 가 증가했는지 또는 이미 채워진 상태라면 유지되는지
- preview 안에 `APPLY`, `REFERENCE`, `DETAIL`, `EXTRACTED_FROM_TEXT` 같은 타입이 실제로 들어가는지
- 위 JSON 수치(`1356` 등)는 한 시점 local snapshot 예시일 뿐 pass/fail 고정값이 아닙니다. 현재 판단은 `failedCount=0`, `updatedCount 또는 skippedCount`, 그리고 실제 DB row 변화로 합니다.

### 5. 실패 시 triage

1. 앱이 부팅 직후 `reference_urls_json` missing column validation으로 죽었는지 확인
   기존 Docker volume은 `schema.sql` 변경을 자동 재적용하지 않습니다. 이 경우엔 volume reset 또는
   `ALTER TABLE` 이 필요합니다. 자세한 사례는 [troubleshooting-log.md](./troubleshooting-log.md) `584)` 를 봅니다.

2. app/db 컨테이너 recreate 후에도 mount 에러가 나는지 확인
   WSL/Docker Desktop stale bind mount 사례는 [troubleshooting-log.md](./troubleshooting-log.md) `583)` 를 봅니다.

3. `failedCount > 0` 이면 raw payload 존재 여부 확인
   `raw_api_payloads` 에 `payload_type='DETAIL'` row가 있는지와, 대상 `service_id` 의
   `welfare_service_details` row가 실제로 있는지 같이 봅니다.

```bash
docker exec youth-welfare-db psql -U postgres -d youth_welfare -c "
SELECT source_type, payload_type, COUNT(*)
FROM raw_api_payloads
WHERE payload_type = 'DETAIL'
GROUP BY source_type, payload_type
ORDER BY source_type;
"
```

```bash
docker exec youth-welfare-db psql -U postgres -d youth_welfare -c "
SELECT COUNT(*)
FROM welfare_service_details;
"
```

## 1. 공통 변수

```bash
export APP_BASE_URL="http://127.0.0.1:8082"
export SMOKE_EMAIL="user@example.com"
export SMOKE_PASSWORD="password123!"

export COOKIE_JAR="$(mktemp)"
export LOGIN_RESPONSE="$(mktemp)"
export REFRESH_RESPONSE="$(mktemp)"
export RECOMMEND_RESPONSE="$(mktemp)"
```

정리:

```bash
rm -f "$COOKIE_JAR" "$LOGIN_RESPONSE" "$REFRESH_RESPONSE" "$RECOMMEND_RESPONSE"
```

## 2. 로그인

```bash
curl -sS \
  -c "$COOKIE_JAR" \
  -H 'Content-Type: application/json' \
  -X POST "$APP_BASE_URL/api/auth/login" \
  -d "{
    \"email\": \"$SMOKE_EMAIL\",
    \"password\": \"$SMOKE_PASSWORD\"
  }" | tee "$LOGIN_RESPONSE"
```

access token 추출:

```bash
export ACCESS_TOKEN="$(python3 - "$LOGIN_RESPONSE" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    data = json.load(fp)

print(data["data"]["accessToken"])
PY
)"
```

확인 포인트:

- 응답 `success=true`
- `data.accessToken` 존재
- `refresh_token` cookie가 `COOKIE_JAR` 에 저장됨

## 3. refresh

cookie 기반 refresh:

```bash
curl -sS \
  -b "$COOKIE_JAR" \
  -c "$COOKIE_JAR" \
  -X POST "$APP_BASE_URL/api/auth/refresh" | tee "$REFRESH_RESPONSE"
```

새 access token 추출:

```bash
export ACCESS_TOKEN="$(python3 - "$REFRESH_RESPONSE" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    data = json.load(fp)

print(data["data"]["accessToken"])
PY
)"
```

확인 포인트:

- 응답 `success=true`
- `data.accessToken` 재발급
- `COOKIE_JAR` 의 refresh cookie 갱신
- 로그인 직후 곧바로 refresh하면 access token 문자열이 같을 수 있으므로, 성공 판단은 새 token으로 보호 API를 재호출해 보는 쪽으로 잡음

## 4. 추천 목록 조회

```bash
curl -sS \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$APP_BASE_URL/api/recommendations?size=5" | tee "$RECOMMEND_RESPONSE"
```

첫 추천 ID 확인:

```bash
export RECOMMENDATION_ID="$(python3 - "$RECOMMEND_RESPONSE" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    data = json.load(fp)

items = data["data"]
print(items[0]["id"] if items else "")
PY
)"
```

확인 포인트:

- 응답 `success=true`
- `data[]` 존재
- 각 항목의 `id`, `serviceId`, `isBookmarked` 확인 가능
- 로컬 DB가 base schema-only 상태면 `success=true`, `data=[]` 도 정상일 수 있음. 이 경우 smoke 실패로 보지 말고 snapshot 적재 여부를 먼저 분리함

## 5. 추천 북마크 토글

`/api/recommendations/{id}/bookmark` 는 추천 응답의 `serviceId` 가 아니라 `id` 를 path에 넣습니다.

```bash
curl -sS \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -X POST "$APP_BASE_URL/api/recommendations/$RECOMMENDATION_ID/bookmark"
```

재조회:

```bash
curl -sS \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$APP_BASE_URL/api/recommendations?size=5"
```

확인 포인트:

- 토글 응답 `success=true`
- 재조회 시 해당 recommendation의 `isBookmarked` 값 변경

## 5-1. 추천 클릭 추적 smoke

반복 검증은 수동 curl 대신 아래 스크립트를 우선 사용합니다.

```bash
deploy/smoke/run-local-recommendation-click-smoke.sh
```

이 smoke의 핵심 포인트:

- 추천 응답의 상세 진입 path는 `id` 가 아니라 `serviceId` 를 사용
- CTR 추적은 `logId` 를 query param(`?logId=`) 으로 전달
- 상세 조회 직후 `recommendation_logs.is_clicked=1`, `clicked_at` 이 채워져야 정상

수동 확인 시 주의:

- `recommendation.id` 는 추천 row id
- `recommendation.serviceId` 는 정책 상세 path variable
- 둘을 혼동하면 수동 probe에서 거짓 `500` 을 만들 수 있음

## 6. 정책 북마크 목록 확인

```bash
curl -sS \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$APP_BASE_URL/api/users/me/bookmarks"
```

확인 포인트:

- 응답 `success=true`
- 방금 토글한 정책이 목록에 반영되는지 확인

## 7. admin status 확인

이 호출은 admin JWT가 필요합니다. 현재 로그인 계정이 admin이 아니면 관리자 계정으로 다시 로그인해 `ACCESS_TOKEN` 을 새로 받습니다.

주의:

- 현재 계약상 `SECURITY_ADMIN_EMAILS` 에 들어 있는 이메일은 공개 회원가입으로 생성할 수 없습니다(`403 / A007`).
- 로컬 smoke에서는 기존 admin 계정을 쓰거나, 일반 사용자 생성 후 allowlist 승격 -> 재로그인 방식으로 admin token을 준비합니다.

```bash
curl -sS \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$APP_BASE_URL/api/admin/users/pii-sync-status?failedSampleLimit=5"
```

확인 포인트:

- 응답 `success=true`
- `failedCount`, `oldestPendingEnqueuedAt`, `failedSamples` 확인

## 8. 로그아웃

```bash
curl -sS \
  -b "$COOKIE_JAR" \
  -c "$COOKIE_JAR" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -X POST "$APP_BASE_URL/api/auth/logout"
```

확인 포인트:

- 응답 `success=true`
- refresh cookie clear
- 직후 `POST /api/auth/refresh` 는 보통 `401`, `errorCode=A001` 로 실패해야 함
- 같은 요청에 실린 `Authorization: Bearer <access-token>` 은 Redis revocation으로 즉시 무효화되므로, 같은 token으로 보호 API를 다시 호출하면 `401` 이 나와야 함
- 다만 logout 요청에 bearer token을 싣지 않은 cookie-only 경로는 refresh 회수만 보장하므로, “현재 access token 즉시 차단” 기대를 그 경로와 혼동하지 않음

## 9. 실패 시 먼저 볼 것

- 로그인 실패: 계정/비밀번호, `SECURITY_ADMIN_EMAILS`, 최근 비밀번호 변경 여부
- refresh 실패: cookie jar 경로, `refresh_token` cookie 저장 여부, `/api/auth` path cookie 사용 여부
- 추천 실패: `Authorization: Bearer <token>` 누락 여부
- 북마크 실패: `serviceId` 가 아니라 recommendation `id` 를 path에 넣었는지 확인
- admin status 실패: 현재 token이 admin 계정 기준인지 확인

## 10. forced logout smoke

이 단계는 admin JWT가 있을 때만 수행합니다.

강제 로그아웃 대상 `userKey` 는 admin `pii-sync-status`, DB 조회, 또는 이미 알고 있는 운영 user key를 사용합니다.

```bash
export FORCE_LOGOUT_USER_KEY="user-key-1"
export FORCE_LOGOUT_RESPONSE="$(mktemp)"

curl -sS \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -X POST "$APP_BASE_URL/api/admin/users/forced-logout" \
  -d "{
    \"userKey\": \"$FORCE_LOGOUT_USER_KEY\"
  }" | tee "$FORCE_LOGOUT_RESPONSE"
```

확인 포인트:

- 응답 `success=true`
- `data.userKey == $FORCE_LOGOUT_USER_KEY`
- `data.accepted=true`

## 11. forced logout log 확인

현재 phase에서는 `cutoffMillis` 를 response body에 싣지 않고, 서버 로그와 Redis에서 확인합니다.

기대 로그 라인 형식:

```text
[Admin] forced logout 트리거 userKey=<userKey> cutoffMillis=<epochMillis>
```

예:

```bash
grep -F "forced logout 트리거 userKey=$FORCE_LOGOUT_USER_KEY" /path/to/app.log | tail -n 1
```

확인 포인트:

- `userKey` 가 대상과 일치
- `cutoffMillis` 가 숫자로 남음

이 숫자는 old/new access token ordering triage용 증적이고, current API contract의 response 필드는 아닙니다.
