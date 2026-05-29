#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

DB_BASELINE_ROOT="${DB_BASELINE_ROOT:-${ROOT_DIR}/tmp/performance/db-query}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${DB_BASELINE_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
COUNTS_TSV="${ARTIFACT_DIR}/table-counts.tsv"
SIZES_TSV="${ARTIFACT_DIR}/table-sizes.tsv"
PG_STAT_TSV="${ARTIFACT_DIR}/pg-stat-statements.tsv"
EXPLAIN_DIR="${ARTIFACT_DIR}/explain"
SUMMARY_TXT="${ARTIFACT_DIR}/db-query-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/db-query-summary.json"
LATEST_DIR="${DB_BASELINE_ROOT}/latest"
LATEST_SUMMARY_TXT="${DB_BASELINE_ROOT}/latest-db-query-summary.txt"
LATEST_SUMMARY_JSON="${DB_BASELINE_ROOT}/latest-db-query-summary.json"

mkdir -p "${ARTIFACT_DIR}" "${EXPLAIN_DIR}"
perf_write_run_context "${CONTEXT_TXT}"

table_count() {
  local table_name="$1"
  local sql="select count(*) from ${table_name};"
  local count
  count="$(smoke_db_query "${sql}" 2>/dev/null || printf 'unavailable')"
  printf '%s\t%s\n' "${table_name}" "${count}"
}

{
  echo -e "table\trow_count"
  for table_name in \
    users auth_users user_profiles welfare_services welfare_service_details \
    service_regions service_tags service_facts raw_api_payloads api_sync_logs \
    recommendation_logs user_recommendations score_weights search_logs \
    recent_policy_views service_view_logs notifications user_alerts \
    web_push_subscriptions chat_sessions chat_messages cluster_ai_results \
    collect_execution_locks recommendation_review_gate_promotion_approvals
  do
    table_count "${table_name}"
  done
} > "${COUNTS_TSV}"

smoke_db_query "
select
  relname as table_name,
  n_live_tup,
  n_dead_tup,
  pg_total_relation_size(relid) as total_bytes,
  pg_relation_size(relid) as table_bytes,
  pg_indexes_size(relid) as index_bytes
from pg_stat_user_tables
order by pg_total_relation_size(relid) desc
limit 40;
" > "${SIZES_TSV}" || true

has_pg_stat="$(
  smoke_db_query "select count(*) from pg_extension where extname = 'pg_stat_statements';" 2>/dev/null || printf '0'
)"
if [[ "${has_pg_stat}" == "1" ]]; then
  smoke_db_query "
  select
    calls,
    round(total_exec_time::numeric, 3) as total_exec_ms,
    round(mean_exec_time::numeric, 3) as mean_exec_ms,
    round(max_exec_time::numeric, 3) as max_exec_ms,
    rows,
    left(regexp_replace(query, E'[\\n\\r\\t ]+', ' ', 'g'), 240) as query
  from pg_stat_statements
  order by total_exec_time desc
  limit 30;
  " > "${PG_STAT_TSV}" || true
else
  echo "pg_stat_statements_unavailable=true" > "${PG_STAT_TSV}"
fi

run_explain() {
  local name="$1"
  local sql="$2"
  local output_file="${EXPLAIN_DIR}/${name}.txt"
  local error_file="${EXPLAIN_DIR}/${name}.err"
  if ! smoke_db_query "explain (analyze, buffers, format text) ${sql}" > "${output_file}" 2> "${error_file}"; then
    {
      echo "EXPLAIN_FAILED"
      cat "${error_file}"
    } > "${output_file}"
  fi
  rm -f "${error_file}"
}

run_explain "policy_list_created_at" "select id, title, created_at from welfare_services order by created_at desc limit 20;"
run_explain "policy_search_keyword_ilike" "select id, title from welfare_services where title ilike '%청년%' or description ilike '%청년%' or support_content ilike '%청년%' or keyword ilike '%청년%' order by created_at desc limit 20;"
run_explain "policy_detail_first" "select ws.id, ws.title, wsd.target_detail, wsd.support_detail from welfare_services ws left join welfare_service_details wsd on wsd.service_id = ws.id order by ws.id limit 1;"
run_explain "recommendation_logs_recent_window" "select user_key, count(*) from recommendation_logs where sent_at >= now() - interval '14 days' group by user_key order by count(*) desc limit 20;"
run_explain "admin_collect_failures_recent" "select job_name, status, count(*) from api_sync_logs where started_at >= now() - interval '14 days' group by job_name, status order by count(*) desc limit 20;"
run_explain "recent_policy_views_user" "select user_key, count(*) from recent_policy_views group by user_key order by count(*) desc limit 20;"

python3 - "${COUNTS_TSV}" "${SIZES_TSV}" "${PG_STAT_TSV}" "${EXPLAIN_DIR}" "${SUMMARY_TXT}" "${SUMMARY_JSON}" "${CONTEXT_TXT}" <<'PY'
import csv, json, re, sys
from pathlib import Path

counts_path, sizes_path, pg_stat_path, explain_dir, summary_txt, summary_json, context_path = map(Path, sys.argv[1:8])

counts = list(csv.DictReader(counts_path.open(encoding="utf-8"), delimiter="\t"))

sizes = []
if sizes_path.exists():
    with sizes_path.open(encoding="utf-8") as fp:
        reader = csv.reader(fp, delimiter="\t")
        for row in reader:
            if len(row) >= 6:
                sizes.append(row)

explain = {}
for path in sorted(explain_dir.glob("*.txt")):
    text = path.read_text(encoding="utf-8", errors="replace")
    execution_ms = None
    planning_ms = None
    for line in text.splitlines():
        m = re.search(r"Execution Time: ([0-9.]+) ms", line)
        if m:
            execution_ms = float(m.group(1))
        m = re.search(r"Planning Time: ([0-9.]+) ms", line)
        if m:
            planning_ms = float(m.group(1))
    explain[path.stem] = {
        "failed": "EXPLAIN_FAILED" in text or "ERROR:" in text,
        "execution_ms": execution_ms,
        "planning_ms": planning_ms,
        "has_seq_scan": "Seq Scan" in text,
        "has_index_scan": "Index Scan" in text or "Index Only Scan" in text or "Bitmap Index Scan" in text,
        "path": str(path),
    }

context = {}
for line in context_path.read_text(encoding="utf-8").splitlines():
    if "=" in line:
        k, v = line.split("=", 1)
        context[k] = v

summary = {
    "context": context,
    "table_counts": counts,
    "table_sizes_raw": sizes[:40],
    "pg_stat_statements_available": "pg_stat_statements_unavailable=true" not in pg_stat_path.read_text(encoding="utf-8", errors="replace"),
    "explain": explain,
}
summary_json.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = ["db_query_baseline=passed"]
for item in counts:
    if item["table"] in {"welfare_services", "recommendation_logs", "search_logs", "recent_policy_views", "chat_messages", "raw_api_payloads"}:
        lines.append(f"{item['table']}_rows={item['row_count']}")
for name, item in explain.items():
    lines.append(
        f"{name} execution_ms={item['execution_ms']} planning_ms={item['planning_ms']} "
        f"failed={str(item['failed']).lower()} "
        f"seq_scan={str(item['has_seq_scan']).lower()} index_scan={str(item['has_index_scan']).lower()}"
    )
summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")
if any(item["failed"] for item in explain.values()):
    raise SystemExit(1)
PY

perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
