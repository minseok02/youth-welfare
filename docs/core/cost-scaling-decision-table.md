# Cost And Scaling Decision Table

작성 기준일: 2026-07-16

## Purpose

Separate cost-saving decisions from reliability upgrades.

This project currently runs a small production stack. Some upgrades improve availability but immediately increase monthly cost. Do not enable them just because they exist; enable them when the signal justifies the cost.

## Current Baseline

| Layer | Current shape | Cost/reliability reading |
| --- | --- | --- |
| EC2 web | `t3.medium` x 2 behind ALB | good for HA demo and single-node failure tolerance |
| RDS | PostgreSQL `db.t3.small`, single-AZ | low cost, downtime possible during AZ/instance issue |
| Valkey | single ElastiCache node | low cost, session/cache disruption on node issue |
| ALB | active public entrypoint | needed for 2-node web HA |
| Route53 health check | enabled | external uptime signal |
| CloudWatch/SNS alarms | enabled | alerting path exists |

## EC2 2 Nodes Vs 1 Node

| Decision | Use when | Do not use when |
| --- | --- | --- |
| keep 2 nodes | demo/HA proof, active operational validation, deploy/restart safety, one-node failure tolerance needed | cost must be minimized and short app downtime is acceptable |
| reduce to 1 node | post-demo low-cost mode, no active HA requirement, traffic is low | validating ALB behavior, doing rolling operational changes, or needing instance failure tolerance |

Before reducing to 1 node:

```bash
RUN_ALB_TARGET_HEALTH=true bash deploy/smoke/run-prod-post-deploy-smoke.sh
```

Then follow [alb-demo-switch-runbook.md](./alb-demo-switch-runbook.md).

## RDS Multi-AZ

| Decision | Trigger |
| --- | --- |
| keep single-AZ | current default; low traffic, cost-sensitive, restore downtime acceptable |
| enable Multi-AZ | real users depend on uptime, RDS outage would be unacceptable, or repeated maintenance/AZ risk becomes operationally expensive |
| run restore rehearsal first | before claiming recovery confidence or before a risky schema/data migration |

Signals to revisit:

- RDS availability incident
- recovery objective becomes tighter than manual restore
- production user traffic increases
- business/demo requirement says DB downtime is unacceptable

## Valkey Failover / Snapshot Retention

| Decision | Trigger |
| --- | --- |
| keep single-node, snapshot retention `0` | Redis remains cache/session/rate-limit support, RDS is canonical, cost-sensitive |
| enable snapshot retention | Redis data becomes important for recovery evidence or long-lived operational state |
| enable failover/Multi-AZ | login/session disruption is unacceptable or Redis outage caused real user impact |

Current reading:

- memory pressure is low
- evictions are `0`
- failover is disabled
- snapshot retention is `0`

This is acceptable only if Redis loss is treated as a temporary session/cache outage, not data loss of record.

## RDS Restore Rehearsal

| Decision | Trigger |
| --- | --- |
| defer | no approval for temporary RDS cost |
| prepare commands only | current state |
| execute rehearsal | user explicitly approves temporary restore instance cost and target identifiers |

Required before execution:

- IAM read-only snapshot inventory policy applied
- target DB identifier chosen
- restore security group chosen
- subnet group chosen
- cleanup date/time agreed

## Alerting Spend

| Decision | Trigger |
| --- | --- |
| keep current CloudWatch/SNS/Route53 | current default |
| add dashboard/APM | incident diagnosis takes too long with logs and smoke scripts |
| add WAF/bot protection | repeated abuse or scraping appears |

## Rule Of Thumb

Upgrade when one of these is true:

- there was a real incident in that layer
- recovery would take longer than the product can tolerate
- repeated manual checks cost more time than the managed feature costs money
- a demo or stakeholder requirement explicitly requires the availability claim

Otherwise, keep the smaller stack and keep the runbooks accurate.
