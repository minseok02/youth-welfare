# Performance Next Checkpoint - 2026-07-14

## Status

Current performance work is closed for now.

- latest runtime commit measured: `1b6b893cd7f94d909fce56811e7391b1a1ed1e30`
- current decision: do not make another behavior-changing optimization immediately
- next step after a later remeasurement: ranking app-side timing instrumentation, if ranking cold tail still repeats

## What Was Completed

The completed work is documented in:

- [performance-change-ledger-2026-07-14.md](performance-change-ledger-2026-07-14.md)
- [post-optimization-stability-check-2026-07-14.md](post-optimization-stability-check-2026-07-14.md)
- [policy-cache-tail-measurement-2026-07-14.md](policy-cache-tail-measurement-2026-07-14.md)
- [ranking-cold-breakdown-measurement-2026-07-14.md](ranking-cold-breakdown-measurement-2026-07-14.md)

Summary:

| Area | Result |
| --- | --- |
| ALB/2 instances | primary and secondary healthy, 2 ALB targets healthy |
| public smoke | list/search/detail/suggestions/trending/ranking returned valid data |
| search generated fields | DB representative improved and aligned with real generated-field path |
| migration safety | RDS master pre-apply wrapper added and verified |
| rate-limit measurement | API latency decision now separates 429 from real failures |
| stability check | accepted, `hold-observe` |
| cache-tail split | ranking cold is clearly worse than search cold |
| ranking breakdown | DB EXPLAIN alone does not explain endpoint cold p95 |

## Final Numbers To Remember

Search:

| Metric | Value |
| --- | ---: |
| aligned DB `policy_search_keyword_api_shape` | 105.263ms |
| stability DB `policy_search_keyword_api_shape` | 66.309ms |
| local search cold p95 | 179.2ms |
| local search warm p95 | 13.2ms |
| edge search cold p95 | 544.3ms |
| edge search warm p95 | 47.0ms |

Ranking:

| Metric | Value |
| --- | ---: |
| local ranking cold p95 | 781.9ms |
| local ranking warm p95 | 6.5ms |
| edge ranking cold p95 | 1893.9ms |
| edge ranking warm p95 | 22.1ms |
| rankable snapshot EXPLAIN | 12.145ms |
| unique-view aggregation EXPLAIN | 0.278ms |
| projection base EXPLAIN | 60.108ms |
| scoring/sorting simulation | 128.302ms |

## What To Recheck Later

Run this only after some normal traffic or after the next deployment window:

```bash
COLD_RUNS=3 WARM_RUNS=5 CACHE_TTL_WAIT_SECONDS=31 WARM_DELAY_SECONDS=0.2 \
  POLICY_CACHE_TAIL_ROOT=tmp/stability/policy-cache-tail-local-rerun-$(date -u +%Y%m%d) \
  APP_BASE_URL=http://127.0.0.1:8082 \
  bash deploy/performance/run-local-policy-cache-tail-baseline.sh

COLD_RUNS=2 WARM_RUNS=4 CACHE_TTL_WAIT_SECONDS=31 WARM_DELAY_SECONDS=0.3 \
  POLICY_CACHE_TAIL_ROOT=tmp/stability/policy-cache-tail-edge-rerun-$(date -u +%Y%m%d) \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-policy-cache-tail-baseline.sh

APP_LOG_COMPOSE_FILE=docker-compose.prod.elasticache.yml APP_LOG_SINCE=30m \
  APP_LOG_COMPOSE_SERVICE=app \
  APP_LOG_ROOT=tmp/stability/app-log-rerun-$(date -u +%Y%m%d) \
  bash deploy/performance/run-local-app-log-observability-baseline.sh
```

Optional DB-only breakdown rerun:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
  RANKING_BREAKDOWN_ROOT=tmp/stability/ranking-cold-breakdown-rerun-$(date -u +%Y%m%d) \
  bash deploy/performance/run-local-ranking-cold-breakdown-baseline.sh
```

## How To Decide Next

Use this decision table after the rerun:

| Rerun result | Decision |
| --- | --- |
| ranking cold p95 stays high, warm remains low | add app-side timing instrumentation in `PolicyRankingService.computeRanking` |
| ranking cold normalizes below about 300-500ms local | keep observing, no optimization |
| search cold becomes worse than ranking cold | reopen search cold path investigation |
| app logs show errors/429/5xx during measurement | fix operational issue before optimizing |
| DB breakdown shows unique-view aggregation grows materially | consider unique-view cache/precompute |
| DB breakdown still small but endpoint cold high | instrument JPA materialization, DTO assembly, serialization, cache write |

## Next Work If Needed

If the later rerun still shows ranking cold tail, do this first:

1. Add low-cardinality timing around `PolicyRankingService.computeRanking` substeps.
2. Log timings only for cold compute or when total ranking compute exceeds a threshold.
3. Do not change response ordering or ranking math.
4. Rerun cache-tail and app-log baselines.
5. Pick one optimization only after live substep timings identify the largest cost.

Do not do next:

- do not rewrite ranking SQL just from the current DB EXPLAIN
- do not remove the existing 30 second response cache
- do not optimize search first unless fresh measurements reverse the ranking/search priority

## Closure

This batch ends with a measurement-backed recommendation, not a runtime behavior change.

The system is currently stable enough to run. The correct next move is to remeasure later, then decide whether ranking instrumentation is still warranted.
