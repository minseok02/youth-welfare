# 런타임 Cutover 실행 로그 템플릿

이 문서는 실제 운영 cutover 직후 결과를 남기기 위한 기록 템플릿입니다.
실행 순서는 [runtime-cutover-checklist.md](./runtime-cutover-checklist.md)를 따르고, 이 문서는 그 결과를 채워 넣는 용도로 사용합니다.

복사해서 날짜/배포차수별 문서나 PR 코멘트에 붙여 넣어 사용합니다.

```md
# Runtime Cutover Log - YYYY-MM-DD HH:mm KST

## 기본 정보
- 작업자:
- 시작 시각:
- 종료 시각:
- 서버/환경:
- 관련 브랜치 / PR:
- 관련 체크리스트: `docs/runtime-cutover-checklist.md`

## 1. 사전 준비
- backup 또는 snapshot:
- `/tmp/runtime-db-accounts.sql` 준비 여부:
- secret store / `.env` 수정 권한 확인:

## 2. 계정 SQL 적용
- 실행 명령:
  - `mysql -h <db-host> -P 3306 -u root -p < /tmp/runtime-db-accounts.sql`
- 결과:
- 비고:

## 3. 권한 검증
- `SHOW GRANTS FOR 'app_core_rw'@'%';`
  - 결과:
- `SHOW GRANTS FOR 'app_pii_rw'@'%';`
  - 결과:
- `SHOW GRANTS FOR 'notification_pii_ro'@'%';`
  - 결과:
- `SHOW GRANTS FOR 'migration_admin'@'%';`
  - 결과:
- `app_core_rw` 의 `youth_welfare_pii.user_pii` 조회 실패 확인:
- `notification_pii_ro` 의 `phone_enc` 조회 실패 확인:

## 4. Migration 적용
- `V2026_04_28_01__drop_runtime_legacy_user_id.sql`
  - 실행 시각:
  - 결과:
- `V2026_04_28_02__add_user_pii_sync_queue.sql`
  - 실행 시각:
  - 결과:

## 5. Env / Secret 갱신
- `DB_USERNAME=app_core_rw` 반영:
- `APP_PII_DB_URL -> youth_welfare_pii` 반영:
- `NOTIFICATION_PII_DB_URL -> youth_welfare_pii` 반영:
- `DB_MIGRATION_*` 반영:
- `DB_APP_PII_*` 반영:
- `DB_NOTIFICATION_PII_RO_*` 반영:

## 6. Preflight Summary
- 실행 명령:
  - `ENV_FILE=.env PRINT_SUMMARY=true deploy/smoke/preflight-runtime-cutover-env.sh`
- 결과:
  - `DB_URL target`:
  - `APP_PII_DB_URL target`:
  - `NOTIFICATION_PII_DB_URL target`:
  - `DB_USERNAME`:
  - `DB_MIGRATION_USERNAME`:
  - `DB_APP_PII_USERNAME`:
  - `DB_NOTIFICATION_PII_RO_USERNAME`:
  - password 상태:
- pass/fail:

## 7. App 재기동
- 실행 명령:
  - `docker compose -f docker-compose.yml up -d --build app`
- 결과:
- 비고:

## 8. Health Check
- 실행 명령:
  - `curl -fsS http://127.0.0.1:8082/actuator/health`
- 결과:

## 9. 핵심 API Smoke
- 로그인:
  - 요청:
  - 결과:
- refresh:
  - 요청:
  - 결과:
- 추천 목록:
  - 요청:
  - 결과:
- 북마크 토글:
  - 요청:
  - 결과:
- `GET /api/admin/users/pii-sync-status?failedSampleLimit=5`
  - 결과:

## 10. Optional one-shot PII sync smoke
- 실행 여부:
- 실행 명령:
  - `ENV_FILE=.env DB_QUERY_USERNAME=migration_admin DB_QUERY_PASSWORD=<password> DB_MIGRATION_USERNAME=migration_admin DB_MIGRATION_PASSWORD=<password> APP_BASE_URL=http://127.0.0.1:8082 deploy/smoke/user-pii-sync-cutover-smoke.sh`
- 결과:

## 11. 판정
- cutover 성공 / 실패:
- 실패 시 중단 지점:
- 추가 조치:

## 12. 롤백 기록
- 롤백 실행 여부:
- 실행 명령:
- 결과:
- 후속 확인:
```

## 최소 보관 항목

- preflight summary 출력
- `SHOW GRANTS` 4종 결과
- migration 적용 시각과 성공 여부
- `/actuator/health` 결과
- 로그인 / 추천 / 북마크 smoke 결과
- rollback 실행 여부
