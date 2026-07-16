# DB 백업 복구 리허설 런북

## 목적

운영 DB 장애나 잘못된 schema 변경에 대비해 백업에서 별도 DB로 복원하고 앱 연결 전 검증하는 절차를 고정한다.

## 원칙

- 운영 DB에는 복원 테스트를 직접 수행하지 않는다.
- 리허설 대상은 별도 RDS instance 또는 격리된 PostgreSQL이어야 한다.
- 복원 DB는 외부 트래픽을 받지 않는 보안 그룹에 둔다.
- 앱을 복원 DB에 붙이기 전 `deploy/postgres/audit-operational-db-state.sh` 와 runtime smoke를 먼저 통과시킨다.
- PII 포함 DB이므로 dump 파일은 암호화된 저장소에 두고 리허설 후 삭제한다.

## 사전 조건

- ops monitor 역할에 `rds:DescribeDBInstances`, `rds:DescribeDBSnapshots`, `rds:DescribeDBInstanceAutomatedBackups` 권한이 적용되어 있어야 한다.
- 복원 대상 DB는 운영과 다른 endpoint, 다른 DB identifier를 사용한다.
- 복원 대상 DB 접속 정보는 별도 `.env.restore` 같은 파일에 둔다.
- 운영 앱의 `DB_URL`은 리허설 중 변경하지 않는다.

승인 전 준비 항목은 [restore-rehearsal-prep-checklist.md](./restore-rehearsal-prep-checklist.md)를 먼저 따른다.

2026-07-16 현재 사전 확인:

- 실행 역할 `arn:aws:sts::857721769929:assumed-role/youth-welfare-ops-monitor-v2-role/...` 에 `deploy/ops/aws-ops-monitor-role-policy.json` 기준 inline policy가 반영됐다.
- 같은 역할로 `rds:DescribeDBInstances` 조회가 통과한다.
- 운영 RDS `youth-welfare-prod-db` 는 `available`, backup retention `7`, latest restorable time `2026-07-16T11:22:28Z`, deletion protection `true`, storage encrypted `true`, Multi-AZ `false`, public access `false` 로 확인됐다.
- snapshot inventory 조회는 현재 role에 `rds:DescribeDBSnapshots`, `rds:DescribeDBInstanceAutomatedBackups` 가 없어 `AccessDenied` 로 막혔다.
- `deploy/ops/aws-ops-monitor-role-policy.json` 는 이 read-only 권한을 포함하도록 갱신했다. 콘솔/IAM에서 반영 후 snapshot inventory를 다시 확인한다.
- EC2 ops role에서 `aws iam put-role-policy` 직접 반영을 시도했지만 `iam:PutRolePolicy` 권한이 없어 `AccessDenied` 로 실패했다. IAM 권한이 있는 사용자/콘솔에서 반영해야 한다.
- 실제 restore rehearsal은 별도 RDS instance를 생성하는 비용 발생 작업이라 현재 보류한다. 명시 승인 후 target DB identifier, subnet group, restore security group을 정한 뒤 진행한다.

## 1. 운영 RDS 백업 상태 확인

```bash
aws rds describe-db-instances \
  --db-instance-identifier youth-welfare-prod-db \
  --query 'DBInstances[0].{Status:DBInstanceStatus,BackupRetentionPeriod:BackupRetentionPeriod,LatestRestorableTime:LatestRestorableTime,DeletionProtection:DeletionProtection,MultiAZ:MultiAZ,StorageEncrypted:StorageEncrypted}' \
  --output table
```

snapshot inventory 권한이 반영된 뒤:

```bash
aws rds describe-db-snapshots \
  --db-instance-identifier youth-welfare-prod-db \
  --snapshot-type automated \
  --query 'reverse(sort_by(DBSnapshots,&SnapshotCreateTime))[:5].{Id:DBSnapshotIdentifier,Status:Status,Created:SnapshotCreateTime,Encrypted:Encrypted}' \
  --output table
```

확인 기준:

- `BackupRetentionPeriod`가 1 이상
- `LatestRestorableTime`이 최근 시각
- `DeletionProtection` 활성
- `StorageEncrypted` 활성

## 2. 별도 DB로 복원

RDS point-in-time restore 또는 snapshot restore로 별도 identifier를 만든다.

```bash
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier youth-welfare-prod-db \
  --target-db-instance-identifier youth-welfare-restore-rehearsal-YYYYMMDD \
  --use-latest-restorable-time \
  --db-subnet-group-name <restore-subnet-group> \
  --vpc-security-group-ids <restore-security-group-id>
```

복원 DB가 `available` 이 될 때까지 기다린다.

## 3. 복원 DB 접속 정보 준비

`.env.restore` 는 운영 `.env.production` 을 복사하지 말고 DB 접속 관련 값만 별도로 둔다.

필수 값:

- `DB_URL`
- `DB_MIGRATION_USERNAME`
- `DB_MIGRATION_PASSWORD`

앱 smoke까지 붙일 때만 필요한 runtime role password를 추가한다.

## 4. 복원 DB 감사

```bash
ENV_FILE=.env.restore deploy/postgres/audit-operational-db-state.sh
```

통과 기준:

- 핵심 테이블 `OK`
- `schema_migration_history` `OK`
- `chat_snapshots_nonnull_orphan_session=0`
- `active_queries_over_5m=0`
- `waiting_locks=0`
- `user_pii_sync_pending_or_failed=0`

알려진 관찰 항목:

- `auth_without_users`, `profiles_without_users`, `pii_without_users` 가 0이 아니면 사용자 core projection 불일치 정리 전까지 FK 추가를 보류한다.
- `active_users_without_pii` 는 0이어야 한다. 0이 아니면 PII sync/backfill 누락으로 보고 복구한다.
- `withdrawn_or_inactive_users_without_pii` 는 탈퇴/비활성 계정에서 PII 삭제가 완료된 상태일 수 있으므로 별도 경고로 보지 않는다.
- `users_without_pii` 는 위 두 값을 합친 총량으로만 본다.

## 5. 앱 연결 리허설

운영 앱이 아니라 일회성 리허설 앱 또는 로컬 앱만 복원 DB에 연결한다.

```bash
ENV_FILE=.env.restore deploy/smoke/preflight-runtime-cutover-env.sh
ENV_FILE=.env.restore deploy/smoke/run-local-runtime-api-smoke.sh
```

검증 후 운영 DB endpoint로 되돌릴 필요가 없도록, 운영 앱 환경 파일은 리허설에 사용하지 않는다.

## 6. 종료

- 리허설 결과와 복원 DB identifier, 복원 시각을 기록한다.
- dump 파일이나 임시 `.env.restore` 는 삭제한다.
- 복원 RDS instance는 비용 발생을 막기 위해 삭제한다.
- 실패한 경우 `docs/core/server-runtime-drift-checklist.md` 기준으로 schema drift부터 분리한다.
