#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-similar.users.viewed.smoke}"
SMOKE_EMAIL="${SMOKE_EMAIL:-}"
SMOKE_NAME="${SMOKE_NAME:-유사조회점검}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
SIMILAR_USERS_VIEWED_SIZE="${SIMILAR_USERS_VIEWED_SIZE:-4}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
COOKIE_JAR="${ARTIFACT_DIR}/similar-users-viewed.cookie"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SIGNUP_RESPONSE="${ARTIFACT_DIR}/signup.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/login.json"
SIMILAR_USERS_VIEWED_RESPONSE="${ARTIFACT_DIR}/similar-users-viewed.json"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ "${KEEP_ARTIFACTS:-false}" != "true" ]]; then
    rm -rf "${ARTIFACT_DIR}"
  fi
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

assert_similar_users_viewed_contract() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

if payload.get("success") is not True:
    raise SystemExit("success must be true")

rows = payload.get("data")
if not isinstance(rows, list):
    raise SystemExit("data must be a list")

for index, row in enumerate(rows):
    if not isinstance(row, dict):
        raise SystemExit(f"row {index} must be an object")
    policy = row.get("policy")
    if not isinstance(policy, dict):
        raise SystemExit(f"row {index}.policy must be an object")
    if not policy.get("id"):
        raise SystemExit(f"row {index}.policy.id is required")
    if not policy.get("title"):
        raise SystemExit(f"row {index}.policy.title is required")
    if not row.get("reasonLabel"):
        raise SystemExit(f"row {index}.reasonLabel is required")

print(len(rows))
PY
}

smoke_require_command curl
smoke_require_command python3
smoke_require_command docker

SMOKE_EMAIL="${SMOKE_EMAIL:-$(smoke_build_email "${SMOKE_EMAIL_PREFIX}")}"

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

smoke_print_step "login"
LOGIN_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${LOGIN_RESPONSE}" \
    -c "${COOKIE_JAR}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\"
    }"
)"
smoke_assert_status 200 "${LOGIN_STATUS}" "login" "${LOGIN_RESPONSE}"
ACCESS_TOKEN="$(extract_access_token "${LOGIN_RESPONSE}")"

smoke_print_step "similar users viewed recommendations"
SIMILAR_USERS_VIEWED_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/recommendations/similar-users-viewed?size=${SIMILAR_USERS_VIEWED_SIZE}" "${SIMILAR_USERS_VIEWED_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${SIMILAR_USERS_VIEWED_STATUS}" "similar users viewed recommendations" "${SIMILAR_USERS_VIEWED_RESPONSE}"
RESULT_COUNT="$(assert_similar_users_viewed_contract "${SIMILAR_USERS_VIEWED_RESPONSE}")"

echo "similar_users_viewed_smoke=passed"
echo "smoke_email=${SMOKE_EMAIL}"
echo "result_count=${RESULT_COUNT}"
if [[ "${KEEP_ARTIFACTS:-false}" == "true" ]]; then
  echo "artifact_dir=${ARTIFACT_DIR}"
fi
