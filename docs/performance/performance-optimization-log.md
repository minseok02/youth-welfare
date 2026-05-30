# Performance Optimization Log

Last updated: 2026-05-30

This document records what was optimized, which baseline numbers triggered the work, and which server remeasurement is still pending.

## Closed Batch

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

### 2026-05-30: ranking constructor fix closeout + representative search explain follow-up

Current trigger values from the accepted server rerun:

- `policy_search_keyword_ilike`: `execution 117.078ms`, `seq scan`
- server hotfix was required for `PolicyRankingService` constructor selection on `HEAD=10e3bea61cf1bc7812d4264e3a51ed5072f79c66`

Planned changes in this batch:

1. `PolicyRankingService`
   - merge the constructor-selection fix into the repository so the runtime no longer depends on a server hotfix
2. performance docs / troubleshooting
   - record the accepted rerun and the constructor failure as a closed operational issue
3. search representative benchmark
   - decide whether the current lowered `LIKE` explain should be replaced with a more realistic API-adjacent representative query, or whether a further DB/index change is justified

Server remeasurement status:

- pending after the constructor-fix merge only
- requires app redeploy
- compare against the `10e3bea61cf1bc7812d4264e3a51ed5072f79c66` server baseline

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
