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
