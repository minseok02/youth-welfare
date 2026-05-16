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

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
COOKIE_JAR="${ARTIFACT_DIR}/deadline.cookie"
SIGNUP_RESPONSE="${ARTIFACT_DIR}/signup.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/login.json"
REFRESH_RESPONSE="${ARTIFACT_DIR}/recommendations.json"
BOOKMARK_RESPONSE="${ARTIFACT_DIR}/bookmark.json"
PROFILE_RESPONSE="${ARTIFACT_DIR}/profile.json"
DISPATCH_RESPONSE="${ARTIFACT_DIR}/deadline-dispatch.json"

cleanup() {
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
else:
    first = items[0]
    print(first.get("id", ""))
    print(first.get("serviceId", ""))
    print(first.get("logId", ""))
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

smoke_print_step "health check"
smoke_assert_status 200 "$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")" "health check" "${HEALTH_RESPONSE}"

SMOKE_EMAIL="$(smoke_build_email "${SMOKE_EMAIL_PREFIX}")"
smoke_seed_verified_email "${SMOKE_EMAIL}"

smoke_print_step "signup ${SMOKE_EMAIL}"
smoke_assert_status 200 "$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/signup" "${SIGNUP_RESPONSE}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\",
      \"name\": \"${SMOKE_NAME}\",
      \"birthDate\": \"${SMOKE_BIRTH_DATE}\",
      \"sido\": \"${SMOKE_SIDO}\",
      \"sgg\": \"${SMOKE_SGG}\",
      \"incomeLevel\": ${SMOKE_INCOME_LEVEL},
      \"employmentStatus\": \"${SMOKE_EMPLOYMENT_STATUS}\",
      \"householdType\": \"${SMOKE_HOUSEHOLD_TYPE}\"
    }"
)" "signup" "${SIGNUP_RESPONSE}"

smoke_print_step "login"
smoke_assert_status 200 "$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${LOGIN_RESPONSE}" \
    -c "${COOKIE_JAR}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\"
    }"
)" "login" "${LOGIN_RESPONSE}"
ACCESS_TOKEN="$(extract_access_token "${LOGIN_RESPONSE}")"
USER_KEY="$(smoke_db_query "SELECT user_key FROM users WHERE email = '${SMOKE_EMAIL}' LIMIT 1;")"

smoke_print_step "recommendations refresh"
smoke_assert_status 200 "$(
  smoke_http_status POST "${APP_BASE_URL}/api/recommendations/refresh" "${REFRESH_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)" "recommendations refresh" "${REFRESH_RESPONSE}"

mapfile -t RECOMMENDATION_FIELDS < <(extract_recommendation_triplet "${REFRESH_RESPONSE}")
RECOMMENDATION_ID="${RECOMMENDATION_FIELDS[0]:-}"
SERVICE_ID="${RECOMMENDATION_FIELDS[1]:-}"
if [[ -z "${RECOMMENDATION_ID}" || -z "${SERVICE_ID}" ]]; then
  echo "deadline reminder smoke requires at least one recommendation" >&2
  cat "${REFRESH_RESPONSE}" >&2
  exit 1
fi

smoke_print_step "bookmark first recommendation"
smoke_assert_status 200 "$(
  smoke_http_status POST "${APP_BASE_URL}/api/recommendations/${RECOMMENDATION_ID}/bookmark" "${BOOKMARK_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)" "bookmark recommendation" "${BOOKMARK_RESPONSE}"

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

TARGET_DATE="$(date -d "+${SMOKE_DEADLINE_DAYS} days" '+%Y-%m-%d')"
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
echo "smoke_email=${SMOKE_EMAIL}"
echo "user_key=${USER_KEY}"
echo "recommendation_id=${RECOMMENDATION_ID}"
echo "service_id=${SERVICE_ID}"
echo "deadline_days=${SMOKE_DEADLINE_DAYS}"
echo "policy_count=${POLICY_COUNT}"
