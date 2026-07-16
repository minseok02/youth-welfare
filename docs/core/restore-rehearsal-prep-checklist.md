# Restore Rehearsal Prep Checklist

작성 기준일: 2026-07-16

## Purpose

Prepare for an RDS restore rehearsal without creating paid resources yet.

Do not run the restore until the user explicitly approves the temporary RDS cost.

## Current Blockers

| Blocker | Status |
| --- | --- |
| snapshot inventory read permission | repo policy updated, AWS IAM not applied yet |
| restore target identifier | not chosen |
| restore subnet group | not chosen |
| restore security group | not chosen |
| cleanup deadline | not agreed |

## IAM First

Apply the updated read-only policy in [aws-ops-monitor-role-policy.json](../../deploy/ops/aws-ops-monitor-role-policy.json) to `youth-welfare-ops-monitor-v2-role`.

The EC2 ops role cannot apply it itself:

```text
iam:PutRolePolicy -> AccessDenied
```

After an IAM-capable principal applies it, verify:

```bash
aws rds describe-db-snapshots \
  --region ap-northeast-2 \
  --db-instance-identifier youth-welfare-prod-db \
  --snapshot-type automated \
  --query 'reverse(sort_by(DBSnapshots,&SnapshotCreateTime))[:5].{Id:DBSnapshotIdentifier,Status:Status,Created:SnapshotCreateTime,Encrypted:Encrypted}' \
  --output table

aws rds describe-db-instance-automated-backups \
  --region ap-northeast-2 \
  --db-instance-identifier youth-welfare-prod-db \
  --query 'DBInstanceAutomatedBackups[0].{Status:Status,RestoreWindowEarliest:EarliestRestorableTime,RestoreWindowLatest:LatestRestorableTime,Encrypted:Encrypted}' \
  --output table
```

## Naming

Suggested restore target identifier:

```text
youth-welfare-restore-rehearsal-YYYYMMDD
```

Suggested local env file:

```text
.env.restore
```

Never point production `.env.production` or `.env.runtime.production` at the restore DB.

## Preflight Values To Choose

| Value | Required before execution |
| --- | --- |
| target DB identifier | yes |
| restore subnet group | yes |
| restore security group | yes |
| DB class | yes, default to smallest acceptable rehearsal class |
| deletion time | yes |
| who verifies app smoke | yes |

## Restore Command Skeleton

Do not run until approved.

```bash
aws rds restore-db-instance-to-point-in-time \
  --region ap-northeast-2 \
  --source-db-instance-identifier youth-welfare-prod-db \
  --target-db-instance-identifier youth-welfare-restore-rehearsal-YYYYMMDD \
  --use-latest-restorable-time \
  --db-subnet-group-name <restore-subnet-group> \
  --vpc-security-group-ids <restore-security-group-id> \
  --no-publicly-accessible
```

Wait:

```bash
aws rds wait db-instance-available \
  --region ap-northeast-2 \
  --db-instance-identifier youth-welfare-restore-rehearsal-YYYYMMDD
```

## Verification Plan

1. Confirm restore DB endpoint is not the production endpoint.
2. Create `.env.restore` with restore DB credentials only.
3. Run DB audit:

```bash
ENV_FILE=.env.restore bash deploy/postgres/audit-operational-db-state.sh
```

4. Run runtime env preflight:

```bash
ENV_FILE=.env.restore PRINT_SUMMARY=true bash deploy/smoke/preflight-runtime-cutover-env.sh
```

5. Only if a rehearsal app is explicitly planned, run bounded runtime smoke against the restore app, not production.

## Cleanup

Before starting the rehearsal, decide the cleanup command and owner.

```bash
aws rds delete-db-instance \
  --region ap-northeast-2 \
  --db-instance-identifier youth-welfare-restore-rehearsal-YYYYMMDD \
  --skip-final-snapshot
```

Use `--skip-final-snapshot` only if the rehearsal result is already documented and no new data needs preservation.

## Stop Conditions

Stop and do not continue if:

- IAM snapshot inventory is still blocked
- target endpoint could be confused with production
- restore security group would allow public access
- cleanup owner/time is not agreed
- user has not approved the temporary RDS cost
