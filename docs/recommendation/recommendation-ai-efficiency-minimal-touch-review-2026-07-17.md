# Recommendation AI Efficiency Minimal-Touch Review 2026-07-17

## Purpose

This review looks for recommendation/AI efficiency work that can improve latency or OpenAI call volume without changing recommendation quality.

The current recommendation reopen gate is still closed for quality-changing work. Therefore this review does not reopen:

- candidate retrieval window
- source/category balancing
- rule or AI weights
- `recommend.ai.top-n`
- prompt semantics or candidate ordering

## Current Production Readings

Read-only DB summary from `recommendation_run_logs`, last 7 days:

| personal | outcome | runs | avg | p50 | p90 | max | avg saved |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `false` | `cache_hit` | `3` | `18ms` | `14ms` | `29ms` | `29ms` | `39.3` |
| `false` | `saved` | `130` | `5091ms` | `4762ms` | `6818ms` | `10373ms` | `40.1` |
| `true` | `saved` | `4` | `5653ms` | `4639ms` | `7059ms` | `7059ms` | `39.8` |

AI status summary for saved runs, last 7 days:

| personal | runs | `SCORED` | `NOT_REQUESTED` | saved | scored/saved |
| --- | ---: | ---: | ---: | ---: | ---: |
| `false` | `130` | `1560` | `3650` | `5210` | `29.9%` |
| `true` | `4` | `48` | `111` | `159` | `30.2%` |

Last 30 hours still had `35` non-personal saved generation runs with avg `5031ms`, so the slow path is current.

## Code Findings

Current safe parts:

- stored recommendation reads do not call AI
- `POST /api/recommendations/refresh-async?personal=false` keeps the same generation pipeline but avoids blocking the UI
- non-personal same-user refresh can reuse the saved DB batch for `recommend.refresh-cache-ttl-minutes`, default `15`
- personal refresh intentionally bypasses that reuse marker

Important cache finding:

- `cluster_ai_results` currently has `0` rows in production.
- `app_core_rw` has `SELECT`, `INSERT`, and `UPDATE` permission on `cluster_ai_results`; `DELETE` is correctly revoked.
- Recent app logs show `personal=false` runs using `clusterId=youth_all`.
- `AiScoringService` bypasses cluster AI cache whenever `clusterId` is `youth_all`.

This is not a simple "turn cache on" bug. `ClusterService` intentionally returns only `youth_all` during the current operating phase. The AI prompt includes user category fields such as age band, region, income, and employment. If `cluster_ai_results` were reused only by `clusterId=youth_all`, AI scores and reasons generated for one profile could be reused for another profile. That would improve latency but can degrade recommendation quality.

## Minimal-Touch Options

### 1. Exact AI Prompt-Hash Cache

Recommended first implementation target.

Cache only when the exact AI request contract is identical:

- model
- system prompt hash
- user prompt hash
- temperature
- max tokens
- response format
- replay seed, when present
- selected candidate list and user category values implicitly included through the user prompt hash

Store the parsed AI result or raw JSON content in Redis with a short TTL, for example `25h`, using a versioned key such as `recommend:ai-prompt-result:v1:<hash>`.

Why this preserves quality:

- a cache hit means the same prompt would have been sent to OpenAI
- no candidate is added or removed
- no score/weight/prompt semantics change
- different user profile text or different top candidate list produces a different hash and misses the cache

Expected effect:

- cache hits should avoid the OpenAI network call, which is the dominant part of the `3s-10s` saved-generation path
- full benefit depends on exact prompt duplication rate, which is currently not measured because replay trace logging is disabled in production
- first deployment must report hit/miss counts before claiming a real-user percentage improvement

Risk:

- OpenAI output can vary between identical calls because temperature is `0.3`; caching makes repeated identical prompts reuse the first successful result during the TTL
- this is acceptable only if the product treats identical prompt refreshes as stable enough for short-lived reuse

Rollback:

- feature flag: `recommend.ai.prompt-cache.enabled=false`
- deleting Redis keys is sufficient; no DB migration needed if Redis is used

### 2. AI Call Timing And Prompt-Hash Observability

Low-risk support step, useful with or before option 1.

Add logs or run-log fields for:

- AI gateway duration
- prompt hash
- requested candidate count
- cache hit/miss when prompt cache exists
- OpenAI response result count

Why this preserves quality:

- no behavioral change
- lets us measure whether option 1 is actually worth keeping

Expected effect:

- no direct speedup
- reduces guesswork before touching AI/candidate behavior

### 3. Personal Refresh Async UX

Quality-preserving but not actual generation efficiency.

The current `/refresh-async` intentionally rejects `personal=true`. Personal refresh still blocks for the full generation path. Adding a personal async path would preserve final recommendation quality if it calls the same `RecommendationGenerationService.recommend(userId, true)` path.

Expected effect:

- user wait pattern improves like non-personal async
- OpenAI cost and background generation duration are unchanged

Risk:

- product semantics must be explicit because personal refresh evicts the non-personal reuse marker
- status UI must distinguish personal and non-personal refresh states

### 4. Prompt Payload Compaction

Do not start here.

Removing duplicated safety text or compacting labels may reduce tokens, but it changes the actual prompt. Even if the intent is the same, model output can shift. This requires top-20 service ID, AI status, score ordering, and reason-quality comparison before deployment.

### 5. Candidate/Top-N Reduction

Blocked for now.

Lowering `recommend.ai.top-n` or reducing candidates would likely improve speed but directly risks dropping important policies or increasing visible `NOT_REQUESTED` cards. This belongs behind the recommendation reopen gate, not the minimal-touch lane.

## Recommendation

Proceed in this order:

1. Implement exact prompt-hash AI result cache behind a feature flag, with hit/miss/duration logging.
2. Verify on local/prod smoke profiles that top `20` service IDs and `aiStatus` distribution are unchanged on cache hits.
3. Re-run recommendation flow measurement and compare saved-generation duration for first call versus identical second call after disabling the same-user refresh marker if needed.
4. Keep cluster-wide `youth_all` AI cache disabled unless cluster segmentation is reintroduced or the cache key includes the full user/prompt signature.

Do not enable `cluster_ai_results` reuse for `youth_all` as a shortcut.
