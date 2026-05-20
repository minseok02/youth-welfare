#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

smoke_require_command curl
smoke_require_command docker
smoke_require_command python3

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

PROFILE_SIDO="${PROFILE_SIDO:-인천광역시}"
PROFILE_SGG="${PROFILE_SGG:-중구}"
PROFILE_INCOME_LEVEL="${PROFILE_INCOME_LEVEL:-5}"
PROFILE_EMPLOYMENT_STATUS="${PROFILE_EMPLOYMENT_STATUS:-미취업}"
PROFILE_HOUSEHOLD_TYPE="${PROFILE_HOUSEHOLD_TYPE:-1인 가구}"
TARGET_SERVICE_ID="${TARGET_SERVICE_ID:-2622}"
TOP_REFRESH_LIMIT="${TOP_REFRESH_LIMIT:-5}"
REPRESENTATIVE_USER_PASSWORD="${REPRESENTATIVE_USER_PASSWORD:-Password123!}"
EXAMPLE_USER_PASSWORD="${EXAMPLE_USER_PASSWORD:-${REPRESENTATIVE_USER_PASSWORD}}"
REAL_USER_PASSWORD="${REAL_USER_PASSWORD:-${REPRESENTATIVE_USER_PASSWORD}}"

TIMESTAMP_UTC="$(smoke_now_ts_utc)"
ARTIFACT_ROOT="${ROOT_DIR}/tmp/recommendation-same-profile-fresh-saved-differential-audit"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${TIMESTAMP_UTC}}"
mkdir -p "${ARTIFACT_DIR}"

HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
ADMIN_LOGIN_RESPONSE="${ARTIFACT_DIR}/admin-login.json"
EXAMPLE_LOGIN_RESPONSE="${ARTIFACT_DIR}/example-login.json"
REAL_USER_LOGIN_RESPONSE="${ARTIFACT_DIR}/real-user-login.json"
EXAMPLE_REFRESH_RESPONSE="${ARTIFACT_DIR}/example-refresh.json"
REAL_USER_REFRESH_RESPONSE="${ARTIFACT_DIR}/real-user-refresh.json"
EXAMPLE_BEFORE_RESPONSE="${ARTIFACT_DIR}/example-before-diagnostics.json"
EXAMPLE_AFTER_RESPONSE="${ARTIFACT_DIR}/example-after-diagnostics.json"
REAL_USER_BEFORE_RESPONSE="${ARTIFACT_DIR}/real-user-before-diagnostics.json"
REAL_USER_AFTER_RESPONSE="${ARTIFACT_DIR}/real-user-after-diagnostics.json"
SUMMARY_OUTPUT="${ARTIFACT_DIR}/same-profile-fresh-saved-differential-summary.txt"
DETAIL_OUTPUT="${ARTIFACT_DIR}/target-before-after.tsv"

extract_access_token() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

print(payload["data"]["accessToken"])
PY
}

query_exact_profile_representative() {
  local origin="$1"
  local prefer_target_top1="$2"
  local extra_clause=""
  if [[ "${prefer_target_top1}" == "true" ]]; then
    extra_clause="and rn = 1 and service_id = ${TARGET_SERVICE_ID}"
  else
    extra_clause="and rn = 1"
  fi

  smoke_db_query "
    with latest as (
      select user_key, max(recommended_at) as recommended_at
      from user_recommendations
      group by user_key
    ),
    ranked as (
      select
        ur.user_key,
        coalesce(nullif(u.account_origin, ''), 'REAL_USER') as account_origin,
        u.email,
        ur.service_id,
        ws.title,
        ur.final_score,
        row_number() over (
          partition by ur.user_key
          order by ur.final_score desc, ur.id desc
        ) as rn
      from user_recommendations ur
      join latest l
        on l.user_key = ur.user_key
       and l.recommended_at = ur.recommended_at
      join users u
        on u.user_key = ur.user_key
      join user_profiles up
        on up.user_key = u.user_key
      join welfare_services ws
        on ws.id = ur.service_id
      where up.sido = '${PROFILE_SIDO}'
        and up.sgg = '${PROFILE_SGG}'
        and up.income_level = ${PROFILE_INCOME_LEVEL}
        and up.employment_status = '${PROFILE_EMPLOYMENT_STATUS}'
        and up.household_type = '${PROFILE_HOUSEHOLD_TYPE}'
        and coalesce(nullif(u.account_origin, ''), 'REAL_USER') = '${origin}'
    )
    select user_key, email, service_id, title, final_score
    from ranked
    where 1=1
      ${extra_clause}
    order by final_score desc, user_key
    limit 1;
  "
}

query_top_service_ids_csv() {
  local user_key="$1"
  smoke_db_query "
    with target_batch as (
      select max(recommended_at) as recommended_at
      from user_recommendations
      where user_key = '${user_key}'
    ),
    ranked as (
      select
        ws.id as service_id,
        row_number() over (
          order by ur.final_score desc, ur.id desc
        ) as final_rank
      from user_recommendations ur
      join target_batch tb
        on tb.recommended_at = ur.recommended_at
      join welfare_services ws
        on ws.id = ur.service_id
      where ur.user_key = '${user_key}'
    )
    select coalesce(string_agg(service_id::text, ',' order by final_rank), '')
    from ranked
    where final_rank <= ${TOP_REFRESH_LIMIT};
  "
}

merge_csv_ids() {
  python3 - "$@" <<'PY'
import sys

seen = set()
merged = []
for raw in sys.argv[1:]:
    for part in raw.split(","):
        value = part.strip()
        if not value or value in seen:
            continue
        seen.add(value)
        merged.append(value)

print(",".join(merged))
PY
}

build_diagnostics_query_args() {
  python3 - "$1" <<'PY'
import sys
from urllib.parse import quote

for part in sys.argv[1].split(","):
    value = part.strip()
    if value:
        print(f"serviceId={quote(value)}")
PY
}

user_login() {
  local email="$1"
  local password="$2"
  local output_file="$3"
  local status

  status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${output_file}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"email\": \"${email}\",
        \"password\": \"${password}\"
      }"
  )"
  smoke_assert_status 200 "${status}" "user login (${email})" "${output_file}"
  extract_access_token "${output_file}"
}

fetch_diagnostics() {
  local user_key="$1"
  local query_args="$2"
  local output_file="$3"
  local token="$4"
  local status

  status="$(
    smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/recommendation-diagnostics?userKey=${user_key}&${query_args}" "${output_file}" \
      -H "Authorization: Bearer ${token}"
  )"
  smoke_assert_status 200 "${status}" "recommendation diagnostics (${user_key})" "${output_file}"
}

refresh_personal() {
  local token="$1"
  local output_file="$2"
  local label="$3"
  local status

  status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/recommendations/refresh?personal=true" "${output_file}" \
      -H "Authorization: Bearer ${token}"
  )"
  smoke_assert_status 200 "${status}" "recommendations refresh personal=true (${label})" "${output_file}"
}

EXAMPLE_ROW="$(query_exact_profile_representative "EXAMPLE_SMOKE" "true")"
if [[ -z "${EXAMPLE_ROW}" ]]; then
  EXAMPLE_ROW="$(query_exact_profile_representative "EXAMPLE_SMOKE" "false")"
fi
if [[ -z "${EXAMPLE_ROW}" ]]; then
  echo "failed to resolve exact-profile EXAMPLE_SMOKE representative" >&2
  exit 1
fi

REAL_USER_ROW="$(query_exact_profile_representative "REAL_USER" "false")"
if [[ -z "${REAL_USER_ROW}" ]]; then
  echo "failed to resolve exact-profile REAL_USER representative" >&2
  exit 1
fi

IFS=$'\t' read -r EXAMPLE_USER_KEY EXAMPLE_EMAIL EXAMPLE_TOP1_SERVICE_ID EXAMPLE_TOP1_TITLE EXAMPLE_TOP1_SCORE <<< "${EXAMPLE_ROW}"
IFS=$'\t' read -r REAL_USER_KEY REAL_USER_EMAIL REAL_USER_TOP1_SERVICE_ID REAL_USER_TOP1_TITLE REAL_USER_TOP1_SCORE <<< "${REAL_USER_ROW}"

EXAMPLE_TOP_SERVICE_IDS_CSV="$(query_top_service_ids_csv "${EXAMPLE_USER_KEY}")"
REAL_USER_TOP_SERVICE_IDS_CSV="$(query_top_service_ids_csv "${REAL_USER_KEY}")"
FOCUS_SERVICE_IDS_CSV="$(merge_csv_ids "${TARGET_SERVICE_ID}" "${EXAMPLE_TOP_SERVICE_IDS_CSV}" "${REAL_USER_TOP_SERVICE_IDS_CSV}")"
QUERY_ARGS="$(build_diagnostics_query_args "${FOCUS_SERVICE_IDS_CSV}" | paste -sd'&' -)"
if [[ -z "${QUERY_ARGS}" ]]; then
  echo "failed to build diagnostics query args" >&2
  exit 1
fi

smoke_resolve_admin_credentials "${ROOT_DIR}"
: "${ADMIN_EMAIL:?ADMIN_EMAIL is empty; export ADMIN_EMAIL or set SECURITY_ADMIN_EMAILS/.env or /tmp/youth-welfare-admin-smoke-email}"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD is empty; export ADMIN_PASSWORD or set /tmp/youth-welfare-admin-smoke-password}"

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

smoke_print_step "admin login (${ADMIN_EMAIL})"
ADMIN_LOGIN_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${ADMIN_LOGIN_RESPONSE}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${ADMIN_EMAIL}\",
      \"password\": \"${ADMIN_PASSWORD}\"
    }"
)"
smoke_assert_status 200 "${ADMIN_LOGIN_STATUS}" "admin login" "${ADMIN_LOGIN_RESPONSE}"
ADMIN_TOKEN="$(extract_access_token "${ADMIN_LOGIN_RESPONSE}")"

smoke_print_step "diagnostics before refresh"
fetch_diagnostics "${EXAMPLE_USER_KEY}" "${QUERY_ARGS}" "${EXAMPLE_BEFORE_RESPONSE}" "${ADMIN_TOKEN}"
fetch_diagnostics "${REAL_USER_KEY}" "${QUERY_ARGS}" "${REAL_USER_BEFORE_RESPONSE}" "${ADMIN_TOKEN}"

smoke_print_step "user login (${EXAMPLE_EMAIL})"
EXAMPLE_USER_TOKEN="$(user_login "${EXAMPLE_EMAIL}" "${EXAMPLE_USER_PASSWORD}" "${EXAMPLE_LOGIN_RESPONSE}")"

smoke_print_step "user login (${REAL_USER_EMAIL})"
REAL_USER_TOKEN="$(user_login "${REAL_USER_EMAIL}" "${REAL_USER_PASSWORD}" "${REAL_USER_LOGIN_RESPONSE}")"

smoke_print_step "fresh personal refresh"
refresh_personal "${EXAMPLE_USER_TOKEN}" "${EXAMPLE_REFRESH_RESPONSE}" "example"
refresh_personal "${REAL_USER_TOKEN}" "${REAL_USER_REFRESH_RESPONSE}" "real-user"

smoke_print_step "diagnostics after refresh"
fetch_diagnostics "${EXAMPLE_USER_KEY}" "${QUERY_ARGS}" "${EXAMPLE_AFTER_RESPONSE}" "${ADMIN_TOKEN}"
fetch_diagnostics "${REAL_USER_KEY}" "${QUERY_ARGS}" "${REAL_USER_AFTER_RESPONSE}" "${ADMIN_TOKEN}"

python3 - \
  "${EXAMPLE_BEFORE_RESPONSE}" \
  "${EXAMPLE_AFTER_RESPONSE}" \
  "${REAL_USER_BEFORE_RESPONSE}" \
  "${REAL_USER_AFTER_RESPONSE}" \
  "${EXAMPLE_REFRESH_RESPONSE}" \
  "${REAL_USER_REFRESH_RESPONSE}" \
  "${SUMMARY_OUTPUT}" \
  "${DETAIL_OUTPUT}" \
  "${TARGET_SERVICE_ID}" \
  "${EXAMPLE_USER_KEY}" \
  "${EXAMPLE_EMAIL}" \
  "${REAL_USER_KEY}" \
  "${REAL_USER_EMAIL}" \
  "${FOCUS_SERVICE_IDS_CSV}" <<'PY'
import csv
import json
import sys

(
    example_before_path,
    example_after_path,
    real_before_path,
    real_after_path,
    example_refresh_path,
    real_refresh_path,
    summary_path,
    detail_path,
    target_service_id_raw,
    example_user_key,
    example_email,
    real_user_key,
    real_user_email,
    focus_service_ids_csv,
) = sys.argv[1:]

target_service_id = int(target_service_id_raw)
focus_service_ids = [int(v) for v in focus_service_ids_csv.split(",") if v.strip()]

def load_diag(path):
    with open(path, "r", encoding="utf-8") as fp:
        payload = json.load(fp)["data"]
    services = {int(row["serviceId"]): row for row in payload.get("services") or [] if row.get("serviceId") is not None}
    return payload, services

def load_refresh(path):
    with open(path, "r", encoding="utf-8") as fp:
        return json.load(fp)["data"]

example_before_payload, example_before = load_diag(example_before_path)
example_after_payload, example_after = load_diag(example_after_path)
real_before_payload, real_before = load_diag(real_before_path)
real_after_payload, real_after = load_diag(real_after_path)
example_refresh = load_refresh(example_refresh_path)
real_refresh = load_refresh(real_refresh_path)

def service_row(services, service_id):
    row = dict(services.get(service_id) or {})
    row.setdefault("serviceId", service_id)
    return row

target_example_before = service_row(example_before, target_service_id)
target_example_after = service_row(example_after, target_service_id)
target_real_before = service_row(real_before, target_service_id)
target_real_after = service_row(real_after, target_service_id)
target_title = (
    target_example_before.get("title")
    or target_example_after.get("title")
    or target_real_before.get("title")
    or target_real_after.get("title")
    or ""
)

example_refresh_ids = [row.get("serviceId") for row in example_refresh if row.get("serviceId") is not None]
real_refresh_ids = [row.get("serviceId") for row in real_refresh if row.get("serviceId") is not None]

def boolish(value):
    if value is True:
        return "true"
    if value is False:
        return "false"
    if value is None:
        return ""
    return str(value)

def top_rank(refresh_rows, service_id):
    for idx, row in enumerate(refresh_rows, start=1):
        if row.get("serviceId") == service_id:
            return idx
    return ""

detail_fields = [
    "dropStage",
    "inBaseRetrieval",
    "inLatestRetrieval",
    "passedLatestFilters",
    "retainedLatestWindow",
    "inMergedCandidates",
    "inPostScoringCandidates",
    "inLatestSavedBatch",
    "latestSavedRank",
    "rerankCurrentRank",
    "latestSavedFinalScore",
    "rerankCurrentFinalScore",
    "latestSavedAiStatus",
    "latestSavedAiReason",
]

with open(detail_path, "w", encoding="utf-8", newline="") as fp:
    writer = csv.writer(fp, delimiter="\t")
    writer.writerow(["service_id", "field", "example_before", "example_after", "real_before", "real_after"])
    for service_id in focus_service_ids:
        ex_before = service_row(example_before, service_id)
        ex_after = service_row(example_after, service_id)
        ru_before = service_row(real_before, service_id)
        ru_after = service_row(real_after, service_id)
        for field in detail_fields:
            writer.writerow([
                service_id,
                field,
                ex_before.get(field, ""),
                ex_after.get(field, ""),
                ru_before.get(field, ""),
                ru_after.get(field, ""),
            ])

ex_before_saved = bool(target_example_before.get("inLatestSavedBatch"))
ex_after_saved = bool(target_example_after.get("inLatestSavedBatch"))
ru_before_saved = bool(target_real_before.get("inLatestSavedBatch"))
ru_after_saved = bool(target_real_after.get("inLatestSavedBatch"))
ex_before_drop = str(target_example_before.get("dropStage") or "")
ex_after_drop = str(target_example_after.get("dropStage") or "")
ru_after_drop = str(target_real_after.get("dropStage") or "")

if ex_before_saved and not ex_after_saved and ex_after_drop == "NOT_IN_SQL_RETRIEVAL":
    classification = "STALE_EXAMPLE_SAVED_BATCH_PATH"
    next_step = "TRACE_WHY_EXAMPLE_SAVED_BATCH_PERSISTED_BEFORE_REFRESH"
elif ex_after_saved and not ru_after_saved:
    classification = "CURRENT_FLOW_ORIGIN_DIFFERENTIAL"
    next_step = "TRACE_ORIGIN_OR_ACCOUNT_SCOPE_IN_CURRENT_FLOW"
elif not ex_after_saved and not ru_after_saved:
    classification = "FRESH_FLOW_SHARED_ABSENCE"
    next_step = "FOCUS_ON_HISTORICAL_SAVED_BATCH_OR_STALE_PERSISTENCE"
else:
    classification = "UNCLASSIFIED_FRESH_SAVED_STATE"
    next_step = "INSPECT_BEFORE_AFTER_DETAIL_ROWS"

summary = {
    "target_service_id": str(target_service_id),
    "target_service_title": target_title,
    "representative_example_user_key": example_user_key,
    "representative_example_email": example_email,
    "representative_real_user_key": real_user_key,
    "representative_real_user_email": real_user_email,
    "focus_service_ids_csv": focus_service_ids_csv,
    "example_before_saved": boolish(target_example_before.get("inLatestSavedBatch")),
    "example_before_drop_stage": ex_before_drop,
    "example_before_saved_rank": str(target_example_before.get("latestSavedRank") or ""),
    "example_before_refresh_rank": str(top_rank(example_refresh, target_service_id)),
    "example_after_saved": boolish(target_example_after.get("inLatestSavedBatch")),
    "example_after_drop_stage": ex_after_drop,
    "example_after_saved_rank": str(target_example_after.get("latestSavedRank") or ""),
    "example_after_refresh_rank": str(top_rank(example_refresh, target_service_id)),
    "real_user_before_saved": boolish(target_real_before.get("inLatestSavedBatch")),
    "real_user_before_drop_stage": str(target_real_before.get("dropStage") or ""),
    "real_user_before_saved_rank": str(target_real_before.get("latestSavedRank") or ""),
    "real_user_before_refresh_rank": str(top_rank(real_refresh, target_service_id)),
    "real_user_after_saved": boolish(target_real_after.get("inLatestSavedBatch")),
    "real_user_after_drop_stage": ru_after_drop,
    "real_user_after_saved_rank": str(target_real_after.get("latestSavedRank") or ""),
    "real_user_after_refresh_rank": str(top_rank(real_refresh, target_service_id)),
    "classification": classification,
    "operator_next_step": next_step,
}

with open(summary_path, "w", encoding="utf-8") as fp:
    for key, value in summary.items():
        fp.write(f"{key}={value}\n")
PY

{
  echo "generated_at_utc=$(smoke_now_iso_utc)"
  echo "generated_at_kst=$(smoke_now_iso_kst)"
  cat "${SUMMARY_OUTPUT}"
  echo "artifact_dir=${ARTIFACT_DIR}"
} > "${SUMMARY_OUTPUT}.tmp"
mv "${SUMMARY_OUTPUT}.tmp" "${SUMMARY_OUTPUT}"

smoke_update_links \
  "${ARTIFACT_DIR}" "${ARTIFACT_ROOT}/latest" \
  "${SUMMARY_OUTPUT}" "${ARTIFACT_ROOT}/latest-same-profile-fresh-saved-differential-summary.txt" \
  "${DETAIL_OUTPUT}" "${ARTIFACT_ROOT}/latest-target-before-after.tsv"

echo
cat "${SUMMARY_OUTPUT}"
echo "summary_output=${SUMMARY_OUTPUT}"
