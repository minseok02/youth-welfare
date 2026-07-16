# Search Summary Projection Optimization 2026-07-16

## Purpose

After generated search fields reduced filtered/deadline SQL time, the remaining miss-path cost moved to presentation summary enrichment.

Observed post-deploy examples on primary:

| Request | Repository | SQL | Summary | Projection | Region |
| --- | ---: | ---: | ---: | ---: | ---: |
| `전세 ACTIVE_ONLY DEADLINE` | `63ms` | `36ms` | `113ms` | `89ms` | `14ms` |
| `취업 ACTIVE_ONLY DEADLINE` | `68ms` | `34ms` | `110ms` | `93ms` | `8ms` |
| `교육 ACTIVE_ONLY DEADLINE` | `63ms` | `34ms` | `66ms` | `52ms` | `9ms` |
| `의료 ACTIVE_ONLY DEADLINE` | `62ms` | `35ms` | `112ms` | `97ms` | `8ms` |
| `문화 ACTIVE_ONLY DEADLINE` | `51ms` | `36ms` | `58ms` | `49ms` | `6ms` |

## Finding

`PolicyPresentationReadService` currently calls:

- `RecommendationProjectionReadService.findCandidateProjectionsByServices`
- which delegates to `CanonicalRecommendationReadModelRepository.findByServiceIds`

That repository builds a full recommendation candidate projection:

- base sidecar labels
- taxonomy terms
- facts
- priority buckets
- audience relevance bonus
- recommendation heuristic fields

The public policy summary response only needs a subset:

- description/summary fallback
- unified category fallback
- youth major/mid labels
- provision method label
- youth employment/education/special labels
- youth marital/income labels
- Gov24 service/user/benefit labels

Production `EXPLAIN (ANALYZE, BUFFERS)` for the underlying DB reads on 20 `월세` result IDs showed:

| Query | Execution time |
| --- | ---: |
| base rows | `0.627ms` |
| taxonomy terms | `0.208ms` |
| service facts | `0.450ms` |
| region labels | `0.215ms` |

So the repeated `projectionMs` `49ms`-`97ms` is not explained by PostgreSQL execution. The next likely cost is full Java projection assembly and recommendation-specific heuristic work in a hot public search path.

## Plan

1. Add a summary-focused projection read method.
2. Preserve response fields used by `PolicySummaryResponse`.
3. Do not alter recommendation ranking/candidate projection behavior.
4. Keep detail/recommendation callers on the existing full projection path unless they explicitly use summary presentation.
5. Compare summary response equivalence in tests and live timings after deploy.

## Expected Impact

| Area | Expected |
| --- | ---: |
| search/list response shape | no change |
| recommendation ranking behavior | no change |
| public search summary projection cost | lower |
| DB schema/index | no change |
| risk | moderate; summary labels must remain equivalent |

## Decision

Proceed with a summary-only projection path in presentation reads, then measure whether `projectionMs` drops enough before touching region labels, entity load, cache writes, or search-log writes.

## Implementation

Changed the public list/search summary path only:

- `PolicyPresentationReadService.findProjections(...)`
  - before: `RecommendationProjectionReadService.findCandidateProjectionsByServices(...)`
  - after: `RecommendationProjectionReadService.findSummaryProjectionsByServices(...)`
- `RecommendationProjectionReadService`
  - added `findSummaryProjectionsByServices(...)`
- `RecommendationSummaryReadRepository`
  - added `findSummaryProjections(...)`
- `CanonicalRecommendationReadModelRepository`
  - added `findSummaryByServiceIds(...)`
  - keeps existing `findByServiceIds(...)` as the full recommendation projection path
  - narrows summary taxonomy rows to:
    - `GOV24_SERVICE_FIELD`
    - `GOV24_USER_TYPE_TOKEN`
    - `GOV24_BENEFIT_TYPE_TOKEN`
  - narrows summary fact rows to:
    - `YOUTH_EMPLOYMENT_REQUIREMENT`
    - `YOUTH_EDUCATION_REQUIREMENT`
    - `YOUTH_SPECIAL_REQUIREMENT`
    - `YOUTH_MARITAL_STATUS`
    - `YOUTH_INCOME_CONDITION_TYPE`
  - builds `toSummaryProjection()` without audience bonus, priority buckets, target-group buckets, special-target scans, or full fact-key output

Unchanged paths:

- recommendation ranking/candidate reads still use `findByServiceIds(...)`
- policy detail presentation still uses `findCandidateProjectionsByServices(...)`
- DB schema and indexes were not changed

## Pre-Deploy Verification

Focused test suite:

```bash
cd backend
./gradlew test --tests com.example.welfare.policy.service.PolicyPresentationReadServiceTest \
  --tests com.example.welfare.recommend.repository.RecommendationSummaryReadRepositoryImplTest \
  --tests com.example.welfare.recommend.service.RecommendationProjectionReadServiceTest \
  --tests com.example.welfare.recommend.repository.CanonicalRecommendationReadModelRepositoryTest \
  --tests com.example.welfare.policy.service.PolicySearchServiceTest \
  --tests com.example.welfare.api.PolicySearchKeywordApiWebMvcTest \
  --no-daemon
```

Result:

- `BUILD SUCCESSFUL`
- `PolicyPresentationReadServiceTest` verifies summary pages now use the summary projection path while detail presentation keeps the full projection path.
- `CanonicalRecommendationReadModelRepositoryTest` verifies summary projection preserves card response labels and leaves recommendation scoring fields empty.

## Post-Deploy Measurement

Pending deployment and live ALB-node measurement.
