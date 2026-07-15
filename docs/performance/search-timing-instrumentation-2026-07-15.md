# Search Timing Instrumentation 2026-07-15

## Purpose

The post-projection low-rate soak moved the next performance watch item from policy list to search:

- primary `policy_search_filtered` p95 `210.9ms`
- secondary `policy_search_filtered` p95 `277.6ms`
- primary `policy_search_keyword` p95 `117.9ms`
- secondary `policy_search_keyword` p95 `130.9ms`

This checkpoint adds timing instrumentation before changing search behavior.

## Current Hypothesis

The current search path has multiple possible cost centers:

- controller fingerprint/rate-limit/search-log overhead
- public search cache hit/miss behavior
- repository SQL id lookup
- loading ordered `WelfareService` entities by matched ids
- `PolicyPresentationReadService` summary enrichment
- filtered/general SQL path versus default relevance fast path

The search repository already has two main shapes:

- default first-page relevance search uses a `matched_ids AS MATERIALIZED` union fast path
- filtered searches use the general query builder

The load/soak result suggests the filtered path needs special attention, but this should be confirmed with live app timings first.

## Plan

Add low-cardinality timing logs only:

1. `PolicyController.search`
   - client fingerprint time
   - rate-limit time
   - service search time
   - search log write time
   - controller total
2. `PolicySearchService.search`
   - normalization/cache lookup
   - repository time
   - presentation/visible-summary collection time
   - response build
   - cache write
   - cache source: local, Redis, miss, authenticated
3. `WelfareServiceSearchRepositoryImpl.search`
   - path: default fast path or general
   - SQL id query time
   - ordered entity load time
   - discovery balance time
   - row count and total count

## Expected Impact

| Area | Expected |
| --- | ---: |
| response shape | no change |
| SQL shape | no change |
| cache key/value | no change |
| search latency | about 0ms to +2ms |
| log volume | only slow/observed search requests |

## Validation Plan

Before deploy:

1. Run focused search service/repository tests.
2. Run search API WebMvc tests.

After deploy:

1. Run keyword and filtered search samples on primary, secondary, and edge.
2. Compare `[PolicySearch*Timing]` logs for slow samples.
3. Confirm no `ERROR`, `Exception`, `5xx`, or unexpected `429`.
4. Keep ALB targets healthy.

## Results

- Implemented controller timing log: `[PolicySearchControllerTiming]`.
- Implemented service timing log: `[PolicySearchServiceTiming]`.
- Implemented repository timing log: `[PolicySearchRepositoryTiming]`.
- Code intentionally does not change response shape, SQL shape, cache key/value, or ranking behavior.
- Pre-deploy focused tests passed:
  - `PolicySearchServiceTest`
  - `WelfareServiceSearchRepositoryImplTest`
  - `PolicySearchKeywordApiWebMvcTest`
  - `PolicySearchKeywordReadServiceTest`

Production sample results are pending deploy.

## Decision

Deploy this as observation-only instrumentation first. Do not tune search SQL, cache TTL, or candidate reduction until live logs identify whether the dominant cost is controller/search-log overhead, cache misses, repository SQL, entity loading, or presentation enrichment.
