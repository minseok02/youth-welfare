# 운영 DB 계정 전환 런북

이 문서는 기존 운영 DB 또는 기존 Docker 볼륨에서 런타임 MySQL 계정을 `root`에서 기능별 계정으로 바꿀 때 사용하는 절차입니다.
신규 볼륨 초기화는 [`deploy/mysql/init/z90-create-runtime-db-users.sh`](../deploy/mysql/init/z90-create-runtime-db-users.sh)가 처리하고, 이 문서는 그 스크립트가 다시 실행되지 않는 환경을 대상으로 합니다.

관련 파일:

- [`deploy/mysql/runtime-db-accounts.sql.example`](../deploy/mysql/runtime-db-accounts.sql.example)
- [`docs/deployment.md`](./deployment.md)
- [`docs/db-migration.md`](./db-migration.md)

## 1. 목표

이번 단계의 목표는 아래 4개 계정을 기존 운영 DB에 맞추고, 앱 datasource를 `app_core_rw`로 전환하는 것입니다.

- `app_core_rw`
  - 현재 단일 datasource 런타임 앱 계정
  - `youth_welfare.*` DML
  - 임시 호환을 위해 `youth_welfare_pii.user_pii` DML 포함
- `app_pii_rw`
  - 추후 PII 전용 datasource용
  - `youth_welfare_pii.user_pii` DML
- `notification_pii_ro`
  - 추후 알림 전용 read-only 계정
  - `youth_welfare_pii.user_pii(user_key, email_enc)` column-level SELECT
- `migration_admin`
  - 수동 migration, schema 점검, 운영 대응용
  - `youth_welfare.*`, `youth_welfare_pii.*` 전체 권한

## 2. 적용 전 체크

- 운영 DB 백업 또는 snapshot 확보
- 현재 앱이 어떤 DB 계정으로 붙는지 확인
- 기존에 `app_core_rw`, `app_pii_rw`, `notification_pii_ro`, `migration_admin` 계정이 있는지 확인
- 운영 `.env` 또는 secret store에서 아래 값을 새로 발급
  - `DB_USERNAME=app_core_rw`
  - `DB_PASSWORD=<app_core_rw password>`
  - `DB_MIGRATION_USERNAME=migration_admin`
  - `DB_MIGRATION_PASSWORD=<migration_admin password>`
  - `DB_APP_PII_USERNAME=app_pii_rw`
  - `DB_APP_PII_PASSWORD=<app_pii_rw password>`
  - `DB_NOTIFICATION_PII_RO_USERNAME=notification_pii_ro`
  - `DB_NOTIFICATION_PII_RO_PASSWORD=<notification_pii_ro password>`

확인 예시:

```sql
SELECT user, host
FROM mysql.user
WHERE user IN ('app_core_rw', 'app_pii_rw', 'notification_pii_ro', 'migration_admin');
```

## 3. SQL 준비

1. [`deploy/mysql/runtime-db-accounts.sql.example`](../deploy/mysql/runtime-db-accounts.sql.example)를 복사합니다.
2. placeholder를 실제 비밀번호로 치환합니다.
3. root 또는 동등한 관리자 계정으로 실행합니다.

예시:

```bash
cp deploy/mysql/runtime-db-accounts.sql.example /tmp/runtime-db-accounts.sql
```

치환할 값:

- `__APP_CORE_RW_PASSWORD__`
- `__APP_PII_RW_PASSWORD__`
- `__NOTIFICATION_PII_RO_PASSWORD__`
- `__MIGRATION_ADMIN_PASSWORD__`

## 4. SQL 적용

로컬 포트 포워딩 또는 bastion을 통해 MySQL에 접속한 상태에서 실행합니다.

```bash
mysql -h 127.0.0.1 -P 3307 -u root -p < /tmp/runtime-db-accounts.sql
```

주의:

- `CREATE USER IF NOT EXISTS`만으로 끝내지 않습니다.
- 기존에 같은 이름의 계정이 있으면 `ALTER USER`와 `REVOKE ... / GRANT ...`까지 같이 적용해 비밀번호와 권한을 현재 기준으로 덮어씁니다.
- 현재 구조에서는 `app_core_rw`가 임시로 `user_pii` DML 권한을 가집니다. 이건 다중 datasource 분리 전까지의 타협입니다.

## 5. 계정 검증

최소 검증은 각 계정이 기대 권한으로 로그인되는지 확인하는 것입니다.

```sql
SHOW GRANTS FOR 'app_core_rw'@'%';
SHOW GRANTS FOR 'app_pii_rw'@'%';
SHOW GRANTS FOR 'notification_pii_ro'@'%';
SHOW GRANTS FOR 'migration_admin'@'%';
```

추가 확인:

- `app_core_rw`
  - `USE youth_welfare; SELECT COUNT(*) FROM users;`
- `app_pii_rw`
  - `SELECT COUNT(*) FROM youth_welfare_pii.user_pii;`
- `notification_pii_ro`
  - `SELECT user_key, email_enc FROM youth_welfare_pii.user_pii LIMIT 1;`
  - `SELECT phone_enc FROM youth_welfare_pii.user_pii LIMIT 1;` 는 실패해야 정상
- `migration_admin`
  - `SHOW DATABASES LIKE 'youth_welfare_pii';`

## 6. 앱 datasource 전환

운영 `.env` 또는 secret store에서 런타임 앱 계정을 아래처럼 바꿉니다.

```env
DB_USERNAME=app_core_rw
DB_PASSWORD=<app_core_rw password>
DB_MIGRATION_USERNAME=migration_admin
DB_MIGRATION_PASSWORD=<migration_admin password>
DB_APP_PII_USERNAME=app_pii_rw
DB_APP_PII_PASSWORD=<app_pii_rw password>
DB_NOTIFICATION_PII_RO_USERNAME=notification_pii_ro
DB_NOTIFICATION_PII_RO_PASSWORD=<notification_pii_ro password>
```

적용 순서:

1. DB 계정 SQL 적용
2. `app_core_rw` 로그인/권한 확인
3. 운영 `.env` 또는 secret 갱신
4. 앱 재기동
5. health check, 로그인, 추천, 북마크 최소 smoke

앱 재기동 예시:

```bash
docker compose -f docker-compose.yml up -d --build app
```

## 7. cutover 체크리스트

- [ ] 운영 DB backup/snapshot 확보
- [ ] 4개 계정 비밀번호 신규 발급
- [ ] `runtime-db-accounts.sql.example` placeholder 치환
- [ ] 운영 DB에 SQL 적용
- [ ] `SHOW GRANTS` 4종 확인
- [ ] `notification_pii_ro`가 `phone_enc`를 읽지 못하는지 확인
- [ ] 운영 `.env`의 `DB_USERNAME`, `DB_PASSWORD` 변경
- [ ] 앱 재기동
- [ ] `/actuator/health` 200 확인
- [ ] 로그인 / refresh / 추천 목록 / 북마크 토글 smoke 확인
- [ ] 이후 migration 작업은 `migration_admin` 계정 기준으로 수행

## 8. 롤백 기준

아래 중 하나라도 실패하면 앱 계정 cutover를 중단하고 이전 datasource credential로 되돌립니다.

- `app_core_rw` 로그인 실패
- 앱 부팅 후 datasource 인증 실패
- 로그인 또는 추천/북마크 핵심 API 실패

롤백 순서:

1. 운영 `.env`의 `DB_USERNAME`, `DB_PASSWORD`를 이전 값으로 복원
2. 앱 재기동
3. 에러 로그 보존 후 DB 계정 grant 재점검

계정 자체를 지우는 것은 마지막 단계입니다.
먼저 앱 cutover만 되돌리고, 권한 문제를 분리해서 확인하는 편이 안전합니다.
