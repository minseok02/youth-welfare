#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
APP_CONTAINER_NAME="${APP_CONTAINER_NAME:-youth-welfare-app}"
DB_CONTAINER_NAME="${DB_CONTAINER_NAME:-youth-welfare-db}"
DB_NAME="${DB_NAME:-youth_welfare}"

smoke_resolve_admin_credentials "${ROOT_DIR}"
if [[ -z "${ADMIN_EMAIL:-}" ]]; then
  echo "ADMIN_EMAIL is empty; set ADMIN_EMAIL, SECURITY_ADMIN_EMAILS in ${ENV_FILE:-${ROOT_DIR}/.env}, or /tmp/youth-welfare-admin-smoke-email" >&2
  exit 1
fi
if [[ -z "${ADMIN_PASSWORD:-}" ]]; then
  echo "ADMIN_PASSWORD is empty; set ADMIN_PASSWORD, ADMIN_PASSWORD in ${ENV_FILE:-${ROOT_DIR}/.env}, or /tmp/youth-welfare-admin-smoke-password" >&2
  exit 1
fi

SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-forced.logout.smoke}"
SMOKE_NAME="${SMOKE_NAME:-강제로그아웃}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"

DB_QUERY_USERNAME="${DB_QUERY_USERNAME:-migration_admin}"
DB_QUERY_PASSWORD="${DB_QUERY_PASSWORD:-welfare1234!}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
USER_COOKIE_JAR="${ARTIFACT_DIR}/user.cookie"
ADMIN_COOKIE_JAR="${ARTIFACT_DIR}/admin.cookie"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SIGNUP_RESPONSE="${ARTIFACT_DIR}/signup.json"
USER_LOGIN_RESPONSE="${ARTIFACT_DIR}/user-login.json"
USER_REFRESH_RESPONSE="${ARTIFACT_DIR}/user-refresh.json"
ADMIN_LOGIN_RESPONSE="${ARTIFACT_DIR}/admin-login.json"
FORCED_LOGOUT_RESPONSE="${ARTIFACT_DIR}/forced-logout.json"
OLD_ACCESS_RESPONSE="${ARTIFACT_DIR}/old-access.json"
OLD_REFRESH_RESPONSE="${ARTIFACT_DIR}/old-refresh.json"
RELOGIN_RESPONSE="${ARTIFACT_DIR}/relogin.json"
RELOGIN_BOOKMARKS_RESPONSE="${ARTIFACT_DIR}/relogin-bookmarks.json"
ADMIN_LOG_MATCH_FILE="${ARTIFACT_DIR}/forced-logout-log.txt"

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

extract_error_code() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

print(payload.get("errorCode", ""))
PY
}

lookup_user_key_by_email() {
  local email="$1"
  smoke_db_query "SELECT user_key FROM users WHERE email = '${email}' LIMIT 1;"
}

smoke_require_command curl
smoke_require_command python3
smoke_require_command docker

SMOKE_EMAIL="$(smoke_build_email "${SMOKE_EMAIL_PREFIX}")"

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

smoke_print_step "signup ${SMOKE_EMAIL}"
smoke_seed_verified_email "${SMOKE_EMAIL}"
SIGNUP_STATUS="$(
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
)"
smoke_assert_status 200 "${SIGNUP_STATUS}" "signup" "${SIGNUP_RESPONSE}"

smoke_print_step "user login"
USER_LOGIN_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${USER_LOGIN_RESPONSE}" \
    -c "${USER_COOKIE_JAR}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\"
    }"
)"
smoke_assert_status 200 "${USER_LOGIN_STATUS}" "user login" "${USER_LOGIN_RESPONSE}"
USER_LOGIN_TOKEN="$(extract_access_token "${USER_LOGIN_RESPONSE}")"

smoke_print_step "user refresh"
USER_REFRESH_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/refresh" "${USER_REFRESH_RESPONSE}" \
    -b "${USER_COOKIE_JAR}" \
    -c "${USER_COOKIE_JAR}"
)"
smoke_assert_status 200 "${USER_REFRESH_STATUS}" "user refresh" "${USER_REFRESH_RESPONSE}"
USER_REFRESHED_TOKEN="$(extract_access_token "${USER_REFRESH_RESPONSE}")"

smoke_print_step "lookup userKey"
USER_KEY="$(lookup_user_key_by_email "${SMOKE_EMAIL}")"
if [[ -z "${USER_KEY}" ]]; then
  echo "failed to resolve userKey for ${SMOKE_EMAIL}" >&2
  exit 1
fi

smoke_print_step "ensure admin account"
smoke_ensure_admin_account "${APP_BASE_URL}" "${ADMIN_EMAIL}" "${ADMIN_PASSWORD}"

smoke_print_step "admin login (${ADMIN_EMAIL})"
ADMIN_LOGIN_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${ADMIN_LOGIN_RESPONSE}" \
    -c "${ADMIN_COOKIE_JAR}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${ADMIN_EMAIL}\",
      \"password\": \"${ADMIN_PASSWORD}\"
    }"
)"
smoke_assert_status 200 "${ADMIN_LOGIN_STATUS}" "admin login" "${ADMIN_LOGIN_RESPONSE}"
ADMIN_TOKEN="$(extract_access_token "${ADMIN_LOGIN_RESPONSE}")"

smoke_print_step "forced logout userKey=${USER_KEY}"
FORCED_LOGOUT_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/admin/users/forced-logout" "${FORCED_LOGOUT_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d "{\"userKey\":\"${USER_KEY}\"}"
)"
smoke_assert_status 200 "${FORCED_LOGOUT_STATUS}" "forced logout" "${FORCED_LOGOUT_RESPONSE}"

smoke_print_step "old access denied"
OLD_ACCESS_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/users/me/bookmarks" "${OLD_ACCESS_RESPONSE}" \
    -H "Authorization: Bearer ${USER_REFRESHED_TOKEN}"
)"
smoke_assert_status 401 "${OLD_ACCESS_STATUS}" "old access after forced logout" "${OLD_ACCESS_RESPONSE}"
OLD_ACCESS_ERROR="$(extract_error_code "${OLD_ACCESS_RESPONSE}")"
if [[ "${OLD_ACCESS_ERROR}" != "A006" ]]; then
  echo "unexpected old-access errorCode: ${OLD_ACCESS_ERROR}" >&2
  cat "${OLD_ACCESS_RESPONSE}" >&2
  exit 1
fi

smoke_print_step "old refresh denied"
OLD_REFRESH_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/refresh" "${OLD_REFRESH_RESPONSE}" \
    -b "${USER_COOKIE_JAR}" \
    -c "${USER_COOKIE_JAR}"
)"
smoke_assert_status 401 "${OLD_REFRESH_STATUS}" "old refresh after forced logout" "${OLD_REFRESH_RESPONSE}"
OLD_REFRESH_ERROR="$(extract_error_code "${OLD_REFRESH_RESPONSE}")"
if [[ "${OLD_REFRESH_ERROR}" != "A003" ]]; then
  echo "unexpected old-refresh errorCode: ${OLD_REFRESH_ERROR}" >&2
  cat "${OLD_REFRESH_RESPONSE}" >&2
  exit 1
fi

smoke_print_step "relogin after forced logout"
RELOGIN_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${RELOGIN_RESPONSE}" \
    -c "${USER_COOKIE_JAR}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\"
    }"
)"
smoke_assert_status 200 "${RELOGIN_STATUS}" "relogin after forced logout" "${RELOGIN_RESPONSE}"
RELOGIN_TOKEN="$(extract_access_token "${RELOGIN_RESPONSE}")"

smoke_print_step "relogin protected api"
RELOGIN_BOOKMARKS_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/users/me/bookmarks" "${RELOGIN_BOOKMARKS_RESPONSE}" \
    -H "Authorization: Bearer ${RELOGIN_TOKEN}"
)"
smoke_assert_status 200 "${RELOGIN_BOOKMARKS_STATUS}" "relogin protected api" "${RELOGIN_BOOKMARKS_RESPONSE}"

smoke_print_step "forced logout log evidence"
docker logs "${APP_CONTAINER_NAME}" 2>&1 | grep -F "forced logout 트리거 userKey=${USER_KEY}" | tail -n 1 > "${ADMIN_LOG_MATCH_FILE}" || true
if [[ ! -s "${ADMIN_LOG_MATCH_FILE}" ]]; then
  echo "forced logout log line not found for userKey=${USER_KEY}" >&2
  exit 1
fi

echo
echo "admin forced logout smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "admin_email=${ADMIN_EMAIL}"
echo "smoke_email=${SMOKE_EMAIL}"
echo "user_key=${USER_KEY}"
echo "old_access_after_forced_logout=401/${OLD_ACCESS_ERROR}"
echo "old_refresh_after_forced_logout=401/${OLD_REFRESH_ERROR}"
echo "relogin_after_forced_logout=200"
