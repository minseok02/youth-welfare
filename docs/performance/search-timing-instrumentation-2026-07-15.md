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

Production deploy:

- Commit: `909c33d489009b869df45aa4a27bae12b9e661ee`
- Primary: `i-0b8d95e454df5e0f0`, Docker health `healthy`, `/actuator/health` `UP`.
- Secondary: `i-0e8a4cc599c1148c8`, Docker health `healthy`, `/actuator/health` `UP`.
- ALB target group: both targets `healthy`.

Production samples:

| Path | Node | Request | Client total | Result | Main timing observation |
| --- | --- | --- | ---: | --- | --- |
| local | primary | `청년`, default relevance | `615.955ms` | `200`, `1476` total, `20` rows | repository `149ms`, summary `89ms`, cache write `49ms`, search log `58ms` |
| local | primary | `청년`, default relevance, immediate repeat | `40.189ms` | `200`, `1476` total, `20` rows | local/Redis cache path; no repository work |
| local | primary | `청년`, `ACTIVE_ONLY`, `DEADLINE` | `268.384ms` | `200`, `1476` total, `20` rows | repository `188ms`; SQL `174ms` |
| local | primary | `청년`, `ACTIVE_ONLY`, `DEADLINE`, immediate repeat | `38.046ms` | `200`, `1476` total, `20` rows | local/Redis cache path; no repository work |
| local | secondary | `청년`, default relevance | `491.030ms` | `200`, `1476` total, `20` rows | Redis/cache path after primary write; controller rate-limit/search-log overhead was visible |
| local | secondary | `청년`, `ACTIVE_ONLY`, `DEADLINE` | `45.994ms` | `200`, `1476` total, `20` rows | Redis/cache path after primary write |
| local | secondary | `월세`, `ACTIVE_ONLY`, `DEADLINE` | `656.429ms` | `200`, `83` total, `20` rows | repository `425ms`; SQL `229ms`, entity load `171ms`, summary `136ms` |
| edge | ALB/primary | `창업`, default relevance | `297.025ms` | `200`, `169` total, `20` rows | repository `96ms`; SQL `68ms`, summary `63ms` |
| edge | ALB/secondary | `창업`, `ACTIVE_ONLY`, `DEADLINE` | `454.171ms` | `200`, `169` total, `20` rows | repository `300ms`; SQL `271ms`, summary `38ms` |

Post-deploy error check:

- No 5xx search responses were observed in app request logs.
- No new application exception was observed in the sampled window.
- The startup Redis repository assignment message is an existing Spring Data informational message and is not tied to this change.

## Decision

The instrumentation is active and shows that cache hits are fast, while cache misses still spend most time in the repository path. The strongest next candidate is the `general_filtered` search SQL used by filtered/deadline searches, followed by occasional ordered entity loading and summary enrichment cost. Do not tune cache TTL first; the slow path still matters for new keywords and expired cache entries.
