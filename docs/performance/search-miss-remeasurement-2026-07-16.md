# Search Miss Remeasurement 2026-07-16

## Purpose

After the summary-only projection deployment, rerun a small controlled fresh-miss measurement before opening another optimization batch.

The goal is to decide whether to immediately implement summary projection round-trip reduction or stop and observe.

## Context

Current deployed code:

- app/code commit: `3806ecddd9f275b3e251d51d5a8f8d9ec592ae47`
- docs/result commit: `28a2f6e924044092775c8425220e0a9fbf12840b`
- ALB target group: `youth-welfare-web-tg`
- targets:
  - primary `i-0b8d95e454df5e0f0`
  - secondary `i-0e8a4cc599c1148c8`

Request shape:

```http
POST /api/policies/search
Content-Type: application/json

{
  "keyword": "<fresh keyword>",
  "statusFilter": "ACTIVE_ONLY",
  "sort": "DEADLINE",
  "page": 0,
  "size": 20
}
```

All target-specific runs used different keyword sets to avoid the 30-second public search cache.

## Client Results

| Target | Samples | p50 | p90 | p95 | max | avg | Notes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| primary loopback | 20 | `84.9ms` | `119.8ms` | `139.9ms` | `159.2ms` | `93.7ms` | all 20 samples returned 20 results |
| secondary loopback | 20 | `85.6ms` | `108.4ms` | `117.8ms` | `127.5ms` | `88.1ms` | 16 non-empty samples, 5 full 20-result samples |
| public ALB | 20 | `120.2ms` | `137.4ms` | `144.0ms` | `220.0ms` | `126.0ms` | 19 non-empty samples, 8 full 20-result samples |

Worst client samples:

| Target | Keyword | Time | Total | Count |
| --- | --- | ---: | ---: | ---: |
| primary | `청년 창업` | `159.2ms` | 140 | 20 |
| primary | `청년 주거` | `139.9ms` | 207 | 20 |
| primary | `청년 취업` | `119.8ms` | 200 | 20 |
| secondary | `복지 일자리` | `127.5ms` | 50 | 20 |
| secondary | `복지 대출` | `117.8ms` | 18 | 18 |
| secondary | `복지 주거` | `108.4ms` | 78 | 20 |
| ALB | `대상 자립` | `220.0ms` | 8 | 8 |
| ALB | `대상 취업` | `144.0ms` | 20 | 20 |
| ALB | `대상 월세` | `137.4ms` | 13 | 13 |

## Slow Log Frequency

The app only emits detailed timing logs above internal thresholds, so absence of a line means the segment stayed below that threshold.

| Window | Lines | Repository timing | Presentation timing | Service timing | Controller timing |
| --- | ---: | ---: | ---: | ---: | ---: |
| primary loopback | 16 | 4 | 0 | 3 | 9 |
| secondary loopback | 17 | 3 | 1 | 3 | 10 |
| ALB on primary | 3 | 0 | 0 | 0 | 3 |
| ALB on secondary | 7 | 1 | 0 | 2 | 4 |

Important observation:

- `PolicyListPresentationTiming` almost disappeared in this run.
- primary had `0` presentation slow logs across the second 20-sample run.
- secondary had only one presentation slow log, at about the threshold:
  - `totalMs=50`, `projectionMs=46`, `regionMs=4`
- ALB had no presentation slow logs on either target.

## Interpretation

The post-deploy spot check still showed some `projectionMs` in the `54ms`-`81ms` range, but this remeasurement does not reproduce that as a repeated bottleneck.

Current state:

- loopback p95 is under `140ms` on primary and under `120ms` on secondary
- public ALB p95 is about `144ms`
- one ALB `220ms` sample did not correspond to a large app-side presentation log, so it is likely edge/network/runtime variance or a below-threshold distributed app segment rather than a clear projection bottleneck
- summary presentation is no longer the strongest repeated signal

## Round-Trip Reduction Options

Current summary projection read uses separate DB calls:

1. base rows from `welfare_services` + details + taxonomy summary slots
2. summary taxonomy terms
3. summary facts
4. region labels in `PolicyPresentationReadService`

The possible next implementation options are:

### Option A: Single Union Row Stream

Read base/taxonomy/fact rows with one SQL statement using a `row_type` discriminator.

Expected implementation shape:

- `BASE` rows carry base projection fields
- `TAXONOMY` rows carry `term_group`, `term_label`, `source_field`
- `FACT` rows carry fact fields
- Java mapping can still reuse `MutableProjection.addTaxonomyTerm(...)` and `addFact(...)`

Pros:

- reduces projection read from three DB round trips to one
- lower mapping risk than PostgreSQL array/json aggregation
- keeps existing Java label resolution behavior

Cons:

- more complex SQL and test setup
- all result rows share a sparse column set
- likely small user-visible gain while current p95 is already acceptable

### Option B: Aggregate Into One Row Per Service

Use SQL aggregates such as arrays or JSON per service.

Pros:

- fewer rows returned to Java
- one row per service can be attractive for presentation reads

Cons:

- JDBC array/json parsing adds new mapping complexity
- higher behavior-equivalence risk around order, dedupe, and null handling
- less aligned with current `MutableProjection` code

### Option C: Do Not Implement Yet

Keep current summary projection code and only revisit if the slow presentation signal repeats.

Recommended trigger to reopen:

- primary or ALB fresh-miss p95 repeatedly exceeds `180ms`-`200ms`, or
- `PolicyListPresentationTiming` appears in more than about 20% of fresh search misses, or
- `projectionMs` repeatedly lands above `70ms` in normal non-warmup requests.

## Decision

Do not implement another summary projection rewrite immediately.

The measured p95 is acceptable for the current state, and the original `projectionMs` signal is not repeated strongly enough. The next action should be observation-oriented:

1. Keep this measurement as the new post-summary-projection checkpoint.
2. If fresh search tail grows again, implement Option A first.
3. If tail remains stable, move performance attention away from summary projection and toward broader low-rate ALB baseline/edge variance checks.
