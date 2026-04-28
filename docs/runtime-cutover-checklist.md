# 런타임 Cutover 체크리스트

이 문서는 운영 전환 시 `계정 전환 -> migration -> preflight -> app 재기동 -> smoke` 순서만 빠르게 따라가기 위한 one-page 체크리스트입니다.
세부 설명은 [db-account-cutover-runbook.md](./db-account-cutover-runbook.md), [db-migration.md](./db-migration.md), [deployment.md](./deployment.md)를 봅니다.

## 1. 준비물

- 운영 DB backup 또는 snapshot
- 신규 비밀번호가 채워진 `/tmp/runtime-db-accounts.sql`
- 운영 `.env` 또는 secret store 수정 권한
- `migration_admin` 접속 정보
- 앱 재기동 권한

이번 cutover 기준 핵심 값:

- `DB_USERNAME=app_core_rw`
- `APP_PII_DB_URL=jdbc:mysql://<host>:3306/youth_welfare_pii?...`
- `NOTIFICATION_PII_DB_URL=jdbc:mysql://<host>:3306/youth_welfare_pii?...`
- `DB_MIGRATION_USERNAME=migration_admin`
- `DB_APP_PII_USERNAME=app_pii_rw`
- `DB_NOTIFICATION_PII_RO_USERNAME=notification_pii_ro`

## 2. 실행 순서

1. 운영 DB backup 또는 snapshot 확보

2. 계정 SQL 적용

```bash
mysql -h <db-host> -P 3306 -u root -p < /tmp/runtime-db-accounts.sql
```

3. 권한 quick check

```sql
SHOW GRANTS FOR 'app_core_rw'@'%';
SHOW GRANTS FOR 'app_pii_rw'@'%';
SHOW GRANTS FOR 'notification_pii_ro'@'%';
SHOW GRANTS FOR 'migration_admin'@'%';

SELECT COUNT(*) FROM youth_welfare.users;
SELECT COUNT(*) FROM youth_welfare_pii.user_pii;
```

- `app_core_rw` 로는 `youth_welfare_pii.user_pii` 조회가 실패해야 정상
- `notification_pii_ro` 로는 `user_key`, `email_enc` 조회만 성공하고 `phone_enc` 조회는 실패해야 정상

4. 운영 DB migration 적용

```bash
mysql -h <db-host> -P 3306 -u migration_admin -p youth_welfare \
  < backend/src/main/resources/db/migration/V2026_04_28_01__drop_runtime_legacy_user_id.sql

mysql -h <db-host> -P 3306 -u migration_admin -p youth_welfare \
  < backend/src/main/resources/db/migration/V2026_04_28_02__add_user_pii_sync_queue.sql
```

5. 운영 `.env` 또는 secret store 갱신

- `DB_USERNAME`, `DB_PASSWORD`
- `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL`
- `DB_MIGRATION_USERNAME`, `DB_MIGRATION_PASSWORD`
- `DB_APP_PII_USERNAME`, `DB_APP_PII_PASSWORD`
- `DB_NOTIFICATION_PII_RO_USERNAME`, `DB_NOTIFICATION_PII_RO_PASSWORD`

6. preflight + summary 확인

```bash
ENV_FILE=.env PRINT_SUMMARY=true deploy/smoke/preflight-runtime-cutover-env.sh
```

정상 기준:

- `DB_URL target` 은 `youth_welfare`
- `APP_PII_DB_URL target` 은 `youth_welfare_pii`
- `NOTIFICATION_PII_DB_URL target` 은 `youth_welfare_pii`
- username 4종이 `app_core_rw`, `migration_admin`, `app_pii_rw`, `notification_pii_ro`
- password 4종이 모두 `set`

7. 앱 재기동

```bash
docker compose -f docker-compose.yml up -d --build app
```

8. 재기동 직후 health check

```bash
curl -fsS http://127.0.0.1:8082/actuator/health
```

9. 핵심 smoke

- 로그인
- refresh
- 추천 목록
- 북마크 토글
- 필요 시 `GET /api/admin/users/pii-sync-status?failedSampleLimit=5`

10. 필요 시 one-shot PII sync smoke

```bash
ENV_FILE=.env \
DB_QUERY_USERNAME=migration_admin \
DB_QUERY_PASSWORD=<migration-admin-password> \
DB_MIGRATION_USERNAME=migration_admin \
DB_MIGRATION_PASSWORD=<migration-admin-password> \
APP_BASE_URL=http://127.0.0.1:8082 \
deploy/smoke/user-pii-sync-cutover-smoke.sh
```

## 3. 중단 기준

아래 중 하나라도 걸리면 cutover를 멈추고 롤백을 우선합니다.

- preflight summary에서 schema 또는 username이 기대값과 다름
- 앱 부팅 실패 또는 `/actuator/health` 비정상
- 로그인 / 추천 / 북마크 핵심 API 실패
- `user_pii_sync_queue` `failedCount > 0` 이고 즉시 원인을 설명할 수 없음

## 4. 롤백 순서

1. 운영 `.env` 또는 secret store를 이전 값으로 복원
2. 앱 재기동
3. health / 로그인 / 추천 재확인
4. DB grant, migration 적용 상태, summary 출력 결과를 따로 점검

## 5. 증적 보관

운영 cutover 직후 아래 결과를 남깁니다.

- preflight summary 출력
- `SHOW GRANTS` 결과
- migration 적용 로그
- `/actuator/health` 응답
- 로그인 / 추천 / 북마크 smoke 결과
