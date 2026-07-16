# Report-Grade Chat Flow Measurement 2026-07-16

## Purpose

This document records the first report-grade measurement for Group D, the chatbot flow.

The goal is to measure representative chatbot response latency and response shape separately from read-only API latency, auth latency, recommendation refresh latency, and integrated user-journey duration.

## Runtime Context

- measurement date: `2026-07-16`
- branch: `refactor/admin-dashboard-sections`
- deployed app runtime revision before measurement: `b40da627`
- public base URL: `https://youthmoa.kr`
- topology: ALB -> 2 EC2 app nodes -> nginx -> Spring Boot app -> RDS PostgreSQL / ElastiCache Valkey
- production OpenAI: enabled
- chat message rate limit: default `5 / 60s` per user
- measurement account count: `1`
- official question count: `3`

No secrets from runtime env files are recorded in this document. Measurement artifacts are sanitized so tokens, cookies, passwords, emails, and user keys are redacted.

## Measurement Scope

Included steps:

- `POST /api/chat/sessions`
- `POST /api/chat/sessions/{sessionId}/messages` x 3
- `GET /api/chat/sessions/{sessionId}/messages`

Setup-only steps:

- verified test account signup
- login to obtain access token
- priority update to `EDUCATION`, `JOB`, `HOUSING`

Excluded:

- high-RPS chatbot stress
- `coachPolicyId` application coaching mode
- admin review paths
- session delete in the official run
- email delivery
- password reset

The official run intentionally stayed below the default `5 / 60s` per-user chat message limit.

## Method

A dedicated script was added:

```bash
ENV_FILE=.env.runtime.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL=https://youthmoa.kr \
APP_HEALTH_URL=http://127.0.0.1:8082/actuator/health \
CHAT_FLOW_ROOT=tmp/performance/chat-flow \
CHAT_FLOW_QUESTION_COUNT=3 \
CHAT_FLOW_REQUEST_DELAY_SECONDS=2 \
  bash deploy/performance/run-local-chat-flow-baseline.sh
```

Important implementation details:

- test email is generated with a dedicated `chat.flow.perf` prefix
- email verification is seeded in Redis instead of sending mail
- one test account is used to keep rate-limit and OpenAI cost bounded
- messages are sent sequentially in one chat session
- raw response artifacts are sanitized before publishing to `latest`
- DB row counts are captured before and after the measured flow

Per-message metrics:

- HTTP status
- duration
- error code
- answer mode
- clarification flag
- answer length
- reference count
- action link count
- branch suggestion count

## Pre-Check

Before the official chatbot run:

- contract check with one question: passed
- ALB target health: `2` healthy targets
- public policy list/search/ranking smoke: HTTP `200`, count `5`
- nginx recent user-facing 5xx: `0`
- DB waiting locks: `0`
- DB active queries over 5 minutes: `0`
- JVM health: HTTP `200`
- JVM restart count: `0`
- JVM memory before run: `635.5 MiB / 1 GiB`
- JVM recent exception count: `0`

The generic DB audit wrapper could not run with the migration credential available on this host, so the lock and long-query checks were executed directly with the app DB read credentials.

## Contract Check

Before the official run, the script was executed with `CHAT_FLOW_QUESTION_COUNT=1`.

Result:

| Step | Status | Duration | Error | Response shape |
| --- | ---: | ---: | --- | --- |
| create session | 200 | 66.8ms | - | session created |
| send message | 200 | 4158.1ms | - | `POLICY_GROUNDED`, references `1`, clarification `false` |
| get messages | 200 | 60.2ms | - | message count `2` |

Artifact:

- `tmp/performance/chat-flow-contract/20260716T172903Z`

Observation:

- the chatbot contract and response parser were correct before the official three-question run
- the measured message path is OpenAI-influenced and cost-bearing in this runtime

## Official Result

Summary:

- account count: `1`
- question count: `3`
- total measured steps: `5`
- success count: `5`
- message-send success count: `3 / 3`
- total errors: `0`
- total rate-limit responses: `0`
- clarification responses: `0`
- answer mode counts: `{"POLICY_GROUNDED": 3}`

Representative questions:

1. `청년 취업 지원 정책을 간단히 알려줘`
2. `인천 중구 청년이 받을 수 있는 주거 지원을 알려줘`
3. `신청하려면 어떤 서류와 절차를 준비해야 해?`

Latency and response shape:

| Step | Status | Duration | Error | Answer mode | Clarification | References | Action links | Branch suggestions |
| --- | ---: | ---: | --- | --- | --- | ---: | ---: | ---: |
| create session | 200 | 51.0ms | - | - | - | - | - | - |
| send question 1 | 200 | 4195.5ms | - | `POLICY_GROUNDED` | false | 1 | 0 | 0 |
| send question 2 | 200 | 3378.5ms | - | `POLICY_GROUNDED` | false | 1 | 0 | 0 |
| send question 3 | 200 | 3273.9ms | - | `POLICY_GROUNDED` | false | 1 | 0 | 0 |
| get messages | 200 | 25.8ms | - | - | - | - | - | - |

Aggregate latency:

| Metric | Value |
| --- | ---: |
| send p50 | 3378.5ms |
| send p95 | 4113.8ms |
| send p99 | 4179.1ms |
| send max | 4195.5ms |
| send average | 3615.9ms |
| all-step p95 | 4032.1ms |

Artifact:

- `tmp/performance/chat-flow/20260716T173010Z`
- latest: `tmp/performance/chat-flow/latest`
- latest summary: `tmp/performance/chat-flow/latest-chat-flow-summary.txt`
- latest JSON: `tmp/performance/chat-flow/latest-chat-flow-summary.json`

Official run DB delta:

| Table | Before | After | Delta |
| --- | ---: | ---: | ---: |
| users | 1376 | 1377 | +1 |
| chat_sessions | 12 | 13 | +1 |
| chat_messages | 38 | 44 | +6 |
| chat_retrieval_snapshots | 337 | 340 | +3 |

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
- JVM memory after run: `636.1 MiB / 1 GiB`
- JVM recent exception count: `0`
- log alert after AI-path classification fix: `ok`

During the first post-check, log alert returned `critical` because chat message latency was counted as ordinary interactive API p95. That was a classification issue, not an application failure:

- chat message endpoints call external AI and normally take seconds
- recommendation refresh was already excluded from ordinary interactive p95 for the same reason
- the evaluator now excludes prefix `POST /api/chat/sessions/` from ordinary p95 and keeps those lines as `excluded_latency`
- session create/list/delete remain ordinary API paths

Validated evaluator output after the fix:

```text
LOG_ALERT_STATUS=ok
excluded_latency=POST /api/chat/sessions/250/messages p95 4148ms count 1
excluded_latency=POST /api/chat/sessions/251/messages p95 3363ms count 2
```

The threshold rule is documented in [log-alert-thresholds.md](../core/log-alert-thresholds.md).

## Data Mutation

This measurement created real production test data:

- contract-check accounts: `1`
- official measurement accounts: `1`
- official chat sessions: `+1`
- official chat messages: `+6`
- official chat retrieval snapshots: `+3`

The accounts use a dedicated generated prefix derived from `chat.flow.perf`. They were left in place as identifiable test data rather than manually deleting rows, because cleanup should go through supported user withdrawal behavior when required.

## Interpretation

The public chatbot flow passed the first report-grade representative-latency measurement.

Accepted statements from this pass:

- session creation, three message sends, and message history read all returned HTTP `200`
- there were `0` application errors
- there were `0` rate-limit responses
- all three representative answers were `POLICY_GROUNDED`
- each answer returned one policy reference
- no response required clarification
- the message-send representative p95 was `4113.8ms`
- the slowest message took `4195.5ms`
- message history read returned `6` messages after three user/assistant turns
- no post-measurement evidence showed ALB target failure, app restart, DB waiting locks, long-running DB work, or user-facing nginx 5xx

Limitations:

- this is a first one-account chatbot-flow pass, not a broad three-run sample
- OpenAI latency and availability are included in the message-send numbers
- this is not a maximum chatbot throughput test
- it does not evaluate answer correctness beyond response shape signals
- the three questions were representative, not an exhaustive prompt set

Current report position:

- use this document for representative chatbot latency and response-shape evidence
- do not merge chatbot message latency into ordinary read API p95
- use the read API and auth documents for non-AI request latency
- run the integrated user journey separately before making whole-flow duration claims

## Next Step

Recommended next measurement order:

1. run Group E integrated user journey with one or a small number of controlled accounts
2. record step-level timing across login, read API, recommendation refresh, bookmark, chat, and logout
3. avoid high-RPS AI stress unless cost and operational risk are explicitly accepted
