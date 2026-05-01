# 로컬 기능 시험 및 시간 측정 (2026-05-01)

## 목적

로컬 기준으로 현재 구현된 주요 기능을 기능별로 다시 실행하고:

- 정상 동작 여부
- 개별 소요 시간
- 광역 회귀 시간
- 병목 후보 / 실제 확인된 문제

를 한 문서에 남깁니다.

## 환경

- 날짜: 2026-05-01
- 기준 환경: local Docker `app + db + redis`, local Gradle test/integration, local smoke scripts
- OpenAI 관련 smoke는 `rule-only-invalid-key` 기준으로 측정

## 1. auth / session revoke

### 단위 / 슬라이스 테스트

- 명령

```bash
cd backend
./gradlew test --no-daemon \
  --tests com.example.welfare.user.controller.AuthControllerWebMvcTest \
  --tests com.example.welfare.user.service.AuthServiceTest \
  --tests com.example.welfare.user.service.UserServiceTest \
  --tests com.example.welfare.user.service.UserSessionRevocationServiceTest \
  --tests com.example.welfare.admin.AdminSecurityWebMvcTest
```

- 결과: `BUILD SUCCESSFUL`
- 시간: `15.68s`

### 통합 테스트

- 명령

```bash
cd backend
./gradlew integrationTest --no-daemon \
  --tests com.example.welfare.integration.AdminSecurityIntegrationTest \
  --tests com.example.welfare.integration.UserWithdrawAccessTokenBaselineIntegrationTest \
  --tests com.example.welfare.integration.UserWithdrawChatCleanupIntegrationTest \
  --tests com.example.welfare.integration.AuthRedisIntegrationTest \
  --tests com.example.welfare.integration.UserCoreDualWriteIntegrationTest \
  --tests com.example.welfare.integration.PolicyBookmarkIntegrationTest \
  --tests com.example.welfare.integration.ChatMessageApiIntegrationTest \
  --tests com.example.welfare.integration.ChatSessionApiIntegrationTest \
  --tests com.example.welfare.integration.RecommendationFlowIntegrationTest
```

- 결과: `BUILD SUCCESSFUL`
- 시간: `41.86s`

### 실제 API 시간

- `signup`: `0.088s`
- `login`: `0.087s`
- `refresh`: `0.016s`
- `logout`: `0.013s`
- `refresh after logout (expected 401)`: `0.004s`

## 2. PII split-account / sync queue

### one-shot smoke

- 명령

```bash
SMOKE_RESET_DB=true APP_HEALTH_TIMEOUT_SECONDS=180 \
deploy/smoke/run-local-pii-sync-cutover-smoke.sh
```

- 결과: `smoke success`
- 시간: `53.42s`

### 확인한 경계

- app build + container 기동
- queue migration 적용
- signup / login / profile update
- request-path sync
- queue `SYNCED`
- withdraw cleanup

## 3. 공개 정책 조회 API

collect snapshot 적재 후 anonymous/public 호출 기준:

- `GET /api/policies?size=5`: `0.042s`
- `GET /api/policies/search?keyword=청년&size=5`: `0.039s`
- `GET /api/policies/ranking?size=5`: `0.221s`
- `GET /api/policies/{id}`: `0.066s`

### 해석

- 목록/검색/상세는 현재 로컬에서 빠른 편이다.
- 랭킹은 다른 공개 조회보다 확실히 느리다.

## 4. 챗봇 API

### 실제 API 시간

- `POST /api/chat/sessions`: `0.020s`
- `GET /api/chat/sessions`: `0.015s`
- `POST /api/chat/sessions/{id}/messages`: `0.626s`
- `GET /api/chat/sessions/{id}/messages`: `0.016s`
- `DELETE /api/chat/sessions/{id}`: `0.022s`

### 해석

- 세션 CRUD는 매우 빠르다.
- 실제 병목은 답변 생성 구간(`send_message`)에 집중된다.

## 5. admin / collect / forced logout 실제 API

### admin API

- `admin login`: `0.087s`
- `GET /api/admin/users/pii-sync-status?failedSampleLimit=5`: `0.015s`
- `POST /api/admin/users/forced-logout`: `0.028s`
- forced logout 뒤 old access deny: `0.012s`
- forced logout 뒤 old refresh deny: `0.016s`
- relogin 뒤 access 회복: `0.014s`

### 실제 collect

첫 실행:

- `POST /api/admin/collect/youth`: `129.638s`
- 결과: `200 success`

확인 시점 snapshot:

- `welfare_services=2363`
- 이후 fresh-reset 상태에서는 sidecar draft schema가 자동 bootstrap 되지 않아, collect 성공 직후 `service_taxonomies`/`service_facts` 확인은 따로 복구가 필요했다.

즉시 재실행:

- `POST /api/admin/collect/youth`: `4.366s`
- 결과: `500`
- app log 기준 원인: 온통청년 upstream `403` (`page=9`)

### 해석

- 현재 가장 큰 실제 병목은 collect 이다.
- 같은 로컬 코드라도 upstream 상태에 따라 재실행 성공/실패가 갈릴 수 있다.

## 6. recommendation / education replay

### 1차 실행

- 명령

```bash
deploy/smoke/run-local-education-priority-replay.sh
```

- 결과: 실패
- 시간: `48.58s`

실패 원인:

1. fresh reset 뒤 canonical sidecar draft schema가 자동으로 올라오지 않았다.
2. 수동 단순 backfill로는 `education target row` 가 충분히 복구되지 않았다.

### 수정

- `backend/src/main/resources/db/migration-draft/V2026_04_30_02__seed_policy_normalization_codes.sql`
  의 `CTE + INSERT` 문법 순서를 MySQL 8.0 기준으로 수정했다.
- 수정 후 draft SQL을 다시 적용해:
  - `service_taxonomies=2363`
  - `education_target_rows=110`
  상태를 복구했다.

### 2차 실행

- 결과: 성공
- 시간: `48.50s`

요약:

- `A_top10_target=4 -> 8`
- `B_top10_target=2 -> 2`
- `A_target_total=10 -> 10`
- `B_target_total=10 -> 10`

### 해석

- replay 자체는 현재 로컬에서 다시 정상이다.
- 다만 fresh reset 후에는 canonical draft schema/backfill이 선행되지 않으면 바로 재현되지 않는다.

## 7. 광역 회귀

### 전체 backend 회귀

- 명령

```bash
cd backend
./gradlew test integrationTest --no-daemon
```

- 결과: `BUILD SUCCESSFUL`
- 시간: `58.69s`

## 8. 확인된 문제점

### 문제 1. draft sidecar seed SQL 문법 오류

- 파일:
  [V2026_04_30_02__seed_policy_normalization_codes.sql](../backend/src/main/resources/db/migration-draft/V2026_04_30_02__seed_policy_normalization_codes.sql)
- 증상:
  MySQL 8.0.45 에서 `WITH ... INSERT INTO ...` 문법 오류 발생
- 조치:
  `INSERT INTO ... WITH ... SELECT ...` 순서로 수정
- 상태:
  이번 작업에서 수정 완료

### 문제 2. fresh local reset 뒤 canonical sidecar draft schema는 자동 bootstrap 되지 않음

- 증상:
  `collect` 는 성공해도 `service_taxonomies` / `service_facts` 가 바로 보장되지 않음
- 영향:
  education replay 같은 canonical downstream 검증은 곧바로 재현되지 않음
- 상태:
  현재도 local verification caveat 로 남아 있음

### 문제 3. 온통청년 실제 collect 재실행은 upstream 403 영향을 받음

- 증상:
  성공 직후 재실행에서 `page=9` upstream `403`
- 영향:
  로컬 코드 회귀와 별개로 collect 안정성이 외부 상태에 흔들림
- 상태:
  external / runtime variability 로 분류

## 9. 병목 후보와 실제 확인

### 확인된 병목

1. `POST /api/admin/collect/youth`
   - 실제 `129.638s`
   - 가장 큰 병목
   - 외부 API 페이지 순회 + 저장 + 로그 기록이 한 요청에 모두 들어간다

2. 챗봇 답변 생성
   - `POST /api/chat/sessions/{id}/messages`: `0.626s`
   - CRUD와 비교하면 확실히 느리다
   - 현재는 fallback/rule-only 기준이며, real OpenAI 모드면 더 느려질 가능성이 높다

3. replay smoke
   - `48.50s`
   - app off/on, signup/login, refresh, 추천 비교가 모두 포함된 end-to-end 검증 시간이므로 개별 API보다 길다

### 예상 병목

1. real OpenAI replay
   - 지금 측정은 `rule-only-invalid-key`
   - live OpenAI 호출을 넣으면 `chat send`, replay 비교 시간이 더 늘어날 가능성이 높다

2. collect + detail/gap-fill 확장
   - 현재도 youth collect만 2분대에 가깝다
   - detail refresh, gap-fill, canonical fact density를 더 붙이면 더 느려질 수 있다

3. ranking query
   - 목록/검색/상세보다 이미 느리다
   - 데이터가 더 늘면 공개 조회 중 먼저 튈 가능성이 있다

## 10. 현재 결론

로컬 기준으로:

- auth/session revoke: 정상
- PII split-account: 정상
- 공개 정책 조회: 정상
- 챗봇: 정상
- forced logout: 정상
- collect: 1차 성공, 재실행은 upstream `403` 변동성 존재
- education replay: SQL 수정 후 다시 정상
- broad regression: 정상

즉 현재 남은 핵심 리스크는 내부 광역 회귀보다는:

1. collect 의 외부 API 의존성
2. fresh local reset 뒤 canonical draft schema/bootstrap 공백
3. real OpenAI 모드의 추가 latency

쪽이다.
