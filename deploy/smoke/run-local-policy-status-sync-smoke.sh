#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
SUMMARY_OUT="${ARTIFACT_DIR}/policy-status-sync-summary.txt"
RESPONSE_OUT="${ARTIFACT_DIR}/policy-status-sync-response.json"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
mkdir -p "${ARTIFACT_DIR}"
smoke_require_command curl
smoke_require_command python3

before_active_past_end_youth="$(smoke_db_query "select count(*) from welfare_services where source_type = 'YOUTH' and status in ('ACTIVE','UPCOMING') and apply_end_date is not null and apply_end_date < current_date;")"
before_active_past_end_gov24="$(smoke_db_query "select count(*) from welfare_services where source_type = 'GOV24' and status in ('ACTIVE','UPCOMING') and apply_end_date is not null and apply_end_date < current_date;")"

smoke_resolve_admin_credentials "${ROOT_DIR}"
smoke_resolve_admin_access_token "${ROOT_DIR}"
: "${ADMIN_EMAIL:?ADMIN_EMAIL is empty; export ADMIN_EMAIL or set SECURITY_ADMIN_EMAILS/.env or /tmp/youth-welfare-admin-smoke-email}"
if [[ -z "${ADMIN_ACCESS_TOKEN:-}" ]]; then
  : "${ADMIN_PASSWORD:?ADMIN_PASSWORD is empty; export ADMIN_PASSWORD or set /tmp/youth-welfare-admin-smoke-password}"
  LOGIN_RESPONSE="${ARTIFACT_DIR}/admin-login.json"
  LOGIN_STATUS="$(
    smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${LOGIN_RESPONSE}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"email\": \"${ADMIN_EMAIL}\",
        \"password\": \"${ADMIN_PASSWORD}\"
      }"
  )"
  smoke_assert_status 200 "${LOGIN_STATUS}" "admin login" "${LOGIN_RESPONSE}"
  ADMIN_TOKEN="$(python3 - "${LOGIN_RESPONSE}" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as f:
    print(json.load(f)["data"]["accessToken"])
PY
)"
else
  ADMIN_TOKEN="${ADMIN_ACCESS_TOKEN}"
fi

STATUS_SYNC_HTTP_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/admin/policies/status-sync" "${RESPONSE_OUT}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${STATUS_SYNC_HTTP_STATUS}" "policy status sync" "${RESPONSE_OUT}"

mapfile -t STATUS_VALUES < <(python3 - "${RESPONSE_OUT}" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as f:
    data = json.load(f)["data"]
print(data["closedCount"])
print(data["activatedCount"])
print(str(data["clusterAiCacheCleanupExecuted"]).lower())
PY
)

after_active_past_end_youth="$(smoke_db_query "select count(*) from welfare_services where source_type = 'YOUTH' and status in ('ACTIVE','UPCOMING') and apply_end_date is not null and apply_end_date < current_date;")"
after_active_past_end_gov24="$(smoke_db_query "select count(*) from welfare_services where source_type = 'GOV24' and status in ('ACTIVE','UPCOMING') and apply_end_date is not null and apply_end_date < current_date;")"

cat > "${SUMMARY_OUT}" <<EOF
policy_status_sync_smoke=passed
closed_count=${STATUS_VALUES[0]}
activated_count=${STATUS_VALUES[1]}
cluster_ai_cache_cleanup_executed=${STATUS_VALUES[2]}
before_active_past_end_youth=${before_active_past_end_youth}
after_active_past_end_youth=${after_active_past_end_youth}
before_active_past_end_gov24=${before_active_past_end_gov24}
after_active_past_end_gov24=${after_active_past_end_gov24}
artifact_dir=${ARTIFACT_DIR}
EOF

cat "${SUMMARY_OUT}"
