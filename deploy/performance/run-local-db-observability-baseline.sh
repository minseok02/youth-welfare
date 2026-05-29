#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

DB_OBSERVABILITY_ROOT="${DB_OBSERVABILITY_ROOT:-${ROOT_DIR}/tmp/performance/db-observability}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${DB_OBSERVABILITY_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
DATABASE_TSV="${ARTIFACT_DIR}/database-stats.tsv"
TABLE_HEALTH_TSV="${ARTIFACT_DIR}/table-health.tsv"
INDEX_USAGE_TSV="${ARTIFACT_DIR}/index-usage.tsv"
LOCKS_TSV="${ARTIFACT_DIR}/locks.tsv"
ACTIVITY_TSV="${ARTIFACT_DIR}/activity.tsv"
LONG_TX_TSV="${ARTIFACT_DIR}/long-transactions.tsv"
PG_STAT_STATEMENTS_TSV="${ARTIFACT_DIR}/pg-stat-statements-advanced.tsv"
SUMMARY_TXT="${ARTIFACT_DIR}/db-observability-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/db-observability-summary.json"
LATEST_DIR="${DB_OBSERVABILITY_ROOT}/latest"
LATEST_SUMMARY_TXT="${DB_OBSERVABILITY_ROOT}/latest-db-observability-summary.txt"
LATEST_SUMMARY_JSON="${DB_OBSERVABILITY_ROOT}/latest-db-observability-summary.json"

perf_require_python
mkdir -p "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"

query_or_note() {
  local output_file="$1"
  local sql="$2"
  if ! smoke_db_query "${sql}" > "${output_file}" 2> "${output_file}.err"; then
    {
      echo "QUERY_UNAVAILABLE"
      cat "${output_file}.err"
    } > "${output_file}"
  fi
  rm -f "${output_file}.err"
}

query_or_note "${DATABASE_TSV}" "
select
  datname,
  numbackends,
  xact_commit,
  xact_rollback,
  blks_read,
  blks_hit,
  tup_returned,
  tup_fetched,
  tup_inserted,
  tup_updated,
  tup_deleted,
  conflicts,
  temp_files,
  temp_bytes,
  deadlocks
from pg_stat_database
where datname = current_database();
"

query_or_note "${TABLE_HEALTH_TSV}" "
select
  relname,
  n_live_tup,
  n_dead_tup,
  round((n_dead_tup::numeric / nullif(n_live_tup + n_dead_tup, 0)) * 100, 3) as dead_tuple_pct,
  seq_scan,
  seq_tup_read,
  idx_scan,
  idx_tup_fetch,
  n_tup_ins,
  n_tup_upd,
  n_tup_del,
  n_tup_hot_upd,
  coalesce(last_vacuum::text, '') as last_vacuum,
  coalesce(last_autovacuum::text, '') as last_autovacuum,
  coalesce(last_analyze::text, '') as last_analyze,
  coalesce(last_autoanalyze::text, '') as last_autoanalyze
from pg_stat_user_tables
order by n_dead_tup desc, n_live_tup desc
limit 80;
"

query_or_note "${INDEX_USAGE_TSV}" "
select
  s.relname as table_name,
  s.indexrelname as index_name,
  s.idx_scan,
  s.idx_tup_read,
  s.idx_tup_fetch,
  pg_relation_size(s.indexrelid) as index_bytes
from pg_stat_user_indexes s
order by s.idx_scan asc, pg_relation_size(s.indexrelid) desc
limit 80;
"

query_or_note "${LOCKS_TSV}" "
select
  mode,
  granted,
  count(*)
from pg_locks
group by mode, granted
order by granted asc, count(*) desc, mode;
"

query_or_note "${ACTIVITY_TSV}" "
select
  coalesce(state, '') as state,
  coalesce(wait_event_type, '') as wait_event_type,
  coalesce(wait_event, '') as wait_event,
  count(*)
from pg_stat_activity
where datname = current_database()
group by state, wait_event_type, wait_event
order by count(*) desc, state;
"

query_or_note "${LONG_TX_TSV}" "
select
  pid,
  usename,
  coalesce(state, '') as state,
  coalesce(wait_event_type, '') as wait_event_type,
  coalesce(wait_event, '') as wait_event,
  round(extract(epoch from (now() - xact_start))::numeric, 3) as xact_age_seconds,
  left(regexp_replace(query, E'[\\n\\r\\t ]+', ' ', 'g'), 180) as query
from pg_stat_activity
where datname = current_database()
  and xact_start is not null
  and now() - xact_start > interval '1 second'
order by xact_start asc
limit 20;
"

has_pg_stat="$(
  smoke_db_query "select count(*) from pg_extension where extname = 'pg_stat_statements';" 2>/dev/null || printf '0'
)"
if [[ "${has_pg_stat}" == "1" ]]; then
  query_or_note "${PG_STAT_STATEMENTS_TSV}" "
  select
    calls,
    round(total_exec_time::numeric, 3) as total_exec_ms,
    round(mean_exec_time::numeric, 3) as mean_exec_ms,
    round(max_exec_time::numeric, 3) as max_exec_ms,
    rows,
    shared_blks_hit,
    shared_blks_read,
    temp_blks_read,
    temp_blks_written,
    left(regexp_replace(query, E'[\\n\\r\\t ]+', ' ', 'g'), 240) as query
  from pg_stat_statements
  order by total_exec_time desc
  limit 40;
  "
else
  echo "pg_stat_statements_unavailable=true" > "${PG_STAT_STATEMENTS_TSV}"
fi

python3 - \
  "${DATABASE_TSV}" \
  "${TABLE_HEALTH_TSV}" \
  "${INDEX_USAGE_TSV}" \
  "${LOCKS_TSV}" \
  "${ACTIVITY_TSV}" \
  "${LONG_TX_TSV}" \
  "${PG_STAT_STATEMENTS_TSV}" \
  "${SUMMARY_TXT}" \
  "${SUMMARY_JSON}" \
  "${CONTEXT_TXT}" <<'PY'
import csv
import json
import sys
from pathlib import Path

database_path, table_path, index_path, locks_path, activity_path, long_tx_path, pg_stat_path, summary_txt, summary_json, context_path = map(Path, sys.argv[1:11])

def unavailable(path):
    text = path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""
    return "QUERY_UNAVAILABLE" in text or "ERROR:" in text

def read_rows(path, headers):
    if unavailable(path):
        return []
    rows = []
    with path.open(encoding="utf-8", errors="replace") as fp:
        for raw in fp:
            raw = raw.rstrip("\n")
            if not raw:
                continue
            parts = raw.split("\t")
            parts += [""] * (len(headers) - len(parts))
            rows.append(dict(zip(headers, parts[:len(headers)])))
    return rows

database = read_rows(database_path, [
    "datname", "numbackends", "xact_commit", "xact_rollback", "blks_read", "blks_hit",
    "tup_returned", "tup_fetched", "tup_inserted", "tup_updated", "tup_deleted",
    "conflicts", "temp_files", "temp_bytes", "deadlocks",
])
tables = read_rows(table_path, [
    "table", "live", "dead", "dead_pct", "seq_scan", "seq_tup_read", "idx_scan",
    "idx_tup_fetch", "ins", "upd", "del", "hot_upd", "last_vacuum", "last_autovacuum",
    "last_analyze", "last_autoanalyze",
])
indexes = read_rows(index_path, ["table", "index", "idx_scan", "idx_tup_read", "idx_tup_fetch", "index_bytes"])
locks = read_rows(locks_path, ["mode", "granted", "count"])
activity = read_rows(activity_path, ["state", "wait_event_type", "wait_event", "count"])
long_tx = read_rows(long_tx_path, ["pid", "usename", "state", "wait_event_type", "wait_event", "xact_age_seconds", "query"])
pg_stat_available = "pg_stat_statements_unavailable=true" not in pg_stat_path.read_text(encoding="utf-8", errors="replace")
pg_stat = read_rows(pg_stat_path, [
    "calls", "total_exec_ms", "mean_exec_ms", "max_exec_ms", "rows", "shared_blks_hit",
    "shared_blks_read", "temp_blks_read", "temp_blks_written", "query",
]) if pg_stat_available else []

context = {}
for line in context_path.read_text(encoding="utf-8").splitlines():
    if "=" in line:
        key, value = line.split("=", 1)
        context[key] = value

top_dead = sorted(
    tables,
    key=lambda item: float(item["dead_pct"] or 0),
    reverse=True,
)[:10]
unused_large_indexes = [
    item for item in indexes
    if (int(item["idx_scan"] or 0) == 0 and int(item["index_bytes"] or 0) >= 1024 * 1024)
][:10]
blocked_locks = [item for item in locks if item["granted"].lower() == "f"]

payload = {
    "context": context,
    "database": database,
    "table_health_top_dead": top_dead,
    "unused_large_indexes": unused_large_indexes,
    "locks": locks,
    "blocked_locks": blocked_locks,
    "activity": activity,
    "long_transactions": long_tx,
    "pg_stat_statements_available": pg_stat_available,
    "pg_stat_statements_top": pg_stat[:20],
    "raw_artifacts": {
        "database": str(database_path),
        "table_health": str(table_path),
        "index_usage": str(index_path),
        "locks": str(locks_path),
        "activity": str(activity_path),
        "long_transactions": str(long_tx_path),
        "pg_stat_statements": str(pg_stat_path),
    },
}
summary_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = ["db_observability_baseline=passed"]
if database:
    db = database[0]
    hits = int(db["blks_hit"] or 0)
    reads = int(db["blks_read"] or 0)
    hit_rate = hits / (hits + reads) if hits + reads else 0
    lines.append(f"numbackends={db['numbackends']}")
    lines.append(f"db_cache_hit_rate={hit_rate:.6f}")
    lines.append(f"deadlocks={db['deadlocks']}")
    lines.append(f"temp_files={db['temp_files']}")
    lines.append(f"temp_bytes={db['temp_bytes']}")
lines.append(f"blocked_lock_count={sum(int(item['count'] or 0) for item in blocked_locks)}")
lines.append(f"long_transaction_count={len(long_tx)}")
lines.append(f"pg_stat_statements_available={str(pg_stat_available).lower()}")
for item in top_dead[:8]:
    lines.append(f"table_dead_pct table={item['table']} live={item['live']} dead={item['dead']} dead_pct={item['dead_pct']}")
for item in unused_large_indexes[:8]:
    lines.append(f"unused_large_index table={item['table']} index={item['index']} index_bytes={item['index_bytes']}")
summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")
PY

perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
