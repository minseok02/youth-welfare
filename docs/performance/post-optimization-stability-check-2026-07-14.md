# Post Optimization Stability Check - 2026-07-14

## Scope

This check freezes the current post-optimization operating state before opening another performance batch.

- branch: `refactor/admin-dashboard-sections`
- commit: `01207a08957f3731c5179ffb6c68e203aa1cf6d3`
- public domain: `https://youthmoa.kr`
- target group: `youth-welfare-web-tg`
- destructive admin or collect actions: not run

## Decision

Status: `hold-observe`

The current deployment is stable enough to keep running, and no immediate rollback or hotfix is indicated. Do not open another performance-changing batch immediately. Observe the current baseline first, then decide whether to work on ranking/search tail latency.

Reasons:

- both EC2 targets are healthy behind ALB
- primary and secondary are on the same commit
- core public policy flows return valid data
- app logs show no raw error lines
- RDS generated search migration and generated indexes are present
- latest local and edge baselines pass
- remaining concerns are measurement/latency-tail candidates, not functional breakage

## Operating State

Primary:

- commit: `01207a08957f3731c5179ffb6c68e203aa1cf6d3`
- app container: `Up`, `healthy`
- actuator health: `UP`

Secondary:

- instance: `i-0e8a4cc599c1148c8`
- commit: `01207a08957f3731c5179ffb6c68e203aa1cf6d3`
- app container: `Up`, `healthy`
- actuator health: `UP`

ALB:

| Instance | Port | State |
| --- | ---: | --- |
| `i-0e8a4cc599c1148c8` | 80 | healthy |
| `i-0b8d95e454df5e0f0` | 80 | healthy |

DNS:

- `youthmoa.kr A` returned `13.209.214.158`, `54.116.109.58`
- `www.youthmoa.kr A` returned `13.209.214.158`, `54.116.109.58`
- authoritative NS set matches Route53 hosted zone:
  - `ns-359.awsdns-44.com.`
  - `ns-787.awsdns-34.net.`
  - `ns-1384.awsdns-45.org.`
  - `ns-1977.awsdns-55.co.uk.`

RDS migration/schema:

- migration history row:
  - version: `2026.07.14.02`
  - script: `V2026_07_14_02__add_policy_search_generated_fields.sql`
  - applied by: `masteradmin`
- generated columns present: `3`
- generated-field indexes present: `3`

## Public Functional Smoke

Executed against `https://youthmoa.kr`.

| Flow | Result |
| --- | --- |
| `GET /` | `200`, 800 bytes |
| `GET /policies` | `200`, 800 bytes |
| `GET /api/policies?page=0&size=20` | `200`, 20 items |
| first list detail | `200`, same ID, content present |
| `POST /api/policies/search` keyword `청년` | `200`, 20 items |
| `POST /api/policies/search/suggestions` keyword `청` | `200`, 8 suggestions |
| `GET /api/policies/search/trending` | `200`, 6 items |
| `GET /api/policies/ranking` | `200`, 20 items |

Representative values:

- first policy list item: `15391`, `1인 자영업자 등 출산급여 지원`
- first search item: `844`, `전통시장 청년창업공간(청년몰) 운영`
- first ranking item:
  - `serviceId=844`
  - title: `전통시장 청년창업공간(청년몰) 운영`
  - `rankingScore=0.6762`

## Admin And Runtime Access

Admin read-only API smoke was not executed because no admin password or reusable admin access token was available on this host.

Compensating checks:

- unauthenticated admin endpoints returned `401`:
  - `/api/admin/dashboard/summary`
  - `/api/admin/dashboard/collect-failures`
  - `/api/admin/dashboard/recommendation-breakdowns`
  - `/api/admin/dashboard/attention-feed`
- RDS runtime privilege verification passed:
  - primary app role login OK
  - admin read-only role login OK
  - migration role login OK
  - cleanup and command roles login OK
  - admin read-only role has expected `SELECT` privileges
  - admin read-only role does not have `INSERT/UPDATE/DELETE` on checked support tables
  - app core role does not have checked high-risk delete/promotion-table privileges

Follow-up gap:

- Run the four admin read-only smoke scripts when an admin smoke password or token is available:
  - `run-local-admin-dashboard-smoke.sh`
  - `run-local-admin-collect-failures-smoke.sh`
  - `run-local-admin-recommendation-breakdowns-smoke.sh`
  - `run-local-admin-attention-feed-smoke.sh`

## Logs And Runtime

Artifacts:

- app log: `tmp/stability/app-log-20260714/20260714T172808Z`
- nginx log: `tmp/stability/nginx-log-20260714/20260714T172808Z`
- JVM/container: `tmp/performance/jvm-runtime/20260714T172808Z`

App log, last 60 minutes:

| Metric | Value |
| --- | ---: |
| raw error lines | 0 |
| raw warn lines | 4 |
| API requests | 125 |
| API 200 | 121 |
| API 400 | 2 |
| API 401 | 2 |
| API duration p50 | 39ms |
| API duration p95 | 219ms |
| API duration p99 | 1081ms |
| API duration max | 1308ms |

Slow samples:

- `GET /api/policies/ranking`: max `1308ms`, 3 samples, p95 `1273.3ms`
- `POST /api/policies/search`: max `1110ms`, 55 samples, p95 `241.8ms`

Nginx tail:

| Metric | Value |
| --- | ---: |
| sample lines | 10000 |
| request p95 | 0.043s |
| request p99 | 0.105s |
| upstream p95 | 0.084s |
| upstream p99 | 0.21556s |
| 502 count in tail | 7 |
| 429 count in tail | 3 |

502 interpretation:

- all shown `502` entries were `GET /alb-health`
- latest entries were around container restart/deploy time, for example `2026-07-14T16:36:42Z` through `2026-07-14T16:36:58Z`
- current ALB target health is healthy for both targets

429 interpretation:

- shown `429` entries were `GET /api/policies` from `curl/8.5.0` at `2026-07-14T11:28:04Z` through `2026-07-14T11:28:05Z`
- treat these as measurement-induced unless they recur outside controlled baselines

JVM/container:

- health status: `200`
- container running: true
- restart count: `0`
- memory: about `602.1MiB / 1GiB`
- read-only root filesystem: true
- recent log exception count: `0`
- actuator metrics endpoint not exposed to the JVM baseline script, returned `401`

## Performance Baseline Recheck

Artifacts:

- local API: `tmp/stability/api-latency-20260714/20260714T172836Z`
- DB query: `tmp/stability/db-query-20260714/20260714T172836Z`
- edge: `tmp/stability/edge-20260714/20260714T172836Z`

Local API latency:

| Scenario | p50 | p95 | Max | Errors | Rate limited |
| --- | ---: | ---: | ---: | ---: | ---: |
| `policy_list_active_only` | 71.9ms | 86.6ms | 89.7ms | 0/5 | 0 |
| `policy_list_default` | 81.9ms | 85.5ms | 86.2ms | 0/5 | 0 |
| `policy_suggestions` | 37.0ms | 59.8ms | 65.2ms | 0/5 | 0 |
| `policy_search_filtered` | 16.2ms | 33.5ms | 37.3ms | 0/5 | 0 |
| `health` | 18.9ms | 28.3ms | 28.6ms | 0/5 | 0 |
| `policy_search_keyword` | 14.4ms | 15.6ms | 15.7ms | 0/5 | 0 |

DB query:

| Query | Execution | Planning | Notes |
| --- | ---: | ---: | --- |
| `admin_collect_failures_recent` | 0.943ms | 0.532ms | seq scan |
| `policy_detail_first` | 0.122ms | 1.860ms | index scan |
| `policy_list_created_at` | 14.314ms | 1.315ms | seq scan |
| `policy_search_keyword_api_shape` | 66.309ms | 6.249ms | generated-field representative |
| `policy_search_keyword_legacy_or_shape` | 290.515ms | 4.360ms | raw legacy comparison |
| `recent_policy_views_user` | 0.170ms | 0.507ms | seq scan |
| `recommendation_logs_recent_window` | 10.357ms | 0.601ms | seq scan |

Table counts:

| Table | Rows |
| --- | ---: |
| `welfare_services` | 15106 |
| `raw_api_payloads` | 44979 |
| `recommendation_logs` | 31985 |
| `search_logs` | 2415 |
| `recent_policy_views` | 69 |
| `chat_messages` | 36 |

Edge baseline:

| Scenario | Status | Total |
| --- | ---: | ---: |
| home | 200 | 75.501ms |
| policies page | 200 | 79.006ms |
| policy list default | 200 | 189.087ms |
| policy search keyword | 200 | 254.031ms |

Security headers:

- HSTS: true
- X-Frame-Options: true
- X-Content-Type-Options: true
- CSP: true
- server tokens hidden: true

## Comparison To Latest Optimization Baseline

| Metric | Previous latest | Stability recheck | Interpretation |
| --- | ---: | ---: | --- |
| local `policy_search_keyword` p95 | 18.9ms | 15.6ms | stable |
| DB `policy_search_keyword_api_shape` | 105.263ms | 66.309ms | stable/improved, same generated-field contract |
| edge `policy_search_keyword` | 300.144ms | 254.031ms | stable |
| app raw error lines | 0 | 0 | stable |
| ALB target health | 2 healthy | 2 healthy | stable |

## Next Action

Recommended next state: observe before changing more.

Do next:

1. Keep this commit as the current operating baseline.
2. Run the four admin read-only smoke scripts once admin smoke credentials are available.
3. Watch whether `GET /api/policies/ranking` slow tail repeats outside tiny sample windows.
4. If another optimization batch is opened, prefer a measurement-first batch:
   - split search/ranking cold and warm cache measurements
   - then decide whether to optimize ranking or uncached search rank/window cost

Follow-up completed:

- [policy-cache-tail-measurement-2026-07-14.md](policy-cache-tail-measurement-2026-07-14.md) split search/ranking cold and warm cache behavior.
- Result: ranking cold compute is the next stronger bottleneck candidate; search should not be optimized first from the current evidence.

Do not do next:

- do not immediately rewrite ranking/search code solely from this check
- do not interpret warm-cache local search p95 as proof that uncached DB cost is gone
- do not treat historical `/alb-health` 502s as current failure while both ALB targets remain healthy
