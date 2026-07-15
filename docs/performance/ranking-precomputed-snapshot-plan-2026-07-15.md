# Ranking Precomputed Snapshot Plan - 2026-07-15

## Status

Implementation batch opened. Production behavior must stay unchanged until snapshot read is explicitly enabled.

## Trigger

Request-time candidate reduction preserved ranking quality, but the longer local comparison rejected it:

| Variant | Cold ranking p50 | Cold ranking p95 | Cold ranking max |
| --- | ---: | ---: | ---: |
| full/off | 559.8ms | 673.9ms | 689.5ms |
| candidate/on | 1104.7ms | 1724.8ms | 1942.2ms |

The failure reason was request-time candidate construction cost. Therefore the next optimization should move ranking computation outside the request path.

## Goal

Precompute the ranking response periodically and let `/api/policies/ranking` read an already-ranked response for the configured snapshot size.

Expected request path on snapshot hit:

1. Read one Redis snapshot key when the requested size equals the configured snapshot size.
2. Return that precomputed response.
3. Return the existing `PolicyRankingResponse` shape.

The fallback path remains the current full request-time ranking.

## Guarded Runtime Flags

Planned properties:

- `policy.ranking.snapshot.read-enabled`
- `policy.ranking.snapshot.refresh-enabled`
- `policy.ranking.snapshot.max-size`
- `policy.ranking.snapshot.ttl-seconds`
- `policy.ranking.snapshot.refresh-fixed-delay-ms`
- `policy.ranking.snapshot.refresh-initial-delay-ms`

Defaults:

- read disabled
- refresh disabled
- snapshot size `20`
- TTL `300s`
- fixed delay `300000ms`
- initial delay `60000ms`

## Storage

Use Redis first:

- key: `policy:ranking:snapshot:v1:full:20`
- payload:
  - `generatedAtEpochMillis`
  - `responses`

Do not introduce a DB table in the first pass. DB fallback can be added only if Redis snapshot durability becomes a real operational problem.

## Request Behavior

1. Local per-size cache hit: return.
2. Redis per-size cache hit: return.
3. Snapshot read enabled, requested size equals configured snapshot size, and snapshot hit: return the snapshot and populate per-size caches.
4. Snapshot missing/stale/parse error/Redis failure/size mismatch: fallback to current full request-time ranking.

## Refresh Behavior

The scheduled refresh:

1. Checks `AppSchedulerGate`.
2. Checks `policy.ranking.snapshot.refresh-enabled`.
3. Computes the current full ranking at `snapshot.max-size`.
4. Writes the Redis snapshot with TTL.

The refresh job does not change public response semantics by itself. Public ranking only uses the snapshot when read is enabled.

Important correctness note:

- The existing exploration slot logic is size-dependent.
- A snapshot computed for size `100` cannot be safely sliced down to size `20` and treated as equivalent to `getRanking(20)`.
- Therefore the first implementation uses an exact configured snapshot size, default `20`, and falls back for other request sizes.

## Acceptance Criteria

| Check | Required |
| --- | --- |
| default behavior | unchanged |
| snapshot read hit | no full rankable snapshot repository call |
| snapshot miss | fallback to full ranking |
| Redis read/write failure | fallback or skip refresh without breaking ranking |
| response shape | unchanged |
| top20 correctness | same as snapshot payload order |
| local health/smoke | pass |

## Expected Performance

Current accepted full/off long local ranking:

| Metric | Current |
| --- | ---: |
| cold ranking p50 | 559.8ms |
| cold ranking p95 | 673.9ms |
| warm ranking p95 | 12.5ms |

Expected snapshot-hit ranking:

| Metric | Expected |
| --- | ---: |
| cold ranking p95 | 100-250ms |
| warm ranking p95 | similar to current warm path |

The exact p95 depends on Redis read, JSON parse, and response size. The important win is removing rankable snapshot loading, unique-view aggregation, Java scoring/sort, selected-service hydration, and projection hydration from the request path.

## Implementation Result

Implemented:

- guarded snapshot read path with default `policy.ranking.snapshot.read-enabled=false`
- guarded scheduled refresh with default `policy.ranking.snapshot.refresh-enabled=false`
- Redis snapshot payload at `policy:ranking:snapshot:v1:full:20`
- fallback to current full request-time ranking on miss, parse failure, Redis failure, or request-size mismatch
- tests for snapshot hit, miss fallback, and scheduled refresh write

Important correction during validation:

- The first measurement used a size `100` snapshot and sliced top 20.
- That was fast, but incorrect: top20 overlap was `19/20`.
- Root cause: existing exploration slot behavior is request-size-dependent.
- Fix: snapshot is only used when requested size equals configured snapshot size. Default snapshot size is `20`.

## Local Measurement

Commands:

```bash
APP_BASE_URL=http://127.0.0.1:8082 COLD_RUNS=7 WARM_RUNS=7 CACHE_TTL_WAIT_SECONDS=31 WARM_DELAY_SECONDS=0.2 \
  POLICY_CACHE_TAIL_ROOT=tmp/performance/ranking-snapshot-runtime-off-20260715 \
  bash deploy/performance/run-local-policy-cache-tail-baseline.sh

APP_BASE_URL=http://127.0.0.1:8082 COLD_RUNS=7 WARM_RUNS=7 CACHE_TTL_WAIT_SECONDS=31 WARM_DELAY_SECONDS=0.2 \
  POLICY_CACHE_TAIL_ROOT=tmp/performance/ranking-snapshot-runtime-on-size20-20260715 \
  bash deploy/performance/run-local-policy-cache-tail-baseline.sh
```

Artifacts:

- off: `tmp/performance/ranking-snapshot-runtime-off-20260715/20260715T142254Z`
- snapshot on: `tmp/performance/ranking-snapshot-runtime-on-size20-20260715/20260715T143638Z`

Result:

| Variant | Cold ranking p50 | Cold ranking p95 | Cold ranking max | Warm ranking p95 |
| --- | ---: | ---: | ---: | ---: |
| full/off | 810.1ms | 1185.5ms | 1248.1ms | 21.1ms |
| snapshot/on size 20 | 22.1ms | 44.1ms | 51.1ms | 14.0ms |

Reduction:

| Metric | Reduction |
| --- | ---: |
| cold ranking p50 | 97.3% |
| cold ranking p95 | 96.3% |
| cold ranking max | 95.9% |
| warm ranking p95 | 33.6% |

Correctness:

| Check | Result |
| --- | --- |
| off/on top20 same order | true |
| off/on top20 overlap | 20/20 |

Refresh log:

```text
[PolicyRankingSnapshot] refresh complete resultCount=20 snapshotCount=13340 scoringCandidateCount=13340 totalMs=2519
```

Decision:

- Snapshot approach is the first ranking optimization in this sequence that is both fast and correct on the local measured path.
- Keep default disabled in code.
- Next deployment should ship disabled first, then enable `POLICY_RANKING_SNAPSHOT_REFRESH_ENABLED=true` and `POLICY_RANKING_SNAPSHOT_READ_ENABLED=true` for a controlled measurement window.

## Production Rollout Measurement

Rollout date: 2026-07-15

Commit deployed:

- `ad16e288c2f8688b420b7b8d0a968053a4a78c1b`

Deployment state:

| Node | Instance | Scheduler | Snapshot read | Snapshot refresh | Notes |
| --- | --- | --- | --- | --- | --- |
| primary | `i-0b8d95e454df5e0f0` | true | true | true | Generates the Redis snapshot. |
| secondary | `i-0e8a4cc599c1148c8` | false | true | false | Reads the shared Redis snapshot. |

Guard state:

- `POLICY_RANKING_CANDIDATE_ENABLED=false`
- `POLICY_RANKING_SNAPSHOT_MAX_SIZE=20`
- request sizes other than `20` still fall back to full request-time ranking

Pre-enable checks:

- primary deployed with default snapshot flags off and health `UP`
- secondary deployed through SSM to the same commit and health `UP`
- ALB target group `youth-welfare-web-tg` had both targets `healthy`
- public smoke passed:
  - `https://youthmoa.kr/` -> `200`
  - `https://youthmoa.kr/api/policies?page=0&size=20` -> `200`
  - `https://youthmoa.kr/api/policies/ranking?size=20` -> `200`

Refresh-only check:

- primary was first restarted with `read=false`, `refresh=true`
- refresh log:

```text
[PolicyRankingSnapshot] refresh complete resultCount=20 snapshotCount=13340 scoringCandidateCount=13340 totalMs=2291
```

Correctness checks:

| Check | Result |
| --- | --- |
| initial primary read-on top20 vs read-off full top20 | same order, `20/20` |
| latest edge read-on top20 vs current primary read-off full top20 | same order, `20/20` |

During validation, the edge snapshot was compared once against an earlier read-off full artifact and showed an adjacent swap between `2612` and `4382`. That was not accepted as a correctness failure because the primary snapshot refresh had already recomputed the snapshot. A current read-off full ranking was captured after the 30-second per-size Redis cache TTL expired, and it matched the latest edge snapshot exactly:

```text
844,15382,11160,7751,10449,4596,7584,14916,1389,5522,234,3967,15391,14912,4382,2612,2666,15287,15250,15393
```

Production measurement commands:

```bash
COLD_RUNS=7 WARM_RUNS=7 CACHE_TTL_WAIT_SECONDS=31 WARM_DELAY_SECONDS=0.2 \
  POLICY_CACHE_TAIL_ROOT=tmp/performance/ranking-snapshot-prod-local-on-20260715 \
  APP_BASE_URL=http://127.0.0.1:8082 \
  bash deploy/performance/run-local-policy-cache-tail-baseline.sh

COLD_RUNS=7 WARM_RUNS=7 CACHE_TTL_WAIT_SECONDS=31 WARM_DELAY_SECONDS=0.2 \
  POLICY_CACHE_TAIL_ROOT=tmp/performance/ranking-snapshot-prod-edge-on-20260715 \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-policy-cache-tail-baseline.sh
```

Artifacts:

- local target: `tmp/performance/ranking-snapshot-prod-local-on-20260715/20260715T152357Z`
- external edge: `tmp/performance/ranking-snapshot-prod-edge-on-20260715/20260715T152747Z`
- correctness captures: `tmp/performance/prod-ranking-snapshot-rollout-20260715`

Measured production result:

| Path | Cold ranking p50 | Cold ranking p95 | Cold ranking max | Warm ranking p95 |
| --- | ---: | ---: | ---: | ---: |
| local target read-on | 23.6ms | 35.0ms | 37.1ms | 14.8ms |
| external edge read-on | 36.0ms | 42.5ms | 44.3ms | 27.3ms |

Comparison to pre-snapshot local full/off measurement:

| Metric | Before | Production read-on local | Reduction |
| --- | ---: | ---: | ---: |
| cold ranking p50 | 810.1ms | 23.6ms | 97.1% |
| cold ranking p95 | 1185.5ms | 35.0ms | 97.0% |
| cold ranking max | 1248.1ms | 37.1ms | 97.0% |
| warm ranking p95 | 21.1ms | 14.8ms | 29.8% |

Comparison to the previous edge cold ranking checkpoint (`828.5ms` p95) shows the external path cold ranking p95 at `42.5ms`, a `94.9%` reduction. Warm edge ranking is small and network-sensitive; it measured `27.3ms` p95 in this run, so the accepted win is the cold ranking tail removal.

Final post-rollout checks:

- primary actuator health: `200`
- public root/list/ranking smoke: `200`
- ALB target health:
  - `i-0b8d95e454df5e0f0`: `healthy`
  - `i-0e8a4cc599c1148c8`: `healthy`

Decision:

- Keep snapshot read enabled for the `size=20` ranking path.
- Keep snapshot refresh enabled only on the scheduler node.
- Keep request-time candidate mode disabled.
- Recheck after normal traffic because ranking inputs can shift between refreshes; compare snapshot against a current full ranking, not against a stale artifact.
