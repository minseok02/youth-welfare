# Report-Grade Read API Measurement 2026-07-16

## Purpose

This document records the three-run report-grade measurement for Group A, the public read-only policy API.

The goal is to produce reportable latency evidence for normal read traffic through the public ALB. This is separate from the capacity-boundary test in `final-load-capacity-check-2026-07-16.md`, which intentionally increased traffic until Redis rate limits started returning 429.

## Runtime Context

- measurement date: `2026-07-16`
- branch: `refactor/admin-dashboard-sections`
- deployed app runtime revision: `a7f733d8`
- run 1 measurement source revision: `a7f733d8` plus uncommitted measurement-script/documentation changes
- runs 2 and 3 measurement source revision: `a600bba3`
- public base URL: `https://youthmoa.kr`
- topology: ALB -> 2 EC2 app nodes -> nginx -> Spring Boot app -> RDS PostgreSQL / ElastiCache Valkey
- ALB target group: 2 healthy targets before and after the run
- data profile: about `13k+` policy records in the production policy table

No secrets from `.env.production` or `.env.runtime.production` are recorded in this document.

## Measurement Scope

Included endpoints:

- `GET /api/policies?page=0&size=20`
- `POST /api/policies/search` with a keyword
- `POST /api/policies/search` with filters
- `POST /api/policies/search/suggestions`
- `GET /api/policies/ranking?size=20`
- `GET /api/policies/{firstPolicyId}`
- mixed read profile across the same endpoint set

Excluded from this pass:

- auth/login flow
- recommendation refresh
- chatbot messages
- integrated login -> search -> recommendation -> chat journey

Those flows mutate user/session/recommendation/chat data and have separate rate limits and OpenAI cost behavior. They should be measured as Groups B to E from `report-grade-performance-measurement-plan-2026-07-16.md`.

## Method

The read API script was extended to accept `API_LOAD_SCENARIOS`, so each endpoint can be measured independently without editing the TSV scenario file.

Common settings:

- public ALB URL
- one client source
- one User-Agent
- `INCLUDE_HEALTH=false`
- `DURATION_SECONDS=90`
- `CONCURRENCY=1`
- endpoint-specific delay chosen to stay below current rate limits
- `REQUEST_TIMEOUT_SECONDS=10`

Interpretation:

- this run measures clean representative latency under controlled low concurrency
- `429` would mean the public Redis rate-limit policy activated
- no `429` in this pass means the latency numbers are not polluted by expected protection responses
- this is not a maximum-throughput or multi-region distributed-load result

## Commands

Representative command shape:

```bash
API_LOAD_ROOT='tmp/performance/report-grade-read-api-list-default' \
APP_BASE_URL='https://youthmoa.kr' \
INCLUDE_HEALTH=false \
API_LOAD_SCENARIOS='policy_list_default' \
DURATION_SECONDS=90 \
CONCURRENCY=1 \
API_LOAD_REQUEST_DELAY_SECONDS=1.2 \
REQUEST_TIMEOUT_SECONDS=10 \
  bash deploy/performance/run-local-api-load-baseline.sh
```

Executed scenario matrix:

| Label | Scenario | Duration | Concurrency | Delay |
| --- | --- | ---: | ---: | ---: |
| list-default | `policy_list_default` | 90s | 1 | 1.2s |
| search-keyword | `policy_search_keyword` | 90s | 1 | 1.2s |
| search-filtered | `policy_search_filtered` | 90s | 1 | 1.2s |
| suggestions | `policy_suggestions` | 90s | 1 | 0.7s |
| ranking | `policy_ranking` | 90s | 1 | 2.5s |
| detail-first | `policy_detail_first` | 90s | 1 | 3.5s |
| mixed-safe | `mixed` | 90s | 1 | 1.2s |

## Pre-Check

Before the run:

- no-cost ops check: passed
- public post-deploy smoke: passed
- ALB target health: `2` healthy targets
- public policy list/search/ranking smoke: HTTP `200`, count `5`
- nginx recent user-facing 5xx: `0`
- DB active queries over 5 minutes: `0`
- DB waiting locks: `0`
- DB idle transactions over 5 minutes: `0`
- DB connections: `24 / 191`
- database size: `553 MB`
- JVM container restart count: `0`
- JVM memory: `595.8 MiB / 1 GiB`
- JVM recent exception count: `0`

## Results

### Three-Run Endpoint Summary

| Scenario | Runs | Requests | Errors | 429 | Avg p50 | Avg p95 | Worst p95 | Avg p99 | Worst max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| policy list default | 3 | 221 | 0 | 0 | 29.5ms | 41.1ms | 45.9ms | 68.2ms | 94.9ms |
| policy search keyword | 3 | 222 | 0 | 0 | 21.2ms | 74.2ms | 86.6ms | 166.3ms | 303.2ms |
| policy search filtered | 3 | 222 | 0 | 0 | 21.6ms | 38.0ms | 43.7ms | 110.1ms | 311.0ms |
| policy suggestions | 3 | 369 | 0 | 0 | 30.7ms | 44.3ms | 47.8ms | 78.5ms | 148.1ms |
| policy ranking | 3 | 108 | 0 | 0 | 15.4ms | 34.1ms | 44.6ms | 57.9ms | 91.8ms |
| policy detail first | 3 | 78 | 0 | 0 | 31.9ms | 56.7ms | 59.4ms | 120.9ms | 221.2ms |

### Three-Run Mixed Profile Summary

| Scenario | Runs | Requests | Errors | 429 | Avg p50 | Avg p95 | Worst p95 | Avg p99 | Worst max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| policy search keyword | 3 | 37 | 0 | 0 | 22.8ms | 145.3ms | 190.7ms | 169.2ms | 269.8ms |
| policy search filtered | 3 | 36 | 0 | 0 | 24.4ms | 70.9ms | 75.0ms | 72.8ms | 76.5ms |
| policy suggestions | 3 | 36 | 0 | 0 | 33.3ms | 51.9ms | 59.4ms | 58.9ms | 70.8ms |
| policy list default | 3 | 39 | 0 | 0 | 31.9ms | 51.6ms | 57.8ms | 59.9ms | 68.9ms |
| policy detail first | 3 | 36 | 0 | 0 | 30.4ms | 44.7ms | 58.5ms | 51.6ms | 80.2ms |
| policy ranking | 3 | 36 | 0 | 0 | 16.9ms | 34.4ms | 56.5ms | 43.7ms | 87.2ms |

### Run-Level Summary

| Run | Label | Requests | Throughput | Errors | 429 | Worst scenario | Worst p95 |
| --- | --- | ---: | ---: | ---: | ---: | --- | ---: |
| r1 | list-default | 73 | 0.811 rps | 0 | 0 | policy list default | 45.9ms |
| r1 | search-keyword | 74 | 0.813 rps | 0 | 0 | policy search keyword | 65.7ms |
| r1 | search-filtered | 74 | 0.817 rps | 0 | 0 | policy search filtered | 32.2ms |
| r1 | suggestions | 123 | 1.363 rps | 0 | 0 | policy suggestions | 45.5ms |
| r1 | ranking | 36 | 0.397 rps | 0 | 0 | policy ranking | 44.6ms |
| r1 | detail-first | 26 | 0.283 rps | 0 | 0 | policy detail first | 59.4ms |
| r1 | mixed-safe | 73 | 0.810 rps | 0 | 0 | policy search keyword | 119.5ms |
| r2 | list-default | 74 | 0.812 rps | 0 | 0 | policy list default | 37.4ms |
| r2 | search-keyword | 74 | 0.813 rps | 0 | 0 | policy search keyword | 86.6ms |
| r2 | search-filtered | 74 | 0.817 rps | 0 | 0 | policy search filtered | 43.7ms |
| r2 | suggestions | 123 | 1.366 rps | 0 | 0 | policy suggestions | 39.7ms |
| r2 | ranking | 36 | 0.397 rps | 0 | 0 | policy ranking | 36.8ms |
| r2 | detail-first | 26 | 0.283 rps | 0 | 0 | policy detail first | 59.0ms |
| r2 | mixed-safe | 74 | 0.812 rps | 0 | 0 | policy search keyword | 125.7ms |
| r3 | list-default | 74 | 0.813 rps | 0 | 0 | policy list default | 40.0ms |
| r3 | search-keyword | 74 | 0.815 rps | 0 | 0 | policy search keyword | 70.2ms |
| r3 | search-filtered | 74 | 0.814 rps | 0 | 0 | policy search filtered | 37.9ms |
| r3 | suggestions | 123 | 1.364 rps | 0 | 0 | policy suggestions | 47.8ms |
| r3 | ranking | 36 | 0.397 rps | 0 | 0 | policy ranking | 20.9ms |
| r3 | detail-first | 26 | 0.283 rps | 0 | 0 | policy detail first | 51.7ms |
| r3 | mixed-safe | 73 | 0.809 rps | 0 | 0 | policy search keyword | 190.7ms |

Run 1 endpoint-only results:

| Scenario | Requests | Throughput | p50 | p95 | p99 | Max | Errors | 429 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| policy list default | 73 | 0.811 rps | 31.0ms | 45.9ms | 71.9ms | 94.9ms | 0 | 0 |
| policy search keyword | 74 | 0.813 rps | 21.4ms | 65.7ms | 191.8ms | 303.2ms | 0 | 0 |
| policy search filtered | 74 | 0.817 rps | 22.1ms | 32.2ms | 77.6ms | 99.9ms | 0 | 0 |
| policy suggestions | 123 | 1.363 rps | 30.4ms | 45.5ms | 94.9ms | 148.1ms | 0 | 0 |
| policy ranking | 36 | 0.397 rps | 15.9ms | 44.6ms | 76.3ms | 91.8ms | 0 | 0 |
| policy detail first | 26 | 0.283 rps | 34.6ms | 59.4ms | 110.9ms | 126.2ms | 0 | 0 |

Run 1 mixed safe profile:

| Scenario | Requests | p50 | p95 | p99 | Max | Errors | 429 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| policy search keyword | 12 | 24.1ms | 119.5ms | 120.6ms | 120.8ms | 0 | 0 |
| policy search filtered | 12 | 24.6ms | 72.1ms | 75.6ms | 76.5ms | 0 | 0 |
| policy suggestions | 12 | 31.2ms | 59.4ms | 68.5ms | 70.8ms | 0 | 0 |
| policy detail first | 12 | 30.1ms | 58.5ms | 75.8ms | 80.2ms | 0 | 0 |
| policy ranking | 12 | 17.5ms | 56.5ms | 81.0ms | 87.2ms | 0 | 0 |
| policy list default | 13 | 30.4ms | 50.8ms | 57.8ms | 59.6ms | 0 | 0 |

Mixed safe totals:

- total requests: `73`
- total throughput: `0.810 rps`
- total errors: `0`
- total 429: `0`
- worst p95 scenario: `policy_search_keyword`, `119.5ms`

## Post-Check

After the run:

- no-cost post-check subset: passed
- public post-deploy smoke: passed
- ALB target health: `2` healthy targets
- public policy list/search/ranking smoke: HTTP `200`, count `5`
- nginx recent user-facing 5xx: `0`
- DB active queries over 5 minutes: `0`
- DB waiting locks: `0`
- DB connections after run 1: `23 / 191`
- DB connections after runs 2 and 3: `25 / 191`
- database size after run 1: `553 MB`
- database size after runs 2 and 3: `554 MB`
- app log alert: `ok`
- nginx log alert: `ok`
- JVM health: HTTP `200`
- JVM restart count: `0`
- JVM memory after run 1: `597.7 MiB / 1 GiB`
- JVM memory after runs 2 and 3: `626.6 MiB / 1 GiB`
- JVM recent exception count: `0`
- DB collect execution lock note: `collect-global` was active until `2026-07-16 17:18:00.144873`; this is a collection scheduler lock, not a PostgreSQL waiting lock. `waiting_locks=0`, `active_queries_over_5m=0`, and user-facing smoke remained healthy.

## Artifacts

- `tmp/performance/report-grade-read-api-list-default/20260716T161850Z`
- `tmp/performance/report-grade-read-api-search-keyword/20260716T162030Z`
- `tmp/performance/report-grade-read-api-search-filtered/20260716T162212Z`
- `tmp/performance/report-grade-read-api-suggestions/20260716T162353Z`
- `tmp/performance/report-grade-read-api-ranking/20260716T162533Z`
- `tmp/performance/report-grade-read-api-detail-first/20260716T162715Z`
- `tmp/performance/report-grade-read-api-mixed-safe/20260716T162857Z`
- `tmp/performance/report-grade-read-api-r2-list-default/20260716T163819Z`
- `tmp/performance/report-grade-read-api-r2-search-keyword/20260716T164000Z`
- `tmp/performance/report-grade-read-api-r2-search-filtered/20260716T164142Z`
- `tmp/performance/report-grade-read-api-r2-suggestions/20260716T164323Z`
- `tmp/performance/report-grade-read-api-r2-ranking/20260716T164503Z`
- `tmp/performance/report-grade-read-api-r2-detail-first/20260716T164644Z`
- `tmp/performance/report-grade-read-api-r2-mixed-safe/20260716T164827Z`
- `tmp/performance/report-grade-read-api-r3-list-default/20260716T165008Z`
- `tmp/performance/report-grade-read-api-r3-search-keyword/20260716T165149Z`
- `tmp/performance/report-grade-read-api-r3-search-filtered/20260716T165331Z`
- `tmp/performance/report-grade-read-api-r3-suggestions/20260716T165512Z`
- `tmp/performance/report-grade-read-api-r3-ranking/20260716T165653Z`
- `tmp/performance/report-grade-read-api-r3-detail-first/20260716T165834Z`
- `tmp/performance/report-grade-read-api-r3-mixed-safe/20260716T170016Z`
- `tmp/performance/jvm-runtime/20260716T163256Z`
- `tmp/performance/jvm-runtime/20260716T170233Z`
- `tmp/prod-post-deploy-smoke/20260716T163256Z`
- `tmp/prod-post-deploy-smoke/20260716T170233Z`

## Interpretation

The public read-only policy API passed the three-run report-grade representative-latency measurement.

Accepted statements from this pass:

- every measured read endpoint returned `0` errors and `0` rate-limit responses
- endpoint-only average p95 stayed below `75ms`
- endpoint-only worst p95 stayed below `87ms`
- the mixed read profile's average worst p95 was `145.3ms` for keyword search
- the mixed read profile's absolute worst p95 was `190.7ms` for keyword search in run 3
- no post-measurement evidence showed ALB target failure, app restart, DB lock buildup, long-running DB work, or user-facing nginx 5xx

Limitations:

- this is a three-run controlled sample, not a distributed multi-region load test
- it uses one source and one User-Agent, so it intentionally measures same-source behavior
- it does not prove maximum concurrent user capacity
- AI-backed recommendation/chat and auth flows are not represented here

Current report position:

- use this document for normal public read API latency with three-run average and worst p95
- use `final-load-capacity-check-2026-07-16.md` for rate-limit/capacity-boundary behavior
- measure auth, recommendation, chat, and integrated journey separately before making user-flow capacity claims

## Next Step

Recommended next measurement order:

1. run Group B auth flow with a small test account pool
2. run Group C recommendation read/shared refresh/personal refresh with explicit OpenAI-cost labeling
3. run Group D chat as a low-iteration representative latency test
4. run Group E integrated user journey after B to D are stable
