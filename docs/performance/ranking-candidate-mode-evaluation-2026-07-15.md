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
- `deploy/performance/run-local-ranking-candidate-sensitivity-evaluation.sh`
- `deploy/performance/run-local-ranking-candidate-random-stress-evaluation.sh`

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

The sensitivity script additionally mutates the in-memory snapshot set only. It does not write to DB. It tests whether the recommendation survives plausible distribution changes:

- tail policies suddenly receive strong unique views
- tail policies suddenly receive high external/API views
- thousands of recent `GOV24` policies are added
- a new data source is added
- `GOV24` and a new source grow together with extra unique-view winners

Verification:

```bash
bash -n deploy/performance/run-local-ranking-candidate-mode-evaluation.sh
```

Result: passed.

Sensitivity script verification:

```bash
bash -n deploy/performance/run-local-ranking-candidate-sensitivity-evaluation.sh
```

Result: passed.

Random stress script verification:

```bash
bash -n deploy/performance/run-local-ranking-candidate-random-stress-evaluation.sh
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

Initial deterministic recommendation:

| Mode | Target | Candidate count | Why |
| --- | ---: | ---: | --- |
| `popular_recent_union` | 1000 | 1000 | top100 preserved, top20 overlap preserved, source mix is healthier than simple/proportional quota |

Fallback if runtime p95 improvement is not enough:

- `popular_recent_union=2000`
- `simple_top_k=1000`
- `source_quota=1000`

Do not choose first:

- `simple_top_k=500`, because it already has a top100 miss.

## Sensitivity Evaluation

Command:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
  RANKING_SENSITIVITY_ROOT=tmp/stability/ranking-candidate-sensitivity-20260715 \
  bash deploy/performance/run-local-ranking-candidate-sensitivity-evaluation.sh
```

Latest artifact:

- `tmp/stability/ranking-candidate-sensitivity-20260715/20260715T104829Z`

Scenarios:

| Scenario | What changed in memory |
| --- | --- |
| `current` | actual production snapshot |
| `unique_surge_tail` | 120 low pre-rank policies receive strong 7-day unique views |
| `external_spike_tail` | 120 low pre-rank policies receive very high external API views |
| `recent_gov24_influx` | 3000 recent GOV24 policies with modest external views are added |
| `new_source_influx` | 4000 recent policies from a new source are added |
| `mixed_future_growth` | 5000 GOV24 + 1500 new-source policies are added, with 200 unique-view winners |

Recommended mode under sensitivity:

| Scenario | Snapshot count | Candidate count | Candidate % | Top20 recall | Top50 recall | Top100 recall | Final top20 overlap | Rejected |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `current` | 13340 | 1000 | 7.50% | 20/20 | 50/50 | 100/100 | 20/20 | false |
| `unique_surge_tail` | 13340 | 1000 | 7.50% | 20/20 | 50/50 | 100/100 | 20/20 | false |
| `external_spike_tail` | 13340 | 1000 | 7.50% | 20/20 | 50/50 | 100/100 | 20/20 | false |
| `recent_gov24_influx` | 16340 | 1000 | 6.12% | 20/20 | 50/50 | 100/100 | 19/20 | false |
| `new_source_influx` | 17340 | 1000 | 5.77% | 20/20 | 50/50 | 100/100 | 19/20 | false |
| `mixed_future_growth` | 19840 | 1036 | 5.22% | 20/20 | 50/50 | 100/100 | 19/20 | false |

The recommended mode had `failure_count=0`.

Failure patterns found in other modes:

| Mode | Failed scenarios | Why this matters |
| --- | ---: | --- |
| `simple_top_k=500` | 3 | too narrow; misses top policies when unique views or new sources shift ranking |
| `simple_top_k=1000` | 3 | global rough top K can collapse into one source or miss unique-view winners |
| `simple_top_k=2000` | 3 | larger K still failed unique/new-source stress |
| `source_quota=1000` | 3 | proportional quota misses emerging high-value new-source policies and unique-view winners |
| `source_quota=2000` | 3 | larger quota still failed mixed future growth |

Important examples:

- Under `unique_surge_tail`, `simple_top_k=500` captured only `1/20` of the full top20 and `1/100` of the full top100.
- Under `new_source_influx`, `simple_top_k=1000` captured only `17/20` of the full top20 because the rough top K collapsed into the synthetic new source and missed current top policies.
- Under `mixed_future_growth`, `source_quota=1000` captured only `18/20` of the full top20 and `56/100` of the full top100.
- `popular_recent_union=1000` avoided these failures because it combines rough popularity, recent candidates, source minimums, and all unique-view policies.

## Sensitivity Interpretation

The first evaluation showed candidate reduction can work on current data. The sensitivity evaluation strengthens the recommendation and changes what should not be done:

- Do not implement simple global top K as the first runtime mode.
- Do not implement proportional source quota alone.
- Keep the union structure; it is the part that survived unique-view and new-source stress.
- Keep “all unique-view policies” as a mandatory include, even if that makes the candidate set slightly larger than the target.
- Keep small per-source minimums so current high-ranking policies do not disappear when a new source floods the top rough score.

The recommendation is still conditional:

- Synthetic scenarios are not a formal proof.
- If a real new source is added or source distribution changes materially, rerun this script before enabling or keeping the candidate mode.
- Runtime rollout should be guarded by configuration and followed by cache-tail + app timing measurement.

## Random Stress Evaluation

After the deterministic and fixed sensitivity scenarios, a wider randomized stress run was added.

Purpose:

- avoid overfitting the recommendation to a few manually chosen scenarios
- combine multiple changes in one trial
- test source floods, unique/API/view spikes, and recent-policy inflow together

Command pattern:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
  RANKING_RANDOM_STRESS_ROOT=tmp/stability/ranking-candidate-random-stress-20260715 \
  RANDOM_STRESS_TRIALS=80 RANDOM_STRESS_SEED=20260715 \
  bash deploy/performance/run-local-ranking-candidate-random-stress-evaluation.sh
```

Runs:

| Seed | Trials | Artifact |
| ---: | ---: | --- |
| `20260715` | 80 | `tmp/stability/ranking-candidate-random-stress-20260715/20260715T112829Z` |
| `20260716` | 80 | `tmp/stability/ranking-candidate-random-stress-20260715-seed20260716/20260715T113100Z` |
| `20260717` | 80 | `tmp/stability/ranking-candidate-random-stress-20260715-seed20260717/20260715T113306Z` |

Total randomized trials: `240`.

Aggregated result:

| Mode | Trials | Failures | Strict top100 misses | Min top20 recall | Min top50 recall | Min top100 recall | Min final top20 overlap | Avg candidate % | Max candidates |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `popular_recent_union=1000` | 240 | 2 | 4 | 20/20 | 48/50 | 87/100 | 19/20 | 6.86% | 2466 |
| `popular_recent_union=1500` | 240 | 0 | 2 | 20/20 | 50/50 | 98/100 | 19/20 | 9.19% | 2627 |
| `popular_recent_union=2000` | 240 | 0 | 1 | 20/20 | 50/50 | 99/100 | 19/20 | 11.72% | 3169 |
| `popular_recent_union=3000` | 240 | 0 | 0 | 20/20 | 50/50 | 100/100 | 19/20 | 17.14% | 3435 |
| `simple_top_k=3000` | 240 | 67 | 82 | 1/20 | 1/50 | 3/100 | 0/20 | 17.09% | 3000 |
| `source_quota=3000` | 240 | 96 | 132 | 6/20 | 14/50 | 20/100 | 5/20 | 17.79% | 3316 |
| `conservative_wide=3000` | 240 | 1 | 3 | 19/20 | 49/50 | 99/100 | 18/20 | 18.55% | 4490 |
| `conservative_wide=5000` | 240 | 1 | 1 | 19/20 | 49/50 | 99/100 | 18/20 | 30.02% | 6438 |

Random stress changed the recommendation:

- `popular_recent_union=1000` is not robust enough. It had 2 threshold failures and 4 strict top100 misses.
- `popular_recent_union=1500` and `2000` passed the threshold but still had strict top100 misses.
- `popular_recent_union=3000` was the only candidate mode tested that had:
  - `0` failures
  - `0` strict top100 misses
  - full top20 recall never below `20/20`
  - full top50 recall never below `50/50`
  - full top100 recall never below `100/100`

The random stress result is stricter than the deterministic sensitivity result. For runtime rollout, prefer the stricter result.

Updated recommended first runtime candidate:

| Mode | Target | Current-data candidate % | Random stress avg candidate % | Why |
| --- | ---: | ---: | ---: | --- |
| `popular_recent_union` | 3000 | 22.49% | 17.14% | only tested mode with 0 failures and 0 strict top100 misses across 240 randomized trials |

`popular_recent_union=2000` remains a possible second-stage reduction only after the 3000-target rollout is measured and accepted.

## Next Runtime Plan

Implement a guarded candidate mode behind configuration:

- default behavior remains full snapshot until enabled
- mode: `popular_recent_union`
- target: `3000`
- candidate construction:
  - rough popularity pool: about 55%
  - recent pool: about 25%
  - source quota pool: about 20%
  - all policies with 7-day unique views are mandatory includes
- keep the existing Java final scoring and exploration behavior
- preserve or explicitly fetch global normalization max values if the implementation shows subset normalization can affect ordering
- add a comparison test that asserts current top20 is preserved for the current fixture/DB representative
- add an operational preflight: rerun candidate evaluation after any major data import, new source, or ranking weight change
- deploy, measure local and edge cold/warm cache-tail again

Expected performance direction:

| Metric | Current | Expected after candidate mode |
| --- | ---: | ---: |
| Java scoring candidates | 13340 | about 3000 |
| Candidate volume | 100% | 22.49% on current data |
| `scoringSortMs` | median 465ms | materially lower |
| warm ranking p95 | ~16-25ms | unchanged |

This is not yet a production behavior change. It is the basis for the next implementation batch.
