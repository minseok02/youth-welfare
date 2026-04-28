# 런타임 API Smoke 명령 모음

이 문서는 운영 cutover 직후 실행할 최소 API smoke 명령 모음입니다.
[runtime-cutover-checklist.md](./runtime-cutover-checklist.md)의 `핵심 smoke` 단계에서 그대로 복사해 사용할 수 있게 정리했습니다.

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

## 9. 실패 시 먼저 볼 것

- 로그인 실패: 계정/비밀번호, `SECURITY_ADMIN_EMAILS`, 최근 비밀번호 변경 여부
- refresh 실패: cookie jar 경로, `refresh_token` cookie 저장 여부, `/api/auth` path cookie 사용 여부
- 추천 실패: `Authorization: Bearer <token>` 누락 여부
- 북마크 실패: `serviceId` 가 아니라 recommendation `id` 를 path에 넣었는지 확인
- admin status 실패: 현재 token이 admin 계정 기준인지 확인
