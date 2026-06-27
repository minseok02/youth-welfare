#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-admin.authz.smoke}"
SMOKE_NAME="${SMOKE_NAME:-관리자권한점검}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
COOKIE_JAR="${ARTIFACT_DIR}/authz.cookie"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SIGNUP_RESPONSE="${ARTIFACT_DIR}/signup.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/login.json"
NOAUTH_RESPONSE="${ARTIFACT_DIR}/admin-noauth.json"
NONADMIN_RESPONSE="${ARTIFACT_DIR}/admin-nonadmin.json"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
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

smoke_require_command curl
smoke_require_command python3

SMOKE_EMAIL="$(smoke_build_email "${SMOKE_EMAIL_PREFIX}")"

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

smoke_print_step "admin summary no-auth"
NOAUTH_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/summary" "${NOAUTH_RESPONSE}"
)"
smoke_assert_status 401 "${NOAUTH_STATUS}" "admin summary no-auth" "${NOAUTH_RESPONSE}"
if [[ "$(extract_error_code "${NOAUTH_RESPONSE}")" != "A006" ]]; then
  echo "unexpected no-auth admin errorCode" >&2
  cat "${NOAUTH_RESPONSE}" >&2
  exit 1
fi

smoke_print_step "signup non-admin ${SMOKE_EMAIL}"
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
smoke_assert_status 200 "${SIGNUP_STATUS}" "signup non-admin" "${SIGNUP_RESPONSE}"

smoke_print_step "login non-admin"
LOGIN_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${LOGIN_RESPONSE}" \
    -c "${COOKIE_JAR}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\"
    }"
)"
smoke_assert_status 200 "${LOGIN_STATUS}" "login non-admin" "${LOGIN_RESPONSE}"
NONADMIN_TOKEN="$(extract_access_token "${LOGIN_RESPONSE}")"

smoke_print_step "admin summary non-admin"
NONADMIN_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/summary" "${NONADMIN_RESPONSE}" \
    -H "Authorization: Bearer ${NONADMIN_TOKEN}"
)"
smoke_assert_status 403 "${NONADMIN_STATUS}" "admin summary non-admin" "${NONADMIN_RESPONSE}"
if [[ "$(extract_error_code "${NONADMIN_RESPONSE}")" != "C003" ]]; then
  echo "unexpected non-admin admin errorCode" >&2
  cat "${NONADMIN_RESPONSE}" >&2
  exit 1
fi

echo "artifact_dir=${ARTIFACT_DIR}"
echo "non_admin_email=${SMOKE_EMAIL}"
echo "no_auth_admin_status=${NOAUTH_STATUS}"
echo "non_admin_admin_status=${NONADMIN_STATUS}"
echo "admin_authorization_smoke=PASS"
