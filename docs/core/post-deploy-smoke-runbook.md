# Post Deploy Smoke Runbook

작성 기준일: 2026-07-16

## Purpose

Provide a short, repeatable smoke set to run after deploy or operational changes without mutating user data.

This is intentionally smaller than `run-prod-runtime-smoke-suite.sh`. The runtime suite verifies stateful auth/session behavior. This post-deploy smoke only checks that the deployed edge/runtime path is healthy enough to keep serving traffic.

## Command

```bash
bash deploy/smoke/run-prod-post-deploy-smoke.sh
```

Default checks:

| Check | Failure condition |
| --- | --- |
| local actuator | `http://127.0.0.1:8082/actuator/health` is not `UP` |
| ALB target health | fewer than `2` healthy targets or any unhealthy target when AWS CLI is available or `RUN_ALB_TARGET_HEALTH=true` |
| public policy list | non-`200`, `success != true`, or empty data |
| public policy search | non-`200`, `success != true`, or empty data |
| public policy ranking | non-`200`, `success != true`, or empty data |
| recent nginx 5xx | any `/api/*` 5xx or `GET`/`HEAD` page 5xx in the sampled tail |

Artifacts:

```text
tmp/prod-post-deploy-smoke/<UTC timestamp>/
```

Summary file:

```text
prod-post-deploy-smoke-summary.txt
```

## Overrides

```bash
PUBLIC_BASE_URL='https://youthmoa.kr' \
APP_HEALTH_URL='http://127.0.0.1:8082/actuator/health' \
EXPECTED_ALB_HEALTHY_TARGETS=2 \
NGINX_TAIL_LINES=4000 \
bash deploy/smoke/run-prod-post-deploy-smoke.sh
```

ALB target health defaults to `auto`: run it when AWS CLI is available, otherwise mark it as skipped.
Require it explicitly when running from the primary ops node:

```bash
RUN_ALB_TARGET_HEALTH=true bash deploy/smoke/run-prod-post-deploy-smoke.sh
```

Skip ALB target health explicitly only when running without AWS credentials:

```bash
RUN_ALB_TARGET_HEALTH=false bash deploy/smoke/run-prod-post-deploy-smoke.sh
```

Skip nginx 5xx parsing only when nginx logs are unavailable:

```bash
RUN_NGINX_5XX_CHECK=false bash deploy/smoke/run-prod-post-deploy-smoke.sh
```

## Interpretation

Pass means:

- local app health is up
- ALB has both expected healthy targets, unless the summary explicitly says the target-health step was skipped
- public read-only API endpoints return valid non-empty responses
- recent sampled nginx logs have no API/page 5xx. Non-GET/HEAD scanner probes such as `POST /` are counted as `probe_5xx` but do not fail this smoke.

Pass does not prove:

- login/signup/session mutation flows
- admin dashboard write paths
- DB migration correctness
- long soak stability

For those, run:

```bash
bash deploy/smoke/run-prod-runtime-smoke-suite.sh
```

## When To Run

Run after:

- app deploy/rebuild
- nginx reload/config change
- ALB target registration/deregistration
- Route53/ACM/edge changes
- instance stop/start or RDS/Redis restart

Also run before opening another performance optimization after an operational change.
