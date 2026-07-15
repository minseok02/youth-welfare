#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

RANKING_CANDIDATE_ROOT="${RANKING_CANDIDATE_ROOT:-${ROOT_DIR}/tmp/performance/ranking-candidate-mode-evaluation}"
UNIQUE_VIEW_WINDOW_DAYS="${UNIQUE_VIEW_WINDOW_DAYS:-7}"
EXPLORE_SLOT_COUNT="${EXPLORE_SLOT_COUNT:-2}"
EXPLORE_WINDOW_DAYS="${EXPLORE_WINDOW_DAYS:-14}"
SIMPLE_TOP_K_VALUES="${SIMPLE_TOP_K_VALUES:-8000,5000,3000,2000,1000,500}"
SOURCE_QUOTA_VALUES="${SOURCE_QUOTA_VALUES:-5000,3000,2000,1000}"
POPULAR_RECENT_UNION_VALUES="${POPULAR_RECENT_UNION_VALUES:-5000,3000,2000,1000}"
CONSERVATIVE_WIDE_VALUES="${CONSERVATIVE_WIDE_VALUES:-8000,5000,3000}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${RANKING_CANDIDATE_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
SNAPSHOTS_TSV="${ARTIFACT_DIR}/rankable-snapshots.tsv"
UNIQUE_VIEWS_TSV="${ARTIFACT_DIR}/unique-view-counts.tsv"
SUMMARY_TXT="${ARTIFACT_DIR}/candidate-mode-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/candidate-mode-summary.json"
DETAILS_TSV="${ARTIFACT_DIR}/candidate-mode-details.tsv"
MISSING_TSV="${ARTIFACT_DIR}/candidate-mode-missing-top100.tsv"
FULL_TOP_TSV="${ARTIFACT_DIR}/full-baseline-top100.tsv"
CANDIDATE_DIR="${ARTIFACT_DIR}/candidate-ids"
LATEST_DIR="${RANKING_CANDIDATE_ROOT}/latest"
LATEST_SUMMARY_TXT="${RANKING_CANDIDATE_ROOT}/latest-candidate-mode-summary.txt"
LATEST_SUMMARY_JSON="${RANKING_CANDIDATE_ROOT}/latest-candidate-mode-summary.json"

perf_require_python
mkdir -p "${ARTIFACT_DIR}" "${CANDIDATE_DIR}"
perf_sanitize_artifacts_on_exit "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"
{
  echo "unique_view_window_days=${UNIQUE_VIEW_WINDOW_DAYS}"
  echo "explore_slot_count=${EXPLORE_SLOT_COUNT}"
  echo "explore_window_days=${EXPLORE_WINDOW_DAYS}"
  echo "simple_top_k_values=${SIMPLE_TOP_K_VALUES}"
  echo "source_quota_values=${SOURCE_QUOTA_VALUES}"
  echo "popular_recent_union_values=${POPULAR_RECENT_UNION_VALUES}"
  echo "conservative_wide_values=${CONSERVATIVE_WIDE_VALUES}"
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
  "${FULL_TOP_TSV}" \
  "${CANDIDATE_DIR}" \
  "${CONTEXT_TXT}" \
  "${EXPLORE_SLOT_COUNT}" \
  "${EXPLORE_WINDOW_DAYS}" \
  "${SIMPLE_TOP_K_VALUES}" \
  "${SOURCE_QUOTA_VALUES}" \
  "${POPULAR_RECENT_UNION_VALUES}" \
  "${CONSERVATIVE_WIDE_VALUES}" <<'PY'
import csv
import json
import math
import statistics
import sys
import time
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path

(
    snapshots_path,
    unique_path,
    summary_txt,
    summary_json,
    details_tsv,
    missing_tsv,
    full_top_tsv,
    candidate_dir,
    context_path,
    explore_slots,
    explore_window_days,
    simple_values,
    source_quota_values,
    popular_recent_values,
    conservative_values,
) = sys.argv[1:16]

snapshots_path = Path(snapshots_path)
unique_path = Path(unique_path)
summary_txt = Path(summary_txt)
summary_json = Path(summary_json)
details_tsv = Path(details_tsv)
missing_tsv = Path(missing_tsv)
full_top_tsv = Path(full_top_tsv)
candidate_dir = Path(candidate_dir)
context_path = Path(context_path)
explore_slots = int(explore_slots)
explore_window_days = int(explore_window_days)

def int_values(raw):
    values = []
    for part in raw.split(","):
        part = part.strip()
        if part:
            values.append(int(part))
    return values

def parse_dt(value):
    if not value:
        return None
    value = value.replace(" ", "T")
    try:
        return datetime.fromisoformat(value)
    except ValueError:
        return None

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

snapshots = []
with snapshots_path.open(encoding="utf-8") as fp:
    for row in csv.reader(fp, delimiter="\t"):
        if len(row) < 8:
            continue
        snapshots.append(Snapshot(
            id=int(row[0]),
            source_type=row[1],
            title=row[2],
            view_count=safe_int(row[3]),
            api_view_count=safe_int(row[4]),
            created_at=parse_dt(row[5]),
            registered_at=parse_dt(row[6]),
            last_modified_at=parse_dt(row[7]),
        ))

unique_views = {}
with unique_path.open(encoding="utf-8") as fp:
    for row in csv.reader(fp, delimiter="\t"):
        if len(row) < 2:
            continue
        unique_views[int(row[0])] = safe_int(row[1])

by_id = {snapshot.id: snapshot for snapshot in snapshots}
source_counts = Counter(snapshot.source_type for snapshot in snapshots)
now = datetime.now()

def log1p(value):
    return math.log1p(max(0, int(value or 0)))

def normalize(value, max_value):
    return 0.0 if max_value <= 0 else value / max_value

def base_time(snapshot):
    return snapshot.created_at or snapshot.registered_at

def freshness_score(snapshot):
    base = base_time(snapshot)
    if base is None:
        return 0.0
    days = max(0, (now - base).days)
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

def normalization_stats(items):
    max_unique_raw = max((log1p(unique_views.get(item.id, 0)) for item in items), default=0.0)
    max_view_raw = max((log1p(item.view_count) for item in items), default=0.0)
    max_external_by_source = defaultdict(float)
    for item in items:
        max_external_by_source[item.source_type] = max(
            max_external_by_source[item.source_type],
            log1p(item.api_view_count),
        )
    total_unique_views = sum(unique_views.get(item.id, 0) for item in items)
    return {
        "max_unique_raw": max_unique_raw,
        "max_view_raw": max_view_raw,
        "max_external_by_source": dict(max_external_by_source),
        "total_unique_views": total_unique_views,
        "weights": weight_set(total_unique_views),
    }

global_stats = normalization_stats(snapshots)

def score_snapshots(items, stats):
    scored = []
    weights = stats["weights"]
    max_unique_raw = stats["max_unique_raw"]
    max_view_raw = stats["max_view_raw"]
    max_external_by_source = stats["max_external_by_source"]
    for snapshot in items:
        source = snapshot.source_type
        source_external_max = max_external_by_source.get(source, 0.0)
        external_available = source_external_max > 0.0
        applied = weights if external_available else without_external(weights)
        unique_norm = normalize(log1p(unique_views.get(snapshot.id, 0)), max_unique_raw)
        view_norm = normalize(log1p(snapshot.view_count), max_view_raw)
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
            "base_time": base_time(snapshot),
        })
    return sorted(scored, key=lambda item: item["score"], reverse=True)

def apply_exploration(sorted_by_score, all_candidate_snapshots, limit):
    selected = list(sorted_by_score[:limit])
    if limit < 10 or not selected:
        return selected
    slots = min(explore_slots, limit)
    existing = {item["id"] for item in selected}
    cutoff = now - timedelta(days=explore_window_days)
    scored_by_id = {item["id"]: item for item in sorted_by_score}
    recent_candidates = []
    for snapshot in all_candidate_snapshots:
        bt = base_time(snapshot)
        if bt is None or bt < cutoff or snapshot.id in existing:
            continue
        candidate = scored_by_id.get(snapshot.id)
        if candidate is not None:
            recent_candidates.append((bt, candidate))
    recent_candidates.sort(key=lambda item: item[0], reverse=True)
    for _bt, candidate in recent_candidates[:slots]:
        if len(selected) >= limit and selected:
            selected.pop()
        selected.append(candidate)
    return selected

def final_ranking(items, limit, use_global_norm):
    stats = global_stats if use_global_norm else normalization_stats(items)
    scored = score_snapshots(items, stats)
    return apply_exploration(scored, items, limit)

full_started = time.perf_counter()
full_top20 = final_ranking(snapshots, 20, use_global_norm=True)
full_top50 = final_ranking(snapshots, 50, use_global_norm=True)
full_top100 = final_ranking(snapshots, 100, use_global_norm=True)
full_compute_ms = (time.perf_counter() - full_started) * 1000.0

full_ids_by_limit = {
    20: [item["id"] for item in full_top20],
    50: [item["id"] for item in full_top50],
    100: [item["id"] for item in full_top100],
}
full_rank = {service_id: idx for idx, service_id in enumerate(full_ids_by_limit[100], 1)}

def rough_pre_rank_score(snapshot):
    # Purposefully cheaper than the final score. This approximates likely high-rank candidates
    # without depending on the exact full ranking result.
    max_view = global_stats["max_view_raw"]
    source_external_max = global_stats["max_external_by_source"].get(snapshot.source_type, 0.0)
    max_unique = global_stats["max_unique_raw"]
    view_norm = normalize(log1p(snapshot.view_count), max_view)
    external_norm = normalize(log1p(snapshot.api_view_count), source_external_max)
    unique_norm = normalize(log1p(unique_views.get(snapshot.id, 0)), max_unique)
    fresh_norm = freshness_score(snapshot)
    return (
        view_norm * 0.42
        + external_norm * 0.25
        + fresh_norm * 0.23
        + unique_norm * 0.10
    )

snapshots_by_rough = sorted(snapshots, key=rough_pre_rank_score, reverse=True)
snapshots_by_recent = sorted(
    snapshots,
    key=lambda item: base_time(item) or datetime.min,
    reverse=True,
)
snapshots_by_source = {
    source: sorted(
        [snapshot for snapshot in snapshots if snapshot.source_type == source],
        key=rough_pre_rank_score,
        reverse=True,
    )
    for source in sorted(source_counts)
}

def ordered_unique(items):
    result = []
    seen = set()
    for item in items:
        if item.id in seen:
            continue
        seen.add(item.id)
        result.append(item)
    return result

def fill_to_target(selected, target):
    seen = {item.id for item in selected}
    for item in snapshots_by_rough:
        if len(selected) >= target:
            break
        if item.id not in seen:
            selected.append(item)
            seen.add(item.id)
    return selected

def top_k_candidates(target):
    return snapshots_by_rough[:target]

def source_quota_candidates(target):
    total = len(snapshots)
    min_per_source = max(25, target // 20)
    selected = []
    for source, rows in snapshots_by_source.items():
        proportional = round(target * (len(rows) / total))
        quota = min(len(rows), max(min_per_source, proportional))
        selected.extend(rows[:quota])
    selected = ordered_unique(selected)
    if len(selected) < target:
        selected = fill_to_target(selected, target)
    return selected

def popular_recent_union_candidates(target):
    popular_n = max(1, round(target * 0.55))
    recent_n = max(1, round(target * 0.25))
    quota_n = max(1, round(target * 0.20))
    unique_items = [by_id[service_id] for service_id in unique_views if service_id in by_id]
    selected = []
    selected.extend(snapshots_by_rough[:popular_n])
    selected.extend(snapshots_by_recent[:recent_n])
    selected.extend(source_quota_candidates(quota_n))
    selected.extend(unique_items)
    selected = ordered_unique(selected)
    if len(selected) < target:
        selected = fill_to_target(selected, target)
    return selected

def conservative_wide_candidates(target):
    selected = []
    selected.extend(snapshots_by_rough[:target])
    selected.extend(snapshots_by_recent[:min(1000, max(100, target // 5))])
    selected.extend([by_id[service_id] for service_id in unique_views if service_id in by_id])
    selected = ordered_unique(selected)
    return selected

mode_specs = []
mode_specs.append(("full_baseline", len(snapshots), snapshots))
for target in int_values(simple_values):
    mode_specs.append(("simple_top_k", target, top_k_candidates(target)))
for target in int_values(source_quota_values):
    mode_specs.append(("source_quota", target, source_quota_candidates(target)))
for target in int_values(popular_recent_values):
    mode_specs.append(("popular_recent_union", target, popular_recent_union_candidates(target)))
for target in int_values(conservative_values):
    mode_specs.append(("conservative_wide", target, conservative_wide_candidates(target)))

def ratio(n, d):
    return f"{n}/{d}"

def overlap_count(a, b):
    return len(set(a) & set(b))

def source_distribution(items):
    counts = Counter(item.source_type if isinstance(item, Snapshot) else item["source_type"] for item in items)
    return dict(sorted(counts.items()))

rows = []
missing_rows = []

for mode, target, candidates in mode_specs:
    candidate_ids = [item.id for item in candidates]
    candidate_id_set = set(candidate_ids)
    started = time.perf_counter()
    final_subset = final_ranking(candidates, 20, use_global_norm=False)
    subset_compute_ms = (time.perf_counter() - started) * 1000.0
    started = time.perf_counter()
    final_global_norm = final_ranking(candidates, 20, use_global_norm=True)
    global_norm_compute_ms = (time.perf_counter() - started) * 1000.0
    final_subset_ids = [item["id"] for item in final_subset]
    final_global_norm_ids = [item["id"] for item in final_global_norm]
    recall20 = sum(1 for service_id in full_ids_by_limit[20] if service_id in candidate_id_set)
    recall50 = sum(1 for service_id in full_ids_by_limit[50] if service_id in candidate_id_set)
    recall100 = sum(1 for service_id in full_ids_by_limit[100] if service_id in candidate_id_set)
    subset_overlap20 = overlap_count(full_ids_by_limit[20], final_subset_ids)
    global_norm_overlap20 = overlap_count(full_ids_by_limit[20], final_global_norm_ids)
    rejected = (
        recall20 < 20
        or recall50 < 49
        or recall100 < 95
        or max(subset_overlap20, global_norm_overlap20) < 19
    )
    missing_top100 = [service_id for service_id in full_ids_by_limit[100] if service_id not in candidate_id_set]
    for service_id in missing_top100:
        snapshot = by_id[service_id]
        missing_rows.append({
            "mode": mode,
            "target": target,
            "service_id": service_id,
            "full_rank": full_rank.get(service_id, ""),
            "source_type": snapshot.source_type,
            "title": snapshot.title,
            "view_count": snapshot.view_count,
            "api_view_count": snapshot.api_view_count,
            "unique_views": unique_views.get(service_id, 0),
        })
    safe_name = f"{mode}-{target}".replace("/", "_")
    (candidate_dir / f"{safe_name}.txt").write_text(
        "\n".join(str(service_id) for service_id in candidate_ids) + "\n",
        encoding="utf-8",
    )
    rows.append({
        "mode": mode,
        "target": target,
        "candidate_count": len(candidates),
        "candidate_pct": (len(candidates) / len(snapshots) * 100.0) if snapshots else 0.0,
        "full_top20_recall_count": recall20,
        "full_top50_recall_count": recall50,
        "full_top100_recall_count": recall100,
        "full_top20_recall": ratio(recall20, 20),
        "full_top50_recall": ratio(recall50, 50),
        "full_top100_recall": ratio(recall100, 100),
        "final_top20_overlap_subset_norm_count": subset_overlap20,
        "final_top20_overlap_global_norm_count": global_norm_overlap20,
        "final_top20_overlap_subset_norm": ratio(subset_overlap20, 20),
        "final_top20_overlap_global_norm": ratio(global_norm_overlap20, 20),
        "missing_top100_count": len(missing_top100),
        "rejected": rejected,
        "subset_compute_ms": subset_compute_ms,
        "global_norm_compute_ms": global_norm_compute_ms,
        "source_distribution": source_distribution(candidates),
        "final_subset_ids": final_subset_ids,
        "final_global_norm_ids": final_global_norm_ids,
    })

with full_top_tsv.open("w", encoding="utf-8") as fp:
    fp.write("rank\tservice_id\tsource_type\tscore\tunique_views\tview_count\tapi_view_count\ttitle\n")
    for idx, item in enumerate(full_top100, 1):
        fp.write(
            f"{idx}\t{item['id']}\t{item['source_type']}\t{item['score']:.6f}\t"
            f"{item['unique_views']}\t{item['view_count']}\t{item['api_view_count']}\t{item['title']}\n"
        )

detail_fields = [
    "mode",
    "target",
    "candidate_count",
    "candidate_pct",
    "full_top20_recall",
    "full_top50_recall",
    "full_top100_recall",
    "final_top20_overlap_subset_norm",
    "final_top20_overlap_global_norm",
    "missing_top100_count",
    "rejected",
    "subset_compute_ms",
    "global_norm_compute_ms",
    "source_distribution",
]
with details_tsv.open("w", encoding="utf-8") as fp:
    writer = csv.DictWriter(fp, fieldnames=detail_fields, delimiter="\t", extrasaction="ignore")
    writer.writeheader()
    for row in rows:
        out = dict(row)
        out["candidate_pct"] = f"{row['candidate_pct']:.2f}"
        out["subset_compute_ms"] = f"{row['subset_compute_ms']:.3f}"
        out["global_norm_compute_ms"] = f"{row['global_norm_compute_ms']:.3f}"
        out["source_distribution"] = json.dumps(row["source_distribution"], ensure_ascii=False, sort_keys=True)
        writer.writerow(out)

missing_fields = [
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

passing = [row for row in rows if not row["rejected"] and row["mode"] != "full_baseline"]
lowest_candidate_passing = sorted(
    passing,
    key=lambda row: (
        row["candidate_count"],
        row["missing_top100_count"],
        -row["final_top20_overlap_global_norm_count"],
    ),
)
mode_preference = {
    "popular_recent_union": 0,
    "simple_top_k": 1,
    "source_quota": 2,
    "conservative_wide": 3,
}
balanced_candidates = [
    row for row in passing
    if row["missing_top100_count"] == 0
]
balanced_sorted = sorted(
    balanced_candidates,
    key=lambda row: (
        row["candidate_count"],
        mode_preference.get(row["mode"], 99),
        -row["final_top20_overlap_global_norm_count"],
    ),
)
recommended = balanced_sorted[0] if balanced_sorted else None

context = {}
for line in context_path.read_text(encoding="utf-8").splitlines():
    if "=" in line:
        key, value = line.split("=", 1)
        context[key] = value

payload = {
    "context": context,
    "snapshot_count": len(snapshots),
    "source_counts": dict(sorted(source_counts.items())),
    "unique_view_service_count": len(unique_views),
    "full_compute_ms": full_compute_ms,
    "full_top20_ids": full_ids_by_limit[20],
    "full_top50_ids": full_ids_by_limit[50],
    "full_top100_ids": full_ids_by_limit[100],
    "rows": rows,
    "lowest_candidate_passing": lowest_candidate_passing[0] if lowest_candidate_passing else None,
    "recommended": recommended,
    "thresholds": {
        "full_top20_recall": "20/20 required",
        "full_top50_recall": ">=49/50",
        "full_top100_recall": ">=95/100",
        "final_top20_overlap": ">=19/20 on subset or global normalization",
    },
}
summary_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = ["ranking_candidate_mode_evaluation=passed"]
lines.append(f"snapshot_count={len(snapshots)}")
lines.append(f"source_counts={json.dumps(dict(sorted(source_counts.items())), ensure_ascii=False, sort_keys=True)}")
lines.append(f"unique_view_service_count={len(unique_views)}")
lines.append(f"full_compute_ms={full_compute_ms:.3f}")
for row in rows:
    lines.append(
        "mode={mode} target={target} candidate_count={candidate_count} candidate_pct={candidate_pct:.2f} "
        "full_top20_recall={full_top20_recall} full_top50_recall={full_top50_recall} "
        "full_top100_recall={full_top100_recall} final_top20_overlap_subset_norm={final_top20_overlap_subset_norm} "
        "final_top20_overlap_global_norm={final_top20_overlap_global_norm} missing_top100={missing_top100_count} "
        "rejected={rejected}".format(**row)
    )
if lowest_candidate_passing:
    lines.append(
        "lowest_candidate_passing mode={mode} target={target} candidate_count={candidate_count} "
        "candidate_pct={candidate_pct:.2f} full_top100_recall={full_top100_recall} "
        "missing_top100={missing_top100_count} final_top20_overlap_global_norm={final_top20_overlap_global_norm}".format(**lowest_candidate_passing[0])
    )
else:
    lines.append("lowest_candidate_passing=none")
if recommended:
    lines.append(
        "recommended mode={mode} target={target} candidate_count={candidate_count} "
        "candidate_pct={candidate_pct:.2f} full_top100_recall={full_top100_recall} "
        "final_top20_overlap_global_norm={final_top20_overlap_global_norm}".format(**recommended)
    )
else:
    lines.append("recommended=none")
summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")
PY

perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
