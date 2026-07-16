# Final Load Capacity Check 2026-07-16

## Purpose

This checkpoint closes the project with a measured read-only load/capacity view after the final production operations work.

The goal is not to claim an absolute maximum real-user count. The current scripts generate synthetic concurrent workers from one client source, so the result is best read as:

- accepted read-only API throughput before errors
- first rate-limit boundary
- user-facing latency under low and moderate traffic
- whether the app, ALB, and DB stayed healthy after the test

## Runtime State

- branch: `refactor/admin-dashboard-sections`
- production commit before this measurement: `b0aa7454`
- primary EC2: `i-0b8d95e454df5e0f0`
- secondary EC2: `i-0e8a4cc599c1148c8`
- public domain: `https://youthmoa.kr`
- ALB target group: 2 healthy targets before measurement
- app container: no restart during this measurement
- DB size at post-check: `553 MB`

## Measurement Method

The measurement follows the repo performance plan:

- latency: p50, p95, p99, max
- traffic: total requests and measured RPS
- errors: non-2xx/3xx count and 429 count
- saturation: JVM container stats, DB locks/long queries/connections, ALB target health

References for the measurement frame are already recorded in [performance-measurement-plan.md](./performance-measurement-plan.md): ISO/IEC 25010/25023, Google SRE golden signals, Web Vitals, and OpenTelemetry/Micrometer style metrics.

Important interpretation rule:

- The first failed step is a **rate-limit boundary**, not a raw CPU/DB maximum.
- Once 429 appears, p95 latency is no longer the capacity number because many requests are rejected quickly.

## Script Adjustment

`deploy/performance/run-local-api-load-baseline.sh` now supports:

```bash
INCLUDE_HEALTH=false
```

Reason:

- internal loopback tests can include `/actuator/health`
- public ALB tests must exclude `/actuator/health` because nginx correctly blocks it from the public domain

## Pre-Test Ops Check

Command:

```bash
bash deploy/ops/run-no-cost-ops-check.sh
```

Result before load:

- local actuator: `UP`
- post-deploy smoke: passed
- ALB target health: 2 healthy targets
- public policy list/search/ranking: `200`, non-empty
- DB core: no open policy errors, no failed notifications, no waiting locks, no active queries over 5 minutes
- direct EC2 public 80/443 access: blocked
- cron/watchdog: installed and active
- log alert: `warning`

The pre-test log alert warning came from a very small 10-minute app-log sample:

- `GET /api/policies/ranking` count: `2`
- ranking p95 in that sample: `2167ms`
- raw error/warn lines: `0`
- nginx user-facing 5xx: `0`

The first low-rate load step immediately showed ranking p95 back at `28.3ms`, so this warning is treated as a transient low-sample/cold-tail observation, not a repeated capacity issue.

## Internal Loopback Step Test

Base URL:

```bash
APP_BASE_URL=http://127.0.0.1:8082
```

Scenarios:

- `GET /actuator/health`
- `GET /api/policies?page=0&size=20`
- `POST /api/policies/search` keyword
- `POST /api/policies/search` filtered/deadline
- `POST /api/policies/search/suggestions`
- `GET /api/policies/ranking?size=20`
- `GET /api/policies/{firstPolicyId}`

| Step | Duration | Concurrency | Delay/worker | RPS | Requests | Errors | 429 | Worst p95 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| internal-c1-d1_2 | 45s | 1 | 1.2s | 0.803 | 37 | 0 | 0 | policy search keyword `147.0ms` |
| internal-c2-d0_8 | 45s | 2 | 0.8s | 2.404 | 110 | 0 | 0 | policy list default `86.4ms` |
| internal-c4-d0_6 | 45s | 4 | 0.6s | 6.403 | 292 | 55 | 55 | policy search keyword `99.6ms` |
| internal-c8-d0_6 | 45s | 8 | 0.6s | 12.845 | 585 | 301 | 301 | not capacity-valid after 429 |
| internal-c12-d0_5 | 45s | 12 | 0.5s | 23.043 | 1045 | 577 | 577 | not capacity-valid after 429 |

Artifact roots:

- `tmp/performance/final-load-capacity-internal-c1-d1_2/20260716T141852Z`
- `tmp/performance/final-load-capacity-internal-c2-d0_8/20260716T141938Z`
- `tmp/performance/final-load-capacity-internal-c4-d0_6/20260716T142025Z`
- `tmp/performance/final-load-capacity-internal-c8-d0_6/20260716T142110Z`
- `tmp/performance/final-load-capacity-internal-c12-d0_5/20260716T142156Z`

Internal interpretation:

- Clean accepted point: about `2.4 rps` from this single-source mixed read profile.
- First protection boundary: about `6.4 rps`, where endpoint rate limiting starts.
- The app did not show latency collapse before rate limiting. Rejected requests were fast, which is why p95 remains low in failed steps.

## Public ALB Step Test

Base URL:

```bash
APP_BASE_URL=https://youthmoa.kr INCLUDE_HEALTH=false
```

Scenarios are the same public read APIs, excluding `/actuator/health`.

| Step | Duration | Concurrency | Delay/worker | RPS | Requests | Errors | 429 | Worst p95 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| edge-c1-d1_2 | 45s | 1 | 1.2s | 0.799 | 36 | 0 | 0 | policy detail first `185.3ms` |
| edge-c2-d0_8 | 45s | 2 | 0.8s | 2.403 | 110 | 0 | 0 | policy search keyword `90.9ms` |
| edge-c4-d0_8 | 45s | 4 | 0.8s | 4.806 | 220 | 40 | 40 | policy search keyword `128.9ms` |

Artifact roots:

- `tmp/performance/final-load-capacity-edge-c1-d1_2/20260716T142317Z`
- `tmp/performance/final-load-capacity-edge-c2-d0_8/20260716T142403Z`
- `tmp/performance/final-load-capacity-edge-c4-d0_8/20260716T142449Z`

Public interpretation:

- Clean accepted point: about `2.4 rps` through ALB/TLS/nginx from this single test source.
- First protection boundary: about `4.8 rps`, where 429 appears.
- At the accepted public point, worst p95 stayed below `100ms`.

## Post-Test Health And Saturation

Post-load production smoke:

- local actuator: passed
- ALB target health: 2 healthy targets
- public policy list/search/ranking: `200`, non-empty
- nginx recent user-facing 5xx: `0`

Post-load DB audit:

- `active_queries_over_5m=0`
- `waiting_locks=0`
- `idle_in_transaction_over_5m=0`
- `current_connections=30`
- `max_connections=191`
- `database_size=553 MB`
- `policy_error_reports_open=0`
- `notification_failed_like=0`

Post-load JVM runtime:

- health status: `200`
- container CPU: `0.17%`
- container memory: `588.2MiB / 1GiB`
- memory share: `57.44%`
- restart count: `0`
- PIDs: `49`
- recent exception count: `0`

Post-load DB observability:

- `numbackends=29`
- `db_cache_hit_rate=0.996477`
- `deadlocks=0`
- `blocked_lock_count=0`
- `long_transaction_count=0`
- `pg_stat_statements_available=false`

Post-load nginx/app logs:

- nginx user-facing 5xx: `0`
- nginx 429 in sampled public logs: `24`
- app API request count in sampled app logs: `1978`
- app status 200: `1021`
- app status 429: `957`
- app error code `P003`: `957`
- app raw error lines: `0`
- app raw warn lines: `0`
- app duration p95 including rejected traffic: `31ms`

The log alert became `critical` immediately after the load test because the test intentionally generated repeated `P003` rate-limit events:

```text
critical=api errorCode P003 repeated 957 >= 10
```

This is expected test evidence, not an unexpected production failure. Re-check the log alert after the 10-minute app-log observation window clears.

Recovery re-check:

```bash
LOG_ALERT_NOTIFY_OK=false bash deploy/ops/send-log-alert.sh
```

Result at `2026-07-16T14:37:11Z`:

- `LOG_ALERT_STATUS=ok`
- remaining observation: nginx `/alb-health` 5xx count `2`
- rate-limit `P003` critical cleared after the app-log observation window moved past the synthetic load test

Final no-cost closeout after recovery:

- command: `bash deploy/ops/run-no-cost-ops-check.sh`
- result at `2026-07-16T14:37:50Z`: `ok_count=9`, `fail_count=0`
- DB runtime: `current_connections=26`, `waiting_locks=0`, `active_queries_over_5m=0`
- log alert: `LOG_ALERT_STATUS=ok`
- direct EC2 public 80/443 access: blocked on both instances
- latest watchdog lines: `health=UP`

## Capacity Conclusion

For the current production setup, under this read-only mixed API profile from one source:

- **Accepted clean public capacity:** about `2.4 rps` with no errors and worst p95 below `100ms`.
- **Accepted clean internal capacity:** about `2.4 rps` with no errors and worst p95 below `90ms` in the second step.
- **First public rate-limit boundary:** about `4.8 rps`.
- **First internal rate-limit boundary:** about `6.4 rps`.
- **Observed failure mode:** 429 rate limiting, not CPU, DB lock, ALB target failure, or 5xx.
- **Alert recovery:** log alert returned to `ok` after the 10-minute rate-limit observation window cleared.

Approximate active-user interpretation:

- If one active user generates one read request every 5 seconds, the clean `2.4 rps` point maps to about `12` simultaneously active clickers from the same synthetic source pattern.
- If one active user generates one read request every 10 seconds, it maps to about `24` simultaneously active clickers.
- This is not a hard real-world user maximum because real users are distributed by IP/session/fingerprint and do not hit the exact same endpoint mix at fixed intervals.

Operationally, the current bottleneck is the configured rate-limit posture before infrastructure saturation. If more live traffic is expected, the next decision is not another query optimization; it is to tune rate-limit policy by endpoint and user/IP identity after reviewing abuse protection requirements.

## Decision

No further code-level performance optimization is required before project closeout.

Keep these as future backlog items only when traffic or product requirements justify them:

1. Rate-limit policy review for public read APIs.
2. Proper k6/JMeter distributed load testing from multiple client sources.
3. Enable `pg_stat_statements` for better DB hot-query attribution.
4. Micrometer/Prometheus HTTP histograms for production p95/p99 without log scraping.
5. Cost-approved HA work: RDS restore rehearsal, RDS Multi-AZ, Valkey/ElastiCache HA.
