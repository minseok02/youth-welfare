#!/usr/bin/env bash
set -euo pipefail

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
APP_CONTAINER_NAME="${APP_CONTAINER_NAME:-youth-welfare-app}"
MYSQL_CONTAINER_NAME="${MYSQL_CONTAINER_NAME:-youth-welfare-db}"

ADMIN_EMAIL="${ADMIN_EMAIL:-admin@example.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-password123!}"

SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-forced.logout.smoke}"
SMOKE_NAME="${SMOKE_NAME:-Forced Logout Smoke}"
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

wait_for_health() {
  local retries="$1"
  local delay_seconds="$2"
  local status=""
  local attempt=1

  while (( attempt <= retries )); do
    if status="$(http_status GET "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" 2>"${ARTIFACT_DIR}/health.stderr")"; then
      if [[ "${status}" == "200" ]]; then
        printf '%s' "${status}"
        return 0
      fi
    fi

    if (( attempt == retries )); then
      echo "health check failed after ${retries} attempts" >&2
      if [[ -s "${ARTIFACT_DIR}/health.stderr" ]]; then
        cat "${ARTIFACT_DIR}/health.stderr" >&2
      fi
      if [[ -f "${HEALTH_RESPONSE}" ]]; then
        cat "${HEALTH_RESPONSE}" >&2
      fi
      return 1
    fi

    sleep "${delay_seconds}"
    attempt=$((attempt + 1))
  done
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
HEALTH_STATUS="$(wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}")"
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

print_step "user login"
USER_LOGIN_STATUS="$(
  http_status POST "${APP_BASE_URL}/api/auth/login" "${USER_LOGIN_RESPONSE}" \
    -c "${USER_COOKIE_JAR}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\"
    }"
)"
assert_status 200 "${USER_LOGIN_STATUS}" "user login" "${USER_LOGIN_RESPONSE}"
USER_LOGIN_TOKEN="$(extract_access_token "${USER_LOGIN_RESPONSE}")"

print_step "user refresh"
USER_REFRESH_STATUS="$(
  http_status POST "${APP_BASE_URL}/api/auth/refresh" "${USER_REFRESH_RESPONSE}" \
    -b "${USER_COOKIE_JAR}" \
    -c "${USER_COOKIE_JAR}"
)"
assert_status 200 "${USER_REFRESH_STATUS}" "user refresh" "${USER_REFRESH_RESPONSE}"
USER_REFRESHED_TOKEN="$(extract_access_token "${USER_REFRESH_RESPONSE}")"

print_step "lookup userKey"
USER_KEY="$(lookup_user_key_by_email "${SMOKE_EMAIL}")"
if [[ -z "${USER_KEY}" ]]; then
  echo "failed to resolve userKey for ${SMOKE_EMAIL}" >&2
  exit 1
fi

print_step "admin login (${ADMIN_EMAIL})"
ADMIN_LOGIN_STATUS="$(
  http_status POST "${APP_BASE_URL}/api/auth/login" "${ADMIN_LOGIN_RESPONSE}" \
    -c "${ADMIN_COOKIE_JAR}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${ADMIN_EMAIL}\",
      \"password\": \"${ADMIN_PASSWORD}\"
    }"
)"
assert_status 200 "${ADMIN_LOGIN_STATUS}" "admin login" "${ADMIN_LOGIN_RESPONSE}"
ADMIN_TOKEN="$(extract_access_token "${ADMIN_LOGIN_RESPONSE}")"

print_step "forced logout userKey=${USER_KEY}"
FORCED_LOGOUT_STATUS="$(
  http_status POST "${APP_BASE_URL}/api/admin/users/forced-logout" "${FORCED_LOGOUT_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d "{\"userKey\":\"${USER_KEY}\"}"
)"
assert_status 200 "${FORCED_LOGOUT_STATUS}" "forced logout" "${FORCED_LOGOUT_RESPONSE}"

print_step "old access denied"
OLD_ACCESS_STATUS="$(
  http_status GET "${APP_BASE_URL}/api/users/me/bookmarks" "${OLD_ACCESS_RESPONSE}" \
    -H "Authorization: Bearer ${USER_REFRESHED_TOKEN}"
)"
assert_status 401 "${OLD_ACCESS_STATUS}" "old access after forced logout" "${OLD_ACCESS_RESPONSE}"
OLD_ACCESS_ERROR="$(extract_error_code "${OLD_ACCESS_RESPONSE}")"
if [[ "${OLD_ACCESS_ERROR}" != "A006" ]]; then
  echo "unexpected old-access errorCode: ${OLD_ACCESS_ERROR}" >&2
  cat "${OLD_ACCESS_RESPONSE}" >&2
  exit 1
fi

print_step "old refresh denied"
OLD_REFRESH_STATUS="$(
  http_status POST "${APP_BASE_URL}/api/auth/refresh" "${OLD_REFRESH_RESPONSE}" \
    -b "${USER_COOKIE_JAR}" \
    -c "${USER_COOKIE_JAR}"
)"
assert_status 401 "${OLD_REFRESH_STATUS}" "old refresh after forced logout" "${OLD_REFRESH_RESPONSE}"
OLD_REFRESH_ERROR="$(extract_error_code "${OLD_REFRESH_RESPONSE}")"
if [[ "${OLD_REFRESH_ERROR}" != "A003" ]]; then
  echo "unexpected old-refresh errorCode: ${OLD_REFRESH_ERROR}" >&2
  cat "${OLD_REFRESH_RESPONSE}" >&2
  exit 1
fi

print_step "relogin after forced logout"
RELOGIN_STATUS="$(
  http_status POST "${APP_BASE_URL}/api/auth/login" "${RELOGIN_RESPONSE}" \
    -c "${USER_COOKIE_JAR}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\"
    }"
)"
assert_status 200 "${RELOGIN_STATUS}" "relogin after forced logout" "${RELOGIN_RESPONSE}"
RELOGIN_TOKEN="$(extract_access_token "${RELOGIN_RESPONSE}")"

print_step "relogin protected api"
RELOGIN_BOOKMARKS_STATUS="$(
  http_status GET "${APP_BASE_URL}/api/users/me/bookmarks" "${RELOGIN_BOOKMARKS_RESPONSE}" \
    -H "Authorization: Bearer ${RELOGIN_TOKEN}"
)"
assert_status 200 "${RELOGIN_BOOKMARKS_STATUS}" "relogin protected api" "${RELOGIN_BOOKMARKS_RESPONSE}"

print_step "forced logout log evidence"
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
