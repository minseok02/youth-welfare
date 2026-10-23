# PostgreSQL 초기화와 기존 DB 변경

현재 런타임은 PostgreSQL입니다. 로컬 Docker 신규 볼륨, 기존 로컬 DB, 서버 RDS의 변경 경로를 구분합니다. [환경 안내](environment-guide.md)에서 앱과 DB 대상을 확인하고, 운영 변경은 별도 승인된 작업 범위에서 수행합니다.

## 현재 파일의 역할

| 경로 | 역할 |
| --- | --- |
| [schema.sql](../../backend/src/main/resources/db/schema.sql) | 신규 DB의 통합 schema. 기존 DB에 자동 반영된다는 뜻이 아님 |
| [Docker init](../../deploy/postgres/init/z90-create-runtime-db-users.sh) | 로컬 PostgreSQL 신규 볼륨의 계정·권한 초기화 |
| `backend/src/main/resources/db/migration/` | 기능별 SQL 변경 파일 |
| `deploy/postgres/patches/` | bootstrap/local patch 스크립트가 적용하는 runtime 변경 SQL |
| [apply-local-runtime-schema-patch.sh](../../deploy/postgres/apply-local-runtime-schema-patch.sh) | **로컬 Docker DB**에 patch와 계정/권한 변경 적용 |
| [apply-local-collect-snapshot-patch.sh](../../deploy/postgres/apply-local-collect-snapshot-patch.sh) | 기존 로컬 Docker DB의 snapshot 전용 검증(기본) 또는 명시적 단일 패치 적용 |
| [bootstrap-rds-runtime.sh](../../deploy/postgres/bootstrap-rds-runtime.sh) | **RDS 대상** bootstrap·계정·schema·patch 적용 |
| [verify-rds-runtime-privileges.sh](../../deploy/postgres/verify-rds-runtime-privileges.sh) | RDS runtime 계정·권한 검증 |
| [복구 리허설](db-backup-restore-rehearsal-runbook.md) | 별도 DB로 복원해 복구 가능성을 검증 |

## 로컬 신규 볼륨

[docker-compose.yml](../../docker-compose.yml)은 PostgreSQL 신규 데이터 디렉터리에 schema와 init 스크립트를 적용합니다. 기존 볼륨의 앱 이미지를 다시 빌드해도 init이 재실행되는 것은 아닙니다. 개발 DB/Redis 기동과 fixture 테스트는 [testing.md](testing.md)를 따릅니다.

기존 데이터가 있는 볼륨을 지우고 새로 만드는 것을 기본 복구 방법으로 사용하지 않습니다.

## 기존 로컬 DB

누락 table/column/grant를 실제 schema와 대조한 뒤 필요한 patch를 검토합니다. 아래 명령은 로컬 Docker의 `youth-welfare-db`에 SQL과 role/grant 변경을 수행합니다. 조회용 preflight가 아닙니다.

```bash
bash deploy/postgres/apply-local-runtime-schema-patch.sh
```

현재 스크립트는 patch 디렉터리의 SQL을 순서대로 다시 실행합니다. 변경 파일 이름이나 history 테이블 존재만으로 미적용 파일만 골라 실행하거나 checksum을 검증한다고 가정하지 않습니다. 적용 범위·데이터 변경·백업을 확인한 뒤 실행하고, 통합 테스트로 확인합니다.

## 수집 snapshot 테이블 누락

2026-10-21 [로컬 수집 기록](../phase-plan.md#2026-10-21-로컬-수집-실행-확인)에서는 `collect_list_snapshots`, `collect_list_snapshot_items`가 없어 목록 변경 비교가 실패했습니다. 이 관측은 당시 로컬 Docker DB에 한정하며 서버 적용 상태를 뜻하지 않습니다.

앱과 같은 DB에 승인된 조회 계정으로 연결한 뒤 다음 읽기 전용 SQL로 존재 여부를 확인합니다. `NULL`은 해당 이름의 relation을 찾지 못했다는 뜻입니다.

```sql
SELECT current_database() AS database_name,
       current_schema() AS schema_name,
       to_regclass('public.collect_list_snapshots') AS list_snapshots,
       to_regclass('public.collect_list_snapshot_items') AS list_snapshot_items;
```

생성 정의는 [통합 schema](../../backend/src/main/resources/db/schema.sql)와 [snapshot migration](../../backend/src/main/resources/db/migration/V2026_06_09_01__add_collect_list_diff_snapshots.sql)에 있습니다. 2026-10-23 작업 A에서 기존 runtime 경로에도 [snapshot 전용 패치](../../deploy/postgres/patches/V2026_10_23_01__add_collect_list_diff_snapshots.sql)를 추가했습니다. 기존 전체 patch 실행기는 다른 계정·schema 변경도 함께 수행하므로 snapshot만 복구하려고 전체 실행하지 않습니다. 파일 추가는 실제 DB 적용의 증거가 아니며 앱 재빌드나 볼륨 삭제로 대신하지 않습니다.

복구 작업은 대상 DB·백업/복원 가능성·두 SQL 경로 차이를 확인한 뒤 필요한 DDL만 별도 변경으로 검토합니다. table/sequence 소유자와 앱·점검 계정의 필요한 권한까지 확인하고, 적용 후 relation 존재·접근 권한·snapshot 저장/비교를 검증합니다. 이 절의 SQL은 진단용이며 DDL 자동 적용 절차가 아닙니다.

### snapshot 전용 검증과 별도 적용

[전용 helper](../../deploy/postgres/apply-local-collect-snapshot-patch.sh)는 env 파일을 읽지 않고 로컬 Unix Docker socket, 실행 중인 `youth-welfare-db`의 local guard/Compose db 라벨, 컨테이너 내부 PostgreSQL socket과 `youth_welfare` DB로 대상을 제한합니다. `DOCKER_HOST`·`DOCKER_CONTEXT`·`PATCH_DIR`로 원격이나 전체 patch 경로로 바꾸지 않습니다. **먼저 앱이 이 DB를 사용하는지 별도로 확인**합니다. 컨테이너 이름과 라벨은 앱 대상 일치나 백업을 보장하지 않습니다.

```bash
# 읽기 전용 metadata 검사. 생략해도 --check가 기본이며 실제 Docker/DB 접근은 수행합니다.
bash deploy/postgres/apply-local-collect-snapshot-patch.sh --check
```

기본 조회 계정은 `admin_dashboard_ro`입니다. `SNAPSHOT_APP_ROLE`·`SNAPSHOT_QUERY_ROLE`로 검토한 서로 다른 기존 계정을 지정할 수 있습니다. 적용 계정은 `migration_admin`이며 계정 생성·비밀번호 변경이나 다른 계정으로의 자동 승격은 하지 않습니다. socket 인증이나 생성/소유·grant 권한이 맞지 않으면 중단하고 별도 권한 검토를 합니다. RDS에는 이 로컬 helper를 사용하지 않습니다.

패치는 한 트랜잭션에서 테이블 2개·인덱스·시퀀스를 준비하고 필요한 앱 `SELECT/INSERT`, 시퀀스 `USAGE/SELECT`, 조회 계정 `SELECT`만 추가합니다. 기존 앱의 다른 권한을 자동 회수하지 않으며, 새 권한이 최소라는 것과 기존 전체 실효 권한이 최소라는 것은 다릅니다. 조회 계정의 table/column 쓰기 권한이나 sequence 변경 권한이 있으면 검증을 실패시킵니다. 권한별 검사는 [PostgreSQL 16 권한 조회 계약](https://www.postgresql.org/docs/16/functions-info.html#FUNCTIONS-INFO-ACCESS-TABLE)에 따라 개별로 수행합니다.

적용 전에는 앱/DB 대상·커밋·적용 SQL/hash·실행 계정·소유자·백업과 복원 가능성·수집 중단/잠금 영향을 확인하고 **별도 적용 승인을 받습니다.** 아래는 그 조건이 충족된 뒤의 실행 형태이지 이번 구현에서 실행한 명령이 아닙니다.

```bash
ALLOW_LOCAL_DOCKER_DB=true SNAPSHOT_BACKUP_CONFIRMED=true \
bash deploy/postgres/apply-local-collect-snapshot-patch.sh --apply
```

이 플래그는 작업자의 확인 표시이며 실제 백업 검증이나 수집 정지를 대신하지 않습니다. helper는 snapshot SQL 하나만 전달합니다. **SQL 파일을 psql에 직접 입력하거나 기존 전체 patch 실행기를 사용하면 기본은 쓰기 적용**이며, 읽기 전용은 `snapshot_check_only=true`로 명시해야 합니다. SQL이 자체 트랜잭션을 관리하므로 외부 트랜잭션에 중첩시키지 않습니다. 오류는 [psql의 ON_ERROR_STOP](https://www.postgresql.org/docs/16/app-psql.html#APP-PSQL-VARIABLES)에 따라 중단하며 성공 표시는 COMMIT 뒤에만 냅니다.

검사는 요구 컬럼의 타입·길이·NULL/default 존재, PK/FK, lookup index, 시퀀스 연결과 실효 권한에 한정됩니다. 기본값 표현식의 모든 의미, 추가 constraint/trigger/RLS, sequence 현재값, 데이터 품질의 완전한 동치 검사는 아닙니다. 기존 구조가 검사와 다르면 자동 ALTER·삭제·권한 회수로 맞추지 않고 중단합니다. 같은 구조에 대한 재실행을 의도하지만 실제 PostgreSQL의 생성·권한·재실행/rollback은 별도 검증이 필요합니다.

적용 후 `--check`와 승인된 최소 저장/비교 검증을 분리해 실행합니다. 전용 patch의 오류는 미커밋 DDL/GRANT를 rollback하도록 구성했지만, 이미 성공한 적용의 되돌림은 후속 snapshot 데이터 유무와 종속성을 확인해 결정합니다. 자동 DROP이나 볼륨 삭제로 복구하지 않습니다. helper·패치 준비/오프라인 회귀 통과는 실제 DB 복구·수집·운영 반영 완료가 아닙니다.

## 서버와 RDS

서버에는 로컬 `db` 컨테이너가 없다는 배포 기준을 전제로 [deployment.md](../deployment.md)의 bootstrap·runtime env·권한 검증 절차를 따릅니다. 로컬 Docker patch 명령을 RDS 적용 명령으로 바꾸어 사용하지 않습니다.

운영 DB 변경 전에는 대상 DB, 백업·복원 가능성, 실행 계정, 적용 SQL, 잠금/데이터 변경, 실패 시 복구 순서를 확인합니다. 앱 재기동과 DB 변경을 구분하고 실제 적용 결과를 검사합니다.

## 자동 적용과 후속 개선의 구분

현재 빌드에는 Flyway 기반의 자동 migration 실행이 구성되어 있지 않습니다. `migration/` 디렉터리나 `schema_migration_history` 테이블이 있다고 해서 자동 적용·한 번만 실행·checksum 검증을 보장하지 않습니다.

신규 DB/기존 DB 전체 변경 경로의 재현 가능성과 이력/checksum 관리는 여전히 리팩토링 과제입니다. snapshot 전용 패치 준비는 그 도구를 도입하거나 다른 migration을 자동 적용한 작업이 아닙니다. 실제 SQL 적용 여부는 해당 작업 기록의 환경·검증 범위를 확인합니다.

## 과거 MySQL 자료

- [정리 전 migration 기록](../history/core/db-migration-before-doc-cleanup.md): 과거 실험·sidecar 설계·MySQL 예시의 원문.
- [보관된 MySQL SQL](../archive/mysql-migrations/README.md): 현재 PostgreSQL runtime에 적용하지 않는 자료.

과거 설계의 유효한 의미는 현재 코드·요구사항과 대조하되, 옛 명령을 현재 실행 순서로 재사용하지 않습니다.
