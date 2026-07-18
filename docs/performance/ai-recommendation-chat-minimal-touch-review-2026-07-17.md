# AI Recommendation And Chat Minimal-Touch Review 2026-07-17

## Purpose

This review covers the slow AI-backed paths:

- recommendation refresh
- recommendation AI scoring
- chatbot message send
- chatbot semantic retrieval and answer generation

The goal is not to make answers faster by weakening quality. The safe lane is:

- measure stage timing first
- avoid work that provably does not affect the final candidate set
- reuse only exact same inputs
- keep prompt, weight, candidate count, and model behavior unchanged unless separately approved

## Current Baseline

Recommendation:

- stored recommendation reads are fast, usually tens of milliseconds
- non-personal first refresh is still OpenAI-backed and takes about `4s-7s`
- async non-personal refresh already improves user-facing wait: last verified async trigger returned in `221ms`, while background generation finished in `6889ms`
- same-user non-personal refresh cache works when reused immediately:
  - first refresh: `4588.9ms`
  - cached refresh: `58.6ms`
  - same `SCORED=12`, `NOT_REQUESTED=27`

Chat:

- report-grade chat flow on `2026-07-16`:
  - message p50: `3378.5ms`
  - message p95: `4113.8ms`
  - max: `4195.5ms`
  - `0` errors
  - `0` rate-limit responses
  - all answers: `POLICY_GROUNDED`
  - each answer returned one reference
- integrated user journey chat message was also around `3260.7ms` to `3535.8ms`

Policy/chunk data:

- `policy_chunks_total=58912`
- `policy_chunks_embedded=58912`

This means semantic retrieval can use vector search for every policy chunk, and every chatbot message can incur query embedding work before OpenAI answer generation.

## Recommendation Findings

Safe existing behavior:

- `GET /api/recommendations` reads saved rows only and does not call AI.
- `POST /api/recommendations/refresh-async?personal=false` keeps the same generation pipeline but avoids blocking the UI.
- same-user non-personal refresh cache is working.

Do not do:

- do not enable `cluster_ai_results` reuse for `clusterId=youth_all`.
- `ClusterService` intentionally returns `youth_all`; AI prompt includes user category fields such as region, income, and employment.
- Reusing a single `youth_all` AI score/reason across profiles can improve speed but can mix another profile's AI judgment into the current user.

Lowest-risk next work:

1. Add AI gateway stage timing and prompt-hash observability.
2. Observe exact prompt duplication rate.
3. Only if the duplicate rate is material, add exact prompt-hash AI result cache behind a feature flag.
4. Keep personal refresh synchronous unless UX requires async; if added, personal async must call the same `recommend(userId, true)` path.

## Chat Findings

Current chatbot send path:

1. save user message
2. load recent messages and session context
3. resolve branch/context
4. retrieve policy candidates
5. load grounding evidence
6. call OpenAI chat completion
7. persist assistant answer and retrieval snapshot

The chatbot can call OpenAI twice in one user message:

- query embedding through `OpenAiChatEmbeddingGateway`
- answer generation through `ChatAiGateway`

Important code finding:

- `PolicyExplorationService.traceChatCandidates(...)` always calls semantic search after FTS search.
- However, when FTS already fills the requested candidate limit, `semanticAddLimit` becomes `0`.
- In that case semantic candidates do not affect final user-facing candidates, but the current code can still pay the embedding/vector search cost.

This creates a quality-preserving optimization candidate:

- defer semantic search until it can actually contribute to final candidates
- if FTS already has `condition.limit()` candidates, skip semantic search
- final candidate IDs should remain identical in that branch
- retrieval snapshot `semantic_service_ids_json` would become empty for skipped semantic search, so the change should record an explicit trace marker or be documented as `semantic_skipped_full_fts`

Expected effect:

- should reduce chatbot latency only on questions where FTS already fills the answer candidate window
- it will not remove the main OpenAI answer-generation call
- likely improvement is partial, not a full `4s -> 100ms` change
- actual saving must be measured because current logs do not split embedding, vector search, grounding, and answer-generation durations

## Quality Impact Prediction

This is a pre-implementation estimate. Treat it as a decision aid, not as proof.

Snapshot-based chat retrieval contribution, last 60 days:

| snapshot type | snapshots with results | final candidates fully from FTS | estimated lazy-skip-safe share |
| --- | ---: | ---: | ---: |
| `INTERACTIVE` | `17` | `9` | `52.9%` |
| `EVALUATION` | `279` | `65` | `23.3%` |

Interpretation:

- interactive sample is small, but it shows a real safe lane where semantic retrieval did not contribute to final candidates
- evaluation sample is broader and shows semantic retrieval often matters
- therefore lazy semantic search must be narrow: skip only when FTS/region keyword candidates already fill the final candidate window
- do not skip semantic just because there are "some" FTS results or because `minResultCount` is satisfied

Predicted quality impact by option:

| Option | Predicted quality impact | Confidence | Main reason |
| --- | --- | --- | --- |
| stage timing logs | none | high | no behavior change |
| recommendation AI prompt/hash observability | none | high | no behavior change |
| lazy chat semantic search with strict full-FTS guard | none to very low on eligible requests | medium-high | final candidate IDs should remain identical when semantic add limit would be `0` |
| exact chat query embedding cache | very low with short TTL | medium | same redacted query/model/dimensions should produce the same retrieval contract, but provider-side model drift is possible |
| exact recommendation AI prompt-result cache | low, only if exact prompt hash and short TTL | medium | same prompt would have been sent, but live AI variability is frozen during TTL |
| personal recommendation async | none for final quality | high | same generation path, only wait pattern changes |
| chat answer exact prompt cache | medium | low-medium | can freeze stale or mediocre answers and interacts with conversation memory |
| prompt compaction | medium | low | can remove safety wording or evidence that affects model behavior |
| recommendation `top-n` reduction | high | high | directly increases `NOT_REQUESTED` exposure and can remove AI scoring from visible cards |
| chat candidate limit reduction | high | high | can remove the correct policy before answer generation |
| model change | medium to high | low | latency can improve, but answer style/correctness can shift unpredictably |

Verification gates before accepting a behavior change:

- chat final candidate service IDs unchanged for lazy-skip branches
- chat `answerMode`, `needsClarification`, reference IDs, and action-link counts unchanged or explicitly explained
- recommendation top `20` service IDs unchanged for exact-cache verification cases
- recommendation `aiStatus` distribution unchanged for exact-cache verification cases
- no prompt/candidate/model change accepted without a separate quality run

## Minimal-Touch Options

### 1. Stage Timing Instrumentation

Recommended first step.

Add structured stage timings for:

- recommendation AI call duration
- recommendation prompt hash, requested candidate count
- chat candidate retrieval duration
- chat semantic embedding duration
- chat vector search duration
- chat grounding duration
- chat answer OpenAI duration
- chat total duration

Why first:

- no quality change
- avoids guessing which AI substep matters
- gives before/after proof for later changes

Implementation on `2026-07-18`:

- added `ChatConversationTiming` at the chat orchestration boundary
- added `ChatSemanticSearchTiming` for embedding count, query embedding, vector search, service load, and total semantic search time
- added `ChatAiTiming` for chat OpenAI answer generation, with `promptSha256`, candidate count, evidence count, `openAiDurationMs`, and total gateway duration
- added `RecommendationAiTiming` for recommendation OpenAI scoring, with prompt hash, total candidates, requested top-N candidates, result count, OpenAI duration, and total gateway duration
- added `RecommendationAiScoringTiming` for cache bypass/hit/miss timing around the recommendation scoring service

Behavior boundary:

- no prompt text, user question, raw evidence, email, user key, or personal identifier is logged
- prompt visibility is limited to SHA-256 hash for duplicate-rate analysis
- no candidate limit, scoring weight, prompt wording, model, cache behavior, or fallback behavior was changed
- expected quality impact is `none`; any changed answer/reference/ranking after this point should be treated as unrelated runtime variability or a regression to investigate

Measurement use:

- if `ChatConversationTiming.totalDurationMs` is high and `ChatAiTiming.openAiDurationMs` dominates, answer generation is the main bottleneck
- if `ChatSemanticSearchTiming.embeddingDurationMs` or `vectorDurationMs` is material and `semanticCandidates` do not affect final candidates, lazy semantic search remains the next low-risk candidate
- if `RecommendationAiTiming.openAiDurationMs` dominates recommendation refresh, prompt-cache viability depends on repeated `promptSha256` frequency, not on `clusterId=youth_all`
- if `RecommendationAiScoringTiming.cacheMode=bypass-youth-all`, cluster cache should still be treated as intentionally bypassed unless the cache key is changed to the full prompt signature

### 2. Lazy Chat Semantic Search

Recommended first behavior change after instrumentation, or can be tested with tight unit coverage.

Change:

- run FTS first
- if FTS/region merged candidates already fill `condition.limit()`, skip semantic query embedding/vector search
- otherwise run semantic search as today

Quality boundary:

- final candidates should be unchanged when semantic add limit would have been `0`
- do not reduce candidate limit
- do not change preferred category, branch terms, region ordering, prompt, or answer model

Verification:

- add unit test proving semantic search is not called when FTS already fills the limit
- add unit test proving semantic still runs when FTS is short
- run chat flow baseline and compare answer mode/reference count
- run existing chat continuity/coaching smoke if corpus is available

### 3. Exact Query Embedding Cache

Possible second behavior change.

Cache query embedding by:

- redacted semantic query
- embedding model
- embedding dimensions

Why relatively safe:

- same redacted text and same embedding model should produce the same retrieval vector contract
- no prompt or answer behavior changes

Risks:

- OpenAI embedding output versioning can drift under the same model name
- cache TTL should be short and feature-flagged
- do not persist fallback/local embeddings as if they were OpenAI embeddings

### 4. Chat Answer Cache

Do not start here.

Even exact prompt caching can be product-sensitive because chat answer freshness, conversation memory, and current policy evidence are user-visible. It may be valid later for exact same prompt hash with a very short TTL, but only after prompt duplication rate is measured.

### 5. Prompt Compaction / Candidate Reduction / Model Change

Do not start here.

These can reduce latency and cost, but they directly risk answer quality:

- fewer policy candidates can remove the correct policy
- shorter evidence can remove application details
- prompt wording changes can alter safety and answer style
- model changes can alter answer correctness

These belong behind a separate quality evaluation, not this minimal-touch lane.

## Recommended Order

1. Add stage timing observability for chat and recommendation AI boundaries.
2. Implement lazy chat semantic search only for the branch where semantic cannot affect final candidates.
3. Re-measure report-grade chat flow with the same three questions.
4. If embedding time is still material, add exact query embedding cache behind a feature flag.
5. For recommendation, keep non-personal async and same-user cache as-is; add prompt-hash observability before any AI result cache.
6. Defer prompt/candidate/model changes until quality gates are explicitly opened.

## Expected Impact Summary

| Area | Change | Quality risk | Expected effect |
| --- | --- | --- | --- |
| recommendation non-personal refresh | already async + same-user cache | low | user click returns fast; generation still seconds |
| recommendation AI prompt cache | observe first | low if exact hash, but not proven useful | depends on duplicate rate |
| chat stage timing | add logs only | none | no speedup, better decisions |
| chat lazy semantic search | skip semantic only when FTS already fills final window | low | saves embedding/vector search on eligible questions |
| chat query embedding cache | exact redacted query cache | low-medium | saves embedding call on repeated queries |
| chat answer cache | exact prompt cache | medium | possible, but product-sensitive |
| prompt/candidate/model changes | not in this lane | high | can be faster but may reduce answer quality |
