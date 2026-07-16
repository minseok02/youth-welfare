# Report-Grade Auth Flow Measurement 2026-07-16

## Purpose

This document records the first report-grade measurement for Group B, the public authentication flow.

The goal is to measure the latency and correctness of the normal user authentication path through the public ALB:

1. login
2. refresh
3. profile read
4. logout

This is separate from signup, email delivery, password reset, and account-lockout testing. Those paths have different cooldowns and operational side effects.

## Runtime Context

- measurement date: `2026-07-16`
- branch: `refactor/admin-dashboard-sections`
- deployed app runtime revision: `a7f733d8`
- measurement source revision: `ac8e2ab6` plus uncommitted auth-flow script/documentation changes
- public base URL: `https://youthmoa.kr`
- topology: ALB -> 2 EC2 app nodes -> nginx -> Spring Boot app -> RDS PostgreSQL / ElastiCache Valkey
- auth login rate limit: `20 / 300s` per client fingerprint
- measurement client: one source and one measurement User-Agent
- official measurement account count: `12`
- contract-check account count: `1`

No secrets from `.env.production` or `.env.runtime.production` are recorded in this document. Measurement artifacts are sanitized so tokens, cookies, passwords, emails, and user keys are redacted.

## Measurement Scope

Included steps:

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/users/me`
- `POST /api/auth/logout`

Setup-only step:

- `POST /api/auth/signup`

Excluded from the measured latency table:

- email verification send
- password reset request/confirm
- account lockout stress
- recommendation refresh
- chatbot messages
- full integrated user journey

## Method

A dedicated script was added:

```bash
ENV_FILE=.env.production \
AUTH_FLOW_ROOT=tmp/performance/report-grade-auth-flow \
APP_BASE_URL=https://youthmoa.kr \
APP_HEALTH_URL=http://127.0.0.1:8082/actuator/health \
AUTH_FLOW_ACCOUNT_COUNT=12 \
AUTH_FLOW_REQUEST_DELAY_SECONDS=1.0 \
SMOKE_TRUSTED_ORIGIN=https://youthmoa.kr \
  bash deploy/performance/run-local-auth-flow-baseline.sh
```

Important implementation details:

- test emails are generated with a dedicated `auth.flow.perf` prefix
- signup is used only to prepare verified test accounts
- email verification is seeded in Redis instead of sending mail
- refresh/logout include trusted `Origin` and `Referer`
- refresh uses the secure refresh cookie from login
- `/api/users/me` uses the refreshed access token
- raw response artifacts are sanitized before publishing to `latest`

Interpretation:

- 429 or `A010` means auth rate-limit policy activated
- 401 means token/cookie/session handling failed for this flow
- this measurement intentionally stays below the login protection boundary
- it measures representative auth latency, not maximum login throughput

## Pre-Check

Before the official run:

- no-cost post-deploy smoke subset: passed
- ALB target health: `2` healthy targets
- public policy list/search/ranking smoke: HTTP `200`, count `5`
- nginx recent user-facing 5xx: `0`
- DB active queries over 5 minutes: `0`
- DB waiting locks: `0`
- DB idle transactions over 5 minutes: `0`
- DB connections: `25 / 191`
- database size: `555 MB`
- DB collect scheduler lock: `collect_locks_active=1`
- JVM health: HTTP `200`
- JVM restart count: `0`
- JVM memory: `630.9 MiB / 1 GiB`
- JVM recent exception count: `0`

`collect_locks_active=1` was the `collect-global` scheduler lock. It was not a PostgreSQL waiting lock and did not coincide with long-running DB queries.

## Contract Check

Before the official 12-account run, the script was executed with one account to validate request contracts.

Result:

| Step | Requests | Success | p50 | p95 | Max | Rate-limited |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| login | 1 | 1 | 419.3ms | 419.3ms | 419.3ms | 0 |
| refresh | 1 | 1 | 168.4ms | 168.4ms | 168.4ms | 0 |
| me | 1 | 1 | 81.8ms | 81.8ms | 81.8ms | 0 |
| logout | 1 | 1 | 40.5ms | 40.5ms | 40.5ms | 0 |

Artifact:

- `tmp/performance/auth-flow-contract-check/20260716T171125Z`

The contract check created one test account.

After tightening the script to avoid passing the test password as a process argument, a one-account post-fix validation also passed:

- `tmp/performance/auth-flow-contract-check-postfix/20260716T171448Z`

## Official Result

Summary:

- account count: `12`
- setup signup success: `12 / 12`
- flow success: `12 / 12`
- measured requests: `48`
- total errors: `0`
- total 429/A010: `0`

Latency:

| Step | Requests | Success | Errors | 429/A010 | p50 | p95 | p99 | Max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| login | 12 | 12 | 0 | 0 | 134.9ms | 151.0ms | 151.6ms | 151.7ms |
| refresh | 12 | 12 | 0 | 0 | 30.0ms | 130.8ms | 163.6ms | 171.8ms |
| me | 12 | 12 | 0 | 0 | 34.4ms | 75.5ms | 76.8ms | 77.1ms |
| logout | 12 | 12 | 0 | 0 | 28.0ms | 57.1ms | 58.5ms | 58.9ms |

Artifact:

- `tmp/performance/report-grade-auth-flow/20260716T171135Z`
- latest: `tmp/performance/report-grade-auth-flow/latest`
- latest summary: `tmp/performance/report-grade-auth-flow/latest-auth-flow-summary.txt`
- latest JSON: `tmp/performance/report-grade-auth-flow/latest-auth-flow-summary.json`

## Post-Check

After the official run:

- no-cost post-deploy smoke subset: passed
- ALB target health: `2` healthy targets
- public policy list/search/ranking smoke: HTTP `200`, count `5`
- nginx recent user-facing 5xx: `0`
- DB active queries over 5 minutes: `0`
- DB waiting locks: `0`
- DB idle transactions over 5 minutes: `0`
- DB connections: `26 / 191`
- database size: `556 MB`
- DB collect scheduler lock: `collect_locks_active=1`
- app log alert: `ok`
- nginx log alert: `ok`
- JVM health: HTTP `200`
- JVM restart count: `0`
- JVM memory: `632.7 MiB / 1 GiB`
- JVM recent exception count: `0`

After the post-fix one-account validation, the final no-cost post-deploy smoke subset and log alert check also passed:

- ALB target health: `2` healthy targets
- nginx recent user-facing 5xx: `0`
- DB active queries over 5 minutes: `0`
- DB waiting locks: `0`
- DB connections: `26 / 191`
- database size: `559 MB`
- app log alert: `ok`
- nginx log alert: `ok`

## Data Mutation

This measurement created real production test data:

- contract-check accounts: `1`
- official measurement accounts: `12`
- post-fix validation accounts: `1`
- total new test accounts from this task: `14`

The accounts use a dedicated generated prefix derived from `auth.flow.perf`. They were left in place as identifiable test data rather than manually deleting rows, because deletion should go through supported user withdrawal behavior when cleanup is required.

## Interpretation

The public auth flow passed the first report-grade representative-latency measurement.

Accepted statements from this pass:

- all `12 / 12` official user flows completed successfully
- all `48 / 48` measured auth requests returned HTTP `200`
- there were `0` 401 responses
- there were `0` 429 or `A010` auth rate-limit responses
- login p95 was `151.0ms`
- refresh p95 was `130.8ms`
- profile read p95 was `75.5ms`
- logout p95 was `57.1ms`
- no post-measurement evidence showed ALB target failure, app restart, DB waiting locks, long-running DB work, or user-facing nginx 5xx

Limitations:

- this is a first auth-flow pass, not a three-run sample
- it intentionally stays below the login rate-limit boundary
- it uses one source and one User-Agent
- it does not measure signup throughput, email delivery, password reset, lockout behavior, or maximum concurrent login capacity

Current report position:

- use this document for representative public auth-flow latency
- use the read API measurement document for public read latency
- measure recommendation, chatbot, and integrated user journey separately before making whole-user-flow claims

## Next Step

Recommended next measurement order:

1. repeat Group B two more times if the final report requires a 3-run auth average
2. run Group C recommendation read/shared refresh/personal refresh with explicit OpenAI-cost labeling
3. run Group D chat as a low-iteration representative latency test
4. run Group E integrated user journey after recommendation and chat are measured separately
