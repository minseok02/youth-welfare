#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-60}"
LIMIT_PER_SOURCE="${LIMIT_PER_SOURCE:-1000}"
RUN_TARGETED_REPAIR="${RUN_TARGETED_REPAIR:-true}"
RUN_GAP_FILL="${RUN_GAP_FILL:-false}"
GAP_FILL_ROUNDS="${GAP_FILL_ROUNDS:-2}"
GAP_FILL_MAX_CALLS_PER_ROUND="${GAP_FILL_MAX_CALLS_PER_ROUND:-20}"

SOURCE_TYPES=("YOUTH" "BOKJIRO_CENTRAL" "BOKJIRO_LOCAL" "GOV24")

smoke_require_command curl
smoke_require_command python3

smoke_resolve_admin_credentials "${ROOT_DIR}"
smoke_resolve_admin_access_token "${ROOT_DIR}"

: "${ADMIN_EMAIL:?ADMIN_EMAIL is empty; export ADMIN_EMAIL or set /tmp/youth-welfare-admin-smoke-email}"
if [[ -z "${ADMIN_ACCESS_TOKEN:-}" ]]; then
  : "${ADMIN_PASSWORD:?ADMIN_PASSWORD is empty; export ADMIN_PASSWORD or set /tmp/youth-welfare-admin-smoke-password}"
fi

RUN_TARGETED_REPAIR="$(smoke_normalize_bool "${RUN_TARGETED_REPAIR}")"
RUN_GAP_FILL="$(smoke_normalize_bool "${RUN_GAP_FILL}")"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

HEALTH_RESPONSE="${TMP_DIR}/health.json"
HEALTH_STDERR="${TMP_DIR}/health.stderr"
LOGIN_RESPONSE="${TMP_DIR}/login.json"
SUMMARY_FILE="${TMP_DIR}/summary.tsv"
TARGETED_FILE="${TMP_DIR}/targeted.tsv"
FINAL_REMAINING_FILE="${TMP_DIR}/remaining.tsv"

smoke_print_step "app health"
smoke_wait_for_health "${HEALTH_RETRY_COUNT}" 2 "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${HEALTH_STDERR}" >/dev/null

if [[ -n "${ADMIN_ACCESS_TOKEN:-}" ]]; then
  smoke_print_step "admin token reuse (${ADMIN_EMAIL})"
  ADMIN_TOKEN="${ADMIN_ACCESS_TOKEN}"
else
  smoke_print_step "admin login (${ADMIN_EMAIL})"
  LOGIN_STATUS="$(
    smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${LOGIN_RESPONSE}" \
      -H 'Content-Type: application/json' \
      --data-binary @- <<JSON
{
  "email": "${ADMIN_EMAIL}",
  "password": "${ADMIN_PASSWORD}"
}
JSON
  )"
  smoke_assert_status 200 "${LOGIN_STATUS}" "admin login" "${LOGIN_RESPONSE}"
  ADMIN_TOKEN="$(python3 - "${LOGIN_RESPONSE}" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

print(payload["data"]["accessToken"])
PY
  )"
fi

db_inverted_count() {
  local source_type="$1"
  local count
  count="$(smoke_db_query "
select count(*)
from welfare_services
where source_type = '${source_type}'
  and min_age is not null
  and max_age is not null
  and min_age > max_age;
")"
  printf '%s' "${count:-0}"
}

db_remaining_rows() {
  smoke_db_query "
select source_type, source_id, min_age, max_age
from welfare_services
where min_age is not null
  and max_age is not null
  and min_age > max_age
order by source_type, source_id;
"
}

call_admin_endpoint() {
  local method="$1"
  local path="$2"
  local output_file="$3"
  local status

  status="$(
    smoke_http_status "${method}" "${APP_BASE_URL}${path}" "${output_file}" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}"
  )"
  smoke_assert_status 200 "${status}" "${path}" "${output_file}"
}

extract_backfill_metrics() {
  local response_file="$1"

  python3 - "${response_file}" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)["data"]

print("\t".join([
    str(payload["scannedCount"]),
    str(payload["repairedCount"]),
    str(payload["missingRawPayloadCount"]),
    str(payload["unrepairedCount"]),
    str(payload["failedCount"]),
]))
PY
}

summarize_bokjiro_detail_coverage() {
  smoke_db_query "
with bokjiro_services as (
  select id, source_type, source_id
  from welfare_services
  where source_type in ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
),
detail_rows as (
  select service_id
  from welfare_service_details
),
detail_raw as (
  select source_type, source_id
  from raw_api_payloads
  where api_category = 'DETAIL'
    and source_type in ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
)
select bs.source_type,
       count(*) as total_services,
       count(dr.service_id) as detail_row_services,
       count(case when drx.source_id is not null then 1 end) as detail_raw_services,
       count(case when dr.service_id is null then 1 end) as missing_detail_rows,
       count(case when drx.source_id is null then 1 end) as missing_detail_raw
from bokjiro_services bs
left join detail_rows dr
  on dr.service_id = bs.id
left join detail_raw drx
  on drx.source_type = bs.source_type
 and drx.source_id = bs.source_id
group by bs.source_type
order by bs.source_type;
"
}

run_targeted_repair() {
  : > "${TARGETED_FILE}"

  while IFS=$'\t' read -r source_type source_id min_age max_age; do
    [[ -n "${source_type}" ]] || continue

    local path=""
    case "${source_type}" in
      BOKJIRO_CENTRAL|BOKJIRO_LOCAL)
        path="/api/admin/collect/bokjiro-details-refresh?sourceId=${source_id}"
        ;;
      GOV24)
        path="/api/admin/collect/gov24-details?sourceId=${source_id}"
        ;;
      *)
        printf '%s\t%s\t%s\t%s\t%s\n' \
          "${source_type}" "${source_id}" "${min_age}" "${max_age}" "UNSUPPORTED_SINGLE_SOURCE_REPAIR" >> "${TARGETED_FILE}"
        continue
        ;;
    esac

    local response_file="${TMP_DIR}/targeted-${source_type}-${source_id}.json"
    call_admin_endpoint POST "${path}" "${response_file}"
    printf '%s\t%s\t%s\t%s\t%s\n' \
      "${source_type}" "${source_id}" "${min_age}" "${max_age}" "${path}" >> "${TARGETED_FILE}"
  done < <(db_remaining_rows)
}

print_summary_table() {
  if [[ ! -s "${SUMMARY_FILE}" ]]; then
    echo "no summary rows collected" >&2
    return 1
  fi

  echo "source_type	before_count	scanned	repaired	missing_raw	unrepaired	failed	after_backfill	after_targeted"
  cat "${SUMMARY_FILE}"
}

: > "${SUMMARY_FILE}"

for source_type in "${SOURCE_TYPES[@]}"; do
  smoke_print_step "inverted age backfill (${source_type})"

  before_count="$(db_inverted_count "${source_type}")"
  response_file="${TMP_DIR}/backfill-${source_type}.json"
  call_admin_endpoint POST "/api/admin/collect/inverted-age-backfill?sourceType=${source_type}&limitPerSource=${LIMIT_PER_SOURCE}" "${response_file}"

  IFS=$'\t' read -r scanned repaired missing_raw unrepaired failed < <(extract_backfill_metrics "${response_file}")
  after_backfill="$(db_inverted_count "${source_type}")"

  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "${source_type}" \
    "${before_count}" \
    "${scanned}" \
    "${repaired}" \
    "${missing_raw}" \
    "${unrepaired}" \
    "${failed}" \
    "${after_backfill}" \
    "pending" >> "${SUMMARY_FILE}"
done

if [[ "${RUN_TARGETED_REPAIR}" == "true" ]]; then
  smoke_print_step "targeted single-source repair for remaining GOV24/BOKJIRO rows"
  run_targeted_repair
else
  : > "${TARGETED_FILE}"
fi

db_remaining_rows > "${FINAL_REMAINING_FILE}"

updated_summary="${TMP_DIR}/summary-updated.tsv"
: > "${updated_summary}"
while IFS=$'\t' read -r source_type before_count scanned repaired missing_raw unrepaired failed after_backfill _; do
  after_targeted="$(db_inverted_count "${source_type}")"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "${source_type}" \
    "${before_count}" \
    "${scanned}" \
    "${repaired}" \
    "${missing_raw}" \
    "${unrepaired}" \
    "${failed}" \
    "${after_backfill}" \
    "${after_targeted}" >> "${updated_summary}"
done < "${SUMMARY_FILE}"
mv "${updated_summary}" "${SUMMARY_FILE}"

if [[ "${RUN_GAP_FILL}" == "true" ]]; then
  smoke_print_step "bokjiro detail gap-fill (${GAP_FILL_ROUNDS} rounds x ${GAP_FILL_MAX_CALLS_PER_ROUND} calls)"
  gap_fill_response="${TMP_DIR}/gap-fill.json"
  call_admin_endpoint POST \
    "/api/admin/collect/bokjiro-details-gap-fill?rounds=${GAP_FILL_ROUNDS}&maxCallsPerRound=${GAP_FILL_MAX_CALLS_PER_ROUND}" \
    "${gap_fill_response}"
fi

smoke_print_step "summary"
print_summary_table

echo
echo "remaining_inverted_rows:"
if [[ -s "${FINAL_REMAINING_FILE}" ]]; then
  cat "${FINAL_REMAINING_FILE}"
else
  echo "(none)"
fi

echo
echo "bokjiro_detail_coverage:"
summarize_bokjiro_detail_coverage

if [[ -s "${TARGETED_FILE}" ]]; then
  echo
  echo "targeted_repairs_executed:"
  cat "${TARGETED_FILE}"
fi

if [[ -s "${FINAL_REMAINING_FILE}" ]]; then
  unsupported_count="$(awk -F'\t' '$1 == "YOUTH" {count++} END {print count+0}' "${FINAL_REMAINING_FILE}")"
  supported_count="$(awk -F'\t' '$1 != "YOUTH" {count++} END {print count+0}' "${FINAL_REMAINING_FILE}")"
  if (( supported_count > 0 )); then
    echo "supported source types still have inverted rows after targeted repair" >&2
    exit 1
  fi
  if (( unsupported_count > 0 )); then
    echo "youth rows still remain; rerun /api/admin/collect/youth-details or broader YOUTH refresh path" >&2
    exit 1
  fi
fi

echo
echo "collect_legacy_repair_suite=passed"
echo "app_base_url=${APP_BASE_URL}"
