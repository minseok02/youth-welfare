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
TOP_COMPETITOR_LIMIT="${TOP_COMPETITOR_LIMIT:-3}"

TIMESTAMP_UTC="$(smoke_now_ts_utc)"
ARTIFACT_ROOT="${ROOT_DIR}/tmp/recommendation-same-profile-path-differential-audit"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${TIMESTAMP_UTC}}"
mkdir -p "${ARTIFACT_DIR}"

HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/admin-login.json"
EXAMPLE_DIAGNOSTICS_RESPONSE="${ARTIFACT_DIR}/example-diagnostics.json"
REAL_USER_DIAGNOSTICS_RESPONSE="${ARTIFACT_DIR}/real-user-diagnostics.json"
EXAMPLE_TOP_ROWS="${ARTIFACT_DIR}/example-top-rows.tsv"
REAL_USER_TOP_ROWS="${ARTIFACT_DIR}/real-user-top-rows.tsv"
DETAIL_OUTPUT="${ARTIFACT_DIR}/focus-services-comparison.tsv"
SUMMARY_OUTPUT="${ARTIFACT_DIR}/same-profile-path-differential-summary.txt"

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

query_top_service_rows() {
  local user_key="$1"
  local output_file="$2"
  smoke_db_query "
    with target_batch as (
      select max(recommended_at) as recommended_at
      from user_recommendations
      where user_key = '${user_key}'
    ),
    ranked as (
      select
        ur.user_key,
        ws.id as service_id,
        ws.title,
        ur.final_score,
        row_number() over (
          partition by ur.user_key
          order by ur.final_score desc, ur.id desc
        ) as final_rank
      from user_recommendations ur
      join target_batch tb
        on tb.recommended_at = ur.recommended_at
      join welfare_services ws
        on ws.id = ur.service_id
      where ur.user_key = '${user_key}'
    )
    select service_id, title, final_score, final_rank
    from ranked
    where final_rank <= ${TOP_COMPETITOR_LIMIT}
    order by final_rank;
  " > "${output_file}"
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
    where final_rank <= ${TOP_COMPETITOR_LIMIT};
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

query_top_service_rows "${EXAMPLE_USER_KEY}" "${EXAMPLE_TOP_ROWS}"
query_top_service_rows "${REAL_USER_KEY}" "${REAL_USER_TOP_ROWS}"

EXAMPLE_TOP_SERVICE_IDS_CSV="$(query_top_service_ids_csv "${EXAMPLE_USER_KEY}")"
REAL_USER_TOP_SERVICE_IDS_CSV="$(query_top_service_ids_csv "${REAL_USER_KEY}")"
FOCUS_SERVICE_IDS_CSV="$(merge_csv_ids "${TARGET_SERVICE_ID}" "${EXAMPLE_TOP_SERVICE_IDS_CSV}" "${REAL_USER_TOP_SERVICE_IDS_CSV}")"
if [[ -z "${FOCUS_SERVICE_IDS_CSV}" ]]; then
  echo "failed to resolve focus service ids" >&2
  exit 1
fi

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
LOGIN_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${LOGIN_RESPONSE}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${ADMIN_EMAIL}\",
      \"password\": \"${ADMIN_PASSWORD}\"
    }"
)"
smoke_assert_status 200 "${LOGIN_STATUS}" "admin login" "${LOGIN_RESPONSE}"
ADMIN_TOKEN="$(extract_access_token "${LOGIN_RESPONSE}")"

smoke_print_step "diagnostics compare"
fetch_diagnostics "${EXAMPLE_USER_KEY}" "${QUERY_ARGS}" "${EXAMPLE_DIAGNOSTICS_RESPONSE}" "${ADMIN_TOKEN}"
fetch_diagnostics "${REAL_USER_KEY}" "${QUERY_ARGS}" "${REAL_USER_DIAGNOSTICS_RESPONSE}" "${ADMIN_TOKEN}"

python3 - \
  "${EXAMPLE_DIAGNOSTICS_RESPONSE}" \
  "${REAL_USER_DIAGNOSTICS_RESPONSE}" \
  "${DETAIL_OUTPUT}" \
  "${SUMMARY_OUTPUT}" \
  "${TARGET_SERVICE_ID}" \
  "${PROFILE_SIDO}" \
  "${PROFILE_SGG}" \
  "${PROFILE_INCOME_LEVEL}" \
  "${PROFILE_EMPLOYMENT_STATUS}" \
  "${PROFILE_HOUSEHOLD_TYPE}" \
  "${EXAMPLE_USER_KEY}" \
  "${EXAMPLE_EMAIL}" \
  "${EXAMPLE_TOP1_SERVICE_ID}" \
  "${EXAMPLE_TOP1_TITLE}" \
  "${EXAMPLE_TOP1_SCORE}" \
  "${REAL_USER_KEY}" \
  "${REAL_USER_EMAIL}" \
  "${REAL_USER_TOP1_SERVICE_ID}" \
  "${REAL_USER_TOP1_TITLE}" \
  "${REAL_USER_TOP1_SCORE}" \
  "${FOCUS_SERVICE_IDS_CSV}" <<'PY'
import csv
import json
import sys

(
    example_path,
    real_path,
    detail_path,
    summary_path,
    target_service_id_raw,
    profile_sido,
    profile_sgg,
    profile_income_level,
    profile_employment_status,
    profile_household_type,
    example_user_key,
    example_email,
    example_top1_service_id,
    example_top1_title,
    example_top1_score,
    real_user_key,
    real_user_email,
    real_user_top1_service_id,
    real_user_top1_title,
    real_user_top1_score,
    focus_service_ids_csv,
) = sys.argv[1:]

target_service_id = int(target_service_id_raw)
focus_service_ids = [int(v) for v in focus_service_ids_csv.split(",") if v.strip()]

with open(example_path, "r", encoding="utf-8") as fp:
    example_payload = json.load(fp)["data"]
with open(real_path, "r", encoding="utf-8") as fp:
    real_payload = json.load(fp)["data"]

def index_services(payload):
    items = payload.get("services") or []
    return {int(item["serviceId"]): item for item in items if item.get("serviceId") is not None}

example_services = index_services(example_payload)
real_services = index_services(real_payload)

def service_row(services, service_id):
    row = dict(services.get(service_id) or {})
    row.setdefault("serviceId", service_id)
    return row

def boolish(value):
    if value is True:
        return "true"
    if value is False:
        return "false"
    if value is None:
        return ""
    return str(value)

target_example = service_row(example_services, target_service_id)
target_real = service_row(real_services, target_service_id)
target_title = target_example.get("title") or target_real.get("title") or ""

detail_fields = [
    "title",
    "category",
    "inBaseRetrieval",
    "inLatestRetrieval",
    "passedBaseFilters",
    "passedLatestFilters",
    "baseRetrievalRank",
    "latestRetrievalRank",
    "retainedBaseWindow",
    "retainedLatestWindow",
    "retainedBaseRank",
    "retainedLatestRank",
    "primaryAudienceRelevant",
    "ageConstraintMatched",
    "youthRelevant",
    "inMergedCandidates",
    "mergedCandidateRank",
    "inPostScoringCandidates",
    "inLatestSavedBatch",
    "dropStage",
    "latestSavedRank",
    "rerankCurrentRank",
    "ruleWeightedScore",
    "rerankCurrentFinalScore",
    "latestSavedFinalScore",
    "latestSavedAiScore",
    "latestSavedAiStatus",
    "latestSavedAiReason",
]

with open(detail_path, "w", encoding="utf-8", newline="") as fp:
    writer = csv.writer(fp, delimiter="\t")
    writer.writerow(["service_id", "field", "example_value", "real_user_value"])
    for service_id in focus_service_ids:
        ex = service_row(example_services, service_id)
        ru = service_row(real_services, service_id)
        for field in detail_fields:
            writer.writerow([
                service_id,
                field,
                ex.get(field, ""),
                ru.get(field, ""),
            ])

def classify_target_differential(ex, ru):
    ex_base = bool(ex.get("inBaseRetrieval"))
    ru_base = bool(ru.get("inBaseRetrieval"))
    ex_latest = bool(ex.get("inLatestRetrieval"))
    ru_latest = bool(ru.get("inLatestRetrieval"))
    ex_pass_latest = bool(ex.get("passedLatestFilters"))
    ru_pass_latest = bool(ru.get("passedLatestFilters"))
    ex_retained = bool(ex.get("retainedLatestWindow"))
    ru_retained = bool(ru.get("retainedLatestWindow"))
    ex_merged = bool(ex.get("inMergedCandidates"))
    ru_merged = bool(ru.get("inMergedCandidates"))
    ex_post = bool(ex.get("inPostScoringCandidates"))
    ru_post = bool(ru.get("inPostScoringCandidates"))
    ex_saved = bool(ex.get("inLatestSavedBatch"))
    ru_saved = bool(ru.get("inLatestSavedBatch"))
    ex_drop_stage = str(ex.get("dropStage") or "")
    ru_drop_stage = str(ru.get("dropStage") or "")

    if ex_saved and not ru_saved and ex_drop_stage == "PRESENT_IN_SAVED_BATCH" and ru_drop_stage == "NOT_IN_SQL_RETRIEVAL":
      return "SAVED_ONLY_EXAMPLE_VS_REAL_SQL_GAP", "TRACE_SAVED_BATCH_ORIGIN_PATH_AND_SQL_RETRIEVAL_DIFF"
    if ex_base and not ru_base:
      return "BASE_RETRIEVAL_DIFFERENTIAL", "TRACE_RETRIEVAL_OR_ORIGIN_GATING"
    if ex_latest and not ru_latest:
      return "LATEST_RETRIEVAL_DIFFERENTIAL", "TRACE_LATEST_RETRIEVAL_BRANCH"
    if ex_pass_latest and not ru_pass_latest:
      return "LATEST_FILTER_DIFFERENTIAL", "TRACE_FILTER_OR_ELIGIBILITY_GATING"
    if ex_retained and not ru_retained:
      return "WINDOW_RETENTION_DIFFERENTIAL", "TRACE_WINDOW_RETAIN_RULES"
    if ex_merged and not ru_merged:
      return "MERGE_DIFFERENTIAL", "TRACE_MERGE_CANDIDATE_CONTRACT"
    if ex_post and not ru_post:
      return "POST_SCORING_DIFFERENTIAL", "TRACE_POST_SCORING_DROP_PATH"
    if ex_saved and not ru_saved:
      return "SAVED_BATCH_DIFFERENTIAL", "TRACE_RERANK_OR_SAVE_PATH"
    if ex_saved and ru_saved:
      return "SAVED_BATCH_SHARED_PATH", "COMPARE_RANK_AND_SCORE_DELTA"
    if not ex_base and not ru_base:
      return "TARGET_ABSENT_FOR_BOTH", "RECHECK_TARGET_SERVICE_SELECTION"
    return "UNCLASSIFIED_TARGET_DIFFERENTIAL", "INSPECT_FOCUS_SERVICE_DETAIL_ROWS"

differential_stage, next_step = classify_target_differential(target_example, target_real)
blocker_class = f"SAME_PROFILE_PATH_{differential_stage}"

summary = {
    "profile_sido": profile_sido,
    "profile_sgg": profile_sgg,
    "profile_income_level": profile_income_level,
    "profile_employment_status": profile_employment_status,
    "profile_household_type": profile_household_type,
    "target_service_id": str(target_service_id),
    "target_service_title": target_title,
    "representative_example_user_key": example_user_key,
    "representative_example_email": example_email,
    "representative_example_top1_service_id": example_top1_service_id,
    "representative_example_top1_title": example_top1_title,
    "representative_example_top1_score": example_top1_score,
    "representative_real_user_key": real_user_key,
    "representative_real_user_email": real_user_email,
    "representative_real_user_top1_service_id": real_user_top1_service_id,
    "representative_real_user_top1_title": real_user_top1_title,
    "representative_real_user_top1_score": real_user_top1_score,
    "focus_service_ids_csv": focus_service_ids_csv,
    "example_target_in_base_retrieval": boolish(target_example.get("inBaseRetrieval")),
    "real_user_target_in_base_retrieval": boolish(target_real.get("inBaseRetrieval")),
    "example_target_in_latest_retrieval": boolish(target_example.get("inLatestRetrieval")),
    "real_user_target_in_latest_retrieval": boolish(target_real.get("inLatestRetrieval")),
    "example_target_passed_latest_filters": boolish(target_example.get("passedLatestFilters")),
    "real_user_target_passed_latest_filters": boolish(target_real.get("passedLatestFilters")),
    "example_target_retained_latest_window": boolish(target_example.get("retainedLatestWindow")),
    "real_user_target_retained_latest_window": boolish(target_real.get("retainedLatestWindow")),
    "example_target_primary_audience_relevant": boolish(target_example.get("primaryAudienceRelevant")),
    "real_user_target_primary_audience_relevant": boolish(target_real.get("primaryAudienceRelevant")),
    "example_target_in_merged_candidates": boolish(target_example.get("inMergedCandidates")),
    "real_user_target_in_merged_candidates": boolish(target_real.get("inMergedCandidates")),
    "example_target_in_post_scoring_candidates": boolish(target_example.get("inPostScoringCandidates")),
    "real_user_target_in_post_scoring_candidates": boolish(target_real.get("inPostScoringCandidates")),
    "example_target_in_latest_saved_batch": boolish(target_example.get("inLatestSavedBatch")),
    "real_user_target_in_latest_saved_batch": boolish(target_real.get("inLatestSavedBatch")),
    "example_target_drop_stage": str(target_example.get("dropStage") or ""),
    "real_user_target_drop_stage": str(target_real.get("dropStage") or ""),
    "example_target_latest_saved_rank": str(target_example.get("latestSavedRank") or ""),
    "real_user_target_latest_saved_rank": str(target_real.get("latestSavedRank") or ""),
    "example_target_latest_saved_final_score": str(target_example.get("latestSavedFinalScore") or ""),
    "real_user_target_latest_saved_final_score": str(target_real.get("latestSavedFinalScore") or ""),
    "example_target_latest_saved_ai_status": str(target_example.get("latestSavedAiStatus") or ""),
    "real_user_target_latest_saved_ai_status": str(target_real.get("latestSavedAiStatus") or ""),
    "example_target_latest_saved_ai_reason": str(target_example.get("latestSavedAiReason") or ""),
    "real_user_target_latest_saved_ai_reason": str(target_real.get("latestSavedAiReason") or ""),
    "differential_stage": differential_stage,
    "blocker_class": blocker_class,
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
  "${SUMMARY_OUTPUT}" "${ARTIFACT_ROOT}/latest-same-profile-path-differential-summary.txt" \
  "${DETAIL_OUTPUT}" "${ARTIFACT_ROOT}/latest-focus-services-comparison.tsv" \
  "${EXAMPLE_DIAGNOSTICS_RESPONSE}" "${ARTIFACT_ROOT}/latest-example-diagnostics.json" \
  "${REAL_USER_DIAGNOSTICS_RESPONSE}" "${ARTIFACT_ROOT}/latest-real-user-diagnostics.json" \
  "${EXAMPLE_TOP_ROWS}" "${ARTIFACT_ROOT}/latest-example-top-rows.tsv" \
  "${REAL_USER_TOP_ROWS}" "${ARTIFACT_ROOT}/latest-real-user-top-rows.tsv"

echo
cat "${SUMMARY_OUTPUT}"
echo "summary_output=${SUMMARY_OUTPUT}"
