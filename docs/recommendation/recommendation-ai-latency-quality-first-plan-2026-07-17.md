# Recommendation AI Latency Quality-First Plan 2026-07-17

## Purpose

This document reopens the recommendation latency question without reopening recommendation quality tuning.

The user-facing problem is real: recommendation refresh is slow because it can include OpenAI scoring. However, the existing recommendation documents intentionally keep score, weight, prompt, source balancing, and candidate tuning closed unless the recommendation gates allow reopening.

Therefore this plan separates:

- safe latency/UX work that preserves the current recommendation result contract
- quality-changing work that stays blocked until the recommendation reopen gate says it is allowed

## Documents Checked

Read these before deciding the plan:

- [recommendation-docs-index.md](./recommendation-docs-index.md)
- [recommendation-current-state.md](./recommendation-current-state.md)
- [recommendation-reopen-decision-runbook.md](./recommendation-reopen-decision-runbook.md)
- [recommendation-operation-checklist.md](./recommendation-operation-checklist.md)
- [recommendation-pipeline.md](./recommendation-pipeline.md)
- [recommendation-standard-code-coverage-and-observation-closeout.md](./recommendation-standard-code-coverage-and-observation-closeout.md)
- [report-grade-recommendation-flow-measurement-2026-07-16.md](../performance/report-grade-recommendation-flow-measurement-2026-07-16.md)

Current live check:

```bash
ENV_FILE=.env.runtime.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
OBSERVATION_ROOT=tmp/recommendation-observation/design-review-20260717 \
KEEP_ARTIFACTS=true \
RUN_HOUSING_STANDARD_CODE_EFFECT_AUDIT=false \
RUN_WELFARE_STANDARD_CODE_MATRIX_AUDIT=false \
RUN_RECOMMENDATION_STANDARD_CODE_ADOPTION_AUDIT=true \
  bash deploy/smoke/run-local-recommendation-observation-suite.sh
```

Result:

- artifact: `tmp/recommendation-observation/design-review-20260717/20260717T071747Z`
- `precheck_status=KEEP_OBSERVING`
- `gate_action_class=KEEP_BASELINE_MONITORING`
- `reopen_allowed=false`
- `review_gate_policy_promotion_status=KEEP_PRIMARY_BASELINE`
- `review_gate_policy_promotion_execution_status=DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW`
- `operator_reading=Recommendation code stays closed. Keep daily observation until real-user traffic and leader signal grow.`

Notes:

- real-user readiness was skipped because an admin password was not available in the runtime env.
- standard-code adoption output was empty because the runtime env path attempted a `migration_admin` DB login that failed. This does not change the recommendation reopen reading, which came from the latest overview/precheck.

## Current Measured Problem

From the report-grade recommendation flow:

| Path | Observed result |
| --- | ---: |
| stored recommendation read before refresh | `49.3ms`, `0` rows for new test user |
| shared refresh | `5437.4ms`, `39` rows, `12` `SCORED`, `27` `NOT_REQUESTED` |
| stored read after refresh | `116.4ms`, `20` rows |
| cached shared refresh | `209.5ms`, reused saved batch |
| personal refresh | `3933.2ms`, `39` rows, `12` `SCORED`, `27` `NOT_REQUESTED` |
| stored read final | `46.6ms`, `20` rows |

Interpretation:

- stored reads are already fast
- refresh is slow because generation includes candidate retrieval, rule scoring, AI top-N scoring, reranking, persistence, and log creation
- reducing AI top-N or changing candidate selection can improve speed but can also degrade recommendation quality

## Existing Code Contract

Current API:

- `GET /api/recommendations?size=N`
  - reads stored `user_recommendations`
  - does not call AI
  - returns `RecommendationResponse`

- `POST /api/recommendations/refresh?personal=false`
  - synchronous
  - uses rate limit
  - if recent non-personal refresh marker exists, reads the marked DB batch and skips generation
  - otherwise runs the full generation pipeline and waits
  - marks the generated batch reusable for `recommend.refresh-cache-ttl-minutes`, default `15`

- `POST /api/recommendations/refresh?personal=true`
  - synchronous
  - evicts non-personal reuse marker
  - uses `youth_all`
  - bypasses cluster AI cache and performs personal AI scoring
  - does not mark the result reusable

Existing safety mechanisms:

- per-user execution lock through `RecommendationExecutionGuard`
- refresh rate limits for shared/personal refresh
- AI top-N limit, currently default `12`
- refresh cache marker that stores `recommendedAt`, not the payload
- rule-version flag included in refresh cache key
- stored rows remain the source of truth for display

## Second Design Review

The direction remains valid, but the implementation must be narrower than a generic async job.

The service now runs behind an ALB with two application instances. Therefore refresh status must not be stored only in
process memory. An in-memory status map would make `POST /refresh-async` on instance A and
`GET /refresh-status` on instance B disagree. The refresh job status should be stored in Redis, keyed by the hashed
user key and refresh mode.

The existing collect async pattern is useful as a shape, but recommendation should not copy its in-memory snapshot
storage. Recommendation needs a small Redis-backed status record instead.

Minimum status fields:

- `state`: `IDLE`, `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `RATE_LIMITED`
- `personal`: whether the job is personal refresh
- `requestedAt`, `startedAt`, `finishedAt`
- `latestRecommendedAt`
- `savedCount`
- `errorCode`: sanitized code only, no raw OpenAI output or profile details
- `generationDurationMs`: background generation time, when known

Important constraints:

1. The background job must call the existing `RecommendationGenerationService.recommend(userId, personal)` path.
   Do not split out a faster private path for async refresh.
2. `RecommendationExecutionGuard` remains the per-user generation lock.
3. The async executor must be bounded. A small fixed pool is enough because every generation can hit OpenAI and RDS.
   With two ALB instances, effective parallelism is `per-instance parallelism * 2`.
4. Status polling must not trigger generation, rate limits, or AI calls.
5. Repeated refresh clicks while the same user's job is active should return the active status and latest saved rows.
   They should not enqueue duplicate work.
6. The frontend already bookmarks recommendation cards through `/api/policies/{serviceId}/bookmark`, not the
   recommendation row id. Keep that behavior. Async refresh replaces `user_recommendations` rows, so service-id based
   bookmark actions are safer while a refresh is running.
7. Do not disable card interactions globally during background refresh. Keep existing recommendations visible, but make
   the refresh state explicit and reload the list only after `SUCCEEDED`.

Quality review:

- The proposed async design does not make OpenAI faster.
- It does not reduce candidate count, persisted recommendation count, AI top-N, prompt content, source balancing, or
  category logic.
- It changes only the user wait pattern: existing saved recommendations stay visible while the same generation pipeline
  runs in the background.

## Why Not Just Make AI Faster

Do not start with these:

1. Lowering `recommend.ai.top-n`
   - It would reduce OpenAI latency and cost.
   - But previous docs say top-N was raised to reduce `NOT_REQUESTED` ratio in top cards and improve visible memo quality.
   - This is a quality-changing experiment, not a safe latency fix.

2. Changing candidate window size or source/category balancing
   - This can drop important policies before ranking.
   - The user already called out the risk: a fast but worse recommendation is not useful.
   - Reopen runbook says candidate/source/global tuning is blocked while `reopen_allowed=false`.

3. Caching personal AI scores broadly
   - Personal refresh intentionally uses user profile context and bypasses the cluster cache.
   - Broad cache reuse risks serving reasons/scores that no longer match the user profile or rule version.

4. Returning only a smaller number of generated recommendations
   - The current refresh creates about `39` rows and read returns top `20`.
   - Reducing persistence count can hide alternatives and make later reads/bookmarks/debugging weaker.

## Recommended Design

### Phase 1: Async Refresh UX, Same Recommendation Pipeline

Goal:

- avoid blocking the user for `4s-7s`
- keep candidate selection, rule scoring, AI top-N, reranking, and persistence unchanged

Proposed API additions:

```http
POST /api/recommendations/refresh-async?personal=false
GET  /api/recommendations/refresh-status
```

`POST /refresh-async` behavior:

1. Read latest saved recommendations first.
2. Resolve the current Redis refresh status for this user and mode.
3. If a refresh is already `QUEUED` or `RUNNING`, return `202 ACCEPTED` with current saved recommendations and the
   active state.
4. Otherwise enqueue one background refresh command using the same generation pipeline.
5. Return immediately with:
   - latest saved recommendations if present
   - empty list only for users with no saved batch yet
   - `refreshState=QUEUED` or `RUNNING`
   - `lastRecommendedAt`
   - optional `pollAfterMs`

`GET /refresh-status` behavior:

- returns `IDLE`, `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, or `RATE_LIMITED`
- includes latest `recommendedAt`, saved count, and sanitized error code if any
- does not expose raw OpenAI output or sensitive user identifiers
- reads Redis status plus the latest saved batch metadata, so it is stable across both ALB-backed instances

Frontend behavior:

- recommendation page first calls stored read
- if rows exist, show them immediately
- if user clicks refresh, keep current cards visible and show "updating" state
- poll status until `SUCCEEDED`, then reload stored recommendations
- if no rows exist, show loading skeleton plus a clear "generating recommendations" state
- continue using service-id based bookmark calls while refresh is running

Why this is safe:

- it does not change ranking, AI prompt, AI top-N, candidates, or persistence
- final saved result is identical to what synchronous refresh would have produced
- it changes waiting behavior, not recommendation quality

Implementation boundary:

- add a recommendation-specific bounded executor, for example `recommendationAsyncExecutor`
- add `RecommendationRefreshAsyncJobService` that owns Redis status and executor submission
- keep the existing synchronous `/refresh` endpoint unchanged for compatibility and comparison
- keep personal refresh synchronous at first unless UX clearly needs async personal refresh too; if personal async is
  added, it must still call `recommend(userId, true)` and keep the current personal cache-bypass behavior

Expected performance impact:

| Scenario | Current | Expected after Phase 1 |
| --- | ---: | ---: |
| existing user page load | stored read `~50-116ms` | same |
| existing user refresh click | waits `~3.9-5.4s` | immediate response `~50-200ms`, background completion still `~3.9-5.4s` |
| first user with no saved rows | waits `~3.9-5.4s` | still waits visually, but through explicit generating state |
| actual generation cost | unchanged | unchanged |
| recommendation quality | unchanged | unchanged |

### Phase 2: Refresh Status And Failure UX

Goal:

- make slow OpenAI-backed generation understandable and recoverable

Add UI states:

- `latest recommendations shown`
- `updating in background`
- `generation failed, showing previous recommendations`
- `rate-limited, try again later`
- `first recommendations are being generated`

This is product-visible quality work because it prevents stale/empty states from being misread as bad recommendations.

### Phase 3: Quality-Preserving Freshness Policy

Only after Phase 1:

- define what counts as stale for recommendations, for example older than 24h or after meaningful profile change
- auto-start background non-personal refresh when stale but never block existing read
- for profile changes, show "recommendations may be based on previous profile" until refresh completes

Do not silently replace rankings while the user is interacting with a card list. Reload or toast after completion.

## Quality-Changing Work Remains Blocked

These stay out of scope until the reopen gate allows them:

- lowering or raising `recommend.ai.top-n`
- changing candidate retrieval window
- changing source/category balancing
- changing rule/AI weights
- changing prompt/input ordering
- caching personal AI scores across different profiles
- promoting recent-window review gates into primary behavior

If one of these becomes necessary, use [recommendation-reopen-decision-runbook.md](./recommendation-reopen-decision-runbook.md) and record:

1. gate status
2. cohort/window
3. reason for reopening
4. selected lane
5. expected quality effect
6. rollback rule

## Verification Plan

Before implementation:

1. Record current state with this document.
2. Run current stored read and synchronous refresh measurement once if a fresh baseline is needed.
3. Confirm post-deploy smoke and ALB health.

After Phase 1 implementation:

Functional checks:

- existing saved recommendations are returned immediately
- refresh job transitions `QUEUED/RUNNING -> SUCCEEDED`
- final stored recommendations match the synchronous pipeline contract
- concurrent refresh returns existing saved data or running status, not duplicate generation
- rate-limit behavior remains enforced
- personal refresh still bypasses non-personal marker and does not reuse cluster cache unexpectedly

Performance checks:

- async request response time
- status poll response time
- background generation duration
- stored read after completion
- OpenAI-backed generation duration unchanged but no longer blocks main interaction

Quality checks:

- compare top `20` service IDs before/after for the same profile and same mode
- compare `aiStatus` distribution (`SCORED`, `NOT_REQUESTED`, fallback statuses)
- compare `finalScore` order for the saved batch
- run integrated recommendation flow and final user journey smoke
- run recommendation observation precheck and confirm `reopen_allowed=false` still means quality tuning is closed

Operational checks:

- post-deploy smoke with ALB target health
- DB waiting locks `0`
- active queries over 5 minutes `0`
- JVM restart count `0`
- no user-facing nginx 5xx

## Phase 1 Implementation 2026-07-17

Implemented the quality-preserving async refresh path.

Backend:

- added `POST /api/recommendations/refresh-async?personal=false&size=N`
- added `GET /api/recommendations/refresh-status`
- added Redis-backed refresh status and active-job keys
- added bounded `recommendationAsyncExecutor`, default parallelism `1` per app instance
- background work calls the existing `RecommendationGenerationService.recommend(userId, personal)` path
- existing synchronous `POST /api/recommendations/refresh` remains unchanged
- `personal=true` is intentionally rejected on `/refresh-async`; personal refresh stays synchronous for now

Frontend:

- regular recommendation refresh now calls `/refresh-async`
- existing saved cards remain visible while background generation runs
- UI polls `/refresh-status`
- after `SUCCEEDED`, the page reloads stored recommendations through `GET /api/recommendations`
- personal refresh still uses the existing synchronous `/refresh?personal=true` path

Quality boundary:

- no change to candidate retrieval
- no change to `recommend.ai.top-n`
- no change to OpenAI prompt/input ordering
- no change to rule/AI weights
- no change to source/category balancing
- no change to persisted recommendation count

Verification:

- `./gradlew clean compileJava` passed
- `./gradlew test --tests com.example.welfare.api.RecommendationPolicyFlowWebMvcTest --tests com.example.welfare.recommend.service.RecommendationRefreshAsyncJobServiceTest` passed
- `npm run build` passed
- `npm run lint` passed
- `npm run test:unit` passed, `50` tests
- full backend `./gradlew test` executed `1286` tests but failed on two existing unrelated contract drifts:
  - `WelfareServiceMapperTest.regionsFromGov24_extractsUniqueSggStemFromAgencyName`
  - `AwsOpsMonitorPolicyContractTest.policyAllowsReadingProductionRdsMetadata`

Deployment:

- commit `bfaad94e` deployed to primary and secondary app instances
- primary local actuator returned `{"status":"UP"}`
- secondary `i-0e8a4cc599c1148c8` pulled `bfaad94e` and returned `{"status":"UP"}`
- `RUN_ALB_TARGET_HEALTH=true bash deploy/smoke/run-prod-post-deploy-smoke.sh` passed
- artifact: `tmp/prod-post-deploy-smoke/20260717T080200Z`
- ALB target health: `2` healthy targets
- public policy list/search/ranking: HTTP `200`, count `5`
- nginx user-path 5xx: `0`

Post-deploy async verification:

- async runtime check artifact: `tmp/recommendation-async-runtime-check/20260717T082749Z`
- `POST /api/recommendations/refresh-async?size=6`: HTTP `202`, initial state `RUNNING`
- initial saved recommendations for the new smoke user: `0`
- async request response time: `221ms`
- status polling: `6` polls
- final state: `SUCCEEDED`
- total time until final state: `6889ms`
- final stored recommendation count read by `GET /api/recommendations?size=6`: `6`
- top 6 AI status counts: `SCORED:5`, `NOT_REQUESTED:1`

Quality-preserving comparison:

- comparison artifact: `tmp/recommendation-async-quality-compare/20260717T082855Z`
- async saved read count compared: `20`
- synchronous `/refresh` response after async completion returned full saved batch count: `40`
- compared top `20` service IDs: equal
- compared top `20` AI statuses: equal
- top `20` AI status counts: `SCORED:11`, `NOT_REQUESTED:9`

Interpretation:

- user-facing refresh click no longer blocks for the whole OpenAI-backed generation path
- current measured immediate async response is `221ms`
- background generation still took about `6.9s` for this smoke user, which is expected because the recommendation
  pipeline and OpenAI scoring were intentionally left unchanged
- the quality contract held for the top `20` comparison against the synchronous refresh readback

Post-deploy recommendation observation:

- artifact: `tmp/recommendation-observation/async-refresh-postdeploy-20260717/20260717T083049Z`
- `recommendation_observation_suite=passed`
- `precheck_status=KEEP_OBSERVING`
- `decision_class=OBSERVE_REAL_USER_TRAFFIC`
- `reopen_allowed=false`
- `review_gate_policy_promotion_status=KEEP_PRIMARY_BASELINE`
- `review_gate_policy_promotion_execution_status=DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW`

Backend test drift closeout:

- `WelfareServiceMapperTest.regionsFromGov24_extractsUniqueSggStemFromAgencyName`
  - expected one derived region for `재단법인안산인재육성재단`
  - fixed by adding a Gov24 local-agency-name fallback that matches unique city/county/district names or stems only
    when the agency name contains a local agency marker such as `재단`, `공단`, `공사`, `장학회`, `시청`, `군청`,
    or `구청`
  - the fallback stores the parent region for type-less stem matches, so `안산` resolves to `경기도 안산시`
    instead of expanding to child districts
- `AwsOpsMonitorPolicyContractTest.policyAllowsReadingProductionRdsMetadata`
  - policy file has `ReadYouthWelfareRdsMetadata` with `Resource="*"`
  - contract was updated to match the current ops policy, which also reads automated backup and snapshot metadata
- verification:
  - targeted drift tests passed
  - full backend `./gradlew test` passed
- deployment:
  - commit `88ac6fc8` deployed to primary and secondary app instances
  - primary local actuator returned `{"status":"UP"}`
  - secondary `i-0e8a4cc599c1148c8` pulled `88ac6fc8` and returned `{"status":"UP"}`
  - `RUN_ALB_TARGET_HEALTH=true bash deploy/smoke/run-prod-post-deploy-smoke.sh` passed
  - artifact: `tmp/prod-post-deploy-smoke/20260717T085208Z`
  - ALB target health: `2` healthy targets
  - public policy list/search/ranking: HTTP `200`, count `5`
  - nginx user-path 5xx: `0`

## Decision

Proceed first with Phase 1 only.

Reason:

- it addresses the actual user-facing wait
- it preserves recommendation quality and current gate decisions
- it avoids premature candidate/AI tuning while the recommendation track still says `KEEP_OBSERVING`

Do not implement candidate reduction or AI top-N changes in the same batch.
