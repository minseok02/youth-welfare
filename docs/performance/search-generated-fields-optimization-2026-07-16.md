# Search Generated Fields Optimization 2026-07-16

## Purpose

After `2026-07-16` EC2/RDS restart verification, the next active performance item is search SQL.

The previous timing instrumentation showed:

- filtered/deadline search miss spends most time in `WelfareServiceSearchRepositoryImpl`
- representative repository timings:
  - primary `청년 ACTIVE_ONLY DEADLINE`: repository `188ms`, SQL `174ms`
  - secondary `월세 ACTIVE_ONLY DEADLINE`: repository `425ms`, SQL `229ms`, entity load `171ms`
  - edge `창업 ACTIVE_ONLY DEADLINE`: repository `300ms`, SQL `271ms`

## Current Finding

The current `general_filtered` SQL still uses computed expressions:

- `to_tsvector('simple', lower(coalesce(title, ...) || ...))`
- `lower(coalesce(title, '')) LIKE ...`
- `similarity(lower(coalesce(title, '')), ...)`

The table already has generated search fields from `V2026_07_14_02__add_policy_search_generated_fields.sql`:

- `title_l`
- `keyword_l`
- `search_document_vector`

The default first-page relevance fast path already uses these generated fields. The general filtered path does not.

## EXPLAIN Baseline

Read-only production `EXPLAIN (ANALYZE, BUFFERS)` was run with `admin_dashboard_ro`.

Current `general_filtered + ACTIVE_ONLY + DEADLINE`:

| Keyword | Execution time | Plan shape | Rows removed by filter |
| --- | ---: | --- | ---: |
| `청년` | `208.932ms` | `idx_ws_active_deadline_list_sort` scan, computed search filter | `12097` |
| `월세` | `181.164ms` | `idx_ws_active_deadline_list_sort` scan, computed search filter | `13488` |
| `창업` | `174.651ms` | `idx_ws_active_deadline_list_sort` scan, computed search filter | `13402` |

The main cost is not sorting. PostgreSQL scans the active deadline index and evaluates expensive search expressions on many rows.

## Candidate Compared

Use generated fields in the general search match/rank expressions, while preserving the existing behavior:

- replace dynamic `to_tsvector(...)` with `ws.search_document_vector`
- replace `lower(coalesce(ws.title, ''))` with `ws.title_l`
- replace `lower(coalesce(ws.keyword, ''))` with `ws.keyword_l`
- keep `similarity(...) >= :trigramThreshold` instead of changing to `%`

Keeping `similarity >= :trigramThreshold` avoids changing pg_trgm threshold semantics. Using `%` could be faster in some cases, but would require adding `set_config('pg_trgm.similarity_threshold', ...)` to every general/search candidate SQL shape. That is a larger behavioral surface.

## Candidate EXPLAIN

Generated-field candidate with behavior-preserving `similarity >= 0.2`:

| Keyword | Execution time | Improvement vs current |
| --- | ---: | ---: |
| `청년` | `25.790ms` | `-87.7%` |
| `월세` | `36.692ms` | `-79.7%` |
| `창업` | `32.284ms` | `-81.5%` |

The plan can still use `idx_ws_active_deadline_list_sort`, but each row filter is much cheaper because the generated fields avoid repeated lower/concatenate/to_tsvector work.

## Equivalence Checks

Count and ordered top-20 IDs matched exactly between current SQL and generated-field SQL:

| Case | Current count | Generated count | Top-20 ordered diff |
| --- | ---: | ---: | ---: |
| `청년` | `1474` | `1474` | `0` |
| `월세` | `83` | `83` | `0` |
| `창업` | `169` | `169` | `0` |
| `주거 + 월세` | `69` | `69` | `0` |
| `GOV24 + 청년` | `417` | `417` | `0` |
| `YOUTH + 청년` | `791` | `791` | `0` |

## Plan

1. Update `WelfareServiceSearchRepositoryImpl` shared search match/rank constants to use generated fields.
2. Keep the default relevance fast path unchanged.
3. Keep `similarity >= :trigramThreshold`; do not introduce `%` into general search in this step.
4. Run focused repository/service/API tests.
5. Deploy and compare live timing logs for filtered/deadline miss requests.

## Expected Impact

| Area | Expected |
| --- | ---: |
| response shape | no change |
| result count/order | no change |
| SQL execution for filtered/deadline miss | `~80%` lower in EXPLAIN |
| cache hit path | no material change |
| migration/index change | none |

## Decision

Proceed with the behavior-preserving generated-field switch first. Defer `%`/GIN candidate pruning and count-query restructuring until after live timing confirms the simpler CPU reduction is enough or not.
