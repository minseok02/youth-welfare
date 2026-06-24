#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-runtime.api.smoke}"
SMOKE_NAME="${SMOKE_NAME:-런타임점검}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
SMOKE_NOTIFICATION_YN="${SMOKE_NOTIFICATION_YN:-false}"
SMOKE_NOTIFICATION_MIN_SCORE="${SMOKE_NOTIFICATION_MIN_SCORE:-0.5}"
SMOKE_DISPLAY_COUNT="${SMOKE_DISPLAY_COUNT:-10}"
SMOKE_INTEREST_FIELD="${SMOKE_INTEREST_FIELD:-교육}"
REQUIRE_RECOMMENDATIONS="${REQUIRE_RECOMMENDATIONS:-false}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"
SMOKE_TRUSTED_ORIGIN="${SMOKE_TRUSTED_ORIGIN:-${PLAYWRIGHT_BASE_URL:-http://127.0.0.1:5173}}"
SMOKE_TRUSTED_REFERER="${SMOKE_TRUSTED_REFERER:-${SMOKE_TRUSTED_ORIGIN}/}"
TRUSTED_ORIGIN_HEADERS=(-H "Origin: ${SMOKE_TRUSTED_ORIGIN}" -H "Referer: ${SMOKE_TRUSTED_REFERER}")

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
COOKIE_JAR="${ARTIFACT_DIR}/runtime.cookie"
SIGNUP_RESPONSE="${ARTIFACT_DIR}/signup.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/login.json"
REFRESH_RESPONSE="${ARTIFACT_DIR}/refresh.json"
RECOMMEND_REFRESH_RESPONSE="${ARTIFACT_DIR}/recommend-refresh.json"
BOOKMARK_LIST_RESPONSE="${ARTIFACT_DIR}/bookmarks.json"
LOGOUT_RESPONSE="${ARTIFACT_DIR}/logout.json"
REFRESH_AFTER_LOGOUT_RESPONSE="${ARTIFACT_DIR}/refresh-after-logout.json"
PRESENTED_AFTER_LOGOUT_RESPONSE="${ARTIFACT_DIR}/presented-after-logout.json"
OLDER_TOKEN_AFTER_LOGOUT_RESPONSE="${ARTIFACT_DIR}/older-token-after-logout.json"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"

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

extract_first_recommendation_id() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

items = payload.get("data") or []
print(items[0]["id"] if items else "")
PY
}

extract_recommendation_count() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

print(len(payload.get("data") or []))
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

smoke_print_step "signup ${SMOKE_EMAIL}"
smoke_seed_verified_email "${SMOKE_EMAIL}"
SIGNUP_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/signup" "${SIGNUP_RESPONSE}" \
    "${TRUSTED_ORIGIN_HEADERS[@]}" \
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
    "${TRUSTED_ORIGIN_HEADERS[@]}" \
    -c "${COOKIE_JAR}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\"
    }"
)"
smoke_assert_status 200 "${LOGIN_STATUS}" "login" "${LOGIN_RESPONSE}"
LOGIN_TOKEN="$(extract_access_token "${LOGIN_RESPONSE}")"

smoke_print_step "refresh"
REFRESH_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/refresh" "${REFRESH_RESPONSE}" \
    "${TRUSTED_ORIGIN_HEADERS[@]}" \
    -b "${COOKIE_JAR}" \
    -c "${COOKIE_JAR}"
)"
smoke_assert_status 200 "${REFRESH_STATUS}" "refresh" "${REFRESH_RESPONSE}"
REFRESHED_TOKEN="$(extract_access_token "${REFRESH_RESPONSE}")"

smoke_print_step "recommendations refresh"
RECOMMEND_REFRESH_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/recommendations/refresh" "${RECOMMEND_REFRESH_RESPONSE}" \
    "${TRUSTED_ORIGIN_HEADERS[@]}" \
    -H "Authorization: Bearer ${REFRESHED_TOKEN}"
)"
smoke_assert_status 200 "${RECOMMEND_REFRESH_STATUS}" "recommendations refresh" "${RECOMMEND_REFRESH_RESPONSE}"
RECOMMENDATION_COUNT="$(extract_recommendation_count "${RECOMMEND_REFRESH_RESPONSE}")"
RECOMMENDATION_ID="$(extract_first_recommendation_id "${RECOMMEND_REFRESH_RESPONSE}")"

if [[ -z "${RECOMMENDATION_ID}" ]]; then
  if [[ "${REQUIRE_RECOMMENDATIONS}" == "true" ]]; then
    echo "recommendations refresh returned no items" >&2
    cat "${RECOMMEND_REFRESH_RESPONSE}" >&2
    exit 1
  fi
  smoke_print_step "recommendation list empty; skipping bookmark flow"
else
  smoke_print_step "toggle bookmark recommendationId=${RECOMMENDATION_ID}"
  BOOKMARK_STATUS="$(
    smoke_http_status POST "${APP_BASE_URL}/api/recommendations/${RECOMMENDATION_ID}/bookmark" /dev/null \
      "${TRUSTED_ORIGIN_HEADERS[@]}" \
      -H "Authorization: Bearer ${REFRESHED_TOKEN}"
  )"
  if [[ "${BOOKMARK_STATUS}" != "200" ]]; then
    echo "bookmark toggle failed: ${BOOKMARK_STATUS}" >&2
    exit 1
  fi

  smoke_print_step "bookmarks list"
  BOOKMARK_LIST_STATUS="$(
    smoke_http_status GET "${APP_BASE_URL}/api/users/me/bookmarks" "${BOOKMARK_LIST_RESPONSE}" \
      -H "Authorization: Bearer ${REFRESHED_TOKEN}"
  )"
  smoke_assert_status 200 "${BOOKMARK_LIST_STATUS}" "bookmarks list" "${BOOKMARK_LIST_RESPONSE}"
fi

smoke_print_step "logout"
LOGOUT_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/logout" "${LOGOUT_RESPONSE}" \
    "${TRUSTED_ORIGIN_HEADERS[@]}" \
    -b "${COOKIE_JAR}" \
    -c "${COOKIE_JAR}" \
    -H "Authorization: Bearer ${REFRESHED_TOKEN}"
)"
smoke_assert_status 200 "${LOGOUT_STATUS}" "logout" "${LOGOUT_RESPONSE}"

smoke_print_step "refresh after logout"
REFRESH_AFTER_LOGOUT_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/refresh" "${REFRESH_AFTER_LOGOUT_RESPONSE}" \
    "${TRUSTED_ORIGIN_HEADERS[@]}" \
    -b "${COOKIE_JAR}" \
    -c "${COOKIE_JAR}"
)"
smoke_assert_status 401 "${REFRESH_AFTER_LOGOUT_STATUS}" "refresh after logout" "${REFRESH_AFTER_LOGOUT_RESPONSE}"
REFRESH_AFTER_LOGOUT_ERROR="$(extract_error_code "${REFRESH_AFTER_LOGOUT_RESPONSE}")"
if [[ "${REFRESH_AFTER_LOGOUT_ERROR}" != "A001" ]]; then
  echo "unexpected refresh-after-logout errorCode: ${REFRESH_AFTER_LOGOUT_ERROR}" >&2
  cat "${REFRESH_AFTER_LOGOUT_RESPONSE}" >&2
  exit 1
fi

smoke_print_step "presented token revoke after logout"
PRESENTED_AFTER_LOGOUT_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/users/me/bookmarks" "${PRESENTED_AFTER_LOGOUT_RESPONSE}" \
    -H "Authorization: Bearer ${REFRESHED_TOKEN}"
)"
smoke_assert_status 401 "${PRESENTED_AFTER_LOGOUT_STATUS}" "presented token revoke after logout" "${PRESENTED_AFTER_LOGOUT_RESPONSE}"
PRESENTED_AFTER_LOGOUT_ERROR="$(extract_error_code "${PRESENTED_AFTER_LOGOUT_RESPONSE}")"
if [[ "${PRESENTED_AFTER_LOGOUT_ERROR}" != "A006" ]]; then
  echo "unexpected presented-token-after-logout errorCode: ${PRESENTED_AFTER_LOGOUT_ERROR}" >&2
  cat "${PRESENTED_AFTER_LOGOUT_RESPONSE}" >&2
  exit 1
fi

smoke_print_step "older login token after logout"
OLDER_AFTER_LOGOUT_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/users/me/bookmarks" "${OLDER_TOKEN_AFTER_LOGOUT_RESPONSE}" \
    -H "Authorization: Bearer ${LOGIN_TOKEN}"
)"
smoke_assert_status 401 "${OLDER_AFTER_LOGOUT_STATUS}" "older login token after logout" "${OLDER_TOKEN_AFTER_LOGOUT_RESPONSE}"
OLDER_AFTER_LOGOUT_ERROR="$(extract_error_code "${OLDER_TOKEN_AFTER_LOGOUT_RESPONSE}")"
if [[ "${OLDER_AFTER_LOGOUT_ERROR}" != "A006" ]]; then
  echo "unexpected older-token-after-logout errorCode: ${OLDER_AFTER_LOGOUT_ERROR}" >&2
  cat "${OLDER_TOKEN_AFTER_LOGOUT_RESPONSE}" >&2
  exit 1
fi

echo
echo "runtime api smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "smoke_trusted_origin=${SMOKE_TRUSTED_ORIGIN}"
echo "smoke_email=${SMOKE_EMAIL}"
echo "recommendation_count=${RECOMMENDATION_COUNT}"
echo "presented_after_logout=401/${PRESENTED_AFTER_LOGOUT_ERROR}"
echo "older_login_token_after_logout=401/${OLDER_AFTER_LOGOUT_ERROR}"
