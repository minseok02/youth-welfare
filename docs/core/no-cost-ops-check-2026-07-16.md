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

Policy error report triage:

- the `5` open reports were all `system-region-audit` `REGION_MISMATCH` rows.
- all were false positives from broad text matching of region stems inside ordinary words:
  - `예산 범위` -> `예산군`
  - `영주권자` -> `영주시`
  - `고령층` -> `고령군`
  - `역량강화` -> `강화군`
  - `참여수당` -> `여수시`
- `RegionCodeUtil.inferRegionNamesFromText()` was changed to stop using suffix-less SGG stem matching for broad policy text.
- the 5 existing false-positive reports were marked `REVIEWED` with reviewer `ops-codex`.
- follow-up DB audit showed `policy_error_reports_open=0`.

User alert triage:

- the `1` unread user alert is a real user-facing `RECOMMENDATION_DIGEST` notification created on `2026-07-13`.
- it should not be marked read by ops; it remains a normal user state, not an operator backlog.

CloudWatch samples:

- RDS `DatabaseConnections`: about `26-27`
- ElastiCache `DatabaseMemoryUsagePercentage`: about `1.75%`
- ElastiCache `Evictions`: `0`
- ElastiCache `CurrConnections`: `9`

Interpretation:

- No DB/Redis cost change is justified from this read-only check.
- Backlog items were operational triage work, not infrastructure scaling signals.

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

One-command read-only closeout:

```bash
bash deploy/ops/run-no-cost-ops-check.sh
```

Run this on the primary node. The wrapper expects the primary-only log alert cron to be installed.

This wrapper checks:

- local actuator
- post-deploy smoke with ALB target health
- DB audit core values
- log alert status
- app/ALB security group shape
- EC2 public-IP direct 80/443 blocking
- cron/watchdog installation and recent status

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

Observed after DB triage closeout:

- policy error reports open: `0`
- notification failed-like: `0`
- collect active locks: `0`
- user unread alert remains `1` and is intentionally left untouched
- `RegionCodeUtil` false-positive prevention was deployed to both EC2 app nodes at commit `a527e567`
- primary and secondary local actuator returned `UP`
- both app containers were `healthy`
- ALB target health reported `2` healthy targets
- post-deploy smoke passed after the rolling deploy
- deploy-window nginx summary had `user_5xx=0`, `alb_health_5xx=2`, `probe_5xx=1`
- log alert was `warning` only because a single deploy-window `GET /api/policies/ranking` sample took `1846ms`; app errors and user-facing 5xx were `0`
- log alert evaluator now requires at least `3` interactive API samples before p95 latency is promoted to warning/critical; smaller samples remain `observation=`
- follow-up log alert returned `LOG_ALERT_STATUS=ok`; deploy-window `/alb-health` 5xx and scanner probe 5xx remained `observation=`
- admin dashboard policy error report API matched DB audit: `openCount=0`, `recentOpenCount24h=0`, `recentReportsCount=0`
- admin dashboard summary matched notification backlog: `notificationUnreadAlerts=1`, `notificationRetryableFailed=0`
- bounded region audit rerun with `limit=100` returned `scannedCount=100`, `candidateCount=0`, `createdReportCount=0`, `skippedExistingReportCount=0`
- app log baseline parser was tightened so `policy error reports` in an INFO message is not counted as a raw app error
- admin dashboard/API paths are excluded from user-facing interactive p95 alerting and retained as `excluded_latency=`

Observed in final no-cost closeout wrapper:

- command: `bash deploy/ops/run-no-cost-ops-check.sh`
- committed revision: `24a953ea`
- result: `ok_count=9`, `fail_count=0`
- local actuator: `UP`
- post-deploy smoke: passed
- ALB target health: `2` healthy targets
- public policy list/search/ranking: `200`, non-empty data
- nginx recent user-facing 5xx: `0`
- DB audit core:
  - `policy_error_reports_open=0`
  - `notification_failed_like=0`
  - `collect_locks_active=0`
  - `waiting_locks=0`
  - `active_queries_over_5m=0`
  - current DB connections: `23`
  - DB size: `553 MB`
- log alert: `LOG_ALERT_STATUS=ok`
- EC2 public-IP direct access:
  - primary `80/443`: blocked
  - secondary `80/443`: blocked
- cron:
  - primary cron service active
  - log alert cron installed
  - app watchdog cron installed
  - latest log alert status lines were `ok`
  - latest watchdog lines were `health=UP`
- secondary:
  - synced to revision `24a953ea` by SSM `git pull --ff-only`
  - cron service active
  - app watchdog cron installed
  - runtime disk cleanup cron installed
  - latest watchdog lines were `health=UP`
  - actuator returned `UP`

## Next No-Cost Items

Recommended order:

1. Use `bash deploy/ops/run-no-cost-ops-check.sh` for future no-cost closeouts.
2. Keep RDS restore rehearsal and HA upgrades in the cost-approval path, not in no-cost maintenance.
