# Policy Cache Tail Measurement - 2026-07-14

## Scope

This batch measures the current cold/warm latency split for public policy search and ranking before changing ranking/search runtime code.

- branch: `refactor/admin-dashboard-sections`
- starting commit: `fa3f6c6f686bcacc24edb875e487a0d510c4028a`
- target endpoints:
  - `POST /api/policies/search`
  - `GET /api/policies/ranking`
- runtime behavior change: none
- new measurement tool: `deploy/performance/run-local-policy-cache-tail-baseline.sh`

## Why This Batch Was Opened

The post-optimization stability check found no functional regression, but recent app logs still showed slow-tail samples:

- `GET /api/policies/ranking`: max about `1308ms` in a tiny sample
- `POST /api/policies/search`: max about `1110ms` in a mixed cache/log window

Search and ranking both use 30 second cache layers:

- search:
  - local in-process cache keyed by normalized search parameters
  - Redis cache prefix `policy:search:public:v1:`
- ranking:
  - local in-process cache keyed by requested size
  - Redis cache prefix `policy:ranking:v1:`

Because the previous API baselines were mostly warm-cache measurements, the next step was to split cold and warm behavior explicitly.

## Plan

1. Add a reusable low-impact measurement script.
2. Measure cold samples by waiting longer than the 30 second cache TTL before each cold request.
3. Measure warm samples by priming the cache and immediately repeating the same request.
4. Run local and public edge variants.
5. Check app/nginx/Redis observability after the measurement.
6. Decide whether the next real optimization should target search or ranking.

## Expected Result

| Endpoint | Cold expectation | Warm expectation | Reason |
| --- | ---: | ---: | --- |
| search keyword | hundreds of ms | tens of ms | DB + presentation work avoided by cache |
| ranking | highest tail, possibly 500ms+ local | single/tens of ms | rankable snapshot scan + unique-view aggregation + scoring avoided by cache |

## Measurement Commands

Script smoke:

```bash
bash -n deploy/performance/run-local-policy-cache-tail-baseline.sh

COLD_RUNS=1 WARM_RUNS=2 CACHE_TTL_WAIT_SECONDS=1 \
  POLICY_CACHE_TAIL_ROOT=tmp/stability/policy-cache-tail-smoke-20260714 \
  APP_BASE_URL=http://127.0.0.1:8082 \
  bash deploy/performance/run-local-policy-cache-tail-baseline.sh
```

Local baseline:

```bash
COLD_RUNS=3 WARM_RUNS=5 CACHE_TTL_WAIT_SECONDS=31 WARM_DELAY_SECONDS=0.2 \
  POLICY_CACHE_TAIL_ROOT=tmp/stability/policy-cache-tail-local-20260714 \
  APP_BASE_URL=http://127.0.0.1:8082 \
  bash deploy/performance/run-local-policy-cache-tail-baseline.sh
```

Edge baseline:

```bash
COLD_RUNS=2 WARM_RUNS=4 CACHE_TTL_WAIT_SECONDS=31 WARM_DELAY_SECONDS=0.3 \
  POLICY_CACHE_TAIL_ROOT=tmp/stability/policy-cache-tail-edge-20260714 \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-policy-cache-tail-baseline.sh
```

Post-measurement observability:

```bash
APP_LOG_COMPOSE_FILE=docker-compose.prod.elasticache.yml APP_LOG_SINCE=15m \
  APP_LOG_COMPOSE_SERVICE=app \
  APP_LOG_ROOT=tmp/stability/policy-cache-tail-app-log-20260714 \
  bash deploy/performance/run-local-app-log-observability-baseline.sh

NGINX_LOG_ROOT=tmp/stability/policy-cache-tail-nginx-log-20260714 \
  NGINX_LOG_TAIL_LINES=3000 \
  bash deploy/performance/run-local-nginx-log-observability-baseline.sh

ENV_FILE=.env.production \
  REDIS_OBSERVABILITY_ROOT=tmp/stability/policy-cache-tail-redis-20260714 \
  bash deploy/performance/run-local-redis-observability-baseline.sh
```

## Results

Artifacts:

- script smoke: `tmp/stability/policy-cache-tail-smoke-20260714/20260714T173927Z`
- local: `tmp/stability/policy-cache-tail-local-20260714/20260714T173946Z`
- edge: `tmp/stability/policy-cache-tail-edge-20260714/20260714T174129Z`
- app log: `tmp/stability/policy-cache-tail-app-log-20260714/20260714T174250Z`
- nginx log: `tmp/stability/policy-cache-tail-nginx-log-20260714/20260714T174250Z`
- Redis: `tmp/stability/policy-cache-tail-redis-20260714/20260714T174250Z`

Local:

| Phase | Endpoint | p50 | p95 | p99 | Max | Success | Items |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| cold | ranking | 566.9ms | 781.9ms | 801.0ms | 805.8ms | 3/3 | 20 |
| cold | search keyword | 158.1ms | 179.2ms | 181.1ms | 181.5ms | 3/3 | 20 |
| warm | ranking | 6.4ms | 6.5ms | 6.5ms | 6.5ms | 5/5 | 20 |
| warm | search keyword | 12.3ms | 13.2ms | 13.4ms | 13.4ms | 5/5 | 20 |

Edge:

| Phase | Endpoint | p50 | p95 | p99 | Max | Success | Items |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| cold | ranking | 1415.5ms | 1893.9ms | 1936.4ms | 1947.0ms | 2/2 | 20 |
| cold | search keyword | 364.5ms | 544.3ms | 560.3ms | 564.3ms | 2/2 | 20 |
| warm | ranking | 20.9ms | 22.1ms | 22.2ms | 22.3ms | 4/4 | 20 |
| warm | search keyword | 26.4ms | 47.0ms | 49.8ms | 50.5ms | 4/4 | 20 |

Post-measurement app log:

| Metric | Value |
| --- | ---: |
| raw error lines | 0 |
| raw warn lines | 0 |
| API request count | 64 |
| API 200 | 64 |
| API duration p50 | 22ms |
| API duration p95 | 515ms |
| API duration p99 | 718ms |
| API duration max | 803ms |

Path breakdown:

| Path | Count | p50 | p95 | Max |
| --- | ---: | ---: | ---: | ---: |
| `GET /api/policies/ranking` | 15 | 4.0ms | 708.5ms | 803ms |
| `POST /api/policies/search` | 29 | 12.0ms | 173.0ms | 286ms |
| `GET /api/policies` | 14 | 69.5ms | 91.2ms | 99ms |
| `POST /api/policies/search/suggestions` | 6 | 34.5ms | 57.8ms | 62ms |

Nginx/Redis after measurement:

- nginx 3000-line tail:
  - request p95 `0.040s`
  - upstream p95 `0.084s`
  - `GET /api/policies/ranking` count `8`
  - no new 429 in the displayed top paths
  - 502 entries are historical `/alb-health` entries in the tail
- Redis:
  - `redis_observability_baseline=passed`
  - slowlog length `0`
  - blocked clients `0`
  - evicted keys `0`
  - `public_search` and `ranking` sampled cache namespace counts were `0` at observation time, consistent with short TTL/expiry timing

## Interpretation

The next meaningful bottleneck is ranking cold compute, not warm-cache search.

Evidence:

- local ranking cold p95 is about `120x` warm ranking p95:
  - `781.9ms / 6.5ms`
- edge ranking cold p95 is about `86x` warm ranking p95:
  - `1893.9ms / 22.1ms`
- search cold is also slower than search warm, but the absolute local p95 is much smaller:
  - search cold p95 `179.2ms`
  - ranking cold p95 `781.9ms`
- app logs during the measurement show no errors, no warnings, and only successful 200 responses.

Likely ranking cold cost sources from code inspection:

1. `PolicyRankingService.computeRanking`
   - loads all rankable active/upcoming snapshots
   - aggregates unique view counts for the 7 day window
   - computes normalized score in Java for all snapshots
   - applies exploration slots
   - loads selected services by ID
   - loads recommendation projections for selected services
2. `ServiceViewLogRepository.findUniqueViewCountsSinceForStatuses`
   - counts distinct user/fingerprint identities grouped by service over recent view logs
3. `PolicyPresentationReadService.findProjections`
   - adds projection work for selected services

## Next Optimization Candidate

Open a ranking cold optimization batch only after this measurement batch is accepted.

Recommended first plan:

1. Add DB representative EXPLAINs for the two ranking data sources:
   - active/upcoming rankable snapshot query
   - 7 day unique view count aggregation
2. Measure current `computeRanking` substeps if possible.
3. Prefer a low-risk optimization first:
   - cache or precompute the unique-view aggregation separately
   - or reduce ranking cold compute to a bounded top candidate set before projection
4. Keep current 30 second response cache in place.

Expected improvement target for a first ranking batch:

| Metric | Current | Conservative target |
| --- | ---: | ---: |
| local ranking cold p95 | 781.9ms | 300-500ms |
| edge ranking cold p95 | 1893.9ms | 800-1300ms |
| warm ranking p95 | 6.5ms local / 22.1ms edge | unchanged |

Do not optimize search first unless new data shows search cold is causing user-visible tail more often than ranking cold.
