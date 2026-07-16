# Ops Readiness Check 2026-07-16

## Purpose

Verify that the operational guardrails created during the performance and HA work are actually installed and healthy.

## AWS Alarm State

ap-northeast-2 CloudWatch alarms:

| Alarm | Metric | State |
| --- | --- | --- |
| `youth-welfare-ec2-status-check-failed` | `AWS/EC2 StatusCheckFailed` | `OK` |
| `youth-welfare-elasticache-current-connections-high` | `AWS/ElastiCache CurrConnections` | `OK` |
| `youth-welfare-elasticache-engine-cpu-high` | `AWS/ElastiCache EngineCPUUtilization` | `OK` |
| `youth-welfare-elasticache-evictions-detected` | `AWS/ElastiCache Evictions` | `OK` |
| `youth-welfare-elasticache-freeable-memory-low` | `AWS/ElastiCache FreeableMemory` | `OK` |
| `youth-welfare-elasticache-memory-usage-high` | `AWS/ElastiCache DatabaseMemoryUsagePercentage` | `OK` |
| `youth-welfare-elasticache-new-connections-high` | `AWS/ElastiCache NewConnections` | `OK` |

us-east-1 CloudWatch alarms:

| Alarm | Metric | State |
| --- | --- | --- |
| `youth-welfare-route53-uptime-unhealthy` | `AWS/Route53 HealthCheckStatus` | `OK` |
| `youth-welfare-billing-estimated-charges-5usd` | `AWS/Billing EstimatedCharges` | `OK` |

SNS:

| Region | Topic | Subscription |
| --- | --- | --- |
| `ap-northeast-2` | `youth-welfare-ops-alerts` | email subscription confirmed |
| `us-east-1` | `youth-welfare-ops-alerts` | email subscription confirmed |

Route53 health check:

| Id | Domain | Path | Type | Observation |
| --- | --- | --- | --- | --- |
| `5645ef15-90f4-4b7d-899f-52879da4f61d` | `youthmoa.kr` | `/` | `HTTPS` | all sampled checker regions returned `200 OK` |

Runtime service state:

| Service | State |
| --- | --- |
| RDS `youth-welfare-prod-db` | `available`, PostgreSQL `16.14`, `db.t3.small`, `20 GiB`, Multi-AZ `false` |
| ElastiCache `youth-welfare-prod-redis-valkey` | `available`, Valkey, single member, automatic failover `disabled` |

## Node Drift Check

| Check | Primary | Secondary | Result |
| --- | --- | --- | --- |
| repo commit during drift check | `8abcdc93` | `8abcdc93` after sync | aligned |
| scheduler | `APP_SCHEDULER_ENABLED=true` | `APP_SCHEDULER_ENABLED=false` | intended |
| ranking snapshot read | `true` | `true` | aligned |
| ranking snapshot refresh | `true` | `false` | intended |
| app health | `UP` | `UP` | healthy |
| app container | `healthy` | `healthy` | healthy |
| root disk | `45%` used | `59%` used | below cleanup threshold |
| runtime disk cleanup cron | installed | installed | aligned |
| app watchdog cron | installed | installed | aligned |
| watchdog compose file | `docker-compose.prod.elasticache.yml` | `docker-compose.prod.elasticache.yml` | aligned |

Finding:

- primary watchdog cron had still referenced `docker-compose.prod.yml`.
- secondary did not have the basic watchdog cron.
- secondary also needed `/var/log/youth-welfare/ops` ownership for the `ubuntu` watchdog process.

Fix:

- `deploy/ops/install-basic-ops-cron.sh` default compose file changed to `docker-compose.prod.elasticache.yml`.
- primary basic ops cron reinstalled.
- secondary fast-forwarded, basic ops cron installed, `/var/log/youth-welfare/ops` created and owned by `ubuntu`.
- primary and secondary watchdog manual runs returned `health=UP`.

## Smoke Command Policy

Use [operations-smoke-matrix.md](./operations-smoke-matrix.md).

Default deploy closeout:

```bash
RUN_ALB_TARGET_HEALTH=true bash deploy/smoke/run-prod-post-deploy-smoke.sh
```

Stateful runtime closeout:

```bash
bash deploy/smoke/run-prod-runtime-smoke-suite.sh
```

Daily read-only observation:

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-ops-observation-suite.sh
```

## Decision

Operational guardrails are in place and currently healthy.

No additional performance optimization should start before the next change runs at least the post-deploy smoke and confirms ALB target health.

Final verification:

- `RUN_ALB_TARGET_HEALTH=true bash deploy/smoke/run-prod-post-deploy-smoke.sh` passed.
- ALB target group reported both targets `healthy`.
