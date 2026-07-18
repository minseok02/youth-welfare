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

Implementation plan on `2026-07-18`:

- skip semantic search only when FTS/region keyword merge already has at least `condition.limit()` candidates
- keep `fallbackStrategy=MERGED_RESULTS` so evaluation reports do not treat this as fallback usage
- record the skip through `ChatSemanticSearchTiming outcome=skipped reason=fts_full`
- return `semanticCandidates=[]` for skipped traces, which means `semantic_service_ids_json` is empty by design for that snapshot
- do not change search keyword construction, region ordering, preferred category filtering, final candidate ordering, candidate limit, prompt, or answer model

Expected effect before deployment:

- no quality change when the guard is true, because the old `semanticAddLimit` was already `0`
- one embedding call and one vector search are avoided for eligible questions
- based on the `2026-07-18` stage timing run, a cold eligible question could save up to the observed semantic time (`~3.3s`), while warmer eligible questions may save hundreds of milliseconds
- questions where FTS is short still use semantic search unchanged

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

## Post-Instrumentation Measurement 2026-07-18

Deployment:

- commit: `69435065`
- primary local health: `UP`
- secondary instance `i-0e8a4cc599c1148c8`: `69435065`, container healthy, actuator `UP`
- ALB target health: primary `healthy`, secondary `healthy`
- public smoke: ranking/list/search all `200`

Verification:

- targeted AI/chat/recommendation tests: `BUILD SUCCESSFUL`
- full backend test: `BUILD SUCCESSFUL`

Recommendation flow:

- artifact: `tmp/performance/recommendation-flow/20260718T054938Z`
- `shared_refresh`: `5598.7ms`, `39` rows, `SCORED=12`, `NOT_REQUESTED=27`
- `shared_refresh_cached`: `131.0ms`, `39` rows, `SCORED=12`, `NOT_REQUESTED=27`
- timing log:
  - `RecommendationAiTiming`: `openAiDurationMs=4160`, `totalDurationMs=4166`, `requestedCandidates=12`, `resultCount=12`
  - `RecommendationAiScoringTiming`: `cacheMode=bypass-youth-all`, `durationMs=4167`

Interpretation:

- recommendation generation is still dominated by the OpenAI scoring call.
- `clusterId=youth_all` cache bypass is visible and expected.
- the same-user saved refresh cache still preserves status distribution and returns quickly.
- next recommendation optimization should not use `cluster_ai_results` by cluster alone; it should first measure duplicate `promptSha256` frequency.

Chat flow:

- artifact: `tmp/performance/chat-flow/20260718T054957Z`
- three send-message calls: `6499.1ms`, `3017.2ms`, `2442.9ms`
- answer modes: `POLICY_GROUNDED=2`, `CLARIFICATION=1`
- errors/rate limits: `0`

Stage timing:

| question | total log duration | candidate retrieval | semantic total | semantic embedding | vector search | chat OpenAI | answer mode |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1 | `6416ms` | `4093ms` | `3348ms` | `3054ms` | `85ms` | `2118ms` | `POLICY_GROUNDED` |
| 2 | `2996ms` | `839ms` | `761ms` | `674ms` | `58ms` | `2068ms` | `POLICY_GROUNDED` |
| 3 | `2420ms` | `452ms` | `390ms` | `299ms` | `61ms` | `1888ms` | `CLARIFICATION` |

Interpretation:

- the first chat question had a cold/slow embedding call and spent more time in retrieval than answer generation.
- after that, OpenAI answer generation was the largest single stage.
- vector search itself was not the main bottleneck in this run; query embedding was the expensive retrieval substep.
- lazy semantic search remains a valid next candidate only when it can be proven not to change final candidates.
- exact query embedding cache may help repeated or near-repeated semantic queries, but should be feature-flagged and evaluated after lazy-skip eligibility is measured.

## Lazy Semantic Search Deployment 2026-07-18

Change:

- commit: `9cc2327d`
- semantic search is skipped only when FTS/region keyword merge already fills `condition.limit()`
- skipped traces keep `fallbackStrategy=MERGED_RESULTS`
- skip is logged as `ChatSemanticSearchTiming outcome=skipped reason=fts_full`

Verification before deploy:

- `PolicyExplorationServiceTest`, `ChatPolicyServiceTest`, `ChatConversationServiceTest`: `BUILD SUCCESSFUL`
- full backend `./gradlew test`: `BUILD SUCCESSFUL`

Deployment verification:

- primary actuator: `UP`
- secondary `i-0e8a4cc599c1148c8`: commit `9cc2327d`, container healthy, actuator `UP`
- ALB target health: primary `healthy`, secondary `healthy`
- public ranking/list smoke: `200`

Post-deploy chat measurement:

| run | artifact | q1 | q2 | q3 | send avg | answer modes |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| before | `tmp/performance/chat-flow/20260718T054957Z` | `6499.1ms` | `3017.2ms` | `2442.9ms` | `3986.4ms` | `POLICY_GROUNDED=2`, `CLARIFICATION=1` |
| after 1 | `tmp/performance/chat-flow/20260718T060443Z` | `4093.9ms` | `3452.7ms` | `3458.2ms` | `3668.3ms` | `POLICY_GROUNDED=3` |
| after 2 | `tmp/performance/chat-flow/20260718T060538Z` | `2317.3ms` | `2527.9ms` | `2194.3ms` | `2346.5ms` | `POLICY_GROUNDED=2`, `CLARIFICATION=1` |

Stage comparison for the eligible first question:

| metric | before | after 2 | change |
| --- | ---: | ---: | ---: |
| client send duration | `6499.1ms` | `2317.3ms` | `-64.3%` |
| conversation total log duration | `6416ms` | `2291ms` | `-64.3%` |
| candidate retrieval | `4093ms` | `93ms` | `-97.7%` |
| semantic embedding | `3054ms` | skipped | avoided |
| chat OpenAI answer | `2102ms` | `2087ms` | effectively unchanged |

Quality/readout comparison:

- q1 reference ID stayed `[5875]`
- q2 reference ID stayed `[15399]`
- q3 reference ID stayed `[15351]`
- after-run 2 restored the same answer-mode pattern as the pre-change run
- after-run 1 differed only in q3 `needsClarification`, while the reference ID stayed the same; this is consistent with OpenAI answer variability rather than candidate retrieval drift

Decision:

- keep lazy semantic skip enabled.
- the guard did what it was supposed to do: FTS-full requests no longer pay query embedding/vector search cost.
- the remaining dominant cost for skipped requests is now OpenAI answer generation.
- for non-skipped requests, semantic embedding still costs hundreds of milliseconds and remains a candidate for exact query embedding cache only after duplicate/near-duplicate query frequency is measured.

## Lazy Semantic Quality Review 2026-07-18

Question:

- did the lazy semantic skip change answer quality or policy candidate quality?

Code-level result:

- the new guard triggers only when `merged.size() >= condition.limit()`.
- under the previous code, the same branch produced `semanticAddLimit=0`.
- `addUniqueCandidates(merged, semanticCandidates, 0)` already meant semantic candidates could not enter the final candidate set.
- therefore the user-visible final candidates should be unchanged by construction for skipped branches.
- the only intentional trace change is `semanticCandidates=[]` and empty `semantic_service_ids_json` for skipped snapshots.

Measured chat-flow comparison:

| run | q1 mode/ref | q2 mode/ref | q3 mode/ref |
| --- | --- | --- | --- |
| before `20260718T054957Z` | `POLICY_GROUNDED`, `[5875]` | `POLICY_GROUNDED`, `[15399]` | `CLARIFICATION`, `[15351]` |
| after 1 `20260718T060443Z` | `POLICY_GROUNDED`, `[5875]` | `POLICY_GROUNDED`, `[15399]` | `POLICY_GROUNDED`, `[15351]` |
| after 2 `20260718T060538Z` | `POLICY_GROUNDED`, `[5875]` | `POLICY_GROUNDED`, `[15399]` | `CLARIFICATION`, `[15351]` |

Interpretation:

- all measured runs kept the same referenced policy IDs for all three questions.
- the stable repeated after-run restored the same `answerMode` and `needsClarification` pattern as the pre-change run.
- after-run 1 changed q3 from `CLARIFICATION` to `POLICY_GROUNDED`, but q3 was not a lazy-skip branch (`ftsCandidates=0`, semantic still ran) and the reference ID stayed `[15351]`.
- that q3 mode difference is consistent with normal OpenAI answer variability, not retrieval candidate drift caused by the skip.

Official retrieval evaluation impact estimate:

- previous official artifact: `tmp/policy-quality-observation/20260717T085956Z/policy-quality-summary-artifact/retrieval-evaluation.json`
- baseline metrics: top1 `1.0`, top3 `1.0`, branch suggestion `1.0`, fallback count `2`, empty result count `0`, semantic contribution count `6`
- retrieval scenarios: `9`
- lazy-skip-eligible scenarios by the official artifact: `2`
  - `housing-cash`: fts `5`, semantic `5`, semantic-only final contribution `0`, final IDs `[2971, 3112, 7584, 531, 11160]`
  - `culture-support`: fts `5`, semantic `1`, semantic-only final contribution `0`, final IDs `[2581, 2253, 9362, 1569, 688]`
- estimated user-facing metric delta from the skip:
  - top1 delta `0`
  - top3 delta `0`
  - fallback delta `0`
  - empty result delta `0`
  - semantic contribution delta `0`
  - average semantic result count delta `-0.67` as trace metadata only

Limitations:

- current admin policy-quality API rerun was blocked because `ALLOW_ADMIN_JWT_MINT=true` resolved an admin email but did not mint an access token from the current local DB/env pairing.
- direct DB snapshot query through `.env.runtime.production` also failed with the current default query credential.
- this does not indicate product quality drift, but it means this review uses existing official artifact comparison, code proof, app timing logs, and chat-flow API artifacts rather than a fresh admin quality run.

Conclusion:

- no measured user-visible quality degradation was found.
- policy reference IDs stayed stable in repeated chat-flow checks.
- official retrieval metrics are expected to stay unchanged because the eligible skipped scenarios had zero semantic-only final contribution.
- the only confirmed change is observability metadata: skipped snapshots no longer list semantic candidates that previously could not affect final candidates anyway.

## Admin Quality Rerun And Semantic Duplicate Audit 2026-07-18

Decision before further changes:

- pause additional AI/recommendation/chat performance behavior changes for now.
- first restore the admin policy-quality rerun path.
- measure semantic query duplication before deciding whether an embedding cache is worth adding.

Smoke helper fix:

- `smoke_db_query()` and `smoke_db_apply_file()` no longer default to `DB_MIGRATION_USERNAME=migration_admin` before runtime credentials.
- new priority is `DB_QUERY_*`, explicit `DB_MIGRATION_*`, `DB_USERNAME/DB_PASSWORD`, `DB_ADMIN_RO_*`, then local fallback.
- reason: `.env.runtime.production` has the app runtime DB password, but not a matching `migration_admin` password; the old fallback could combine `migration_admin` with the app password and block admin quality smoke.

Fresh policy quality rerun:

- command: `ALLOW_ADMIN_JWT_MINT=true ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres APP_BASE_URL=http://127.0.0.1:8082 bash deploy/smoke/run-local-policy-quality-summary.sh`
- artifact: `tmp/policy-quality-observation/lazy-semantic-quality-20260718T065920Z`
- result: passed
- dataset: `retrieval-baseline-v2`
- scenario count: `11`
- top1 hit rate: `1.0`
- top3 hit rate: `1.0`
- branch suggestion hit rate: `1.0`
- fallback count: `2`
- empty result count: `0`
- quality gate: passed

Semantic duplicate observation:

- added `deploy/smoke/run-local-chat-semantic-query-duplicate-audit.sh`.
- artifact: `tmp/chat-semantic-query-duplicate-audit/20260718T070517Z`
- raw question export: `false`
- DB proxy key: `md5(normalized_question|preferred_terms_json)`
- app log observability now emits exact semantic `queryHash` on `ChatSemanticSearchTiming` success/empty events, so future log-based checks can use the actual semantic query hash without storing user text.

Measured duplicate proxy:

| window | semantic snapshots | distinct query keys | duplicate snapshots | repeated groups | max group | duplicate rate |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1d | `35` | `12` | `23` | `11` | `4` | `65.71%` |
| 7d | `41` | `14` | `27` | `12` | `4` | `65.85%` |
| 30d | `127` | `22` | `105` | `18` | `14` | `82.68%` |

Interpretation:

- duplicate rate is high enough to justify designing a bounded exact semantic embedding cache next.
- this round did not add that cache and did not change retrieval/answer behavior.
- quality is still green after the lazy semantic skip, so the next cache design must preserve exact query input, embedding model, preferred terms, and safe invalidation boundaries.
