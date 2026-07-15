# Current Performance Baseline

Last updated: 2026-07-15

This document records the current performance baseline. Raw artifacts are generated under `tmp/performance`.

## Current Baseline Commands

API, DB, Redis:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' RUNS=7 \
  bash deploy/performance/run-local-performance-baseline-suite.sh
```

Wrapper duration, including current priority:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' RUN_WRAPPER_BASELINE=true \
FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
  bash deploy/performance/run-local-performance-baseline-suite.sh
```

Extended baseline:

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
EXTERNAL_BASE_URL='https://youthmoa.kr' \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
DURATION_SECONDS=10 CONCURRENCY=3 \
  bash deploy/performance/run-local-performance-extended-suite.sh
```

Stateful flow duration:

```bash
ENV_FILE=.env.production APP_BASE_URL='http://127.0.0.1:8082' \
RUN_RUNTIME_API=true RUN_AUTH_SESSION=false \
RUN_BOOKMARK_CONSISTENCY=false RUN_ADMIN_FORCED_LOGOUT=false \
  bash deploy/performance/run-local-stateful-flow-duration-baseline.sh
```

Deep observation, excluding long load tests:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
  bash deploy/performance/run-local-performance-deep-observation-suite.sh
```

## Artifact Index

- API latency latest: `tmp/performance/api-latency/latest`
- DB query latest: `tmp/performance/db-query/latest`
- Redis latest: `tmp/performance/redis/latest`
- Wrapper duration latest: `tmp/performance/wrapper-duration/latest`
- Full suite latest: `tmp/performance/full-suite/latest`
- API load latest: `tmp/performance/api-load/latest`
- Web vitals latest: `tmp/performance/web-vitals/latest`
- JVM runtime latest: `tmp/performance/jvm-runtime/latest`
- Edge latest: `tmp/performance/edge/latest`
- Stateful flow duration latest: `tmp/performance/stateful-flow-duration/latest`
- Extended suite latest: `tmp/performance/extended-suite/latest`
- DB observability latest: `tmp/performance/db-observability/latest`
- Redis observability latest: `tmp/performance/redis-observability/latest`
- Web interaction latest: `tmp/performance/web-interaction/latest`
- nginx log observability latest: `tmp/performance/nginx-log-observability/latest`
- Deep observation suite latest: `tmp/performance/deep-observation-suite/latest`

## Baseline Summary

## 2026-07-15 Accepted Ranking Snapshot Checkpoint

This checkpoint is the current accepted ranking endpoint baseline after enabling the guarded Redis precomputed ranking snapshot for `size=20`.

Runtime state:

- commit: `d0c5654e5e4d24f86671c760aab0b21e3eb832e0`
- deployed code commit: `ad16e288c2f8688b420b7b8d0a968053a4a78c1b`
- primary EC2 `i-0b8d95e454df5e0f0`: scheduler enabled, `POLICY_RANKING_SNAPSHOT_READ_ENABLED=true`, `POLICY_RANKING_SNAPSHOT_REFRESH_ENABLED=true`
- secondary EC2 `i-0e8a4cc599c1148c8`: scheduler disabled, `POLICY_RANKING_SNAPSHOT_READ_ENABLED=true`, `POLICY_RANKING_SNAPSHOT_REFRESH_ENABLED=false`
- request-time candidate mode remains disabled
- ALB target group: both targets `healthy`

Measurement commands:

```bash
COLD_RUNS=7 WARM_RUNS=7 CACHE_TTL_WAIT_SECONDS=31 WARM_DELAY_SECONDS=0.2 \
  POLICY_CACHE_TAIL_ROOT=tmp/performance/ranking-snapshot-stability-local-20260715 \
  APP_BASE_URL=http://127.0.0.1:8082 \
  bash deploy/performance/run-local-policy-cache-tail-baseline.sh

COLD_RUNS=7 WARM_RUNS=7 CACHE_TTL_WAIT_SECONDS=31 WARM_DELAY_SECONDS=0.2 \
  POLICY_CACHE_TAIL_ROOT=tmp/performance/ranking-snapshot-stability-edge-20260715 \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-policy-cache-tail-baseline.sh
```

Artifacts:

- local stability: `tmp/performance/ranking-snapshot-stability-local-20260715/20260715T154113Z`
- edge stability: `tmp/performance/ranking-snapshot-stability-edge-20260715/20260715T154501Z`
- public API shape captures: `tmp/performance/ranking-snapshot-stability-correctness-20260715`
- runtime smoke: `tmp/prod-runtime-smoke/snapshot-stability-20260715-retry-env`
- post-snapshot broader baseline and next-bottleneck decision: [post-snapshot-current-baseline-2026-07-15.md](./post-snapshot-current-baseline-2026-07-15.md)

Accepted ranking numbers:

| Path | Cold ranking p50 | Cold ranking p95 | Cold ranking max | Warm ranking p95 | Success |
| --- | ---: | ---: | ---: | ---: | --- |
| local target | 24.3ms | 30.1ms | 30.3ms | 13.4ms | 7/7 |
| external edge | 30.8ms | 49.6ms | 50.6ms | 22.4ms | 7/7 |

Comparison points:

| Baseline | Before | Current | Reduction |
| --- | ---: | ---: | ---: |
| local full/off cold ranking p95 | 1185.5ms | 30.1ms | 97.5% |
| previous edge cold ranking p95 | 828.5ms | 49.6ms | 94.0% |
| earlier ALB cache-tail edge cold ranking p95 | 1893.9ms | 49.6ms | 97.4% |

Functional regression check:

- public `GET /api/policies/ranking?size=5`: `5` items
- public `GET /api/policies/ranking?size=20`: `20` items
- public `GET /api/policies/ranking?size=30`: `30` items
- public `GET /api/policies?page=0&size=20`: `20` items
- public `POST /api/policies/search`: `20` items
- runtime API smoke over `https://youthmoa.kr` passed signup, login, refresh, recommendation refresh, bookmark toggle, bookmark list, logout, refresh-after-logout, and revoked-token checks
- recommendation refresh returned `39` items in the smoke account
- app snapshot logs showed refresh complete entries and no snapshot read/refresh failure lines in the checked window

Smoke execution note:

- public `/actuator/health` correctly returned nginx `403`, so the runtime smoke was rerun with `APP_HEALTH_URL=http://127.0.0.1:8082/actuator/health`
- the first retry omitted `ENV_FILE`, so email verification seed tried the local Redis container; the accepted run used `ENV_FILE=.env.runtime.production` and seeded ElastiCache

Decision:

- Ranking snapshot remains accepted for the `size=20` ranking path.
- Continue using current ranking p95 from this checkpoint as the baseline before opening the next optimization.
- Broader post-snapshot API/DB/edge measurement selected `policy_list_default` cold/tail as the next bottleneck candidate.
- Ranking snapshot rollback is documented in [ranking-snapshot-rollback-runbook-2026-07-15.md](./ranking-snapshot-rollback-runbook-2026-07-15.md).

Initial accepted server baseline:

- generated_at_utc: `2026-05-29T15:21:23Z`
- git_head: `c91ec8b0851830245ea4370d7e51b533b1e916df`
- app_base_url: `http://127.0.0.1:8082`
- full_suite_artifact: `tmp/performance/full-suite/20260529T152123Z`
- wrapper_duration_artifact: `tmp/performance/wrapper-duration/20260529T152344Z`
- extended_suite_artifact: `tmp/performance/extended-suite/20260529T153645Z`
- stateful_flow_duration_artifact: `tmp/performance/stateful-flow-duration/20260529T153838Z`
- deep_observation_suite_artifact: `tmp/performance/deep-observation-suite/20260529T155025Z`
- sample profile: API `RUNS=7`, `WARMUP_RUNS=1`; DB `EXPLAIN (ANALYZE, BUFFERS)`; Redis `INFO` and `SLOWLOG GET 20`

| Area | Scenario | p50 | p95 | p99 | Max | Error Rate | Notes |
|---|---|---:|---:|---:|---:|---:|---|
| API | health | 16.3ms | 18.1ms | 18.7ms | 18.9ms | 0/7 | internal |
| API | policy list default | 54.8ms | 56.8ms | 57.3ms | 57.4ms | 0/7 | internal |
| API | policy search keyword | 458.1ms | 631.2ms | 677.7ms | 689.4ms | 0/7 | internal |
| API | policy search filtered | 465.0ms | 534.3ms | 536.9ms | 537.5ms | 0/7 | filters and deadline sort |
| API | policy suggestions | 277.6ms | 287.4ms | 290.7ms | 291.5ms | 0/7 | autocomplete |
| API | policy ranking | 389.2ms | 566.2ms | 573.0ms | 574.7ms | 0/7 | ranking endpoint |
| API | policy detail | 22.2ms | 27.0ms | 28.5ms | 28.9ms | 0/7 | first policy id `14916` |
| API | admin dashboard summary | 72.3ms | 74.6ms | 74.8ms | 74.8ms | 0/7 | admin token |
| API | admin collect failures | 19.5ms | 31.8ms | 33.9ms | 34.4ms | 0/7 | admin token |
| API | admin recommendation breakdowns | 71.6ms | 74.5ms | 75.0ms | 75.1ms | 0/7 | admin token |
| DB | policy list explain | N/A | N/A | N/A | 15.328ms | N/A | seq scan, execution time |
| DB | policy search keyword explain | N/A | N/A | N/A | 133.776ms | N/A | seq scan, execution time |
| DB | policy detail explain | N/A | N/A | N/A | 0.104ms | N/A | index scan, execution time |
| DB | recommendation logs recent window | N/A | N/A | N/A | 0.846ms | N/A | seq scan, execution time |
| DB | admin collect failures recent | N/A | N/A | N/A | 0.299ms | N/A | seq scan, execution time |
| Redis | keyspace hit rate | N/A | N/A | N/A | 49.631% | N/A | INFO stats |
| Redis | memory | N/A | N/A | N/A | 1.35M | N/A | used_memory_human |
| Redis | fragmentation ratio | N/A | N/A | N/A | 5.90 | N/A | INFO stats |
| Wrapper | recommendation observation | N/A | N/A | N/A | 5.882s | 0/1 | opt-in duration |
| Wrapper | current priority | N/A | N/A | N/A | 103.248s | 0/1 | opt-in duration |
| API Load | total | N/A | N/A | N/A | 3.881 rps | 0/39 | 10s, concurrency 3 |
| API Load | policy ranking | 1653.6ms | 1887.6ms | 1922.9ms | 1931.7ms | 0/6 | short load probe |
| API Load | policy search keyword | 1343.1ms | 1419.4ms | 1421.8ms | 1422.4ms | 0/6 | short load probe |
| API Load | policy search filtered | 1258.6ms | 1427.6ms | 1445.3ms | 1449.7ms | 0/6 | short load probe |
| Web | `/` | N/A | N/A | N/A | 632ms LCP | 0/1 | FCP 168ms, CLS 0.000107 |
| Web | `/policies` | N/A | N/A | N/A | 652ms LCP | 0/1 | FCP 192ms, CLS 0.001233 |
| Web | `/policies?keyword=청년` | N/A | N/A | N/A | 856ms LCP | 0/1 | FCP 340ms, CLS 0.000983 |
| Web | `/login` | N/A | N/A | N/A | 472ms LCP | 0/1 | FCP 140ms, CLS 0 |
| JVM | app container | N/A | N/A | N/A | 601.3MiB / 1GiB | N/A | CPU 0.31%, PIDs 46 |
| JVM | actuator metrics | N/A | N/A | N/A | 401 | N/A | metrics not exposed |
| Edge | `/` | N/A | N/A | N/A | 43.564ms | 0/1 | external curl total |
| Edge | `/api/policies` | N/A | N/A | N/A | 98.700ms | 0/1 | external curl total |
| Edge | `/api/policies/search` | N/A | N/A | N/A | 514.180ms | 0/1 | external curl total |
| Edge | nginx headers | N/A | N/A | N/A | passed | N/A | HSTS, frame, nosniff, CSP, server tokens |
| Stateful | runtime API smoke | N/A | N/A | N/A | 23.576s | 0/1 | auth-session disabled by default |
| DB Obs | database cache hit | N/A | N/A | N/A | 99.9997% | N/A | `pg_stat_database` |
| DB Obs | blocked locks | N/A | N/A | N/A | 0 | N/A | `pg_locks` |
| DB Obs | long transactions | N/A | N/A | N/A | 0 | N/A | >1s xact age |
| DB Obs | pg_stat_statements | N/A | N/A | N/A | unavailable | N/A | extension not available |
| DB Obs | temp files | N/A | N/A | N/A | 12 | N/A | 93,904,896 bytes |
| Redis Obs | dbsize | N/A | N/A | N/A | 65 keys | N/A | sampled all keys |
| Redis Obs | slowlog length | N/A | N/A | N/A | 1 | N/A | `SLOWLOG LEN` |
| Redis Obs | latency events | N/A | N/A | N/A | 0 lines | N/A | `LATENCY LATEST` |
| Redis Obs | top command | N/A | N/A | N/A | `ping` 34823 calls | N/A | commandstats |
| Web Interaction | policies search input | N/A | N/A | N/A | 652ms action | 0/1 | max event duration 64ms |
| Web Interaction | login form fill | N/A | N/A | N/A | 305ms action | 0/1 | max event duration 0ms |
| nginx Log | sampled lines | N/A | N/A | N/A | 4691 | N/A | current access.log tail |
| nginx Log | request_time fields | N/A | N/A | N/A | unavailable | N/A | log format has no request_time/upstream_response_time |
| nginx Log | 4xx sample | N/A | N/A | N/A | 690 | N/A | includes expected auth refresh 401s |

Data profile at this baseline:

- `welfare_services`: 14920 rows
- `raw_api_payloads`: 44398 rows
- `recommendation_logs`: 1017 rows
- `search_logs`: 127 rows
- `recent_policy_views`: 3 rows
- `chat_messages`: 0 rows

Known measurement gaps:

- `/actuator/metrics` returned `401`, so JVM/Hikari/GC metrics are not yet part of the accepted baseline.
- `interactionToNextPaint_ms` is still approximated through browser event timing in `web-interaction`; full INP needs the production Web Vitals library or Chrome trace processing.
- `RUN_AUTH_SESSION=true` was not accepted for the stateful baseline because the nested withdraw smoke needs valid elevated DB credentials in production.
- `pg_stat_statements` is not available, so DB top-query attribution is currently limited to representative `EXPLAIN` and catalog snapshots.
- nginx access logs do not include `$request_time` or `$upstream_response_time`, so log p95/p99 cannot be computed from logs yet.

## Comparison Notes

When an optimization is applied, add a row here with:

- before HEAD
- after HEAD
- expected effect
- observed delta
- rollback threshold
- follow-up action

| Date | Before HEAD | After HEAD | Area | Change | Before | After | Delta | Decision |
|---|---|---|---|---|---:|---:|---:|---|
| TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD |

## 2026-05-30 Server Refresh Highlights

The next accepted server refresh was captured on `HEAD=a57dcf7510b47537c95d90a76b15131ebfca0520`.

- `performance_baseline_suite=passed`
- `performance_extended_suite=passed`
- `stateful_flow_duration_baseline=passed`
- `health=UP`
- recent app log `ERROR/Exception/Caused by 없음`

Notable measured values from that server run:

| Area | Scenario | Value | Notes |
|---|---|---:|---|
| API | policy ranking p95 | 870.6ms | hotspot candidate |
| API | policy ranking p99 | 886.7ms | hotspot candidate |
| API | policy ranking max | 890.8ms | hotspot candidate |
| API | policy search keyword p95 | 646.5ms | hotspot candidate |
| API | policy search keyword p99 | 710.9ms | hotspot candidate |
| API | policy search keyword max | 727.0ms | hotspot candidate |
| DB | policy_search_keyword_ilike explain | 125.734ms | seq scan |
| Wrapper | recommendation observation | 5.956s | server wrapper duration |
| Wrapper | current priority | 154.715s | server wrapper duration |
| API Load | total throughput | 3.951 rps | 10s, concurrency 3 |
| API Load | policy ranking p95 | 2034.4ms | load hotspot |
| API Load | policy ranking p99 | 2064.9ms | load hotspot |
| API Load | policy ranking max | 2072.5ms | load hotspot |
| Edge | `/api/policies/search` total | 505.675ms | external curl total |
| Web | `/` LCP | 620ms | deployed origin |
| Web | `/policies` LCP | 636ms | deployed origin |
| Web | `/login` LCP | 480ms | deployed origin |

Operational note from the same run:

- the first `RUN_WRAPPER_BASELINE=true` execution failed because nested smoke wrappers inherited the parent `ARTIFACT_DIR` and cleanup removed `wrapper-durations.tsv`
- rerun with `KEEP_ARTIFACTS=true` succeeded
- this wrapper artifact isolation bug is the first optimization item in [performance-optimization-log.md](./performance-optimization-log.md)

## 2026-05-30 Accepted Optimization Rerun

The next accepted server rerun was captured on `HEAD=487bf64b75d9a20e606ebd89f10bcb0dc9b23db1`.

- app redeploy completed
- `health=UP`
- `performance_baseline_suite=passed`
- `performance_extended_suite=passed`
- `stateful_flow_duration_baseline=passed`
- recent log `ERROR/Exception/Caused by 없음` after the collect-lock window

Important run context:

- the first wrapper-inclusive run no longer hit artifact cleanup failure
- it did fail once because the `17:00 UTC` collect schedule held the collect-global lock and `current_priority` hit `409/COL002`
- after the lock cleared, the same wrapper-inclusive baseline passed without `KEEP_ARTIFACTS=true`

Accepted rerun values:

| Area | Scenario | Value | Notes |
|---|---|---:|---|
| API | policy ranking p95 | 999.4ms | still hotspot |
| API | policy ranking p99 | 1008.5ms | still hotspot |
| API | policy ranking max | 1010.8ms | still hotspot |
| API | policy search keyword p95 | 338.6ms | improved |
| API | policy search keyword p99 | 347.7ms | improved |
| API | policy search keyword max | 349.9ms | improved |
| DB | policy_search_keyword_ilike explain | 133.042ms | still seq scan |
| Wrapper | recommendation observation | 7.502s | slower than previous baseline |
| Wrapper | current priority | 105.535s | improved |
| API Load | policy ranking p95 | 2244.0ms | still hotspot |
| API Load | policy ranking max | 2251.8ms | still hotspot |

## 2026-05-30 Second Optimization Rerun

The next accepted server rerun was captured on `HEAD=909bb4d682f9eee5cd6f09556dc584fe3b3696d4`.

- runtime DB index SQL applied with the RDS master account
- app redeploy completed
- `health=UP`
- `performance_baseline_suite=passed`
- `performance_extended_suite=passed`
- `ERROR/Exception/COL002 없음` after redeploy at `2026-05-29T17:21:00Z`

Important run context:

- wrapper baseline no longer needed `KEEP_ARTIFACTS=true`
- `DB_MIGRATION_USERNAME` could not apply the new indexes because it lacked table-owner privileges, so the runtime index SQL was applied with the RDS master account instead

Accepted rerun values:

| Area | Scenario | Value | Notes |
|---|---|---:|---|
| API | policy ranking p95 | 885.1ms | improved from first accepted rerun |
| API | policy ranking p99 | 887.0ms | improved |
| API | policy ranking max | 887.4ms | improved |
| API | policy search keyword p95 | 628.7ms | regressed |
| API | policy search keyword p99 | 650.7ms | regressed |
| API | policy search keyword max | 656.2ms | regressed |
| DB | policy_search_keyword_ilike explain | 127.449ms | still seq scan |
| Wrapper | recommendation observation | 6.231s | improved |
| Wrapper | current priority | 208.540s | regressed; step-level breakdown needed |
| API Load | policy ranking p95 | 2410.1ms | still hotspot |
| API Load | policy ranking max | 2421.0ms | still hotspot |
| API Load | policy search keyword p95 | 1662.2ms | hotspot under load |
| API Load | policy search keyword max | 1667.0ms | hotspot under load |

Interpretation from this rerun:

- the `policy_ranking` unique-view aggregation/index batch helped
- the search path still needs a query-shape follow-up; the representative explain stayed on a seq scan and API latency regressed
- `current_priority` now needs nested duration breakdown, not just a top-level wrapper total

## 2026-05-30 Third Optimization Rerun

The next accepted server rerun was captured on `HEAD=f351ee32466ce9aed73c7d755e38ab6bcac2db03`.

- app redeploy completed
- `health=UP`
- baseline, wrapper baseline, and extended suites passed
- recent app log `ERROR/Exception 없음`

Important run context:

- wrapper-inclusive rerun now publishes nested duration fields directly in `current_priority` summary/json
- `active_baseline` summary/json now exposes backend, ops, and collect step durations, but frontend is still one level short of full sub-step parity in the published field names

Accepted rerun values:

| Area | Scenario | Value | Notes |
|---|---|---:|---|
| API | policy ranking p95 | 933.3ms | wrapper-inclusive baseline |
| API | policy ranking p99 | 935.3ms | wrapper-inclusive baseline |
| API | policy ranking max | 935.7ms | wrapper-inclusive baseline |
| API | policy ranking p95 (baseline) | 1088.2ms | baseline-only run |
| API | policy ranking p99 (baseline) | 1103.0ms | baseline-only run |
| API | policy ranking max (baseline) | 1106.7ms | baseline-only run |
| API | policy search keyword p95 | 417.1ms | wrapper-inclusive baseline |
| API | policy search keyword p99 | 432.1ms | wrapper-inclusive baseline |
| API | policy search keyword max | 435.9ms | wrapper-inclusive baseline |
| API | policy search keyword p95 (baseline) | 334.6ms | baseline-only run |
| API | policy search keyword p99 (baseline) | 336.7ms | baseline-only run |
| API | policy search keyword max (baseline) | 337.2ms | baseline-only run |
| DB | policy_search_keyword_ilike explain | 258.805ms | still seq scan, wrapper-inclusive baseline |
| Wrapper | recommendation observation | 5.858s | improved |
| Wrapper | current priority | 194.844s | majority is active baseline |
| Wrapper | current priority -> active baseline | 188.817s | nested step total now visible |
| Wrapper | current priority -> recommendation observation | 5.895s | nested step total now visible |
| API Load | policy ranking p95 | 2410.1ms | still hotspot |
| API Load | policy ranking max | 2421.0ms | still hotspot |
| API Load | policy search keyword p95 | 1662.2ms | hotspot under load |
| API Load | policy search keyword max | 1667.0ms | hotspot under load |

Interpretation from this rerun:

- search API latency recovered again after the query-shape cleanup
- ranking is still the primary unresolved endpoint hotspot
- the representative lowered `LIKE` explain stayed on a seq scan and got slower, so the benchmark query is still not closed
- `current_priority` is now explainable: most of the total comes from `active_baseline`, not `recommendation_observation`

## 2026-05-30 Fourth Optimization Rerun

The next accepted server rerun was captured on `HEAD=10e3bea61cf1bc7812d4264e3a51ed5072f79c66`.

- first boot on the untouched merge commit failed because `PolicyRankingService` constructor selection was ambiguous
- the server applied a one-line hotfix by marking the 2-arg constructor with `@Autowired`, then rebuilt and continued the rerun
- after that hotfix, baseline, wrapper baseline, and extended suites all passed
- `health=UP`
- recent app log `ERROR/Exception 없음` after the successful redeploy

Accepted rerun values:

| Area | Scenario | Value | Notes |
|---|---|---:|---|
| API | policy ranking p95 | 5.8ms | wrapper-inclusive baseline |
| API | policy ranking p99 | 6.0ms | wrapper-inclusive baseline |
| API | policy ranking max | 6.0ms | wrapper-inclusive baseline |
| API | policy ranking p95 (baseline) | 8.5ms | baseline-only run |
| API | policy ranking p99 (baseline) | 8.5ms | baseline-only run |
| API | policy ranking max (baseline) | 8.5ms | baseline-only run |
| API | policy search keyword p95 | 426.7ms | wrapper-inclusive baseline |
| API | policy search keyword p99 | 464.3ms | wrapper-inclusive baseline |
| API | policy search keyword max | 473.7ms | wrapper-inclusive baseline |
| API | policy search keyword p95 (baseline) | 402.1ms | baseline-only run |
| API | policy search keyword p99 (baseline) | 418.6ms | baseline-only run |
| API | policy search keyword max (baseline) | 422.7ms | baseline-only run |
| DB | policy_search_keyword_ilike explain | 117.078ms | still seq scan |
| Wrapper | recommendation observation | 5.879s | stable |
| Wrapper | current priority | 156.841s | improved |
| Wrapper | current priority -> active baseline | 150.881s | nested step total visible |
| Wrapper | current priority -> recommendation observation | 5.828s | nested step total visible |
| Wrapper | active baseline -> backend tests | 61.941s | snake_case key exposed |
| Wrapper | active baseline -> frontend lint | 8.446s | snake_case key exposed |
| Wrapper | active baseline -> frontend build | 1.492s | snake_case key exposed |
| Wrapper | active baseline -> frontend e2e | 59.436s | snake_case key exposed |
| Wrapper | active baseline -> ops baseline | 3.056s | snake_case key exposed |
| Wrapper | active baseline -> collect legacy repair | 16.416s | snake_case key exposed |

Interpretation from this rerun:

- the ranking TTL micro-cache is accepted; `/api/policies/ranking` dropped from a ~900ms hotspot to single-digit milliseconds under repeated baseline sampling
- `current_priority` improved materially and is now directly attributable to backend tests + frontend e2e rather than opaque wrapper time
- `policy_search_keyword` remains acceptable at the API level but the representative lowered `LIKE` explain still does not close
- the constructor injection bug must be reflected back into the repository because the server had to hotfix it manually

Current benchmark caveat after this rerun:

- the `policy_search_keyword_ilike` representative explain is now treated as historical only
- next rerun should compare against `policy_search_keyword_api_shape`, not the old multi-long-text lowered `LIKE` fallback
- reason: the accepted server rerun proved the API path itself is acceptable, while the remaining seq scan came from a benchmark query that no longer matched the current repository contract and also missed runtime `title/keyword` trigram migration coverage
