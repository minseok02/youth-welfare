#!/usr/bin/env bash
set -euo pipefail

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
MYSQL_CONTAINER_NAME="${MYSQL_CONTAINER_NAME:-youth-welfare-db}"

SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-withdraw.smoke}"
SMOKE_NAME="${SMOKE_NAME:-Withdraw Smoke}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"

DB_QUERY_USERNAME="${DB_QUERY_USERNAME:-migration_admin}"
DB_QUERY_PASSWORD="${DB_QUERY_PASSWORD:-welfare1234!}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
COOKIE_JAR="${ARTIFACT_DIR}/user.cookie"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SIGNUP_RESPONSE="${ARTIFACT_DIR}/signup.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/login.json"
REFRESH_RESPONSE="${ARTIFACT_DIR}/refresh.json"
WITHDRAW_RESPONSE="${ARTIFACT_DIR}/withdraw.json"
OLD_ACCESS_RESPONSE="${ARTIFACT_DIR}/old-access.json"
STALE_REFRESH_RESPONSE="${ARTIFACT_DIR}/stale-refresh.json"
USER_STATE_FILE="${ARTIFACT_DIR}/user-state.tsv"

cleanup() {
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing required command: $1" >&2
    exit 1
  }
}

http_status() {
  local method="$1"
  local url="$2"
  local output_file="$3"
  shift 3
  curl -sS -o "${output_file}" -w "%{http_code}" -X "${method}" "$url" "$@"
}

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
  docker exec -e MYSQL_PWD="${DB_QUERY_PASSWORD}" "${MYSQL_CONTAINER_NAME}" \
    mysql --default-character-set=utf8mb4 --batch --skip-column-names \
    -u"${DB_QUERY_USERNAME}" youth_welfare \
    -e "SELECT user_key FROM users WHERE email = '${email}' LIMIT 1;"
}

capture_user_state() {
  local user_key="$1"
  docker exec -e MYSQL_PWD="${DB_QUERY_PASSWORD}" "${MYSQL_CONTAINER_NAME}" \
    mysql --default-character-set=utf8mb4 --batch --skip-column-names \
    -u"${DB_QUERY_USERNAME}" youth_welfare \
    -e "SELECT id, email, is_active FROM users WHERE user_key = '${user_key}' LIMIT 1;"
}

print_step() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

assert_status() {
  local expected="$1"
  local actual="$2"
  local context="$3"
  local file_path="$4"
  if [[ "${expected}" != "${actual}" ]]; then
    echo "${context} failed: expected ${expected}, got ${actual}" >&2
    cat "${file_path}" >&2
    exit 1
  fi
}

require_command curl
require_command python3
require_command docker

SMOKE_EMAIL="${SMOKE_EMAIL_PREFIX}.$(date +%s)@example.com"

print_step "health check"
HEALTH_STATUS="$(http_status GET "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}")"
assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

print_step "signup ${SMOKE_EMAIL}"
SIGNUP_STATUS="$(
  http_status POST "${APP_BASE_URL}/api/auth/signup" "${SIGNUP_RESPONSE}" \
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
assert_status 200 "${SIGNUP_STATUS}" "signup" "${SIGNUP_RESPONSE}"

print_step "login"
LOGIN_STATUS="$(
  http_status POST "${APP_BASE_URL}/api/auth/login" "${LOGIN_RESPONSE}" \
    -c "${COOKIE_JAR}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\"
    }"
)"
assert_status 200 "${LOGIN_STATUS}" "login" "${LOGIN_RESPONSE}"
LOGIN_TOKEN="$(extract_access_token "${LOGIN_RESPONSE}")"

print_step "refresh"
REFRESH_STATUS="$(
  http_status POST "${APP_BASE_URL}/api/auth/refresh" "${REFRESH_RESPONSE}" \
    -b "${COOKIE_JAR}" \
    -c "${COOKIE_JAR}"
)"
assert_status 200 "${REFRESH_STATUS}" "refresh" "${REFRESH_RESPONSE}"
REFRESHED_TOKEN="$(extract_access_token "${REFRESH_RESPONSE}")"

print_step "lookup userKey"
USER_KEY="$(lookup_user_key_by_email "${SMOKE_EMAIL}")"
if [[ -z "${USER_KEY}" ]]; then
  echo "failed to resolve userKey for ${SMOKE_EMAIL}" >&2
  exit 1
fi

print_step "withdraw"
WITHDRAW_STATUS="$(
  http_status DELETE "${APP_BASE_URL}/api/users/me" "${WITHDRAW_RESPONSE}" \
    -b "${COOKIE_JAR}" \
    -c "${COOKIE_JAR}" \
    -H "Authorization: Bearer ${REFRESHED_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d "{\"password\":\"${SMOKE_PASSWORD}\"}"
)"
assert_status 200 "${WITHDRAW_STATUS}" "withdraw" "${WITHDRAW_RESPONSE}"

print_step "old access denied"
OLD_ACCESS_STATUS="$(
  http_status GET "${APP_BASE_URL}/api/users/me/bookmarks" "${OLD_ACCESS_RESPONSE}" \
    -H "Authorization: Bearer ${REFRESHED_TOKEN}"
)"
assert_status 401 "${OLD_ACCESS_STATUS}" "old access after withdraw" "${OLD_ACCESS_RESPONSE}"
OLD_ACCESS_ERROR="$(extract_error_code "${OLD_ACCESS_RESPONSE}")"
if [[ "${OLD_ACCESS_ERROR}" != "A006" ]]; then
  echo "unexpected old-access errorCode: ${OLD_ACCESS_ERROR}" >&2
  cat "${OLD_ACCESS_RESPONSE}" >&2
  exit 1
fi

print_step "stale refresh denied"
STALE_REFRESH_STATUS="$(
  http_status POST "${APP_BASE_URL}/api/auth/refresh" "${STALE_REFRESH_RESPONSE}" \
    -b "${COOKIE_JAR}" \
    -c "${COOKIE_JAR}"
)"
assert_status 410 "${STALE_REFRESH_STATUS}" "stale refresh after withdraw" "${STALE_REFRESH_RESPONSE}"
STALE_REFRESH_ERROR="$(extract_error_code "${STALE_REFRESH_RESPONSE}")"
if [[ "${STALE_REFRESH_ERROR}" != "U003" ]]; then
  echo "unexpected stale-refresh errorCode: ${STALE_REFRESH_ERROR}" >&2
  cat "${STALE_REFRESH_RESPONSE}" >&2
  exit 1
fi

print_step "capture withdrawn user state"
capture_user_state "${USER_KEY}" > "${USER_STATE_FILE}"
if [[ ! -s "${USER_STATE_FILE}" ]]; then
  echo "withdrawn user state missing for userKey=${USER_KEY}" >&2
  exit 1
fi
USER_ROW="$(cat "${USER_STATE_FILE}")"
USER_ID="$(echo "${USER_ROW}" | cut -f1)"
USER_EMAIL_AFTER="$(echo "${USER_ROW}" | cut -f2)"
USER_ACTIVE_AFTER="$(echo "${USER_ROW}" | cut -f3)"
if [[ "${USER_EMAIL_AFTER}" != "withdrawn_${USER_ID}" ]]; then
  echo "unexpected withdrawn email: ${USER_EMAIL_AFTER} (expected withdrawn_${USER_ID})" >&2
  exit 1
fi
if [[ "${USER_ACTIVE_AFTER}" != "0" ]]; then
  echo "unexpected withdrawn active flag: ${USER_ACTIVE_AFTER}" >&2
  exit 1
fi

echo
echo "withdraw smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "smoke_email=${SMOKE_EMAIL}"
echo "user_key=${USER_KEY}"
echo "old_access_after_withdraw=401/${OLD_ACCESS_ERROR}"
echo "stale_refresh_after_withdraw=410/${STALE_REFRESH_ERROR}"
echo "withdrawn_email=${USER_EMAIL_AFTER}"
