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
AI_REASON_RESPONSE="${ARTIFACT_DIR}/ai-reasons.tsv"

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

smoke_print_step "query persisted ai reasons"
smoke_db_query "
  with latest_batch as (
    select max(recommended_at) as recommended_at
    from user_recommendations
    where user_key = '${TARGET_USER_KEY}'
  )
  select
    ur.service_id,
    coalesce(ur.ai_reason, ''),
    coalesce(ur.ai_status::text, ''),
    coalesce(ur.ai_score::text, ''),
    coalesce(ur.final_score::text, ''),
    coalesce(ws.title, '')
  from user_recommendations ur
  join latest_batch lb on ur.recommended_at = lb.recommended_at
  join welfare_services ws on ws.id = ur.service_id
  where ur.user_key = '${TARGET_USER_KEY}'
    and ur.service_id in (${REFRESH_SERVICE_IDS_CSV})
  order by ur.final_score desc, ur.service_id desc;
" > "${AI_REASON_RESPONSE}"

python3 - "${REFRESH_RESPONSE}" "${DIAGNOSTICS_RESPONSE}" "${AI_REASON_RESPONSE}" <<'PY'
import csv
import json
import sys
from collections import defaultdict

refresh_path, diag_path, reason_path = sys.argv[1:4]

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
positive_rows = [
    row for row in top_rows
    if row.get("latestSavedAiStatus") == "SCORED" and (row.get("latestSavedAiScore") or 0.0) > 0.0
]

reason_map = {}
with open(reason_path, "r", encoding="utf-8") as fp:
    reader = csv.reader(fp, delimiter="\t")
    for row in reader:
        if not row:
            continue
        reason_map[int(row[0])] = {
            "aiReason": row[1],
            "aiStatus": row[2],
            "aiScore": row[3],
            "finalScore": row[4],
            "title": row[5],
        }

positive_by_source_category = defaultdict(list)
for row in positive_rows:
    positive_by_source_category[(row.get("sourceType"), row.get("category"))].append(row)
for rows in positive_by_source_category.values():
    rows.sort(key=lambda row: (row.get("latestSavedRank") or 9999, row.get("serviceId") or 99999999))

def choose_comparator(zero_row):
    same_cat = positive_by_source_category.get((zero_row.get("sourceType"), zero_row.get("category")), [])
    return same_cat[0] if same_cat else None

print(f"METRIC target_user_key={diag.get('userKey')}")
print(f"METRIC refresh_count={len(refresh_rows)}")
print(f"METRIC ai_zero_count={len(zero_rows)}")
print(f"METRIC ai_positive_count={len(positive_rows)}")
print()
print("[AI REASON CONTRAST]")
for row in zero_rows:
    zero_id = int(row["serviceId"])
    zero_reason = reason_map.get(zero_id, {})
    comp = choose_comparator(row)
    print(
        f"zero={zero_id}\t"
        f"title={row.get('title')}\t"
        f"category={row.get('category')}\t"
        f"savedAi={row.get('latestSavedAiScore')}\t"
        f"savedRank={row.get('latestSavedRank')}\t"
        f"aiReason={zero_reason.get('aiReason','')}"
    )
    if comp is None:
        print("comp=NONE")
        print()
        continue
    comp_id = int(comp["serviceId"])
    comp_reason = reason_map.get(comp_id, {})
    print(
        f"comp={comp_id}\t"
        f"title={comp.get('title')}\t"
        f"category={comp.get('category')}\t"
        f"savedAi={comp.get('latestSavedAiScore')}\t"
        f"savedRank={comp.get('latestSavedRank')}\t"
        f"aiReason={comp_reason.get('aiReason','')}"
    )
    print()
PY

printf '\nartifacts=%s\n' "${ARTIFACT_DIR}"
