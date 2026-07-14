# Ranking Cold Breakdown Measurement - 2026-07-14

## Scope

This measurement breaks down the current `GET /api/policies/ranking` cold path without changing runtime behavior.

- starting commit: `6bb201a66d4c92b6503df03af074a8f19ae752f1`
- measurement script: `deploy/performance/run-local-ranking-cold-breakdown-baseline.sh`
- app endpoint changes: none
- DB changes: none

## Trigger

The policy cache-tail measurement showed that ranking cold latency is the next strongest bottleneck candidate:

| Endpoint | Local cold p95 | Local warm p95 | Edge cold p95 | Edge warm p95 |
| --- | ---: | ---: | ---: | ---: |
| ranking | 781.9ms | 6.5ms | 1893.9ms | 22.1ms |
| search keyword | 179.2ms | 13.2ms | 544.3ms | 47.0ms |

Before optimizing ranking, the cold path needed to be separated into:

- rankable snapshot query
- 7 day unique-view aggregation
- Java scoring/sorting
- selected service lookup
- projection base/terms/facts lookup

## Plan

1. Add a standalone read-only breakdown script.
2. Query the same data sources used by `PolicyRankingService.computeRanking`.
3. Simulate the Java scoring/sorting path in Python using the same weights and exploration-slot rules.
4. Run `EXPLAIN (ANALYZE, BUFFERS)` for each representative DB query.
5. Compare isolated DB/compute costs against the endpoint cold p95 from the cache-tail measurement.

## Expected Result

Before this measurement, the likely candidates were:

| Candidate | Expected risk |
| --- | --- |
| 7 day unique-view aggregation | possible high DB cost if view logs grow |
| rankable snapshot query | possible cost because it reads all active/upcoming rankable policies |
| Java scoring/sorting | possible cost because it scores every snapshot |
| projection lookup | possible cost because ranking response adds canonical labels/facts |
| serialization/cache write | possible unmeasured app-side cost |

## Command

```bash
bash -n deploy/performance/run-local-ranking-cold-breakdown-baseline.sh

ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
  RANKING_BREAKDOWN_ROOT=tmp/stability/ranking-cold-breakdown-20260714 \
  bash deploy/performance/run-local-ranking-cold-breakdown-baseline.sh
```

Final artifact:

- `tmp/stability/ranking-cold-breakdown-20260714/20260714T180800Z`

## Result

Summary:

| Metric | Value |
| --- | ---: |
| rankable snapshot rows | 13340 |
| unique-view service rows | 5 |
| total unique views in 7 day window | 10 |
| selected ranking services | 20 |
| projection base rows | 20 |
| projection term rows | 86 |
| projection fact rows | 242 |
| scoring/sorting simulation | 128.302ms |

Representative selected IDs:

```text
844,15382,11160,7751,10449,4596,7584,14916,1389,5522,234,3967,15391,14912,2612,4382,2666,15287,15250,15393
```

Top rows from the scoring simulation:

| Rank | Service ID | Score | Unique views | View count | API view count | Source |
| ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1 | 844 | 0.676247 | 1 | 58 | 109 | YOUTH |
| 2 | 15382 | 0.612720 | 5 | 5 | 52 | BOKJIRO_LOCAL |
| 3 | 11160 | 0.401906 | 1 | 3 | 188941 | GOV24 |
| 4 | 7751 | 0.399047 | 0 | 15 | 74 | GOV24 |
| 5 | 10449 | 0.394320 | 2 | 2 | 9683 | GOV24 |

DB EXPLAIN:

| Step | Execution | Planning | Plan note |
| --- | ---: | ---: | --- |
| rankable snapshot query | 12.145ms | 1.521ms | seq scan |
| unique-view aggregation query | 0.278ms | 1.985ms | seq/index |
| selected services query | 0.297ms | 1.741ms | index |
| projection base query | 60.108ms | 6.757ms | index |
| projection terms query | 0.647ms | 1.041ms | index |
| projection facts query | 0.432ms | 0.603ms | index |

Wall-time note:

- The script also records per-query wall time.
- Those wall times include repeated `psql` process/connection setup and should not be read as the endpoint's internal per-step timing.
- Use the `EXPLAIN` execution times and the Python scoring simulation as the primary breakdown signal.

## Interpretation

The isolated DB queries do not fully explain the endpoint cold p95.

Known numbers:

- local endpoint ranking cold p95: `781.9ms`
- edge endpoint ranking cold p95: `1893.9ms`
- DB EXPLAIN sum for representative pieces: about `73.9ms`
- scoring/sorting simulation: `128.302ms`
- projection base is the largest DB piece at `60.108ms`

This suggests the remaining ranking cold cost is likely in app-side work not captured by isolated DB EXPLAIN:

- JPA entity materialization for rankable snapshots or selected services
- transaction/repository overhead
- projection object assembly
- response DTO assembly
- JSON serialization
- Redis/local cache write
- cross-process/runtime overhead from the endpoint path

The 7 day unique-view aggregation is not currently the primary problem:

- only `5` services had unique views in the window
- EXPLAIN execution was `0.278ms`

## Decision

Do not optimize ranking SQL blindly yet.

Next best step:

1. Add low-cardinality app-side timing around `PolicyRankingService.computeRanking` substeps:
   - rankable snapshot repository call
   - unique-view aggregation repository call
   - scoring/sorting
   - selected service load
   - projection load
   - DTO build/cache write
2. Run the existing cache-tail script again.
3. Use live endpoint substep timings to pick the first optimization.

Likely candidates after instrumentation:

- projection base reduction/cache if projection load is large in-app too
- selected service/entity loading reduction if JPA materialization dominates
- move scoring/candidate selection closer to SQL if scoring/snapshot materialization dominates
- keep response cache unchanged because warm p95 is already good

## Current Recommendation

Open a small ranking instrumentation batch next, not a behavior-changing optimization batch.

Acceptance target for that next batch:

- no response contract change
- no ranking order change
- logs expose per-step duration only when cold compute crosses a threshold
- rerun cold/warm cache-tail measurement and app log observability
