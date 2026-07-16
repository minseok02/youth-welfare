# Production Ops Quickstart

작성 기준일: 2026-07-16

## Purpose

Give the normal operator a short entrypoint after the HA, performance, alerting, backup, and runbook cleanup work.

Use this page first. Open the deeper runbooks only when the situation matches.

## Normal Daily Check

```bash
cd /home/ubuntu/youth-welfare
git status --short --branch
curl -fsS http://127.0.0.1:8082/actuator/health
RUN_ALB_TARGET_HEALTH=true bash deploy/smoke/run-prod-post-deploy-smoke.sh
```

Expected:

- git branch is the expected production branch
- local actuator returns `UP`
- ALB has `2` healthy targets
- public policy list/search/ranking return `200`
- nginx recent user-path 5xx is `0`

## Use These First

| Need | Open |
| --- | --- |
| choose which smoke to run | [operations-smoke-matrix.md](./operations-smoke-matrix.md) |
| first 5 minutes of an incident | [incident-first-five-minutes-runbook.md](./incident-first-five-minutes-runbook.md) |
| decide whether `/alb-health` 502 matters | [alb-health-502-runbook.md](./alb-health-502-runbook.md) |
| disk cleanup policy | [runtime-disk-cleanup-runbook.md](./runtime-disk-cleanup-runbook.md) |
| data-layer backup/restore status | [data-layer-risk-and-backup-check-2026-07-16.md](./data-layer-risk-and-backup-check-2026-07-16.md) |
| latest no-cost ops checks | [no-cost-ops-check-2026-07-16.md](./no-cost-ops-check-2026-07-16.md) |
| cost-increasing HA decision | [cost-scaling-decision-table.md](./cost-scaling-decision-table.md) |

## Current Production Shape

| Layer | Current state |
| --- | --- |
| public entrypoint | Route53 -> ALB -> EC2 targets |
| web nodes | primary + secondary, both ALB healthy |
| scheduler | primary enabled, secondary disabled |
| database | RDS PostgreSQL single-AZ, backups/PITR metadata verified |
| Redis/Valkey | single-node ElastiCache, no snapshot retention |
| alerting | CloudWatch/Route53/SNS configured; SNS publish and mailbox receipt confirmed |
| watchdog | installed on primary and secondary |
| disk cleanup | weekly threshold-gated cleanup installed on primary and secondary |

## Still Pending Human Confirmation

| Item | Status | Owner |
| --- | --- | --- |
| restore rehearsal execution | prepared, blocked until temporary RDS cost is approved | human |
| ops role security group read | repository policy updated; live IAM policy still needs `ec2:DescribeSecurityGroups` | human/operator |
| RDS Multi-AZ | decision only, not enabled | human |
| Valkey failover/snapshot retention | decision only, not enabled | human |
| webhook alert channel | not configured | human/operator |

## Do Not Do Without Approval

- Do not run RDS restore rehearsal.
- Do not enable RDS Multi-AZ.
- Do not enable Valkey failover/Multi-AZ.
- Do not enable Valkey snapshot retention.
- Do not prune Docker volumes.
- Do not change Route53 records unless following the ALB/demo switch runbook.

## Closeout Rule

Before starting another optimization or infrastructure change, run:

```bash
RUN_ALB_TARGET_HEALTH=true bash deploy/smoke/run-prod-post-deploy-smoke.sh
```

If it fails, stop and use [incident-first-five-minutes-runbook.md](./incident-first-five-minutes-runbook.md).
