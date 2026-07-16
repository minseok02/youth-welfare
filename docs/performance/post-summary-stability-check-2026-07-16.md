# Post Summary Projection Stability Check 2026-07-16

## Purpose

After the generated-search and summary-projection performance batches, verify that the deployed production runtime is stable before opening another optimization.

This check intentionally does not change runtime code.

## Current State

- repo HEAD: `12a43f1f484a636aeb0168cedfc9445cde44e12c`
- deployed app/code commit: `3806ecddd9f275b3e251d51d5a8f8d9ec592ae47`
- latest docs/checkpoint commit: `12a43f1f484a636aeb0168cedfc9445cde44e12c`
- ALB target group: `youth-welfare-web-tg`
- targets:
  - primary `i-0b8d95e454df5e0f0`
  - secondary `i-0e8a4cc599c1148c8`

## Runtime Health

| Component | Result |
| --- | --- |
| ALB primary target | `healthy` |
| ALB secondary target | `healthy` |
| primary Docker app | `running healthy` |
| primary actuator | `{"status":"UP"}` |
| secondary Docker app | `running healthy` |
| secondary actuator | `{"status":"UP"}` |
| RDS `youth-welfare-prod-db` | `available`, PostgreSQL `16.14`, `db.t3.small` |
| ElastiCache/Valkey | `available`, replication group `youth-welfare-prod-redis-valkey` |

Resource snapshot:

| Target | App CPU | App memory | Root disk | System memory available |
| --- | ---: | ---: | ---: | ---: |
| primary | `0.19%` | `568.1MiB / 1GiB` | `85%` used | `2443MiB` |
| secondary | `0.17%` | `548MiB / 1GiB` | `59%` used | `2659MiB` |

Operational note:

- primary root disk at `85%` is the only resource warning found in this pass.

## API Baseline

Primary loopback API baseline:

- artifact: `tmp/stability/api-latency-primary-20260716/20260716T070432Z`
- status: `passed`
- samples: `63`
- errors: `0`
- rate limited: `0`

| Scenario | p50 | p95 | max |
| --- | ---: | ---: | ---: |
| policy list default | `32.0ms` | `37.0ms` | `37.6ms` |
| policy list active only | `25.7ms` | `32.6ms` | `32.7ms` |
| policy search keyword | `14.2ms` | `19.7ms` | `21.3ms` |
| policy search filtered | `16.0ms` | `16.4ms` | `16.4ms` |
| policy ranking | `9.2ms` | `10.3ms` | `10.4ms` |
| policy suggestions | `23.9ms` | `25.6ms` | `26.1ms` |
| policy detail first | `36.1ms` | `36.6ms` | `36.7ms` |
| policy trending | `20.3ms` | `67.3ms` | `86.7ms` |

Public ALB API baseline:

- artifact: `tmp/stability/api-latency-alb-20260716/20260716T070432Z`
- status: `passed`
- samples: `56`
- errors: `0`
- rate limited: `0`

| Scenario | p50 | p95 | max |
| --- | ---: | ---: | ---: |
| policy list default | `81.6ms` | `108.0ms` | `117.4ms` |
| policy list active only | `62.9ms` | `68.1ms` | `68.6ms` |
| policy search keyword | `52.6ms` | `55.5ms` | `55.8ms` |
| policy search filtered | `62.6ms` | `130.8ms` | `132.1ms` |
| policy ranking | `45.1ms` | `62.3ms` | `66.7ms` |
| policy suggestions | `59.0ms` | `82.5ms` | `85.3ms` |
| policy detail first | `67.4ms` | `133.4ms` | `158.9ms` |
| policy trending | `54.3ms` | `55.5ms` | `55.8ms` |

Edge baseline:

- artifact: `tmp/stability/edge-20260716/20260716T070432Z`
- status: `passed`
- security headers present:
  - HSTS
  - `X-Frame-Options`
  - `X-Content-Type-Options`
  - CSP
- server version token hidden: `true`

Spot results:

| Scenario | Status | Total |
| --- | ---: | ---: |
| home | `200` | `84.335ms` |
| policies page | `200` | `86.518ms` |
| policy list default | `200` | `229.473ms` |
| policy search keyword | `200` | `230.709ms` |

The edge spot values are single samples and are used only as an external path smoke, not as the accepted p95 baseline.

## Observability

App log observability:

- artifact: `tmp/stability/app-log-20260716/20260716T070559Z`
- status: `passed`
- raw error lines: `0`
- raw warn lines: `0`
- API request count: `162`
- API status counts:
  - `200`: `161`
  - `401`: `1`
- API duration:
  - p50 `32ms`
  - p95 `155ms`
  - p99 `189ms`
  - max `597ms`

Secondary app log tail:

- last 30 minutes warning/error count: `0`
- slow timing tail showed one search sample:
  - repository `97ms`
  - SQL `79ms`
  - service `130ms`
  - controller `142ms`

Primary app log tail after smoke:

- last 30 minutes warning/error count: `0`

NGINX log observability:

- artifact: `tmp/stability/nginx-log-20260716/20260716T070559Z`
- status: `passed`
- request time:
  - p50 `0.000s`
  - p95 `0.041s`
  - p99 `0.101s`
  - max `2.472s`
- sampled status counts:
  - `200`: `3387`
  - `301`: `6`
  - `400`: `7`
  - `401`: `1`
  - `404`: `1`
  - `405`: `4`
  - `502`: `44`

502 interpretation:

- all sampled `502` lines were `GET /alb-health` from `ELB-HealthChecker/2.0`
- recent examples were during deploy/restart windows:
  - `2026-07-16 04:44`-`04:49 UTC`
  - `2026-07-16 05:32 UTC`
  - `2026-07-16 05:59 UTC`
- no sampled `502` was a user API path
- current ALB target health is healthy on both targets

DB observability:

- artifact: `tmp/stability/db-observability-20260716/20260716T070559Z`
- status: `passed`
- active DB backends: `25`
- cache hit rate: `0.995778`
- deadlocks: `0`
- blocked locks: `0`
- long transactions: `0`
- temp files/bytes: `0`
- `pg_stat_statements`: unavailable

Note:

- several large indexes were reported as unused in the current statistics window. Do not drop from this one pass; this is only a stats-window signal.

Redis/Valkey observability:

- artifact: `tmp/stability/redis-observability-20260716/20260716T070559Z`
- status: `passed`
- dbsize: `46`
- hit rate: `0.761694`
- slowlog length: `0`
- latency event lines: `0`
- connected clients: `10`
- blocked clients: `0`
- used memory: `6.74M`
- evicted keys: `0`
- rejected connections: `0`

## Functional Smoke

Production cutover verification:

- artifact: `tmp/stability/prod-cutover-verify-20260716`
- status: `passed`
- runtime env render: passed
- env preflight: passed
- RDS runtime privilege verification: passed
- nginx edge baseline verification: passed

Runtime API smoke:

- artifact: `tmp/stability/runtime-api-smoke-20260716b`
- status: `passed`
- covered:
  - health check
  - signup
  - login
  - refresh
  - recommendation refresh
  - bookmark toggle
  - bookmarks list
  - logout
  - refresh after logout rejected
  - presented/older token rejected after logout
- recommendation count: `39`
- post-logout token checks:
  - `presented_after_logout=401/A006`
  - `older_login_token_after_logout=401/A006`

Manual public ALB read-only smoke:

| Check | Result |
| --- | --- |
| policy list | passed, `200`, `92.4ms`, 20 rows |
| policy list fields | passed |
| policy search `월세` | passed, `200`, `92.1ms`, 20 rows, total 83 |
| policy search fields | passed, top result `익산형 청년월세 지원사업`, region `전북특별자치도 익산시` |
| policy detail for search top | passed, `200`, `130.9ms`, id `858` |
| ranking | passed, `200`, `34.9ms`, 20 rows |
| suggestions | passed, `200`, `33.6ms`, 10 rows |

Skipped/failed-but-replaced check:

- `run-local-policy-search-scenario-audit.sh` against public ALB failed at health precheck because `/` returns frontend HTML, not actuator-style health JSON.
- The same read-only policy list/search/detail/ranking/suggestion checks were performed manually against the public ALB and passed.

## Decision

Current deployment is stable enough to keep running.

Do not open another performance-changing code batch immediately. The next action should be operational cleanup/observation, not query or Java hot-path rewrites.

Recommended follow-ups:

1. Clean or expand primary root disk because `/` is at `85%`.
2. Treat `/alb-health` 502 lines as deploy/restart-window noise while both ALB targets remain healthy.
3. Keep watching app/NGINX logs for real user API 5xx; none were found in this pass.
4. If performance work resumes, start from a broader low-rate ALB baseline instead of a single hot-path assumption.

## Follow-Up: Primary Disk Cleanup

Trigger:

- primary root disk was `85%` used during the stability check.

Pre-cleanup usage:

| Item | Usage |
| --- | ---: |
| `/` | `16G / 19G`, `85%` used, `2.8G` available |
| `/home/ubuntu/youth-welfare/tmp/performance` | `3.5G` |
| `/home/ubuntu/.npm` | `2.1G` |
| Docker build cache | `2.258G` |
| Docker local volumes | `667.8MB`, not cleaned |

Cleanup performed:

- deleted old `tmp/performance` artifacts
- ran `npm cache clean --force`
- ran `docker builder prune -af`

Preserved:

- current `youth-welfare-app` image and running container
- Docker volumes, including the old unused local `postgres_data` volume
- current `tmp/stability` artifacts from this check
- Playwright browser cache

Post-cleanup usage:

| Item | Usage |
| --- | ---: |
| `/` | `8.2G / 19G`, `45%` used, `11G` available |
| `/home/ubuntu/youth-welfare/tmp` | `254M` |
| `/home/ubuntu/.npm` | `53M` |
| Docker build cache | `0B` |

Post-cleanup verification:

- primary Docker app: `running healthy`
- primary actuator: `{"status":"UP"}`
- public search smoke: `POST /api/policies/search` returned `200`, `success=true`, `5` rows
- ALB targets remained healthy:
  - `i-0b8d95e454df5e0f0`
  - `i-0e8a4cc599c1148c8`

Decision:

- primary disk warning is closed for now.
- no app redeploy was required.
- future deploy builds may be slower on the first run because Docker build cache was intentionally cleared.

Follow-up automation:

- added `deploy/ops/cleanup-runtime-disk-artifacts.sh`
- added `deploy/ops/install-runtime-disk-cleanup-cron.sh`
- added [runtime disk cleanup runbook](../core/runtime-disk-cleanup-runbook.md)
- installed primary runtime disk cleanup cron

Policy:

- default script mode is `DRY_RUN=true`
- cron mode is threshold-gated at `70%` root disk usage
- `tmp/performance` children older than `2` days are removable
- `tmp/stability` is preserved by default
- npm cache and Docker builder cache are reproducible and may be cleaned
- Docker volumes are not cleaned

Secondary follow-up check:

| Item | Result |
| --- | ---: |
| `/` | `11G / 19G`, `59%` used, `7.6G` available |
| `/home/ubuntu/youth-welfare/tmp/performance` | `5.8M` |
| `/home/ubuntu/.npm` | `41M` |
| Docker images | `4.382GB` |
| Docker build cache | `5.616GB`, `5.053GB` reclaimable |
| Docker volumes | `0B` |
| app container | `Up 2 hours (healthy)` |
| actuator | `{"status":"UP"}` |

Decision:

- secondary was below the `70%` automatic cleanup threshold, so it was not an immediate disk risk.
- Docker build cache was still large enough to justify syncing the cleanup script and weekly threshold-gated cron to secondary.
- secondary was fast-forwarded to the cleanup automation commit, the cleanup scripts passed `bash -n`, dry-run succeeded, and the weekly cleanup cron was installed under the `ubuntu` crontab.
- no real secondary cleanup was run because root disk usage was below the configured threshold and the app stayed healthy.

## Follow-Up: ALB 502 / Post-Deploy Smoke

Added:

- `deploy/smoke/run-prod-post-deploy-smoke.sh`
- [ALB health 502 runbook](../core/alb-health-502-runbook.md)
- [post deploy smoke runbook](../core/post-deploy-smoke-runbook.md)

Purpose:

- classify `/alb-health` 502 separately from user-facing 5xx
- make post-deploy verification repeatable without mutating user data
- check local actuator, ALB target health, public read-only policy APIs, and recent nginx user-path 5xx in one command
- ALB target health defaults to `auto`; it runs when AWS CLI is available and is explicitly skipped on nodes without AWS CLI

Initial run:

| Check | Result |
| --- | --- |
| local actuator | passed, `200` |
| ALB target health | passed, `2` healthy targets |
| public policy list | passed, `200`, count `5` |
| public policy search | passed, `200`, count `5` |
| public policy ranking | passed, `200`, count `5` |
| recent nginx 5xx split | passed, user-path `5xx=0`, `/alb-health 5xx=6` |

Decision:

- recent `/alb-health` 5xx without user-path 5xx and with both ALB targets healthy remains classified as health-check/deploy-window noise.
- future deploys should run `bash deploy/smoke/run-prod-post-deploy-smoke.sh` before opening another code or performance batch.
