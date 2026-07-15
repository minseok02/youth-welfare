# Policy List Projection Query Optimization 2026-07-15

## Purpose

The policy list timing checkpoint showed that warm repeated list latency is dominated by projection lookup inside `PolicyPresentationReadService`.

This document records the plan before changing code, the measured SQL reason, and the validation rules for the optimization.

## Before State

- previous tracking document: [policy-list-timing-instrumentation-2026-07-15.md](./policy-list-timing-instrumentation-2026-07-15.md)
- code before change: `ab5859e16771d9689339fa1be84fb0f2453c3b39`
- endpoint under test: `GET /api/policies?page=0&size=20`
- relevant code path:
  - `PolicyPresentationReadService.findProjections`
  - `RecommendationProjectionReadService.findCandidateProjectionsByServices`
  - `CanonicalRecommendationReadModelRepository.findByServiceIds`
  - `CanonicalRecommendationReadModelRepository.baseRows`

The service layer already batches the 20 list policies into one projection call. The issue is not an N+1 call from the list path.

## Root Cause Candidate

`baseRows()` reads `service_taxonomy_summary_slots` through six slot-specific subqueries:

- `YOUTH_MAJOR`
- `YOUTH_MID`
- `PROVISION_METHOD`
- `GOV24_SERVICE_FIELD`
- `GOV24_USER_TYPE`
- `GOV24_BENEFIT_TYPE`

Each subquery aggregates the full table by `service_id`, then joins the result to the 20 requested policies. This means a page of 20 policies can still scan and aggregate tens of thousands of summary slot rows.

The existing index `idx_stss_service_slot(service_id, slot_key)` is more useful if the query first constrains `service_id IN (:serviceIds)`.

## Before SQL Measurement

The first page policy ids used for the comparison:

```text
15391,3925,3843,3775,3769,3729,3692,3564,3552,3542,3312,3251,3202,3058,3050,3008,2987,15389,15390,15351
```

Representative `EXPLAIN (ANALYZE, BUFFERS)` result:

| Query | Execution time | Interpretation |
| --- | ---: | --- |
| current `baseRows()` | 50.649ms | six full slot-key aggregates dominate |
| filtered single summary-slot aggregate candidate | 0.340ms | uses requested `service_id` set first |
| `taxonomyTermRows()` | 0.307ms | not the bottleneck |
| `factRows()` | 0.141ms | not the bottleneck |

## Planned Change

Replace only the `summarySlotTableReady()` branch of `baseRows()`.

Current shape:

```sql
LEFT JOIN (
  SELECT service_id, MAX(slot_label) AS slot_label
  FROM service_taxonomy_summary_slots
  WHERE slot_key = '...'
  GROUP BY service_id
) slot_alias ON slot_alias.service_id = ws.id
```

New shape:

```sql
WITH summary_slots AS (
  SELECT service_id,
         MAX(slot_label) FILTER (WHERE slot_key = 'YOUTH_MAJOR') AS youth_major_label,
         MAX(slot_label) FILTER (WHERE slot_key = 'YOUTH_MID') AS youth_mid_label,
         MAX(slot_label) FILTER (WHERE slot_key = 'PROVISION_METHOD') AS provision_method_label,
         MAX(slot_label) FILTER (WHERE slot_key = 'GOV24_SERVICE_FIELD') AS gov24_service_field_label,
         MAX(slot_label) FILTER (WHERE slot_key = 'GOV24_USER_TYPE') AS gov24_user_type_label,
         MAX(slot_label) FILTER (WHERE slot_key = 'GOV24_BENEFIT_TYPE') AS gov24_benefit_type_label
  FROM service_taxonomy_summary_slots
  WHERE service_id IN (:serviceIds)
    AND slot_key IN (...)
  GROUP BY service_id
)
```

This preserves the response columns and fallback order:

1. summary slot label
2. legacy `service_taxonomies` label
3. `welfare_services.apply_method_name` for provision method only

## Expected Impact

| Area | Expected |
| --- | ---: |
| list response shape | no change |
| policy ordering | no change |
| projection label values | no change |
| DB schema/indexes | no change |
| `baseRows()` SQL execution | about 50ms -> under 2ms for the measured page |
| warm list endpoint | about 30ms to 90ms lower if projection remains the dominant cost |

The endpoint p95 may not drop by the same amount if ALB/nginx/network, serialization, first-tail rows query, or rate-limit timing becomes dominant after this change.

## Validation Plan

Before deploy:

1. Run focused unit tests around projection and policy presentation.
2. Run policy list/search/ranking API tests that cover public response shape.
3. Compare the same 20 first-page policy responses before and after if the local server is rebuilt.

After deploy:

1. Confirm both ALB targets are healthy.
2. Re-run 12 samples for edge, primary loopback, and secondary loopback.
3. Compare `[PolicyListPresentationTiming] projectionMs` against the before range of `47ms` to `114ms`.
4. Smoke root, policy list, policy ranking, and keyword search.
5. Record the result in this document and `performance-optimization-log.md`.

## Rollback

Rollback is a normal code rollback to the previous commit.

Because this change does not alter schema, env vars, Redis keys, or cache state, rollback only requires redeploying the previous application image/commit if response values or latency regress.

## Implementation

- implementation commit: `65f317e344df54ed3e5f1d8597e7112ec20d3460`
- changed file: `backend/src/main/java/com/example/welfare/recommend/repository/CanonicalRecommendationReadModelRepository.java`
- deployed to primary: `i-0b8d95e454df5e0f0`
- deployed to secondary: `i-0e8a4cc599c1148c8`
- ALB target health after deploy: both targets healthy

The implementation replaced the six summary-slot aggregate subqueries with one `summary_slots` CTE that first filters by `service_id IN (:serviceIds)`.

No schema, index, Redis, env var, or response contract change was made.

## Pre-Deploy Validation

Focused tests:

```bash
./gradlew test \
  --tests com.example.welfare.recommend.service.RecommendationProjectionReadServiceTest \
  --tests com.example.welfare.policy.service.PolicyPresentationReadServiceTest \
  --tests com.example.welfare.policy.service.PolicyListServiceTest \
  --tests com.example.welfare.api.RecommendationPolicyFlowWebMvcTest \
  --tests com.example.welfare.api.PolicySearchKeywordApiWebMvcTest \
  --no-daemon
```

Result: passed.

SQL equivalence on the first page ids:

| Direction | Difference count |
| --- | ---: |
| old minus new | 0 |
| new minus old | 0 |

New query execution after implementation check:

| Query | Execution time |
| --- | ---: |
| new `baseRows()` | 0.301ms |

## Post-Deploy Measurements

Artifacts:

- `tmp/performance/policy-list-projection-query-optimization-20260715/primary-after-samples.txt`
- `tmp/performance/policy-list-projection-query-optimization-20260715/edge-after-samples.txt`
- `tmp/performance/policy-list-projection-query-optimization-20260715/primary-warm-after-samples.txt`
- `tmp/performance/policy-list-projection-query-optimization-20260715/edge-warm-after-samples.txt`
- secondary samples were collected through SSM and stored on the secondary node under the same artifact directory

Immediate post-deploy samples:

| Path | Samples | p50 | p95 | Max | Notes |
| --- | ---: | ---: | ---: | ---: | --- |
| primary loopback | 12 | 57.2ms | 381.1ms | 381.1ms | first request after primary restart was the tail |
| secondary loopback | 12 | 60.2ms | 130.8ms | 130.8ms | first requests after secondary restart still included cold row/region/projection cost |
| edge | 12 | 65.0ms | 695.5ms | 695.5ms | one sample hit secondary shortly after restart and carried deployment cold cost |

Warm stability samples after both nodes were up and warmed:

| Path | Samples | p50 | p95 | Max |
| --- | ---: | ---: | ---: | ---: |
| primary loopback | 12 | 42.4ms | 108.0ms | 108.0ms |
| secondary loopback | 12 | 35.7ms | 48.2ms | 48.2ms |
| edge | 12 | 46.4ms | 165.5ms | 165.5ms |

Warm stability excluding only the first sample of each warm batch:

| Path | Samples | p50 | p95 | Max |
| --- | ---: | ---: | ---: | ---: |
| primary loopback | 11 | 42.4ms | 67.8ms | 67.8ms |
| secondary loopback | 11 | 35.7ms | 48.2ms | 48.2ms |
| edge | 11 | 46.4ms | 74.5ms | 74.5ms |

## Observed Delta

Compared with the timing-instrumentation checkpoint:

| Path | Before p50 | After warm p50 | p50 reduction | Before p95 | After warm p95 | p95 reduction |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| primary loopback | 110.1ms | 42.4ms | 61.5% | 197.3ms | 108.0ms | 45.3% |
| secondary loopback | 104.4ms | 35.7ms | 65.8% | 171.7ms | 48.2ms | 71.9% |
| edge | 121.2ms | 46.4ms | 61.7% | 728.7ms | 165.5ms | 77.3% |

If the first sample of the warm stability batch is excluded, p95 reduction is:

| Path | Before p95 | After warm p95 excluding first sample | p95 reduction |
| --- | ---: | ---: | ---: |
| primary loopback | 197.3ms | 67.8ms | 65.6% |
| secondary loopback | 171.7ms | 48.2ms | 71.9% |
| edge | 728.7ms | 74.5ms | 89.8% |

## Log Interpretation

Primary after deploy:

- first measured request: repository `118ms`, presentation `49ms`, service `188ms`, controller `208ms`
- later warm request: controller `119ms`
- projection slow log mostly disappeared because presentation stayed below the `50ms` slow threshold

Secondary after deploy:

- first measured request: repository `171ms`, presentation `124ms`, projection `78ms`, region `42ms`, rate limit `65ms`, controller `393ms`
- later warm request: presentation `57ms`, projection `46ms`, controller `100ms`

Interpretation:

- The intended projection query improvement worked.
- The old repeated projection range was `47ms` to `114ms`; after the change, warm projection either falls below the slow log threshold or logs around `46ms`.
- The remaining deployment/first-request tail is now mixed across repository rows, region label warmup, rate-limit timing, and edge/target timing.
- Do not open another projection SQL change immediately. Re-check with a broader baseline first.

## Smoke

Final public smoke after both nodes were deployed:

| Endpoint | Status | Total |
| --- | ---: | ---: |
| `/` | 200 | 28.536ms |
| `/api/policies?page=0&size=20` | 200 | 86.960ms |
| `/api/policies/ranking?size=20` | 200 | 95.622ms |
| `/api/policies/search` keyword `청년` | 200 | 434.216ms |

ALB target health: both targets healthy.

## Decision

Accept the change.

The measured improvement is large enough, the SQL equivalence check passed, focused tests passed, public smoke passed, and there is no schema or cache rollback burden.

Next recommended action is not another immediate query change. Run a broader baseline and inspect whether the remaining list tail is still policy-list-specific or has shifted to first-request warmup, rate limiting, region labels, or edge behavior.
