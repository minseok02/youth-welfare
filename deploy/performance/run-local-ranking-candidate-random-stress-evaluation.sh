#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

RANKING_RANDOM_STRESS_ROOT="${RANKING_RANDOM_STRESS_ROOT:-${ROOT_DIR}/tmp/performance/ranking-candidate-random-stress}"
UNIQUE_VIEW_WINDOW_DAYS="${UNIQUE_VIEW_WINDOW_DAYS:-7}"
RANDOM_STRESS_TRIALS="${RANDOM_STRESS_TRIALS:-80}"
RANDOM_STRESS_SEED="${RANDOM_STRESS_SEED:-20260715}"
EXPLORE_SLOT_COUNT="${EXPLORE_SLOT_COUNT:-2}"
EXPLORE_WINDOW_DAYS="${EXPLORE_WINDOW_DAYS:-14}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${RANKING_RANDOM_STRESS_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
SNAPSHOTS_TSV="${ARTIFACT_DIR}/rankable-snapshots.tsv"
UNIQUE_VIEWS_TSV="${ARTIFACT_DIR}/unique-view-counts.tsv"
SUMMARY_TXT="${ARTIFACT_DIR}/candidate-random-stress-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/candidate-random-stress-summary.json"
DETAILS_TSV="${ARTIFACT_DIR}/candidate-random-stress-details.tsv"
FAILURES_TSV="${ARTIFACT_DIR}/candidate-random-stress-failures.tsv"
LATEST_DIR="${RANKING_RANDOM_STRESS_ROOT}/latest"
LATEST_SUMMARY_TXT="${RANKING_RANDOM_STRESS_ROOT}/latest-candidate-random-stress-summary.txt"
LATEST_SUMMARY_JSON="${RANKING_RANDOM_STRESS_ROOT}/latest-candidate-random-stress-summary.json"

perf_require_python
mkdir -p "${ARTIFACT_DIR}"
perf_sanitize_artifacts_on_exit "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"
{
  echo "unique_view_window_days=${UNIQUE_VIEW_WINDOW_DAYS}"
  echo "random_stress_trials=${RANDOM_STRESS_TRIALS}"
  echo "random_stress_seed=${RANDOM_STRESS_SEED}"
  echo "explore_slot_count=${EXPLORE_SLOT_COUNT}"
  echo "explore_window_days=${EXPLORE_WINDOW_DAYS}"
} >> "${CONTEXT_TXT}"

RANKABLE_SQL="
select
  id,
  source_type,
  replace(replace(coalesce(title, ''), chr(10), ' '), chr(13), ' ') as title,
  coalesce(view_count, 0) as view_count,
  coalesce(api_view_count, 0) as api_view_count,
  created_at,
  registered_at,
  last_modified_at
from welfare_services
where status in ('ACTIVE', 'UPCOMING')
  and (apply_end_date is null or apply_end_date >= current_date);
"

UNIQUE_SQL="
select
  svl.service_id,
  count(distinct case
    when svl.user_key is not null then 'K:' || svl.user_key
    else 'F:' || svl.client_fingerprint
  end) as unique_view_count
from service_view_logs svl
join welfare_services ws on ws.id = svl.service_id
where svl.viewed_at >= now() - interval '${UNIQUE_VIEW_WINDOW_DAYS} days'
  and ws.status in ('ACTIVE', 'UPCOMING')
  and (ws.apply_end_date is null or ws.apply_end_date >= current_date)
group by svl.service_id;
"

smoke_db_query "${RANKABLE_SQL}" > "${SNAPSHOTS_TSV}"
smoke_db_query "${UNIQUE_SQL}" > "${UNIQUE_VIEWS_TSV}"

python3 - \
  "${SNAPSHOTS_TSV}" \
  "${UNIQUE_VIEWS_TSV}" \
  "${SUMMARY_TXT}" \
  "${SUMMARY_JSON}" \
  "${DETAILS_TSV}" \
  "${FAILURES_TSV}" \
  "${CONTEXT_TXT}" \
  "${RANDOM_STRESS_TRIALS}" \
  "${RANDOM_STRESS_SEED}" \
  "${EXPLORE_SLOT_COUNT}" \
  "${EXPLORE_WINDOW_DAYS}" <<'PY'
import csv
import json
import math
import random
import statistics
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, replace
from datetime import datetime, timedelta
from pathlib import Path

(
    snapshots_path,
    unique_path,
    summary_txt,
    summary_json,
    details_tsv,
    failures_tsv,
    context_path,
    trial_count,
    seed,
    explore_slots,
    explore_window_days,
) = sys.argv[1:12]

snapshots_path = Path(snapshots_path)
unique_path = Path(unique_path)
summary_txt = Path(summary_txt)
summary_json = Path(summary_json)
details_tsv = Path(details_tsv)
failures_tsv = Path(failures_tsv)
context_path = Path(context_path)
trial_count = int(trial_count)
seed = int(seed)
explore_slots = int(explore_slots)
explore_window_days = int(explore_window_days)
now = datetime.now()

@dataclass(frozen=True)
class Snapshot:
    id: int
    source_type: str
    title: str
    view_count: int
    api_view_count: int
    created_at: datetime | None
    registered_at: datetime | None
    last_modified_at: datetime | None

def parse_dt(value):
    if not value:
        return None
    value = value.replace(" ", "T")
    try:
        return datetime.fromisoformat(value)
    except ValueError:
        return None

def safe_int(value):
    try:
        return max(0, int(value or 0))
    except ValueError:
        return 0

base_snapshots = []
with snapshots_path.open(encoding="utf-8") as fp:
    for row in csv.reader(fp, delimiter="\t"):
        if len(row) < 8:
            continue
        base_snapshots.append(Snapshot(
            id=int(row[0]),
            source_type=row[1],
            title=row[2],
            view_count=safe_int(row[3]),
            api_view_count=safe_int(row[4]),
            created_at=parse_dt(row[5]),
            registered_at=parse_dt(row[6]),
            last_modified_at=parse_dt(row[7]),
        ))

base_unique_views = {}
with unique_path.open(encoding="utf-8") as fp:
    for row in csv.reader(fp, delimiter="\t"):
        if len(row) < 2:
            continue
        base_unique_views[int(row[0])] = safe_int(row[1])

def log1p(value):
    return math.log1p(max(0, int(value or 0)))

def normalize(value, max_value):
    return 0.0 if max_value <= 0 else value / max_value

def base_time(snapshot):
    return snapshot.created_at or snapshot.registered_at

def freshness_score(snapshot):
    bt = base_time(snapshot)
    if bt is None:
        return 0.0
    days = max(0, (now - bt).days)
    return math.exp(-days / 30.0)

def weight_set(total_unique_views):
    if total_unique_views < 100:
        return (0.25, 0.50, 0.15, 0.10)
    if total_unique_views < 1000:
        return (0.45, 0.30, 0.15, 0.10)
    return (0.55, 0.20, 0.15, 0.10)

def without_external(weights):
    unique, view, _external, freshness = weights
    total = unique + view + freshness
    if total <= 0:
        return weights
    return (unique / total, view / total, 0.0, freshness / total)

def normalization_stats(items, unique_views):
    max_unique_raw = max((log1p(unique_views.get(item.id, 0)) for item in items), default=0.0)
    max_view_raw = max((log1p(item.view_count) for item in items), default=0.0)
    max_external_by_source = defaultdict(float)
    for item in items:
        max_external_by_source[item.source_type] = max(max_external_by_source[item.source_type], log1p(item.api_view_count))
    total_unique_views = sum(unique_views.get(item.id, 0) for item in items)
    return {
        "max_unique_raw": max_unique_raw,
        "max_view_raw": max_view_raw,
        "max_external_by_source": dict(max_external_by_source),
        "total_unique_views": total_unique_views,
        "weights": weight_set(total_unique_views),
    }

def score_snapshots(items, unique_views, stats):
    scored = []
    weights = stats["weights"]
    for snapshot in items:
        source_external_max = stats["max_external_by_source"].get(snapshot.source_type, 0.0)
        external_available = source_external_max > 0.0
        applied = weights if external_available else without_external(weights)
        unique_norm = normalize(log1p(unique_views.get(snapshot.id, 0)), stats["max_unique_raw"])
        view_norm = normalize(log1p(snapshot.view_count), stats["max_view_raw"])
        external_norm = normalize(log1p(snapshot.api_view_count), source_external_max) if external_available else 0.0
        fresh_norm = freshness_score(snapshot)
        score = (
            unique_norm * applied[0]
            + view_norm * applied[1]
            + external_norm * applied[2]
            + fresh_norm * applied[3]
        )
        scored.append({
            "id": snapshot.id,
            "source_type": snapshot.source_type,
            "title": snapshot.title,
            "score": score,
            "unique_views": unique_views.get(snapshot.id, 0),
            "view_count": snapshot.view_count,
            "api_view_count": snapshot.api_view_count,
        })
    return sorted(scored, key=lambda item: item["score"], reverse=True)

def apply_exploration(scored, candidate_snapshots, limit):
    selected = list(scored[:limit])
    if limit < 10 or not selected:
        return selected
    slots = min(explore_slots, limit)
    existing = {item["id"] for item in selected}
    cutoff = now - timedelta(days=explore_window_days)
    scored_by_id = {item["id"]: item for item in scored}
    recent = []
    for snapshot in candidate_snapshots:
        bt = base_time(snapshot)
        if bt is None or bt < cutoff or snapshot.id in existing:
            continue
        candidate = scored_by_id.get(snapshot.id)
        if candidate is not None:
            recent.append((bt, candidate))
    recent.sort(key=lambda item: item[0], reverse=True)
    for _bt, candidate in recent[:slots]:
        if len(selected) >= limit and selected:
            selected.pop()
        selected.append(candidate)
    return selected

def final_ranking(items, unique_views, full_stats, limit):
    return apply_exploration(score_snapshots(items, unique_views, full_stats), items, limit)

def rough_ranked(items, unique_views, full_stats):
    def rough(snapshot):
        view_norm = normalize(log1p(snapshot.view_count), full_stats["max_view_raw"])
        external_norm = normalize(log1p(snapshot.api_view_count), full_stats["max_external_by_source"].get(snapshot.source_type, 0.0))
        unique_norm = normalize(log1p(unique_views.get(snapshot.id, 0)), full_stats["max_unique_raw"])
        fresh_norm = freshness_score(snapshot)
        return view_norm * 0.42 + external_norm * 0.25 + fresh_norm * 0.23 + unique_norm * 0.10
    return sorted(items, key=rough, reverse=True)

def ordered_unique(items):
    result = []
    seen = set()
    for item in items:
        if item.id in seen:
            continue
        seen.add(item.id)
        result.append(item)
    return result

def build_candidates(mode, target, items, unique_views, full_stats):
    ranked = rough_ranked(items, unique_views, full_stats)
    ranked_position = {item.id: idx for idx, item in enumerate(ranked)}
    by_id = {item.id: item for item in items}
    by_source = {
        source: sorted(
            [item for item in items if item.source_type == source],
            key=lambda row: ranked_position.get(row.id, 10**9),
        )
        for source in sorted(Counter(item.source_type for item in items))
    }
    if mode == "simple_top_k":
        return ranked[:target]

    def fill(selected):
        seen = {item.id for item in selected}
        for item in ranked:
            if len(selected) >= target:
                break
            if item.id not in seen:
                selected.append(item)
                seen.add(item.id)
        return selected

    def source_quota(quota_target):
        total = len(items)
        min_per_source = max(25, quota_target // 20)
        selected = []
        for _source, rows in by_source.items():
            proportional = round(quota_target * (len(rows) / total))
            quota = min(len(rows), max(min_per_source, proportional))
            selected.extend(rows[:quota])
        selected = ordered_unique(selected)
        return fill(selected) if len(selected) < quota_target else selected

    if mode == "source_quota":
        return source_quota(target)

    if mode == "popular_recent_union":
        recent = sorted(items, key=lambda row: base_time(row) or datetime.min, reverse=True)
        selected = []
        selected.extend(ranked[:max(1, round(target * 0.55))])
        selected.extend(recent[:max(1, round(target * 0.25))])
        selected.extend(source_quota(max(1, round(target * 0.20))))
        selected.extend([by_id[service_id] for service_id in unique_views if service_id in by_id])
        selected = ordered_unique(selected)
        return fill(selected) if len(selected) < target else selected

    if mode == "conservative_wide":
        recent = sorted(items, key=lambda row: base_time(row) or datetime.min, reverse=True)
        selected = []
        selected.extend(ranked[:target])
        selected.extend(recent[:min(1000, max(100, target // 5))])
        selected.extend([by_id[service_id] for service_id in unique_views if service_id in by_id])
        return ordered_unique(selected)

    raise ValueError(mode)

def source_distribution(items):
    return dict(sorted(Counter(item.source_type for item in items).items()))

def overlap_count(left, right):
    return len(set(left) & set(right))

def next_id_start(items):
    return max(item.id for item in items) + 1

def synthetic_policy(start_id, idx, source, rng, profile):
    if profile == "recent_modest":
        view = rng.randint(0, 5)
        api = rng.randint(50, 3_000)
        days = rng.randint(0, 14)
    elif profile == "external_heavy":
        view = rng.randint(0, 3)
        api = rng.randint(10_000, 5_000_000)
        days = rng.randint(0, 90)
    elif profile == "view_heavy":
        view = rng.randint(20, 300)
        api = rng.randint(0, 5_000)
        days = rng.randint(0, 180)
    elif profile == "long_tail":
        view = rng.randint(0, 2)
        api = rng.randint(0, 200)
        days = rng.randint(30, 600)
    else:
        view = rng.randint(0, 20)
        api = rng.randint(0, 20_000)
        days = rng.randint(0, 365)
    created = now - timedelta(days=days)
    return Snapshot(
        id=start_id + idx,
        source_type=source,
        title=f"synthetic {source} {profile} {idx + 1}",
        view_count=view,
        api_view_count=api,
        created_at=created,
        registered_at=created,
        last_modified_at=created,
    )

def mutate_trial(trial_index):
    rng = random.Random(seed + trial_index * 7919)
    items = list(base_snapshots)
    unique_views = dict(base_unique_views)
    notes = []
    next_id = next_id_start(items)
    source_pool = ["GOV24", "YOUTH", "BOKJIRO_LOCAL", "BOKJIRO_CENTRAL", "NEW_PARTNER", "LOCAL_GOV", "EDU_PARTNER"]
    profiles = ["recent_modest", "external_heavy", "view_heavy", "long_tail", "mixed"]

    for event_idx in range(rng.randint(1, 4)):
        source = rng.choice(source_pool)
        profile = rng.choice(profiles)
        count = rng.choice([100, 250, 500, 1000, 2000, 4000, 8000])
        if source in {"BOKJIRO_CENTRAL"}:
            count = min(count, 1000)
        additions = [synthetic_policy(next_id, i, source, rng, profile) for i in range(count)]
        items.extend(additions)
        next_id += count
        notes.append(f"add:{source}:{profile}:{count}")

    full_stats_for_tail = normalization_stats(items, unique_views)
    rough = rough_ranked(items, unique_views, full_stats_for_tail)
    tail = list(reversed(rough))
    mid = rough[len(rough) // 3: len(rough) // 3 + min(3000, len(rough) // 3)]
    recent = sorted(items, key=lambda row: base_time(row) or datetime.min, reverse=True)
    pools = [tail, mid, recent, items]

    for spike_idx in range(rng.randint(1, 5)):
        pool = rng.choice(pools)
        count = min(len(pool), rng.choice([25, 50, 100, 200, 400, 800]))
        targets = rng.sample(pool, count)
        kind = rng.choice(["unique", "api", "view", "mixed"])
        target_ids = {target.id for target in targets}
        if kind in {"unique", "mixed"}:
            for target in targets:
                unique_views[target.id] = max(unique_views.get(target.id, 0), rng.randint(5, 250))
        if kind in {"api", "mixed"}:
            items = [
                replace(item, api_view_count=max(item.api_view_count, rng.randint(20_000, 10_000_000)))
                if item.id in target_ids else item
                for item in items
            ]
        if kind == "view":
            items = [
                replace(item, view_count=max(item.view_count, rng.randint(20, 500)))
                if item.id in target_ids else item
                for item in items
            ]
        notes.append(f"spike:{kind}:{count}")

    return items, unique_views, ",".join(notes)

modes = [
    ("simple_top_k", 1000),
    ("simple_top_k", 2000),
    ("simple_top_k", 3000),
    ("popular_recent_union", 1000),
    ("popular_recent_union", 1500),
    ("popular_recent_union", 2000),
    ("popular_recent_union", 3000),
    ("source_quota", 1000),
    ("source_quota", 2000),
    ("source_quota", 3000),
    ("conservative_wide", 3000),
    ("conservative_wide", 5000),
]

rows = []
failures = []
for trial in range(1, trial_count + 1):
    items, unique_views, notes = mutate_trial(trial)
    full_stats = normalization_stats(items, unique_views)
    full_top20 = final_ranking(items, unique_views, full_stats, 20)
    full_top50 = final_ranking(items, unique_views, full_stats, 50)
    full_top100 = final_ranking(items, unique_views, full_stats, 100)
    full_ids = {
        20: [row["id"] for row in full_top20],
        50: [row["id"] for row in full_top50],
        100: [row["id"] for row in full_top100],
    }
    by_id = {item.id: item for item in items}
    for mode, target in modes:
        candidates = build_candidates(mode, target, items, unique_views, full_stats)
        candidate_ids = {item.id for item in candidates}
        final_top20 = final_ranking(candidates, unique_views, full_stats, 20)
        final_top20_ids = [row["id"] for row in final_top20]
        recall20 = sum(1 for service_id in full_ids[20] if service_id in candidate_ids)
        recall50 = sum(1 for service_id in full_ids[50] if service_id in candidate_ids)
        recall100 = sum(1 for service_id in full_ids[100] if service_id in candidate_ids)
        overlap20 = overlap_count(full_ids[20], final_top20_ids)
        rejected = recall20 < 20 or recall50 < 49 or recall100 < 95 or overlap20 < 19
        strict_miss = recall100 < 100
        row = {
            "trial": trial,
            "notes": notes,
            "mode": mode,
            "target": target,
            "snapshot_count": len(items),
            "unique_view_service_count": len(unique_views),
            "candidate_count": len(candidates),
            "candidate_pct": len(candidates) / len(items) * 100.0 if items else 0.0,
            "full_top20_recall_count": recall20,
            "full_top50_recall_count": recall50,
            "full_top100_recall_count": recall100,
            "full_top20_recall": f"{recall20}/20",
            "full_top50_recall": f"{recall50}/50",
            "full_top100_recall": f"{recall100}/100",
            "final_top20_overlap_count": overlap20,
            "final_top20_overlap": f"{overlap20}/20",
            "missing_top100_count": 100 - recall100,
            "rejected": rejected,
            "strict_top100_miss": strict_miss,
            "source_distribution": source_distribution(candidates),
        }
        rows.append(row)
        if rejected:
            missing = [service_id for service_id in full_ids[100] if service_id not in candidate_ids]
            for service_id in missing[:5]:
                snapshot = by_id[service_id]
                failures.append({
                    "trial": trial,
                    "mode": mode,
                    "target": target,
                    "service_id": service_id,
                    "source_type": snapshot.source_type,
                    "title": snapshot.title,
                    "view_count": snapshot.view_count,
                    "api_view_count": snapshot.api_view_count,
                    "unique_views": unique_views.get(service_id, 0),
                    "notes": notes,
                })

detail_fields = [
    "trial",
    "mode",
    "target",
    "snapshot_count",
    "unique_view_service_count",
    "candidate_count",
    "candidate_pct",
    "full_top20_recall",
    "full_top50_recall",
    "full_top100_recall",
    "final_top20_overlap",
    "missing_top100_count",
    "rejected",
    "strict_top100_miss",
    "source_distribution",
    "notes",
]
with details_tsv.open("w", encoding="utf-8") as fp:
    writer = csv.DictWriter(fp, fieldnames=detail_fields, delimiter="\t", extrasaction="ignore")
    writer.writeheader()
    for row in rows:
        out = dict(row)
        out["candidate_pct"] = f"{row['candidate_pct']:.2f}"
        out["source_distribution"] = json.dumps(row["source_distribution"], ensure_ascii=False, sort_keys=True)
        writer.writerow(out)

failure_fields = [
    "trial",
    "mode",
    "target",
    "service_id",
    "source_type",
    "view_count",
    "api_view_count",
    "unique_views",
    "title",
    "notes",
]
with failures_tsv.open("w", encoding="utf-8") as fp:
    writer = csv.DictWriter(fp, fieldnames=failure_fields, delimiter="\t", extrasaction="ignore")
    writer.writeheader()
    for row in failures:
        writer.writerow(row)

aggregate = {}
for mode, target in modes:
    key = f"{mode}:{target}"
    selected = [row for row in rows if row["mode"] == mode and row["target"] == target]
    aggregate[key] = {
        "trials": len(selected),
        "failure_count": sum(1 for row in selected if row["rejected"]),
        "strict_top100_miss_count": sum(1 for row in selected if row["strict_top100_miss"]),
        "min_top20_recall": min(row["full_top20_recall_count"] for row in selected),
        "min_top50_recall": min(row["full_top50_recall_count"] for row in selected),
        "min_top100_recall": min(row["full_top100_recall_count"] for row in selected),
        "min_final_top20_overlap": min(row["final_top20_overlap_count"] for row in selected),
        "avg_candidate_pct": statistics.mean(row["candidate_pct"] for row in selected),
        "max_candidate_count": max(row["candidate_count"] for row in selected),
    }

recommended_key = "popular_recent_union:3000"
recommended = aggregate[recommended_key]
context = {}
for line in context_path.read_text(encoding="utf-8").splitlines():
    if "=" in line:
        key, value = line.split("=", 1)
        context[key] = value

payload = {
    "context": context,
    "base_snapshot_count": len(base_snapshots),
    "base_unique_view_service_count": len(base_unique_views),
    "trial_count": trial_count,
    "seed": seed,
    "aggregate": aggregate,
    "recommended_key": recommended_key,
    "recommended": recommended,
    "rows": rows,
    "failure_sample_count": len(failures),
}
summary_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = ["ranking_candidate_random_stress=passed"]
lines.append(f"trial_count={trial_count}")
lines.append(f"seed={seed}")
lines.append(f"base_snapshot_count={len(base_snapshots)}")
lines.append(f"base_unique_view_service_count={len(base_unique_views)}")
lines.append(
    "recommended mode=popular_recent_union target=3000 "
    f"failure_count={recommended['failure_count']} "
    f"strict_top100_miss_count={recommended['strict_top100_miss_count']} "
    f"min_top20_recall={recommended['min_top20_recall']}/20 "
    f"min_top50_recall={recommended['min_top50_recall']}/50 "
    f"min_top100_recall={recommended['min_top100_recall']}/100 "
    f"min_final_top20_overlap={recommended['min_final_top20_overlap']}/20 "
    f"avg_candidate_pct={recommended['avg_candidate_pct']:.2f} "
    f"max_candidate_count={recommended['max_candidate_count']}"
)
for key, item in sorted(aggregate.items()):
    lines.append(
        f"mode={key} failure_count={item['failure_count']} "
        f"strict_top100_miss_count={item['strict_top100_miss_count']} "
        f"min_top20_recall={item['min_top20_recall']}/20 "
        f"min_top50_recall={item['min_top50_recall']}/50 "
        f"min_top100_recall={item['min_top100_recall']}/100 "
        f"min_final_top20_overlap={item['min_final_top20_overlap']}/20 "
        f"avg_candidate_pct={item['avg_candidate_pct']:.2f} "
        f"max_candidate_count={item['max_candidate_count']}"
    )
summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")
PY

perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
