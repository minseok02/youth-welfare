# Performance Optimization Log

Last updated: 2026-07-15

This document records what was optimized, which baseline numbers triggered the work, and whether the follow-up measurement is accepted or still pending.

## How To Read

- `Closed Batch`: why the batch was opened, what changed, and which accepted server rerun closed it
- `Observed deltas`: the concrete before/after numbers that justified keeping or replacing the change
- `Interpretation`: whether the batch is actually closed or only partially useful
- `Comparison Table`: one-line before/after ledger for fast review across multiple batches

## Open Batch

### 2026-07-15: ranking candidate mode evaluation

Trigger:

- Ranking cold timing showed `scoringSortMs` as the largest live app-side cost.
- Current ranking cold compute scores and sorts all `13340` rankable snapshots.
- Candidate reduction is likely required before more data sources increase rankable volume further.

Plan:

1. Keep production ranking behavior unchanged.
2. Treat the current full-snapshot ranking as the reference answer.
3. Compare multiple candidate-reduction strategies:
   - simple pre-rank top K
   - source type quota
   - popular + recent + unique-view union
   - conservative wide candidate sets
4. Reject any mode that fails full top 20 candidate recall.
5. Use the measured recall/overlap result to choose the first runtime optimization.

Tracking document: [ranking-candidate-mode-evaluation-2026-07-15.md](ranking-candidate-mode-evaluation-2026-07-15.md)

Sensitivity update:

- Added `deploy/performance/run-local-ranking-candidate-sensitivity-evaluation.sh`.
- Tested current data plus synthetic changes:
  - unique-view surge in long-tail policies
  - external/API view spike in long-tail policies
  - 3000 recent GOV24 additions
  - 4000 recent policies from a new source
  - mixed GOV24/new-source growth with extra unique-view winners
- `popular_recent_union=1000` passed all scenarios:
  - top20 recall stayed `20/20`
  - top50 recall stayed `50/50`
  - top100 recall stayed `100/100`
  - final top20 overlap stayed `20/20` on current/unique/external scenarios and `19/20` on large growth scenarios
- `simple_top_k` and plain `source_quota` failed multiple sensitivity scenarios.

Updated recommendation:

- Initial fixed-scenario recommendation was `popular_recent_union=1000`.
- Do not implement simple global top K first.
- Rerun candidate sensitivity evaluation after real source growth or ranking weight changes.

Random stress update:

- Added `deploy/performance/run-local-ranking-candidate-random-stress-evaluation.sh`.
- Ran 240 randomized trials across seeds `20260715`, `20260716`, and `20260717`.
- Randomized trials mixed source floods, new synthetic sources, recent-policy growth, unique-view spikes, API-view spikes, and view spikes.
- Aggregated result:
  - `popular_recent_union=1000`: 2 failures, 4 strict top100 misses
  - `popular_recent_union=1500`: 0 failures, 2 strict top100 misses
  - `popular_recent_union=2000`: 0 failures, 1 strict top100 miss
  - `popular_recent_union=3000`: 0 failures, 0 strict top100 misses
  - `simple_top_k=3000`: 67 failures
  - `source_quota=3000`: 96 failures
- Intermediate candidate after the first random batch: `popular_recent_union=3000`.
- This was superseded by the expanded random stress update below.

Expanded random stress update:

- Reran three more 80-trial seeds (`20260718`, `20260719`, `20260720`) for 480 total randomized trials.
- The 480-trial aggregate kept `popular_recent_union=3000` above the hard threshold, but found 1 strict top100 miss:
  - `popular_recent_union=1000`: 6 failures, 10 strict top100 misses
  - `popular_recent_union=1500`: 3 failures, 5 strict top100 misses
  - `popular_recent_union=2000`: 1 failure, 3 strict top100 misses
  - `popular_recent_union=3000`: 0 failures, 1 strict top100 miss
- Added wide-target checks for `popular_recent_union=4000` and `5000` across seeds `20260718`, `20260719`, and `20260720`.
- Wide-target 240-trial aggregate:
  - `popular_recent_union=3000`: 0 failures, 1 strict top100 miss, avg candidate `16.92%`
  - `popular_recent_union=4000`: 0 failures, 0 strict top100 misses, avg candidate `22.51%`
  - `popular_recent_union=5000`: 0 failures, 0 strict top100 misses, avg candidate `28.13%`
- Reran deterministic sensitivity with recommended target `4000`; all fixed scenarios passed with top100 recall `100/100`.
- Updated first guarded runtime candidate: `popular_recent_union=4000`.
- `popular_recent_union=3000` remains a possible later reduction only after a 4000-target rollout is measured and accepted.

Runtime implementation update:

- Added guarded runtime candidate mode for ranking with default `POLICY_RANKING_CANDIDATE_ENABLED=false`.
- Implemented `popular_recent_union=4000` using full-snapshot normalization and candidate-only scoring/sort.
- Added separate cache variants so full and candidate ranking do not share local/Redis cache entries.
- Initial request-time candidate selection was rejected because it made cold ranking worse:
  - full/off initial cold ranking p95 `1005.8ms`
  - candidate/on first cold ranking p95 `3331.5ms`
- Optimized candidate selection by precomputing rough scores and then using bounded heaps for rough, recent, and source-quota pools.
- Final short local comparison:
  - candidate/on heap cold ranking p50 `896.4ms`, p95 `1432.8ms`, warm p95 `17.2ms`
  - full/off final cold ranking p50 `945.0ms`, p95 `1777.2ms`, warm p95 `15.4ms`
- Current local top20 check: candidate/on matched full/off in the same order with `20/20` overlap.
- Keep production default disabled until a longer comparison confirms p95 improvement. If request-time candidate selection is not consistently better, move this optimization to a precomputed ranking/candidate snapshot.

Longer runtime comparison:

- Ran 7 cold samples per mode after the short run.
- Full/off artifact: `tmp/performance/ranking-candidate-runtime-off-long-20260715/20260715T134806Z`
- Candidate/on artifact: `tmp/performance/ranking-candidate-runtime-on-long-20260715/20260715T135241Z`
- Result:
  - full/off cold ranking p50 `559.8ms`, p95 `673.9ms`, max `689.5ms`
  - candidate/on cold ranking p50 `1104.7ms`, p95 `1724.8ms`, max `1942.2ms`
  - warm ranking stayed close: full/off p95 `12.5ms`, candidate/on p95 `14.6ms`
- Correctness still passed: candidate/on top20 matched full/off in the same order with `20/20` overlap.
- Decision: do not enable request-time candidate mode. Keep the guarded code disabled and move the next ranking optimization toward a precomputed ranking/candidate snapshot.

Precomputed ranking snapshot update:

- Added guarded Redis ranking snapshot read/refresh path, default disabled.
- Initial size `100` snapshot was fast but rejected because slicing it down to top20 produced `19/20` overlap. Existing exploration slots are request-size-dependent.
- Changed first snapshot target to exact size `20`; other request sizes fall back to full ranking.
- Local 7-cold-sample comparison:
  - full/off cold ranking p50 `810.1ms`, p95 `1185.5ms`, max `1248.1ms`
  - snapshot/on size 20 cold ranking p50 `22.1ms`, p95 `44.1ms`, max `51.1ms`
  - warm ranking p95 `21.1ms -> 14.0ms`
- Reduction:
  - cold p50 `97.3%`
  - cold p95 `96.3%`
  - cold max `95.9%`
- Correctness passed: snapshot/on top20 matched full/off in the same order with `20/20` overlap.
- Decision: snapshot approach is the first ranking optimization in this sequence that is both fast and correct on the measured local path. Ship disabled first, then enable refresh/read for a controlled measurement window.

Production rollout follow-up:

- Deployed commit `ad16e288c2f8688b420b7b8d0a968053a4a78c1b` to both ALB targets.
- Primary node (`i-0b8d95e454df5e0f0`) runs scheduler plus `POLICY_RANKING_SNAPSHOT_READ_ENABLED=true` and `POLICY_RANKING_SNAPSHOT_REFRESH_ENABLED=true`.
- Secondary node (`i-0e8a4cc599c1148c8`) keeps scheduler disabled and runs `POLICY_RANKING_SNAPSHOT_READ_ENABLED=true`, `POLICY_RANKING_SNAPSHOT_REFRESH_ENABLED=false`.
- Request-time candidate mode remains disabled.
- Refresh log confirmed `resultCount=20`, `snapshotCount=13340`.
- Production correctness:
  - initial primary read-on vs read-off full top20 matched in the same order with `20/20`
  - latest edge read-on vs current primary read-off full top20 matched in the same order with `20/20`
  - one stale-artifact comparison showed an adjacent swap after the scheduled refresh; the current full recomputation matched the latest snapshot, so this was classified as baseline staleness, not a snapshot correctness failure
- Production 7-cold-sample read-on measurement:
  - local target cold ranking p50 `23.6ms`, p95 `35.0ms`, max `37.1ms`, warm p95 `14.8ms`
  - external edge cold ranking p50 `36.0ms`, p95 `42.5ms`, max `44.3ms`, warm p95 `27.3ms`
- Reduction from local full/off p95 `1185.5ms` to production local read-on p95 `35.0ms`: `97.0%`.
- Reduction from previous edge cold ranking p95 `828.5ms` to production edge read-on p95 `42.5ms`: `94.9%`.
- Final smoke: root/list/ranking returned `200`; ALB targets `i-0b8d95e454df5e0f0` and `i-0e8a4cc599c1148c8` were both `healthy`.
- Artifacts:
  - `tmp/performance/ranking-snapshot-prod-local-on-20260715/20260715T152357Z`
  - `tmp/performance/ranking-snapshot-prod-edge-on-20260715/20260715T152747Z`
  - `tmp/performance/prod-ranking-snapshot-rollout-20260715`

Stability/regression recheck:

- Re-ran cache-tail stability after the rollout:
  - local target cold ranking p50 `24.3ms`, p95 `30.1ms`, max `30.3ms`, warm p95 `13.4ms`
  - external edge cold ranking p50 `30.8ms`, p95 `49.6ms`, max `50.6ms`, warm p95 `22.4ms`
- Accepted reduction now uses the stability local p95 `30.1ms`:
  - local full/off p95 `1185.5ms -> 30.1ms`, `97.5%` lower
  - previous edge cold p95 `828.5ms -> 49.6ms`, `94.0%` lower
- Public regression checks:
  - ranking sizes `5`, `20`, `30` returned the requested item counts
  - policy list and keyword search returned `20` items
  - runtime API smoke over `https://youthmoa.kr` passed signup/login/refresh/recommendation refresh/bookmark/logout/token-revoke checks
- Snapshot logs in the checked window contained refresh-complete entries only, with no snapshot read/refresh failure lines.
- ALB targets remained healthy.
- Current baseline was updated in `performance-baseline-current.md`.
- Stability artifacts:
  - `tmp/performance/ranking-snapshot-stability-local-20260715/20260715T154113Z`
  - `tmp/performance/ranking-snapshot-stability-edge-20260715/20260715T154501Z`
  - `tmp/performance/ranking-snapshot-stability-correctness-20260715`
  - `tmp/prod-runtime-smoke/snapshot-stability-20260715-retry-env`

Post-snapshot broader baseline and env formalization:

- Snapshot runtime flags were moved from temporary runtime env files into `.env.runtime.production` on both nodes.
- Primary backup: `.env.runtime.production.bak-snapshot-20260715T160904Z`.
- Secondary backup: `.env.runtime.production.bak-snapshot-20260715T160805Z`.
- Formalized state:
  - primary: scheduler true, snapshot read true, snapshot refresh true
  - secondary: scheduler false, snapshot read true, snapshot refresh false
  - request-time candidate mode false on both nodes
- Re-ran API latency, DB query, and edge baseline after snapshot was accepted.
- Current broader result:
  - local API ranking p95 `10.2ms`
  - edge API ranking p95 `47.7ms`
  - edge API `policy_list_default` p95 `751.0ms`, max `927.8ms`
  - direct primary local list first sample about `755ms`; secondary local list samples stayed about `102ms` to `181ms`
  - DB representative `policy_list_created_at` execution `15.113ms`
- Decision: do not optimize ranking/search next. Open the next investigation on policy list default cold/tail and separate app timing, Redis hit/miss, DB, serialization, and target behavior before changing code.
- Rollback runbook added: `docs/performance/ranking-snapshot-rollback-runbook-2026-07-15.md`.
- Broader baseline document added: `docs/performance/post-snapshot-current-baseline-2026-07-15.md`.

Policy list timing instrumentation follow-up:

- Added policy list app-side timing instrumentation in `0dd5fac7562c33902c95f1d519e84c77fda3c939`.
- Tracking document: `docs/performance/policy-list-timing-instrumentation-2026-07-15.md`.
- Request samples after deploy:
  - edge list p50 `121.2ms`, p95 `728.7ms`, max `728.7ms`
  - primary loopback p50 `110.1ms`, p95 `197.3ms`, max `197.3ms`
  - secondary loopback p50 `104.4ms`, p95 `171.7ms`, max `171.7ms`
- First-tail app timing was mixed:
  - primary repository rows `162ms`, projection `79ms`, region `49ms`, controller total `404ms`
  - secondary repository rows `198ms`, projection `155ms`, region `64ms`, controller total `520ms`
- Warm repeated app timing narrowed to presentation projection lookup:
  - repository usually fell to about `14ms` to `40ms`
  - region labels usually fell to about `2ms` to `10ms`
  - projection lookup repeatedly stayed about `47ms` to `114ms`
- Decision: do not optimize the default list rows query first. Inspect and optimize the projection lookup path used by `PolicyPresentationReadService`.

Policy list projection query optimization plan:

- Tracking document: `docs/performance/policy-list-projection-query-optimization-2026-07-15.md`.
- Root cause candidate: `CanonicalRecommendationReadModelRepository.baseRows()` aggregates `service_taxonomy_summary_slots` six times by slot key before restricting to the 20 requested service ids.
- Before SQL measurement on the current first page ids:
  - current `baseRows()` execution `50.649ms`
  - filtered single summary-slot aggregate candidate execution `0.340ms`
  - `taxonomyTermRows()` execution `0.307ms`
  - `factRows()` execution `0.141ms`
- Planned change: keep response fields and fallback order unchanged, but constrain summary slots by `service_id IN (:serviceIds)` first and pivot the six labels in one aggregate.

Policy list projection query optimization result:

- Implemented and deployed commit `65f317e344df54ed3e5f1d8597e7112ec20d3460` to both ALB targets.
- Focused tests passed.
- SQL equivalence on the first page ids passed:
  - old minus new `0`
  - new minus old `0`
- New `baseRows()` execution check: `0.301ms`.
- Warm stability after both nodes were up:
  - primary loopback p50 `42.4ms`, p95 `108.0ms`
  - secondary loopback p50 `35.7ms`, p95 `48.2ms`
  - edge p50 `46.4ms`, p95 `165.5ms`
- Warm stability excluding the first sample:
  - primary loopback p95 `67.8ms`
  - secondary loopback p95 `48.2ms`
  - edge p95 `74.5ms`
- Reduction vs previous timing checkpoint:
  - primary loopback p50 `61.5%` lower, p95 `45.3%` lower
  - secondary loopback p50 `65.8%` lower, p95 `71.9%` lower
  - edge p50 `61.7%` lower, p95 `77.3%` lower
- Decision: accept the change. Do not open another projection SQL change immediately; rerun a broader baseline and inspect remaining first-request/rate-limit/region/edge tail.

Post-projection broader baseline:

- Tracking document: `docs/performance/post-projection-current-baseline-2026-07-15.md`.
- API latency after projection optimization:
  - local policy list default p50 `30.3ms`, p95 `40.6ms`
  - edge policy list default p50 `71.1ms`, p95 `110.0ms`
  - edge ranking p95 `50.0ms`
  - edge search keyword p95 `106.4ms`
- Target-specific list samples:
  - primary loopback p50 `26.3ms`, p95 `59.2ms`
  - secondary loopback p50 `28.0ms`, p95 `34.6ms`
  - edge p50 `38.1ms`, p95 `61.9ms`
- DB representative checks:
  - policy list created_at execution `12.804ms`
  - policy search keyword API shape execution `166.438ms`
- Decision: stop code changes for this sequence. Policy list default is no longer the clear repeated bottleneck; if continuing performance work, run a longer low-concurrency load/soak check before choosing the next target.

Post-projection low-rate load/soak:

- Tracking document: `docs/performance/post-projection-load-soak-2026-07-15.md`.
- Initial primary load at `120s`, concurrency `3`, delay `0.15s`, about `17.201 rps`, failed due to rate limits. This is recorded as a rate-limit boundary, not an accepted performance baseline.
- Accepted low-rate soak used `180s`, concurrency `1`, delay `1.2s`, about `0.8 rps`.
- Primary accepted result:
  - policy search filtered p95 `210.9ms`
  - policy search keyword p95 `117.9ms`
  - policy list default p95 `33.1ms`
  - policy ranking p95 `17.4ms`
  - errors `0`, rate limited `0`
- Secondary accepted result:
  - policy search filtered p95 `277.6ms`
  - policy search keyword p95 `130.9ms`
  - policy list default p95 `46.5ms`
  - policy ranking p95 `19.3ms`
  - errors `0`, rate limited `0`
- Final public smoke and ALB health passed.
- Decision: do not open another policy-list optimization. If another performance round is opened, start with search query instrumentation/planning.

### 2026-07-15: ranking app-side timing instrumentation

Trigger:

- The latest rerun still showed ranking cold p95 as the strongest repeatable tail:
  - local ranking cold p95 `804.2ms`
  - local ranking warm p95 `6.9ms`
  - edge ranking cold p95 `828.5ms`
  - edge ranking warm p95 `20.8ms`
- DB-only ranking breakdown remained too small to explain the endpoint cold tail by itself.

Plan:

1. Add low-cardinality cold-compute timing inside `PolicyRankingService`.
2. Keep ranking math, ordering, response shape, local cache, and Redis cache TTL unchanged.
3. Log substep timings only for slow cold computes.
4. Rerun the same cache-tail and app-log checks.

Expected impact:

| Metric | Expected |
| --- | ---: |
| ranking response/order change | 0 |
| warm ranking latency change | 0 |
| cold ranking latency change | about 0ms to +1ms |
| DB query change | 0 |

Tracking document: [ranking-app-timing-instrumentation-2026-07-15.md](ranking-app-timing-instrumentation-2026-07-15.md)

Current primary result:

- `PolicyRankingServiceTest` passed.
- Primary deploy succeeded and health returned `UP`.
- Ranking/search/list smoke endpoints returned valid data.
- Warmed local cache-tail:
  - cold ranking p95 `1005.0ms`
  - warm ranking p95 `16.4ms`
  - cold search p95 `190.1ms`
  - warm search p95 `26.1ms`
- New app-side timing shows the largest cold cost is `scoringSortMs`:
  - median `465.0ms`
  - max `889ms`
- Projection load is smaller:
  - median `61.0ms`
  - max `95ms`

Interim conclusion:

- instrumentation worked
- no ranking behavior change was made
- next optimization should target Java scoring/sorting candidate volume

Deploy closeout:

- commit deployed: `0d1e209e6537fadd0625f35215aa922c29d49e26`
- primary health: `UP`
- secondary health: `UP`
- ALB targets: `i-0e8a4cc599c1148c8=healthy`, `i-0b8d95e454df5e0f0=healthy`
- external smoke:
  - ranking `200`, 5 items
  - search `200`, 5 items, total `1476`
  - list `200`, 5 items, total `13340`
- edge cache-tail artifact: `tmp/stability/ranking-app-timing-edge-20260715/20260715T073334Z`
  - cold ranking p95 `633.3ms`
  - warm ranking p95 `24.6ms`
  - cold search p95 `553.3ms`
  - warm search p95 `35.0ms`

Post-optimization stability check: [post-optimization-stability-check-2026-07-14.md](post-optimization-stability-check-2026-07-14.md)

Latest cache-tail measurement: [policy-cache-tail-measurement-2026-07-14.md](policy-cache-tail-measurement-2026-07-14.md)

One-page change ledger: [performance-change-ledger-2026-07-14.md](performance-change-ledger-2026-07-14.md)

Latest ranking breakdown measurement: [ranking-cold-breakdown-measurement-2026-07-14.md](ranking-cold-breakdown-measurement-2026-07-14.md)

Next checkpoint / closure: [performance-next-checkpoint-2026-07-14.md](performance-next-checkpoint-2026-07-14.md)

## Closed Batch

### 2026-07-14: latest generated-search rebaseline and DB representative realignment

Trigger:

- After the generated search field deployment and migration pre-apply guard, the next optimization target needed a fresh baseline.
- The first latest-commit DB rerun showed `policy_search_keyword_api_shape=249.283ms`, while the local API baseline showed `policy_search_keyword p95=18.9ms`.
- Code inspection confirmed the DB representative still used raw `to_tsvector(...)`, `lower(...)`, and raw title/keyword expressions for the `api_shape` case, while the deployed fast path uses generated `search_document_vector`, `title_l`, and `keyword_l`.
- API search samples are also warm-cache influenced because `PolicySearchService` keeps public search responses in local/Redis cache.

Plan:

1. Run a low-impact rebaseline without long load tests:
   - local API latency
   - DB query EXPLAIN
   - Redis baseline
   - public edge contract
   - app/nginx log observability
2. Treat the initial DB result as a measurement-contract issue, not an app regression.
3. Align `policy_search_keyword_api_shape` with the generated-field app fast path.
4. Keep the raw OR/rank query as `policy_search_keyword_legacy_or_shape` for comparison.
5. Rerun DB baseline and use the corrected numbers for the next bottleneck decision.

Expected impact:

| Metric | Before | Expected after change | Interpretation |
| --- | ---: | ---: | --- |
| Runtime API latency | unchanged | unchanged | measurement script only |
| DB representative accuracy | raw expression path | generated-field path | should match current app contract better |
| `policy_search_keyword_api_shape` EXPLAIN | 249.283ms on stale representative | lower, still not necessarily equal to cached API | uncached DB cost becomes clearer |
| legacy/raw comparison | mixed into api shape | retained separately | easier before/after review |

Initial latest-commit rebaseline:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
  APP_BASE_URL=http://127.0.0.1:8082 RUNS=7 WARMUP_RUNS=1 \
  PERFORMANCE_ROOT=tmp/performance/latest-rebaseline-suite-20260714 \
  bash deploy/performance/run-local-performance-baseline-suite.sh

EDGE_ROOT=tmp/performance/latest-rebaseline-edge-20260714 \
  EXTERNAL_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-edge-baseline.sh

APP_LOG_COMPOSE_FILE=docker-compose.prod.elasticache.yml APP_LOG_SINCE=30m \
  APP_LOG_COMPOSE_SERVICE=app \
  APP_LOG_ROOT=tmp/performance/latest-rebaseline-app-log-20260714 \
  bash deploy/performance/run-local-app-log-observability-baseline.sh

NGINX_LOG_ROOT=tmp/performance/latest-rebaseline-nginx-log-20260714 \
  bash deploy/performance/run-local-nginx-log-observability-baseline.sh
```

Initial observed result:

- suite artifact: `tmp/performance/latest-rebaseline-suite-20260714/20260714T170401Z`
- API latency:
  - `api_latency_baseline=passed`
  - `sample_count=42`
  - `total_errors=0`
  - `total_rate_limited=0`
  - `policy_search_keyword p95=18.9ms`
  - `policy_list_default p95=76.7ms`
  - `policy_suggestions p95=37.8ms`
- DB query before representative alignment:
  - `policy_search_keyword_api_shape=249.283ms`
  - `policy_search_keyword_legacy_or_shape=290.632ms`
- Redis baseline:
  - `redis_baseline=skipped`
  - `reason=container_not_running`
- edge artifact: `tmp/performance/latest-rebaseline-edge-20260714/20260714T170523Z`
  - `edge_baseline=passed`
  - `policy_search_keyword POST /api/policies/search status=200 total_ms=300.144`
  - `policy_list_default GET /api/policies... status=200 total_ms=212.932`
- app log artifact: `tmp/performance/latest-rebaseline-app-log-20260714/20260714T170523Z`
  - `raw_error_lines=0`
  - `raw_warn_lines=2`
  - API duration p95 `207ms`, p99 `1088ms`, max `1308ms`
  - `POST /api/policies/search` p95 `241.8ms`, p99 `1045.2ms`, max `1110ms`
  - `GET /api/policies/ranking` had only 2 samples but one slow tail, max `1308ms`
- nginx log artifact: `tmp/performance/latest-rebaseline-nginx-log-20260714/20260714T170523Z`
  - request p95 `0.046s`
  - upstream p95 `0.084s`
  - tail included 9 historical `502 GET /alb-health` entries and 3 `429 GET /api/policies`
  - current edge baseline requests themselves returned `200`

Implementation:

- `deploy/performance/run-local-db-query-baseline.sh`
  - `policy_search_keyword_api_shape` now uses:
    - `search_document_vector`
    - `title_l`
    - `keyword_l`
  - `policy_search_keyword_legacy_or_shape` still keeps the raw expression/rank path for comparison.

Actual verification:

```bash
bash -n deploy/performance/run-local-db-query-baseline.sh

ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
  DB_BASELINE_ROOT=tmp/performance/db-query-generated-aligned-20260714 \
  bash deploy/performance/run-local-db-query-baseline.sh
```

Observed result after representative alignment:

- artifact: `tmp/performance/db-query-generated-aligned-20260714/20260714T170706Z`
- `db_query_baseline=passed`
- `welfare_services_rows=15104`
- `policy_search_keyword_api_shape=105.263ms`
- `policy_search_keyword_legacy_or_shape=398.957ms`
- `recommendation_logs_recent_window=10.617ms`
- `policy_list_created_at=21.273ms`

Actual impact:

| Metric | Before | After | Result |
| --- | ---: | ---: | --- |
| API latency script | passed | passed | unchanged |
| local warm-cache `policy_search_keyword` p95 | 18.9ms | unchanged | app runtime not changed |
| DB `policy_search_keyword_api_shape` | 249.283ms | 105.263ms | representative corrected |
| DB legacy/raw shape | 290.632ms initial run | 398.957ms aligned rerun | still expensive comparison path |
| public edge search | 300.144ms | unchanged | network/ALB/browser-facing path |

Interpretation:

- The generated-field app path is materially better than the stale DB representative suggested, but uncached DB search still costs about `105ms`.
- Warm API samples should not be used as proof that the uncached DB path is solved.
- The next likely performance candidates are:
  - split search measurement into cold vs warm cache contracts
  - reduce uncached search final ranking/window cost
  - investigate `GET /api/policies/ranking` slow tail with a larger controlled sample
  - separate historical deploy/health-check 502s from current nginx log baselines

### 2026-07-14: API latency rate-limit decision classification

Trigger:

- API latency script already reports per-scenario `rate_limited_count`, but the top-level text summary still always starts with `api_latency_baseline=passed`.
- When a bounded measurement intentionally includes rate-limit-sensitive endpoints or uses high external `RUNS`, 429 samples can look like normal successful latency data unless the operator manually reads every row.

Plan:

1. Keep current endpoint contract and default rate-limit-sensitive exclusions.
2. Add top-level decision classes:
   - `passed`
   - `passed_with_rate_limit`
   - `failed`
3. Add external-run warning context when `APP_BASE_URL` is public/external and `RUNS` is above the default warning threshold.
4. Verify the script with a normal external run and a bounded rate-limit-sensitive run.

Expected impact:

| Metric | Before | Expected after change | Interpretation |
| --- | --- | --- | --- |
| API latency | unchanged | unchanged | measurement script only |
| 429 interpretation | manual per-row reading | top-level `passed_with_rate_limit` | lower false alarm risk |
| contract/non-429 errors | mixed with 429 | top-level `failed` when present | clearer regression signal |
| high external RUNS | no warning | context warning | avoids accidental rate-limit-heavy baselines |

Implementation:

- `deploy/performance/run-local-api-latency-baseline.sh`
  - adds `RATE_LIMIT_WARNING_RUNS`, default `15`
  - writes external high-RUNS warning into run context
  - adds top-level `decision`, `total_errors`, `total_rate_limited`, `total_non_rate_limit_errors` to JSON
  - changes text summary first line to one of:
    - `api_latency_baseline=passed`
    - `api_latency_baseline=passed_with_rate_limit`
    - `api_latency_baseline=failed`

Actual verification:

```bash
bash -n deploy/performance/run-local-api-latency-baseline.sh

RUNS=3 WARMUP_RUNS=0 \
  API_LATENCY_ROOT=tmp/performance/api-latency-decision-normal-20260714 \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-api-latency-baseline.sh

RUNS=3 WARMUP_RUNS=0 INCLUDE_RATE_LIMIT_SENSITIVE_ENDPOINTS=true \
  API_LATENCY_ROOT=tmp/performance/api-latency-decision-sensitive-20260714 \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-api-latency-baseline.sh

RUNS=16 WARMUP_RUNS=0 \
  API_LATENCY_ROOT=tmp/performance/api-latency-decision-warning-20260714 \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-api-latency-baseline.sh

EDGE_ROOT=tmp/performance/edge-current-contract-20260714 \
  EXTERNAL_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-edge-baseline.sh
```

Observed result:

- normal external artifact: `tmp/performance/api-latency-decision-normal-20260714/20260714T165915Z`
  - `api_latency_baseline=passed`
  - `total_errors=0`
  - `total_rate_limited=0`
- sensitive external artifact: `tmp/performance/api-latency-decision-sensitive-20260714/20260714T165926Z`
  - `api_latency_baseline=passed`
  - `total_errors=0`
  - `total_rate_limited=0`
  - `policy_ranking` showed a tail sample: p95 `1221.4ms`, max `1348.6ms`
- high-RUNS external artifact: `tmp/performance/api-latency-decision-warning-20260714/20260714T165952Z`
  - `api_latency_baseline=passed`
  - `total_errors=0`
  - `total_rate_limited=0`
  - warning emitted: `external RUNS=16 exceeds RATE_LIMIT_WARNING_RUNS=15`
- edge contract artifact: `tmp/performance/edge-current-contract-20260714/20260714T170036Z`
  - `edge_baseline=passed`
  - `policy_search_keyword POST /api/policies/search status=200 total_ms=71.740`

Actual impact:

| Metric | Before | After | Result |
| --- | --- | --- | --- |
| normal external baseline | `passed` | `passed` with aggregate error counters | clearer |
| high external RUNS | no top-level warning | warning emitted | improved |
| 429 classification | per-scenario only | top-level decision-ready counters | improved |
| current edge search contract | already POST | verified POST `200` | confirmed |
| API latency | unchanged | unchanged | measurement-only change |

Interpretation:

- No runtime app change was needed.
- Current edge script is already aligned with the POST search contract.
- Future measurements with 429s will now surface as `passed_with_rate_limit` unless non-429 failures are also present.

### 2026-07-14: RDS migration pre-apply safety guard

Trigger:

- The generated search field deployment showed a schema/app ordering risk:
  - app deployment finished before the generated columns were present
  - `migration_admin` failed to apply `ALTER TABLE welfare_services` with `must be owner of table welfare_services`
  - RDS master account was required for the DDL
- This is not a request latency optimization. It is an operational performance/safety batch: reduce deployment diagnosis time and prevent broken-request windows for DDL-backed app changes.

Plan:

1. Add a reusable wrapper for one-file RDS migration pre-apply.
   - input: `backend/src/main/resources/db/migration/VYYYY_MM_DD_NN__description.sql`
   - account: `RDS_MASTER_USERNAME` / `RDS_MASTER_PASSWORD`
   - output: deterministic stdout fields for version, script name, SHA, dry-run/apply result
2. Record `schema_migration_history` automatically after successful apply.
3. Document the required order for DDL-backed deployments:
   - DB pre-apply
   - schema/history verification
   - app deploy
   - API smoke
4. Validate the wrapper without changing schema first using `--dry-run`.
5. Run the wrapper against the already-applied generated-search migration to verify idempotent operation and history upsert behavior.

Expected impact:

| Metric | Before | Expected after guard | Interpretation |
| --- | ---: | ---: | --- |
| API latency | unchanged | unchanged | this is not a query-path optimization |
| DDL permission discovery | during deploy/failure | before deploy via dry-run | faster failure mode |
| broken-request window for new-column code | possible until manual DB fix | expected `0` when pre-apply is followed | DB schema exists before app code reads it |
| manual history record time | ad hoc `psql` insert | wrapper-managed | lower operator error |
| recovery/diagnosis time for owner mismatch | roughly minutes | seconds to one dry-run failure | practical deployment safety gain |

Acceptance checks:

```bash
bash -n deploy/postgres/apply-rds-migration-file.sh
ENV_FILE=.env.production deploy/postgres/apply-rds-migration-file.sh --dry-run \
  backend/src/main/resources/db/migration/V2026_07_14_02__add_policy_search_generated_fields.sql
ENV_FILE=.env.production NOTE='idempotent verification after generated search deployment' \
  deploy/postgres/apply-rds-migration-file.sh \
  backend/src/main/resources/db/migration/V2026_07_14_02__add_policy_search_generated_fields.sql
```

Acceptance target:

- wrapper does not print secrets
- dry-run verifies master connection and migration metadata without applying SQL
- idempotent apply succeeds on an already-applied migration
- `schema_migration_history` contains the expected script name and SHA

Implementation:

- added `deploy/postgres/apply-rds-migration-file.sh`
- documented the DB pre-apply flow in `docs/core/db-migration.md`

Actual verification:

```bash
bash -n deploy/postgres/apply-rds-migration-file.sh
ENV_FILE=.env.production deploy/postgres/apply-rds-migration-file.sh --dry-run \
  backend/src/main/resources/db/migration/V2026_07_14_02__add_policy_search_generated_fields.sql
ENV_FILE=.env.production NOTE='idempotent verification after generated search deployment' \
  deploy/postgres/apply-rds-migration-file.sh \
  backend/src/main/resources/db/migration/V2026_07_14_02__add_policy_search_generated_fields.sql
```

Observed result:

- syntax check passed
- dry-run returned:
  - `db_user=masteradmin`
  - `history_existing_rows=1`
  - `dry_run_result=planned`
  - no secret values printed
- idempotent apply returned `apply_result=ok`
- already-applied columns/indexes were skipped by PostgreSQL `IF NOT EXISTS` notices
- `schema_migration_history` verification:
  - version `2026.07.14.02`
  - script `V2026_07_14_02__add_policy_search_generated_fields.sql`
  - SHA `47ef3bbca32efe6365340aa64a6af881888279d61292af0445671a1de5466295`
  - applied by `masteradmin`
- schema verification:
  - generated columns present count: `3`
  - generated-field indexes present count: `3`

Actual impact:

| Metric | Before | After | Result |
| --- | --- | --- | --- |
| DDL owner mismatch detection | during deploy/manual attempt | dry-run before app deploy | improved |
| history record | manual ad hoc SQL | wrapper-managed upsert | improved |
| broken-request risk for new-column code | possible if app deploy wins race | avoided when wrapper runs first | improved |
| API latency | unchanged | unchanged | not a query optimization |
| idempotent re-run | manual judgement | verified with existing migration | improved |

Interpretation:

- This guard turns the `migration_admin` owner mismatch from a deployment-time surprise into a pre-deploy check.
- It should be used before the next schema-backed app change, especially any change where Java SQL references new columns or indexes immediately.

### 2026-07-14: generated search text/vector feasibility plan

Trigger:

- The public search cold-miss batch reduced the current app query shape to `235.428ms`, but rank/sort still recomputes search document text, `to_tsvector`, and `similarity` from raw `title/description/support_content/keyword`.
- The next search-specific improvement should be evaluated as a schema batch, not a quick query rewrite.
- Production DB is PostgreSQL `16.14`; `welfare_services` currently has about `15101` rows.

Current constraints:

- existing expression indexes:
  - `idx_ws_search_document_fts`
  - `idx_ws_search_document_trgm`
  - `idx_ws_title_trgm`
  - `idx_ws_keyword_trgm`
- current optimized search uses `UNION` matched IDs and keeps final rank computation from raw fields.
- function volatility check:
  - `to_tsvector(regconfig,text)` is immutable
  - `lower(text)` is immutable
  - `similarity(text,text)` is immutable
  - `concat_ws(text, any)` is stable, so generated expressions should keep explicit `coalesce(...) || ...` concatenation.

Candidate designs to compare before implementation:

1. Stored generated search document text only.
   - column: `search_document_text`
   - expression: lower concatenation of title/description/support_content/keyword
   - indexes: GIN trigram on `search_document_text`
   - expected benefit: removes repeated text concatenation/lower work and simplifies trigram/document matching
2. Stored generated search vector only.
   - column: `search_document_vector`
   - expression: `to_tsvector('simple', lower(...))`
   - indexes: GIN on `search_document_vector`
   - expected benefit: removes repeated `to_tsvector` work and simplifies FTS rank condition
3. Stored generated text + vector.
   - use text for LIKE/trigram/similarity
   - use vector for FTS match/rank
   - expected benefit: largest read-side reduction, highest write/migration overhead
4. No generated column yet; keep current optimized `UNION` query.
   - expected benefit: no migration risk
   - downside: cold-miss query still spends CPU on derived text/vector/rank computation

Feasibility and risk checks:

- use a session-local temporary table copied from `welfare_services` to estimate read-side gains without touching production schema
- compare query shapes for `청년` and `월세`
- verify whether generated columns preserve the same top IDs as current production query
- estimate migration cost:
  - adding STORED generated columns rewrites/computes table data
  - GIN indexes should be created concurrently for production safety if implemented outside Flyway transactional migration
  - rollback should drop new indexes first, then generated columns
- do not implement schema changes in this batch until the comparison shows enough upside to justify the migration risk.

Acceptance checks for this planning batch:

```bash
# temporary-table experiment only; no production schema changes
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
  bash <generated search feasibility experiment>
```

Acceptance target:

- generated text/vector candidate produces the same representative top IDs as current production query
- measured temp-table query execution shows whether expected DB improvement is material enough
- document includes expected performance, migration risk, and rollback recommendation before any schema implementation

Experiment result:

- artifact: `tmp/performance/generated-search-feasibility-20260714/20260714T162903Z`
- comparison shape:
  - `raw_expr`: current default relevance fast path query shape, using expression indexes but recomputing `to_tsvector(...)`, `lower(...)`, and `similarity(...)` from raw columns during final rank/sort
  - `generated_fields`: same query shape and same temp data, but using stored generated `search_document_vector`, `title_l`, and `keyword_l` for match/rank
- no production schema was changed; all generated columns and indexes were created on a session-local temporary table.

| Keyword | Raw expr avg | Generated fields avg | Saved | Improvement | Same total | Same top 10 |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| `월세` | `48.828ms` | `37.479ms` | `11.350ms` | `23.2%` | yes, `83` | yes |
| `청년` | `216.032ms` | `85.471ms` | `130.562ms` | `60.4%` | yes, `1474` | yes |

Representative top IDs stayed identical:

- `월세`: `7584,11600,9099,531,11160,12245,3394,5207,2971,3112`
- `청년`: `844,471,2393,2399,472,1076,2372,897,2394,1879`

Recommendation before implementation:

- implement the schema batch only for generated fields that the current app fast path actually uses:
  - `search_document_vector tsvector`
  - `title_l text`
  - `keyword_l text`
- keep `search_document_text` out of the first implementation unless a separate query path starts using document-wide trigram/LIKE directly. It was useful for feasibility reasoning, but the current default relevance fast path does not need it after `search_document_vector` exists.
- update `WelfareServiceSearchRepositoryImpl` constants to read generated fields on the default relevance fast path first; then decide separately whether chat/suggestion/general filtered search should also move to the generated fields.
- migration risk is acceptable for the current row count (`15101`) but still a schema change:
  - adding STORED generated columns computes values for existing rows
  - new GIN indexes should be created with production lock behavior in mind
  - if Flyway runs this as a normal transaction, avoid `CREATE INDEX CONCURRENTLY`; if using manual/ops SQL, prefer concurrent index creation for lower lock impact
- rollback order:
  - drop generated-field indexes first
  - revert repository SQL constants to raw expressions
  - drop generated columns after traffic is confirmed back on raw expressions

Implementation gate:

- proceed only if we accept a schema migration plus app query change for a measured DB-side gain of roughly `23%` on narrower search and `60%` on broader search in the representative temp-table run.

Implementation selected:

- migration: `backend/src/main/resources/db/migration/V2026_07_14_02__add_policy_search_generated_fields.sql`
  - adds stored generated `title_l`
  - adds stored generated `keyword_l`
  - adds stored generated `search_document_vector`
  - adds GIN indexes `idx_ws_title_l_trgm`, `idx_ws_keyword_l_trgm`, `idx_ws_search_document_vector_gin`
- schema snapshot: `backend/src/main/resources/db/schema.sql`
- app query: `WelfareServiceSearchRepositoryImpl` default first-page relevance fast path now uses generated fields for match/rank.
- unchanged in this batch:
  - filtered/general policy search
  - regional policy search
  - chat candidate search
  - suggestion title candidate search
  - old expression indexes, because the unchanged paths still use raw expressions.

Pre-deploy verification:

```bash
cd backend
./gradlew test --tests 'com.example.welfare.policy.repository.WelfareServiceSearchRepositoryImplTest' \
  --tests 'com.example.welfare.global.config.PostgresRuntimeContractTest' --no-daemon
./gradlew test --no-daemon
```

Both commands passed before deployment.

Deployment closeout:

- commit: `d08753b5ba9efc6176d7033dd6717b84376d084d`
- primary instance `i-0b8d95e454df5e0f0` deployed with `docker-compose.prod.elasticache.yml`; health `UP`
- secondary instance `i-0e8a4cc599c1148c8` deployed with the same commit through SSM; health `UP`
- ALB target group `youth-welfare-web-tg` showed both targets `healthy`
- runtime RDS migration required the RDS master account because `migration_admin` is not the owner of `welfare_services`
  - first attempt with `migration_admin` failed with `must be owner of table welfare_services`
  - applied `V2026_07_14_02__add_policy_search_generated_fields.sql` with `masteradmin`
  - recorded `schema_migration_history` version `2026.07.14.02`
- RDS verification confirmed:
  - generated columns: `title_l`, `keyword_l`, `search_document_vector`
  - indexes: `idx_ws_title_l_trgm`, `idx_ws_keyword_l_trgm`, `idx_ws_search_document_vector_gin`
- public API functional verification through `https://youthmoa.kr`:
  - `청년`: total `1474`, first 6 IDs `844,471,2393,2399,472,1076`
  - `월세`: total `83`, first 6 IDs `7584,11600,9099,531,11160,12245`

Post-deploy DB comparison:

- artifact: `tmp/performance/generated-search-postdeploy-20260714/20260714T164502Z`

| Keyword | Raw expr avg | Generated fields avg | Saved | Improvement | Same total | Same top 10 |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| `월세` | `37.387ms` | `25.556ms` | `11.831ms` | `31.6%` | yes, `83` | yes |
| `청년` | `211.005ms` | `57.851ms` | `153.154ms` | `72.6%` | yes, `1474` | yes |

Post-deploy public API spot check:

- artifact: `tmp/performance/generated-search-api-postdeploy-20260714/20260714T164524Z`

| Label | Count | Status | p50 | p95 | Max |
| --- | ---: | --- | ---: | ---: | ---: |
| `월세` | `12` | `200` | `64.2ms` | `86.4ms` | `500.7ms` |
| `청년` | `12` | `200` | `68.5ms` | `194.9ms` | `546.1ms` |

Interpretation:

- The DB-side read improvement is material and preserves representative result ordering/counts.
- Public API spot checks stayed successful; max values include external HTTPS/ALB/application path and should be read as a quick smoke, not as a full latency baseline.
- Keep the old expression indexes for now because filtered/general/chat/suggestion paths still use raw expressions.


### 2026-07-14: public search cold-miss query shape optimization

Trigger:

- Current production rebaseline identified the representative search query as the next DB-side bottleneck:
  - `policy_search_keyword_api_shape`: `316.053ms`
  - artifact: `tmp/performance/current-prod-rebaseline-db-20260714/20260714T142103Z`
- Redis shared cache keeps repeated public search fast, but the first request for a new public search key can still pay the cold-miss query cost.

Breakdown artifacts:

- current query decomposition: `tmp/performance/search-cold-miss-breakdown-20260714/20260714T143614Z`
- `%` operator alternatives: `tmp/performance/search-cold-miss-alternatives-20260714/20260714T143756Z`
- `UNION` candidate alternatives: `tmp/performance/search-cold-miss-union-20260714/20260714T143859Z`
- materialized field alternatives: `tmp/performance/search-cold-miss-materialized-20260714/20260714T143958Z`
- `set_config` CTE validation: `tmp/performance/search-cold-miss-setconfig-20260714/20260714T144047Z`

Observed decomposition:

| Query shape | Execution | Interpretation |
| --- | ---: | --- |
| FTS-only count | 4.341ms | `idx_ws_search_document_fts` is effective |
| title LIKE count | 9.317ms | cheap alone |
| keyword LIKE count | 7.620ms | cheap alone |
| title similarity count | 26.256ms | `similarity(...) >= 0.2` does not use trigram GIN in current shape |
| full match count | 155.565ms | OR predicate falls back to scanning `idx_ws_search_youth` candidates |
| full match rank limit 100 | 267.301ms | rank/sort over broad candidates dominates |
| actual default search limit 100 + `COUNT OVER` | 276.401ms | current first-page default relevance shape |
| `UNION` matched IDs + light rank + `COUNT OVER` | 182.895ms | best low-risk candidate in dry run |
| `UNION` matched IDs + current full rank + `set_config` CTE | 191.763ms | preserves current rank formula and uses FTS/trigram indexes |

Planned implementation:

1. Add an optimized default relevance path inside `WelfareServiceSearchRepositoryImpl`.
   - only when `status == null`
   - `statusFilter == ACTIVE_ONLY`
   - `sort == RELEVANCE`
   - no category/source/online/income/target/Gov24 filters
   - no sido/sgg region filters
2. Keep all filtered, regional, non-relevance sort, and taxonomy searches on the existing query builder.
3. Build matched candidate IDs with a `UNION` of:
   - FTS document match
   - title LIKE
   - keyword LIKE
   - title trigram `%`
   - keyword trigram `%`
4. Use `set_config('pg_trgm.similarity_threshold', '0.2', true)` through a CTE so trigram `%` matches the existing `similarity >= 0.2` threshold without leaking session state.
5. Preserve the existing final rank formula and `COUNT(*) OVER()` result shape.

Implementation note:

- optimized default relevance SQL added in `WelfareServiceSearchRepositoryImpl`
- branch guard added so filtered/regional/non-relevance searches keep the existing general query
- repository unit coverage added for:
  - default first-page relevance search uses `matched_ids AS MATERIALIZED`
  - filtered search falls back to the existing general query

Expected impact:

| Metric | Current | Expected after optimized default path | Notes |
| --- | ---: | ---: | --- |
| default search DB execution | 276.401ms | 180~210ms | dry-run best compatible shape was 191.763ms |
| representative baseline query | 316.053ms | 190~230ms | depends on exact baseline SQL and cache state |
| public cached API p95 | 66.0ms | similar | Redis hit path already fast |
| cold-miss public search | high variance | lower by roughly 25~35% DB-side | first request for a new cache key |

Acceptance checks:

```bash
cd backend
./gradlew test --tests '*PolicySearch*' --no-daemon

ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
  DB_BASELINE_ROOT=tmp/performance/search-cold-miss-optimized-db-20260714 \
  bash deploy/performance/run-local-db-query-baseline.sh

RUNS=20 WARMUP_RUNS=2 API_LATENCY_DELAY_SECONDS=1 \
  API_LATENCY_ROOT=tmp/performance/search-cold-miss-optimized-api-20260714 \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-api-latency-baseline.sh
```

Acceptance target:

- existing `PolicySearch` tests pass
- optimized default search explain uses FTS/trigram indexes in the matched-ID CTE
- representative default search DB execution moves toward the `180~230ms` band
- no API error/rate-limit regression in the public latency baseline

Pre-deploy verification:

```bash
cd backend
./gradlew test --tests '*PolicySearch*' \
  --tests 'com.example.welfare.policy.repository.WelfareServiceSearchRepositoryImplTest' \
  --no-daemon

./gradlew test --no-daemon
```

Result: `BUILD SUCCESSFUL` for both runs.

Deployment:

- commit: `4cb75ba8 Optimize default public search cold misses`
- pushed to `origin/refactor/admin-dashboard-sections`
- primary node rebuilt locally with `docker compose --env-file .env.production -f docker-compose.prod.elasticache.yml up -d --build app`
- secondary node deployed through SSM and fast-forwarded to `4cb75ba83ed087afb8682e135a7accd2e8850a5b`
- secondary SSM deployment command reported `Failed` with response code `1`, but stdout showed successful build and `health_attempt=9 container=healthy http=200`
- separate secondary verification command returned `Status=Success`, `container_health="healthy"`, and `actuator={"status":"UP"}`
- ALB target group `youth-welfare-web-tg` after deploy:
  - `i-0b8d95e454df5e0f0`: `healthy`
  - `i-0e8a4cc599c1148c8`: `healthy`

Post-deploy measurement:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
  DB_BASELINE_ROOT=tmp/performance/search-cold-miss-optimized-db-20260714 \
  bash deploy/performance/run-local-db-query-baseline.sh

RUNS=20 WARMUP_RUNS=2 API_LATENCY_DELAY_SECONDS=1 \
  API_LATENCY_ROOT=tmp/performance/search-cold-miss-optimized-api-20260714 \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-api-latency-baseline.sh
```

Result:

- DB artifact: `tmp/performance/search-cold-miss-optimized-db-20260714/20260714T145227Z`
- API artifact: `tmp/performance/search-cold-miss-optimized-api-20260714/20260714T145423Z`
- modified DB baseline now records:
  - `policy_search_keyword_api_shape`: current optimized app query shape
  - `policy_search_keyword_legacy_or_shape`: old OR/similarity comparison shape

DB observed delta:

| Query shape | Before | After | Delta |
| --- | ---: | ---: | ---: |
| representative pre-change search query | 316.053ms | - | - |
| legacy OR shape with current active visibility predicate | - | 288.959ms | comparison only |
| current optimized app shape | 276.401ms dry run | 235.428ms | accepted partial improvement |

API latency result:

| Scenario | p50 | p95 | p99 | Max | Errors | Rate limited |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `policy_search_keyword` | 59.2ms | 64.5ms | 70.1ms | 71.5ms | 0/20 | 0 |
| `policy_search_filtered` | 59.7ms | 134.0ms | 247.5ms | 275.9ms | 0/20 | 0 |
| `policy_suggestions` | 81.9ms | 101.5ms | 118.9ms | 123.3ms | 0/20 | 0 |
| `policy_list_default` | 134.6ms | 169.8ms | 179.1ms | 181.4ms | 0/20 | 0 |
| `policy_list_active_only` | 126.9ms | 176.5ms | 286.7ms | 314.2ms | 0/20 | 0 |

Interpretation:

- search DB cold-miss shape improved, but not as much as the most optimistic dry run
- the accepted DB delta is roughly `288.959ms -> 235.428ms` against the legacy shape in the same post-deploy baseline, about an 18.5% reduction
- public cached keyword search remains stable at p95 `64.5ms`
- filtered search max improved materially versus the current rebaseline max `817.5ms`, though this is still partly affected by ALB/runtime variance
- list p95 was higher in this API sample, but this batch did not change list code; keep watching it in the next general baseline
- the next search-specific improvement should probably avoid recomputing `to_tsvector`/rank from raw text, for example with generated search vector/text fields, but that is a larger schema batch

Regression closeout before the next batch:

```bash
cd backend
./gradlew test --no-daemon

RUNS=3 WARMUP_RUNS=1 API_LATENCY_DELAY_SECONDS=1 \
  INCLUDE_RATE_LIMIT_SENSITIVE_ENDPOINTS=true \
  API_LATENCY_ROOT=tmp/performance/search-cold-miss-regression-api-20260714 \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-api-latency-baseline.sh
```

Result:

- backend full test suite: `BUILD SUCCESSFUL`
- API regression artifact: `tmp/performance/search-cold-miss-regression-api-20260714/20260714T160652Z`
- `api_latency_baseline=passed`
- `sample_count=24`
- all checked scenarios had `errors=0/3` and `rate_limited=0`

Regression scenario summary:

| Scenario | p50 | p95 | Max | Errors | Rate limited |
| --- | ---: | ---: | ---: | ---: | ---: |
| `policy_list_default` | 225.8ms | 295.1ms | 302.8ms | 0/3 | 0 |
| `policy_list_active_only` | 222.5ms | 294.5ms | 302.6ms | 0/3 | 0 |
| `policy_search_keyword` | 110.6ms | 204.0ms | 214.3ms | 0/3 | 0 |
| `policy_search_filtered` | 50.5ms | 67.7ms | 69.6ms | 0/3 | 0 |
| `policy_suggestions` | 75.3ms | 88.1ms | 89.5ms | 0/3 | 0 |
| `policy_trending` | 52.5ms | 54.4ms | 54.6ms | 0/3 | 0 |
| `policy_ranking` | 50.5ms | 62.7ms | 64.1ms | 0/3 | 0 |
| `policy_detail_first` | 79.1ms | 135.7ms | 142.0ms | 0/3 | 0 |

Interpretation:

- existing public list/search/suggestion/trending/ranking/detail flows still return successful responses after the search cold-miss change
- this smoke run is for regression coverage, not a replacement for the 20-run latency baseline
- no blocker remains before starting the next optimization batch

Functional correctness verification:

```bash
# DB legacy-vs-optimized search comparison and external API value checks
# artifact: tmp/performance/search-functional-correctness-20260714/20260714T161455Z
```

Result:

- artifact: `tmp/performance/search-functional-correctness-20260714/20260714T161455Z`
- `functional_correctness=passed`
- DB legacy-vs-optimized search comparison:
  - `월세`: legacy count `83`, optimized count `83`
  - `월세`: top 10 IDs matched exactly: `7584,11600,9099,531,11160,12245,3394,5207,2971,3112`
  - `월세`: legacy minus optimized `0`, optimized minus legacy `0`
  - `청년`: legacy count `1474`, optimized count `1474`
  - `청년`: top 10 IDs matched exactly: `844,471,2393,2399,472,1076,2372,897,2394,1879`
  - `청년`: legacy minus optimized `0`, optimized minus legacy `0`
- External API value checks:
  - `POST /api/policies/search`, keyword `월세`, size `6`: API IDs matched DB expected IDs exactly: `7584,11600,9099,531,11160,12245`
  - `POST /api/policies/search`, keyword `청년`, size `6`: API IDs matched DB expected IDs exactly: `844,471,2393,2399,472,1076`
  - `월세` search total was `83`; `청년` search total was `1474`
  - filtered deadline search returned 10 active/upcoming rows and non-null deadlines were sorted ascending
  - default and active list responses returned 5 active/upcoming rows with required `id/title/status` fields and positive totals
  - detail response returned the requested policy ID with required `title/status` fields
  - ranking response returned 5 rows with required `serviceId/title` fields and descending `rankingScore`
  - trending response returned non-empty unique keyword strings
  - suggestions response returned non-empty unique suggestion strings

Interpretation:

- the optimized search path preserves the legacy candidate set and top ordering for representative `월세` and `청년` searches
- the external API search response matches the DB-expected optimized order for default first-page searches that do not trigger discovery balancing
- list/detail/ranking/trending/suggestion flows return coherent values after the change
- this closes the functional verification requested before starting the next optimization batch

### 2026-07-14: current production performance rebaseline after cache/query batches

Trigger:

- Recent production batches changed the public read path:
  - Redis shared cache for public search and ranking
  - Redis active list count cache
  - autocomplete fallback lightweight query path
  - Redis cache namespace observability
  - policy list fast path and sort indexes
- The next optimization should be chosen from a fresh, same-day baseline rather than from pre-change numbers.

Measurement commands:

```bash
RUNS=20 WARMUP_RUNS=2 API_LATENCY_DELAY_SECONDS=1 \
  API_LATENCY_ROOT=tmp/performance/current-prod-rebaseline-api-20260714 \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-api-latency-baseline.sh

ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
  DB_BASELINE_ROOT=tmp/performance/current-prod-rebaseline-db-20260714 \
  bash deploy/performance/run-local-db-query-baseline.sh

ENV_FILE=.env.production \
  REDIS_OBSERVABILITY_ROOT=tmp/performance/current-prod-rebaseline-redis-20260714 \
  bash deploy/performance/run-local-redis-observability-baseline.sh

APP_BASE_URL=http://127.0.0.1:8082 \
  JVM_RUNTIME_ROOT=tmp/performance/current-prod-rebaseline-jvm-20260714 \
  bash deploy/performance/run-local-jvm-runtime-baseline.sh

ENV_FILE=.env.production \
  REDIS_OBSERVABILITY_ROOT=tmp/performance/current-prod-rebaseline-redis-warm-20260714 \
  bash deploy/performance/run-local-redis-observability-baseline.sh
```

Artifacts:

- API latency: `tmp/performance/current-prod-rebaseline-api-20260714/20260714T141843Z`
- DB query: `tmp/performance/current-prod-rebaseline-db-20260714/20260714T142103Z`
- Redis server snapshot: `tmp/performance/current-prod-rebaseline-redis-20260714/20260714T142103Z`
- Redis warmed cache namespace snapshot: `tmp/performance/current-prod-rebaseline-redis-warm-20260714/20260714T142211Z`
- JVM/container runtime: `tmp/performance/current-prod-rebaseline-jvm-20260714/20260714T142103Z`

API latency result:

| Scenario | p50 | p95 | p99 | Max | Errors | Rate limited |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `policy_list_default` | 101.4ms | 135.0ms | 260.0ms | 291.2ms | 0/20 | 0 |
| `policy_list_active_only` | 100.9ms | 121.3ms | 143.4ms | 149.0ms | 0/20 | 0 |
| `policy_search_keyword` | 50.4ms | 66.0ms | 70.7ms | 71.9ms | 0/20 | 0 |
| `policy_suggestions` | 71.4ms | 80.1ms | 85.7ms | 87.1ms | 0/20 | 0 |
| `policy_search_filtered` | 50.3ms | 119.2ms | 677.8ms | 817.5ms | 0/20 | 0 |

DB query result:

| Representative query | Execution | Planning | Scan note |
| --- | ---: | ---: | --- |
| `policy_detail_first` | 0.096ms | 1.675ms | index scan |
| `admin_collect_failures_recent` | 0.671ms | 0.529ms | small seq scan |
| `recent_policy_views_user` | 0.171ms | 0.502ms | small seq scan |
| `recommendation_logs_recent_window` | 9.454ms | 0.590ms | seq scan |
| `policy_list_created_at` | 10.642ms | 1.613ms | seq scan |
| `policy_search_keyword_api_shape` | 316.053ms | 3.655ms | index scan, expensive ranking/filter shape |

Redis/JVM result:

- Redis server snapshot: `redis_observability_baseline=passed`
- Redis warmed cache snapshot:
  - public search: 1 key, TTL 12s
  - ranking: 1 key, TTL 11s
  - active list count: 1 key, TTL 9s
- Redis health signals: `slowlog_len=0`, `latency_event_lines=0`, `evicted_keys=0`, `rejected_connections=0`
- JVM/container snapshot: `jvm_runtime_baseline=passed`
- actuator health: `200`
- actuator metrics endpoint: `401`, so detailed actuator metrics remain unavailable to the baseline script
- container stats: CPU `0.14%`, memory `581.3MiB / 1GiB`, restart count `0`, recent log exception count `0`

Interpretation:

- This batch did not require an app code change or redeploy.
- Current public list latency is acceptable for the latest measurement: default p95 `135.0ms`, active-only p95 `121.3ms`.
- The visible API outlier is `policy_search_filtered` p99/max, but the DB-side expensive representative is `policy_search_keyword_api_shape` at `316.053ms`.
- Next optimization candidate should be the public search cold-miss query shape and ranking/filter work, not Redis server health or basic list indexes.

### 2026-07-14: 정책 목록 API fast path + 정렬 인덱스 계획

Trigger values from the 2026-07-14 ALB measurement:

- `GET /api/policies`: `p50 145.6ms`, `p95 165.2ms`, `p99 194.1ms`, `max 205.9ms`
- `GET /api/policies?sort=DEADLINE`: success-only `p50 140.3ms`, `p95 165.6ms`, `p99 186.4ms`, `max 193.0ms`
- DB representative `policy_list_created_at`: `execution 14.756ms`, `planning 4.152ms`, `seq scan`
- simplified latest/deadline explains observed during triage were roughly `21~23ms`, with count around `9~15ms`
- artifact: `docs/performance/alb-measurement-2026-07-14.md`

Current read path:

1. `PolicyListService.getList()` normalizes all request filters into `PolicyListReadCondition`.
2. `WelfareServiceReadRepositoryImpl.findList()` always delegates to `WelfareServiceRepository.findListWithFilters()`.
3. `findListWithFilters()` is one large native SQL query that handles default list, category/source/status/region/Gov24/tag/income filters, count, and all sort modes through `CASE WHEN :sort = ...`.

Problem:

- the default list path pays for a query shape designed for every optional filter
- `:param IS NULL OR ...` predicates and `ORDER BY CASE WHEN :sort ...` make planner/index behavior harder
- this is not the largest current latency hotspot, but it is a growth-risk path because it is a high-traffic public list API

Planned implementation:

1. Add a conservative fast-path predicate in `WelfareServiceReadRepositoryImpl`.
   - only apply when `status == null`
   - `statusFilter == ACTIVE_ONLY`
   - no category/source/region/onlineApply/income/targetGroup/Gov24 filters
   - sort is one of `LATEST`, `DEADLINE`, `VIEWS`
2. Add dedicated repository methods for:
   - active latest list
   - active deadline list
   - active views list
3. Keep all filtered, region-first, Gov24, tag, income, `ALL`, and `EXPIRED_ONLY` cases on the existing `findListWithFilters()` query.
4. Add partial/expression sort indexes for active/upcoming rows:
   - latest sort: `coalesce(last_modified_at, registered_at, created_at) desc`, `id desc`
   - deadline sort: `coalesce(apply_end_date, DATE '9999-12-31') asc`, latest tiebreaker, `id desc`
   - views sort: `coalesce(api_view_count, 0) desc`, `coalesce(view_count, 0) desc`, latest tiebreaker, `id desc`
   - `status IN ('ACTIVE', 'UPCOMING')` is kept as the partial-index predicate rather than the leading key so the index order matches the list `ORDER BY`
5. Keep exact `COUNT(*)` in the first batch. Consider a short TTL count cache only if post-change measurements still show count as material.

Implementation note:

- code path implemented in `WelfareServiceReadRepositoryImpl`
- dedicated native queries added to `WelfareServiceRepository`
- fresh schema and migration file added as `V2026_07_14_01__add_policy_list_fast_path_indexes.sql`
- runtime RDS index application required the RDS master account because `DB_MIGRATION_USERNAME` is not the owner of `welfare_services`
- after manual index application, `EXPLAIN` changed from `Seq Scan + Sort` to:
  - `Index Scan using idx_ws_active_latest_list_sort`
  - `Index Scan using idx_ws_active_deadline_list_sort`
  - `Index Scan using idx_ws_active_views_list_sort`

Expected impact:

| Metric | Current | Expected after fast path + indexes | Interpretation |
| --- | ---: | ---: | --- |
| `GET /api/policies` p95 | `165.2ms` | `135~155ms` | modest user-facing reduction |
| `GET /api/policies?sort=DEADLINE` p95 | `165.6ms` | `135~155ms` | similar reduction |
| list select DB time | `15~23ms` | `3~8ms` | material DB-side reduction |
| full API p95 reduction | - | `10~30ms` | API includes app mapping, JSON, network, ALB |

Acceptance checks:

```bash
cd backend
./gradlew test --tests '*PolicyList*' --no-daemon

RUNS=30 WARMUP_RUNS=2 bash deploy/performance/run-local-api-latency-baseline.sh
bash deploy/performance/run-local-db-query-baseline.sh
```

Acceptance target:

- basic list p95 at or below roughly `140ms`, with `150ms` as the minimum useful target
- deadline list p95 at or below roughly `140~150ms`
- no content/total regression for existing filters
- no increase in 4xx/5xx outside rate-limit noise from repeated measurements

Post-implementation measurement:

- commit: `4744eae9 Add fast path for default policy lists`
- follow-up doc commit: `6843a9a7 Record policy list fast path results`
- deployed to both ALB targets on 2026-07-14
- ALB target health after deploy: both targets `healthy`
- runtime RDS indexes applied manually with the RDS master account

Deployment notes:

- primary node (`i-0b8d95e454df5e0f0`) was rebuilt locally with `docker compose --env-file .env.production -f docker-compose.prod.elasticache.yml up -d --build app`
- secondary node (`i-0e8a4cc599c1148c8`) was deployed through SSM
- first secondary SSM attempt failed before deployment because `AWS-RunShellScript` ran under `/bin/sh` and did not support `set -o pipefail`
- second secondary SSM attempt failed before deployment because the root SSM context hit git `safe.directory`
- third secondary SSM attempt ran as `ubuntu`, fast-forwarded `9a9f36ec -> 4744eae9`, rebuilt the image, and reached `health_attempt=19 status=healthy`
- SSM reported that third command as failed with exit code `1`, but a separate verification command confirmed:
  - secondary git head `4744eae96afd74e7375130599ba3297b92f6d79f`
  - secondary container health `healthy`
  - secondary actuator health `{"status":"UP"}`
- after the doc-only follow-up commit, secondary was fast-forwarded to `6843a9a7`; no app rebuild was required for that doc-only commit

Verification commands:

```bash
cd backend
./gradlew test --no-daemon

RUNS=20 WARMUP_RUNS=2 \
  API_LATENCY_ROOT=tmp/performance/policy-list-fast-path-20260714 \
  APP_BASE_URL=http://127.0.0.1:8082 \
  bash deploy/performance/run-local-api-latency-baseline.sh

RUNS=20 WARMUP_RUNS=2 \
  API_LATENCY_ROOT=tmp/performance/policy-list-fast-path-external-20260714 \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-api-latency-baseline.sh
```

Verification result:

- unit test suite: `BUILD SUCCESSFUL`
- primary container health after deploy: `healthy`
- secondary container health after deploy: `healthy`
- ALB target group `youth-welfare-web-tg` target health after deploy:
  - `i-0b8d95e454df5e0f0`: `healthy`
  - `i-0e8a4cc599c1148c8`: `healthy`
- integration test attempt for `PolicyListFastPathIntegrationTest` was blocked by local integration runtime preflight because local PostgreSQL `127.0.0.1:5433` and Redis `6379` were not running
- direct RDS `EXPLAIN (ANALYZE, BUFFERS)` confirmed native query execution and index usage:
  - latest list select: `Execution Time 0.278ms`
  - deadline list select: `Execution Time 0.801ms`
  - views list select: `Execution Time 0.241ms`
  - active count remained `Execution Time 10.323ms`
- API latency artifacts:
  - local target: `tmp/performance/policy-list-fast-path-20260714/20260714T103543Z`
  - external URL: `tmp/performance/policy-list-fast-path-external-20260714/20260714T103633Z`

Observed API deltas:

| Scenario | Before | After local target | After external URL | Notes |
| --- | ---: | ---: | ---: | --- |
| `GET /api/policies` p95 | `165.2ms` | `146.6ms` | `149.0ms` | accepted minimum target met |
| `GET /api/policies?statusFilter=ACTIVE_ONLY` p95 | not separately tracked | `91.7ms` | `182.9ms` | external run had higher network/target variance |

Interpretation:

- fast path and indexes are worth keeping
- DB select is no longer the meaningful list bottleneck for first-page default lists
- exact `COUNT(*)` and response assembly/network now dominate the remaining `GET /api/policies` latency
- next list-specific step, if needed, should evaluate a bounded count cache rather than adding more sort indexes

## Closed Batch

### 2026-07-14: Redis cache namespace observability script

Trigger:

- Redis shared cache now affects public search, ranking, and active list count paths.
- The existing Redis observability script captured server-wide Redis metrics, slowlog, command stats, and sampled key prefixes, but did not directly answer whether the app cache namespaces were populated and carrying expected TTLs.
- Manual `redis-cli` checks were used after the previous Redis batches, so the same evidence needed to be repeatable as a scripted artifact.

Implemented changes:

1. `deploy/performance/run-local-redis-observability-baseline.sh`
   - now uses the shared `smoke_redis_cli` helper instead of only `docker exec youth-welfare-redis`
   - local Redis remains supported through the existing container path
   - production ElastiCache/Valkey can be selected through `ENV_FILE=.env.production`
   - connection failure now records a skipped artifact with `reason=redis_unreachable`
2. Added cache namespace TTL/count summaries for:
   - `policy:search:public:v1:*`
   - `policy:ranking:v1:*`
   - `policy:list:active-only:count:v1`
3. The namespace artifact records only counts and TTL aggregates.
   - Redis values are not read or dumped
   - namespace key names are not written to artifact files
   - Redis `SCAN` + `TTL` aggregation runs server-side through one `EVAL` call per namespace to avoid slow per-key client calls
4. `deploy/performance/run-local-performance-deep-observation-suite.sh` now passes `ENV_FILE=.env.production` to Redis observability by default, matching the DB observability path used for production measurements.

Verification:

```bash
bash -n deploy/performance/run-local-redis-observability-baseline.sh
bash -n deploy/performance/run-local-performance-deep-observation-suite.sh

curl -sS -o /tmp/youthmoa-policy-list-warm.json \
  'https://youthmoa.kr/api/policies?page=0&size=20'
curl -sS -o /tmp/youthmoa-ranking-warm.json \
  'https://youthmoa.kr/api/policies/ranking?size=20'
curl -sS -o /tmp/youthmoa-search-warm.json \
  -H 'Content-Type: application/json' \
  --data '{"keyword":"청년","page":0,"size":20}' \
  'https://youthmoa.kr/api/policies/search'

ENV_FILE=.env.production \
  REDIS_OBSERVABILITY_ROOT=tmp/performance/redis-cache-observability-20260714 \
  bash deploy/performance/run-local-redis-observability-baseline.sh
```

Result:

- artifact: `tmp/performance/redis-cache-observability-20260714/20260714T130250Z`
- `redis_observability_baseline=passed`
- `dbsize=42`
- `keyspace_hit_rate=0.643979`
- `slowlog_len=0`
- `latency_event_lines=0`
- `connected_clients=9`
- `blocked_clients=0`
- `used_memory_human=6.76M`
- `used_memory_peak_human=6.89M`
- `evicted_keys=0`
- `rejected_connections=0`
- cache namespace summary:

| Namespace | Pattern | Keys sampled | Expiring TTLs | TTL min | TTL max | TTL avg |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| public search | `policy:search:public:v1:*` | 1 | 1 | 12s | 12s | 12.0s |
| ranking | `policy:ranking:v1:*` | 1 | 1 | 11s | 11s | 11.0s |
| active list count | `policy:list:active-only:count:v1` | 1 | 1 | 9s | 9s | 9.0s |

Interpretation:

- the script now replaces the manual Redis cache checks used in earlier batches
- all three app cache families were visible after warm-up and had expiring TTLs
- low TTL values are expected because the caches use short 30-second windows and the observability command ran after warm-up calls completed
- no app redeploy was required because this batch changed only scripts and documentation

### 2026-07-14: ALB autocomplete fallback lightweight path closeout

Trigger:

- ALB baseline before this change showed `POST /api/policies/search/suggestions` as a visible public latency hotspot:
  - `p50=275.2ms`
  - `p95=378.3ms`
  - `max=396.6ms`
- browser policy search flow wall time was `action_wall_ms=696`
- artifact: `tmp/performance/alb-valid-api-baseline-20260713T170710Z`

Breakdown:

| Query slice | Before execution |
| --- | ---: |
| log suggestions, `청` | 14.186ms |
| log suggestions, `청년` | 7.842ms |
| policy fallback candidates, `청` | 352.222ms |
| policy fallback candidates, `청년` | 318.474ms |

Why this was changed:

- search log suggestions were not the slow part
- fallback policy candidates used a heavier full-document search/ranking path than suggestions need
- autocomplete only needs title/keyword-like candidates, not full chat/search relevance ranking

Implemented changes:

1. `WelfareServiceSearchRepository.searchSuggestionTitleCandidates()`
   - title/keyword-only fallback candidate query
   - keeps the full `searchChatCandidates()` path unchanged
2. `PolicySearchKeywordReadService`
   - uses the lightweight title candidate path only when log suggestions do not fill the requested limit
   - still merges and ranks log-derived suggestions before policy fallback candidates

DB-level remeasurement of the new fallback query shape:

| Query slice | After execution |
| --- | ---: |
| light policy fallback candidates, `청` | 19.251ms |
| light policy fallback candidates, `청년` | 16.931ms |

Verification:

```bash
cd backend
./gradlew test --tests 'com.example.welfare.policy.service.PolicySearchKeywordReadServiceTest' --no-daemon
```

Result: `BUILD SUCCESSFUL`.

Production measurement:

```bash
sleep 70
RUNS=20 WARMUP_RUNS=2 API_LATENCY_DELAY_SECONDS=1 \
  API_LATENCY_ROOT=tmp/performance/suggestion-fallback-external-20260714 \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-api-latency-baseline.sh
```

Result:

- artifact: `tmp/performance/suggestion-fallback-external-20260714/20260714T123006Z`
- `api_latency_baseline=passed`
- `sample_count=100`
- all default public scenarios had `errors=0` and `rate_limited=0`

Observed delta:

| Scenario | Before | After production | Delta |
| --- | ---: | ---: | ---: |
| `POST /api/policies/search/suggestions` p50 | `275.2ms` | `71.8ms` | `-203.4ms` |
| `POST /api/policies/search/suggestions` p95 | `378.3ms` | `94.4ms` | `-283.9ms` |
| `POST /api/policies/search/suggestions` max | `396.6ms` | `302.6ms` | `-94.0ms` |

Interpretation:

- autocomplete fallback lightweight path is accepted and should stay
- p95 dropped below `100ms` in the accepted production run
- no additional code change is needed for this batch

### 2026-07-14: policy list default outlier triage

Trigger:

- after the active list count cache deploy, the accepted external latency run showed `policy_list_active_only` improving to `p95=121.3ms`, but `policy_list_default` still had an outlier:
  - `p50=124.1ms`
  - `p95=321.3ms`
  - `p99=1412.4ms`
  - `max=1685.2ms`
- DB row select and count paths had already been optimized, so this batch checked whether the remaining outlier was reproducible and where it appeared.

Plan executed:

1. Confirm ALB target health before measurement.
2. Wait for policy list rate-limit buckets to clear.
3. Run public ALB baseline with `API_LATENCY_DELAY_SECONDS=1`.
4. Run primary direct local baseline with `APP_BASE_URL=http://127.0.0.1:8082`.
5. Run secondary direct local baseline through SSM with the same local target URL.
6. Avoid code changes unless the outlier reproduced in a concrete application-side path.

Verification context:

- ALB target group `youth-welfare-web-tg` was healthy before and after:
  - `i-0b8d95e454df5e0f0`: `healthy`
  - `i-0e8a4cc599c1148c8`: `healthy`
- primary container health: `healthy`
- secondary measurement command returned `Status=Success`

Public ALB measurement:

```bash
sleep 70
RUNS=20 WARMUP_RUNS=2 API_LATENCY_DELAY_SECONDS=1 \
  API_LATENCY_ROOT=tmp/performance/policy-list-default-outlier-public-20260714 \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-api-latency-baseline.sh
```

Result:

- artifact: `tmp/performance/policy-list-default-outlier-public-20260714/20260714T121742Z`
- `api_latency_baseline=passed`
- `sample_count=100`
- all scenarios had `errors=0` and `rate_limited=0`

| Scenario | p50 | p95 | p99 | Max |
| --- | ---: | ---: | ---: | ---: |
| `policy_list_default` | 110.6ms | 150.7ms | 284.1ms | 317.5ms |
| `policy_list_active_only` | 105.0ms | 125.0ms | 142.4ms | 146.7ms |

Primary direct local measurement:

```bash
RUNS=20 WARMUP_RUNS=2 API_LATENCY_DELAY_SECONDS=1 \
  API_LATENCY_ROOT=tmp/performance/policy-list-default-outlier-primary-local-20260714 \
  APP_BASE_URL=http://127.0.0.1:8082 \
  bash deploy/performance/run-local-api-latency-baseline.sh
```

Result:

- artifact: `tmp/performance/policy-list-default-outlier-primary-local-20260714/20260714T121955Z`
- `api_latency_baseline=passed`
- `sample_count=120`

| Scenario | p50 | p95 | p99 | Max |
| --- | ---: | ---: | ---: | ---: |
| `policy_list_default` | 70.0ms | 90.1ms | 90.4ms | 90.4ms |
| `policy_list_active_only` | 74.3ms | 140.9ms | 319.2ms | 363.7ms |

Secondary direct local measurement:

```bash
RUNS=20 WARMUP_RUNS=2 API_LATENCY_DELAY_SECONDS=1 \
  API_LATENCY_ROOT=tmp/performance/policy-list-default-outlier-secondary-local-20260714 \
  APP_BASE_URL=http://127.0.0.1:8082 \
  bash deploy/performance/run-local-api-latency-baseline.sh
```

Result:

- artifact on secondary: `tmp/performance/policy-list-default-outlier-secondary-local-20260714/20260714T122232Z`
- `api_latency_baseline=passed`
- `sample_count=120`

| Scenario | p50 | p95 | p99 | Max |
| --- | ---: | ---: | ---: | ---: |
| `policy_list_default` | 70.2ms | 104.5ms | 188.9ms | 210.0ms |
| `policy_list_active_only` | 64.9ms | 104.1ms | 122.5ms | 127.1ms |

Interpretation:

- the previous `policy_list_default max=1685.2ms` outlier did not reproduce
- direct local measurements on both targets were materially lower than the public ALB path
- this points to transient edge/network/runtime variance rather than a repeatable DB or application query regression
- no code change is warranted from this batch
- continue watching `policy_list_default` in future external baselines, but do not prioritize it above open functional/performance work unless the outlier repeats

### 2026-07-14: active policy list count cache

Trigger:

- after the policy list fast path and sort indexes, RDS `EXPLAIN` showed list select queries under `1ms`, while active count remained around `10ms`.
- default public list requests still used Spring Data `Page`, so the native fast-path methods executed the same `COUNT(*)` on each request.
- this count is identical across `LATEST`, `DEADLINE`, and `VIEWS` for the default `ACTIVE_ONLY` list, and can safely tolerate a short TTL.

Why this was changed:

- the previous fast path made row selection cheap, but left repeated active-count work in the request path
- default public list count is shared across ALB targets and across the three supported fast-path sort modes
- filtered/region/Gov24/tag/income lists still need exact per-filter counts and remain unchanged

Implemented changes:

1. `WelfareServiceRepository`
   - added row-only native methods for default `ACTIVE_ONLY` latest/deadline/views list fast paths
   - added `countActiveOnlyVisibleList()` for the exact default active count
2. `WelfareServiceReadRepositoryImpl`
   - default `ACTIVE_ONLY` fast path now builds `PageImpl` from row query plus cached/exact total
   - Redis key: `policy:list:active-only:count:v1`
   - Redis miss runs exact count once and writes it with TTL
   - Redis read/write failures log `errorType` and fall back to exact DB count
3. Config
   - TTL is configurable through `POLICY_ACTIVE_LIST_COUNT_CACHE_TTL_SECONDS`
   - default TTL is `30` seconds

Verification:

```bash
cd backend
./gradlew test \
  --tests 'com.example.welfare.policy.repository.WelfareServiceReadRepositoryImplTest' \
  --tests 'com.example.welfare.policy.service.PolicyListServiceTest' \
  --no-daemon

./gradlew test --tests 'com.example.welfare.policy.*' --no-daemon
```

Result: `BUILD SUCCESSFUL`.

Deployment:

- commit: `6d4aaadd Cache active policy list count`
- pushed to `origin/refactor/admin-dashboard-sections`
- primary node (`i-0b8d95e454df5e0f0`) rebuilt locally with `docker compose --env-file .env.production -f docker-compose.prod.elasticache.yml up -d --build app`
- secondary node (`i-0e8a4cc599c1148c8`) deployed through SSM and fast-forwarded to `6d4aaadde5fae782c976e6a1e0d9e600e0548a63`
- secondary SSM deployment command reported `Failed` with response code `1`, but stdout showed successful build plus `health_attempt=9 container=healthy http=200`
- separate secondary verification command returned `Status=Success`, `container_health="healthy"`, and `actuator={"status":"UP"}`
- ALB target group `youth-welfare-web-tg` after deploy:
  - `i-0b8d95e454df5e0f0`: `healthy`
  - `i-0e8a4cc599c1148c8`: `healthy`

Post-deploy Redis verification:

```bash
curl -fsS -o /dev/null -w 'list_status=%{http_code} time=%{time_total}\n' \
  'https://youthmoa.kr/api/policies?page=0&size=20'
redis-cli get policy:list:active-only:count:v1
redis-cli ttl policy:list:active-only:count:v1
```

Result:

- `list_status=200 time=0.118898`
- `count_value=13334`
- `count_ttl=30`

Post-deploy measurement:

```bash
sleep 70
RUNS=20 WARMUP_RUNS=2 API_LATENCY_DELAY_SECONDS=1 \
  API_LATENCY_ROOT=tmp/performance/active-list-count-cache-external-clean-20260714 \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-api-latency-baseline.sh
```

Result:

- artifact: `tmp/performance/active-list-count-cache-external-clean-20260714/20260714T112956Z`
- `api_latency_baseline=passed`
- `sample_count=100`
- all default public scenarios had `errors=0` and `rate_limited=0`
- an earlier immediate run after manual list probing hit `policy_list_active_only` rate limits, so the accepted run waited for the 60-second list window and used `API_LATENCY_DELAY_SECONDS=1`

External latency summary:

| Scenario | p50 | p95 | p99 | Errors | Rate limited |
| --- | ---: | ---: | ---: | ---: | ---: |
| `policy_list_default` | 124.1ms | 321.3ms | 1412.4ms | 0/20 | 0 |
| `policy_list_active_only` | 108.6ms | 121.3ms | 126.5ms | 0/20 | 0 |
| `policy_search_keyword` | 57.4ms | 107.7ms | 190.2ms | 0/20 | 0 |
| `policy_suggestions` | 77.7ms | 106.2ms | 200.6ms | 0/20 | 0 |
| `policy_search_filtered` | 55.3ms | 60.5ms | 61.7ms | 0/20 | 0 |

Interpretation:

- Redis count cache is working and removes repeated exact `COUNT(*)` from the default active list path within the TTL
- `policy_list_active_only` p95 improved materially in the accepted run, from the prior post-Redis-cache run's `220.9ms` to `121.3ms`
- `policy_list_default` still saw an external network/runtime outlier in this sample, so it should be watched in the next measurement rather than treated as a DB-count regression

### 2026-07-14: Redis shared cache for public search and ranking

Trigger:

- `PolicySearchService` already had a 30-second JVM-local public search cache, but with two ALB targets each EC2 warmed that cache independently.
- `PolicyRankingService` also had a 30-second JVM-local ranking cache, so repeated `/api/policies/ranking` calls could recompute on the other target.
- ElastiCache/Valkey was already part of production runtime, so short-lived public read cache could be shared without adding a new service dependency.

Why this was changed:

- repeated public search/ranking traffic should not pay one warmup per EC2 target
- login-specific search remains uncached because bookmark state differs by user
- Redis must be a performance layer only; Redis read/write/serialization failures must not fail the API request

Implemented changes:

1. `PolicySearchService`
   - local JVM cache remains L1
   - Redis shared cache is now L2 for unauthenticated public search only
   - successful public search responses write to both local L1 and Redis
   - Redis key shape is `policy:search:public:v1:{sha256(request material)}` so raw keywords are not stored in key names
2. `PolicyRankingService`
   - local JVM cache remains L1
   - Redis shared cache is now L2 by normalized ranking size
   - successful ranking responses write to both local L1 and Redis
   - Redis key shape is `policy:ranking:v1:{normalized size}`
3. DTO/config
   - public policy response DTOs declare explicit Jackson builder deserialization for Redis JSON reads
   - TTL settings are configurable through:
     - `POLICY_PUBLIC_SEARCH_CACHE_TTL_SECONDS`
     - `POLICY_RANKING_CACHE_TTL_SECONDS`
   - defaults remain 30 seconds

Verification:

```bash
cd backend
./gradlew test --tests 'com.example.welfare.policy.service.*' --no-daemon
```

Result: `BUILD SUCCESSFUL`.

Deployment:

- commit: `6c606362 Add Redis shared cache for public policy reads`
- pushed to `origin/refactor/admin-dashboard-sections`
- primary node (`i-0b8d95e454df5e0f0`) rebuilt locally with `docker compose --env-file .env.production -f docker-compose.prod.elasticache.yml up -d --build app`
- secondary node (`i-0e8a4cc599c1148c8`) deployed through SSM and fast-forwarded to `6c60636277c3099aff3370e37a44ef8062c0dd49`
- first secondary SSM deployment command reported `Failed` with response code `1`, but its stdout showed successful build plus `health_attempt=10 container=healthy http=200`
- separate secondary verification command returned `Status=Success`, `container_health="healthy"`, and `actuator={"status":"UP"}`
- ALB target group `youth-welfare-web-tg` after deploy:
  - `i-0b8d95e454df5e0f0`: `healthy`
  - `i-0e8a4cc599c1148c8`: `healthy`

Post-deploy measurement:

```bash
RUNS=20 WARMUP_RUNS=2 \
  API_LATENCY_ROOT=tmp/performance/redis-shared-cache-external-20260714 \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-api-latency-baseline.sh
```

Result:

- artifact: `tmp/performance/redis-shared-cache-external-20260714/20260714T111243Z`
- `api_latency_baseline=passed`
- `sample_count=100`
- all default public scenarios had `errors=0` and `rate_limited=0`

External latency summary:

| Scenario | p50 | p95 | p99 | Errors | Rate limited |
| --- | ---: | ---: | ---: | ---: | ---: |
| `policy_list_active_only` | 118.7ms | 220.9ms | 224.7ms | 0/20 | 0 |
| `policy_list_default` | 125.9ms | 166.8ms | 169.9ms | 0/20 | 0 |
| `policy_search_keyword` | 59.0ms | 121.4ms | 207.2ms | 0/20 | 0 |
| `policy_suggestions` | 77.7ms | 101.1ms | 103.2ms | 0/20 | 0 |
| `policy_search_filtered` | 56.7ms | 79.0ms | 80.8ms | 0/20 | 0 |

Repeated public search/ranking check:

- 10 alternating calls to `POST /api/policies/search` and `GET /api/policies/ranking?size=20` through `https://youthmoa.kr`
- first ranking call: `0.944s`, subsequent ranking calls: roughly `45~77ms`
- first search call: `0.436s`, subsequent search calls: roughly `52~84ms`
- Redis verification immediately after the run:
  - `search_keys=1`
  - `ranking_keys=1`
  - `search_ttl=17`
  - `ranking_ttl=18`

Interpretation:

- Redis shared cache is working for the public search and ranking paths
- repeated ALB-facing requests no longer need one recompute per instance once a shared key exists
- this change is worth keeping; remaining list latency is now more related to list count/network variance than ranking/search recomputation

### 2026-07-14: measurement scripts rate-limit-friendly cleanup

Trigger:

- repeated API measurements produced rate-limit noise:
  - repeated `policy_detail_first` calls against the same service ID could return `429`
  - ranking/trending/detail buckets can be consumed during tight sequential measurement loops
  - external `/actuator/health` is not a valid public edge health scenario and fails when `APP_BASE_URL=https://youthmoa.kr`
- this mixed contract errors, rate-limit errors, and real latency in the same summary.

Why this was changed:

- bounded before/after latency baselines should measure normal public list/search/suggestion behavior by default
- rate-limit-sensitive endpoints should still be measurable, but only when explicitly requested
- load tests should be able to run either as pressure tests or as lower-rate controlled runs

Implemented changes:

1. `deploy/performance/run-local-api-latency-baseline.sh`
   - added `API_LATENCY_DELAY_SECONDS`, default `0.2`, between warmup and measured requests
   - added `INCLUDE_RATE_LIMIT_SENSITIVE_ENDPOINTS=false` by default
   - default latency runs now exclude `policy_detail_first`, `policy_ranking`, and `policy_trending`
   - added `INCLUDE_ACTUATOR_HEALTH=auto`; health is included for local URLs and excluded for public URLs by default
   - summary TSV/JSON/text now reports `rate_limited_count`
2. `deploy/performance/run-local-api-load-baseline.sh`
   - added `API_LOAD_REQUEST_DELAY_SECONDS`, default `0`, as an optional per-worker delay
   - summary JSON/text now reports `rate_limited_count`
   - context output records the configured request delay

Verification:

```bash
bash -n deploy/performance/run-local-api-latency-baseline.sh
bash -n deploy/performance/run-local-api-load-baseline.sh

RUNS=20 WARMUP_RUNS=2 \
  API_LATENCY_ROOT=tmp/performance/rate-limit-friendly-latency-20260714 \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-api-latency-baseline.sh

DURATION_SECONDS=2 CONCURRENCY=1 API_LOAD_REQUEST_DELAY_SECONDS=0.2 \
  API_LOAD_ROOT=tmp/performance/rate-limit-friendly-load-20260714 \
  APP_BASE_URL=http://127.0.0.1:8082 \
  bash deploy/performance/run-local-api-load-baseline.sh
```

Result:

- external latency artifact: `tmp/performance/rate-limit-friendly-latency-20260714/20260714T104734Z`
- local load artifact: `tmp/performance/rate-limit-friendly-load-20260714/20260714T104833Z`
- external default latency run measured 100 requests across 5 normal public scenarios
- default external run excluded `/actuator/health`, detail, ranking, and trending
- all default external latency scenarios had `errors=0` and `rate_limited=0`

External latency summary:

| Scenario | p50 | p95 | p99 | Errors | Rate limited |
| --- | ---: | ---: | ---: | ---: | ---: |
| `policy_search_filtered` | 54.3ms | 321.8ms | 446.2ms | 0/20 | 0 |
| `policy_list_active_only` | 109.8ms | 157.9ms | 185.1ms | 0/20 | 0 |
| `policy_list_default` | 111.6ms | 135.1ms | 156.1ms | 0/20 | 0 |
| `policy_suggestions` | 70.6ms | 77.5ms | 78.0ms | 0/20 | 0 |
| `policy_search_keyword` | 52.3ms | 55.8ms | 55.9ms | 0/20 | 0 |

Operational note:

- use `INCLUDE_RATE_LIMIT_SENSITIVE_ENDPOINTS=true` only when the goal is to inspect detail/ranking/trending behavior
- use `INCLUDE_ACTUATOR_HEALTH=true` only for local/internal runs where `/actuator/health` is expected to be reachable
- use `API_LOAD_REQUEST_DELAY_SECONDS` when a load run should avoid turning into a rate-limit test

### 2026-07-14: performance baseline scripts aligned to current search API contract

Trigger:

- 2026-07-14 ALB measurement exposed that some performance scripts still used legacy search endpoints:
  - `GET /api/policies/search?keyword=...`
  - `GET /api/policies/search/suggestions?keyword=...`
- the current backend contract is:
  - `POST /api/policies/search` with `PolicySearchRequest` JSON body
  - `POST /api/policies/search/suggestions` with `PolicySearchSuggestionRequest` JSON body
  - `GET /api/policies/search/trending` remains unchanged
- the same scripts also carried old list/search filter values:
  - `statusFilter=open`
  - `sort=deadline`
- current accepted values are uppercase API values:
  - `statusFilter=ACTIVE_ONLY`
  - `sort=DEADLINE`

Why this was changed:

- the measurement layer must exercise the same API contract as the production frontend
- legacy GET calls created false 400/405-style failures and mixed real performance data with contract drift
- old lowercase filter values created false `C001` validation failures
- without this fix, later before/after latency comparisons for search/list work would not be trustworthy

Implemented changes:

1. `deploy/performance/run-local-api-latency-baseline.sh`
   - endpoint table now includes an optional JSON body column
   - `policy_search_keyword` now calls `POST /api/policies/search`
   - `policy_search_filtered` now calls `POST /api/policies/search` with `ACTIVE_ONLY` and `DEADLINE`
   - `policy_suggestions` now calls `POST /api/policies/search/suggestions`
   - sample TSV records the request body used for POST scenarios
2. `deploy/performance/run-local-edge-baseline.sh`
   - replaced the legacy search GET path with a named POST request scenario
   - summary output now includes scenario and method, not just path
3. `deploy/performance/run-local-api-load-baseline.sh`
   - scenario TSV now has a `body_json` column
   - Python load worker sends JSON body and `Content-Type: application/json` when required
   - filtered search uses current uppercase API values
4. `docs/performance/alb-measurement-2026-07-14.md`
   - recorded the 2-EC2 ALB measurement context, artifacts, ALB/DNS state, and observed API/DB/log metrics before opening the next optimization batch

Verification:

```bash
bash -n deploy/performance/run-local-api-latency-baseline.sh
bash -n deploy/performance/run-local-edge-baseline.sh
bash -n deploy/performance/run-local-api-load-baseline.sh

RUNS=1 WARMUP_RUNS=0 \
  API_LATENCY_ROOT=tmp/performance/api-latency-script-fix-2 \
  APP_BASE_URL=http://127.0.0.1:8082 \
  bash deploy/performance/run-local-api-latency-baseline.sh

DURATION_SECONDS=4 CONCURRENCY=1 \
  API_LOAD_ROOT=tmp/performance/api-load-script-fix-2 \
  APP_BASE_URL=http://127.0.0.1:8082 \
  bash deploy/performance/run-local-api-load-baseline.sh

EDGE_ROOT=tmp/performance/edge-script-fix \
  EXTERNAL_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-edge-baseline.sh
```

Observed verification result:

- syntax checks passed
- short API latency run passed with `errors=0/1` for all measured scenarios
- short API load run passed with `errors=0` across `43` total requests
- edge baseline passed with `POST /api/policies/search status=200`

Commit:

- `6bcf0fdb Update performance baselines for current search API`
- pushed to `origin/refactor/admin-dashboard-sections`

### 2026-05-30: wrapper artifact isolation + search/ranking read-path reduction

Before optimization, accepted server measurements showed three clear hotspots:

- `policy_ranking`: `p95 870.6ms`, `p99 886.7ms`, `max 890.8ms`
- `policy_search_keyword`: `p95 646.5ms`, `p99 710.9ms`, `max 727.0ms`
- `policy_search_keyword_ilike` representative DB explain: `execution 125.734ms`, `seq scan`

The same server run also exposed an operational wrapper bug:

- `run-local-performance-baseline-suite.sh` with `RUN_WRAPPER_BASELINE=true` failed on the first run
- root cause: nested smoke wrappers inherited the parent `ARTIFACT_DIR` and cleanup removed `wrapper-durations.tsv`
- temporary workaround on the server was `KEEP_ARTIFACTS=true`

Implemented changes in this batch:

1. `deploy/performance/run-local-wrapper-duration-baseline.sh`
   - force child wrappers to use dedicated artifact directories
   - stop nested smoke cleanup from deleting the parent wrapper baseline outputs
2. `backend/.../WelfareServiceSearchRepositoryImpl`
   - remove the duplicated `count(*)` + paged `select` search pattern
   - use a single paged query with `COUNT(*) OVER()` so the expensive keyword match is evaluated once per request
3. `backend/.../PolicyRankingService`
   - stop loading every rankable `WelfareService` entity just to compute scores
   - use rankable snapshots for scoring inputs
   - aggregate recent unique views without building a large `IN (...)` list of all active/upcoming service IDs
   - fetch full `WelfareService` rows only for the final selected ranking slice

Local verification for this batch:

```bash
cd backend && ./gradlew test --no-daemon \
  --tests 'com.example.welfare.policy.service.PolicyRankingServiceTest' \
  --tests 'com.example.welfare.policy.service.PolicySearchServiceTest'

bash -n deploy/performance/run-local-wrapper-duration-baseline.sh \
  deploy/performance/run-local-performance-baseline-suite.sh

git diff --check
```

Accepted server remeasurement:

- server HEAD: `487bf64b75d9a20e606ebd89f10bcb0dc9b23db1`
- app redeploy completed
- baseline, extended, and stateful flow suites passed
- first wrapper-inclusive baseline no longer failed on artifact cleanup
- first wrapper-inclusive baseline did still fail once because the `17:00 UTC` collect schedule held the collect-global lock and `current_priority` hit `409/COL002`
- after lock release, the same command passed without `KEEP_ARTIFACTS=true`

Observed deltas from the accepted server rerun:

- `policy_search_keyword` p95: `646.5ms -> 338.6ms`
- `current_priority` wrapper: `154.715s -> 105.535s`
- `policy_ranking` p95: `870.6ms -> 999.4ms`
- `recommendation_observation` wrapper: `5.956s -> 7.502s`
- `policy_search_keyword_ilike` representative explain: `125.734ms -> 133.042ms`, still `Seq Scan`

Interpretation:

- wrapper artifact isolation is closed
- search API latency improved materially
- ranking remains the primary unresolved hotspot
- the representative fallback `ILIKE` query still needs DB-side help even though API search latency improved

## Closed Batch

### 2026-05-30: ranking 2차 + search representative explain 보강

Current trigger values from the accepted server rerun:

- `policy_ranking`: `p95 999.4ms`, `p99 1008.5ms`, `max 1010.8ms`
- `policy_search_keyword_ilike` representative DB explain: `execution 133.042ms`, `seq scan`

Implemented changes in this batch:

1. `ServiceViewLogRepository` / `PolicyRankingService`
   - unique view aggregation now joins rankable statuses directly instead of scanning all recent view rows without status filtering
2. `db/migration/V2026_05_30_01__add_performance_indexes.sql`
   - add `service_view_logs(viewed_at, service_id)` for recent-window aggregation
   - add trigram indexes for `description`, `support_content`, and the combined search document expression
3. `schema.sql`
   - fresh init path matches the same indexes

Local verification for this batch:

```bash
cd backend && ./gradlew test --no-daemon \
  --tests 'com.example.welfare.policy.service.PolicyRankingServiceTest' \
  --tests 'com.example.welfare.policy.service.PolicySearchServiceTest'

git diff --check
```

Accepted server remeasurement:

- server HEAD: `909bb4d682f9eee5cd6f09556dc584fe3b3696d4`
- runtime DB indexes applied manually with the RDS master account because `DB_MIGRATION_USERNAME` lacked owner privileges
- app redeploy completed
- baseline and extended suites passed

Observed deltas from the accepted server rerun:

- `policy_ranking` p95: `999.4ms -> 885.1ms`
- `recommendation_observation` wrapper: `7.502s -> 6.231s`
- `policy_search_keyword` p95: `338.6ms -> 628.7ms`
- `current_priority` wrapper: `105.535s -> 208.540s`
- `policy_search_keyword_ilike` representative explain: `133.042ms -> 127.449ms`, still `Seq Scan`

Interpretation:

- ranking improved enough to keep the recent-view aggregation/index change
- search did not close; API latency regressed and the representative explain remained on a seq scan
- current-priority total became harder to interpret because the wrapper still reported only a top-level duration

## Closed Batch

### 2026-05-30: current-priority breakdown observability + search 3차 query-shape cleanup

Current trigger values from the accepted server rerun:

- `policy_search_keyword`: `p95 628.7ms`, `p99 650.7ms`, `max 656.2ms`
- `policy_search_keyword_ilike`: `execution 127.449ms`, `seq scan`
- `current_priority`: `208.540s`

Planned changes in this batch:

1. `run-local-active-baseline-suite.sh` / `run-local-current-priority-suite.sh`
   - publish nested step durations in summary/json so the next server rerun shows which sub-step regressed
2. `WelfareServiceSearchRepositoryImpl`
   - narrow expensive trigram scoring back to `title`/`keyword`
   - keep full-document FTS as the long-text match path
   - align substring fallback with `lower(...) LIKE` so the existing trigram expression indexes are more likely to help
3. `run-local-db-query-baseline.sh`
   - align the representative `policy_search_keyword_ilike` explain with the lowered expression/index path instead of raw `ILIKE`

Accepted server remeasurement:

- server HEAD: `f351ee32466ce9aed73c7d755e38ab6bcac2db03`
- app redeploy completed
- baseline, wrapper baseline, and extended suites passed

Observed deltas from the accepted server rerun:

- `policy_search_keyword` p95: `628.7ms -> 417.1ms` in the wrapper-inclusive baseline
- `policy_search_keyword` p95: `628.7ms -> 334.6ms` in the baseline-only run
- `policy_ranking` p95: `885.1ms -> 933.3ms` in the wrapper-inclusive baseline
- `policy_search_keyword_ilike` representative explain: `127.449ms -> 258.805ms`, still `Seq Scan`
- `current_priority` wrapper remained large, but the rerun exposed nested durations:
  - `active_baseline_duration_ms=188817`
  - `recommendation_observation_duration_ms=5895`

Interpretation:

- search API latency recovered, so keeping the simplified title/keyword trigram path is reasonable
- the representative search explain is still unresolved and may need a different benchmark query or a different index/query contract
- `current_priority` is no longer opaque; most of the total time is `active_baseline`
- ranking still needs one more bounded optimization

## Closed Batch

### 2026-05-30: ranking 3차 + active baseline step contract cleanup

Current trigger values from the accepted server rerun:

- `policy_ranking`: `p95 933.3ms`, `p99 935.3ms`, `max 935.7ms`
- `policy_search_keyword_ilike`: `execution 258.805ms`, `seq scan`
- `current_priority`: `194.844s`, but now attributable mostly to `active_baseline`

Planned changes in this batch:

1. `PolicyRankingService`
   - add a short TTL cache for the public `/api/policies/ranking` read path so repeated baseline hits stop recomputing the same ranking within a small window
2. `run-local-active-baseline-suite.sh`
   - finish the step contract cleanup with stable snake_case labels and direct frontend `lint/build/e2e` duration fields
3. performance docs / troubleshooting
   - record the accepted rerun and distinguish endpoint hotspots from wrapper bundle time

Accepted server remeasurement:

- server HEAD: `10e3bea61cf1bc7812d4264e3a51ed5072f79c66`
- first boot failed because `PolicyRankingService` had two constructors and Spring did not have a fixed runtime selection
- server hotfix added `@Autowired` to the public 2-arg constructor before rebuild
- after that hotfix, baseline, wrapper baseline, and extended suites passed

Observed deltas from the accepted server rerun:

- `policy_ranking` p95: `933.3ms -> 5.8ms` in the wrapper-inclusive baseline
- `policy_ranking` p95: `1088.2ms -> 8.5ms` in the baseline-only run
- `policy_search_keyword` p95: `417.1ms -> 426.7ms` in the wrapper-inclusive baseline
- `policy_search_keyword` p95: `334.6ms -> 402.1ms` in the baseline-only run
- `policy_search_keyword_ilike` representative explain: `258.805ms -> 117.078ms`, still `Seq Scan`
- `current_priority` wrapper: `194.844s -> 156.841s`
- `active_baseline` nested durations are now stable and machine-friendly:
  - `backend_tests_duration_ms=61941`
  - `frontend_lint_duration_ms=8446`
  - `frontend_build_duration_ms=1492`
  - `frontend_e2e_duration_ms=59436`
  - `ops_baseline_duration_ms=3056`
  - `collect_legacy_repair_duration_ms=16416`

Interpretation:

- the ranking TTL cache is accepted and closed
- `active_baseline` step contract cleanup is accepted and closed
- search API remains acceptable, but the representative explain still does not close because the lowered `LIKE` benchmark remains a seq scan
- the constructor injection fix is mandatory follow-through because the accepted server rerun required a manual hotfix

## Current Active Batch

### 2026-05-30: ranking constructor fix closeout + search representative benchmark realignment

Current trigger values from the accepted server rerun:

- `policy_search_keyword_ilike`: `execution 117.078ms`, `seq scan`
- server hotfix was required for `PolicyRankingService` constructor selection on `HEAD=10e3bea61cf1bc7812d4264e3a51ed5072f79c66`

Planned changes in this batch:

1. `PolicyRankingService`
   - merge the constructor-selection fix into the repository so the runtime no longer depends on a server hotfix
2. runtime search indexes
   - add the missing `idx_ws_title_trgm` / `idx_ws_keyword_trgm` migration so existing RDS instances match fresh init `schema.sql`
3. performance docs / troubleshooting
   - record the accepted rerun and the constructor failure as a closed operational issue
4. search representative benchmark
   - replace the current lowered `LIKE` explain with a more realistic API-adjacent query shape so future server reruns compare the actual search contract rather than a stale fallback probe

Server remeasurement status:

- pending after the constructor-fix merge and the representative benchmark realignment
- requires DB index apply + app redeploy
- compare ranking and wrapper values against the `10e3bea61cf1bc7812d4264e3a51ed5072f79c66` server baseline
- treat `policy_search_keyword_ilike` as historical; next accepted DB comparison key should be `policy_search_keyword_api_shape`

Accepted server remeasurement:

- server HEAD: `731f5f2c2e6f58a61ba5b7d89ff20ce6e36990f3`
- runtime DB `idx_ws_title_trgm`, `idx_ws_keyword_trgm` applied with the RDS master account
- app redeploy was not needed for this batch
- baseline and wrapper baseline passed

Observed deltas from the accepted server rerun:

- `policy_search_keyword_api_shape` explain:
  - baseline `execution 314.609ms`, `seq_scan=false`, `index_scan=true`
  - wrapper baseline `execution 312.486ms`, `seq_scan=false`, `index_scan=true`
- `policy_ranking` p95 remained single-digit:
  - baseline `8.7ms`
  - wrapper baseline `6.4ms`
- `policy_search_keyword` p95 remained the next real hotspot:
  - baseline `481.0ms`
  - wrapper baseline `340.9ms`
- `current_priority` nested durations:
  - `active_baseline_duration_ms=146306`
  - `recommendation_observation_duration_ms=5998`

Interpretation:

- constructor-fix closeout is complete
- representative search benchmark drift is closed
- the remaining work is no longer DB-plan correctness but actual public search API latency variance

## Current Active Batch

### 2026-05-30: public policy search TTL cache

Current trigger values from the accepted server rerun:

- `policy_search_keyword` baseline `p95 481.0ms`, `p99 513.8ms`, `max 522.0ms`
- `policy_search_keyword` wrapper baseline `p95 340.9ms`, `p99 346.6ms`, `max 348.0ms`
- `policy_search_keyword_api_shape` is already `index_scan=true`, so the remaining hotspot is above the representative DB plan

Planned changes in this batch:

1. `PolicySearchService`
   - add a bounded TTL cache for `userId=null` public search requests
   - keep logged-in requests uncached because bookmark state is user-specific
2. tests
   - prove anonymous repeated search reuses the cache
   - prove logged-in search still executes on each call
3. docs / troubleshooting
   - record that API latency can remain after representative explain is closed because response assembly still contributes meaningfully

Accepted server remeasurement:

- server HEAD: `7b53d637395aa6c5978a633bc25477f30fd38c9f`
- app redeploy completed
- baseline and wrapper baseline passed
- recent app log `ERROR/Exception 없음`

Observed deltas from the accepted server rerun:

- `policy_search_keyword` baseline:
  - `p95 481.0ms -> 33.5ms`
  - `p99 513.8ms -> 33.8ms`
  - `max 522.0ms -> 33.9ms`
- `policy_search_keyword` wrapper baseline:
  - `p95 340.9ms -> 20.3ms`
  - `p99 346.6ms -> 21.0ms`
  - `max 348.0ms -> 21.2ms`
- server logs showed the cache effect directly:
  - first same-shape search `294~324ms`
  - repeated same-shape search `0~1ms`
- `policy_search_keyword_api_shape` remained stable:
  - baseline `execution 316.565ms`, `seq_scan=false`, `index_scan=true`
  - wrapper baseline `execution 313.969ms`, `seq_scan=false`, `index_scan=true`

Interpretation:

- the public search TTL cache is accepted and closed
- the representative DB plan stayed healthy while the API hotspot itself collapsed
- the remaining performance work is no longer search/ranking endpoint latency, but wrapper bundle rerun cost

## Closed Batch

### 2026-05-30: current-priority recent baseline reuse

Current trigger values from the accepted server rerun:

- `current_priority` stayed materially above recommendation observation because `active_baseline` dominated the total
- recent accepted examples:
  - `active_baseline_duration_ms=146306`
  - `recommendation_observation_duration_ms=5998`

Planned changes in this batch:

1. `run-local-active-baseline-suite.sh`
   - publish stable `tmp/active-baseline-suite/latest*` snapshot and summary/json
   - include `suite_duration_ms` and run settings in the summary/json contract
2. `run-local-current-priority-suite.sh`
   - reuse a recent passed `active_baseline` with matching settings inside a short TTL
   - keep explicit `active_baseline_reused=true/false` in summary/json
3. docs / troubleshooting
   - record that this optimization targets wrapper rerun cost, not application endpoint latency

Accepted server remeasurement:

- server HEAD: `1e62b24dde6405980d9aba8161559c29574a5a6f`
- app redeploy was not needed for this batch
- `RUN_BACKEND_TESTS=false RUN_FRONTEND_BASELINE=false RUN_OPS_BASELINE=false RUN_COLLECT_LEGACY_REPAIR=false bash deploy/smoke/run-local-active-baseline-suite.sh`
  - `active_baseline_suite=passed`
  - `tmp/active-baseline-suite/latest*` stable snapshot and summary/json published
  - `suite_duration_ms=0` recorded for the no-op verification run
- `RUN_RECOMMENDATION_OBSERVATION=false ... bash deploy/smoke/run-local-current-priority-suite.sh`
  - `current_priority_suite=passed`
  - `active_baseline_reused=true`
  - `reuse_age_seconds=17`
  - `reuse_ttl_seconds=900`

Interpretation:

- `active_baseline` standalone latest snapshot contract is accepted and closed
- `current_priority` recent baseline reuse is accepted and closed
- remaining wrapper cost is now bounded by whether a fresh `active_baseline` rerun is actually required, not by forced duplicate reruns every time

## Maintenance Note

- `search/ranking` endpoint hotspot work is closed on the accepted server baseline
- representative search benchmark drift is closed on `policy_search_keyword_api_shape`
- `current_priority` rerun-cost mitigation is closed with recent baseline reuse
- remaining performance work is optional follow-up only:
  - extended/load tuning if a new load-specific target is approved
  - bundle-duration budgeting if operator cadence changes

## Closed Batch

### 2026-06-02: Gov24 region backfill read/write path reduction

Current trigger values from current server Docker runtime Gov24 region work:

- `scope=regions&limitPerSource=0` scanned `10954` Gov24 LIST payloads
- previous implementation hydrated full JPA `RawApiPayload` / `WelfareService` pairs, then did per-service region replacement inside one long transaction
- server Docker runtime observation before the change was minutes-level runtime with an idle transaction while parsing/writing the full Gov24 set

Implemented changes in this batch:

1. `NormalizedPolicySidecarBackfillReadRepository`
   - add a lightweight region backfill projection (`serviceId`, `sourceId`, `payloadJson`)
   - read Gov24 LIST targets with one native join query instead of loading full entities and doing per-row service lookup
2. `CollectItemRegionCommandRepository`
   - add `replaceAllBatch(...)`
   - delete existing `service_regions` for all successfully parsed services in chunks
   - insert the regenerated region rows with a single JDBC batch path
3. `PolicyEmbeddingRefreshRequestService`
   - keep collect batch embedding refresh as post-save best effort
   - strict embedding failure is logged as a warning and no longer turns a successful collect save into HTTP 500

Server Docker runtime verification for this batch:

```bash
bash deploy/smoke/run-local-gov24-acceptance-suite.sh

cd backend && ./gradlew test --no-daemon \
  --tests 'com.example.welfare.collect.normalization.NormalizedPolicySidecarBackfillServiceTest' \
  --tests 'com.example.welfare.collect.repository.CollectItemRegionCommandRepositoryImplTest' \
  --tests 'com.example.welfare.collect.repository.NormalizedPolicySidecarBackfillReadRepositoryImplTest' \
  --tests 'com.example.welfare.policy.service.PolicyEmbeddingRefreshRequestServiceTest'

COMPOSE_FILE=docker-compose.yml docker compose build app
COMPOSE_FILE=docker-compose.yml docker compose up -d app

POST /api/admin/collect/gov24-sidecars-backfill?scope=regions&limitPerSource=0
bash deploy/smoke/run-local-gov24-region-backfill-smoke.sh
bash deploy/smoke/run-local-gov24-region-coverage-audit.sh
POST /api/admin/collect/gov24-details?maxCallsPerRun=1
bash deploy/smoke/run-local-gov24-collect-embedding-boundary-smoke.sh
bash deploy/smoke/run-local-gov24-recommend-surface-audit.sh
bash deploy/smoke/run-local-gov24-recommend-score-audit.sh
KEEP_ARTIFACTS=true bash deploy/smoke/run-local-gov24-education-signal-smoke.sh
```

Observed server Docker runtime deltas:

- Gov24 region backfill endpoint: minutes-level previous server Docker observation -> `duration_ms=10258`
- service log: `scanned=10954`, `upserted=10954`, `missing=0`, `failed=0`, `regions=33837`, `elapsedMs=9933`
- initial post-change region coverage before the later regionless follow-up: `9794 / 10954 = 89.41%`
- Gov24 detail collect with strict embedding fallback returned `200`, `requested=1 saved=1 skipped=0 failed=0`
- embedding fallback log stayed warning-only: `batch embedding refresh skipped serviceCount=1`
- education smoke: fresh batch `32`, top2 `GOV24:2`, Gov24 top10 rows `10`
- Gov24 acceptance suite: `passed`, `8` steps, `suite_duration_ms=52002`
- latest server Docker acceptance summary: `tmp/gov24-acceptance-suite/latest-gov24-acceptance-summary.txt`
- follow-up regionless audit with local-agency SGG/stem inference stayed seconds-level:
  region suite step `7839ms`, endpoint metric `4708ms`, coverage `9814 / 10954 = 89.59%`,
  region rows `33010`, full acceptance suite `53202ms`

Interpretation:

- Gov24 region backfill is now a bounded bulk repair path, not a long full-entity replay path
- collect save success and embedding rebuild health are intentionally separated
- current server Docker runtime measurement is accepted for this batch; production/RDS parity still depends on the target deployment data shape, especially detail/support backlog state

## Closed Batch

### 2026-06-02: Gov24 missing list sidecar batch write path

Current trigger values from the server Docker Gov24 acceptance suite:

- `gov24-sidecars-backfill?scope=list&missingOnly=true&limitPerSource=0` had to refill `10952` missing summary-slot services
- the generic sidecar writer took `gov24_sidecar_backfill_duration_ms=308290`
- the full Gov24 acceptance suite took `suite_duration_ms=368329`
- runtime observation showed app CPU-bound work while the DB had no long active query

Implemented changes in this batch:

1. `NormalizedPolicySidecarBackfillReadRepository`
   - add a lightweight missing-summary projection (`serviceId`, `sourceId`, `payloadJson`)
   - avoid hydrating full raw payload/service entity pairs for Gov24 `missingOnly=list`
2. `DeferredNormalizedPolicySidecarCommandRepository`
   - add `replaceTaxonomySidecarsBatch(...)`
   - batch upsert `service_taxonomies`
   - chunked delete/batch insert `service_taxonomy_summary_slots`
   - chunked delete/batch insert `service_taxonomy_terms`
3. `NormalizedPolicySidecarBackfillService`
   - route only Gov24 `missingOnly=list` through the batch taxonomy path
   - leave the general list sidecar replay path on the existing full writer

Server Docker runtime verification for this batch:

```bash
cd backend && ./gradlew test --no-daemon \
  --tests 'com.example.welfare.collect.normalization.NormalizedPolicySidecarBackfillServiceTest' \
  --tests 'com.example.welfare.collect.repository.DeferredNormalizedPolicySidecarCommandRepositoryImplTest' \
  --tests 'com.example.welfare.collect.repository.NormalizedPolicySidecarBackfillReadRepositoryImplTest'

COMPOSE_FILE=docker-compose.yml docker compose build app
COMPOSE_FILE=docker-compose.yml docker compose up -d app

# controlled measurement: delete Gov24 required summary slots, then refill through the smoke/suite.
bash deploy/smoke/run-local-gov24-sidecar-backfill-smoke.sh
KEEP_ARTIFACTS=true bash deploy/smoke/run-local-gov24-acceptance-suite.sh
```

Observed server Docker runtime deltas:

- sidecar full-fill smoke: `before_missing=10954 -> after_missing=0`
- service log: `scanned=10954`, `upserted=10954`, `missing=0`, `failed=0`, `elapsedMs=16118`
- acceptance suite sidecar step: `308290ms -> 15377ms`
- full Gov24 acceptance suite: `368329ms -> 74226ms`
- quality audit after refill stayed closed: `detail_rows=10954`, `support_raw=10954`, `support_fact_services=10954`, `support_missing_fact_services=0`

Interpretation:

- Gov24 missing-list sidecar repair is now seconds-level for a full `10954` service refill.
- The generic full sidecar replay path remains available for non-missing repair, but operator smoke/acceptance no longer pays that cost for summary-slot gaps.
- Current server Docker runtime measurement is accepted and should replace the previous `308s` sidecar acceptance number.
## Comparison Table

| Date | Before HEAD | After HEAD | Area | Change | Before | After | Delta | Decision |
|---|---|---|---|---|---:|---:|---:|---|
| 2026-05-30 | `a57dcf7510b47537c95d90a76b15131ebfca0520` | `487bf64b75d9a20e606ebd89f10bcb0dc9b23db1` | Wrapper | child artifact isolation | first run failed on artifact cleanup | first run passed artifact-wise, later blocked once by collect lock only | cleanup failure removed | closed |
| 2026-05-30 | `a57dcf7510b47537c95d90a76b15131ebfca0520` | `487bf64b75d9a20e606ebd89f10bcb0dc9b23db1` | Search | single-pass keyword page query | `p95 646.5ms` | `p95 338.6ms` | `-307.9ms` | improvement accepted |
| 2026-05-30 | `a57dcf7510b47537c95d90a76b15131ebfca0520` | `487bf64b75d9a20e606ebd89f10bcb0dc9b23db1` | Current Priority | nested search/ranking effects | `154.715s` | `105.535s` | `-49.180s` | improvement accepted |
| 2026-05-30 | `a57dcf7510b47537c95d90a76b15131ebfca0520` | `487bf64b75d9a20e606ebd89f10bcb0dc9b23db1` | Ranking | snapshot-based scoring + no full-entity preload | `p95 870.6ms` | `p95 999.4ms` | `+128.8ms` | needs follow-up |
| 2026-05-30 | `a57dcf7510b47537c95d90a76b15131ebfca0520` | `487bf64b75d9a20e606ebd89f10bcb0dc9b23db1` | Wrapper | recommendation observation | `5.956s` | `7.502s` | `+1.546s` | observe, likely environment-sensitive |
| 2026-05-30 | `487bf64b75d9a20e606ebd89f10bcb0dc9b23db1` | `909bb4d682f9eee5cd6f09556dc584fe3b3696d4` | Ranking | status-filtered unique-view aggregation + recent-view index | `p95 999.4ms` | `p95 885.1ms` | `-114.3ms` | improvement accepted |
| 2026-05-30 | `487bf64b75d9a20e606ebd89f10bcb0dc9b23db1` | `909bb4d682f9eee5cd6f09556dc584fe3b3696d4` | Search | extra trigram indexes + broadened similarity scoring | `p95 338.6ms` | `p95 628.7ms` | `+290.1ms` | replace with 3차 query-shape cleanup |
| 2026-05-30 | `487bf64b75d9a20e606ebd89f10bcb0dc9b23db1` | `909bb4d682f9eee5cd6f09556dc584fe3b3696d4` | Search DB | extra trigram indexes for representative fallback query | `133.042ms seq scan` | `127.449ms seq scan` | `-5.593ms` | still unresolved |
| 2026-05-30 | `487bf64b75d9a20e606ebd89f10bcb0dc9b23db1` | `909bb4d682f9eee5cd6f09556dc584fe3b3696d4` | Current Priority | full wrapper total | `105.535s` | `208.540s` | `+103.005s` | add nested duration breakdown |
| 2026-05-30 | `909bb4d682f9eee5cd6f09556dc584fe3b3696d4` | `f351ee32466ce9aed73c7d755e38ab6bcac2db03` | Search | title/keyword-focused query-shape cleanup | `p95 628.7ms` | `p95 417.1ms` | `-211.6ms` | improvement accepted |
| 2026-05-30 | `909bb4d682f9eee5cd6f09556dc584fe3b3696d4` | `f351ee32466ce9aed73c7d755e38ab6bcac2db03` | Search DB | lowered representative explain | `127.449ms seq scan` | `258.805ms seq scan` | `+131.356ms` | representative query still unresolved |
| 2026-05-30 | `909bb4d682f9eee5cd6f09556dc584fe3b3696d4` | `f351ee32466ce9aed73c7d755e38ab6bcac2db03` | Wrapper | current_priority nested duration visibility | top-level total only | `active_baseline_duration_ms`, `recommendation_observation_duration_ms` exposed | observability improved | accepted |
| 2026-05-30 | `f351ee32466ce9aed73c7d755e38ab6bcac2db03` | `10e3bea61cf1bc7812d4264e3a51ed5072f79c66` | Ranking | 30s public read TTL cache | `p95 933.3ms` | `p95 5.8ms` | `-927.5ms` | improvement accepted |
| 2026-05-30 | `f351ee32466ce9aed73c7d755e38ab6bcac2db03` | `10e3bea61cf1bc7812d4264e3a51ed5072f79c66` | Active Baseline | snake_case step durations | frontend bundled, key names unstable | `backend_tests/frontend_lint/frontend_build/frontend_e2e/...` direct keys | observability improved | accepted |
| 2026-05-30 | `f351ee32466ce9aed73c7d755e38ab6bcac2db03` | `10e3bea61cf1bc7812d4264e3a51ed5072f79c66` | Search DB | lowered representative explain | `258.805ms seq scan` | `117.078ms seq scan` | `-141.727ms` | still unresolved |
| 2026-05-30 | `10e3bea61cf1bc7812d4264e3a51ed5072f79c66` | `731f5f2c2e6f58a61ba5b7d89ff20ce6e36990f3` | Search DB | runtime `title/keyword` trgm index + API-shape benchmark realignment | historical lowered LIKE seq scan | `policy_search_keyword_api_shape` index scan | contract realigned | accepted |
| 2026-05-30 | `731f5f2c2e6f58a61ba5b7d89ff20ce6e36990f3` | `7b53d637395aa6c5978a633bc25477f30fd38c9f` | Search | public policy search TTL cache | baseline `p95 481.0ms`, wrapper `p95 340.9ms` | baseline `p95 33.5ms`, wrapper `p95 20.3ms` | `-447.5ms`, `-320.6ms` | improvement accepted |
| 2026-05-30 | `7b53d637395aa6c5978a633bc25477f30fd38c9f` | `1e62b24dde6405980d9aba8161559c29574a5a6f` | Current Priority | recent `active_baseline` reuse | recent baseline rerun required every time | `active_baseline_reused=true`, `reuse_age_seconds=17`, `reuse_ttl_seconds=900` | duplicate rerun avoided | improvement accepted |
| 2026-06-02 | server dirty worktree | server dirty worktree | Gov24 Backfill | lightweight projection + bulk region replace | minutes-level server Docker observation | endpoint `10.258s`, service `9.933s` for `10954` rows; acceptance suite `52.002s` | minutes -> seconds | accepted on current server Docker runtime |
| 2026-06-02 | server dirty worktree | server dirty worktree | Gov24 Region Quality | local-agency SGG/stem inference follow-up | `9794/10954` coverage, `1160` regionless | `9814/10954` coverage, `1140` regionless; region step `7.839s`, suite `53.202s` | `+20` services inferred | accepted on current server Docker runtime |
| 2026-06-02 | server dirty worktree | server dirty worktree | Gov24 Sidecar | lightweight missing-list projection + batch taxonomy write | sidecar step `308.290s`, suite `368.329s` | sidecar step `15.377s`, suite `74.226s` for `10954` refill | `-292.913s`, `-294.103s` | accepted on current server Docker runtime |
