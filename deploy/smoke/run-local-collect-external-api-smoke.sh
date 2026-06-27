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
STORAGE_PARITY_OUT="${ARTIFACT_DIR}/collect-storage-parity.tsv"
SIDECAR_PARITY_OUT="${ARTIFACT_DIR}/collect-sidecar-parity.tsv"
DETAIL_SUPPORT_COVERAGE_OUT="${ARTIFACT_DIR}/collect-detail-support-coverage.tsv"
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
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
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
    ('welfare_services'),
    ('welfare_service_details'),
    ('service_taxonomies'),
    ('service_taxonomy_terms'),
    ('service_taxonomy_summary_slots'),
    ('service_facts')
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
WITH expected_sources(source_type) AS (
  VALUES
    ('YOUTH'),
    ('BOKJIRO_CENTRAL'),
    ('BOKJIRO_LOCAL'),
    ('GOV24')
),
api_by_source AS (
  SELECT
    CASE
      WHEN job_name IN ('YOUTH', 'YOUTH_DETAILS') THEN 'YOUTH'
      WHEN job_name = 'BOKJIRO_CENTRAL' THEN 'BOKJIRO_CENTRAL'
      WHEN job_name = 'BOKJIRO_LOCAL' THEN 'BOKJIRO_LOCAL'
      WHEN job_name LIKE 'GOV24%' THEN 'GOV24'
      ELSE NULL
    END AS source_type,
    count(*) FILTER (WHERE status = 'SUCCESS')::bigint AS success_runs,
    count(*) FILTER (WHERE status = 'PARTIAL_SUCCESS')::bigint AS partial_runs,
    count(*) FILTER (WHERE status = 'FAILED')::bigint AS failed_runs,
    coalesce(sum(requested_count), 0)::bigint AS requested,
    coalesce(sum(saved_count), 0)::bigint AS saved,
    coalesce(to_char(max(started_at), 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') AS latest_started_at
  FROM api_sync_logs
  WHERE started_at >= now() - (${SUMMARY_WINDOW_DAYS} || ' days')::interval
  GROUP BY 1
),
raw_by_source AS (
  SELECT
    source_type::text AS source_type,
    count(*)::bigint AS raw_rows,
    coalesce(to_char(max(updated_at), 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') AS latest_raw_updated_at
  FROM raw_api_payloads
  GROUP BY source_type::text
),
welfare_by_source AS (
  SELECT
    source_type::text AS source_type,
    count(*)::bigint AS welfare_rows,
    coalesce(to_char(max(updated_at), 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') AS latest_welfare_updated_at
  FROM welfare_services
  GROUP BY source_type::text
)
SELECT
  es.source_type,
  coalesce(abs.success_runs, 0)::text,
  coalesce(abs.partial_runs, 0)::text,
  coalesce(abs.failed_runs, 0)::text,
  coalesce(abs.requested, 0)::text,
  coalesce(abs.saved, 0)::text,
  coalesce(rbs.raw_rows, 0)::text,
  coalesce(wbs.welfare_rows, 0)::text,
  coalesce(abs.latest_started_at, ''),
  coalesce(rbs.latest_raw_updated_at, ''),
  coalesce(wbs.latest_welfare_updated_at, '')
FROM expected_sources es
LEFT JOIN api_by_source abs ON abs.source_type = es.source_type
LEFT JOIN raw_by_source rbs ON rbs.source_type = es.source_type
LEFT JOIN welfare_by_source wbs ON wbs.source_type = es.source_type
ORDER BY es.source_type;
" > "${STORAGE_PARITY_OUT}"

  smoke_db_query "
WITH expected_sources(source_type) AS (
  VALUES
    ('YOUTH'),
    ('BOKJIRO_CENTRAL'),
    ('BOKJIRO_LOCAL'),
    ('GOV24')
),
api_by_source AS (
  SELECT
    CASE
      WHEN job_name IN ('YOUTH', 'YOUTH_DETAILS') THEN 'YOUTH'
      WHEN job_name = 'BOKJIRO_CENTRAL' THEN 'BOKJIRO_CENTRAL'
      WHEN job_name = 'BOKJIRO_LOCAL' THEN 'BOKJIRO_LOCAL'
      WHEN job_name LIKE 'GOV24%' THEN 'GOV24'
      ELSE NULL
    END AS source_type,
    count(*) FILTER (WHERE status = 'SUCCESS')::bigint AS success_runs,
    coalesce(sum(saved_count), 0)::bigint AS saved
  FROM api_sync_logs
  WHERE started_at >= now() - (${SUMMARY_WINDOW_DAYS} || ' days')::interval
  GROUP BY 1
),
sidecar_by_source AS (
  SELECT
    ws.source_type::text AS source_type,
    count(DISTINCT ws.id)::bigint AS welfare_rows,
    count(DISTINCT stx.service_id)::bigint AS taxonomy_services,
    count(DISTINCT stss.service_id)::bigint AS summary_slot_services,
    count(DISTINCT stt.service_id)::bigint AS taxonomy_term_services,
    count(DISTINCT sf.service_id)::bigint AS fact_services
  FROM welfare_services ws
  LEFT JOIN service_taxonomies stx ON stx.service_id = ws.id
  LEFT JOIN service_taxonomy_summary_slots stss ON stss.service_id = ws.id
  LEFT JOIN service_taxonomy_terms stt ON stt.service_id = ws.id
  LEFT JOIN service_facts sf ON sf.service_id = ws.id
  GROUP BY ws.source_type::text
),
gov24_missing_summary AS (
  SELECT count(*)::bigint AS missing_services
  FROM (
    SELECT ws.id
    FROM welfare_services ws
    LEFT JOIN service_taxonomy_summary_slots stss
      ON stss.service_id = ws.id
     AND stss.slot_key IN ('GOV24_SERVICE_FIELD','GOV24_USER_TYPE','GOV24_BENEFIT_TYPE')
    WHERE ws.source_type = 'GOV24'
      AND EXISTS (
        SELECT 1
        FROM raw_api_payloads rap
        WHERE rap.source_type = ws.source_type
          AND rap.source_id = ws.source_id
          AND rap.api_category = 'LIST'
      )
    GROUP BY ws.id
    HAVING count(DISTINCT stss.slot_key) < 3
  ) gaps
)
SELECT
  es.source_type,
  coalesce(abs.success_runs, 0)::text,
  coalesce(abs.saved, 0)::text,
  coalesce(sbs.welfare_rows, 0)::text,
  coalesce(sbs.taxonomy_services, 0)::text,
  coalesce(sbs.summary_slot_services, 0)::text,
  coalesce(sbs.taxonomy_term_services, 0)::text,
  coalesce(sbs.fact_services, 0)::text,
  CASE WHEN es.source_type = 'GOV24'
       THEN coalesce((SELECT missing_services FROM gov24_missing_summary), 0)
       ELSE 0
  END::text
FROM expected_sources es
LEFT JOIN api_by_source abs ON abs.source_type = es.source_type
LEFT JOIN sidecar_by_source sbs ON sbs.source_type = es.source_type
ORDER BY es.source_type;
" > "${SIDECAR_PARITY_OUT}"

  smoke_db_query "
WITH lanes(
  lane_key,
  source_type,
  raw_category,
  expected_detail_raw,
  expected_detail_row,
  expected_support_raw,
  expected_support_fact
) AS (
  VALUES
    ('YOUTH_DETAIL', 'YOUTH', 'DETAIL', true, false, false, false),
    ('BOKJIRO_CENTRAL_DETAIL', 'BOKJIRO_CENTRAL', 'DETAIL', true, true, false, false),
    ('BOKJIRO_LOCAL_DETAIL', 'BOKJIRO_LOCAL', 'DETAIL', true, true, false, false),
    ('GOV24_DETAIL', 'GOV24', 'DETAIL', true, true, false, false),
    ('GOV24_SUPPORT', 'GOV24', 'SUPPORT', false, false, true, true)
),
lane_jobs(lane_key, job_name) AS (
  VALUES
    ('YOUTH_DETAIL', 'YOUTH_DETAILS'),
    ('BOKJIRO_CENTRAL_DETAIL', 'BOKJIRO_DETAIL'),
    ('BOKJIRO_CENTRAL_DETAIL', 'BOKJIRO_DETAIL_REFRESH'),
    ('BOKJIRO_CENTRAL_DETAIL', 'BOKJIRO_DETAIL_GAP_FILL'),
    ('BOKJIRO_LOCAL_DETAIL', 'BOKJIRO_DETAIL'),
    ('BOKJIRO_LOCAL_DETAIL', 'BOKJIRO_DETAIL_REFRESH'),
    ('BOKJIRO_LOCAL_DETAIL', 'BOKJIRO_DETAIL_GAP_FILL'),
    ('GOV24_DETAIL', 'GOV24_DETAIL'),
    ('GOV24_SUPPORT', 'GOV24_SUPPORT_CONDITIONS')
),
api_by_lane AS (
  SELECT
    lj.lane_key,
    count(*) FILTER (WHERE asl.status = 'SUCCESS')::bigint AS success_runs,
    coalesce(sum(asl.requested_count), 0)::bigint AS requested,
    coalesce(sum(asl.saved_count), 0)::bigint AS saved,
    coalesce(to_char(max(asl.started_at), 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') AS latest_started_at
  FROM lane_jobs lj
  JOIN api_sync_logs asl ON asl.job_name = lj.job_name
  WHERE asl.started_at >= now() - (${SUMMARY_WINDOW_DAYS} || ' days')::interval
  GROUP BY lj.lane_key
),
service_by_source AS (
  SELECT
    source_type::text AS source_type,
    count(*)::bigint AS service_rows
  FROM welfare_services
  GROUP BY source_type::text
),
list_raw_by_source AS (
  SELECT
    source_type::text AS source_type,
    count(DISTINCT source_id)::bigint AS list_raw_services
  FROM raw_api_payloads
  WHERE api_category = 'LIST'
  GROUP BY source_type::text
),
raw_by_lane AS (
  SELECT
    l.lane_key,
    count(DISTINCT rap.source_id)::bigint AS raw_services
  FROM lanes l
  LEFT JOIN raw_api_payloads rap
    ON rap.source_type::text = l.source_type
   AND rap.api_category::text = l.raw_category
  GROUP BY l.lane_key
),
detail_row_by_source AS (
  SELECT
    ws.source_type::text AS source_type,
    count(DISTINCT wsd.service_id)::bigint AS detail_row_services
  FROM welfare_service_details wsd
  JOIN welfare_services ws ON ws.id = wsd.service_id
  GROUP BY ws.source_type::text
),
support_fact_by_source AS (
  SELECT
    ws.source_type::text AS source_type,
    count(DISTINCT sf.service_id)::bigint AS support_fact_services
  FROM service_facts sf
  JOIN welfare_services ws ON ws.id = sf.service_id
  WHERE sf.fact_code_set_key = 'GOV24_SUPPORT_CONDITION'
  GROUP BY ws.source_type::text
)
SELECT
  l.lane_key,
  l.source_type,
  l.raw_category,
  coalesce(sbs.service_rows, 0)::text AS service_rows,
  coalesce(lrs.list_raw_services, 0)::text AS list_raw_services,
  CASE WHEN l.expected_detail_raw THEN coalesce(rbl.raw_services, 0) ELSE 0 END::text AS detail_raw_services,
  CASE WHEN l.expected_detail_row THEN coalesce(drs.detail_row_services, 0) ELSE 0 END::text AS detail_row_services,
  CASE WHEN l.expected_support_raw THEN coalesce(rbl.raw_services, 0) ELSE 0 END::text AS support_raw_services,
  CASE WHEN l.expected_support_fact THEN coalesce(sfs.support_fact_services, 0) ELSE 0 END::text AS support_fact_services,
  CASE WHEN l.expected_detail_raw
       THEN greatest(coalesce(sbs.service_rows, 0) - coalesce(rbl.raw_services, 0), 0)
       ELSE 0
  END::text AS missing_detail_raw_services,
  CASE WHEN l.expected_detail_row
       THEN greatest(coalesce(sbs.service_rows, 0) - coalesce(drs.detail_row_services, 0), 0)
       ELSE 0
  END::text AS missing_detail_row_services,
  CASE WHEN l.expected_support_raw
       THEN greatest(coalesce(sbs.service_rows, 0) - coalesce(rbl.raw_services, 0), 0)
       ELSE 0
  END::text AS missing_support_raw_services,
  CASE WHEN l.expected_support_fact
       THEN greatest(coalesce(sbs.service_rows, 0) - coalesce(sfs.support_fact_services, 0), 0)
       ELSE 0
  END::text AS missing_support_fact_services,
  coalesce(abl.success_runs, 0)::text,
  coalesce(abl.requested, 0)::text,
  coalesce(abl.saved, 0)::text,
  coalesce(abl.latest_started_at, ''),
  l.expected_detail_raw::text,
  l.expected_detail_row::text,
  l.expected_support_raw::text,
  l.expected_support_fact::text
FROM lanes l
LEFT JOIN service_by_source sbs ON sbs.source_type = l.source_type
LEFT JOIN list_raw_by_source lrs ON lrs.source_type = l.source_type
LEFT JOIN raw_by_lane rbl ON rbl.lane_key = l.lane_key
LEFT JOIN detail_row_by_source drs ON drs.source_type = l.source_type
LEFT JOIN support_fact_by_source sfs ON sfs.source_type = l.source_type
LEFT JOIN api_by_lane abl ON abl.lane_key = l.lane_key
ORDER BY l.lane_key;
" > "${DETAIL_SUPPORT_COVERAGE_OUT}"

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
  : > "${STORAGE_PARITY_OUT}"
  : > "${SIDECAR_PARITY_OUT}"
  : > "${DETAIL_SUPPORT_COVERAGE_OUT}"
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
  "${STORAGE_PARITY_OUT}" \
  "${SIDECAR_PARITY_OUT}" \
  "${DETAIL_SUPPORT_COVERAGE_OUT}" \
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
    storage_parity_path,
    sidecar_parity_path,
    detail_support_coverage_path,
    runtime_status_path,
    locks_path,
    summary_out_path,
    json_out_path,
    note_out_path,
    artifact_dir,
    summary_window_days,
) = sys.argv[1:17]

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
storage_parity_rows = rows(storage_parity_path)
sidecar_parity_rows = rows(sidecar_parity_path)
detail_support_coverage_rows = rows(detail_support_coverage_path)
api_recent_rows = rows(api_recent_path)

api_runs_i = as_int(api_runs)
api_failed_runs_i = as_int(api_failed_runs)
api_partial_runs_i = as_int(api_partial_runs)
raw_rows_i = as_int(raw_rows)

sources_with_api_success_without_raw_payload = []
sources_with_api_success_without_welfare_rows = []
storage_mismatch_sources = []
for row in storage_parity_rows:
    if len(row) < 8:
        continue
    source_type = row[0]
    success_runs = as_int(row[1])
    saved_count = as_int(row[5])
    raw_count = as_int(row[6])
    welfare_count = as_int(row[7])
    if success_runs > 0 and saved_count > 0 and raw_count == 0:
        sources_with_api_success_without_raw_payload.append(source_type)
    if success_runs > 0 and saved_count > 0 and welfare_count == 0:
        sources_with_api_success_without_welfare_rows.append(source_type)
storage_mismatch_sources = sorted(set(
    sources_with_api_success_without_raw_payload + sources_with_api_success_without_welfare_rows
))

sources_with_recent_success_without_taxonomy = []
sources_with_gov24_missing_required_summary_slots = []
sidecar_mismatch_sources = []
for row in sidecar_parity_rows:
    if len(row) < 9:
        continue
    source_type = row[0]
    success_runs = as_int(row[1])
    saved_count = as_int(row[2])
    welfare_count = as_int(row[3])
    taxonomy_count = as_int(row[4])
    gov24_missing_required_summary_slot_count = as_int(row[8])
    if success_runs > 0 and saved_count > 0 and welfare_count > 0 and taxonomy_count == 0:
        sources_with_recent_success_without_taxonomy.append(source_type)
    if source_type == "GOV24" and gov24_missing_required_summary_slot_count > 0:
        sources_with_gov24_missing_required_summary_slots.append(source_type)
sidecar_mismatch_sources = sorted(set(
    sources_with_recent_success_without_taxonomy + sources_with_gov24_missing_required_summary_slots
))

lanes_with_recent_success_without_detail_raw = []
lanes_with_recent_success_without_detail_rows = []
lanes_with_recent_success_without_support_raw = []
lanes_with_recent_success_without_support_facts = []
detail_support_coverage_mismatch_lanes = []
for row in detail_support_coverage_rows:
    if len(row) < 21:
        continue
    lane_key = row[0]
    service_count = as_int(row[3])
    detail_raw_count = as_int(row[5])
    detail_row_count = as_int(row[6])
    support_raw_count = as_int(row[7])
    support_fact_count = as_int(row[8])
    success_runs = as_int(row[13])
    saved_count = as_int(row[15])
    expected_detail_raw = row[17] == "true"
    expected_detail_row = row[18] == "true"
    expected_support_raw = row[19] == "true"
    expected_support_fact = row[20] == "true"
    has_recent_saved_success = success_runs > 0 and saved_count > 0 and service_count > 0
    if has_recent_saved_success and expected_detail_raw and detail_raw_count == 0:
        lanes_with_recent_success_without_detail_raw.append(lane_key)
    if has_recent_saved_success and expected_detail_row and detail_row_count == 0:
        lanes_with_recent_success_without_detail_rows.append(lane_key)
    if has_recent_saved_success and expected_support_raw and support_raw_count == 0:
        lanes_with_recent_success_without_support_raw.append(lane_key)
    if has_recent_saved_success and expected_support_fact and support_fact_count == 0:
        lanes_with_recent_success_without_support_facts.append(lane_key)
detail_support_coverage_mismatch_lanes = sorted(set(
    lanes_with_recent_success_without_detail_raw
    + lanes_with_recent_success_without_detail_rows
    + lanes_with_recent_success_without_support_raw
    + lanes_with_recent_success_without_support_facts
))

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
elif storage_mismatch_sources:
    decision_class = "STORAGE_PARITY_REVIEW"
    operator_reading = (
        "Recent source collect success exists, but at least one source has no raw payload or welfare service rows. "
        "Compare api_sync_logs with raw_api_payloads and welfare_services before treating the collect baseline as healthy."
    )
    next_action = "docs/collect/collect-external-api-smoke-runbook.md"
elif sidecar_mismatch_sources:
    decision_class = "SIDECAR_PARITY_REVIEW"
    operator_reading = (
        "Recent source collect success exists, but canonical sidecar coverage is missing or Gov24 list summary slots are incomplete. "
        "Run the bounded sidecar backfill path before using downstream replay/recommendation conclusions."
    )
    next_action = "docs/collect/collect-ops.md"
elif detail_support_coverage_mismatch_lanes:
    decision_class = "DETAIL_SUPPORT_COVERAGE_REVIEW"
    operator_reading = (
        "Recent detail/support collect success exists, but the corresponding raw, detail row, or support fact coverage is empty for at least one lane. "
        "Check detail/support coverage before treating downstream policy quality as healthy."
    )
    next_action = "docs/collect/collect-detail-execution-contract.md"
else:
    decision_class = "BASELINE_HEALTHY"
    operator_reading = "Collect external API DB smoke is healthy for the selected window. Recent runs, raw payload freshness, storage parity, sidecar parity, detail/support coverage, circuits, and locks are within the expected baseline."
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
    f"storage_parity_rows={len(storage_parity_rows)}",
    f"sources_with_api_success_without_raw_payload={','.join(sources_with_api_success_without_raw_payload) if sources_with_api_success_without_raw_payload else '(none)'}",
    f"sources_with_api_success_without_welfare_rows={','.join(sources_with_api_success_without_welfare_rows) if sources_with_api_success_without_welfare_rows else '(none)'}",
    f"storage_mismatch_sources={','.join(storage_mismatch_sources) if storage_mismatch_sources else '(none)'}",
    f"sidecar_parity_rows={len(sidecar_parity_rows)}",
    f"sources_with_recent_success_without_taxonomy={','.join(sources_with_recent_success_without_taxonomy) if sources_with_recent_success_without_taxonomy else '(none)'}",
    f"sources_with_gov24_missing_required_summary_slots={','.join(sources_with_gov24_missing_required_summary_slots) if sources_with_gov24_missing_required_summary_slots else '(none)'}",
    f"sidecar_mismatch_sources={','.join(sidecar_mismatch_sources) if sidecar_mismatch_sources else '(none)'}",
    f"detail_support_coverage_rows={len(detail_support_coverage_rows)}",
    f"detail_support_coverage_mismatch_lanes={','.join(detail_support_coverage_mismatch_lanes) if detail_support_coverage_mismatch_lanes else '(none)'}",
    f"lanes_with_recent_success_without_detail_raw={','.join(lanes_with_recent_success_without_detail_raw) if lanes_with_recent_success_without_detail_raw else '(none)'}",
    f"lanes_with_recent_success_without_detail_rows={','.join(lanes_with_recent_success_without_detail_rows) if lanes_with_recent_success_without_detail_rows else '(none)'}",
    f"lanes_with_recent_success_without_support_raw={','.join(lanes_with_recent_success_without_support_raw) if lanes_with_recent_success_without_support_raw else '(none)'}",
    f"lanes_with_recent_success_without_support_facts={','.join(lanes_with_recent_success_without_support_facts) if lanes_with_recent_success_without_support_facts else '(none)'}",
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
    "storage_parity": [
        {
            "source_type": row[0],
            "success_runs": as_int(row[1]) if len(row) > 1 else 0,
            "partial_runs": as_int(row[2]) if len(row) > 2 else 0,
            "failed_runs": as_int(row[3]) if len(row) > 3 else 0,
            "requested": as_int(row[4]) if len(row) > 4 else 0,
            "saved": as_int(row[5]) if len(row) > 5 else 0,
            "raw_rows": as_int(row[6]) if len(row) > 6 else 0,
            "welfare_rows": as_int(row[7]) if len(row) > 7 else 0,
            "latest_api_started_at": row[8] if len(row) > 8 else "",
            "latest_raw_updated_at": row[9] if len(row) > 9 else "",
            "latest_welfare_updated_at": row[10] if len(row) > 10 else "",
        }
        for row in storage_parity_rows
    ],
    "sources_with_api_success_without_raw_payload": sources_with_api_success_without_raw_payload,
    "sources_with_api_success_without_welfare_rows": sources_with_api_success_without_welfare_rows,
    "storage_mismatch_sources": storage_mismatch_sources,
    "sidecar_parity": [
        {
            "source_type": row[0],
            "success_runs": as_int(row[1]) if len(row) > 1 else 0,
            "saved": as_int(row[2]) if len(row) > 2 else 0,
            "welfare_rows": as_int(row[3]) if len(row) > 3 else 0,
            "taxonomy_services": as_int(row[4]) if len(row) > 4 else 0,
            "summary_slot_services": as_int(row[5]) if len(row) > 5 else 0,
            "taxonomy_term_services": as_int(row[6]) if len(row) > 6 else 0,
            "fact_services": as_int(row[7]) if len(row) > 7 else 0,
            "gov24_missing_required_summary_slot_services": as_int(row[8]) if len(row) > 8 else 0,
        }
        for row in sidecar_parity_rows
    ],
    "sources_with_recent_success_without_taxonomy": sources_with_recent_success_without_taxonomy,
    "sources_with_gov24_missing_required_summary_slots": sources_with_gov24_missing_required_summary_slots,
    "sidecar_mismatch_sources": sidecar_mismatch_sources,
    "detail_support_coverage": [
        {
            "lane_key": row[0],
            "source_type": row[1] if len(row) > 1 else "",
            "raw_category": row[2] if len(row) > 2 else "",
            "service_rows": as_int(row[3]) if len(row) > 3 else 0,
            "list_raw_services": as_int(row[4]) if len(row) > 4 else 0,
            "detail_raw_services": as_int(row[5]) if len(row) > 5 else 0,
            "detail_row_services": as_int(row[6]) if len(row) > 6 else 0,
            "support_raw_services": as_int(row[7]) if len(row) > 7 else 0,
            "support_fact_services": as_int(row[8]) if len(row) > 8 else 0,
            "missing_detail_raw_services": as_int(row[9]) if len(row) > 9 else 0,
            "missing_detail_row_services": as_int(row[10]) if len(row) > 10 else 0,
            "missing_support_raw_services": as_int(row[11]) if len(row) > 11 else 0,
            "missing_support_fact_services": as_int(row[12]) if len(row) > 12 else 0,
            "recent_success_runs": as_int(row[13]) if len(row) > 13 else 0,
            "recent_requested": as_int(row[14]) if len(row) > 14 else 0,
            "recent_saved": as_int(row[15]) if len(row) > 15 else 0,
            "latest_started_at": row[16] if len(row) > 16 else "",
            "expected_detail_raw": row[17] == "true" if len(row) > 17 else False,
            "expected_detail_row": row[18] == "true" if len(row) > 18 else False,
            "expected_support_raw": row[19] == "true" if len(row) > 19 else False,
            "expected_support_fact": row[20] == "true" if len(row) > 20 else False,
        }
        for row in detail_support_coverage_rows
    ],
    "detail_support_coverage_mismatch_lanes": detail_support_coverage_mismatch_lanes,
    "lanes_with_recent_success_without_detail_raw": lanes_with_recent_success_without_detail_raw,
    "lanes_with_recent_success_without_detail_rows": lanes_with_recent_success_without_detail_rows,
    "lanes_with_recent_success_without_support_raw": lanes_with_recent_success_without_support_raw,
    "lanes_with_recent_success_without_support_facts": lanes_with_recent_success_without_support_facts,
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
    f"- `storage_mismatch_sources`: `{','.join(storage_mismatch_sources) if storage_mismatch_sources else '(none)'}`",
    f"- `sources_with_api_success_without_raw_payload`: `{','.join(sources_with_api_success_without_raw_payload) if sources_with_api_success_without_raw_payload else '(none)'}`",
    f"- `sources_with_api_success_without_welfare_rows`: `{','.join(sources_with_api_success_without_welfare_rows) if sources_with_api_success_without_welfare_rows else '(none)'}`",
    f"- `sidecar_mismatch_sources`: `{','.join(sidecar_mismatch_sources) if sidecar_mismatch_sources else '(none)'}`",
    f"- `sources_with_recent_success_without_taxonomy`: `{','.join(sources_with_recent_success_without_taxonomy) if sources_with_recent_success_without_taxonomy else '(none)'}`",
    f"- `sources_with_gov24_missing_required_summary_slots`: `{','.join(sources_with_gov24_missing_required_summary_slots) if sources_with_gov24_missing_required_summary_slots else '(none)'}`",
    f"- `detail_support_coverage_mismatch_lanes`: `{','.join(detail_support_coverage_mismatch_lanes) if detail_support_coverage_mismatch_lanes else '(none)'}`",
    f"- `lanes_with_recent_success_without_detail_raw`: `{','.join(lanes_with_recent_success_without_detail_raw) if lanes_with_recent_success_without_detail_raw else '(none)'}`",
    f"- `lanes_with_recent_success_without_detail_rows`: `{','.join(lanes_with_recent_success_without_detail_rows) if lanes_with_recent_success_without_detail_rows else '(none)'}`",
    f"- `lanes_with_recent_success_without_support_raw`: `{','.join(lanes_with_recent_success_without_support_raw) if lanes_with_recent_success_without_support_raw else '(none)'}`",
    f"- `lanes_with_recent_success_without_support_facts`: `{','.join(lanes_with_recent_success_without_support_facts) if lanes_with_recent_success_without_support_facts else '(none)'}`",
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
smoke_sanitize_artifacts "${ARTIFACT_DIR}"
smoke_publish_dir_snapshot "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
smoke_publish_file "${SUMMARY_OUT}" "${LATEST_SUMMARY_LINK}"
smoke_publish_file "${JSON_OUT}" "${LATEST_JSON_LINK}"
smoke_publish_file "${NOTE_OUT}" "${LATEST_NOTE_LINK}"

cat "${SUMMARY_OUT}"
echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
echo "latest_note_link=${LATEST_NOTE_LINK}"
