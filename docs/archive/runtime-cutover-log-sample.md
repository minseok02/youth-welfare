# Runtime Cutover Log Sample - 2026-04-28 22:10 KST

이 문서는 [runtime-cutover-log-template.md](../runtime-cutover-log-template.md)를 실제로 어떻게 채우는지 보여주기 위한 예시입니다.
모든 값은 예시용이며, host / email / token / password / 시각은 redacted 또는 fictitious 값입니다.

## 기본 정보
- 작업자: `ops-admin`
- 시작 시각: `2026-04-28 21:42 KST`
- 종료 시각: `2026-04-28 22:10 KST`
- 서버/환경: `prod-app-1`, `prod-db-1`
- 관련 브랜치 / PR: `docs/deploy-runtime-api-smoke-commands`, `#24`
- 관련 체크리스트: `docs/runtime-cutover-checklist.md`
- 관련 API smoke 명령: `docs/runtime-api-smoke-commands.md`

## 1. 사전 준비
- backup 또는 snapshot: `prod-db snapshot created at 2026-04-28 21:35 KST`
- `/tmp/runtime-db-accounts.sql` 준비 여부: `done`
- secret store / `.env` 수정 권한 확인: `done`

## 2. 계정 SQL 적용
- 실행 명령:
  - `mysql -h db-prod.internal -P 3306 -u root -p < /tmp/runtime-db-accounts.sql`
- 결과: `success`
- 비고: `existing app_core_rw / migration_admin password rotated`

## 3. 권한 검증
- `SHOW GRANTS FOR 'app_core_rw'@'%';`
  - 결과: `youth_welfare.* DML only`
- `SHOW GRANTS FOR 'app_pii_rw'@'%';`
  - 결과: `youth_welfare_pii.user_pii DML`
- `SHOW GRANTS FOR 'notification_pii_ro'@'%';`
  - 결과: `user_pii(user_key, email_enc) SELECT`
- `SHOW GRANTS FOR 'migration_admin'@'%';`
  - 결과: `youth_welfare.*`, `youth_welfare_pii.* ALL`
- `app_core_rw` 의 `youth_welfare_pii.user_pii` 조회 실패 확인: `confirmed`
- `notification_pii_ro` 의 `phone_enc` 조회 실패 확인: `confirmed`

## 4. Migration 적용
- `V2026_04_28_01__drop_runtime_legacy_user_id.sql`
  - 실행 시각: `2026-04-28 21:49 KST`
  - 결과: `success`
- `V2026_04_28_02__add_user_pii_sync_queue.sql`
  - 실행 시각: `2026-04-28 21:51 KST`
  - 결과: `success`

## 5. Env / Secret 갱신
- `DB_USERNAME=app_core_rw` 반영: `done`
- `APP_PII_DB_URL -> youth_welfare_pii` 반영: `done`
- `NOTIFICATION_PII_DB_URL -> youth_welfare_pii` 반영: `done`
- `DB_MIGRATION_*` 반영: `done`
- `DB_APP_PII_*` 반영: `done`
- `DB_NOTIFICATION_PII_RO_*` 반영: `done`

## 6. Preflight Summary
- 실행 명령:
  - `ENV_FILE=.env PRINT_SUMMARY=true deploy/smoke/preflight-runtime-cutover-env.sh`
- 결과:
  - `DB_URL target`: `db-prod.internal:3306 / youth_welfare`
  - `APP_PII_DB_URL target`: `db-prod.internal:3306 / youth_welfare_pii`
  - `NOTIFICATION_PII_DB_URL target`: `db-prod.internal:3306 / youth_welfare_pii`
  - `DB_USERNAME`: `app_core_rw`
  - `DB_MIGRATION_USERNAME`: `migration_admin`
  - `DB_APP_PII_USERNAME`: `app_pii_rw`
  - `DB_NOTIFICATION_PII_RO_USERNAME`: `notification_pii_ro`
  - password 상태: `all set`
- pass/fail: `pass`

## 7. App 재기동
- 실행 명령:
  - `docker compose -f docker-compose.yml up -d --build app`
- 결과: `success`
- 비고: `startup validation passed`

## 8. Health Check
- 실행 명령:
  - `curl -fsS https://youth-welfare.kr/actuator/health`
- 결과:
  - `{"status":"UP"}`

## 9. 핵심 API Smoke
- 로그인:
  - 요청: `POST /api/auth/login`
  - 결과: `200 success, accessToken issued, refresh_token cookie set`
- refresh:
  - 요청: `POST /api/auth/refresh`
  - 결과: `200 success, accessToken rotated`
- 추천 목록:
  - 요청: `GET /api/recommendations?size=5`
  - 결과: `200 success, 5 items`
- 북마크 토글:
  - 요청: `POST /api/recommendations/1842/bookmark`
  - 결과: `200 success, isBookmarked flipped on re-fetch`
- `GET /api/admin/users/pii-sync-status?failedSampleLimit=5`
  - 결과: `200 success, failedCount=0, oldestPendingEnqueuedAt=null`

## 10. Optional one-shot PII sync smoke
- 실행 여부: `yes`
- 실행 명령:
  - `ENV_FILE=.env DB_QUERY_USERNAME=migration_admin DB_QUERY_PASSWORD=<redacted> DB_MIGRATION_USERNAME=migration_admin DB_MIGRATION_PASSWORD=<redacted> APP_BASE_URL=https://youth-welfare.kr deploy/smoke/user-pii-sync-cutover-smoke.sh`
- 결과: `success, queue_status=SYNCED`

## 11. 판정
- cutover 성공 / 실패: `success`
- 실패 시 중단 지점: `n/a`
- 추가 조치: `monitor pii-sync-status for 30 minutes`

## 12. 롤백 기록
- 롤백 실행 여부: `no`
- 실행 명령: `n/a`
- 결과: `n/a`
- 후속 확인: `none`
