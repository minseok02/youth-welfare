#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-deadline.reminder.smoke}"
SMOKE_NAME="${SMOKE_NAME:-마감알림점검}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
SMOKE_DEADLINE_DAYS="${SMOKE_DEADLINE_DAYS:-3}"
SMOKE_NOTIFICATION_MIN_SCORE="${SMOKE_NOTIFICATION_MIN_SCORE:-0.0}"
SMOKE_DISPLAY_COUNT="${SMOKE_DISPLAY_COUNT:-10}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-90}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
ALLOW_DIRECT_DB_MUTATION="${ALLOW_DIRECT_DB_MUTATION:-false}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/admin.login.json"
REFRESH_RESPONSE="${ARTIFACT_DIR}/recommendations.json"
BOOKMARK_RESPONSE="${ARTIFACT_DIR}/bookmark.json"
PROFILE_RESPONSE="${ARTIFACT_DIR}/profile.json"
DISPATCH_RESPONSE="${ARTIFACT_DIR}/deadline-dispatch.json"
RESTORE_SERVICE_STATE="false"
ORIGINAL_APPLY_END_DATE=""
ORIGINAL_STATUS=""
SERVICE_ID=""
ADMIN_EMAIL="${ADMIN_EMAIL:-}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"
USER_KEY=""
ACCESS_TOKEN=""
RECOMMENDATION_ID=""
ORIGINAL_IS_BOOKMARKED=""
RESTORE_BOOKMARK="false"
RESTORE_PROFILE="false"
ORIGINAL_NOTIFICATION_YN=""
ORIGINAL_NOTIFICATION_EMAIL_YN=""
ORIGINAL_NOTIFICATION_IN_APP_YN=""
ORIGINAL_NOTIFICATION_WEB_PUSH_YN=""
ORIGINAL_NOTIFICATION_PERIOD=""
ORIGINAL_NOTIFICATION_MIN_SCORE=""
ORIGINAL_DISPLAY_COUNT=""

cleanup() {
  if [[ "${RESTORE_BOOKMARK}" == "true" && -n "${RECOMMENDATION_ID}" && -n "${ACCESS_TOKEN}" ]]; then
    smoke_http_status POST "${APP_BASE_URL}/api/recommendations/${RECOMMENDATION_ID}/bookmark" /dev/null \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" >/dev/null || echo "failed to restore bookmark state for recommendationId=${RECOMMENDATION_ID}" >&2
  fi

  if [[ "${RESTORE_SERVICE_STATE}" == "true" && -n "${SERVICE_ID}" ]]; then
    local apply_end_date_sql="NULL"
    local status_sql="NULL"

    if [[ -n "${ORIGINAL_APPLY_END_DATE}" ]]; then
      apply_end_date_sql="DATE '${ORIGINAL_APPLY_END_DATE}'"
    fi

    if [[ -n "${ORIGINAL_STATUS}" ]]; then
      status_sql="'${ORIGINAL_STATUS//\'/\'\'}'"
    fi

    smoke_db_query \
      "UPDATE welfare_services SET apply_end_date = ${apply_end_date_sql}, status = ${status_sql} WHERE id = ${SERVICE_ID};" \
      >/dev/null || echo "failed to restore welfare_services row id=${SERVICE_ID}" >&2
  fi

  if [[ "${RESTORE_PROFILE}" == "true" && -n "${ACCESS_TOKEN}" ]]; then
    smoke_http_status PUT "${APP_BASE_URL}/api/users/me" /dev/null \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"notificationYn\": ${ORIGINAL_NOTIFICATION_YN},
        \"notificationEmailYn\": ${ORIGINAL_NOTIFICATION_EMAIL_YN},
        \"notificationInAppYn\": ${ORIGINAL_NOTIFICATION_IN_APP_YN},
        \"notificationWebPushYn\": ${ORIGINAL_NOTIFICATION_WEB_PUSH_YN},
        \"notificationPeriod\": \"${ORIGINAL_NOTIFICATION_PERIOD}\",
        \"notificationMinScore\": ${ORIGINAL_NOTIFICATION_MIN_SCORE},
        \"displayCount\": ${ORIGINAL_DISPLAY_COUNT}
      }" >/dev/null || echo "failed to restore admin notification profile" >&2
  fi

  if [[ "${KEEP_ARTIFACTS}" != "true" ]]; then
    rm -rf "${ARTIFACT_DIR}"
  fi
}
trap cleanup EXIT

json_read() {
  local body_file="$1"
  local expr="$2"
  python3 - "$body_file" "$expr" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    data = json.load(fp)

value = data
for part in sys.argv[2].split("."):
    if not part:
        continue
    value = value[part]

if isinstance(value, bool):
    print("true" if value else "false")
elif value is None:
    print("null")
else:
    print(value)
PY
}

extract_access_token() {
  json_read "$1" "data.accessToken"
}

extract_recommendation_triplet() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

items = payload.get("data") or []
if not items:
    print("")
    print("")
    print("")
    print("")
else:
    first = items[0]
    print(first.get("id", ""))
    print(first.get("serviceId", ""))
    print(first.get("logId", ""))
    print("true" if first.get("isBookmarked") else "false")
PY
}

assert_equals() {
  local expected="$1"
  local actual="$2"
  local message="$3"
  if [[ "${expected}" != "${actual}" ]]; then
    echo "${message}: expected=${expected} actual=${actual}" >&2
    exit 1
  fi
}

assert_int_delta() {
  local before="$1"
  local after="$2"
  local delta="$3"
  local message="$4"
  local actual_delta=$((after - before))
  if (( actual_delta != delta )); then
    echo "${message}: expected_delta=${delta} actual_delta=${actual_delta} before=${before} after=${after}" >&2
    exit 1
  fi
}

ALLOW_DIRECT_DB_MUTATION="$(smoke_normalize_bool "${ALLOW_DIRECT_DB_MUTATION}")"
KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"

if [[ "$(smoke_resolve_db_mode)" != "docker" && "${ALLOW_DIRECT_DB_MUTATION}" != "true" ]]; then
  echo "deadline reminder smoke mutates welfare_services; refuse to run against direct postgres mode unless ALLOW_DIRECT_DB_MUTATION=true" >&2
  exit 1
fi

login_admin() {
  smoke_resolve_admin_credentials "${ROOT_DIR}"
  : "${ADMIN_EMAIL:?ADMIN_EMAIL is empty; export ADMIN_EMAIL or set /tmp/youth-welfare-admin-smoke-email}"
  : "${ADMIN_PASSWORD:?ADMIN_PASSWORD is empty; export ADMIN_PASSWORD or set /tmp/youth-welfare-admin-smoke-password}"

  smoke_print_step "admin login (${ADMIN_EMAIL})"
  smoke_assert_status 200 "$(
    smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${LOGIN_RESPONSE}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"email\": \"${ADMIN_EMAIL}\",
        \"password\": \"${ADMIN_PASSWORD}\"
      }"
  )" "admin login" "${LOGIN_RESPONSE}"
  ACCESS_TOKEN="$(extract_access_token "${LOGIN_RESPONSE}")"
  USER_KEY="$(smoke_db_query "SELECT user_key FROM users WHERE email = '${ADMIN_EMAIL}' LIMIT 1;")"
  if [[ -z "${USER_KEY}" ]]; then
    echo "failed to resolve admin user_key for ${ADMIN_EMAIL}" >&2
    exit 1
  fi
}

capture_original_profile() {
  local original_profile_response="${ARTIFACT_DIR}/profile.original.json"
  smoke_assert_status 200 "$(
    smoke_http_status GET "${APP_BASE_URL}/api/users/me" "${original_profile_response}" \
      -H "Authorization: Bearer ${ACCESS_TOKEN}"
  )" "capture admin profile" "${original_profile_response}"

  ORIGINAL_NOTIFICATION_YN="$(json_read "${original_profile_response}" "data.notificationYn")"
  ORIGINAL_NOTIFICATION_EMAIL_YN="$(json_read "${original_profile_response}" "data.notificationEmailYn")"
  ORIGINAL_NOTIFICATION_IN_APP_YN="$(json_read "${original_profile_response}" "data.notificationInAppYn")"
  ORIGINAL_NOTIFICATION_WEB_PUSH_YN="$(json_read "${original_profile_response}" "data.notificationWebPushYn")"
  ORIGINAL_NOTIFICATION_PERIOD="$(json_read "${original_profile_response}" "data.notificationPeriod")"
  ORIGINAL_NOTIFICATION_MIN_SCORE="$(json_read "${original_profile_response}" "data.notificationMinScore")"
  ORIGINAL_DISPLAY_COUNT="$(json_read "${original_profile_response}" "data.displayCount")"
  RESTORE_PROFILE="true"
}

smoke_print_step "health check"
smoke_assert_status 200 "$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")" "health check" "${HEALTH_RESPONSE}"

SMOKE_EMAIL="$(smoke_build_email "${SMOKE_EMAIL_PREFIX}")"
login_admin
capture_original_profile

smoke_print_step "recommendations refresh"
smoke_assert_status 200 "$(
  smoke_http_status POST "${APP_BASE_URL}/api/recommendations/refresh" "${REFRESH_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)" "recommendations refresh" "${REFRESH_RESPONSE}"

mapfile -t RECOMMENDATION_FIELDS < <(extract_recommendation_triplet "${REFRESH_RESPONSE}")
RECOMMENDATION_ID="${RECOMMENDATION_FIELDS[0]:-}"
SERVICE_ID="${RECOMMENDATION_FIELDS[1]:-}"
ORIGINAL_IS_BOOKMARKED="${RECOMMENDATION_FIELDS[3]:-false}"
if [[ -z "${RECOMMENDATION_ID}" || -z "${SERVICE_ID}" ]]; then
  echo "deadline reminder smoke requires at least one recommendation" >&2
  cat "${REFRESH_RESPONSE}" >&2
  exit 1
fi

if [[ "${ORIGINAL_IS_BOOKMARKED}" != "true" ]]; then
  smoke_print_step "bookmark first recommendation"
  smoke_assert_status 200 "$(
    smoke_http_status POST "${APP_BASE_URL}/api/recommendations/${RECOMMENDATION_ID}/bookmark" "${BOOKMARK_RESPONSE}" \
      -H "Authorization: Bearer ${ACCESS_TOKEN}"
  )" "bookmark recommendation" "${BOOKMARK_RESPONSE}"
  RESTORE_BOOKMARK="true"
fi

smoke_print_step "save in-app only notification profile"
smoke_assert_status 200 "$(
  smoke_http_status PUT "${APP_BASE_URL}/api/users/me" "${PROFILE_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"notificationYn\": true,
      \"notificationEmailYn\": false,
      \"notificationInAppYn\": true,
      \"notificationWebPushYn\": false,
      \"notificationPeriod\": \"DAILY\",
      \"notificationMinScore\": ${SMOKE_NOTIFICATION_MIN_SCORE},
      \"displayCount\": ${SMOKE_DISPLAY_COUNT}
    }"
)" "save notification profile" "${PROFILE_RESPONSE}"

TARGET_DATE="$(smoke_db_query "SELECT (CURRENT_DATE + ${SMOKE_DEADLINE_DAYS})::date;")"
ORIGINAL_SERVICE_STATE="$(
  smoke_db_query "SELECT coalesce(to_char(apply_end_date, 'YYYY-MM-DD'), '__SMOKE_NULL__'), coalesce(status, '__SMOKE_NULL__') FROM welfare_services WHERE id = ${SERVICE_ID};"
)"
if [[ -z "${ORIGINAL_SERVICE_STATE}" ]]; then
  echo "failed to capture original welfare_services row for id=${SERVICE_ID}" >&2
  exit 1
fi

IFS=$'\t' read -r ORIGINAL_APPLY_END_DATE ORIGINAL_STATUS <<< "${ORIGINAL_SERVICE_STATE}"
if [[ "${ORIGINAL_APPLY_END_DATE}" == "__SMOKE_NULL__" ]]; then
  ORIGINAL_APPLY_END_DATE=""
fi
if [[ "${ORIGINAL_STATUS}" == "__SMOKE_NULL__" ]]; then
  ORIGINAL_STATUS=""
fi
RESTORE_SERVICE_STATE="true"

smoke_print_step "force bookmarked service apply_end_date=${TARGET_DATE}"
smoke_db_query "UPDATE welfare_services SET apply_end_date = DATE '${TARGET_DATE}', status = 'ACTIVE' WHERE id = ${SERVICE_ID};" >/dev/null

BEFORE_NOTIFICATIONS="$(smoke_db_query "SELECT COUNT(*) FROM notifications WHERE user_key = '${USER_KEY}';")"
BEFORE_ALERTS="$(smoke_db_query "SELECT COUNT(*) FROM user_alerts WHERE user_key = '${USER_KEY}';")"

smoke_print_step "deadline test dispatch"
smoke_assert_status 200 "$(
  smoke_http_status POST "${APP_BASE_URL}/api/notifications/deadline-test-dispatch?days=${SMOKE_DEADLINE_DAYS}" "${DISPATCH_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)" "deadline test dispatch" "${DISPATCH_RESPONSE}"

assert_equals "SENT" "$(json_read "${DISPATCH_RESPONSE}" "data.dispatchStatus")" "deadline dispatch status"
POLICY_COUNT="$(json_read "${DISPATCH_RESPONSE}" "data.policyCount")"
if (( POLICY_COUNT <= 0 )); then
  echo "deadline dispatch returned no candidate policies" >&2
  cat "${DISPATCH_RESPONSE}" >&2
  exit 1
fi

AFTER_NOTIFICATIONS="$(smoke_db_query "SELECT COUNT(*) FROM notifications WHERE user_key = '${USER_KEY}';")"
AFTER_ALERTS="$(smoke_db_query "SELECT COUNT(*) FROM user_alerts WHERE user_key = '${USER_KEY}';")"

assert_int_delta "${BEFORE_NOTIFICATIONS}" "${AFTER_NOTIFICATIONS}" 1 "notifications delta"
assert_int_delta "${BEFORE_ALERTS}" "${AFTER_ALERTS}" 1 "user_alerts delta"

LATEST_NOTIFICATION_STATUS="$(smoke_db_query "SELECT status FROM notifications WHERE user_key = '${USER_KEY}' ORDER BY id DESC LIMIT 1;")"
LATEST_ALERT_KIND="$(smoke_db_query "SELECT kind FROM user_alerts WHERE user_key = '${USER_KEY}' ORDER BY id DESC LIMIT 1;")"
LATEST_ALERT_STATUS="$(smoke_db_query "SELECT status FROM user_alerts WHERE user_key = '${USER_KEY}' ORDER BY id DESC LIMIT 1;")"

assert_equals "sent" "${LATEST_NOTIFICATION_STATUS}" "latest notification status"
assert_equals "DEADLINE_REMINDER" "${LATEST_ALERT_KIND}" "latest alert kind"
assert_equals "UNREAD" "${LATEST_ALERT_STATUS}" "latest alert status"

echo
echo "deadline reminder smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "admin_email=${ADMIN_EMAIL}"
echo "user_key=${USER_KEY}"
echo "recommendation_id=${RECOMMENDATION_ID}"
echo "service_id=${SERVICE_ID}"
echo "deadline_days=${SMOKE_DEADLINE_DAYS}"
echo "policy_count=${POLICY_COUNT}"
