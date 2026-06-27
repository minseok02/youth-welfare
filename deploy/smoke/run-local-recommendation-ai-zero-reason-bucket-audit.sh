#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
TARGET_USER_KEY="${TARGET_USER_KEY:-}"
TOP_REFRESH_LIMIT="${TOP_REFRESH_LIMIT:-20}"
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

if [[ -z "${TARGET_USER_KEY}" ]]; then
  TARGET_USER_KEY="$(resolve_target_user_key_from_latest)"
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

if [[ -z "${REFRESH_SERVICE_IDS_CSV}" ]]; then
  echo "no fresh refresh ids resolved" >&2
  exit 1
fi

query_args="$(build_diagnostics_query_args "${REFRESH_SERVICE_IDS_CSV}" | paste -sd'&' -)"
smoke_print_step "recommendation diagnostics after fresh refresh"
DIAGNOSTICS_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/recommendation-diagnostics?userKey=${TARGET_USER_KEY}&${query_args}" "${DIAGNOSTICS_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}"
  )"
smoke_assert_status 200 "${DIAGNOSTICS_STATUS}" "recommendation diagnostics" "${DIAGNOSTICS_RESPONSE}"

python3 - "${REFRESH_RESPONSE}" "${DIAGNOSTICS_RESPONSE}" <<'PY'
import json
import sys
from collections import Counter

refresh_path, diag_path = sys.argv[1:3]

with open(refresh_path, "r", encoding="utf-8") as fp:
    refresh_rows = json.load(fp)["data"]
with open(diag_path, "r", encoding="utf-8") as fp:
    diag = json.load(fp)["data"]

services = {
    int(row["serviceId"]): row
    for row in diag.get("services") or []
    if row.get("serviceId") is not None
}
top_rows = [
    services[int(row["serviceId"])]
    for row in refresh_rows
    if row.get("serviceId") is not None and int(row["serviceId"]) in services
]
zero_rows = [
    row for row in top_rows
    if row.get("latestSavedAiStatus") == "SCORED" and (row.get("latestSavedAiScore") or 0.0) == 0.0
]

def classify(reason):
    text = (reason or "").strip()
    if not text:
        return "BLANK_REASON"
    if any(token in text for token in ["소득 수준", "소득 5분위", "기초생활수급자", "저소득층"]):
        return "INCOME_MISMATCH"
    if any(token in text for token in ["대학생", "장학금", "학자금", "청소년", "아동"]):
        return "STUDENT_AUDIENCE_MISMATCH"
    if any(token in text for token in ["강화군민", "거주", "지역", "인천 거주자"]):
        return "REGION_MISMATCH"
    if any(token in text for token in ["직접적인 도움이 적", "직접적인 도움이 되지", "연관성이 낮", "직접적인 혜택이 적"]):
        return "LOW_DIRECT_HELP"
    if any(token in text for token in ["해당되지 않", "대상으로 하", "대상군", "적합하지 않"]):
        return "AUDIENCE_MISMATCH"
    return "OTHER"

bucket_counts = Counter(classify(row.get("latestSavedAiReason")) for row in zero_rows)
source_counts = Counter(row.get("sourceType") or "UNKNOWN" for row in zero_rows)
category_counts = Counter(row.get("category") or "UNKNOWN" for row in zero_rows)

def encode(counter):
    if not counter:
        return ""
    return ",".join(f"{key}:{counter[key]}" for key in sorted(counter))

print(f"METRIC target_user_key={diag.get('userKey')}")
print(f"METRIC refresh_count={len(refresh_rows)}")
print(f"METRIC fresh_top_count={len(top_rows)}")
print(f"METRIC ai_zero_count={len(zero_rows)}")
print(f"METRIC ai_zero_reason_buckets={encode(bucket_counts)}")
print(f"METRIC ai_zero_source_distribution={encode(source_counts)}")
print(f"METRIC ai_zero_category_distribution={encode(category_counts)}")

print()
print("[AI ZERO REASON BUCKETS]")
for row in zero_rows:
    reason = row.get("latestSavedAiReason")
    print(
        f"serviceId={row.get('serviceId')}\t"
        f"bucket={classify(reason)}\t"
        f"savedRank={row.get('latestSavedRank')}\t"
        f"sourceType={row.get('sourceType')}\t"
        f"category={row.get('category')}\t"
        f"savedAiReason={reason}\t"
        f"title={row.get('title')}"
    )
PY

printf '\nartifacts=%s\n' "${ARTIFACT_DIR}"
