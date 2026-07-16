# Final Production Operations Runbook 2026-07-16

## Purpose

This is the final handoff runbook for the current production deployment.

Use it when an operator needs to confirm that the service is still in the intended final state after the ALB two-node switch, no-cost operations cleanup, alerting checks, performance measurements, and final functional regression.

For a shorter daily entrypoint, start with [production-ops-quickstart.md](./production-ops-quickstart.md).

## Final Known Good State

| Area | Current state |
| --- | --- |
| branch | `refactor/admin-dashboard-sections` |
| final functional regression app revision | `f852c8a5` |
| final regression documentation sync revision | `318be677` before this handoff runbook |
| public domains | `youthmoa.kr`, `www.youthmoa.kr` |
| public DNS | Route53 public hosted zone A alias records to `dualstack.youth-welfare-alb-1121910292.ap-northeast-2.elb.amazonaws.com.` |
| public path | Route53 -> internet-facing ALB -> EC2 app targets -> nginx -> Spring Boot |
| app nodes | primary `i-0b8d95e454df5e0f0`, secondary `i-0e8a4cc599c1148c8` |
| ALB target state | `2` healthy targets in final checks |
| scheduler | primary enabled, secondary disabled |
| database | RDS PostgreSQL `youth-welfare-prod-db`, single-AZ, encrypted, deletion protection enabled |
| cache | ElastiCache Valkey `youth-welfare-prod-redis-valkey`, single member |
| public EC2 bypass | direct public EC2 `80/443` blocked; app HTTP allowed from ALB security group |
| alerting | CloudWatch alarms, Route53 health check, SNS email subscription confirmed |
| final functional regression | `18/18` steps passed, `0` errors, `0` rate limits |
| final load view | clean same-source public mixed read point about `2.4 rps`; first 429 boundary about `4.8 rps` |

## Handoff Verification

Final post-documentation smoke:

- command: `RUN_ALB_TARGET_HEALTH=true bash deploy/smoke/run-prod-post-deploy-smoke.sh`
- artifact: `tmp/prod-post-deploy-smoke/20260716T180412Z`
- local actuator: passed
- ALB target health: `2` healthy targets
- public policy list/search/ranking: HTTP `200`, count `5`
- nginx recent 5xx: `user_5xx=0`, `alb_health_5xx=0`, `probe_5xx=0`
- decision: `prod_post_deploy_smoke=passed`

## Console Checks

### Route53

Open AWS Console -> Route 53 -> Hosted zones -> `youthmoa.kr`.

Expected public hosted zone records:

| Record | Type | Expected target |
| --- | --- | --- |
| `youthmoa.kr` | A alias | `dualstack.youth-welfare-alb-1121910292.ap-northeast-2.elb.amazonaws.com.` |
| `www.youthmoa.kr` | A alias | `dualstack.youth-welfare-alb-1121910292.ap-northeast-2.elb.amazonaws.com.` |
| `youthmoa.kr` | NS/SOA | unchanged public hosted zone authority records |
| ACM validation CNAMEs | CNAME | keep the apex and `www` validation CNAMEs |
| SES DKIM CNAMEs | CNAME | keep existing DKIM records |

Do not create or change a private hosted zone for the public service path. The current public service uses the public hosted zone alias records above.

### EC2 / ALB

Open AWS Console -> EC2 -> Load Balancers.

Expected:

- load balancer is internet-facing and active
- listeners match the current edge setup: HTTP redirects to HTTPS and HTTPS forwards to the app target group
- certificate is the ACM certificate for `youthmoa.kr` / `www.youthmoa.kr`
- target group shows both app instances as `healthy`

Open EC2 -> Target Groups -> the youth-welfare target group.

If only one target is healthy:

1. Check the unhealthy reason in the target group.
2. Use SSM to check local actuator on that instance.
3. Check nginx and app container health on that instance.
4. Do not change Route53 first. The DNS alias should keep pointing to the ALB.

### EC2 Security Group

Expected app instance ingress:

- app HTTP target traffic is allowed from the ALB security group
- direct public `80` and `443` are not open to `0.0.0.0/0`
- SSH remains limited to the existing operator path

If public EC2 IPs answer directly on `80/443`, that is a regression because it bypasses ALB, HTTPS certificate handling, and ALB target health.

### IAM Role

Current ops role:

```text
arn:aws:iam::857721769929:role/youth-welfare-ops-monitor-v2-role
```

Attached policy set recorded during closeout:

- `AmazonSSMManagedInstanceCore`
- `CloudWatchAgentServerPolicy`
- `youth-welfare-ops-monitor-inline`
- `youth-welfare-ops-monitor-v2-rolePolicy`

The role should allow only the operational surface needed by the scripts:

- CloudWatch alarm describe/update for `youth-welfare-*`
- SNS topic read/publish/subscribe operations for alert tests
- Route53 health check and DNS record reads/controlled changes
- ELB load balancer, listener, target group, and target health reads
- SSM command send/read for the app instances
- EC2 instance and security group reads
- RDS and ElastiCache metadata reads

Use the same role on both app instances if both nodes must run the same ops scripts. Do not replace it with broad administrator access.

If SSM stops working, check in this order:

1. Instance has the expected instance profile.
2. `AmazonSSMManagedInstanceCore` is still attached.
3. SSM Agent is running.
4. The instance has outbound HTTPS path to AWS Systems Manager endpoints.
5. The command is sent in `ap-northeast-2`.

### CloudWatch / SNS / Route53 Health Check

Expected:

- ap-northeast-2 CloudWatch alarms for EC2 and ElastiCache are `OK`
- us-east-1 Route53 health check alarm is `OK`
- us-east-1 billing alarm is `OK`
- `youth-welfare-ops-alerts` SNS topic has a confirmed email subscription
- SNS test notification has been received

Do not click an SNS unsubscribe link unless the operator intentionally wants to stop receiving alerts.

## Terminal Checks

Run from the primary node unless noted.

### Fast Daily Check

```bash
cd /home/ubuntu/youth-welfare
git status --short --branch
curl -fsS http://127.0.0.1:8082/actuator/health
RUN_ALB_TARGET_HEALTH=true bash deploy/smoke/run-prod-post-deploy-smoke.sh
```

Expected:

- branch is the expected production branch
- actuator returns `UP`
- ALB target health reports `2` healthy targets
- public policy list/search/ranking return `200`
- recent user-facing nginx 5xx is `0`

### No-Cost Ops Closeout

```bash
cd /home/ubuntu/youth-welfare
bash deploy/ops/run-no-cost-ops-check.sh
```

This checks local actuator, post-deploy smoke, DB audit core values, log alert status, security group shape, direct EC2 public access blocking, cron/watchdog state, and secondary health through SSM.

Expected:

- `fail_count=0`
- ALB target health `2`
- direct EC2 public `80/443` blocked
- DB waiting locks `0`
- active DB queries over 5 minutes `0`
- log alert `ok`
- primary and secondary watchdog latest lines show `health=UP`

### Secondary Instance Revision And Health

```bash
aws ssm send-command \
  --region ap-northeast-2 \
  --instance-ids i-0e8a4cc599c1148c8 \
  --document-name AWS-RunShellScript \
  --parameters '{"commands":["sudo -u ubuntu bash -lc '\''cd /home/ubuntu/youth-welfare && git rev-parse --short HEAD && git status --short --branch && curl -fsS http://127.0.0.1:8082/actuator/health'\''"]}' \
  --query 'Command.CommandId' \
  --output text
```

Then poll:

```bash
aws ssm get-command-invocation \
  --region ap-northeast-2 \
  --command-id '<command-id>' \
  --instance-id i-0e8a4cc599c1148c8 \
  --query '{Status:Status,Stdout:StandardOutputContent,Stderr:StandardErrorContent}' \
  --output json
```

Expected:

- command status `Success`
- revision matches the primary revision
- git status is clean against origin
- actuator returns `UP`

### Log Alert Check

```bash
cd /home/ubuntu/youth-welfare
LOG_ALERT_NOTIFY_OK=false bash deploy/ops/send-log-alert.sh
```

Expected:

- `LOG_ALERT_STATUS=ok`
- scanner-like unauthenticated probes and non-user nginx probe 5xx are retained as observations, not page-worthy user failures
- AI-backed recommendation/chat latency is not mixed into ordinary API p95 warnings

### Functional Regression

Use this only when stateful user behavior must be revalidated. It creates bounded test data and uses OpenAI-backed recommendation/chat paths.

```bash
cd /home/ubuntu/youth-welfare
ENV_FILE=.env.runtime.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL=https://youthmoa.kr \
APP_HEALTH_URL=http://127.0.0.1:8082/actuator/health \
INTEGRATED_JOURNEY_ROOT=tmp/performance/final-functional-regression \
JOURNEY_REQUEST_DELAY_SECONDS=0.5 \
  bash deploy/performance/run-local-integrated-user-journey-baseline.sh
```

Expected based on the final accepted run:

- signup/login/refresh/logout succeed
- profile read and priority update succeed
- policy list/search/detail return expected payloads
- recommendation refresh and stored read return recommendations
- bookmark on/off state is reflected correctly
- chat answer is `POLICY_GROUNDED` with at least one reference
- no ALB, DB, JVM, or log-alert regression appears after the flow

## Cost And Risk Boundaries

Safe to run repeatedly with no meaningful extra AWS cost:

- post-deploy smoke
- no-cost ops check
- local actuator checks
- ALB target health reads
- Route53 record reads
- DB read-only lock/long-query audit
- JVM runtime baseline
- SSM health/revision commands
- log alert check with notification disabled

Possible cost or operational risk:

- Route53 health checks and CloudWatch alarms if creating new ones
- SNS publish if sending live notifications
- OpenAI-backed recommendation/chat checks
- long or high-rate load tests
- distributed load tests from multiple IPs or regions
- RDS restore rehearsal
- RDS Multi-AZ
- ElastiCache failover/Multi-AZ or snapshot retention changes

Do not run a high-rate or distributed load test just because the site has few users. The current production stack has rate limits, shared paid resources, and OpenAI-backed paths. Treat heavy tests as explicit experiments with a stop condition.

## Troubleshooting Order

### Public Site Down

1. Route53 alias still points to the ALB.
2. ALB listener and certificate are active.
3. Target group has `2` healthy targets.
4. Local actuator is `UP` on each instance.
5. nginx config is loaded and serving.
6. app container is healthy.
7. DB and Valkey are reachable.
8. recent nginx user-facing 5xx and app errors explain the failure.

### One Target Unhealthy

Use SSM on the unhealthy instance:

```bash
curl -fsS http://127.0.0.1:8082/actuator/health
docker ps
docker compose -f docker-compose.prod.elasticache.yml ps
```

Then check the target group reason. Do not remove the healthy target while debugging unless doing a controlled rollback.

### 429 During Tests

429 during same-source load tests is the configured rate-limit boundary. It is not by itself proof of CPU, DB, or ALB saturation.

Use post-test evidence before calling it saturation:

- ALB target health
- user-facing 5xx
- JVM restart count
- DB waiting locks
- active queries over 5 minutes
- container CPU/memory

### Recommendation Or Chat Slow

Recommendation refresh and chatbot message send include OpenAI-backed work. Compare them against AI-specific measurements, not ordinary read API p95.

Stored recommendation reads should remain fast. If stored reads become slow, investigate DB/query path separately from generation latency.

### SNS Alert Missing

1. Confirm the SNS subscription is still confirmed.
2. Confirm alarm actions are enabled.
3. Confirm the alarm is in the expected region.
4. Send a controlled test publish only if the operator expects an email.
5. Do not unsubscribe from the email unless intentionally removing the recipient.

## Source Documents

- [production-ops-quickstart.md](./production-ops-quickstart.md)
- [operations-smoke-matrix.md](./operations-smoke-matrix.md)
- [post-deploy-smoke-runbook.md](./post-deploy-smoke-runbook.md)
- [ops-readiness-check-2026-07-16.md](./ops-readiness-check-2026-07-16.md)
- [no-cost-ops-check-2026-07-16.md](./no-cost-ops-check-2026-07-16.md)
- [incident-first-five-minutes-runbook.md](./incident-first-five-minutes-runbook.md)
- [data-layer-risk-and-backup-check-2026-07-16.md](./data-layer-risk-and-backup-check-2026-07-16.md)
- [cost-scaling-decision-table.md](./cost-scaling-decision-table.md)
- [../performance/final-load-capacity-check-2026-07-16.md](../performance/final-load-capacity-check-2026-07-16.md)
- [../performance/final-functional-regression-check-2026-07-16.md](../performance/final-functional-regression-check-2026-07-16.md)
- [../performance/final-report-performance-summary-2026-07-16.md](../performance/final-report-performance-summary-2026-07-16.md)
