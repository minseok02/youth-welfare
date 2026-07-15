# Ranking Candidate Mode Evaluation - 2026-07-15

## Status

Evaluation completed. Runtime behavior unchanged.

This batch evaluates candidate-reduction strategies only. It does not change production ranking behavior.

## Why Candidate Reduction Is Being Evaluated

The previous instrumentation batch showed the largest live ranking cold cost is Java scoring/sorting:

| Field | Median | Max |
| --- | ---: | ---: |
| `scoringSortMs` | 465.0ms | 889ms |
| `rankableSnapshotMs` | 132.5ms | 500ms |
| `projectionLoadMs` | 61.0ms | 95ms |

Current rankable candidate volume:

| Source type | Rankable rows |
| --- | ---: |
| `BOKJIRO_CENTRAL` | 168 |
| `BOKJIRO_LOCAL` | 1286 |
| `GOV24` | 10680 |
| `YOUTH` | 1206 |
| total | 13340 |

The current ranking endpoint scores and sorts all `13340` rankable snapshots on every cold miss. If more sources are added, this cost will grow with the full rankable dataset.

Candidate reduction is directionally valid, but it has a correctness risk: a policy that would have ranked highly in the full calculation could be excluded before final scoring. Therefore, every candidate strategy must be compared against the current full-ranking result before any runtime behavior change.

## Evaluation Principle

The current full-snapshot ranking is the reference answer.

Each candidate mode must answer:

1. Did it include every policy from the current full top 20?
2. Did it preserve most of the full top 50 and top 100?
3. After Java scoring only the candidate set, how close is the final top 20 to the full top 20?
4. Which policies were missed, and what rank did they have in the full result?
5. How much candidate volume did it remove?

## Candidate Modes To Test

### 1. Full Baseline

Current behavior:

- score all rankable snapshots
- sort all scored snapshots
- apply exploration slots

This is the comparison baseline, not an optimization.

### 2. Simple Pre-rank Top K

Build a rough pre-rank score from available snapshot fields:

- `view_count`
- `api_view_count`
- freshness from `created_at` / `registered_at`
- 7-day unique views when present

Test sizes:

- `5000`
- `3000`
- `2000`
- `1000`
- `500`

Risk:

- a single global top K can over-represent `GOV24`
- source types with fewer rows can be squeezed out

### 3. Source Type Quota

Select top candidates inside each source type, then union the result.

Test total targets:

- `5000`
- `3000`
- `2000`
- `1000`

Risk:

- quota tuning can under- or over-represent source types if source quality differs

### 4. Popular + Recent + Unique Union

Union several conservative pools:

- popular by rough pre-rank
- recent by base time
- policies with unique views in the 7-day window
- source-type quota pool

Test total targets:

- `5000`
- `3000`
- `2000`
- `1000`

This is the most likely first production candidate if it passes because it protects popular, recent, unique-view, and source-diversity signals at the same time.

### 5. Conservative Wide Candidate

Start with wider candidate sets and only reduce after recall stays high.

Test sizes:

- `8000`
- `5000`
- `3000`

This gives the lowest correctness risk but a smaller initial latency gain.

### 6. Precomputed Snapshot

Do not compute ranking from scratch on request. Precompute ranking snapshots periodically and have the API read top N.

This is not the first implementation target in this batch, but the evaluation should show whether request-time candidate reduction is enough. If candidate reduction cannot preserve quality and reduce latency, precompute becomes the next design candidate.

## Acceptance Criteria For A Runtime Candidate Mode

| Metric | Required threshold |
| --- | ---: |
| full top 20 candidate recall | 100% |
| full top 50 candidate recall | >= 98% |
| full top 100 candidate recall | >= 95% |
| final top 20 overlap after candidate scoring | >= 95% |
| missing full top 20 policies | 0 |
| source type collapse | none observed |
| candidate count reduction | material |

If full top 20 recall is below 100%, the mode is rejected regardless of speed.

## Planned Tool Output

The comparison script should write:

- `candidate-mode-summary.txt`
- `candidate-mode-summary.json`
- `candidate-mode-details.tsv`
- `candidate-mode-missing-top100.tsv`
- `full-baseline-top100.tsv`
- one candidate ID list per mode

Example summary row:

```text
mode=popular_recent_union target=2000 candidate_count=1874 full_top20_recall=20/20 full_top50_recall=50/50 full_top100_recall=98/100 final_top20_overlap=20/20 rejected=false
```

## Current Working Assumption

The first likely production direction is `popular_recent_union`, but only if the data confirms it.

The safest fallback is a wider candidate mode such as `simple_top_k=5000` or `conservative_wide=5000`.

## Implementation

Added:

- `deploy/performance/run-local-ranking-candidate-mode-evaluation.sh`

The script:

1. Reads the same rankable snapshot fields used by ranking cold breakdown.
2. Reads 7-day unique-view counts.
3. Recomputes the current full ranking in Python as the comparison baseline.
4. Builds candidate sets for each mode.
5. Scores candidate sets using:
   - subset normalization, which models the simplest runtime implementation
   - global/full normalization, which models a safer implementation that keeps full max values
6. Writes summary, JSON, top 100, missing top 100, and candidate ID artifacts.

The script is read-only against the DB.

Verification:

```bash
bash -n deploy/performance/run-local-ranking-candidate-mode-evaluation.sh
```

Result: passed.

## Evaluation Run

Command:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
  RANKING_CANDIDATE_ROOT=tmp/stability/ranking-candidate-mode-evaluation-20260715 \
  bash deploy/performance/run-local-ranking-candidate-mode-evaluation.sh
```

Latest artifact:

- `tmp/stability/ranking-candidate-mode-evaluation-20260715/20260715T075756Z`

Latest summary:

- `tmp/stability/ranking-candidate-mode-evaluation-20260715/latest-candidate-mode-summary.txt`
- `tmp/stability/ranking-candidate-mode-evaluation-20260715/latest-candidate-mode-summary.json`

Baseline:

| Metric | Value |
| --- | ---: |
| rankable snapshots | 13340 |
| unique-view service count | 5 |
| Python full compute simulation | 156.167ms |

Source distribution:

| Source type | Rows |
| --- | ---: |
| `BOKJIRO_CENTRAL` | 168 |
| `BOKJIRO_LOCAL` | 1286 |
| `GOV24` | 10680 |
| `YOUTH` | 1206 |

## Results

Key candidate results:

| Mode | Target | Actual candidates | Candidate % | Top20 recall | Top50 recall | Top100 recall | Final top20 overlap | Missing top100 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `simple_top_k` | 2000 | 2000 | 14.99% | 20/20 | 50/50 | 100/100 | 20/20 | 0 |
| `simple_top_k` | 1000 | 1000 | 7.50% | 20/20 | 50/50 | 100/100 | 20/20 | 0 |
| `simple_top_k` | 500 | 500 | 3.75% | 20/20 | 50/50 | 99/100 | 20/20 | 1 |
| `source_quota` | 2000 | 2075 | 15.55% | 20/20 | 50/50 | 100/100 | 20/20 | 0 |
| `source_quota` | 1000 | 1037 | 7.77% | 20/20 | 50/50 | 100/100 | 20/20 | 0 |
| `popular_recent_union` | 2000 | 2000 | 14.99% | 20/20 | 50/50 | 100/100 | 20/20 | 0 |
| `popular_recent_union` | 1000 | 1000 | 7.50% | 20/20 | 50/50 | 100/100 | 20/20 | 0 |
| `conservative_wide` | 3000 | 3296 | 24.71% | 20/20 | 50/50 | 100/100 | 20/20 | 0 |

Lowest candidate count that still passed the basic threshold:

| Mode | Target | Candidate count | Note |
| --- | ---: | ---: | --- |
| `simple_top_k` | 500 | 500 | top20 preserved, but full top100 missed 1 policy |

The missed full top100 policy for `simple_top_k=500`:

| Full rank | Service ID | Source | Views | API views | Unique views | Title |
| ---: | ---: | --- | ---: | ---: | ---: | --- |
| 91 | 14618 | `GOV24` | 1 | 609 | 0 | 인재육성 교육 지원 |

## Interpretation

The data supports candidate reduction.

Important details:

- Every tested mode preserved full top20.
- Every tested mode except `simple_top_k=500` preserved full top100.
- `simple_top_k=500` is too aggressive for the first runtime change because it already drops one full top100 policy.
- `popular_recent_union=1000` keeps top100 recall at `100/100` while reducing candidate count to `7.50%` of the full set.
- `source_quota=1000` also passes, but it keeps `GOV24` very dominant because quota is proportional after minimum guarantees.
- `popular_recent_union=1000` has a more balanced source mix:
  - `BOKJIRO_CENTRAL=73`
  - `BOKJIRO_LOCAL=406`
  - `GOV24=384`
  - `YOUTH=137`

Recommended first runtime candidate:

| Mode | Target | Candidate count | Why |
| --- | ---: | ---: | --- |
| `popular_recent_union` | 1000 | 1000 | top100 preserved, top20 overlap preserved, source mix is healthier than simple/proportional quota |

Fallback if runtime p95 improvement is not enough:

- `popular_recent_union=2000`
- `simple_top_k=1000`
- `source_quota=1000`

Do not choose first:

- `simple_top_k=500`, because it already has a top100 miss.

## Next Runtime Plan

Implement a guarded candidate mode behind configuration:

- default behavior remains full snapshot until enabled
- mode: `popular_recent_union`
- target: `1000`
- keep the existing Java final scoring and exploration behavior
- preserve or explicitly fetch global normalization max values if the implementation shows subset normalization can affect ordering
- add a comparison test that asserts current top20 is preserved for the current fixture/DB representative
- deploy, measure local and edge cold/warm cache-tail again

Expected performance direction:

| Metric | Current | Expected after candidate mode |
| --- | ---: | ---: |
| Java scoring candidates | 13340 | about 1000 |
| Candidate volume | 100% | 7.50% |
| `scoringSortMs` | median 465ms | materially lower |
| warm ranking p95 | ~16-25ms | unchanged |

This is not yet a production behavior change. It is the basis for the next implementation batch.
