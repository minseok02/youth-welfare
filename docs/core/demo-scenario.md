# 데모 시나리오

로컬 실행/검증 문서군 진입점은 [local-validation-docs-index.md](./local-validation-docs-index.md)를 먼저 봅니다.

졸업 발표나 기능 검수 때 바로 따라갈 수 있는 백엔드 데모 순서다.
기준 환경은 현재 `Docker Compose + PostgreSQL + Redis` 기준 로컬 앱이다.
Nginx/HTTPS 같은 운영 reverse proxy 전제는 이 데모 문서의 범위가 아니다.
서버 크기는 [성능 측정 기준](../performance/performance-measurement-plan.md)에 따라 별도로 판단한다.

> 격리된 개발 앱·DB·Redis의 테스트 계정으로만 진행합니다. `127.0.0.1`도 운영 앱이나 RDS를 가리킬 수 있으므로 [환경 안내](environment-guide.md)에서 대상을 먼저 확인합니다. 가입·프로필·추천 refresh·북마크·수신거부·로그아웃은 상태 변경이며 추천은 실제 AI 비용을 발생시킬 수 있습니다. 단순 사전 조회에 `--quick`/전체 validation을 자동 포함하지 않습니다.

## 1. 사전 확인

같은 Bash 세션에서 실제 앱과 허용된 프론트 origin을 명시합니다. HTTP 오류가 나면 다음 단계로 진행하지 않습니다.

```bash
: "${DEMO_APP_BASE_URL:?격리된 테스트 앱 URL을 명시하세요}"
: "${DEMO_TRUSTED_ORIGIN:?앱의 허용된 프론트 origin을 명시하세요}"
curl -fsS "${DEMO_APP_BASE_URL}/actuator/health"
```

요청 본문 파일은 Git 밖의 승인된 경로에 권한 `600`으로 준비합니다. 실제 password/token/cookie/Authorization 값은 명령 문자열·문서·발표 화면·로그에 복사하지 않습니다. 로그인 응답과 cookie jar도 아래 임시 파일로만 받습니다.
- 정책 데이터 존재 확인:

```sql
SELECT COUNT(*) AS service_count FROM welfare_services;
```

- 추천/CTR 분석용 로그 테이블 확인:

```sql
SELECT COUNT(*) AS recommendation_log_count FROM recommendation_logs;
```

## 2. 회원가입

먼저 프론트 또는 [인증 API 계약](api-mapping.md#인증회원가입-계약)의 이메일 인증을 완료합니다. 실제 인증 메일 발송은 승인된 테스트 수신자에게만 수행합니다. 사전 준비된 fixture 계정으로 시작하면 이 단계는 생략하며 운영 Redis에 인증 완료 값을 직접 쓰지 않습니다.

`DEMO_SIGNUP_BODY_FILE`에 넣을 JSON 구조 예시입니다. 이메일·비밀번호는 인증 완료한 테스트 계정 값으로 준비합니다.

```json
{
    "email": "<인증 완료한 테스트 이메일>",
    "password": "<테스트 비밀번호>",
    "name": "데모사용자",
    "birthDate": "2000-05-10",
    "privacyNoticeConfirmed": true,
    "optionalProfileConsentAgreed": true,
    "sido": "서울특별시",
    "sgg": "관악구",
    "incomeLevel": 4,
    "employmentStatus": "재학중",
    "householdType": "1인가구"
}
```

```bash
curl -fsS -X POST "${DEMO_APP_BASE_URL}/api/auth/signup" \
  -H 'Content-Type: application/json' \
  --data-binary "@${DEMO_SIGNUP_BODY_FILE:?보호된 signup JSON 파일이 필요합니다}"
```

## 3. 로그인

`DEMO_LOGIN_BODY_FILE`은 `email/password`를 가진 보호된 JSON 파일입니다. cookie jar를 유지해야 refresh/logout까지 이어지며 실제 cookie의 secure/HTTPS 설정도 테스트 환경과 맞아야 합니다. 아래 `mktemp` 파일은 기본 권한 `600`이며 시연 후 둘 다 삭제합니다.

```bash
DEMO_COOKIE_JAR="$(mktemp /tmp/yw-demo-cookie.XXXXXX)"
DEMO_AUTH_RESPONSE="$(mktemp /tmp/yw-demo-auth.XXXXXX)"
curl -fsS -c "${DEMO_COOKIE_JAR}" -o "${DEMO_AUTH_RESPONSE}" \
  -X POST "${DEMO_APP_BASE_URL}/api/auth/login" \
  -H 'Content-Type: application/json' \
  --data-binary "@${DEMO_LOGIN_BODY_FILE:?보호된 login JSON 파일이 필요합니다}"

demo_read_access_token() {
  python3 -c 'import json, sys; print(json.load(sys.stdin)["data"]["accessToken"])' < "${DEMO_AUTH_RESPONSE}"
}
ACCESS_TOKEN="$(demo_read_access_token)"
```

토큰 변수나 로그인 응답을 `echo`/`cat`으로 출력하지 않습니다. 로그인 실패 시 응답을 공개 artifact에 복사하지 말고 비밀값을 제외한 HTTP/error code만 확인합니다.

## 4. 프로필 / 알림 설정

예시는 외부 알림 발송을 끈 상태로 프로필을 변경합니다. 실제 메일·웹푸시 시연은 별도 승인한 채널/수신자에서만 켭니다.

```bash
curl -fsS -X PUT "${DEMO_APP_BASE_URL}/api/users/me" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{
    "sido": "서울특별시",
    "sgg": "관악구",
    "incomeLevel": 4,
    "employmentStatus": "재학중",
    "householdType": "1인가구",
    "notificationYn": false,
    "notificationPeriod": "DAILY",
    "notificationMinScore": 0.6,
    "displayCount": 10,
    "interestFields": ["주거", "교육·직업훈련"]
  }'
```

## 5. 우선순위 설정

사용 가능한 코드:

- `HOUSING` — 주거
- `JOB` — 일자리
- `EDUCATION` — 교육·직업훈련
- `FINANCE` — 금융·생활
- `CULTURE` — 문화·여가
- `PARTICIPATION` — 참여·기회
- `FAMILY` — 가족·돌봄
- `DEADLINE` — 마감임박

```bash
curl -fsS -X PUT "${DEMO_APP_BASE_URL}/api/users/me/priorities" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{
    "priorityCodes": ["HOUSING", "EDUCATION", "JOB"]
  }'
```

## 6. 정책 목록 / 검색 / 상세

목록:

```bash
curl -fsS --get "${DEMO_APP_BASE_URL}/api/policies" \
  --data-urlencode 'category=주거' --data-urlencode 'sido=서울특별시' \
  --data-urlencode 'sgg=관악구' --data-urlencode 'sort=VIEWS' \
  --data-urlencode 'page=0' --data-urlencode 'size=5' \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

검색:

```bash
curl -fsS -X POST "${DEMO_APP_BASE_URL}/api/policies/search" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"keyword":"청년","status":"ACTIVE","sort":"RELEVANCE","page":0,"size":5}'
```

랭킹:

```bash
curl -fsS "${DEMO_APP_BASE_URL}/api/policies/ranking?size=5" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

상세:

```bash
curl -fsS "${DEMO_APP_BASE_URL}/api/policies/<앞 단계에서 확인한 serviceId>" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

> 주의: `1` 같은 고정 ID를 기준으로 성공/실패를 판단하지 않습니다.
> 목록/검색/추천 응답에서 실제로 받은 `serviceId` 를 이어서 씁니다.

## 7. 추천 생성 / 조회

추천 새로 계산:

```bash
curl -fsS -X POST "${DEMO_APP_BASE_URL}/api/recommendations/refresh" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

저장된 추천 조회:

```bash
curl -fsS "${DEMO_APP_BASE_URL}/api/recommendations?size=10" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

확인 포인트:

- `finalScore`
- `aiScore`
- `aiReason`
- `bookmarked` (JSON 응답)
- `recommendedAt`

## 8. 북마크 토글

정책 기준 북마크:

```bash
curl -fsS -X POST "${DEMO_APP_BASE_URL}/api/policies/<실제 serviceId>/bookmark" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

추천 기준 북마크:

```bash
curl -fsS -X POST "${DEMO_APP_BASE_URL}/api/recommendations/<실제 recommendationId>/bookmark" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

## 9. 알림 수신 거부

로그인 상태:

```bash
curl -fsS -X POST "${DEMO_APP_BASE_URL}/api/users/me/notifications/unsubscribe" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

비로그인 링크형은 위 로그인 상태 수신거부와 별도 선택 시나리오입니다.

```text
프론트: /notifications/unsubscribe#token=...
API: POST /api/notifications/unsubscribe
JSON body: {"token":"<승인된 테스트용 opaque token>"}
```

프론트는 hash 토큰을 읽은 뒤 URL에서 제거하고 POST JSON으로 보냅니다. API query에 token을 붙이거나 과거 GET API를 사용하지 않습니다. 수동 검증은 보호된 본문 파일과 허용된 origin이 필요합니다.

```bash
curl -fsS -X POST "${DEMO_APP_BASE_URL}/api/notifications/unsubscribe" \
  -H "Origin: ${DEMO_TRUSTED_ORIGIN}" \
  -H 'Content-Type: application/json' \
  --data-binary "@${DEMO_UNSUBSCRIBE_BODY_FILE:?보호된 token JSON 파일이 필요합니다}"
```

## 10. Refresh / Logout

refresh cookie 기반 access token 재발급 (로그아웃 전에 실행):

```bash
curl -fsS -b "${DEMO_COOKIE_JAR}" -c "${DEMO_COOKIE_JAR}" \
  -o "${DEMO_AUTH_RESPONSE}" -X POST "${DEMO_APP_BASE_URL}/api/auth/refresh" \
  -H "Origin: ${DEMO_TRUSTED_ORIGIN}"
ACCESS_TOKEN="$(demo_read_access_token)"
```

로그아웃 (식별 가능한 사용자 세션의 refresh 회수·access cutoff와 제시한 bearer 회수):

```bash
curl -fsS -b "${DEMO_COOKIE_JAR}" -c "${DEMO_COOKIE_JAR}" \
  -X POST "${DEMO_APP_BASE_URL}/api/auth/logout" \
  -H "Origin: ${DEMO_TRUSTED_ORIGIN}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

> Refresh 먼저, Logout 나중에 실행합니다. cookie jar에서 토큰이 삭제된 뒤의 missing-cookie 응답과 회수한 refresh 원문 재사용 응답은 다를 수 있습니다. [인증 계약](../auth/auth-session-revocation-current-state.md#1-logout)에 따라 presented/older access 거부도 별도로 확인합니다. 시연 후 생성한 `DEMO_COOKIE_JAR`, `DEMO_AUTH_RESPONSE` 파일을 삭제하고 `ACCESS_TOKEN` 변수를 해제합니다.

## 11. CTR / Cold Start 확인 쿼리

아래 SQL은 PostgreSQL에서 **남아 있는 로그의 클릭 표시 비율**을 조회합니다. 미클릭 로그 삭제와 생성/알림 준비 로그 혼합 때문에 실제 발송 수·노출 CTR·가중치 개선 효과를 뜻하지 않습니다. [로그 해석](../recommendation/recommendation-pipeline.md#저장된-로그의-진단-쿼리)을 먼저 확인합니다.

AI/fallback별 잔존 로그:

```sql
SELECT is_fallback,
       COUNT(*) AS log_rows,
       COUNT(*) FILTER (WHERE is_clicked) AS clicked_rows,
       ROUND(100.0 * COUNT(*) FILTER (WHERE is_clicked)
             / NULLIF(COUNT(*), 0), 1) AS recorded_click_pct
FROM recommendation_logs
GROUP BY is_fallback;
```

가중치별 잔존 로그:

```sql
SELECT rule_weight_used,
       ai_weight_used,
       COUNT(*) AS log_rows,
       ROUND(100.0 * COUNT(*) FILTER (WHERE is_clicked)
             / NULLIF(COUNT(*), 0), 1) AS recorded_click_pct
FROM recommendation_logs
GROUP BY rule_weight_used, ai_weight_used;
```

전체 잔존 로그 수와 저장된 가중치 설정 대조:

```sql
SELECT COUNT(*) AS total_logs FROM recommendation_logs;
SELECT weight_key, min_log_count, rule_weight, ai_weight, is_active
FROM score_weights
ORDER BY min_log_count;
```

기존 기본 seed의 구간은 `0~99=COLD_START`, `100~499=GROWTH`, `500+=STABLE`입니다. 실제 선택은 `score_weights`의 활성 행과 `min_log_count`에 따라 달라지므로 고정 숫자만으로 현재 단계를 확정하지 않습니다.

> 주의: 위 log count stage는 recommendation pipeline의 weight bucket 해석용 힌트일 뿐입니다.
> 실제 weight tuning 가능 여부는 클릭 표본 수, clicked service 분산, fallback/AI 분포를 함께 보는
> [recommendation-ctr-readiness-runbook.md](../recommendation/recommendation-ctr-readiness-runbook.md) 기준을 우선합니다.

## 12. 발표 멘트 포인트

- 로그인 후 추천은 실시간 AI를 매번 다시 호출하지 않고 저장된 추천을 반환한다.
- 추천은 규칙/AI 점수를 조합하고 저장된 가중치 구간을 사용한다. 로그 수 증가 자체가 모델 학습·추천 품질 향상을 뜻하지 않는다.
- `recommendation_logs`의 잔존 클릭 표시는 실제 노출·발송 성공 CTR과 구분한다.
- 인앱·이메일·웹푸시 경로가 구현돼 있다. 현재 SMTP provider와 실제 발송 가능 여부는 runtime 설정으로 확인하며, 카카오 알림톡은 별도 연동 후보이다.
