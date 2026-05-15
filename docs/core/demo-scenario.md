# 데모 시나리오

로컬 실행/검증 문서군 진입점은 [local-validation-docs-index.md](./local-validation-docs-index.md)를 먼저 봅니다.

졸업 발표나 기능 검수 때 바로 따라갈 수 있는 백엔드 데모 순서다.
기준 환경은 현재 `Docker Compose + PostgreSQL + Redis` 기준 로컬 앱이다.
Nginx/HTTPS 같은 운영 reverse proxy 전제는 이 데모 문서의 범위가 아니다.
발표용 단일 서버는 현재 실측 기준 `t3.medium` 이상을 권장한다.

## 1. 사전 확인

- 앱 기동: `http://127.0.0.1:8082/actuator/health`
- 정책 데이터 존재 확인:

```sql
SELECT COUNT(*) AS service_count FROM welfare_services;
```

- 추천/CTR 분석용 로그 테이블 확인:

```sql
SELECT COUNT(*) AS recommendation_log_count FROM recommendation_logs;
```

## 2. 회원가입

```bash
curl -i -X POST http://127.0.0.1:8082/api/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "demo.user@example.com",
    "password": "demoPass123!",
    "name": "데모사용자",
    "birthDate": "2000-05-10",
    "sido": "서울특별시",
    "sgg": "관악구",
    "incomeLevel": 4,
    "employmentStatus": "재학중",
    "householdType": "1인가구"
  }'
```

## 3. 로그인

쿠키 파일을 유지해야 refresh/logout 데모까지 이어진다.

```bash
curl -i -c /tmp/yw-cookie.txt -X POST http://127.0.0.1:8082/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "demo.user@example.com",
    "password": "demoPass123!"
  }'
```

- 응답 본문에서 `accessToken` 추출
- 이후 예시는 `ACCESS_TOKEN` 셸 변수에 넣고 진행

```bash
ACCESS_TOKEN='여기에 access token'
```

## 4. 프로필 / 알림 설정

```bash
curl -i -X PUT http://127.0.0.1:8082/api/users/me \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{
    "phone": "01012345678",
    "sido": "서울특별시",
    "sgg": "관악구",
    "incomeLevel": 4,
    "employmentStatus": "재학중",
    "householdType": "1인가구",
    "notificationYn": true,
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
curl -i -X PUT http://127.0.0.1:8082/api/users/me/priorities \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{
    "priorityCodes": ["HOUSING", "EDUCATION", "JOB"]
  }'
```

## 6. 정책 목록 / 검색 / 상세

목록:

```bash
curl -s "http://127.0.0.1:8082/api/policies?category=주거&sido=서울특별시&sgg=관악구&sort=VIEWS&page=0&size=5" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

검색:

```bash
curl -s "http://127.0.0.1:8082/api/policies/search?keyword=청년&status=ACTIVE&sort=NAME&page=0&size=5" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

랭킹:

```bash
curl -s "http://127.0.0.1:8082/api/policies/ranking?size=5" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

상세:

```bash
curl -s "http://127.0.0.1:8082/api/policies/1" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

## 7. 추천 생성 / 조회

추천 새로 계산:

```bash
curl -s -X POST http://127.0.0.1:8082/api/recommendations/refresh \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

저장된 추천 조회:

```bash
curl -s "http://127.0.0.1:8082/api/recommendations?size=10" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

확인 포인트:

- `finalScore`
- `aiScore`
- `aiReason`
- `isBookmarked`
- `recommendedAt`

## 8. 북마크 토글

정책 기준 북마크:

```bash
curl -i -X POST http://127.0.0.1:8082/api/policies/1/bookmark \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

추천 기준 북마크:

```bash
curl -i -X POST http://127.0.0.1:8082/api/recommendations/1/bookmark \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

## 9. 알림 수신 거부

로그인 상태:

```bash
curl -i -X POST http://127.0.0.1:8082/api/users/me/notifications/unsubscribe \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

비로그인 상태 링크형:

```text
GET /api/notifications/unsubscribe?token=...
```

## 10. Refresh / Logout

refresh cookie 기반 access token 재발급 (로그아웃 전에 실행):

```bash
curl -i -b /tmp/yw-cookie.txt -c /tmp/yw-cookie.txt \
  -X POST http://127.0.0.1:8082/api/auth/refresh
```

로그아웃 (refresh token 무효화):

```bash
curl -i -b /tmp/yw-cookie.txt \
  -X POST http://127.0.0.1:8082/api/auth/logout
```

> 주의: 로그아웃 후에는 refresh token이 무효화되어 `/api/auth/refresh`가 `A003`을 반환합니다. Refresh 먼저, Logout 나중에 실행하세요.

## 11. CTR / Cold Start 확인 쿼리

AI vs fallback CTR:

```sql
SELECT is_fallback,
       COUNT(*) AS total_sent,
       SUM(is_clicked) AS clicked,
       ROUND(SUM(is_clicked) * 100.0 / COUNT(*), 1) AS ctr_pct
FROM recommendation_logs
GROUP BY is_fallback;
```

가중치 단계별 CTR:

```sql
SELECT rule_weight_used,
       ai_weight_used,
       COUNT(*) AS total_sent,
       ROUND(SUM(is_clicked) * 100.0 / COUNT(*), 1) AS ctr_pct
FROM recommendation_logs
GROUP BY rule_weight_used, ai_weight_used;
```

현재 Cold Start 단계 추정:

```sql
SELECT COUNT(*) AS total_logs FROM recommendation_logs;
```

- `0~99`: COLD_START
- `100~499`: GROWTH
- `500+`: STABLE

## 12. 발표 멘트 포인트

- 로그인 후 추천은 실시간 AI를 매번 다시 호출하지 않고 저장된 추천을 반환한다.
- 추천 점수는 `rule + ai` 혼합이며, 로그가 쌓일수록 AI 비중이 커진다.
- 추천 발송/클릭은 `recommendation_logs`에 남아서 CTR 분석이 가능하다.
- 알림은 Gmail 기반 1차 운영, 카카오 알림톡은 2차 확장 항목이다.
