#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

RANKING_BREAKDOWN_ROOT="${RANKING_BREAKDOWN_ROOT:-${ROOT_DIR}/tmp/performance/ranking-cold-breakdown}"
RANKING_SIZE="${RANKING_SIZE:-20}"
UNIQUE_VIEW_WINDOW_DAYS="${UNIQUE_VIEW_WINDOW_DAYS:-7}"
EXPLORE_SLOT_COUNT="${EXPLORE_SLOT_COUNT:-2}"
EXPLORE_WINDOW_DAYS="${EXPLORE_WINDOW_DAYS:-14}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${RANKING_BREAKDOWN_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
TIMINGS_TSV="${ARTIFACT_DIR}/step-timings.tsv"
SUMMARY_TXT="${ARTIFACT_DIR}/ranking-cold-breakdown-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/ranking-cold-breakdown-summary.json"
LATEST_DIR="${RANKING_BREAKDOWN_ROOT}/latest"
LATEST_SUMMARY_TXT="${RANKING_BREAKDOWN_ROOT}/latest-ranking-cold-breakdown-summary.txt"
LATEST_SUMMARY_JSON="${RANKING_BREAKDOWN_ROOT}/latest-ranking-cold-breakdown-summary.json"

SNAPSHOTS_TSV="${ARTIFACT_DIR}/rankable-snapshots.tsv"
UNIQUE_VIEWS_TSV="${ARTIFACT_DIR}/unique-view-counts.tsv"
TOP_TSV="${ARTIFACT_DIR}/computed-top-ranking.tsv"
COMPUTE_JSON="${ARTIFACT_DIR}/scoring-compute-summary.json"
SELECTED_IDS_TXT="${ARTIFACT_DIR}/selected-service-ids.txt"
SELECTED_SERVICES_TSV="${ARTIFACT_DIR}/selected-services.tsv"
PROJECTION_BASE_TSV="${ARTIFACT_DIR}/projection-base.tsv"
PROJECTION_TERMS_TSV="${ARTIFACT_DIR}/projection-terms.tsv"
PROJECTION_FACTS_TSV="${ARTIFACT_DIR}/projection-facts.tsv"
EXPLAIN_DIR="${ARTIFACT_DIR}/explain"

perf_require_python
mkdir -p "${ARTIFACT_DIR}" "${EXPLAIN_DIR}"
perf_sanitize_artifacts_on_exit "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"
{
  echo "ranking_size=${RANKING_SIZE}"
  echo "unique_view_window_days=${UNIQUE_VIEW_WINDOW_DAYS}"
  echo "explore_slot_count=${EXPLORE_SLOT_COUNT}"
  echo "explore_window_days=${EXPLORE_WINDOW_DAYS}"
} >> "${CONTEXT_TXT}"

printf 'step\texit_code\twall_ms\toutput_file\n' > "${TIMINGS_TSV}"

run_query_step() {
  local label="$1"
  local sql="$2"
  local output_file="$3"
  local start_ms end_ms exit_code wall_ms

  start_ms="$(perf_now_ms)"
  set +e
  smoke_db_query "${sql}" > "${output_file}" 2> "${output_file}.err"
  exit_code=$?
  set -e
  end_ms="$(perf_now_ms)"
  wall_ms=$((end_ms - start_ms))
  if (( exit_code != 0 )); then
    {
      echo "QUERY_FAILED"
      cat "${output_file}.err"
    } > "${output_file}"
  fi
  rm -f "${output_file}.err"
  printf '%s\t%s\t%s\t%s\n' "${label}" "${exit_code}" "${wall_ms}" "${output_file}" >> "${TIMINGS_TSV}"
  return "${exit_code}"
}

run_explain() {
  local label="$1"
  local sql="$2"
  run_query_step "explain_${label}" "explain (analyze, buffers, format text) ${sql}" "${EXPLAIN_DIR}/${label}.txt" || true
}

RANKABLE_SQL="
select
  id,
  source_type,
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

run_query_step "rankable_snapshot_query" "${RANKABLE_SQL}" "${SNAPSHOTS_TSV}"
run_query_step "unique_view_aggregation_query" "${UNIQUE_SQL}" "${UNIQUE_VIEWS_TSV}"
run_explain "rankable_snapshot_query" "${RANKABLE_SQL}"
run_explain "unique_view_aggregation_query" "${UNIQUE_SQL}"

compute_start_ms="$(perf_now_ms)"
python3 - "${SNAPSHOTS_TSV}" "${UNIQUE_VIEWS_TSV}" "${RANKING_SIZE}" "${EXPLORE_SLOT_COUNT}" "${EXPLORE_WINDOW_DAYS}" "${TOP_TSV}" "${COMPUTE_JSON}" "${SELECTED_IDS_TXT}" <<'PY'
import csv
import json
import math
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path

snapshots_path, unique_path, ranking_size, explore_slots, explore_window_days, top_tsv, compute_json, selected_ids_path = sys.argv[1:9]
ranking_size = int(ranking_size)
explore_slots = int(explore_slots)
explore_window_days = int(explore_window_days)
top_tsv = Path(top_tsv)
compute_json = Path(compute_json)
selected_ids_path = Path(selected_ids_path)

started = time.perf_counter()

def parse_dt(value):
    if not value:
        return None
    value = value.replace(" ", "T")
    try:
        return datetime.fromisoformat(value)
    except ValueError:
        return None

snapshots = []
with open(snapshots_path, encoding="utf-8") as fp:
    for row in csv.reader(fp, delimiter="\t"):
        if len(row) < 7:
            continue
        snapshots.append({
            "id": int(row[0]),
            "source_type": row[1],
            "view_count": max(0, int(row[2] or 0)),
            "api_view_count": max(0, int(row[3] or 0)),
            "created_at": parse_dt(row[4]),
            "registered_at": parse_dt(row[5]),
            "last_modified_at": parse_dt(row[6]),
        })

unique_views = {}
with open(unique_path, encoding="utf-8") as fp:
    for row in csv.reader(fp, delimiter="\t"):
        if len(row) < 2:
            continue
        unique_views[int(row[0])] = max(0, int(row[1] or 0))

def log1p(value):
    return math.log1p(max(0, int(value or 0)))

def normalize(value, max_value):
    return 0.0 if max_value <= 0 else value / max_value

def policy_base_time(snapshot):
    return snapshot["created_at"] or snapshot["registered_at"]

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

def freshness_score(snapshot, now):
    base = policy_base_time(snapshot)
    if base is None:
        return 0.0
    days = max(0, (now - base).days)
    return math.exp(-days / 30.0)

now = datetime.now()
max_unique_raw = max((log1p(unique_views.get(s["id"], 0)) for s in snapshots), default=0.0)
max_view_raw = max((log1p(s["view_count"]) for s in snapshots), default=0.0)
max_external_by_source = {}
for snapshot in snapshots:
    source = snapshot["source_type"]
    max_external_by_source[source] = max(max_external_by_source.get(source, 0.0), log1p(snapshot["api_view_count"]))

total_unique_views = sum(unique_views.values())
base_weights = weight_set(total_unique_views)
scored = []
for snapshot in snapshots:
    source = snapshot["source_type"]
    external_available = max_external_by_source.get(source, 0.0) > 0.0
    weights = base_weights if external_available else without_external(base_weights)
    unique_norm = normalize(log1p(unique_views.get(snapshot["id"], 0)), max_unique_raw)
    view_norm = normalize(log1p(snapshot["view_count"]), max_view_raw)
    external_norm = normalize(log1p(snapshot["api_view_count"]), max_external_by_source.get(source, 0.0)) if external_available else 0.0
    fresh_norm = freshness_score(snapshot, now)
    score = (
        unique_norm * weights[0]
        + view_norm * weights[1]
        + external_norm * weights[2]
        + fresh_norm * weights[3]
    )
    scored.append({
        "id": snapshot["id"],
        "source_type": source,
        "score": score,
        "created_at": snapshot["created_at"],
        "registered_at": snapshot["registered_at"],
        "unique_views": unique_views.get(snapshot["id"], 0),
        "view_count": snapshot["view_count"],
        "api_view_count": snapshot["api_view_count"],
    })

sorted_by_score = sorted(scored, key=lambda item: item["score"], reverse=True)
selected = list(sorted_by_score[:ranking_size])
if ranking_size >= 10 and selected:
    slots = min(explore_slots, ranking_size)
    existing = {item["id"] for item in selected}
    cutoff = now - timedelta(days=explore_window_days)
    by_id = {item["id"]: item for item in scored}
    recent_candidates = []
    for snapshot in snapshots:
        base = policy_base_time(snapshot)
        if base is None or base < cutoff or snapshot["id"] in existing:
            continue
        candidate = by_id.get(snapshot["id"])
        if candidate is not None:
            recent_candidates.append((base, candidate))
    recent_candidates.sort(key=lambda item: item[0], reverse=True)
    for _base, candidate in recent_candidates[:slots]:
        if len(selected) >= ranking_size and selected:
            selected.pop()
        selected.append(candidate)

selected_ids = [item["id"] for item in selected]
selected_ids_path.write_text(",".join(str(item) for item in selected_ids), encoding="utf-8")

with top_tsv.open("w", encoding="utf-8") as fp:
    fp.write("rank\tservice_id\tscore\tunique_views\tview_count\tapi_view_count\tsource_type\n")
    for idx, item in enumerate(selected, 1):
        fp.write(
            f"{idx}\t{item['id']}\t{item['score']:.6f}\t{item['unique_views']}\t"
            f"{item['view_count']}\t{item['api_view_count']}\t{item['source_type']}\n"
        )

elapsed_ms = (time.perf_counter() - started) * 1000.0
payload = {
    "scoring_compute_ms": elapsed_ms,
    "snapshot_count": len(snapshots),
    "unique_view_service_count": len(unique_views),
    "total_unique_views": total_unique_views,
    "max_unique_raw": max_unique_raw,
    "max_view_raw": max_view_raw,
    "max_external_by_source": max_external_by_source,
    "base_weights": {
        "unique": base_weights[0],
        "view": base_weights[1],
        "external": base_weights[2],
        "freshness": base_weights[3],
    },
    "selected_count": len(selected_ids),
    "selected_ids": selected_ids,
}
compute_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"scoring_compute_ms={elapsed_ms:.3f}")
PY
compute_end_ms="$(perf_now_ms)"
printf '%s\t%s\t%s\t%s\n' "java_scoring_sorting_simulation" "0" "$((compute_end_ms - compute_start_ms))" "${COMPUTE_JSON}" >> "${TIMINGS_TSV}"

SELECTED_IDS_CSV="$(cat "${SELECTED_IDS_TXT}")"
if [[ -z "${SELECTED_IDS_CSV}" ]]; then
  echo "selected ids are empty" >&2
  exit 1
fi

SELECTED_SERVICES_SQL="
select
  id,
  title,
  source_type,
  status,
  coalesce(view_count, 0) as view_count,
  coalesce(api_view_count, 0) as api_view_count,
  created_at,
  registered_at,
  last_modified_at,
  apply_end_date
from welfare_services
where id in (${SELECTED_IDS_CSV});
"

PROJECTION_BASE_SQL="
select
  ws.id as service_id,
  ws.source_type,
  ws.unified_category,
  coalesce(stss_youth_major.slot_label, st.youth_major_label) as youth_major_label,
  coalesce(stss_youth_mid.slot_label, st.youth_mid_label) as youth_mid_label,
  coalesce(stss_provision_method.slot_label, st.provision_method_label, ws.apply_method_name) as provision_method_label,
  coalesce(stss_gov24_service_field.slot_label, st.gov24_service_field_label) as gov24_service_field_label,
  coalesce(stss_gov24_user_type.slot_label, st.gov24_user_type_label) as gov24_user_type_label,
  coalesce(stss_gov24_benefit_type.slot_label, st.gov24_benefit_type_label) as gov24_benefit_type_label,
  ws.title,
  left(replace(replace(coalesce(wsd.support_detail, ws.support_content, ws.description), chr(10), ' '), chr(13), ' '), 240) as summary_sample,
  ws.min_age,
  ws.max_age,
  ws.min_income,
  ws.max_income,
  ws.apply_end_date,
  ws.search_youth_relevant
from welfare_services ws
left join welfare_service_details wsd on wsd.service_id = ws.id
left join service_taxonomies st on st.service_id = ws.id
left join (
  select service_id, max(slot_label) as slot_label
  from service_taxonomy_summary_slots
  where slot_key = 'YOUTH_MAJOR'
  group by service_id
) stss_youth_major on stss_youth_major.service_id = ws.id
left join (
  select service_id, max(slot_label) as slot_label
  from service_taxonomy_summary_slots
  where slot_key = 'YOUTH_MID'
  group by service_id
) stss_youth_mid on stss_youth_mid.service_id = ws.id
left join (
  select service_id, max(slot_label) as slot_label
  from service_taxonomy_summary_slots
  where slot_key = 'PROVISION_METHOD'
  group by service_id
) stss_provision_method on stss_provision_method.service_id = ws.id
left join (
  select service_id, max(slot_label) as slot_label
  from service_taxonomy_summary_slots
  where slot_key = 'GOV24_SERVICE_FIELD'
  group by service_id
) stss_gov24_service_field on stss_gov24_service_field.service_id = ws.id
left join (
  select service_id, max(slot_label) as slot_label
  from service_taxonomy_summary_slots
  where slot_key = 'GOV24_USER_TYPE'
  group by service_id
) stss_gov24_user_type on stss_gov24_user_type.service_id = ws.id
left join (
  select service_id, max(slot_label) as slot_label
  from service_taxonomy_summary_slots
  where slot_key = 'GOV24_BENEFIT_TYPE'
  group by service_id
) stss_gov24_benefit_type on stss_gov24_benefit_type.service_id = ws.id
where ws.id in (${SELECTED_IDS_CSV});
"

PROJECTION_TERMS_SQL="
select service_id, term_group, term_label, source_field
from service_taxonomy_terms
where service_id in (${SELECTED_IDS_CSV})
order by service_id, term_group, sort_order, term_label;
"

PROJECTION_FACTS_SQL="
select service_id, fact_merge_key, fact_code_set_key, fact_code, raw_value, text_value
from service_facts
where service_id in (${SELECTED_IDS_CSV})
order by service_id, fact_merge_key;
"

run_query_step "selected_services_query" "${SELECTED_SERVICES_SQL}" "${SELECTED_SERVICES_TSV}"
run_query_step "projection_base_query" "${PROJECTION_BASE_SQL}" "${PROJECTION_BASE_TSV}"
run_query_step "projection_terms_query" "${PROJECTION_TERMS_SQL}" "${PROJECTION_TERMS_TSV}"
run_query_step "projection_facts_query" "${PROJECTION_FACTS_SQL}" "${PROJECTION_FACTS_TSV}"
run_explain "selected_services_query" "${SELECTED_SERVICES_SQL}"
run_explain "projection_base_query" "${PROJECTION_BASE_SQL}"
run_explain "projection_terms_query" "${PROJECTION_TERMS_SQL}"
run_explain "projection_facts_query" "${PROJECTION_FACTS_SQL}"

python3 - "${TIMINGS_TSV}" "${COMPUTE_JSON}" "${SUMMARY_TXT}" "${SUMMARY_JSON}" "${CONTEXT_TXT}" "${SNAPSHOTS_TSV}" "${UNIQUE_VIEWS_TSV}" "${SELECTED_SERVICES_TSV}" "${PROJECTION_BASE_TSV}" "${PROJECTION_TERMS_TSV}" "${PROJECTION_FACTS_TSV}" "${EXPLAIN_DIR}" <<'PY'
import csv
import json
import re
import sys
from pathlib import Path

(
    timings_path,
    compute_path,
    summary_txt,
    summary_json,
    context_path,
    snapshots_path,
    unique_path,
    selected_services_path,
    projection_base_path,
    projection_terms_path,
    projection_facts_path,
    explain_dir,
) = map(Path, sys.argv[1:13])

timings = list(csv.DictReader(timings_path.open(encoding="utf-8"), delimiter="\t"))
compute = json.loads(compute_path.read_text(encoding="utf-8"))
context = {}
for line in context_path.read_text(encoding="utf-8").splitlines():
    if "=" in line:
        key, value = line.split("=", 1)
        context[key] = value

def count_rows(path):
    if not path.exists():
        return 0
    text = path.read_text(encoding="utf-8", errors="replace")
    if not text.strip() or "QUERY_FAILED" in text:
        return 0
    return len([line for line in text.splitlines() if line.strip()])

explain = {}
for path in sorted(explain_dir.glob("*.txt")):
    text = path.read_text(encoding="utf-8", errors="replace")
    execution_ms = None
    planning_ms = None
    for line in text.splitlines():
        match = re.search(r"Execution Time: ([0-9.]+) ms", line)
        if match:
            execution_ms = float(match.group(1))
        match = re.search(r"Planning Time: ([0-9.]+) ms", line)
        if match:
            planning_ms = float(match.group(1))
    explain[path.stem] = {
        "execution_ms": execution_ms,
        "planning_ms": planning_ms,
        "has_seq_scan": "Seq Scan" in text,
        "has_index_scan": "Index Scan" in text or "Index Only Scan" in text or "Bitmap Index Scan" in text,
        "path": str(path),
    }

primary_steps = [
    "rankable_snapshot_query",
    "unique_view_aggregation_query",
    "java_scoring_sorting_simulation",
    "selected_services_query",
    "projection_base_query",
    "projection_terms_query",
    "projection_facts_query",
]
timing_by_step = {row["step"]: row for row in timings}
total_wall_ms = sum(int(timing_by_step[step]["wall_ms"]) for step in primary_steps if step in timing_by_step)

payload = {
    "context": context,
    "timings": timings,
    "compute": compute,
    "row_counts": {
        "rankable_snapshots": count_rows(snapshots_path),
        "unique_view_services": count_rows(unique_path),
        "selected_services": count_rows(selected_services_path),
        "projection_base_rows": count_rows(projection_base_path),
        "projection_term_rows": count_rows(projection_terms_path),
        "projection_fact_rows": count_rows(projection_facts_path),
    },
    "primary_total_wall_ms": total_wall_ms,
    "explain": explain,
}
summary_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = ["ranking_cold_breakdown_baseline=passed"]
lines.append(f"primary_total_wall_ms={total_wall_ms}")
lines.append(f"rankable_snapshot_rows={payload['row_counts']['rankable_snapshots']}")
lines.append(f"unique_view_service_rows={payload['row_counts']['unique_view_services']}")
lines.append(f"selected_service_rows={payload['row_counts']['selected_services']}")
lines.append(f"projection_base_rows={payload['row_counts']['projection_base_rows']}")
lines.append(f"projection_term_rows={payload['row_counts']['projection_term_rows']}")
lines.append(f"projection_fact_rows={payload['row_counts']['projection_fact_rows']}")
for step in primary_steps:
    row = timing_by_step.get(step)
    if row:
        lines.append(f"{step} wall_ms={row['wall_ms']} output={row['output_file']}")
for name, item in sorted(explain.items()):
    lines.append(
        f"explain {name} execution_ms={item['execution_ms']} planning_ms={item['planning_ms']} "
        f"seq_scan={str(item['has_seq_scan']).lower()} index_scan={str(item['has_index_scan']).lower()}"
    )
summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")
PY

perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
