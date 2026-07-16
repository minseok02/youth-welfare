# Data Layer Risk And Backup Check 2026-07-16

## Purpose

Check the current RDS and ElastiCache/Valkey operational risk before opening another performance or feature change.

This check does not enable Multi-AZ, failover, restore rehearsal, or new paid resources.

## RDS Current State

| Item | Value |
| --- | --- |
| DB identifier | `youth-welfare-prod-db` |
| status | `available` |
| engine | PostgreSQL `16.14` |
| class | `db.t3.small` |
| storage | `20 GiB`, `gp3` |
| storage encrypted | `true` |
| public access | `false` |
| deletion protection | `true` |
| Multi-AZ | `false` |
| backup retention | `7` days |
| backup window | `16:11-16:41 UTC` |
| latest restorable time | `2026-07-16T11:22:28Z` |
| maintenance window | `wed:19:50-wed:20:20 UTC` |
| auto minor version upgrade | `true` |

Recent storage metric:

| Metric | Latest observed value |
| --- | ---: |
| `FreeStorageSpace` | about `17.7GB` free |

Reading:

- Current capacity pressure is low.
- RDS has point-in-time restore metadata available.
- RDS remains single-AZ, so an AZ/instance failure can still cause downtime until AWS/RDS recovery or manual restore completes.
- Restore rehearsal remains deferred because it creates a separate RDS instance and costs money.

## RDS Backup Inventory Gap

The current EC2 role can read `DescribeDBInstances`, but snapshot inventory calls were denied:

- `rds:DescribeDBSnapshots`
- `rds:DescribeDBInstanceAutomatedBackups`

The repo policy file [aws-ops-monitor-role-policy.json](../../deploy/ops/aws-ops-monitor-role-policy.json) was updated to include these read-only actions.

Apply attempt:

- attempted `aws iam put-role-policy` from the EC2 ops role
- result: `AccessDenied` for `iam:PutRolePolicy`
- meaning: the repo policy file is ready, but an IAM-capable principal must apply it in AWS

Apply the updated policy before the next backup inventory check, then run:

```bash
aws rds describe-db-snapshots \
  --region ap-northeast-2 \
  --db-instance-identifier youth-welfare-prod-db \
  --snapshot-type automated \
  --query 'reverse(sort_by(DBSnapshots,&SnapshotCreateTime))[:5].{Id:DBSnapshotIdentifier,Status:Status,Created:SnapshotCreateTime,Allocated:AllocatedStorage,Encrypted:Encrypted}' \
  --output table

aws rds describe-db-instance-automated-backups \
  --region ap-northeast-2 \
  --db-instance-identifier youth-welfare-prod-db \
  --query 'DBInstanceAutomatedBackups[0].{Status:Status,AllocatedStorage:AllocatedStorage,Encrypted:Encrypted,RestoreWindowEarliest:EarliestRestorableTime,RestoreWindowLatest:LatestRestorableTime}' \
  --output table
```

## Valkey Current State

| Item | Value |
| --- | --- |
| replication group | `youth-welfare-prod-redis-valkey` |
| status | `available` |
| engine | `valkey` |
| members | `youth-welfare-prod-redis-valkey-001` |
| automatic failover | `disabled` |
| Multi-AZ | `disabled` |
| at-rest encryption | `true` |
| transit encryption | `false` |
| snapshot retention | `0` |
| snapshot window | `00:30-01:30 UTC` |

Recent metrics:

| Metric | Latest observed value |
| --- | ---: |
| `DatabaseMemoryUsagePercentage` | about `1.75%` |
| `Evictions` | `0` |

Reading:

- Current Redis/Valkey capacity pressure is low.
- Redis is used as cache/session/rate-limit/runtime coordination storage, not the canonical database.
- Snapshot retention is `0`, so there is no Redis snapshot backup path today.
- Failover is disabled, so Redis node failure can cause login/session/rate-limit/cache disruption until recovery.

## Valkey Snapshot Inventory Gap

The current EC2 role can read ElastiCache cluster and replication group metadata, but snapshot inventory was denied:

- `elasticache:DescribeSnapshots`

The repo policy file was updated to include that read-only action.

The same IAM apply attempt failed because the EC2 ops role is not allowed to mutate IAM policies.

After applying the updated policy, run:

```bash
aws elasticache describe-snapshots \
  --region ap-northeast-2 \
  --replication-group-id youth-welfare-prod-redis-valkey \
  --query 'Snapshots[:5].{Name:SnapshotName,Status:SnapshotStatus,Created:NodeSnapshots[0].SnapshotCreateTime}' \
  --output table
```

## Alert Delivery Test

SNS test publishes were sent without changing any CloudWatch alarm state:

| Region | Topic | Publish result |
| --- | --- | --- |
| `ap-northeast-2` | `youth-welfare-ops-alerts` | accepted, MessageId recorded in command output |
| `us-east-1` | `youth-welfare-ops-alerts` | accepted, MessageId recorded in command output |

Webhook:

- `ALERT_WEBHOOK_URL` is not configured in `/home/ubuntu/.config/youth-welfare/ops.env`.
- Webhook delivery was therefore not tested.

Human follow-up:

- Check the subscribed mailbox for both SNS test messages.
- If either message is missing, check spam/quarantine first, then SNS subscription delivery policy.

## Decision

Do not enable paid HA changes immediately.

Recommended next actions:

1. Apply the updated read-only IAM policy so snapshot inventory can be verified from the ops node.
2. Keep RDS as single-AZ for now, but document that failover is not automatic.
3. Keep Valkey single-node for now, but treat node loss as a session/cache outage scenario.
4. Do not run restore rehearsal until the user explicitly approves the temporary RDS cost.
