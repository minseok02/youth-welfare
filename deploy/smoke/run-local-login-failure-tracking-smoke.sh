#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_WRONG_PASSWORD="${SMOKE_WRONG_PASSWORD:-wrong-password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-login.failure.smoke}"
SMOKE_NAME="${SMOKE_NAME:-로그인실패점검}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
COOKIE_JAR="${ARTIFACT_DIR}/login-failure.cookie"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SIGNUP_RESPONSE="${ARTIFACT_DIR}/signup.json"
WRONG_LOGIN_ONE_RESPONSE="${ARTIFACT_DIR}/wrong-login-1.json"
WRONG_LOGIN_TWO_RESPONSE="${ARTIFACT_DIR}/wrong-login-2.json"
SUCCESS_LOGIN_RESPONSE="${ARTIFACT_DIR}/success-login.json"
LOGOUT_RESPONSE="${ARTIFACT_DIR}/logout.json"

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

query_login_failure_state() {
  local email="$1"
  smoke_db_query "
    SELECT
      u.user_key,
      u.login_fail_count,
      au.login_fail_count,
      COALESCE(u.locked_until::text, ''),
      COALESCE(au.locked_until::text, '')
    FROM users u
    JOIN auth_users au ON au.user_key = u.user_key
    WHERE u.email = '${email}'
    LIMIT 1;
  "
}

assert_login_failure_state() {
  local email="$1"
  local expected_legacy_count="$2"
  local expected_auth_count="$3"
  local expected_legacy_locked="$4"
  local expected_auth_locked="$5"
  local state
  local user_key legacy_count auth_count legacy_locked auth_locked

  state="$(query_login_failure_state "${email}")"
  if [[ -z "${state}" ]]; then
    echo "failed to resolve login failure state for ${email}" >&2
    exit 1
  fi

  IFS=$'\t' read -r user_key legacy_count auth_count legacy_locked auth_locked <<< "${state}"

  if [[ "${legacy_count}" != "${expected_legacy_count}" ]]; then
    echo "unexpected users.login_fail_count for ${email}: expected ${expected_legacy_count}, got ${legacy_count}" >&2
    exit 1
  fi

  if [[ "${auth_count}" != "${expected_auth_count}" ]]; then
    echo "unexpected auth_users.login_fail_count for ${email}: expected ${expected_auth_count}, got ${auth_count}" >&2
    exit 1
  fi

  if [[ "${expected_legacy_locked}" == "empty" && -n "${legacy_locked}" ]]; then
    echo "expected users.locked_until to be empty for ${email}, got ${legacy_locked}" >&2
    exit 1
  fi

  if [[ "${expected_auth_locked}" == "empty" && -n "${auth_locked}" ]]; then
    echo "expected auth_users.locked_until to be empty for ${email}, got ${auth_locked}" >&2
    exit 1
  fi
}

smoke_require_command curl
smoke_require_command python3

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

smoke_print_step "wrong password login 1"
WRONG_LOGIN_ONE_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${WRONG_LOGIN_ONE_RESPONSE}" \
    -c "${COOKIE_JAR}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_WRONG_PASSWORD}\"
    }"
)"
smoke_assert_status 401 "${WRONG_LOGIN_ONE_STATUS}" "wrong password login 1" "${WRONG_LOGIN_ONE_RESPONSE}"
WRONG_LOGIN_ONE_ERROR="$(extract_error_code "${WRONG_LOGIN_ONE_RESPONSE}")"
if [[ "${WRONG_LOGIN_ONE_ERROR}" != "A004" ]]; then
  echo "unexpected wrong-login-1 errorCode: ${WRONG_LOGIN_ONE_ERROR}" >&2
  cat "${WRONG_LOGIN_ONE_RESPONSE}" >&2
  exit 1
fi

smoke_print_step "wrong password login 2"
WRONG_LOGIN_TWO_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${WRONG_LOGIN_TWO_RESPONSE}" \
    -c "${COOKIE_JAR}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_WRONG_PASSWORD}\"
    }"
)"
smoke_assert_status 401 "${WRONG_LOGIN_TWO_STATUS}" "wrong password login 2" "${WRONG_LOGIN_TWO_RESPONSE}"
WRONG_LOGIN_TWO_ERROR="$(extract_error_code "${WRONG_LOGIN_TWO_RESPONSE}")"
if [[ "${WRONG_LOGIN_TWO_ERROR}" != "A004" ]]; then
  echo "unexpected wrong-login-2 errorCode: ${WRONG_LOGIN_TWO_ERROR}" >&2
  cat "${WRONG_LOGIN_TWO_RESPONSE}" >&2
  exit 1
fi

smoke_print_step "verify failure count persistence"
assert_login_failure_state "${SMOKE_EMAIL}" "2" "2" "empty" "empty"

smoke_print_step "successful login resets failure count"
SUCCESS_LOGIN_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${SUCCESS_LOGIN_RESPONSE}" \
    -c "${COOKIE_JAR}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\"
    }"
)"
smoke_assert_status 200 "${SUCCESS_LOGIN_STATUS}" "successful login after failures" "${SUCCESS_LOGIN_RESPONSE}"
SUCCESS_LOGIN_TOKEN="$(extract_access_token "${SUCCESS_LOGIN_RESPONSE}")"

smoke_print_step "verify failure count reset"
assert_login_failure_state "${SMOKE_EMAIL}" "0" "0" "empty" "empty"

smoke_print_step "logout"
LOGOUT_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/logout" "${LOGOUT_RESPONSE}" \
    -b "${COOKIE_JAR}" \
    -c "${COOKIE_JAR}" \
    -H "Authorization: Bearer ${SUCCESS_LOGIN_TOKEN}"
)"
smoke_assert_status 200 "${LOGOUT_STATUS}" "logout" "${LOGOUT_RESPONSE}"

echo
echo "login failure tracking smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "smoke_email=${SMOKE_EMAIL}"
echo "wrong_login_error_1=401/${WRONG_LOGIN_ONE_ERROR}"
echo "wrong_login_error_2=401/${WRONG_LOGIN_TWO_ERROR}"
echo "failure_count_after_wrong_login=users:2,auth_users:2"
echo "failure_count_after_success_login=users:0,auth_users:0"
