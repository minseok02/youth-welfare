# Performance Measurement Plan

Last updated: 2026-05-29

## Purpose

This plan defines the baseline measurements that must be captured before performance optimization work starts. The goal is to make later optimization comparisons reproducible instead of relying on one-off impressions.

## Reference Model

Use these references as the measurement frame:

- ISO/IEC 25010: performance efficiency is evaluated through time behaviour, resource utilization, and capacity.
- ISO/IEC 25023: quantitative measures are used to evaluate ISO/IEC 25010 product-quality characteristics.
- Google SRE four golden signals: latency, traffic, errors, and saturation.
- Web Vitals: LCP, INP, and CLS for browser/user experience.
- OpenTelemetry/Micrometer style: metrics should carry timestamp, value, and enough attributes to identify endpoint, scenario, status, and environment.

Sources:

- https://iso25000.com/index.php/en/iso-25000-standards/iso-25010
- https://www.iso.org/standard/35747.html
- https://sre.google/sre-book/monitoring-distributed-systems/
- https://web.dev/articles/vitals
- https://opentelemetry.io/docs/concepts/signals/metrics/
- https://csrc.nist.gov/pubs/sp/800/55/v1/final

## Measurement Principles

- Record `git_head`, timestamp, host, base URL, DB mode, and dataset counts with every run.
- Keep raw samples, not only averages.
- Compare p50, p95, p99, max, error rate, and throughput.
- Split cold and warm measurements when cache behaviour matters.
- Keep local/internal measurements separate from external `https://youthmoa.kr` measurements.
- Keep read-only baseline separate from side-effecting flows such as signup, recommendation refresh, collect repair, and password reset.
- Use the same scenario definitions before and after optimization.
- Treat p95 or p99 regression as more important than average improvement.
- Run short baseline checks before optimization and longer load checks only when the short baseline is stable.
- Separate tool overhead from application performance. For example, Playwright duration is useful for release confidence but not a pure API latency metric.

## Tooling Strategy

Use lightweight local scripts first, then add heavier load and observability tooling when the first baseline is stable.

| Layer | First Tool | Deeper Tool | Purpose |
|---|---|---|---|
| API latency | `curl` timing scripts | k6, Apache JMeter | repeated latency, throughput, error rate, load/stress/spike tests |
| Browser UX | Playwright timing | Lighthouse CI, Web Vitals JS | route flow duration, LCP, INP, CLS, bundle impact |
| PostgreSQL | `EXPLAIN (ANALYZE, BUFFERS)`, `pg_stat_user_tables` | `pg_stat_statements`, `auto_explain`, `pgbench` | query plans, buffer reads, slow query ranking, DB-only load |
| Redis | `redis-cli INFO`, `SLOWLOG` | `redis-cli --latency`, `LATENCY DOCTOR`, commandstats | cache hit rate, memory pressure, command latency |
| JVM/Spring | Actuator health | Micrometer, Prometheus, Grafana, OpenTelemetry | HTTP histograms, GC, heap, Hikari pool, thread saturation |
| Edge/nginx | smoke headers and curl timing | access log percentiles, TLS timing | external latency and security header regression |
| Batch/wrappers | duration wrapper | scheduled historical trend | deploy confidence and operational runtime drift |

Recommended progression:

1. Keep this repo's `deploy/performance` scripts as the canonical before/after comparison contract.
2. Enable `pg_stat_statements` and Micrometer percentiles before claiming DB/JVM optimization wins.
3. Add k6 scenarios only after endpoint-level p95/p99 is stable enough to avoid mixing correctness issues with load issues.
4. Add Web Vitals/Lighthouse after frontend route flows are stable against the deployed origin.

## Test Types To Keep Separate

- Baseline: low-volume repeated checks used for before/after optimization comparison.
- Load test: expected traffic level for a fixed duration.
- Stress test: traffic above expected level to find the breaking point.
- Spike test: sudden traffic increase to check recovery behaviour.
- Soak test: long run to find memory leaks, connection leaks, Redis growth, and DB bloat.
- Smoke/performance hybrid: existing wrappers that verify correctness and record runtime, but do not replace load testing.

## Required Baseline Domains

### API Latency

Measure internal API latency with repeated requests:

- `GET /actuator/health`
- `GET /api/policies`
- `GET /api/policies?statusFilter=open`
- `GET /api/policies/search?keyword=청년`
- `GET /api/policies/search` with filters and sorting
- `GET /api/policies/search/suggestions`
- `GET /api/policies/search/trending`
- `GET /api/policies/ranking`
- `GET /api/policies/{id}`
- `GET /api/admin/dashboard/summary`
- `GET /api/admin/dashboard/collect-failures`
- `GET /api/admin/dashboard/recommendation-breakdowns`

Metrics:

- HTTP code
- p50/p90/p95/p99/max latency
- response size
- error rate
- measured URL and auth mode

Script:

```bash
APP_BASE_URL='http://127.0.0.1:8082' RUNS=7 \
  bash deploy/performance/run-local-api-latency-baseline.sh
```

Short load probe:

```bash
APP_BASE_URL='http://127.0.0.1:8082' DURATION_SECONDS=20 CONCURRENCY=4 \
  bash deploy/performance/run-local-api-load-baseline.sh
```

### Side-Effecting User Journeys

Keep these separate from the default read-only baseline because they create, update, or delete state:

- login
- logout/revoke
- session expiry and return-to-original-route
- bookmark create/delete
- mypage tab and recent-policy flows
- chat session create/message/delete
- password reset request/confirm with a controlled test account
- admin forced logout
- admin collect/backfill actions with explicit limits

Metrics:

- end-to-end duration
- API latency per step
- success/error code per step
- DB row delta for affected tables
- Redis key delta and TTL behaviour where relevant
- cleanup result

Rule:

- Never run these against real user accounts except approved smoke/admin accounts.
- Always record the fixture account, generated IDs, cleanup status, and whether the flow is idempotent.
- Do not mix these numbers with read-only API latency percentiles.
- Keep broad auth-session/withdraw flows opt-in when they require elevated DB credentials.

Script:

```bash
ENV_FILE=.env.production APP_BASE_URL='http://127.0.0.1:8082' \
  bash deploy/performance/run-local-stateful-flow-duration-baseline.sh
```

### PostgreSQL

Measure both data profile and query execution:

- table row counts
- table/index sizes
- dead tuple counts
- `pg_stat_statements` top queries when available
- `EXPLAIN (ANALYZE, BUFFERS)` for representative query families
- planning time and execution time
- shared/local/temp buffer hits and reads
- seq scan vs index scan
- sort method and memory
- dead tuple ratio and vacuum risk
- index size vs table size
- connection count, lock waits, and long-running transactions when deeper DB observability is enabled

Initial representative query families:

- policy list ordered by `created_at`
- policy search keyword fallback
- policy detail with detail join
- recommendation logs recent-window aggregation
- admin collect failures recent-window aggregation
- recent policy views by user

Optimization questions this baseline should answer later:

- Did an index reduce p95 API latency and DB execution time, or only move cost elsewhere?
- Did a query change remove seq scans on large tables?
- Did dead tuple growth or missing vacuum make the same query slower over time?
- Did `pg_stat_statements` total time move away from the optimized query to a new bottleneck?

Script:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
  bash deploy/performance/run-local-db-query-baseline.sh
```

Deep observability snapshot:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
  bash deploy/performance/run-local-db-observability-baseline.sh
```

### Redis

Measure Redis state and cache/session health:

- connected clients
- blocked clients
- used memory and peak memory
- fragmentation ratio
- total commands processed
- current ops/sec
- keyspace hits/misses and hit rate
- expired keys
- evicted keys
- rejected connections
- slowlog sample
- commandstats when available
- key count by logical namespace when a safe key scan is added
- latency monitor output when enabled

Optimization questions this baseline should answer later:

- Did cache hit rate increase without causing memory pressure?
- Did session/logout/revoke paths add slow commands or key churn?
- Did TTL changes reduce stale data without increasing DB fallback traffic?
- Did memory fragmentation, evictions, or slowlog entries increase after the change?

Script:

```bash
bash deploy/performance/run-local-redis-baseline.sh
```

Deep observability snapshot:

```bash
bash deploy/performance/run-local-redis-observability-baseline.sh
```

### Wrapper And Batch Duration

Measure operational wrapper duration because these scripts are now part of deployment confidence:

- recommendation observation
- current priority suite
- active baseline nested steps
- ops baseline
- collect legacy repair
- frontend e2e duration

Script:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
  bash deploy/performance/run-local-wrapper-duration-baseline.sh
```

### Frontend

Capture both lab and scenario measurements:

- bundle size from Vite build
- Playwright scenario duration
- route-level navigation time
- Core Web Vitals in browser lab runs: LCP, INP, CLS
- deployed-origin smoke result count

Current first step is wrapper duration, Playwright duration, and the deployed-origin Web Vitals baseline script.

Script:

```bash
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
  bash deploy/performance/run-local-web-vitals-baseline.sh
```

Current limitation: `interactionToNextPaint_ms` is recorded as `null` until a dedicated interaction script or the Web Vitals browser library is wired in.

Interaction timing approximation:

```bash
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
  bash deploy/performance/run-local-web-interaction-baseline.sh
```

Frontend optimization questions this baseline should answer later:

- Did bundle size or route navigation time change after UI work?
- Did policy search/detail/mypage flows regress on deployed-origin Playwright?
- Did LCP, INP, or CLS improve on actual routes instead of only on local dev?

### JVM/Spring

Target metrics to add through Micrometer/Actuator or Prometheus:

- `http.server.requests` latency histogram
- JVM heap/non-heap memory
- GC pause count and duration
- thread count
- Hikari active/idle/pending connection counts
- Tomcat active threads
- process CPU
- application startup time

Current status: actuator health is available. Full metrics exposure should be enabled separately before making JVM-level optimization claims.

Script:

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
  bash deploy/performance/run-local-jvm-runtime-baseline.sh
```

Current limitation: production actuator currently exposes `health`; `/actuator/metrics` is not exposed to this script, so the first JVM baseline uses container stats and recent log exception counts.

JVM optimization questions this baseline should answer later:

- Did API latency improve while GC pause or heap usage worsened?
- Did DB pool pending connections appear during search or admin dashboard calls?
- Did thread count or Tomcat active threads approach saturation during load?
- Did startup time regress after dependency or configuration changes?

### External Edge

Measure external behaviour separately from internal `127.0.0.1` calls:

- `https://youthmoa.kr` health and route response timing
- nginx access log p50/p95/p99 if logs are available
- TLS handshake and first byte timing
- static asset cache headers
- existing edge baseline headers: HSTS, frame options, content type options, CSP, server tokens

Do not mix external internet latency with internal API latency in one comparison table.

Script:

```bash
EXTERNAL_BASE_URL='https://youthmoa.kr' \
  bash deploy/performance/run-local-edge-baseline.sh
```

nginx log observability:

```bash
bash deploy/performance/run-local-nginx-log-observability-baseline.sh
```

## Baseline Suite

The first suite runs API, DB, and Redis by default. Wrapper duration is opt-in because it is slower.

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' RUNS=7 \
  bash deploy/performance/run-local-performance-baseline-suite.sh
```

With wrapper duration:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' RUNS=7 RUN_WRAPPER_BASELINE=true \
FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
  bash deploy/performance/run-local-performance-baseline-suite.sh
```

Extended suite:

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
EXTERNAL_BASE_URL='https://youthmoa.kr' \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
DURATION_SECONDS=20 CONCURRENCY=4 \
  bash deploy/performance/run-local-performance-extended-suite.sh
```

Deep observation suite, excluding long load tests:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
  bash deploy/performance/run-local-performance-deep-observation-suite.sh
```

## Output Contract

Each script writes timestamped artifacts under `tmp/performance/<suite>/<timestamp>/` and publishes a stable `latest` snapshot plus summary files.

Expected roots:

- `tmp/performance/api-latency/latest`
- `tmp/performance/db-query/latest`
- `tmp/performance/redis/latest`
- `tmp/performance/wrapper-duration/latest`
- `tmp/performance/full-suite/latest`
- `tmp/performance/api-load/latest`
- `tmp/performance/web-vitals/latest`
- `tmp/performance/jvm-runtime/latest`
- `tmp/performance/edge/latest`
- `tmp/performance/stateful-flow-duration/latest`
- `tmp/performance/extended-suite/latest`
- `tmp/performance/db-observability/latest`
- `tmp/performance/redis-observability/latest`
- `tmp/performance/web-interaction/latest`
- `tmp/performance/nginx-log-observability/latest`
- `tmp/performance/deep-observation-suite/latest`

## Optimization Comparison Rule

For each optimization PR or deploy:

- compare against the most recent accepted baseline
- mark as regression if p95 latency worsens by more than 10% without a documented reason
- mark as regression if error rate increases
- mark as capacity risk if latency improves but CPU, DB pool, Redis memory, or connection saturation worsens materially
- keep DB query plan diffs for any query touched by index or repository changes

## Follow-Up Instrumentation

Add these in later phases:

- Prometheus/Grafana dashboard for SRE golden signals
- Micrometer percentiles for selected HTTP endpoints
- `pg_stat_statements` reset-and-capture workflow for focused DB optimization windows
- Redis command latency monitor trend capture
- Lighthouse CI scores for deployed-origin routes
- k6 scenarios for sustained load and capacity testing beyond the current short load probe
- nginx log format update to include `$request_time` and `$upstream_response_time`
