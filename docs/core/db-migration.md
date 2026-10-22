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

생성 정의는 [통합 schema](../../backend/src/main/resources/db/schema.sql)와 [snapshot migration](../../backend/src/main/resources/db/migration/V2026_06_09_01__add_collect_list_diff_snapshots.sql)에 있습니다. 현재 기본 `deploy/postgres/patches/`에는 이 생성문이 없어 **로컬 patch 스크립트만 실행해도 누락이 해결된다고 보장할 수 없습니다.** 앱 재빌드나 볼륨 삭제를 복구 방법으로 대신하지 않습니다.

복구 작업은 대상 DB·백업/복원 가능성·두 SQL 경로 차이를 확인한 뒤 필요한 DDL만 별도 변경으로 검토합니다. table/sequence 소유자와 앱·점검 계정의 필요한 권한까지 확인하고, 적용 후 relation 존재·접근 권한·snapshot 저장/비교를 검증합니다. 이 절의 SQL은 진단용이며 DDL 자동 적용 절차가 아닙니다.

## 서버와 RDS

서버에는 로컬 `db` 컨테이너가 없다는 배포 기준을 전제로 [deployment.md](../deployment.md)의 bootstrap·runtime env·권한 검증 절차를 따릅니다. 로컬 Docker patch 명령을 RDS 적용 명령으로 바꾸어 사용하지 않습니다.

운영 DB 변경 전에는 대상 DB, 백업·복원 가능성, 실행 계정, 적용 SQL, 잠금/데이터 변경, 실패 시 복구 순서를 확인합니다. 앱 재기동과 DB 변경을 구분하고 실제 적용 결과를 검사합니다.

## 자동 적용과 후속 개선의 구분

현재 빌드에는 Flyway 기반의 자동 migration 실행이 구성되어 있지 않습니다. `migration/` 디렉터리나 `schema_migration_history` 테이블이 있다고 해서 자동 적용·한 번만 실행·checksum 검증을 보장하지 않습니다.

두 SQL 경로의 일치 여부와 신규 DB/기존 DB의 재현 가능한 변경 방식은 리팩토링 확인 과제입니다. 이번 문서 정리에서 migration 도구 도입, patch 수정, 실제 SQL 실행은 수행하지 않습니다.

## 과거 MySQL 자료

- [정리 전 migration 기록](../history/core/db-migration-before-doc-cleanup.md): 과거 실험·sidecar 설계·MySQL 예시의 원문.
- [보관된 MySQL SQL](../archive/mysql-migrations/README.md): 현재 PostgreSQL runtime에 적용하지 않는 자료.

과거 설계의 유효한 의미는 현재 코드·요구사항과 대조하되, 옛 명령을 현재 실행 순서로 재사용하지 않습니다.
