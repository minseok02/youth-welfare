# Report-Grade Recommendation Flow Measurement 2026-07-16

## Purpose

This document records the first report-grade measurement for Group C, the recommendation flow.

The goal is to measure recommendation read and generation latency separately:

1. stored recommendation read
2. shared refresh
3. stored read after refresh
4. shared refresh cache reuse
5. personal refresh
6. stored read after personal refresh

This is separate from public read API latency, auth latency, chatbot latency, and integrated user-journey duration.

## Runtime Context

- measurement date: `2026-07-16`
- branch: `refactor/admin-dashboard-sections`
- deployed app runtime revision: `a7f733d8`
- measurement source revision: `c9abe400` plus uncommitted recommendation-flow script/documentation changes
- public base URL: `https://youthmoa.kr`
- topology: ALB -> 2 EC2 app nodes -> nginx -> Spring Boot app -> RDS PostgreSQL / ElastiCache Valkey
- production OpenAI: enabled
- recommendation AI top-N: default `12`
- shared refresh rate limit: default `3 / 60s` per user
- personal refresh rate limit: production override observed earlier as `50` max requests
- measurement account count: `1`

No secrets from `.env.production` or `.env.runtime.production` are recorded in this document. Measurement artifacts are sanitized so tokens, cookies, passwords, emails, and user keys are redacted.

## Measurement Scope

Included steps:

- `GET /api/recommendations?size=20` before refresh
- `POST /api/recommendations/refresh?personal=false`
- `GET /api/recommendations?size=20` after shared refresh
- `POST /api/recommendations/refresh?personal=false` repeated to verify cache reuse
- `POST /api/recommendations/refresh?personal=true`
- `GET /api/recommendations?size=20` after personal refresh

Setup-only steps:

- verified test account signup
- login to obtain access token

Excluded:

- bookmark toggle
- click tracking
- chatbot
- email delivery
- password reset
- high-RPS recommendation stress

## Method

A dedicated script was added:

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
RECOMMEND_FLOW_ROOT=tmp/performance/report-grade-recommendation-flow \
APP_BASE_URL=https://youthmoa.kr \
APP_HEALTH_URL=http://127.0.0.1:8082/actuator/health \
RECOMMEND_RUN_PERSONAL=true \
RECOMMEND_REQUEST_DELAY_SECONDS=1.0 \
SMOKE_TRUSTED_ORIGIN=https://youthmoa.kr \
  bash deploy/performance/run-local-recommendation-flow-baseline.sh
```

Important implementation details:

- test email is generated with a dedicated `recommend.flow.perf` prefix
- email verification is seeded in Redis instead of sending mail
- one test account is used to keep rate-limit and OpenAI cost bounded
- refresh requests include trusted `Origin` and `Referer`
- raw response artifacts are sanitized before publishing to `latest`
- DB row counts are captured before and after the measured flow

Interpretation:

- `R004` or HTTP `429` means recommendation refresh rate-limit activated
- `R003` or HTTP `409` means same-user recommendation generation was already running
- `aiStatus=SCORED` means AI scoring was present in returned recommendations
- `aiStatus=NOT_REQUESTED` means the item was not part of the AI top-N request set
- because production OpenAI is enabled, any step returning `SCORED` should be treated as OpenAI-backed and cost-bearing

## Pre-Check

Before the recommendation run:

- no-cost post-deploy smoke subset: passed
- ALB target health: `2` healthy targets
- public policy list/search/ranking smoke: HTTP `200`, count `5`
- nginx recent user-facing 5xx: `0`
- DB active queries over 5 minutes: `0`
- DB waiting locks: `0`
- DB idle transactions over 5 minutes: `0`
- DB collect scheduler lock: `0`
- DB connections: `25 / 191`
- database size: `559 MB`
- JVM health: HTTP `200`
- JVM restart count: `0`
- JVM memory: `634.5 MiB / 1 GiB`
- JVM recent exception count: `0`

## Contract Check

Before the official run, the script was executed with `RECOMMEND_RUN_PERSONAL=false`.

Result:

| Step | Status | Duration | Result count | Error | AI status counts |
| --- | ---: | ---: | ---: | --- | --- |
| stored read before | 200 | 96.8ms | 0 | - | `{}` |
| shared refresh | 200 | 7021.8ms | 39 | - | `{"NOT_REQUESTED":27,"SCORED":12}` |
| stored read after | 200 | 72.0ms | 20 | - | `{"NOT_REQUESTED":14,"SCORED":6}` |
| shared refresh cached | 200 | 81.6ms | 39 | - | `{"NOT_REQUESTED":27,"SCORED":12}` |
| stored read final | 200 | 431.4ms | 20 | - | `{"NOT_REQUESTED":14,"SCORED":6}` |

Artifact:

- `tmp/performance/recommendation-flow-contract-check/20260716T172022Z`

Contract-check DB delta:

| Table | Before | After | Delta |
| --- | ---: | ---: | ---: |
| user_recommendations | 17975 | 18014 | +39 |
| recommendation_logs | 32269 | 32308 | +39 |
| recommendation_run_logs | 548 | 550 | +2 |

Observation:

- `personal=false` still returned `SCORED` recommendations in this runtime, so the shared refresh path must be reported as AI-backed in this measurement.
- The second shared refresh was much faster because it reused the refresh cache marker.

The contract check created one test account.

## Official Result

Summary:

- account count: `1`
- total measured steps: `6`
- success count: `6`
- total errors: `0`
- total 429/R004: `0`
- total 409/R003: `0`

Latency and result shape:

| Step | Status | Duration | Result count | Error | AI status counts |
| --- | ---: | ---: | ---: | --- | --- |
| stored read before | 200 | 49.3ms | 0 | - | `{}` |
| shared refresh | 200 | 5437.4ms | 39 | - | `{"NOT_REQUESTED":27,"SCORED":12}` |
| stored read after | 200 | 116.4ms | 20 | - | `{"NOT_REQUESTED":16,"SCORED":4}` |
| shared refresh cached | 200 | 209.5ms | 39 | - | `{"NOT_REQUESTED":27,"SCORED":12}` |
| personal refresh | 200 | 3933.2ms | 39 | - | `{"NOT_REQUESTED":27,"SCORED":12}` |
| stored read final | 200 | 46.6ms | 20 | - | `{"NOT_REQUESTED":14,"SCORED":6}` |

Artifact:

- `tmp/performance/report-grade-recommendation-flow/20260716T172049Z`
- latest: `tmp/performance/report-grade-recommendation-flow/latest`
- latest summary: `tmp/performance/report-grade-recommendation-flow/latest-recommendation-flow-summary.txt`
- latest JSON: `tmp/performance/report-grade-recommendation-flow/latest-recommendation-flow-summary.json`

Official run DB delta:

| Table | Before | After | Delta |
| --- | ---: | ---: | ---: |
| user_recommendations | 18014 | 18053 | +39 |
| recommendation_logs | 32308 | 32347 | +39 |
| recommendation_run_logs | 550 | 553 | +3 |

## Post-Check

After the official run:

- no-cost post-deploy smoke subset: passed
- ALB target health: `2` healthy targets
- public policy list/search/ranking smoke: HTTP `200`, count `5`
- nginx recent user-facing 5xx: `0`
- DB active queries over 5 minutes: `0`
- DB waiting locks: `0`
- DB idle transactions over 5 minutes: `0`
- DB collect scheduler lock: `0`
- DB connections: `26 / 191`
- database size: `559 MB`
- app log alert: `ok`
- nginx log alert: `ok`
- log alert excluded batch latency note: `POST /api/recommendations/refresh p95 187ms count 1`
- JVM health: HTTP `200`
- JVM restart count: `0`
- JVM memory: `635 MiB / 1 GiB`
- JVM recent exception count: `0`

## Data Mutation

This measurement created real production test data:

- contract-check accounts: `1`
- official measurement accounts: `1`
- total new test accounts from this task: `2`
- total user_recommendations delta from this task: `+78`
- total recommendation_logs delta from this task: `+78`
- total recommendation_run_logs delta from this task: `+5`

The accounts use a dedicated generated prefix derived from `recommend.flow.perf`. They were left in place as identifiable test data rather than manually deleting rows, because deletion should go through supported user withdrawal behavior when cleanup is required.

## Interpretation

The public recommendation flow passed the first report-grade representative-latency measurement.

Accepted statements from this pass:

- all official recommendation steps returned HTTP `200`
- there were `0` rate-limit responses (`429` / `R004`)
- there were `0` already-running conflicts (`409` / `R003`)
- stored recommendation read after data existed stayed below `117ms` in the official run
- first shared refresh took `5437.4ms`
- repeated shared refresh with cache marker took `209.5ms`
- personal refresh took `3933.2ms`
- shared and personal refresh both returned `39` recommendations
- shared and personal refresh both returned `12` `SCORED` AI-status items and `27` `NOT_REQUESTED` items
- no post-measurement evidence showed ALB target failure, app restart, DB waiting locks, long-running DB work, or user-facing nginx 5xx

Limitations:

- this is a first one-account recommendation-flow pass, not a three-run sample
- OpenAI latency and availability are included in the refresh numbers
- this is not a high-RPS recommendation throughput test
- it does not measure recommendation quality, bookmark behavior, click tracking, or long-term recommendation stability
- the shared refresh path was observed as AI-backed in this runtime, so it should not be presented as a no-cost rule-only path

Current report position:

- use this document for representative recommendation read and generation latency
- explicitly label refresh latency as OpenAI-influenced/cost-bearing
- use the read API and auth documents for non-AI request latency
- measure chatbot and integrated user journey separately before making whole-user-flow claims

## Next Step

Recommended next measurement order:

1. run Group D chatbot as a low-iteration representative latency test
2. run Group E integrated user journey after chatbot is measured separately
3. repeat recommendation measurement only if a three-run average is required, because each refresh can invoke OpenAI
