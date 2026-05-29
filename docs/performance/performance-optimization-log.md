# Performance Optimization Log

Last updated: 2026-05-30

This document records what was optimized, which baseline numbers triggered the work, and which server remeasurement is still pending.

## Current Active Batch

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

Server remeasurement status:

- pending
- use the same performance commands from [performance-baseline-current.md](./performance-baseline-current.md)
- accepted result should update this section with before/after deltas

## Comparison Table

| Date | Before HEAD | After HEAD | Area | Change | Before | After | Delta | Decision |
|---|---|---|---|---|---:|---:|---:|---|
| 2026-05-30 | `a57dcf7510b47537c95d90a76b15131ebfca0520` | pending | Wrapper | child artifact isolation | first run failed | pending | pending | server remeasure needed |
| 2026-05-30 | `a57dcf7510b47537c95d90a76b15131ebfca0520` | pending | Search | single-pass keyword page query | `p95 646.5ms` | pending | pending | server remeasure needed |
| 2026-05-30 | `a57dcf7510b47537c95d90a76b15131ebfca0520` | pending | Ranking | snapshot-based scoring + no full-entity preload | `p95 870.6ms` | pending | pending | server remeasure needed |
