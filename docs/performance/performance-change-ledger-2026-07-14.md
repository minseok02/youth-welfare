# Performance Change Ledger - 2026-07-14

## Purpose

This ledger summarizes the performance and operations work completed around the ALB/two-instance deployment and search/ranking optimization cycle.

Use this as the one-page entry point before opening another optimization batch.

## Current Baseline State

- branch: `refactor/admin-dashboard-sections`
- current commit at the time of this ledger: `6bb201a66d4c92b6503df03af074a8f19ae752f1`
- primary EC2: healthy
- secondary EC2: healthy
- ALB target group: 2 healthy targets
- public domain: `https://youthmoa.kr`
- current decision: continue operating, measure before changing more

## Summary Table

| Area | Starting problem | What changed | Why | Before | After / Current | Result |
| --- | --- | --- | --- | ---: | ---: | --- |
| ALB/two-instance deployment | Needed to confirm traffic and health after switching to 2 instances | Verified Route53 to ALB, both targets healthy, both EC2s same commit | Avoid diagnosing app performance while infra state is uncertain | 1 public DNS record to ALB, target state needed confirmation | 2 ALB targets healthy, primary/secondary synced | accepted |
| Search DB path | Search representative query still recomputed raw `to_tsvector/lower/similarity` | Added generated `title_l`, `keyword_l`, `search_document_vector`; aligned DB representative | Remove repeated generated work and compare against real app fast path | stale representative `249.283ms`; earlier postdeploy DB shape around `316ms+` | aligned `105.263ms`, later stability `66.309ms` | improved |
| RDS migration safety | App could deploy before schema existed; `migration_admin` was not table owner | Added `deploy/postgres/apply-rds-migration-file.sh` using RDS master pre-apply flow | Prevent broken-request window and owner mismatch surprises | owner mismatch discovered during deploy/manual attempt | dry-run/apply/history verification path exists | improved safety |
| API latency measurement | 429/rate-limit samples could still look like plain `passed` | Added top-level decision/counters and external high-RUNS warning | Avoid false interpretation of rate-limited runs | manual per-row reading | `passed`, `passed_with_rate_limit`, `failed` decisions available | improved measurement |
| Edge contract | Edge search baseline needed to match current `POST /api/policies/search` contract | Verified edge script already uses POST search | Avoid measuring legacy endpoint | old TODO suspected legacy endpoint | edge POST search `200`, e.g. `254.031ms` latest | confirmed |
| Stability after many changes | Many sequential changes made it hard to know whether features still worked | Ran post-optimization stability check | Stop before more changes and verify functionality/ops | no single post-change checkpoint | public smoke passed, app errors `0`, DB schema/roles verified | accepted, hold-observe |
| Search/ranking cache interpretation | Warm and cold API numbers were mixed | Added cold/warm cache-tail measurement script | Decide next bottleneck from cold vs warm behavior | logs showed mixed slow tails | ranking cold much worse than search cold | next candidate identified |
| Ranking cold breakdown | Ranking cold was high, but internal cost source was unclear | Added ranking cold breakdown script | Separate DB query, scoring, selected service, projection costs before optimizing | local ranking cold p95 `781.9ms`; edge `1893.9ms` | DB EXPLAIN small except projection base `60.108ms`; scoring simulation `128.302ms` | needs instrumentation/targeted plan |

## Detailed Deltas

### Search Generated Fields

Change:

- migration: `V2026_07_14_02__add_policy_search_generated_fields.sql`
- app fast path now uses:
  - `search_document_vector`
  - `title_l`
  - `keyword_l`
- DB baseline representative was corrected to use the same generated-field path.

Why:

- The previous representative still measured raw expression recomputation, which overstated the current app fast path.
- The app was already benefiting from cached responses, so DB representative and warm API numbers had to be separated.

Observed improvement:

| Metric | Before | After |
| --- | ---: | ---: |
| stale DB `policy_search_keyword_api_shape` | 249.283ms | 105.263ms |
| stability DB `policy_search_keyword_api_shape` | 105.263ms | 66.309ms |
| local warm `policy_search_keyword` p95 | 18.9ms | 15.6ms |
| edge search | 300.144ms | 254.031ms |

Interpretation:

- Search is improved and currently not the strongest next target.
- Uncached search still has cost, but ranking cold is larger.

### Deployment Safety

Change:

- added RDS one-file pre-apply wrapper:
  - `deploy/postgres/apply-rds-migration-file.sh`

Why:

- Generated-column code can break if app deployment reaches production before schema does.
- `migration_admin` cannot perform owner-only DDL on `welfare_services`.

Verification:

- wrapper syntax passed
- dry-run confirmed master connection
- idempotent apply succeeded
- `schema_migration_history` contains:
  - version `2026.07.14.02`
  - script `V2026_07_14_02__add_policy_search_generated_fields.sql`
  - applied by `masteradmin`
- generated columns present: `3`
- generated indexes present: `3`

Interpretation:

- Not a latency optimization.
- This is an operational safety improvement that should be used before future DDL-backed app changes.

### Rate-Limit Measurement Classification

Change:

- `deploy/performance/run-local-api-latency-baseline.sh` now reports:
  - `total_errors`
  - `total_rate_limited`
  - `total_non_rate_limit_errors`
  - top-level decision

Why:

- A run with 429s should not be read the same way as a clean successful run.

Observed:

- normal external run: `passed`, `total_rate_limited=0`
- sensitive external run: `passed`, `total_rate_limited=0`
- high-RUNS external run: warning emitted when `RUNS=16`

Interpretation:

- Runtime unchanged.
- Measurement reliability improved.

### Stability Check

Document:

- [post-optimization-stability-check-2026-07-14.md](post-optimization-stability-check-2026-07-14.md)

Result:

- primary/secondary same commit
- both app containers healthy
- ALB 2 targets healthy
- public flows returned valid data:
  - home
  - policies page
  - policy list
  - policy detail
  - search
  - suggestions
  - trending
  - ranking
- app raw error lines: `0`
- RDS runtime privilege verification passed

Known gap:

- admin read-only smoke needs admin password/token to run the full API contract checks.
- unauthenticated admin endpoints correctly returned `401`.

Decision:

- `hold-observe`
- stable enough to operate, but do not keep changing blindly.

### Cache Tail Measurement

Document:

- [policy-cache-tail-measurement-2026-07-14.md](policy-cache-tail-measurement-2026-07-14.md)

Why:

- Warm-cache local API numbers were very fast, but logs still showed ranking/search slow tails.
- We needed to know whether cold behavior was the real issue.

Observed:

| Endpoint | Local cold p95 | Local warm p95 | Edge cold p95 | Edge warm p95 |
| --- | ---: | ---: | ---: | ---: |
| ranking | 781.9ms | 6.5ms | 1893.9ms | 22.1ms |
| search keyword | 179.2ms | 13.2ms | 544.3ms | 47.0ms |

Interpretation:

- Ranking cold compute is the next stronger bottleneck candidate.
- Search should not be optimized first from the current evidence.

### Ranking Cold Breakdown

Document:

- [ranking-cold-breakdown-measurement-2026-07-14.md](ranking-cold-breakdown-measurement-2026-07-14.md)

Why:

- Ranking cold p95 was high, but it was unclear whether the cause was DB query cost, Java scoring, projection lookup, serialization, or cache write.

Observed:

| Step | Measured result |
| --- | ---: |
| rankable snapshot rows | 13340 |
| unique-view service rows | 5 |
| selected services | 20 |
| projection term rows | 86 |
| projection fact rows | 242 |
| Java scoring/sorting simulation | 128.302ms |
| rankable snapshot EXPLAIN execution | 12.145ms |
| unique-view aggregation EXPLAIN execution | 0.278ms |
| selected services EXPLAIN execution | 0.297ms |
| projection base EXPLAIN execution | 60.108ms |
| projection terms EXPLAIN execution | 0.647ms |
| projection facts EXPLAIN execution | 0.432ms |

Interpretation:

- The DB pieces alone do not explain the full local endpoint cold p95 of `781.9ms`.
- The next ranking batch should add targeted app-side substep instrumentation before changing ranking behavior.
- Likely remaining cost includes JPA/entity materialization, response assembly/serialization, cache writes, and connection/runtime overhead not captured by isolated EXPLAIN.

## Current Recommendation

Do next:

1. Keep current production behavior.
2. Add a temporary or permanent low-cardinality timing breakdown inside `PolicyRankingService.computeRanking`.
3. Measure the live endpoint again with the same cold/warm cache-tail script.
4. Only then choose the first ranking optimization.

Likely first optimization after instrumentation:

- If unique-view aggregation is cheap in app too, do not optimize it first.
- If projection base or response assembly dominates, reduce projection/base work or cache the projection result.
- If snapshot materialization dominates, move ranking candidate selection/scoring closer to SQL or reduce the candidate set before Java scoring.

Do not do next:

- Do not optimize search first based on the current numbers.
- Do not remove the existing 30 second response cache; warm behavior is already good.
- Do not treat DB EXPLAIN alone as complete ranking endpoint cost.
