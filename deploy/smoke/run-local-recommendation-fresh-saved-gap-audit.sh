#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
TARGET_USER_KEY="${TARGET_USER_KEY:-}"
TARGET_SERVICE_IDS_CSV="${TARGET_SERVICE_IDS_CSV:-}"
TOP_REFRESH_LIMIT="${TOP_REFRESH_LIMIT:-10}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

USER_EMAIL="${USER_EMAIL:-}"
USER_PASSWORD="${USER_PASSWORD:-}"
USER_ACCESS_TOKEN="${USER_ACCESS_TOKEN:-}"
ADMIN_EMAIL="${ADMIN_EMAIL:-}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
USER_LOGIN_RESPONSE="${ARTIFACT_DIR}/user-login.json"
ADMIN_LOGIN_RESPONSE="${ARTIFACT_DIR}/admin-login.json"
REFRESH_RESPONSE="${ARTIFACT_DIR}/refresh-personal.json"
DIAGNOSTICS_RESPONSE="${ARTIFACT_DIR}/recommendation-diagnostics.json"

cleanup() {
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

resolve_target_user_key_from_latest() {
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

if [[ -z "${TARGET_USER_KEY}" ]]; then
  TARGET_USER_KEY="$(resolve_target_user_key_from_latest)"
fi
if [[ -z "${TARGET_SERVICE_IDS_CSV}" && -n "${TARGET_USER_KEY}" ]]; then
  TARGET_SERVICE_IDS_CSV="$(smoke_resolve_recommendation_local_target_family_ids_csv "${TARGET_USER_KEY}")"
fi

if [[ -z "${TARGET_USER_KEY}" ]]; then
  echo "missing TARGET_USER_KEY and no recommendation batch exists" >&2
  exit 1
fi

smoke_require_command curl
smoke_require_command python3

smoke_resolve_admin_credentials "${ROOT_DIR}"
: "${ADMIN_EMAIL:?ADMIN_EMAIL is empty; export ADMIN_EMAIL or set SECURITY_ADMIN_EMAILS/.env or /tmp/youth-welfare-admin-smoke-email}"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD is empty; export ADMIN_PASSWORD or set /tmp/youth-welfare-admin-smoke-password}"

if [[ -z "${USER_ACCESS_TOKEN}" ]]; then
  : "${USER_EMAIL:?USER_EMAIL is empty; provide USER_EMAIL or USER_ACCESS_TOKEN}"
  : "${USER_PASSWORD:?USER_PASSWORD is empty; provide USER_PASSWORD or USER_ACCESS_TOKEN}"
fi

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

if [[ -z "${USER_ACCESS_TOKEN}" ]]; then
  smoke_print_step "user login (${USER_EMAIL})"
  USER_LOGIN_STATUS="$(
    smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${USER_LOGIN_RESPONSE}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"email\": \"${USER_EMAIL}\",
        \"password\": \"${USER_PASSWORD}\"
      }"
  )"
  smoke_assert_status 200 "${USER_LOGIN_STATUS}" "user login" "${USER_LOGIN_RESPONSE}"
  USER_ACCESS_TOKEN="$(extract_access_token "${USER_LOGIN_RESPONSE}")"
fi

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
ADMIN_ACCESS_TOKEN="$(extract_access_token "${ADMIN_LOGIN_RESPONSE}")"

smoke_print_step "recommendations refresh personal=true"
REFRESH_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/recommendations/refresh?personal=true" "${REFRESH_RESPONSE}" \
    -H "Authorization: Bearer ${USER_ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${REFRESH_STATUS}" "recommendations refresh personal=true" "${REFRESH_RESPONSE}"

REFRESH_SERVICE_IDS_CSV="$(
  python3 - "${REFRESH_RESPONSE}" "${TOP_REFRESH_LIMIT}" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)["data"]

limit = int(sys.argv[2])
ids = []
for row in payload[:limit]:
    service_id = row.get("serviceId")
    if service_id is not None:
        ids.append(str(service_id))
print(",".join(ids))
PY
)"

FOCUS_SERVICE_IDS_CSV="$(merge_csv_ids "${TARGET_SERVICE_IDS_CSV}" "${REFRESH_SERVICE_IDS_CSV}")"
if [[ -z "${FOCUS_SERVICE_IDS_CSV}" ]]; then
  echo "no focus service ids resolved" >&2
  exit 1
fi

query_args="$(build_diagnostics_query_args "${FOCUS_SERVICE_IDS_CSV}" | paste -sd'&' -)"
if [[ -z "${query_args}" ]]; then
  echo "failed to build diagnostics query args" >&2
  exit 1
fi

smoke_print_step "recommendation diagnostics after fresh refresh"
DIAGNOSTICS_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/recommendation-diagnostics?userKey=${TARGET_USER_KEY}&${query_args}" "${DIAGNOSTICS_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${DIAGNOSTICS_STATUS}" "recommendation diagnostics" "${DIAGNOSTICS_RESPONSE}"

python3 - "${REFRESH_RESPONSE}" "${DIAGNOSTICS_RESPONSE}" "${TARGET_SERVICE_IDS_CSV}" "${REFRESH_SERVICE_IDS_CSV}" <<'PY'
import json
import sys

refresh_path, diag_path = sys.argv[1], sys.argv[2]
target_ids = {int(v) for v in sys.argv[3].split(",") if v.strip()}
refresh_ids = {int(v) for v in sys.argv[4].split(",") if v.strip()}

with open(refresh_path, "r", encoding="utf-8") as fp:
    refresh_rows = json.load(fp)["data"]

with open(diag_path, "r", encoding="utf-8") as fp:
    diag = json.load(fp)["data"]

services = {int(row["serviceId"]): row for row in diag.get("services") or [] if row.get("serviceId") is not None}

print(f"METRIC target_user_key={diag.get('userKey')}")
print(f"METRIC target_service_ids_csv={sys.argv[3]}")
print(f"METRIC fresh_refresh_ids_csv={sys.argv[4]}")
print(f"METRIC rerank_trace_mode={diag.get('rerankTraceMode')}")
print(f"METRIC refresh_count={len(refresh_rows)}")
print(f"METRIC latest_saved_candidate_count={diag.get('latestSavedCandidateCount')}")

print()
print("[FRESH REFRESH TOP]")
for idx, row in enumerate(refresh_rows[:len(refresh_ids)], start=1):
    service_id = row.get("serviceId")
    if service_id is None:
        continue
    diag_row = services.get(int(service_id), {})
    print(
        f"{idx}\t{service_id}\t"
        f"sourceType={row.get('sourceType')}\t"
        f"category={row.get('category')}\t"
        f"savedRank={diag_row.get('latestSavedRank')}\t"
        f"rerankRank={diag_row.get('rerankCurrentRank')}\t"
        f"savedFinal={diag_row.get('latestSavedFinalScore')}\t"
        f"currentFinal={diag_row.get('rerankCurrentFinalScore')}\t"
        f"savedAi={diag_row.get('latestSavedAiScore')}\t"
        f"title={row.get('title')}"
    )

print()
print("[TARGET FRESH GAP]")
for service_id in sorted(target_ids):
    row = services.get(service_id)
    if row is None:
        print(f"{service_id}\tmissing=true")
        continue
    print(
        f"{service_id}\t"
        f"inFreshTop={service_id in refresh_ids}\t"
        f"rerankRank={row.get('rerankCurrentRank')}\t"
        f"savedRank={row.get('latestSavedRank')}\t"
        f"retainBaseRank={row.get('retainedBaseRank')}\t"
        f"mergedRank={row.get('mergedCandidateRank')}\t"
        f"dropStage={row.get('dropStage')}\t"
        f"currentFinal={row.get('rerankCurrentFinalScore')}\t"
        f"savedFinal={row.get('latestSavedFinalScore')}\t"
        f"savedAi={row.get('latestSavedAiScore')}\t"
        f"savedAiStatus={row.get('latestSavedAiStatus')}\t"
        f"diversityPenalty={row.get('rerankDiversityPenalty')}\t"
        f"noPriorityAdj={row.get('rerankNoPriorityAdjustment')}\t"
        f"title={row.get('title')}"
    )

print()
print(f"METRIC artifact_dir={diag_path.rsplit('/', 1)[0]}")
PY
