#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

RANKING_SENSITIVITY_ROOT="${RANKING_SENSITIVITY_ROOT:-${ROOT_DIR}/tmp/performance/ranking-candidate-sensitivity-evaluation}"
UNIQUE_VIEW_WINDOW_DAYS="${UNIQUE_VIEW_WINDOW_DAYS:-7}"
EXPLORE_SLOT_COUNT="${EXPLORE_SLOT_COUNT:-2}"
EXPLORE_WINDOW_DAYS="${EXPLORE_WINDOW_DAYS:-14}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${RANKING_SENSITIVITY_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
SNAPSHOTS_TSV="${ARTIFACT_DIR}/rankable-snapshots.tsv"
UNIQUE_VIEWS_TSV="${ARTIFACT_DIR}/unique-view-counts.tsv"
SUMMARY_TXT="${ARTIFACT_DIR}/candidate-sensitivity-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/candidate-sensitivity-summary.json"
DETAILS_TSV="${ARTIFACT_DIR}/candidate-sensitivity-details.tsv"
MISSING_TSV="${ARTIFACT_DIR}/candidate-sensitivity-missing-top100.tsv"
LATEST_DIR="${RANKING_SENSITIVITY_ROOT}/latest"
LATEST_SUMMARY_TXT="${RANKING_SENSITIVITY_ROOT}/latest-candidate-sensitivity-summary.txt"
LATEST_SUMMARY_JSON="${RANKING_SENSITIVITY_ROOT}/latest-candidate-sensitivity-summary.json"

perf_require_python
mkdir -p "${ARTIFACT_DIR}"
perf_sanitize_artifacts_on_exit "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"
{
  echo "unique_view_window_days=${UNIQUE_VIEW_WINDOW_DAYS}"
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
  "${MISSING_TSV}" \
  "${CONTEXT_TXT}" \
  "${EXPLORE_SLOT_COUNT}" \
  "${EXPLORE_WINDOW_DAYS}" <<'PY'
import csv
import json
import math
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
    missing_tsv,
    context_path,
    explore_slots,
    explore_window_days,
) = sys.argv[1:10]

snapshots_path = Path(snapshots_path)
unique_path = Path(unique_path)
summary_txt = Path(summary_txt)
summary_json = Path(summary_json)
details_tsv = Path(details_tsv)
missing_tsv = Path(missing_tsv)
context_path = Path(context_path)
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

def safe_int(value):
    try:
        return max(0, int(value or 0))
    except ValueError:
        return 0

def parse_dt(value):
    if not value:
        return None
    value = value.replace(" ", "T")
    try:
        return datetime.fromisoformat(value)
    except ValueError:
        return None

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

def final_ranking(items, unique_views, full_stats, use_global_norm, limit):
    stats = full_stats if use_global_norm else normalization_stats(items, unique_views)
    return apply_exploration(score_snapshots(items, unique_views, stats), items, limit)

def rough_ranked(items, unique_views, full_stats):
    def rough(snapshot):
        view_norm = normalize(log1p(snapshot.view_count), full_stats["max_view_raw"])
        external_norm = normalize(
            log1p(snapshot.api_view_count),
            full_stats["max_external_by_source"].get(snapshot.source_type, 0.0),
        )
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
        for source, rows in by_source.items():
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

def mutate_tail(items, count):
    full_stats = normalization_stats(items, base_unique_views)
    ranked = rough_ranked(items, base_unique_views, full_stats)
    return list(reversed(ranked))[:count]

def next_id_start(items):
    return max(item.id for item in items) + 1

def add_synthetic(items, start_id, source_type, count, view_base, api_base, days_old, title_prefix):
    result = list(items)
    for idx in range(count):
        created = now - timedelta(days=(idx % max(1, days_old + 1)))
        result.append(Snapshot(
            id=start_id + idx,
            source_type=source_type,
            title=f"{title_prefix} {idx + 1}",
            view_count=view_base + (idx % 7),
            api_view_count=api_base + ((idx * 37) % max(1, api_base)),
            created_at=created,
            registered_at=created,
            last_modified_at=created,
        ))
    return result

def scenario_current():
    return list(base_snapshots), dict(base_unique_views), "actual production snapshot"

def scenario_unique_surge_tail():
    unique = dict(base_unique_views)
    for idx, snapshot in enumerate(mutate_tail(base_snapshots, 120)):
        unique[snapshot.id] = 40 + (idx % 20)
    return list(base_snapshots), unique, "120 low pre-rank policies receive strong 7-day unique views"

def scenario_external_spike_tail():
    targets = {snapshot.id for snapshot in mutate_tail(base_snapshots, 120)}
    items = [
        replace(snapshot, api_view_count=2_000_000 + (snapshot.id % 100_000))
        if snapshot.id in targets else snapshot
        for snapshot in base_snapshots
    ]
    return items, dict(base_unique_views), "120 low pre-rank policies receive very high external API views"

def scenario_recent_gov24_influx():
    items = add_synthetic(base_snapshots, next_id_start(base_snapshots), "GOV24", 3000, 1, 1500, 5, "synthetic recent gov24")
    return items, dict(base_unique_views), "3000 recent GOV24 policies with modest external views are added"

def scenario_new_source_influx():
    items = add_synthetic(base_snapshots, next_id_start(base_snapshots), "NEW_PARTNER", 4000, 2, 2500, 7, "synthetic new source")
    return items, dict(base_unique_views), "4000 recent policies from a new source are added"

def scenario_mixed_future_growth():
    start = next_id_start(base_snapshots)
    items = add_synthetic(base_snapshots, start, "GOV24", 5000, 1, 1200, 20, "synthetic future gov24")
    items = add_synthetic(items, start + 5000, "NEW_PARTNER", 1500, 3, 3500, 10, "synthetic future partner")
    unique = dict(base_unique_views)
    for idx, snapshot in enumerate(items[-200:]):
        unique[snapshot.id] = 20 + (idx % 15)
    return items, unique, "5000 GOV24 + 1500 new-source policies are added, with 200 unique-view winners"

scenarios = [
    ("current", scenario_current),
    ("unique_surge_tail", scenario_unique_surge_tail),
    ("external_spike_tail", scenario_external_spike_tail),
    ("recent_gov24_influx", scenario_recent_gov24_influx),
    ("new_source_influx", scenario_new_source_influx),
    ("mixed_future_growth", scenario_mixed_future_growth),
]

modes = [
    ("simple_top_k", 500),
    ("simple_top_k", 1000),
    ("simple_top_k", 2000),
    ("popular_recent_union", 1000),
    ("popular_recent_union", 2000),
    ("popular_recent_union", 3000),
    ("popular_recent_union", 4000),
    ("source_quota", 1000),
    ("source_quota", 2000),
    ("conservative_wide", 3000),
]

rows = []
missing_rows = []
for scenario_name, scenario_fn in scenarios:
    items, unique_views, description = scenario_fn()
    full_stats = normalization_stats(items, unique_views)
    full_top20 = final_ranking(items, unique_views, full_stats, True, 20)
    full_top50 = final_ranking(items, unique_views, full_stats, True, 50)
    full_top100 = final_ranking(items, unique_views, full_stats, True, 100)
    full_ids = {
        20: [row["id"] for row in full_top20],
        50: [row["id"] for row in full_top50],
        100: [row["id"] for row in full_top100],
    }
    rank100 = {service_id: idx for idx, service_id in enumerate(full_ids[100], 1)}
    by_id = {item.id: item for item in items}
    for mode, target in modes:
        candidates = build_candidates(mode, target, items, unique_views, full_stats)
        candidate_ids = {item.id for item in candidates}
        final_global = final_ranking(candidates, unique_views, full_stats, True, 20)
        final_global_ids = [row["id"] for row in final_global]
        recall20 = sum(1 for service_id in full_ids[20] if service_id in candidate_ids)
        recall50 = sum(1 for service_id in full_ids[50] if service_id in candidate_ids)
        recall100 = sum(1 for service_id in full_ids[100] if service_id in candidate_ids)
        overlap20 = overlap_count(full_ids[20], final_global_ids)
        rejected = recall20 < 20 or recall50 < 49 or recall100 < 95 or overlap20 < 19
        missing_top100 = [service_id for service_id in full_ids[100] if service_id not in candidate_ids]
        for service_id in missing_top100[:20]:
            snapshot = by_id[service_id]
            missing_rows.append({
                "scenario": scenario_name,
                "mode": mode,
                "target": target,
                "service_id": service_id,
                "full_rank": rank100.get(service_id, ""),
                "source_type": snapshot.source_type,
                "view_count": snapshot.view_count,
                "api_view_count": snapshot.api_view_count,
                "unique_views": unique_views.get(service_id, 0),
                "title": snapshot.title,
            })
        rows.append({
            "scenario": scenario_name,
            "description": description,
            "mode": mode,
            "target": target,
            "snapshot_count": len(items),
            "candidate_count": len(candidates),
            "candidate_pct": len(candidates) / len(items) * 100.0 if items else 0.0,
            "full_top20_recall": f"{recall20}/20",
            "full_top50_recall": f"{recall50}/50",
            "full_top100_recall": f"{recall100}/100",
            "final_top20_overlap": f"{overlap20}/20",
            "missing_top100_count": len(missing_top100),
            "rejected": rejected,
            "source_distribution": source_distribution(candidates),
        })

detail_fields = [
    "scenario",
    "mode",
    "target",
    "snapshot_count",
    "candidate_count",
    "candidate_pct",
    "full_top20_recall",
    "full_top50_recall",
    "full_top100_recall",
    "final_top20_overlap",
    "missing_top100_count",
    "rejected",
    "source_distribution",
]
with details_tsv.open("w", encoding="utf-8") as fp:
    writer = csv.DictWriter(fp, fieldnames=detail_fields, delimiter="\t", extrasaction="ignore")
    writer.writeheader()
    for row in rows:
        out = dict(row)
        out["candidate_pct"] = f"{row['candidate_pct']:.2f}"
        out["source_distribution"] = json.dumps(row["source_distribution"], ensure_ascii=False, sort_keys=True)
        writer.writerow(out)

missing_fields = [
    "scenario",
    "mode",
    "target",
    "service_id",
    "full_rank",
    "source_type",
    "view_count",
    "api_view_count",
    "unique_views",
    "title",
]
with missing_tsv.open("w", encoding="utf-8") as fp:
    writer = csv.DictWriter(fp, fieldnames=missing_fields, delimiter="\t", extrasaction="ignore")
    writer.writeheader()
    for row in missing_rows:
        writer.writerow(row)

recommended_mode = ("popular_recent_union", 4000)
recommended_rows = [
    row for row in rows
    if row["mode"] == recommended_mode[0] and row["target"] == recommended_mode[1]
]
recommended_failures = [row for row in recommended_rows if row["rejected"]]
mode_fail_counts = Counter(f"{row['mode']}:{row['target']}" for row in rows if row["rejected"])
scenario_fail_counts = Counter(row["scenario"] for row in rows if row["rejected"])

context = {}
for line in context_path.read_text(encoding="utf-8").splitlines():
    if "=" in line:
        key, value = line.split("=", 1)
        context[key] = value

payload = {
    "context": context,
    "base_snapshot_count": len(base_snapshots),
    "base_unique_view_service_count": len(base_unique_views),
    "scenarios": [{"name": name, "description": fn()[2]} for name, fn in scenarios],
    "modes": [{"mode": mode, "target": target} for mode, target in modes],
    "rows": rows,
    "recommended_mode": {
        "mode": recommended_mode[0],
        "target": recommended_mode[1],
        "failure_count": len(recommended_failures),
        "failures": recommended_failures,
    },
    "mode_fail_counts": dict(sorted(mode_fail_counts.items())),
    "scenario_fail_counts": dict(sorted(scenario_fail_counts.items())),
}
summary_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = ["ranking_candidate_sensitivity_evaluation=passed"]
lines.append(f"base_snapshot_count={len(base_snapshots)}")
lines.append(f"base_unique_view_service_count={len(base_unique_views)}")
lines.append(f"recommended_mode={recommended_mode[0]} target={recommended_mode[1]} failure_count={len(recommended_failures)}")
for key, count in sorted(mode_fail_counts.items()):
    lines.append(f"mode_fail_count mode={key} count={count}")
for key, count in sorted(scenario_fail_counts.items()):
    lines.append(f"scenario_fail_count scenario={key} count={count}")
for row in rows:
    if row["mode"] == recommended_mode[0] and row["target"] == recommended_mode[1]:
        lines.append(
            "recommended_scenario scenario={scenario} snapshot_count={snapshot_count} candidate_count={candidate_count} "
            "candidate_pct={candidate_pct:.2f} full_top20_recall={full_top20_recall} "
            "full_top50_recall={full_top50_recall} full_top100_recall={full_top100_recall} "
            "final_top20_overlap={final_top20_overlap} missing_top100={missing_top100_count} "
            "rejected={rejected}".format(**row)
        )
summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")
PY

perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
