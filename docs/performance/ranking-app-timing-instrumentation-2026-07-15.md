# Ranking App Timing Instrumentation - 2026-07-15

## Status

Completed.

This is an observability batch, not a ranking behavior optimization.

## Why This Batch Exists

The latest repeated cache-tail measurement showed ranking cold latency is still the strongest candidate:

| Metric | Latest value |
| --- | ---: |
| local ranking cold p95 | 804.2ms |
| local ranking warm p95 | 6.9ms |
| edge ranking cold p95 | 828.5ms |
| edge ranking warm p95 | 20.8ms |

The DB-only ranking breakdown did not explain the full endpoint tail:

| Step | Latest value |
| --- | ---: |
| rankable snapshot EXPLAIN | 12.783ms |
| unique-view aggregation EXPLAIN | 0.287ms |
| selected services EXPLAIN | 0.405ms |
| projection base EXPLAIN | 50.214ms |
| projection terms EXPLAIN | 0.475ms |
| projection facts EXPLAIN | 0.417ms |
| scoring/sorting simulation | 130.580ms |

Decision from the previous checkpoint:

- do not change ranking math or response contract yet
- add app-side substep timing first
- rerun the same cold/warm cache-tail measurement
- pick the next optimization only after live substep timings show the largest cost

## Plan Before Code Change

1. Keep `PolicyRankingService` response behavior unchanged.
2. Add low-cardinality timing around cold ranking compute only:
   - rankable snapshot repository call
   - unique-view repository call
   - scoring and sorting
   - exploration slot selection
   - selected service load
   - projection load
   - DTO build
   - local cache write
   - Redis cache write
3. Log only after cache miss and only when cold compute total exceeds a threshold.
4. Keep local and Redis cache TTL behavior unchanged.
5. Verify with the existing `PolicyRankingServiceTest`.
6. Deploy, rerun cache-tail, and inspect app logs for the new timing line.

## Expected Performance Impact

| Area | Expected impact | Reason |
| --- | ---: | --- |
| ranking response data/order | 0 | no ranking math or sorting change |
| ranking cold p95 | about 0ms to +1ms | a few `System.nanoTime()` calls and one conditional log on cold misses |
| ranking warm p95 | 0 | warm local/Redis cache path remains unchanged |
| DB load | 0 | no query or transaction change |
| log volume | low | only slow cold compute emits the timing line |

This batch should not be judged by latency improvement. It should be judged by whether the next rerun identifies the dominant live substep.

## Acceptance Criteria

- `PolicyRankingServiceTest` passes.
- Ranking endpoint still returns valid data.
- No app error/warn lines appear during the measurement window.
- App logs include `PolicyRankingColdTiming` for slow cold ranking computes.
- Cache-tail measurement remains comparable to the previous run.

## Implementation

Changed `PolicyRankingService` only:

- `getRanking` still checks JVM-local cache first and Redis second.
- Cold miss now computes `RankingComputation` with timing fields.
- Local cache write and Redis cache write are timed after compute.
- A `PolicyRankingColdTiming` info log is emitted only when cold total time is at least `policy.ranking.cold-timing-log-threshold-ms`, default `300ms`.
- Ranking score math, exploration logic, response DTO, local cache TTL, and Redis cache TTL are unchanged.

Timing fields:

| Field | Meaning |
| --- | --- |
| `rankableSnapshotMs` | `findRankableSnapshots` repository/materialization time |
| `uniqueViewMs` | 7-day unique-view aggregation repository time |
| `scoringSortMs` | max calculation, normalization, score calculation, sorting, and score map build |
| `explorationMs` | exploration slot selection |
| `selectedServiceLoadMs` | selected entity load by IDs |
| `projectionLoadMs` | selected response projection load |
| `dtoBuildMs` | `PolicyRankingResponse` construction |
| `localCacheWriteMs` | JVM-local ranking cache write |
| `redisCacheWriteMs` | Redis shared ranking cache write |

## Verification

Unit test:

```bash
cd backend
./gradlew --no-daemon test --tests 'com.example.welfare.policy.service.PolicyRankingServiceTest'
```

Result:

- `BUILD SUCCESSFUL`
- `5 actionable tasks: 3 executed, 2 up-to-date`

Docker build/deploy on primary:

```bash
docker compose -f docker-compose.prod.elasticache.yml up -d --build app
```

Result:

- `bootJar -x test` successful
- container recreated
- `GET /actuator/health` returned `{"status":"UP"}`

Functional smoke on primary:

| Endpoint | Result |
| --- | --- |
| `GET /api/policies/ranking?size=5` | `5` items, first service `844` |
| `POST /api/policies/search` keyword `청년`, size `5` | `5` items, total `1476`, first service `844` |
| `GET /api/policies?size=5` | `5` items, total `13340`, first service `15391` |
| `GET /actuator/health` | `UP` |

Secondary sync:

- SSM deploy command pulled commit `0d1e209e6537fadd0625f35215aa922c29d49e26`.
- Docker compose recreated `youth-welfare-app`.
- Verification command on secondary returned:
  - git HEAD `0d1e209e6537fadd0625f35215aa922c29d49e26`
  - compose status `Up ... (healthy)`
  - `GET /actuator/health` returned `{"status":"UP"}`

ALB/edge smoke after both instances were updated:

| Check | Result |
| --- | --- |
| ALB target `i-0e8a4cc599c1148c8` | `healthy` |
| ALB target `i-0b8d95e454df5e0f0` | `healthy` |
| `https://youthmoa.kr/api/policies/ranking?size=5` | `5` items, first service `844` |
| `POST https://youthmoa.kr/api/policies/search` keyword `청년`, size `5` | `5` items, total `1476`, first service `844` |
| `https://youthmoa.kr/api/policies?size=5` | `5` items, total `13340`, first service `15391` |

## Primary Measurement

First local cache-tail run after deploy:

- artifact: `tmp/stability/ranking-app-timing-local-20260715/20260715T072259Z`
- app log artifact: `tmp/stability/ranking-app-timing-app-log-20260715/20260715T072449Z`

| Phase | Ranking p50 | Ranking p95 | Ranking max | Search p95 |
| --- | ---: | ---: | ---: | ---: |
| cold | 1003.6ms | 1625.9ms | 1695.0ms | 920.0ms |
| warm | 13.2ms | 16.6ms | 17.2ms | 27.9ms |

This run includes post-deploy connection/JPA warm-up. The three cold ranking samples visibly decreased as the app warmed:

| Sample | `totalMs` | `rankableSnapshotMs` | `scoringSortMs` | `projectionLoadMs` |
| --- | ---: | ---: | ---: | ---: |
| 1 | 1665ms | 500ms | 889ms | 65ms |
| 2 | 978ms | 274ms | 496ms | 78ms |
| 3 | 707ms | 132ms | 434ms | 52ms |

Warmed local cache-tail rerun:

- artifact: `tmp/stability/ranking-app-timing-local-warmed-20260715/20260715T072459Z`
- app log artifact: `tmp/stability/ranking-app-timing-app-log-warmed-20260715/20260715T072649Z`

| Phase | Ranking p50 | Ranking p95 | Ranking max | Search p95 |
| --- | ---: | ---: | ---: | ---: |
| cold | 578.4ms | 1005.0ms | 1052.4ms | 190.1ms |
| warm | 14.1ms | 16.4ms | 16.9ms | 26.1ms |

All API requests in the app-log window returned `200`.

Observed app-log counters:

| Signal | First run | Warmed window |
| --- | ---: | ---: |
| raw error lines | 0 | 0 |
| raw warn lines | 1 | 1 |
| API request count | 18 | 36 |
| API 200 count | 18 | 36 |

The single warn line was a `PolicySearchService` slow search observation during cold measurement, not a functional failure.

## Timing Interpretation

Across six cold ranking timing lines after primary deploy:

| Field | Min | Median | Max |
| --- | ---: | ---: | ---: |
| `totalMs` | 559ms | 842.5ms | 1665ms |
| `rankableSnapshotMs` | 113ms | 132.5ms | 500ms |
| `uniqueViewMs` | 4ms | 6.0ms | 40ms |
| `scoringSortMs` | 300ms | 465.0ms | 889ms |
| `explorationMs` | 53ms | 77.5ms | 130ms |
| `selectedServiceLoadMs` | 5ms | 7.0ms | 18ms |
| `projectionLoadMs` | 45ms | 61.0ms | 95ms |
| `dtoBuildMs` | 0ms | 0.0ms | 3ms |
| `localCacheWriteMs` | 0ms | 0.0ms | 0ms |
| `redisCacheWriteMs` | 3ms | 4.5ms | 15ms |

Current conclusion:

- The cache path is still healthy; warm ranking p95 stayed around `16ms`.
- Redis cache write and DTO build are not meaningful bottlenecks.
- Unique-view aggregation is not a meaningful bottleneck in the current data.
- Projection load is measurable but not the largest cost.
- The largest live app-side cost is scoring/sorting over `13340` rankable snapshots.
- The next behavior-changing optimization should reduce Java scoring/sorting work or shrink the candidate set before Java scoring.

## Edge Measurement After Secondary Sync

Artifact:

- `tmp/stability/ranking-app-timing-edge-20260715/20260715T073334Z`

| Phase | Ranking p50 | Ranking p95 | Ranking max | Search p95 |
| --- | ---: | ---: | ---: | ---: |
| cold | 597.6ms | 633.3ms | 637.3ms | 553.3ms |
| warm | 24.1ms | 24.6ms | 24.6ms | 35.0ms |

Result:

- `policy_cache_tail_baseline=passed`
- no non-200 samples
- ranking warm edge path remains healthy after both instances were updated
- cold ranking still remains above warm by a large margin, matching the instrumentation conclusion

## Next Candidate

Plan the next optimization around candidate reduction, not projection first.

Likely direction:

1. Keep the current score formula as the correctness reference.
2. Load a bounded rankable candidate set from DB using a conservative pre-rank/order expression.
3. Run the existing Java scoring/exploration only over that bounded candidate set.
4. Compare ranking overlap/order against the current full-snapshot implementation before accepting.
