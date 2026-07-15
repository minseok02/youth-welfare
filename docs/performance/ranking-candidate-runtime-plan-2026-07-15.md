# Ranking Candidate Runtime Plan - 2026-07-15

## Status

Implementation batch opened. Production behavior must stay unchanged until the guard flag is enabled.

## Trigger

The ranking cold timing instrumentation showed the largest app-side cold cost is Java scoring/sorting:

| Field | Median | Max |
| --- | ---: | ---: |
| `scoringSortMs` | 465.0ms | 889ms |
| `rankableSnapshotMs` | 132.5ms | 500ms |
| `projectionLoadMs` | 61.0ms | 95ms |

The current runtime scores and sorts all `13340` rankable snapshots. Candidate-mode evaluation selected `popular_recent_union=4000` as the first guarded runtime target.

## Why `popular_recent_union=4000`

The candidate evaluation moved through three stages:

1. Deterministic scenarios showed `popular_recent_union=1000` could work on fixed cases.
2. Random stress found `1000`, `1500`, and `2000` were not robust enough, and `3000` was better.
3. Expanded stress found `3000` still had 1 strict top100 miss, while `4000` and `5000` had none.

Wide-target result:

| Mode | Trials | Failures | Strict top100 misses | Min top100 recall | Avg candidate % |
| --- | ---: | ---: | ---: | ---: | ---: |
| `popular_recent_union=3000` | 240 | 0 | 1 | 99/100 | 16.92% |
| `popular_recent_union=4000` | 240 | 0 | 0 | 100/100 | 22.51% |
| `popular_recent_union=5000` | 240 | 0 | 0 | 100/100 | 28.13% |

Decision: use `4000` first because it removed the remaining strict top100 miss without paying the extra `5000` candidate cost.

## Implementation Plan

1. Add guarded runtime properties:
   - `policy.ranking.candidate.enabled`
   - `policy.ranking.candidate.mode`
   - `policy.ranking.candidate.target`
2. Keep the default path as full ranking.
3. Include the candidate mode and target in the local and Redis cache keys so flag changes cannot reuse stale ranking results.
4. Keep normalization stats based on the full snapshot set.
5. Build candidates with the same formula used by the evaluation scripts:
   - rough popularity pool: `55%`
   - recent pool: `25%`
   - source quota pool: `20%`
   - all policies with 7-day unique views are mandatory includes
   - fill shortfalls from rough popularity until target is reached
6. Score and sort only the candidate set.
7. Keep final scoring, exploration slots, DTO shape, selected-service lookup, projection lookup, local cache, and Redis cache behavior unchanged.
8. Add tests for:
   - candidate mode disabled keeps full snapshot scoring candidates
   - candidate mode enabled limits selected scoring candidates
   - mandatory unique-view and recent policies survive candidate reduction
   - cache keys differ between full and candidate modes

## Expected Performance

Current data:

| Metric | Before | Candidate mode |
| --- | ---: | ---: |
| Rankable snapshots loaded | 13340 | 13340 |
| Java scoring candidates | 13340 | about 4000 |
| Candidate volume | 100% | 29.99% on current data |
| `scoringSortMs` | median 465ms | expected materially lower |
| Warm ranking p95 | ~16-25ms | expected unchanged |

The expected endpoint improvement is smaller than the candidate reduction ratio because snapshot load, unique-view aggregation, selected-service lookup, projection lookup, and cache writes still remain.

## Rollout Rule

Deploy with the flag disabled first. Enable candidate mode only after health and smoke checks pass.

If post-enable checks show ranking correctness drift or unexpected latency regression, disable `policy.ranking.candidate.enabled` and rerun the same measurement scripts.

## Implementation Result

Implemented:

- guarded properties under `policy.ranking.candidate.*`
- default `enabled=false`
- mode `popular_recent_union`
- target `4000`
- separate local/Redis cache variants for full vs candidate ranking
- full-snapshot normalization with candidate-only scoring/sort
- cold timing fields:
  - `scoringCandidateCount`
  - `candidateModeEnabled`
  - `candidateMode`
  - `candidateTarget`
  - `candidateSelectionMs`

Correctness check on the current local runtime snapshot:

| Check | Result |
| --- | --- |
| candidate off ranking smoke | passed |
| candidate on ranking smoke | passed |
| candidate on top20 vs full top20 | same order, `20/20` overlap |
| `PolicyRankingServiceTest` | passed |

## Measurement Result

The first request-time implementation was not acceptable:

| Variant | Cold ranking p95 | Main issue | Artifact |
| --- | ---: | --- | --- |
| full/off initial | 1005.8ms | baseline | `tmp/performance/ranking-candidate-runtime-off-20260715/20260715T131418Z` |
| candidate/on first | 3331.5ms | rough-score comparator recalculated too much | `tmp/performance/ranking-candidate-runtime-on-20260715/20260715T131717Z` |
| candidate/on rough-score precomputed | 1959.7ms | full candidate sorting still too costly | `tmp/performance/ranking-candidate-runtime-on-optimized-20260715/20260715T132223Z` |

The implementation was then changed to use bounded priority queues for the rough, recent, and source-quota pools.

Final short local comparison:

| Variant | Cold ranking p50 | Cold ranking p95 | Warm ranking p95 | Artifact |
| --- | ---: | ---: | ---: | --- |
| candidate/on heap | 896.4ms | 1432.8ms | 17.2ms | `tmp/performance/ranking-candidate-runtime-on-heap-20260715/20260715T132817Z` |
| full/off final | 945.0ms | 1777.2ms | 15.4ms | `tmp/performance/ranking-candidate-runtime-off-final-20260715/20260715T133059Z` |

Cold timing examples:

| Variant | `scoringCandidateCount` | `candidateSelectionMs` | `scoringSortMs` | `totalMs` |
| --- | ---: | ---: | ---: | ---: |
| candidate/on heap sample 1 | 4000 | 338ms | 345ms | 1462ms |
| candidate/on heap sample 2 | 4000 | 304ms | 50ms | 877ms |
| candidate/on heap sample 3 | 4000 | 223ms | 36ms | 767ms |
| full/off final sample 1 | 13340 | 0ms | 397ms | 1840ms |
| full/off final sample 2 | 13340 | 0ms | 196ms | 918ms |
| full/off final sample 3 | 13340 | 0ms | 177ms | 880ms |

Interpretation:

- Candidate scoring/sort moved in the right direction.
- Request-time candidate construction is now much cheaper than the first attempt, but it still adds `~223-338ms` in representative cold samples.
- The short final run favored candidate mode on cold p50/p95, but the sample is small and variance is high.
- Keep the production default disabled until a longer edge/local run confirms the p95 improvement.

Next recommended step:

1. Run a longer local comparison with at least 7 cold samples per mode.
2. If candidate mode still wins, deploy with `POLICY_RANKING_CANDIDATE_ENABLED=false`.
3. Enable the flag only for the controlled measurement window.
4. If p95 is not consistently better, move candidate selection out of the request path with a precomputed ranking/candidate snapshot.

## Longer Local Comparison

The 7-cold-sample comparison was run after the short comparison because the short run had high variance.

Commands:

```bash
APP_BASE_URL=http://127.0.0.1:8082 COLD_RUNS=7 WARM_RUNS=7 CACHE_TTL_WAIT_SECONDS=31 WARM_DELAY_SECONDS=0.2 \
  POLICY_CACHE_TAIL_ROOT=tmp/performance/ranking-candidate-runtime-off-long-20260715 \
  bash deploy/performance/run-local-policy-cache-tail-baseline.sh

APP_BASE_URL=http://127.0.0.1:8082 COLD_RUNS=7 WARM_RUNS=7 CACHE_TTL_WAIT_SECONDS=31 WARM_DELAY_SECONDS=0.2 \
  POLICY_CACHE_TAIL_ROOT=tmp/performance/ranking-candidate-runtime-on-long-20260715 \
  bash deploy/performance/run-local-policy-cache-tail-baseline.sh
```

Artifacts:

- off: `tmp/performance/ranking-candidate-runtime-off-long-20260715/20260715T134806Z`
- on: `tmp/performance/ranking-candidate-runtime-on-long-20260715/20260715T135241Z`

Result:

| Variant | Cold ranking p50 | Cold ranking p95 | Cold ranking max | Warm ranking p95 |
| --- | ---: | ---: | ---: | ---: |
| full/off | 559.8ms | 673.9ms | 689.5ms | 12.5ms |
| candidate/on | 1104.7ms | 1724.8ms | 1942.2ms | 14.6ms |

Correctness check:

| Check | Result |
| --- | --- |
| off/on top20 same order | true |
| off/on top20 overlap | 20/20 |

Candidate on log samples:

| Sample | `scoringCandidateCount` | `candidateSelectionMs` | `scoringSortMs` | `totalMs` |
| --- | ---: | ---: | ---: | ---: |
| 1 | 4000 | 481ms | 71ms | 1915ms |
| 2 | 4000 | 400ms | 52ms | 1190ms |
| 3 | 4000 | 568ms | 47ms | 1102ms |
| 4 | 4000 | 220ms | 50ms | 712ms |
| 5 | 4000 | 279ms | 65ms | 764ms |
| 6 | 4000 | 583ms | 41ms | 1088ms |
| 7 | 4000 | 195ms | 35ms | 543ms |

Final decision for this batch:

- Do not enable request-time candidate mode.
- Keep the code guarded and default-disabled.
- The candidate mode preserves the current top20 for this snapshot, but request-time candidate construction costs more than it saves in the longer run.
- The next optimization should move ranking candidate selection/scoring out of the request path.

Next recommended design:

1. Precompute a ranked snapshot periodically.
2. Store the top N service IDs and scores in Redis or a compact DB table.
3. Make `/api/policies/ranking` read the precomputed top IDs and hydrate only the requested page.
4. Keep full request-time ranking as fallback when the snapshot is missing or stale.
