# Report-Grade Integrated User Journey Measurement 2026-07-16

## Purpose

This document records the first report-grade measurement for Group E, the integrated user journey.

The goal is to measure one realistic end-to-end user path across auth, profile, public read APIs, recommendation generation, bookmark state, chatbot, and logout. This is separate from maximum throughput testing and separate from the endpoint-specific measurements.

## Runtime Context

- measurement date: `2026-07-16`
- branch: `refactor/admin-dashboard-sections`
- deployed app runtime revision before measurement: `99933dd8`
- public base URL: `https://youthmoa.kr`
- topology: ALB -> 2 EC2 app nodes -> nginx -> Spring Boot app -> RDS PostgreSQL / ElastiCache Valkey
- production OpenAI: enabled
- measurement account count: `1`
- measured steps: `18`
- AI-backed measured steps: `recommend_refresh`, `chat_message`

No secrets from runtime env files are recorded in this document. Measurement artifacts are sanitized so tokens, cookies, passwords, emails, and user keys are redacted.

## Measurement Scope

Included flow:

1. signup
2. login
3. refresh
4. profile read
5. priority update
6. policy list
7. policy search
8. policy detail
9. recommendation refresh
10. recommendation stored read
11. bookmark on
12. bookmark list after on
13. bookmark off
14. bookmark list after off
15. chat session create
16. chat message
17. chat message history read
18. logout

Excluded:

- password reset
- email delivery
- high-RPS stress
- multi-user distributed load
- negative auth checks after logout

## Method

A dedicated script was added:

```bash
ENV_FILE=.env.runtime.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL=https://youthmoa.kr \
APP_HEALTH_URL=http://127.0.0.1:8082/actuator/health \
INTEGRATED_JOURNEY_ROOT=tmp/performance/integrated-user-journey \
JOURNEY_REQUEST_DELAY_SECONDS=0.5 \
  bash deploy/performance/run-local-integrated-user-journey-baseline.sh
```

Important implementation details:

- test email is generated with a dedicated `integrated.journey.perf` prefix
- email verification is seeded in Redis instead of sending mail
- one test account is used to keep rate-limit and OpenAI cost bounded
- the bookmark is toggled on and then off, so the final bookmark state is clean
- logout is part of the measured journey
- logout deletes the user's chat sessions through `AuthTokenService`, so final chat DB row delta is expected to be `0` even though the chat step succeeded
- raw response artifacts are sanitized before publishing to `latest`
- DB row counts are captured before and after the measured flow

Correctness signals recorded per step:

- HTTP status
- duration
- error code
- expected response shape
- recommendation count and AI status counts
- bookmark containment after on/off
- chatbot answer mode, clarification flag, reference count, and message count

## Pre-Check

Before the official integrated run:

- contract check: passed
- ALB target health: `2` healthy targets
- public policy list/search/ranking smoke: HTTP `200`, count `5`
- nginx recent user-facing 5xx: `0`
- DB waiting locks: `0`
- DB active queries over 5 minutes: `0`
- JVM health: HTTP `200`
- JVM restart count: `0`
- JVM memory before run: `636.4 MiB / 1 GiB`
- JVM recent exception count: `0`

The DB lock and long-query checks were executed directly with app DB read credentials.

## Contract Check

Before the official run, the full script was executed once against the public ALB URL.

Result:

- total steps: `18`
- success count: `18`
- errors: `0`
- rate-limit responses: `0`
- total measured API time: `11711.3ms`
- non-AI measured API time: `1048.9ms`
- AI measured API time: `10662.4ms`
- recommendation refresh: `7126.7ms`, result count `40`, AI statuses `{"NOT_REQUESTED":28,"SCORED":12}`
- chat message: `3535.8ms`, answer mode `POLICY_GROUNDED`, reference count `1`

Artifact:

- `tmp/performance/integrated-user-journey-contract/20260716T174209Z`

Contract-check DB delta:

| Table | Before | After | Delta |
| --- | ---: | ---: | ---: |
| users | 1377 | 1378 | +1 |
| user_recommendations | 18053 | 18093 | +40 |
| recommendation_logs | 32347 | 32387 | +40 |
| recommendation_run_logs | 553 | 554 | +1 |
| chat_sessions | 13 | 13 | 0 |
| chat_messages | 44 | 44 | 0 |
| chat_retrieval_snapshots | 340 | 340 | 0 |

The chat DB delta stayed `0` because logout cleans up chat sessions for the user. The chat response itself was validated before logout.

## Official Result

Summary:

- total measured steps: `18`
- success count: `18`
- errors: `0`
- rate-limit responses: `0`
- HTTP status counts: `{"200": 18}`
- total measured API time: `8935.8ms`
- non-AI measured API time: `980.9ms`
- AI measured API time: `7954.9ms`
- AI share of measured API time: about `89.0%`
- overall per-step p95: `3475.7ms`
- slowest step: `4694.2ms`

Step-level result:

| Step | Phase | Status | Duration | Correctness signal |
| --- | --- | ---: | ---: | --- |
| signup | auth | 200 | 162.2ms | account created |
| login | auth | 200 | 132.1ms | access token present |
| refresh | auth | 200 | 27.3ms | refreshed access token present |
| profile_me | profile | 200 | 33.7ms | profile present |
| priorities_update | profile | 200 | 50.2ms | priority update accepted |
| policy_list | read_api | 200 | 49.5ms | result count `20`, first service `15406` |
| policy_search | read_api | 200 | 120.2ms | result count `20` |
| policy_detail | read_api | 200 | 47.3ms | service `15406`, title present |
| recommend_refresh | recommendation | 200 | 4694.2ms | result count `40`, AI statuses `{"NOT_REQUESTED":28,"SCORED":12}` |
| recommend_read | recommendation | 200 | 51.1ms | result count `20` |
| bookmark_on | bookmark | 200 | 32.4ms | service `15389` toggled on |
| bookmarks_after_on | bookmark | 200 | 49.4ms | service `15389` present |
| bookmark_off | bookmark | 200 | 124.2ms | service `15389` toggled off |
| bookmarks_after_off | bookmark | 200 | 22.6ms | service `15389` absent |
| chat_create | chat | 200 | 23.3ms | session created |
| chat_message | chat | 200 | 3260.7ms | `POLICY_GROUNDED`, reference count `1`, clarification `false` |
| chat_messages | chat | 200 | 26.6ms | message count `2` |
| logout | auth | 200 | 28.8ms | logout accepted |

Phase-level measured API time:

| Phase | Step count | Total | Max | Average |
| --- | ---: | ---: | ---: | ---: |
| auth | 4 | 350.5ms | 162.2ms | 87.6ms |
| profile | 2 | 83.9ms | 50.2ms | 42.0ms |
| read_api | 3 | 217.0ms | 120.2ms | 72.3ms |
| recommendation | 2 | 4745.3ms | 4694.2ms | 2372.6ms |
| bookmark | 4 | 228.6ms | 124.2ms | 57.1ms |
| chat | 3 | 3310.6ms | 3260.7ms | 1103.5ms |

Artifact:

- `tmp/performance/integrated-user-journey/20260716T174247Z`
- latest: `tmp/performance/integrated-user-journey/latest`
- latest summary: `tmp/performance/integrated-user-journey/latest-integrated-user-journey-summary.txt`
- latest JSON: `tmp/performance/integrated-user-journey/latest-integrated-user-journey-summary.json`

Official run DB delta:

| Table | Before | After | Delta |
| --- | ---: | ---: | ---: |
| users | 1378 | 1379 | +1 |
| user_recommendations | 18093 | 18133 | +40 |
| recommendation_logs | 32387 | 32427 | +40 |
| recommendation_run_logs | 554 | 555 | +1 |
| chat_sessions | 13 | 13 | 0 |
| chat_messages | 44 | 44 | 0 |
| chat_retrieval_snapshots | 340 | 340 | 0 |

## Post-Check

After the official run:

- no-cost post-deploy smoke subset: passed
- ALB target health: `2` healthy targets
- public policy list/search/ranking smoke: HTTP `200`, count `5`
- nginx recent user-facing 5xx: `0`
- DB waiting locks: `0`
- DB active queries over 5 minutes: `0`
- JVM health: HTTP `200`
- JVM restart count: `0`
- JVM memory after run: `636.5 MiB / 1 GiB`
- JVM recent exception count: `0`
- log alert: `ok`
- log alert excluded AI latency note: `POST /api/chat/sessions/253/messages p95 3249ms count 1`

## Data Mutation

This measurement created real production test data:

- contract-check accounts: `1`
- official measurement accounts: `1`
- total new test accounts from this task: `2`
- total user_recommendations delta from this task: `+80`
- total recommendation_logs delta from this task: `+80`
- total recommendation_run_logs delta from this task: `+2`
- final bookmark state: off / not present in bookmark list
- final chat row delta: `0`, because logout cleans up user chat sessions

The accounts use a dedicated generated prefix derived from `integrated.journey.perf`. They were left in place as identifiable test data rather than manually deleting rows, because cleanup should go through supported user withdrawal behavior when required.

## Interpretation

The public integrated user journey passed the first report-grade end-to-end measurement.

Accepted statements from this pass:

- all `18` measured steps returned HTTP `200`
- there were `0` application errors
- there were `0` rate-limit responses
- public read API steps returned expected result shapes
- recommendation refresh returned `40` recommendations with `12` `SCORED` AI-status items
- stored recommendation read returned `20` recommendations
- bookmark on/off was reflected in `/api/users/me/bookmarks`
- chatbot returned a `POLICY_GROUNDED` answer with one reference
- logout completed and cleaned up the measured user's chat session rows
- no post-measurement evidence showed ALB target failure, app restart, DB waiting locks, long-running DB work, or user-facing nginx 5xx

The main performance conclusion is that the integrated journey is now dominated by AI-backed operations:

- non-AI measured API time was under `1s`
- recommendation refresh and chat message together took about `7.95s`
- AI-backed steps accounted for about `89.0%` of measured API time

Limitations:

- this is a first one-account integrated journey pass, not a multi-user throughput test
- measured time is API processing time summed across steps, not browser think time or page rendering time
- OpenAI latency and availability are included in the recommendation and chat steps
- signup is included, so this represents a new-user journey rather than a returning-user-only journey
- no password reset, email send, or distributed region/IP load was measured

Current report position:

- use this document for end-to-end user journey evidence
- use endpoint-specific documents for detailed read/auth/recommendation/chat numbers
- do not present this as maximum concurrent-user capacity
- if a returning-user journey is required, rerun a separate login-only variant without signup

## Next Step

Recommended next work:

1. consolidate all report-grade measurements into a final result-report performance summary table
2. add a short interpretation section separating non-AI latency from AI-backed latency
3. run a final regression smoke only if additional code changes are made
