#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-notification.channel.smoke}"
SMOKE_NAME="${SMOKE_NAME:-알림채널점검}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
SMOKE_NOTIFICATION_MIN_SCORE="${SMOKE_NOTIFICATION_MIN_SCORE:-0.0}"
SMOKE_DISPLAY_COUNT="${SMOKE_DISPLAY_COUNT:-10}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-90}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
ADMIN_LOGIN_RESPONSE="${ARTIFACT_DIR}/admin.login.json"

ADMIN_EMAIL="${ADMIN_EMAIL:-}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"
ADMIN_ACCESS_TOKEN=""
ADMIN_USER_KEY=""
ORIGINAL_NOTIFICATION_YN=""
ORIGINAL_NOTIFICATION_EMAIL_YN=""
ORIGINAL_NOTIFICATION_IN_APP_YN=""
ORIGINAL_NOTIFICATION_WEB_PUSH_YN=""
ORIGINAL_NOTIFICATION_PERIOD=""
ORIGINAL_NOTIFICATION_MIN_SCORE=""
ORIGINAL_DISPLAY_COUNT=""
RESTORE_PROFILE="false"

cleanup() {
  if [[ "${RESTORE_PROFILE}" == "true" && -n "${ADMIN_ACCESS_TOKEN}" ]]; then
    smoke_http_status PUT "${APP_BASE_URL}/api/users/me" /dev/null \
      -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
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

extract_dispatch_status() {
  json_read "$1" "data.dispatchStatus"
}

extract_recommendation_count() {
  json_read "$1" "data.recommendationCount"
}

extract_error_code() {
  json_read "$1" "errorCode"
}

query_count() {
  local sql="$1"
  smoke_db_query "${sql}"
}

reset_manual_digest_history() {
  smoke_db_query "DELETE FROM user_alerts WHERE user_key='${ADMIN_USER_KEY}' AND kind='RECOMMENDATION_DIGEST' AND event_key LIKE 'manual:%';" >/dev/null
  smoke_db_query "DELETE FROM notifications WHERE user_key='${ADMIN_USER_KEY}' AND period_type='manual';" >/dev/null
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

login_admin() {
  smoke_resolve_admin_credentials "${ROOT_DIR}"
  : "${ADMIN_EMAIL:?ADMIN_EMAIL is empty; export ADMIN_EMAIL or set /tmp/youth-welfare-admin-smoke-email}"

  smoke_resolve_admin_access_token "${ROOT_DIR}"
  if [[ -n "${ADMIN_ACCESS_TOKEN:-}" ]]; then
    local admin_email_hash
    admin_email_hash="$(smoke_sha256_hex "${ADMIN_EMAIL}")"
    ADMIN_USER_KEY="$(
      smoke_db_query "
        SELECT u.user_key
        FROM auth_users au
        JOIN users u ON u.user_key = au.user_key
        WHERE au.email_lookup_hash = $(smoke_sql_quote "${admin_email_hash}")
          AND COALESCE(au.is_active, true) = true
          AND COALESCE(u.is_active, true) = true
        LIMIT 1;
      "
    )"
    if [[ -z "${ADMIN_USER_KEY}" ]]; then
      echo "failed to resolve admin user_key for ${ADMIN_EMAIL}" >&2
      exit 1
    fi
    return 0
  fi

  : "${ADMIN_PASSWORD:?ADMIN_PASSWORD is empty; export ADMIN_PASSWORD or set /tmp/youth-welfare-admin-smoke-password}"

  smoke_print_step "admin login (${ADMIN_EMAIL})"
  local login_status
  login_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${ADMIN_LOGIN_RESPONSE}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"email\": \"${ADMIN_EMAIL}\",
        \"password\": \"${ADMIN_PASSWORD}\"
      }"
  )"
  smoke_assert_status 200 "${login_status}" "admin login" "${ADMIN_LOGIN_RESPONSE}"

  ADMIN_ACCESS_TOKEN="$(extract_access_token "${ADMIN_LOGIN_RESPONSE}")"
  local admin_email_hash
  admin_email_hash="$(smoke_sha256_hex "${ADMIN_EMAIL}")"
  ADMIN_USER_KEY="$(
    smoke_db_query "
      SELECT u.user_key
      FROM auth_users au
      JOIN users u ON u.user_key = au.user_key
      WHERE au.email_lookup_hash = $(smoke_sql_quote "${admin_email_hash}")
        AND COALESCE(au.is_active, true) = true
        AND COALESCE(u.is_active, true) = true
      LIMIT 1;
    "
  )"
  if [[ -z "${ADMIN_USER_KEY}" ]]; then
    echo "failed to resolve admin user_key for ${ADMIN_EMAIL}" >&2
    exit 1
  fi
}

capture_original_profile() {
  local profile_response="${ARTIFACT_DIR}/admin.profile.original.json"
  local profile_status
  profile_status="$(
    smoke_http_status GET "${APP_BASE_URL}/api/users/me" "${profile_response}" \
      -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}"
  )"
  smoke_assert_status 200 "${profile_status}" "capture admin profile" "${profile_response}"

  ORIGINAL_NOTIFICATION_YN="$(json_read "${profile_response}" "data.notificationYn")"
  ORIGINAL_NOTIFICATION_EMAIL_YN="$(json_read "${profile_response}" "data.notificationEmailYn")"
  ORIGINAL_NOTIFICATION_IN_APP_YN="$(json_read "${profile_response}" "data.notificationInAppYn")"
  ORIGINAL_NOTIFICATION_WEB_PUSH_YN="$(json_read "${profile_response}" "data.notificationWebPushYn")"
  ORIGINAL_NOTIFICATION_PERIOD="$(json_read "${profile_response}" "data.notificationPeriod")"
  ORIGINAL_NOTIFICATION_MIN_SCORE="$(json_read "${profile_response}" "data.notificationMinScore")"
  ORIGINAL_DISPLAY_COUNT="$(json_read "${profile_response}" "data.displayCount")"
  RESTORE_PROFILE="true"
}

refresh_recommendations() {
  local scenario_key="$1"
  local refresh_response="${ARTIFACT_DIR}/${scenario_key}.recommend-refresh.json"

  smoke_clear_recommendation_refresh_rate_limit "${ADMIN_USER_KEY}"
  smoke_print_step "${scenario_key}: recommendations refresh"
  local refresh_status
  refresh_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/recommendations/refresh" "${refresh_response}" \
      -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}"
  )"
  smoke_assert_status 200 "${refresh_status}" "${scenario_key} recommendations refresh" "${refresh_response}"

  local count
  count="$(python3 - "${refresh_response}" <<'PY'
import json
import sys
with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)
print(len(payload.get("data") or []))
PY
)"
  if (( count <= 0 )); then
    echo "${scenario_key}: recommendations refresh returned no items" >&2
    cat "${refresh_response}" >&2
    exit 1
  fi
}

save_notification_profile() {
  local scenario_key="$1"
  local notif_yn="$2"
  local email_yn="$3"
  local in_app_yn="$4"
  local web_push_yn="$5"
  local expected_status="$6"

  local response_file="${ARTIFACT_DIR}/${scenario_key}.profile.json"
  smoke_print_step "${scenario_key}: save notification profile"
  local status
  status="$(
    smoke_http_status PUT "${APP_BASE_URL}/api/users/me" "${response_file}" \
      -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"notificationYn\": ${notif_yn},
        \"notificationEmailYn\": ${email_yn},
        \"notificationInAppYn\": ${in_app_yn},
        \"notificationWebPushYn\": ${web_push_yn},
        \"notificationPeriod\": \"DAILY\",
        \"notificationMinScore\": ${SMOKE_NOTIFICATION_MIN_SCORE},
        \"displayCount\": ${SMOKE_DISPLAY_COUNT}
      }"
  )"
  smoke_assert_status "${expected_status}" "${status}" "${scenario_key} save notification profile" "${response_file}"

  if [[ "${expected_status}" == "200" ]]; then
    local success
    success="$(json_read "${response_file}" "success")"
    assert_equals "true" "${success}" "${scenario_key} save notification profile success flag"
  fi
}

assert_profile_flags() {
  local scenario_key="$1"
  local expected_notif="$2"
  local expected_email="$3"
  local expected_in_app="$4"
  local expected_web_push="$5"

  local profile_response="${ARTIFACT_DIR}/${scenario_key}.profile-read.json"
  local profile_status
  profile_status="$(
    smoke_http_status GET "${APP_BASE_URL}/api/users/me" "${profile_response}" \
      -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}"
  )"
  smoke_assert_status 200 "${profile_status}" "${scenario_key} read profile" "${profile_response}"

  assert_equals "${expected_notif}" "$(json_read "${profile_response}" "data.notificationYn")" "${scenario_key} notificationYn"
  assert_equals "${expected_email}" "$(json_read "${profile_response}" "data.notificationEmailYn")" "${scenario_key} notificationEmailYn"
  assert_equals "${expected_in_app}" "$(json_read "${profile_response}" "data.notificationInAppYn")" "${scenario_key} notificationInAppYn"
  assert_equals "${expected_web_push}" "$(json_read "${profile_response}" "data.notificationWebPushYn")" "${scenario_key} notificationWebPushYn"
}

run_digest_dispatch() {
  local scenario_key="$1"
  local response_file="${ARTIFACT_DIR}/${scenario_key}.digest.json"

  smoke_print_step "${scenario_key}: digest test dispatch"
  local status
  status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/notifications/digest-test-dispatch" "${response_file}" \
      -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}"
  )"
  smoke_assert_status 200 "${status}" "${scenario_key} digest test dispatch" "${response_file}"
  assert_equals "true" "$(json_read "${response_file}" "success")" "${scenario_key} digest response success"
  assert_equals "SENT" "$(extract_dispatch_status "${response_file}")" "${scenario_key} digest dispatchStatus"

  local recommendation_count
  recommendation_count="$(extract_recommendation_count "${response_file}")"
  if (( recommendation_count <= 0 )); then
    echo "${scenario_key}: digest recommendationCount must be > 0" >&2
    cat "${response_file}" >&2
    exit 1
  fi
}

run_inapp_only_scenario() {
  refresh_recommendations "inapp_only"
  save_notification_profile "inapp_only" true false true false 200
  assert_profile_flags "inapp_only" true false true false
  reset_manual_digest_history

  local notif_before alert_before
  notif_before="$(query_count "SELECT COUNT(*) FROM notifications WHERE user_key='${ADMIN_USER_KEY}';")"
  alert_before="$(query_count "SELECT COUNT(*) FROM user_alerts WHERE user_key='${ADMIN_USER_KEY}';")"

  run_digest_dispatch "inapp_only"

  local notif_after alert_after latest_notif_status latest_notif_total latest_alert_status
  notif_after="$(query_count "SELECT COUNT(*) FROM notifications WHERE user_key='${ADMIN_USER_KEY}';")"
  alert_after="$(query_count "SELECT COUNT(*) FROM user_alerts WHERE user_key='${ADMIN_USER_KEY}';")"
  latest_notif_status="$(query_count "SELECT status FROM notifications WHERE user_key='${ADMIN_USER_KEY}' ORDER BY created_at DESC LIMIT 1;")"
  latest_notif_total="$(query_count "SELECT total_services FROM notifications WHERE user_key='${ADMIN_USER_KEY}' ORDER BY created_at DESC LIMIT 1;")"
  latest_alert_status="$(query_count "SELECT status FROM user_alerts WHERE user_key='${ADMIN_USER_KEY}' ORDER BY created_at DESC LIMIT 1;")"

  assert_int_delta "${notif_before}" "${notif_after}" 1 "inapp_only notifications delta"
  assert_int_delta "${alert_before}" "${alert_after}" 1 "inapp_only user_alerts delta"
  assert_equals "sent" "${latest_notif_status}" "inapp_only latest notification status"
  assert_equals "UNREAD" "${latest_alert_status}" "inapp_only latest user_alert status"
  if (( latest_notif_total <= 0 )); then
    echo "inapp_only latest notification total_services must be > 0, got ${latest_notif_total}" >&2
    exit 1
  fi
}

run_webpush_only_subscription_state_scenario() {
  refresh_recommendations "webpush_only"
  save_notification_profile "webpush_only" true false false true 200
  assert_profile_flags "webpush_only" true false false true
  reset_manual_digest_history

  local notif_before alert_before push_before
  notif_before="$(query_count "SELECT COUNT(*) FROM notifications WHERE user_key='${ADMIN_USER_KEY}';")"
  alert_before="$(query_count "SELECT COUNT(*) FROM user_alerts WHERE user_key='${ADMIN_USER_KEY}';")"
  push_before="$(query_count "SELECT COUNT(*) FROM web_push_subscriptions WHERE user_key='${ADMIN_USER_KEY}' AND enabled = true;")"

  run_digest_dispatch "webpush_only"

  local notif_after alert_after push_after latest_notif_status latest_notif_total
  notif_after="$(query_count "SELECT COUNT(*) FROM notifications WHERE user_key='${ADMIN_USER_KEY}';")"
  alert_after="$(query_count "SELECT COUNT(*) FROM user_alerts WHERE user_key='${ADMIN_USER_KEY}';")"
  push_after="$(query_count "SELECT COUNT(*) FROM web_push_subscriptions WHERE user_key='${ADMIN_USER_KEY}' AND enabled = true;")"
  latest_notif_status="$(query_count "SELECT status FROM notifications WHERE user_key='${ADMIN_USER_KEY}' ORDER BY created_at DESC LIMIT 1;")"
  latest_notif_total="$(query_count "SELECT total_services FROM notifications WHERE user_key='${ADMIN_USER_KEY}' ORDER BY created_at DESC LIMIT 1;")"

  assert_int_delta "${notif_before}" "${notif_after}" 1 "webpush_only notifications delta"
  assert_int_delta "${alert_before}" "${alert_after}" 0 "webpush_only user_alerts delta"
  assert_equals "${push_before}" "${push_after}" "webpush_only enabled subscriptions unchanged"
  assert_equals "sent" "${latest_notif_status}" "webpush_only latest notification status"
  if (( latest_notif_total <= 0 )); then
    echo "webpush_only latest notification total_services must be > 0, got ${latest_notif_total}" >&2
    exit 1
  fi
}

run_invalid_no_channel_scenario() {
  local response_file="${ARTIFACT_DIR}/invalid_channels.profile.invalid.json"

  smoke_print_step "invalid_channels: reject no enabled channels"
  local status
  status="$(
    smoke_http_status PUT "${APP_BASE_URL}/api/users/me" "${response_file}" \
      -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"notificationYn\": true,
        \"notificationEmailYn\": false,
        \"notificationInAppYn\": false,
        \"notificationWebPushYn\": false,
        \"notificationPeriod\": \"DAILY\",
        \"notificationMinScore\": ${SMOKE_NOTIFICATION_MIN_SCORE},
        \"displayCount\": ${SMOKE_DISPLAY_COUNT}
      }"
  )"
  smoke_assert_status 400 "${status}" "invalid_channels save notification profile" "${response_file}"
}

smoke_require_command curl
smoke_require_command python3

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

login_admin
capture_original_profile
run_inapp_only_scenario
run_webpush_only_subscription_state_scenario
run_invalid_no_channel_scenario

echo
echo "notification channel smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "admin_email=${ADMIN_EMAIL}"
echo "admin_user_key=${ADMIN_USER_KEY}"
echo "artifact_dir=${ARTIFACT_DIR}"
echo "scenarios=inapp_only,webpush_only_subscription_state,invalid_channels"
