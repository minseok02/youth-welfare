#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
TARGET_USER_KEY="${TARGET_USER_KEY:-}"
TARGET_SERVICE_IDS_CSV="${TARGET_SERVICE_IDS_CSV:-}"
TOP_SAVED_LIMIT="${TOP_SAVED_LIMIT:-10}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/admin-login.json"
DIAGNOSTICS_RESPONSE="${ARTIFACT_DIR}/recommendation-diagnostics.json"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

normalize_bool() {
  local value="${1,,}"
  case "${value}" in
    true|false) printf '%s' "${value}" ;;
    *)
      echo "unsupported boolean value: ${1}" >&2
      exit 1
      ;;
  esac
}

KEEP_ARTIFACTS="$(normalize_bool "${KEEP_ARTIFACTS}")"

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

resolve_target_user_key() {
  if [[ -n "${TARGET_USER_KEY}" ]]; then
    printf '%s' "${TARGET_USER_KEY}"
    return 0
  fi

  smoke_db_query "
    with latest_per_user as (
      select user_key, max(recommended_at) as recommended_at
      from user_recommendations
      group by user_key
    )
    select user_key
    from latest_per_user
    order by recommended_at desc, user_key desc
    limit 1;
  "
}

resolve_top_saved_ids_csv() {
  local user_key="$1"

  smoke_db_query "
    with target_batch as (
      select max(recommended_at) as recommended_at
      from user_recommendations
      where user_key = '${user_key}'
    ),
    ranked as (
      select
        ur.service_id,
        row_number() over (
          order by ur.final_score desc, ur.id asc
        ) as final_rank
      from user_recommendations ur
      join target_batch tb
        on tb.recommended_at = ur.recommended_at
      where ur.user_key = '${user_key}'
    )
    select coalesce(string_agg(service_id::text, ',' order by final_rank), '')
    from ranked
    where final_rank <= ${TOP_SAVED_LIMIT};
  "
}

merge_csv_ids() {
  python3 - "$1" "$2" <<'PY'
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

FOCUS_USER_KEY="$(resolve_target_user_key)"
if [[ -z "${FOCUS_USER_KEY}" ]]; then
  echo "missing target user_key and no recommendation batch exists" >&2
  exit 1
fi
if [[ -z "${TARGET_SERVICE_IDS_CSV}" ]]; then
  TARGET_SERVICE_IDS_CSV="$(smoke_resolve_recommendation_local_target_family_ids_csv "${FOCUS_USER_KEY}")"
fi

TOP_SAVED_IDS_CSV="$(resolve_top_saved_ids_csv "${FOCUS_USER_KEY}")"
FOCUS_SERVICE_IDS_CSV="$(merge_csv_ids "${TARGET_SERVICE_IDS_CSV}" "${TOP_SAVED_IDS_CSV}")"

if [[ -z "${FOCUS_SERVICE_IDS_CSV}" ]]; then
  echo "no focus service ids resolved" >&2
  exit 1
fi

smoke_require_command curl
smoke_require_command python3

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

query_args="$(build_diagnostics_query_args "${FOCUS_SERVICE_IDS_CSV}" | paste -sd'&' -)"
if [[ -z "${query_args}" ]]; then
  echo "failed to build diagnostics query args" >&2
  exit 1
fi

smoke_print_step "recommendation diagnostics"
DIAGNOSTICS_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/recommendation-diagnostics?userKey=${FOCUS_USER_KEY}&${query_args}" "${DIAGNOSTICS_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${DIAGNOSTICS_STATUS}" "recommendation diagnostics" "${DIAGNOSTICS_RESPONSE}"

python3 - "${DIAGNOSTICS_RESPONSE}" "${TARGET_SERVICE_IDS_CSV}" "${TOP_SAVED_IDS_CSV}" <<'PY'
import json
import sys

response_path = sys.argv[1]
target_ids = {int(v) for v in sys.argv[2].split(",") if v.strip()}
top_saved_ids = {int(v) for v in sys.argv[3].split(",") if v.strip()}

with open(response_path, "r", encoding="utf-8") as fp:
    payload = json.load(fp)["data"]

services = payload.get("services") or []

print(f"METRIC target_user_key={payload.get('userKey')}")
print(f"METRIC target_service_ids_csv={sys.argv[2]}")
print(f"METRIC top_saved_ids_csv={sys.argv[3]}")
print(f"METRIC base_candidate_count={payload.get('baseCandidateCount')}")
print(f"METRIC latest_candidate_count={payload.get('latestCandidateCount')}")
print(f"METRIC filtered_base_candidate_count={payload.get('filteredBaseCandidateCount')}")
print(f"METRIC filtered_latest_candidate_count={payload.get('filteredLatestCandidateCount')}")
print(f"METRIC merged_candidate_count={payload.get('mergedCandidateCount')}")
print(f"METRIC latest_saved_candidate_count={payload.get('latestSavedCandidateCount')}")
print(f"METRIC rerank_trace_mode={payload.get('rerankTraceMode')}")
print(f"METRIC no_priority_profile={payload.get('noPriorityProfile')}")

def role(service_id: int) -> str:
    flags = []
    if service_id in target_ids:
        flags.append("target")
    if service_id in top_saved_ids:
        flags.append("saved-top")
    return ",".join(flags) if flags else "-"

targets = [row for row in services if int(row.get("serviceId")) in target_ids]
saved_top = [row for row in services if int(row.get("serviceId")) in top_saved_ids]

print()
print("[TARGET SAVED GAP]")
for row in sorted(targets, key=lambda item: ((item.get("rerankCurrentRank") or 9999), (item.get("serviceId") or 99999999))):
    print(
        f"{row.get('serviceId')}\t{role(int(row.get('serviceId')))}\t"
        f"base_rank={row.get('baseRetrievalRank')}\t"
        f"retain_base_rank={row.get('retainedBaseRank')}\t"
        f"merged_rank={row.get('mergedCandidateRank')}\t"
        f"rerank_rank={row.get('rerankCurrentRank')}\t"
        f"saved_rank={row.get('latestSavedRank')}\t"
        f"retain_base={row.get('retainedBaseWindow')}\t"
        f"merged={row.get('inMergedCandidates')}\t"
        f"post={row.get('inPostScoringCandidates')}\t"
        f"saved={row.get('inLatestSavedBatch')}\t"
        f"dropStage={row.get('dropStage')}\t"
        f"ruleWeighted={row.get('ruleWeightedScore')}\t"
        f"currentFinal={row.get('rerankCurrentFinalScore')}\t"
        f"savedFinal={row.get('latestSavedFinalScore')}\t"
        f"currentAi={row.get('aiScore')}\t"
        f"savedAi={row.get('latestSavedAiScore')}\t"
        f"savedAiStatus={row.get('latestSavedAiStatus')}\t"
        f"diversityBucket={row.get('rerankDiversityBucket')}\t"
        f"diversityPenalty={row.get('rerankDiversityPenalty')}\t"
        f"noPriorityAdj={row.get('rerankNoPriorityAdjustment')}\t"
        f"sourceType={row.get('sourceType')}\t"
        f"category={row.get('category')}\t"
        f"title={row.get('title')}"
    )

print()
print("[TOP SAVED BATCH]")
for row in sorted(saved_top, key=lambda item: ((item.get("latestSavedRank") or 9999), (item.get("serviceId") or 99999999))):
    print(
        f"{row.get('latestSavedRank')}\t{row.get('serviceId')}\t{role(int(row.get('serviceId')))}\t"
        f"rerank_rank={row.get('rerankCurrentRank')}\t"
        f"base_rank={row.get('baseRetrievalRank')}\t"
        f"retain_base_rank={row.get('retainedBaseRank')}\t"
        f"ruleWeighted={row.get('ruleWeightedScore')}\t"
        f"currentFinal={row.get('rerankCurrentFinalScore')}\t"
        f"savedFinal={row.get('latestSavedFinalScore')}\t"
        f"savedAi={row.get('latestSavedAiScore')}\t"
        f"savedAiStatus={row.get('latestSavedAiStatus')}\t"
        f"diversityBucket={row.get('rerankDiversityBucket')}\t"
        f"diversityPenalty={row.get('rerankDiversityPenalty')}\t"
        f"sourceType={row.get('sourceType')}\t"
        f"category={row.get('category')}\t"
        f"title={row.get('title')}"
    )

print()
print(f"METRIC artifact_dir={response_path.rsplit('/', 1)[0]}")
PY
