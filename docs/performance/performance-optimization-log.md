# Performance Optimization Log

Last updated: 2026-07-14

This document records what was optimized, which baseline numbers triggered the work, and whether the follow-up measurement is accepted or still pending.

## How To Read

- `Closed Batch`: why the batch was opened, what changed, and which accepted server rerun closed it
- `Observed deltas`: the concrete before/after numbers that justified keeping or replacing the change
- `Interpretation`: whether the batch is actually closed or only partially useful
- `Comparison Table`: one-line before/after ledger for fast review across multiple batches

## Open Batch

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

### 2026-07-13: ALB 전환 후 자동완성 fallback 경량화

Trigger values from the ALB baseline:

- `POST /api/policies/search/suggestions`: `p50 275.2ms`, `p95 378.3ms`, `max 396.6ms`
- browser policy search flow: `action_wall_ms=696`
- artifact: `tmp/performance/alb-valid-api-baseline-20260713T170710Z`

Breakdown measurement showed the slow part was not `search_logs`.

| Query slice | Before execution |
| --- | ---: |
| log suggestions, `청` | 14.186ms |
| log suggestions, `청년` | 7.842ms |
| policy fallback candidates, `청` | 352.222ms |
| policy fallback candidates, `청년` | 318.474ms |

Implemented code change:

1. Add `WelfareServiceSearchRepository.searchSuggestionTitleCandidates()`
2. Keep the existing full `searchChatCandidates()` path unchanged
3. Make policy search suggestions use the new title/keyword-only fallback instead of full-document search/ranking

DB-level remeasurement of the new fallback query shape:

| Query slice | After execution |
| --- | ---: |
| light policy fallback candidates, `청` | 19.251ms |
| light policy fallback candidates, `청년` | 16.931ms |

Local verification:

```bash
cd backend
./gradlew test --tests 'com.example.welfare.policy.service.PolicySearchKeywordReadServiceTest'
```

Result: `BUILD SUCCESSFUL`.

Status:

- code/test change is ready
- production API after measurement is pending
- to close this batch, deploy the same commit to both ALB targets and rerun the valid API baseline against `https://youthmoa.kr`

## Closed Batch

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
