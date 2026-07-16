# No-Cost Ops Check 2026-07-16

## Purpose

Record the checks and fixes that do not add AWS cost after the ALB two-node, ElastiCache, alerting, and performance work.

This pass intentionally avoided:

- RDS restore rehearsal
- RDS Multi-AZ
- ElastiCache Multi-AZ/failover
- ElastiCache snapshot retention
- Route53 cutover changes
- Docker volume pruning

## Production Shape During Check

| Area | Observed state |
| --- | --- |
| public DNS | `youthmoa.kr` and `www.youthmoa.kr` Route53 alias to the ALB |
| ALB | internet-facing ALB, HTTP `:80` redirects, HTTPS `:443` forwards |
| ALB targets | `2` healthy EC2 targets |
| EC2 app nodes | primary `172.31.25.51`, secondary `172.31.44.73` |
| app compose | `docker-compose.prod.elasticache.yml` |
| database | RDS PostgreSQL `16.14`, single-AZ |
| cache | single-node ElastiCache Valkey |
| scheduler | primary enabled, secondary disabled |

## Cost-Free Fixes Applied

### Compose Default Drift

Several live helper defaults still pointed at `docker-compose.prod.yml`, even though current production runs through ElastiCache and uses `docker-compose.prod.elasticache.yml`.

Changed defaults:

- `deploy/ops/app-watchdog.sh`
- `deploy/performance/run-local-app-log-observability-baseline.sh`
- `deploy/performance/verify-operational-log-retention.sh`

Reason:

- Cron already passed `COMPOSE_FILE=docker-compose.prod.elasticache.yml`, but manual/default helper execution should not fall back to the old local-Redis compose path.

### Scanner Noise Classification

Observed noise:

- app alert initially saw repeated `401/A006` from unauthenticated scanner-style API probes.
- nginx saw one `501 POST /`, also scanner-style.

Changed behavior:

- `A006` remains visible as `observation=excluded errorCode A006 repeated ...`, but does not make the log alert critical by itself.
- nginx log baseline now classifies 5xx into `alb_health_5xx`, `user_5xx`, and `probe_5xx`.
- log alert warning/critical only uses user-facing nginx 5xx.
- post-deploy smoke fails on `/api/*` 5xx or `GET`/`HEAD` page 5xx, while non-GET/HEAD root probes such as `POST /` are counted as `probe_5xx`.

Reason:

- Scanner probes should stay observable, but they should not page or fail deploy closeout unless they hit user-facing API/page paths.

## Cron State

Primary:

- app watchdog every 2 minutes
- Docker prune threshold cleanup
- log alert every 5 minutes
- nightly ops handoff
- weekly frontend observation
- runtime disk cleanup
- cron service active

Secondary:

- app watchdog every 2 minutes
- Docker prune threshold cleanup
- runtime disk cleanup
- cron service active

Intentional difference:

- log alert, nightly handoff, and scheduler-like operational jobs remain primary-only to avoid duplicate notifications and duplicate scheduled work.

## Read-Only Data Layer Check

Command:

```bash
ENV_FILE=.env.production bash deploy/postgres/audit-operational-db-state.sh
```

Result:

- passed
- table presence checks passed
- `auth_without_users=0`
- `profiles_without_users=0`
- `pii_without_users=0`
- `users_without_auth=0`
- `active_users_without_pii=0`
- orphan chat snapshot checks passed
- `user_pii_sync_pending_or_failed=0`
- active queries over 5 minutes: `0`
- waiting locks: `0`
- idle-in-transaction over 5 minutes: `0`
- current DB connections: `27` of max `191`
- DB size: about `553 MB`

Operational backlog notes:

- unread user alerts: `1`
- open policy error reports: `5`
- active collect locks: `0`

CloudWatch samples:

- RDS `DatabaseConnections`: about `26-27`
- ElastiCache `DatabaseMemoryUsagePercentage`: about `1.75%`
- ElastiCache `Evictions`: `0`
- ElastiCache `CurrConnections`: `9`

Interpretation:

- No DB/Redis cost change is justified from this read-only check.
- Backlog items are operational triage work, not infrastructure scaling signals.

## Edge And Security Header Check

Observed:

- HTTP returns `301` redirect to HTTPS from the ALB.
- HTTPS returns `200` through nginx.
- HTTPS response includes HSTS, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, and CSP headers.
- Route53 root and `www` records both alias to the ALB.
- Primary and secondary EC2 instances are running and share the app security group.

Security group detailed rule inspection:

- initially blocked from the ops node because the live IAM role did not allow `ec2:DescribeSecurityGroups`.
- repository policy was updated to add that read-only action.
- live IAM policy was then updated and security group detail reads succeeded.

Security group finding:

- EC2 app security group `sg-01b664b3af6ad95a2` had direct public `80` and `443` ingress.
- That allowed EC2 public-IP access to bypass the ALB.

Fix applied:

- removed EC2 app SG `80` ingress from `0.0.0.0/0`
- removed EC2 app SG `443` ingress from `0.0.0.0/0`
- kept EC2 app SG `80` ingress from ALB SG `sg-06ac84b1d37d48409`
- kept SSH `22` ingress from the existing explicit operator IP and AWS prefix list

Observed after fix:

- primary direct `http://3.38.21.132/`: blocked by timeout
- primary direct `https://3.38.21.132/`: blocked by timeout
- secondary direct `http://52.79.186.92/`: blocked by timeout
- secondary direct `https://52.79.186.92/`: blocked by timeout
- public `https://youthmoa.kr/`: `200`
- public `/api/policies?page=0&size=1`: `200`
- ALB target health: `2` healthy targets

## IAM Drift

Live role:

- `arn:aws:iam::857721769929:role/youth-welfare-ops-monitor-v2-role`

Repository policy:

- `deploy/ops/aws-ops-monitor-role-policy.json`

Confirmed drift:

```diff
 "Action": [
-  "ec2:DescribeInstances"
+  "ec2:DescribeInstances",
+  "ec2:DescribeSecurityGroups"
 ]
```

Initial effect:

- ALB target health, Route53 records, RDS metadata, ElastiCache metadata, CloudWatch metrics, SNS test publish, and SSM commands are available.
- EC2 security group rule details were not available from the ops role until the IAM inline/customer policy was updated in AWS.

Decision:

- IAM update is complete.
- Security group drift audit can now run from the ops node.

## Verification Commands

Use these after this change is deployed/synced:

```bash
bash -n deploy/ops/app-watchdog.sh
bash -n deploy/performance/run-local-app-log-observability-baseline.sh
bash -n deploy/performance/run-local-nginx-log-observability-baseline.sh
bash -n deploy/performance/evaluate-log-alert-thresholds.sh
bash -n deploy/performance/verify-operational-log-retention.sh
bash -n deploy/smoke/run-prod-post-deploy-smoke.sh
python3 -m json.tool deploy/ops/aws-ops-monitor-role-policy.json >/dev/null
LOG_ALERT_NOTIFY_OK=false bash deploy/ops/send-log-alert.sh
RUN_ALB_TARGET_HEALTH=true bash deploy/smoke/run-prod-post-deploy-smoke.sh
```

Expected:

- script syntax checks pass
- IAM policy JSON parses
- log alert does not become critical for scanner-only `A006` or non-user nginx probe 5xx
- post-deploy smoke passes with `2` healthy ALB targets

Observed on primary after the change:

- script syntax checks passed
- IAM policy JSON parsed
- `git diff --check` passed
- `LOG_ALERT_STATUS=ok`
- log alert kept scanner `POST /` 501 as `observation=nginx probe 5xx count 1`
- post-deploy smoke passed
- ALB target health reported `2` healthy targets
- public policy list/search/ranking returned `200` with non-empty data
- nginx recent 5xx summary: `user_5xx=0`, `alb_health_5xx=0`, `probe_5xx=1`

Observed after IAM and security group closeout:

- `ec2:DescribeSecurityGroups` succeeded from the ops node
- EC2 app SG allows app HTTP only from the ALB SG
- EC2 public IP direct 80/443 access is blocked on both nodes
- `https://youthmoa.kr` remains healthy
- post-deploy smoke passed with `2` healthy ALB targets

## Next No-Cost Items

Recommended order:

1. Re-run log alert after a few normal traffic windows and confirm scanner observations do not create alert fatigue.
2. Review the `1` unread user alert and `5` open policy error reports from the DB audit.
3. Keep RDS restore rehearsal and HA upgrades in the cost-approval path, not in no-cost maintenance.
