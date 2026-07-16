# Final Functional Regression Check 2026-07-16

## Purpose

This document records the final production functional regression check after the report-grade performance summary work.

The goal is not to create a new performance baseline. The goal is to confirm that the deployed service still returns the expected values across the main user path:

- signup/login/refresh/logout
- profile read and priority update
- policy list/search/detail
- recommendation refresh and stored read
- bookmark on/off state
- chatbot response and message history
- ALB, DB, JVM, and log-alert health before and after the flow

## Runtime Context

- check date: `2026-07-16`
- branch: `refactor/admin-dashboard-sections`
- primary revision: `f852c8a5`
- secondary revision: `f852c8a5`
- public base URL: `https://youthmoa.kr`
- topology: ALB -> 2 EC2 app nodes -> nginx -> Spring Boot app -> RDS PostgreSQL / ElastiCache Valkey
- production OpenAI: enabled
- script: `deploy/performance/run-local-integrated-user-journey-baseline.sh`
- artifact root: `tmp/performance/final-functional-regression`

No secrets from runtime env files are recorded in this document. Artifacts are sanitized.

## Pre-Check

Before the regression flow:

- local actuator: passed
- ALB target health: `2` healthy targets
- public policy list/search/ranking smoke: HTTP `200`, count `5`
- nginx recent user-facing 5xx: `0`
- DB waiting locks: `0`
- DB active queries over 5 minutes: `0`
- JVM health: HTTP `200`
- JVM restart count: `0`
- JVM memory: `636.3 MiB / 1 GiB`
- JVM recent exception count: `0`

Pre-check artifact examples:

- `tmp/prod-post-deploy-smoke/20260716T175329Z`
- `tmp/performance/jvm-runtime/20260716T175328Z`

## Regression Command

```bash
ENV_FILE=.env.runtime.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL=https://youthmoa.kr \
APP_HEALTH_URL=http://127.0.0.1:8082/actuator/health \
INTEGRATED_JOURNEY_ROOT=tmp/performance/final-functional-regression \
JOURNEY_REQUEST_DELAY_SECONDS=0.5 \
  bash deploy/performance/run-local-integrated-user-journey-baseline.sh
```

## Functional Result

Summary:

- total steps: `18`
- success count: `18`
- errors: `0`
- rate-limit responses: `0`
- HTTP status counts: all `200`
- artifact: `tmp/performance/final-functional-regression/20260716T175343Z`
- latest summary: `tmp/performance/final-functional-regression/latest-integrated-user-journey-summary.txt`
- latest JSON: `tmp/performance/final-functional-regression/latest-integrated-user-journey-summary.json`

Step-level correctness:

| Step | Expected value | Observed |
| --- | --- | --- |
| signup | account creation succeeds | HTTP `200` |
| login | access token is returned | `access_token_present=true` |
| refresh | refreshed access token is returned | `access_token_present=true` |
| profile read | profile payload exists | `profile_present=true` |
| priorities update | profile priority update accepted | HTTP `200` |
| policy list | list returns policies | result count `20`, first service `15406` |
| policy search | search returns policies | result count `20` |
| policy detail | selected policy detail exists | service `15406`, title present |
| recommendation refresh | generated recommendation list exists | result count `40`, `SCORED=12`, `NOT_REQUESTED=28` |
| recommendation read | stored recommendations are readable | result count `20` |
| bookmark on | target bookmark is added | service `15389` toggled on |
| bookmarks after on | target appears in bookmark list | contains service `true`, result count `1` |
| bookmark off | target bookmark is removed | service `15389` toggled off |
| bookmarks after off | target no longer appears | contains service `false`, result count `0` |
| chat create | chat session is created | session `254` |
| chat message | chatbot returns grounded answer | `POLICY_GROUNDED`, reference count `1`, clarification `false` |
| chat messages | user/assistant messages are readable | message count `2` |
| logout | logout accepted | HTTP `200` |

Latency was not the decision criterion for this regression, but the run recorded:

- total measured API time: `11724.4ms`
- non-AI measured API time: `923.8ms`
- AI-backed measured API time: `10800.6ms`
- slowest step: recommendation refresh `7131.7ms`
- chat message: `3668.9ms`

These numbers are consistent with the earlier conclusion that AI-backed recommendation/chat steps dominate end-to-end time.

## Data Mutation

Official regression DB delta:

| Table | Before | After | Delta |
| --- | ---: | ---: | ---: |
| users | 1379 | 1380 | +1 |
| user_recommendations | 18133 | 18173 | +40 |
| recommendation_logs | 32427 | 32467 | +40 |
| recommendation_run_logs | 555 | 556 | +1 |
| chat_sessions | 13 | 13 | 0 |
| chat_messages | 44 | 44 | 0 |
| chat_retrieval_snapshots | 340 | 340 | 0 |

The chat row delta is expected to stay `0` because logout deletes the user's chat sessions. The chatbot response and message history were validated before logout.

The bookmark is toggled on and then off in the same flow, so the final bookmark state is clean.

## Post-Check

After the regression flow:

- local actuator: passed
- ALB target health: `2` healthy targets
- public policy list/search/ranking smoke: HTTP `200`, count `5`
- nginx recent user-facing 5xx: `0`
- DB waiting locks: `0`
- DB active queries over 5 minutes: `0`
- JVM health: HTTP `200`
- JVM restart count: `0`
- JVM memory: `637.1 MiB / 1 GiB`
- JVM recent exception count: `0`
- log alert: `ok`
- log alert excluded AI latency note: `POST /api/recommendations/refresh p95 7118ms count 1`

Post-check artifact examples:

- `tmp/prod-post-deploy-smoke/20260716T175423Z`
- `tmp/performance/jvm-runtime/20260716T175423Z`
- `tmp/performance/app-log-observability/latest-app-log-observability-summary.json`
- `tmp/performance/nginx-log-observability/latest-nginx-log-observability-summary.json`

## Secondary Instance Check

Secondary instance check through SSM:

- instance: `i-0e8a4cc599c1148c8`
- command id: `fbf30067-82ed-4dc2-9b1c-7a2557376aed`
- revision: `f852c8a5`
- git status: clean against `origin/refactor/admin-dashboard-sections`
- local actuator health: `UP`

## Decision

The final functional regression check passed.

Accepted statements:

- all major user-facing API functions in the integrated flow returned expected values
- recommendation generation and stored recommendation read both worked
- bookmark on/off state was reflected correctly
- chatbot produced a grounded response with a reference
- logout completed and cleaned up chat session rows
- ALB, DB, JVM, and log-alert checks remained healthy after the run
- primary and secondary instances are on the same git revision

No additional code-level performance optimization is recommended from this regression checkpoint. The remaining work should be documentation, operational runbook cleanup, or presentation/report polishing.
