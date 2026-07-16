# Report-Grade Performance Measurement Plan 2026-07-16

## Purpose

This plan defines the performance measurements that can be used in the graduation result report.

It is stricter than the operational closeout checks. The closeout checks answer "is production healthy now"; this plan answers "what performance evidence can be reported, under what assumptions, and with what limits".

## Current Project Facts

- production branch: `refactor/admin-dashboard-sections`
- current production revision at planning time: `a7f733d8`
- deployment: EC2 2 app nodes behind ALB, nginx on each node, RDS PostgreSQL, ElastiCache Valkey
- public URL: `https://youthmoa.kr`
- current DB pool: `DB_POOL_MAX_SIZE=5` per app node
- public API rate limiting is implemented in Redis
- OpenAI is enabled in production, so AI-backed recommendation/chat measurements can create external latency and cost
- existing report docs: `docs/final-report-*`
- existing performance docs: `docs/performance/performance-measurement-plan.md`, `docs/performance/performance-baseline-current.md`, `docs/performance/final-load-capacity-check-2026-07-16.md`

Do not record secrets from `.env.production` or `.env.runtime.production` in report artifacts.

## Code Constraints That Affect Interpretation

### Policy API Rate Limits

The public policy API is protected by `PolicyTrafficRateLimitService`.

Default code limits:

- list: `60 / 60s`
- search: `60 / 60s`
- ranking: `30 / 60s`
- detail: `20 / 60s` per service
- suggestions: `180 / 60s`

The rate-limit actor is based on `ClientFingerprintService`, which hashes `remoteAddr + User-Agent`.

Interpretation:

- public ALB tests with one client source and one User-Agent measure a same-source operational protection boundary
- using multiple User-Agents or multiple source IPs changes the rate-limit actor and must be reported separately
- 429 means protection policy activated; it is not by itself CPU/DB/ALB saturation

### Auth Rate Limits

Login is protected by `AuthRateLimitService`.

Default code limit:

- login: `20 / 300s` per client fingerprint

Interpretation:

- login load should use a controlled test account pool
- repeated login from one source will hit auth rate-limit before measuring raw auth throughput

### Recommendation Rate Limits And AI Cost

Recommendation refresh is protected by `RecommendationRefreshRateLimitService`.

Code defaults:

- shared refresh: `3 / 60s`
- personal refresh: `3 / 600s`

Production env overrides observed at planning time:

- personal refresh max requests: `50`

The recommendation controller documents `personal=true` as a personal-profile real-time AI path. `RealtimeAiGateway` can call OpenAI unless rule-only fallback is configured.

Interpretation:

- `GET /api/recommendations` is a stored recommendation read path
- `POST /api/recommendations/refresh?personal=false` is a shared/non-personal refresh path
- `POST /api/recommendations/refresh?personal=true` is an AI-capable personal refresh path and must be measured separately
- recommendation measurements must record result count, failure count, 429/R004 count, and row growth

### Chat Rate Limits And AI Cost

Chat message send is protected by `ChatRateLimitService`.

Default code limit:

- messages: `5 / 60s` per user

Chat uses the OpenAI-backed answer path when available.

Interpretation:

- chat should be measured as representative response latency, not high-RPS stress
- use 3 to 5 representative questions and keep iteration count low
- record answer mode, reference count, action link count, p50/p95/max, and failure rate

### Data Mutation

Runtime smoke and realistic user-flow tests create real operational records:

- users/auth users/profile/PII rows
- refresh/session Redis keys
- recommendation rows
- recommendation logs
- bookmarks
- chat sessions/messages/snapshots

Interpretation:

- use a dedicated test email prefix
- record created account count and row deltas
- clean up through supported user withdrawal paths only when the test goal requires cleanup
- otherwise keep test data clearly identifiable by prefix and document it

## Measurement Groups

### Group A: Read-Only Policy API

Goal:

- report public read API latency and operational rate-limit boundary

Endpoints:

- `GET /api/policies?page=0&size=20`
- `POST /api/policies/search`
- `GET /api/policies/{firstPolicyId}`
- `GET /api/policies/ranking?size=20`
- `POST /api/policies/search/suggestions`

Method:

- public ALB base URL: `https://youthmoa.kr`
- endpoint-only steps plus mixed read profile
- each accepted step should run 3 repetitions where practical
- record p50/p95/p99/max, RPS, request count, 429, 5xx, and error rate

Current script:

```bash
APP_BASE_URL=https://youthmoa.kr \
INCLUDE_HEALTH=false \
API_LOAD_SCENARIOS=policy_search_keyword \
DURATION_SECONDS=120 CONCURRENCY=2 API_LOAD_REQUEST_DELAY_SECONDS=0.8 \
  bash deploy/performance/run-local-api-load-baseline.sh
```

Use `API_LOAD_SCENARIOS=mixed` for the mixed profile.

### Group B: Auth Flow

Goal:

- report login/refresh/profile/logout latency and auth stability

Endpoints:

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/users/me`
- `POST /api/auth/logout`

Method:

- pre-create or reuse a bounded test account pool
- keep account prefix consistent
- include trusted `Origin` and `Referer` headers for refresh/logout
- record p50/p95/p99/max, A010/429 count, 401 count, and flow success rate

Do not include password reset or email verification send in load tests because those touch email/cooldown behavior.

### Group C: Recommendation

Goal:

- report stored recommendation read latency and recommendation generation latency

Subtests:

1. stored recommendation read: `GET /api/recommendations`
2. shared refresh: `POST /api/recommendations/refresh?personal=false`
3. personal refresh: `POST /api/recommendations/refresh?personal=true`

Method:

- use test account pool with complete profiles
- measure `personal=false` and `personal=true` separately
- record result count, p50/p95/p99/max, 429/R004 count, R003 already-running count, failure rate, and DB row delta
- explicitly mark whether OpenAI-backed path was used

The report should not merge recommendation refresh numbers with read-only policy API numbers.

### Group D: Chat

Goal:

- report representative chatbot response latency and answer quality signals

Flow:

- create chat session
- send 3 to 5 representative questions
- record per-question latency
- record answer mode, reference count, action link count, and failures

Method:

- low iteration count only
- keep within `5 / 60s` per user unless using multiple test users
- do not use chat for high-RPS stress unless rate-limit policy is explicitly the object of the test

Current accepted measurement:

- [report-grade-chat-flow-measurement-2026-07-16.md](./report-grade-chat-flow-measurement-2026-07-16.md)
- artifact: `tmp/performance/chat-flow/20260716T173010Z`
- result: `3 / 3` representative questions succeeded, `0` errors, `0` rate-limit responses
- message-send p95: `4113.8ms`
- answer mode: `POLICY_GROUNDED` x `3`
- interpretation: OpenAI-influenced representative chatbot latency, not ordinary read API latency

### Group E: Integrated User Journey

Goal:

- report end-to-end user-perceived flow duration

Flow:

1. signup or login
2. profile read/update if needed
3. policy search
4. policy detail
5. recommendation refresh
6. bookmark
7. chat question
8. logout

Method:

- use existing runtime smoke behavior as the correctness contract
- add timing around each step
- record total duration p50/p95/max and step-level durations

This is a user journey measurement, not a maximum-throughput test.

Current accepted measurement:

- [report-grade-integrated-user-journey-measurement-2026-07-16.md](./report-grade-integrated-user-journey-measurement-2026-07-16.md)
- artifact: `tmp/performance/integrated-user-journey/20260716T174247Z`
- result: `18 / 18` steps succeeded, `0` errors, `0` rate-limit responses
- total measured API time: `8935.8ms`
- non-AI measured API time: `980.9ms`
- AI-backed measured API time: `7954.9ms`
- interpretation: new-user integrated journey, dominated by recommendation/chat AI latency

## Guardrails

Stop or pause measurement if any of these occur:

- repeated user-facing 5xx
- ALB target becomes unhealthy
- app container restart count increases
- DB waiting locks appear
- active query over 5 minutes appears
- p95 stays above several seconds for non-AI read paths
- log alert remains critical after the observation window clears

## Required Pre/Post Checks

Before measurement:

```bash
bash deploy/ops/run-no-cost-ops-check.sh
```

After measurement:

```bash
bash deploy/ops/run-no-cost-ops-check.sh
LOG_ALERT_NOTIFY_OK=false bash deploy/ops/send-log-alert.sh
```

Also record:

- ALB target health
- DB current connections, waiting locks, long queries
- JVM memory/restart count
- app/nginx 5xx
- test account and recommendation/chat row deltas

## Reporting Rules

Use these phrases:

- "operational policy boundary" for 429-limited results
- "single-source synthetic load" for one host/User-Agent tests
- "RPS-based active-user estimate" for converted user counts

Avoid these phrases:

- "maximum users" unless a user behavior model is explicitly stated
- "server limit" when 429 is the first failure mode
- "AI recommendation accuracy" from load-test data alone

## Initial Execution Order

1. Document protocol and script capability.
2. Run pre-check.
3. Run Group A read-only endpoint/mixed measurements.
4. Review artifacts and log alerts.
5. Decide auth/recommendation/chat test account pool size before running Group B/C/D.
6. Run Group B/C/D with controlled counts.
7. Run integrated journey timing.
8. Run final recovery check and update report evidence docs.
