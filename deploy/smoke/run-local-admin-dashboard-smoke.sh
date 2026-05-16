#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
APP_CONTAINER_NAME="${APP_CONTAINER_NAME:-youth-welfare-app}"

smoke_resolve_admin_credentials "${ROOT_DIR}"
: "${ADMIN_EMAIL:?ADMIN_EMAIL is empty; export ADMIN_EMAIL or set SECURITY_ADMIN_EMAILS/.env or /tmp/youth-welfare-admin-smoke-email}"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD is empty; export ADMIN_PASSWORD or set /tmp/youth-welfare-admin-smoke-password}"
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS:-7}"
TREND_WINDOW_DAYS_CSV="${TREND_WINDOW_DAYS_CSV:-1,7,30}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/admin-login.json"
DASHBOARD_RESPONSE="${ARTIFACT_DIR}/dashboard-summary.json"

cleanup() {
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

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

extract_jwt_roles() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import base64
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    token = json.load(fp)["data"]["accessToken"]

payload = token.split(".")[1]
payload += "=" * (-len(payload) % 4)
decoded = json.loads(base64.urlsafe_b64decode(payload))
print(",".join(decoded.get("roles") or []))
PY
}

extract_container_admin_allowlist() {
  if ! command -v docker >/dev/null 2>&1; then
    return 0
  fi

  if ! docker ps --format '{{.Names}}' | grep -Fxq "${APP_CONTAINER_NAME}"; then
    return 0
  fi

  docker exec "${APP_CONTAINER_NAME}" /bin/sh -lc 'printf "%s" "${SECURITY_ADMIN_EMAILS:-}"' 2>/dev/null || true
}

assert_dashboard_contract() {
  local response_file="$1"
  local expected_summary_window_days="$2"
  local expected_trend_windows_csv="$3"
  python3 - "$response_file" "$expected_summary_window_days" "$expected_trend_windows_csv" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

data = payload["data"]
expected_summary_window = int(sys.argv[2])
expected_trend_windows = [int(value) for value in sys.argv[3].split(",") if value]

assert data["generatedAt"], "generatedAt missing"
assert isinstance(data["collect"]["failedJobsLast24h"], int), "collect.failedJobsLast24h must be int"
assert isinstance(data["recommendation"]["totalLogs"], int), "recommendation.totalLogs must be int"
assert data["collect"]["windowDays"] == expected_summary_window, "unexpected collect windowDays"
assert data["recommendation"]["windowDays"] == expected_summary_window, "unexpected recommendation windowDays"
assert isinstance(data["recommendation"]["topWeightStage"], bool), "recommendation.topWeightStage must be bool"
if data["recommendation"]["topWeightStage"]:
    assert data["recommendation"]["nextWeightKey"] is None, "top stage should not have nextWeightKey"
    assert data["recommendation"]["nextWeightMinLogCount"] is None, "top stage should not have nextWeightMinLogCount"
    assert data["recommendation"]["remainingLogsUntilNextWeight"] is None, "top stage should not have remaining logs"
else:
    assert isinstance(data["recommendation"]["nextWeightKey"], str) and data["recommendation"]["nextWeightKey"], "nextWeightKey missing"
    assert isinstance(data["recommendation"]["nextWeightMinLogCount"], int), "nextWeightMinLogCount must be int"
    assert isinstance(data["recommendation"]["remainingLogsUntilNextWeight"], int), "remainingLogsUntilNextWeight must be int"
    assert data["recommendation"]["remainingLogsUntilNextWeight"] >= 0, "remainingLogsUntilNextWeight must be >= 0"
assert data["notification"]["windowDays"] == expected_summary_window, "unexpected notification windowDays"
assert data["search"]["windowDays"] == expected_summary_window, "unexpected search windowDays"
assert isinstance(data["notification"]["sentInWindow"], int), "notification.sentInWindow must be int"
assert isinstance(data["search"]["zeroResultSearchesInWindow"], int), "search.zeroResultSearchesInWindow must be int"

collect_windows = [point["windowDays"] for point in data["trend"]["collect"]]
recommendation_windows = [point["windowDays"] for point in data["trend"]["recommendation"]]
search_windows = [point["windowDays"] for point in data["trend"]["search"]]

assert collect_windows == expected_trend_windows, f"unexpected collect trend windows: {collect_windows}"
assert recommendation_windows == expected_trend_windows, f"unexpected recommendation trend windows: {recommendation_windows}"
assert search_windows == expected_trend_windows, f"unexpected search trend windows: {search_windows}"

print(data["generatedAt"])
print(data["collect"]["failedJobsLast24h"])
print(data["recommendation"]["totalLogs"])
print(data["recommendation"]["nextWeightKey"] or "")
print("" if data["recommendation"]["remainingLogsUntilNextWeight"] is None else data["recommendation"]["remainingLogsUntilNextWeight"])
print(data["notification"]["sentInWindow"])
print(data["search"]["zeroResultSearchesInWindow"])
print(data["collect"]["windowDays"])
print(",".join(str(v) for v in collect_windows))
PY
}

smoke_require_command curl
smoke_require_command python3

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

CONTAINER_ADMIN_ALLOWLIST="$(extract_container_admin_allowlist)"

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
ADMIN_ROLES="$(extract_jwt_roles "${LOGIN_RESPONSE}")"

if [[ ",${ADMIN_ROLES}," != *",ROLE_ADMIN,"* ]]; then
  echo "admin login succeeded but ROLE_ADMIN is missing for ${ADMIN_EMAIL}" >&2
  if [[ -n "${CONTAINER_ADMIN_ALLOWLIST}" ]]; then
    echo "current ${APP_CONTAINER_NAME} SECURITY_ADMIN_EMAILS=${CONTAINER_ADMIN_ALLOWLIST}" >&2
  else
    echo "current ${APP_CONTAINER_NAME} SECURITY_ADMIN_EMAILS is empty or container was not inspectable" >&2
  fi
  echo "for local docker validation, restart app with:" >&2
  echo "  SECURITY_ADMIN_EMAILS=${ADMIN_EMAIL} docker compose up -d --force-recreate app" >&2
  exit 1
fi

smoke_print_step "dashboard summary"
TREND_QUERY_STRING="$(python3 - "${TREND_WINDOW_DAYS_CSV}" <<'PY'
import sys
from urllib.parse import urlencode

values = [value.strip() for value in sys.argv[1].split(",") if value.strip()]
print(urlencode([("trendWindowDays", value) for value in values]))
PY
)"
DASHBOARD_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/summary?summaryWindowDays=${SUMMARY_WINDOW_DAYS}&${TREND_QUERY_STRING}" "${DASHBOARD_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${DASHBOARD_STATUS}" "dashboard summary" "${DASHBOARD_RESPONSE}"

DASHBOARD_ASSERT_OUTPUT="$(
  assert_dashboard_contract "${DASHBOARD_RESPONSE}" "${SUMMARY_WINDOW_DAYS}" "${TREND_WINDOW_DAYS_CSV}"
)"
mapfile -t DASHBOARD_VALUES <<< "${DASHBOARD_ASSERT_OUTPUT}"

echo
echo "admin dashboard smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "admin_email=${ADMIN_EMAIL}"
echo "admin_roles=${ADMIN_ROLES}"
echo "generated_at=${DASHBOARD_VALUES[0]}"
echo "collect_failed_jobs_last24h=${DASHBOARD_VALUES[1]}"
echo "recommendation_total_logs=${DASHBOARD_VALUES[2]}"
echo "recommendation_next_weight_key=${DASHBOARD_VALUES[3]}"
echo "recommendation_remaining_logs_until_next_weight=${DASHBOARD_VALUES[4]}"
echo "notification_sent_in_window=${DASHBOARD_VALUES[5]}"
echo "search_zero_result_searches_in_window=${DASHBOARD_VALUES[6]}"
echo "summary_window_days=${DASHBOARD_VALUES[7]}"
echo "collect_trend_windows=${DASHBOARD_VALUES[8]}"
echo "requested_summary_window_days=${SUMMARY_WINDOW_DAYS}"
echo "requested_trend_window_days=${TREND_WINDOW_DAYS_CSV}"
if [[ -n "${CONTAINER_ADMIN_ALLOWLIST}" ]]; then
  echo "container_security_admin_emails=${CONTAINER_ADMIN_ALLOWLIST}"
fi
