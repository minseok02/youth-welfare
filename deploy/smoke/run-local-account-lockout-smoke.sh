#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_WRONG_PASSWORD="${SMOKE_WRONG_PASSWORD:-wrong-password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-account.lockout.smoke}"
SMOKE_NAME="${SMOKE_NAME:-계정잠금점검}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
LOCK_THRESHOLD="${LOCK_THRESHOLD:-5}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SIGNUP_RESPONSE="${ARTIFACT_DIR}/signup.json"
LOCKED_LOGIN_RESPONSE="${ARTIFACT_DIR}/locked-login.json"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

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

query_lockout_state() {
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

assert_lockout_state() {
  local email="$1"
  local expected_count="$2"
  local state
  local user_key legacy_count auth_count legacy_locked auth_locked

  state="$(query_lockout_state "${email}")"
  if [[ -z "${state}" ]]; then
    echo "failed to resolve lockout state for ${email}" >&2
    exit 1
  fi

  IFS=$'\t' read -r user_key legacy_count auth_count legacy_locked auth_locked <<< "${state}"

  if [[ "${legacy_count}" != "${expected_count}" ]]; then
    echo "unexpected users.login_fail_count for ${email}: expected ${expected_count}, got ${legacy_count}" >&2
    exit 1
  fi

  if [[ "${auth_count}" != "${expected_count}" ]]; then
    echo "unexpected auth_users.login_fail_count for ${email}: expected ${expected_count}, got ${auth_count}" >&2
    exit 1
  fi

  if [[ -z "${legacy_locked}" ]]; then
    echo "expected users.locked_until to be populated for ${email}" >&2
    exit 1
  fi

  if [[ -z "${auth_locked}" ]]; then
    echo "expected auth_users.locked_until to be populated for ${email}" >&2
    exit 1
  fi
}

smoke_require_command curl
smoke_require_command python3
KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
mkdir -p "${ARTIFACT_DIR}"

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
      \"privacyNoticeConfirmed\": true,
      \"optionalProfileConsentAgreed\": true,
      \"sido\": \"${SMOKE_SIDO}\",
      \"sgg\": \"${SMOKE_SGG}\",
      \"incomeLevel\": ${SMOKE_INCOME_LEVEL},
      \"employmentStatus\": \"${SMOKE_EMPLOYMENT_STATUS}\",
      \"householdType\": \"${SMOKE_HOUSEHOLD_TYPE}\"
    }"
)"
smoke_assert_status 200 "${SIGNUP_STATUS}" "signup" "${SIGNUP_RESPONSE}"

for attempt in $(seq 1 "${LOCK_THRESHOLD}"); do
  attempt_response="${ARTIFACT_DIR}/wrong-login-${attempt}.json"

  smoke_print_step "wrong password login ${attempt}/${LOCK_THRESHOLD}"
  attempt_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${attempt_response}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"email\": \"${SMOKE_EMAIL}\",
        \"password\": \"${SMOKE_WRONG_PASSWORD}\"
      }"
  )"
  smoke_assert_status 401 "${attempt_status}" "wrong password login ${attempt}" "${attempt_response}"
  attempt_error="$(extract_error_code "${attempt_response}")"
  if [[ "${attempt_error}" != "A004" ]]; then
    echo "unexpected wrong-login-${attempt} errorCode: ${attempt_error}" >&2
    cat "${attempt_response}" >&2
    exit 1
  fi
done

smoke_print_step "verify locked state at threshold"
assert_lockout_state "${SMOKE_EMAIL}" "${LOCK_THRESHOLD}"

smoke_print_step "locked account denies correct password login"
LOCKED_LOGIN_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${LOCKED_LOGIN_RESPONSE}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\"
    }"
)"
smoke_assert_status 401 "${LOCKED_LOGIN_STATUS}" "locked account login" "${LOCKED_LOGIN_RESPONSE}"
LOCKED_LOGIN_ERROR="$(extract_error_code "${LOCKED_LOGIN_RESPONSE}")"
if [[ "${LOCKED_LOGIN_ERROR}" != "A005" ]]; then
  echo "unexpected locked-login errorCode: ${LOCKED_LOGIN_ERROR}" >&2
  cat "${LOCKED_LOGIN_RESPONSE}" >&2
  exit 1
fi

echo
echo "account lockout smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "smoke_email=${SMOKE_EMAIL}"
echo "lock_threshold=${LOCK_THRESHOLD}"
echo "threshold_attempt_error=401/A004"
echo "post_lock_correct_password_error=401/${LOCKED_LOGIN_ERROR}"
echo "locked_state=users:${LOCK_THRESHOLD},auth_users:${LOCK_THRESHOLD},locked_until:set"
