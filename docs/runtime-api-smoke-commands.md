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

auth/session revoke 세 개를 연속으로 돌릴 때는 아래 wrapper를 우선 사용합니다.

```bash
deploy/smoke/run-local-auth-session-smoke.sh
```

기본 순서:

1. runtime logout smoke
2. withdraw smoke
3. admin forced logout smoke

로컬 기준선을 한 번에 다시 확인할 때는 아래 상위 wrapper를 우선 사용합니다.

```bash
deploy/smoke/run-local-validation-suite.sh
```

기본 순서:

1. auth/session smoke wrapper
2. recommendation click smoke
3. admin dashboard smoke
4. education priority replay smoke

주의:

- replay smoke는 DB/app 재기동이 섞일 수 있어 항상 마지막에 둡니다.
- local Docker app에서 admin 검증이 필요하면 `SECURITY_ADMIN_EMAILS=admin@example.com` 상태로 app이 떠 있어야 합니다.

빠른 재검증만 할 때는 `quick` 프로필을 사용합니다.

```bash
VALIDATION_PROFILE=quick deploy/smoke/run-local-validation-suite.sh
```

`quick` 은 `auth/session -> recommendation click -> admin dashboard` 까지만 돌고 replay는 건너뜁니다.
기본 `full` 프로필은 replay까지 포함합니다.

전제:

- 앱 base URL은 `APP_BASE_URL` 로 둡니다.
- 로그인 계정은 일반 사용자 1개, admin 확인이 필요하면 관리자 계정 1개를 따로 준비합니다.
- `python3` 는 로그인/refresh 응답에서 `accessToken` 을 뽑는 용도로 사용합니다.

## 1. 공통 변수

```bash
export APP_BASE_URL="http://127.0.0.1:8082"
export SMOKE_EMAIL="user@example.com"
export SMOKE_PASSWORD="Password123!"

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
