#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/collect-external-api-smoke}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS:-14}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

SOURCE_AVAILABLE_OUT="${ARTIFACT_DIR}/source-available.tsv"
API_SUMMARY_OUT="${ARTIFACT_DIR}/api-sync-summary.tsv"
API_BREAKDOWN_OUT="${ARTIFACT_DIR}/api-sync-breakdown.tsv"
API_RECENT_OUT="${ARTIFACT_DIR}/api-sync-recent.tsv"
RAW_SUMMARY_OUT="${ARTIFACT_DIR}/raw-payload-summary.tsv"
RAW_BREAKDOWN_OUT="${ARTIFACT_DIR}/raw-payload-breakdown.tsv"
RUNTIME_STATUS_OUT="${ARTIFACT_DIR}/collect-runtime-statuses.tsv"
LOCKS_OUT="${ARTIFACT_DIR}/collect-execution-locks.tsv"
SUMMARY_OUT="${ARTIFACT_DIR}/collect-external-api-smoke-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/collect-external-api-smoke.json"
NOTE_OUT="${ARTIFACT_DIR}/collect-external-api-smoke-note.md"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-collect-external-api-smoke-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-collect-external-api-smoke.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-collect-external-api-smoke-note.md"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"

smoke_require_command python3
mkdir -p "${ARTIFACT_DIR}"

smoke_print_step "collect external API DB source availability"
smoke_db_query "
WITH expected(table_name) AS (
  VALUES
    ('api_sync_logs'),
    ('raw_api_payloads'),
    ('collect_runtime_statuses'),
    ('collect_execution_locks'),
    ('welfare_services')
)
SELECT
  CASE WHEN count(*) FILTER (WHERE to_regclass('public.' || table_name) IS NULL) = 0 THEN 'true' ELSE 'false' END,
  coalesce(string_agg(table_name, ',' ORDER BY table_name) FILTER (WHERE to_regclass('public.' || table_name) IS NULL), '')
FROM expected;
" > "${SOURCE_AVAILABLE_OUT}"

IFS=$'\t' read -r SOURCE_AVAILABLE SOURCE_MISSING < "${SOURCE_AVAILABLE_OUT}"

if [[ "${SOURCE_AVAILABLE}" == "true" ]]; then
  smoke_print_step "collect external API DB summaries"
  smoke_db_query "
SELECT
  count(*)::bigint AS runs,
  count(*) FILTER (WHERE status = 'SUCCESS')::bigint AS success_runs,
  count(*) FILTER (WHERE status = 'PARTIAL_SUCCESS')::bigint AS partial_runs,
  count(*) FILTER (WHERE status = 'FAILED')::bigint AS failed_runs,
  coalesce(sum(requested_count), 0)::bigint AS requested,
  coalesce(sum(saved_count), 0)::bigint AS saved,
  coalesce(sum(skipped_count), 0)::bigint AS skipped,
  coalesce(sum(filtered_count), 0)::bigint AS filtered,
  coalesce(sum(failed_count), 0)::bigint AS failed,
  coalesce(to_char(max(started_at), 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') AS latest_started_at,
  coalesce(to_char(max(finished_at), 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') AS latest_finished_at
FROM api_sync_logs
WHERE started_at >= now() - (${SUMMARY_WINDOW_DAYS} || ' days')::interval;
" > "${API_SUMMARY_OUT}"

  smoke_db_query "
SELECT
  job_name,
  status,
  count(*)::bigint AS runs,
  coalesce(sum(requested_count), 0)::bigint AS requested,
  coalesce(sum(saved_count), 0)::bigint AS saved,
  coalesce(sum(skipped_count), 0)::bigint AS skipped,
  coalesce(sum(filtered_count), 0)::bigint AS filtered,
  coalesce(sum(failed_count), 0)::bigint AS failed,
  coalesce(to_char(max(started_at), 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') AS latest_started_at
FROM api_sync_logs
WHERE started_at >= now() - (${SUMMARY_WINDOW_DAYS} || ' days')::interval
GROUP BY job_name, status
ORDER BY latest_started_at DESC, runs DESC, job_name
LIMIT 50;
" > "${API_BREAKDOWN_OUT}"

  smoke_db_query "
SELECT
  job_name,
  status,
  requested_count,
  saved_count,
  skipped_count,
  filtered_count,
  failed_count,
  coalesce(error_code, '') AS error_code,
  left(coalesce(error_message, ''), 240) AS error_message,
  coalesce(to_char(started_at, 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') AS started_at,
  coalesce(to_char(finished_at, 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') AS finished_at
FROM api_sync_logs
ORDER BY started_at DESC
LIMIT 20;
" > "${API_RECENT_OUT}"

  smoke_db_query "
SELECT
  count(*)::bigint AS rows,
  count(DISTINCT source_type)::bigint AS source_type_count,
  count(DISTINCT api_category)::bigint AS api_category_count,
  coalesce(to_char(max(fetched_at), 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') AS latest_fetched_at,
  coalesce(to_char(max(updated_at), 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') AS latest_updated_at,
  (SELECT count(*)::bigint FROM welfare_services) AS welfare_service_total
FROM raw_api_payloads;
" > "${RAW_SUMMARY_OUT}"

  smoke_db_query "
SELECT
  source_type,
  api_category,
  count(*)::bigint AS rows,
  coalesce(to_char(max(fetched_at), 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') AS latest_fetched_at,
  coalesce(to_char(max(updated_at), 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') AS latest_updated_at
FROM raw_api_payloads
GROUP BY source_type, api_category
ORDER BY latest_updated_at DESC, rows DESC, source_type, api_category
LIMIT 50;
" > "${RAW_BREAKDOWN_OUT}"

  smoke_db_query "
SELECT
  circuit_key,
  coalesce(to_char(open_until, 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') AS open_until,
  CASE WHEN open_until > now() THEN 'true' ELSE 'false' END AS open,
  coalesce(to_char(updated_at, 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') AS updated_at
FROM collect_runtime_statuses
ORDER BY updated_at DESC
LIMIT 50;
" > "${RUNTIME_STATUS_OUT}"

  smoke_db_query "
SELECT
  lock_name,
  owner_token,
  coalesce(to_char(locked_until, 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') AS locked_until,
  CASE WHEN locked_until > now() THEN 'true' ELSE 'false' END AS active,
  coalesce(to_char(acquired_at, 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') AS acquired_at,
  coalesce(to_char(updated_at, 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') AS updated_at
FROM collect_execution_locks
ORDER BY locked_until DESC
LIMIT 50;
" > "${LOCKS_OUT}"
else
  : > "${API_SUMMARY_OUT}"
  : > "${API_BREAKDOWN_OUT}"
  : > "${API_RECENT_OUT}"
  : > "${RAW_SUMMARY_OUT}"
  : > "${RAW_BREAKDOWN_OUT}"
  : > "${RUNTIME_STATUS_OUT}"
  : > "${LOCKS_OUT}"
fi

python3 - \
  "${SOURCE_AVAILABLE_OUT}" \
  "${API_SUMMARY_OUT}" \
  "${API_BREAKDOWN_OUT}" \
  "${API_RECENT_OUT}" \
  "${RAW_SUMMARY_OUT}" \
  "${RAW_BREAKDOWN_OUT}" \
  "${RUNTIME_STATUS_OUT}" \
  "${LOCKS_OUT}" \
  "${SUMMARY_OUT}" \
  "${JSON_OUT}" \
  "${NOTE_OUT}" \
  "${ARTIFACT_DIR}" \
  "${SUMMARY_WINDOW_DAYS}" <<'PY'
import json
import sys
from pathlib import Path

(
    source_available_path,
    api_summary_path,
    api_breakdown_path,
    api_recent_path,
    raw_summary_path,
    raw_breakdown_path,
    runtime_status_path,
    locks_path,
    summary_out_path,
    json_out_path,
    note_out_path,
    artifact_dir,
    summary_window_days,
) = sys.argv[1:14]

def first_row(path):
    text = Path(path).read_text(encoding="utf-8")
    lines = text.splitlines()
    if not lines:
        return []
    return lines[0].split("\t")

def rows(path):
    text = Path(path).read_text(encoding="utf-8")
    lines = text.splitlines()
    if not lines:
        return []
    return [line.split("\t") for line in lines]

def as_int(value):
    if value is None or value == "":
        return 0
    return int(value)

source_available_row = first_row(source_available_path)
source_available = source_available_row[0] == "true" if source_available_row else False
source_missing = source_available_row[1] if len(source_available_row) > 1 else ""

api_summary = first_row(api_summary_path)
if api_summary:
    (
        api_runs,
        api_success_runs,
        api_partial_runs,
        api_failed_runs,
        api_requested,
        api_saved,
        api_skipped,
        api_filtered,
        api_failed,
        api_latest_started_at,
        api_latest_finished_at,
    ) = api_summary
else:
    api_runs = api_success_runs = api_partial_runs = api_failed_runs = "0"
    api_requested = api_saved = api_skipped = api_filtered = api_failed = "0"
    api_latest_started_at = api_latest_finished_at = ""

raw_summary = first_row(raw_summary_path)
if raw_summary:
    (
        raw_rows,
        raw_source_type_count,
        raw_api_category_count,
        raw_latest_fetched_at,
        raw_latest_updated_at,
        welfare_service_total,
    ) = raw_summary
else:
    raw_rows = raw_source_type_count = raw_api_category_count = welfare_service_total = "0"
    raw_latest_fetched_at = raw_latest_updated_at = ""

runtime_rows = rows(runtime_status_path)
lock_rows = rows(locks_path)
open_circuits = [row[0] for row in runtime_rows if len(row) >= 3 and row[2] == "true"]
active_locks = [row[0] for row in lock_rows if len(row) >= 4 and row[3] == "true"]
api_breakdown_rows = rows(api_breakdown_path)
raw_breakdown_rows = rows(raw_breakdown_path)
api_recent_rows = rows(api_recent_path)

api_runs_i = as_int(api_runs)
api_failed_runs_i = as_int(api_failed_runs)
api_partial_runs_i = as_int(api_partial_runs)
raw_rows_i = as_int(raw_rows)

if not source_available:
    decision_class = "SOURCE_UNAVAILABLE"
    operator_reading = "Required collect DB tables are missing. Apply runtime schema patch before interpreting collect health."
    next_action = "bash deploy/postgres/apply-local-runtime-schema-patch.sh"
elif api_runs_i == 0 and raw_rows_i == 0:
    decision_class = "NO_COLLECT_HISTORY"
    operator_reading = "Collect tables exist, but this local DB has no recent api_sync_logs or raw_api_payloads. Run a bounded collect smoke before drawing source quality conclusions."
    next_action = "bash deploy/smoke/run-local-collect-governance-observation-suite.sh"
elif active_locks:
    decision_class = "ACTIVE_LOCK_REVIEW"
    operator_reading = "One or more collect locks are still active. Do not start another external API collect until the current run finishes or the lock is verified stale."
    next_action = "docs/collect/collect-ops.md"
elif open_circuits or api_failed_runs_i > 0:
    decision_class = "ATTENTION_REQUIRED"
    operator_reading = "Recent failed collect runs or open collect circuits exist. Review latest api_sync_logs and source resilience before treating the baseline as healthy."
    next_action = "bash deploy/smoke/run-local-admin-collect-failures-smoke.sh"
elif api_partial_runs_i > 0:
    decision_class = "PARTIAL_SUCCESS_REVIEW"
    operator_reading = "Recent collect runs include partial success. Review failed_count, error_code, and raw payload freshness by source before changing collect budgets."
    next_action = "docs/collect/collect-detail-execution-contract.md"
else:
    decision_class = "BASELINE_HEALTHY"
    operator_reading = "Collect external API DB smoke is healthy for the selected window. Recent runs, raw payload freshness, circuits, and locks are within the expected baseline."
    next_action = "docs/collect/collect-current-state.md"

summary_lines = [
    "collect_external_api_smoke=passed",
    f"artifact_dir={artifact_dir}",
    f"summary_window_days={summary_window_days}",
    f"source_available={str(source_available).lower()}",
    f"source_missing={source_missing}",
    f"api_sync_log_runs_{summary_window_days}d={api_runs_i}",
    f"api_sync_success_runs_{summary_window_days}d={as_int(api_success_runs)}",
    f"api_sync_partial_runs_{summary_window_days}d={api_partial_runs_i}",
    f"api_sync_failed_runs_{summary_window_days}d={api_failed_runs_i}",
    f"api_sync_requested_{summary_window_days}d={as_int(api_requested)}",
    f"api_sync_saved_{summary_window_days}d={as_int(api_saved)}",
    f"api_sync_skipped_{summary_window_days}d={as_int(api_skipped)}",
    f"api_sync_filtered_{summary_window_days}d={as_int(api_filtered)}",
    f"api_sync_failed_items_{summary_window_days}d={as_int(api_failed)}",
    f"api_sync_latest_started_at={api_latest_started_at}",
    f"raw_payload_total={raw_rows_i}",
    f"raw_payload_source_type_count={as_int(raw_source_type_count)}",
    f"raw_payload_api_category_count={as_int(raw_api_category_count)}",
    f"raw_payload_latest_fetched_at={raw_latest_fetched_at}",
    f"welfare_service_total={as_int(welfare_service_total)}",
    f"api_sync_breakdown_rows={len(api_breakdown_rows)}",
    f"api_sync_recent_rows={len(api_recent_rows)}",
    f"raw_payload_breakdown_rows={len(raw_breakdown_rows)}",
    f"collect_runtime_status_rows={len(runtime_rows)}",
    f"open_collect_circuit_keys={','.join(open_circuits) if open_circuits else '(none)'}",
    f"collect_execution_lock_rows={len(lock_rows)}",
    f"active_collect_lock_keys={','.join(active_locks) if active_locks else '(none)'}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    f"next_action={next_action}",
]
Path(summary_out_path).write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": artifact_dir,
    "summary_window_days": int(summary_window_days),
    "source_available": source_available,
    "source_missing": source_missing,
    "api_sync": {
        "runs": api_runs_i,
        "success_runs": as_int(api_success_runs),
        "partial_runs": api_partial_runs_i,
        "failed_runs": api_failed_runs_i,
        "requested": as_int(api_requested),
        "saved": as_int(api_saved),
        "skipped": as_int(api_skipped),
        "filtered": as_int(api_filtered),
        "failed_items": as_int(api_failed),
        "latest_started_at": api_latest_started_at,
        "latest_finished_at": api_latest_finished_at,
    },
    "raw_payload": {
        "total": raw_rows_i,
        "source_type_count": as_int(raw_source_type_count),
        "api_category_count": as_int(raw_api_category_count),
        "latest_fetched_at": raw_latest_fetched_at,
        "latest_updated_at": raw_latest_updated_at,
    },
    "welfare_service_total": as_int(welfare_service_total),
    "open_collect_circuit_keys": open_circuits,
    "active_collect_lock_keys": active_locks,
    "decision_class": decision_class,
    "operator_reading": operator_reading,
    "next_action": next_action,
}
Path(json_out_path).write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Collect External API Smoke",
    "",
    f"- `decision_class`: `{decision_class}`",
    f"- `source_available`: `{str(source_available).lower()}`",
    f"- `source_missing`: `{source_missing or '(none)'}`",
    f"- `api_sync_log_runs_{summary_window_days}d`: `{api_runs_i}`",
    f"- `api_sync_failed_runs_{summary_window_days}d`: `{api_failed_runs_i}`",
    f"- `api_sync_partial_runs_{summary_window_days}d`: `{api_partial_runs_i}`",
    f"- `raw_payload_total`: `{raw_rows_i}`",
    f"- `welfare_service_total`: `{as_int(welfare_service_total)}`",
    f"- `open_collect_circuit_keys`: `{','.join(open_circuits) if open_circuits else '(none)'}`",
    f"- `active_collect_lock_keys`: `{','.join(active_locks) if active_locks else '(none)'}`",
    f"- `next_action`: `{next_action}`",
    "",
    "## Operator Reading",
    "",
    operator_reading,
]
Path(note_out_path).write_text("\n".join(note_lines) + "\n", encoding="utf-8")
PY

smoke_publish_dir_snapshot "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
smoke_publish_file "${SUMMARY_OUT}" "${LATEST_SUMMARY_LINK}"
smoke_publish_file "${JSON_OUT}" "${LATEST_JSON_LINK}"
smoke_publish_file "${NOTE_OUT}" "${LATEST_NOTE_LINK}"

cat "${SUMMARY_OUT}"
echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
echo "latest_note_link=${LATEST_NOTE_LINK}"
