#!/usr/bin/env bash
set -euo pipefail

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-runtime.api.smoke}"
SMOKE_NAME="${SMOKE_NAME:-Runtime API Smoke}"
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

print_step "recommendations refresh"
RECOMMEND_REFRESH_STATUS="$(
  http_status POST "${APP_BASE_URL}/api/recommendations/refresh" "${RECOMMEND_REFRESH_RESPONSE}" \
    -H "Authorization: Bearer ${REFRESHED_TOKEN}"
)"
assert_status 200 "${RECOMMEND_REFRESH_STATUS}" "recommendations refresh" "${RECOMMEND_REFRESH_RESPONSE}"
RECOMMENDATION_COUNT="$(extract_recommendation_count "${RECOMMEND_REFRESH_RESPONSE}")"
RECOMMENDATION_ID="$(extract_first_recommendation_id "${RECOMMEND_REFRESH_RESPONSE}")"

if [[ -z "${RECOMMENDATION_ID}" ]]; then
  if [[ "${REQUIRE_RECOMMENDATIONS}" == "true" ]]; then
    echo "recommendations refresh returned no items" >&2
    cat "${RECOMMEND_REFRESH_RESPONSE}" >&2
    exit 1
  fi
  print_step "recommendation list empty; skipping bookmark flow"
else
  print_step "toggle bookmark recommendationId=${RECOMMENDATION_ID}"
  BOOKMARK_STATUS="$(
    http_status POST "${APP_BASE_URL}/api/recommendations/${RECOMMENDATION_ID}/bookmark" /dev/null \
      -H "Authorization: Bearer ${REFRESHED_TOKEN}"
  )"
  if [[ "${BOOKMARK_STATUS}" != "200" ]]; then
    echo "bookmark toggle failed: ${BOOKMARK_STATUS}" >&2
    exit 1
  fi

  print_step "bookmarks list"
  BOOKMARK_LIST_STATUS="$(
    http_status GET "${APP_BASE_URL}/api/users/me/bookmarks" "${BOOKMARK_LIST_RESPONSE}" \
      -H "Authorization: Bearer ${REFRESHED_TOKEN}"
  )"
  assert_status 200 "${BOOKMARK_LIST_STATUS}" "bookmarks list" "${BOOKMARK_LIST_RESPONSE}"
fi

print_step "logout"
LOGOUT_STATUS="$(
  http_status POST "${APP_BASE_URL}/api/auth/logout" "${LOGOUT_RESPONSE}" \
    -b "${COOKIE_JAR}" \
    -c "${COOKIE_JAR}" \
    -H "Authorization: Bearer ${REFRESHED_TOKEN}"
)"
assert_status 200 "${LOGOUT_STATUS}" "logout" "${LOGOUT_RESPONSE}"

print_step "refresh after logout"
REFRESH_AFTER_LOGOUT_STATUS="$(
  http_status POST "${APP_BASE_URL}/api/auth/refresh" "${REFRESH_AFTER_LOGOUT_RESPONSE}" \
    -b "${COOKIE_JAR}" \
    -c "${COOKIE_JAR}"
)"
assert_status 401 "${REFRESH_AFTER_LOGOUT_STATUS}" "refresh after logout" "${REFRESH_AFTER_LOGOUT_RESPONSE}"
REFRESH_AFTER_LOGOUT_ERROR="$(extract_error_code "${REFRESH_AFTER_LOGOUT_RESPONSE}")"
if [[ "${REFRESH_AFTER_LOGOUT_ERROR}" != "A001" ]]; then
  echo "unexpected refresh-after-logout errorCode: ${REFRESH_AFTER_LOGOUT_ERROR}" >&2
  cat "${REFRESH_AFTER_LOGOUT_RESPONSE}" >&2
  exit 1
fi

print_step "presented token revoke after logout"
PRESENTED_AFTER_LOGOUT_STATUS="$(
  http_status GET "${APP_BASE_URL}/api/users/me/bookmarks" "${PRESENTED_AFTER_LOGOUT_RESPONSE}" \
    -H "Authorization: Bearer ${REFRESHED_TOKEN}"
)"
assert_status 401 "${PRESENTED_AFTER_LOGOUT_STATUS}" "presented token revoke after logout" "${PRESENTED_AFTER_LOGOUT_RESPONSE}"
PRESENTED_AFTER_LOGOUT_ERROR="$(extract_error_code "${PRESENTED_AFTER_LOGOUT_RESPONSE}")"
if [[ "${PRESENTED_AFTER_LOGOUT_ERROR}" != "A006" ]]; then
  echo "unexpected presented-token-after-logout errorCode: ${PRESENTED_AFTER_LOGOUT_ERROR}" >&2
  cat "${PRESENTED_AFTER_LOGOUT_RESPONSE}" >&2
  exit 1
fi

print_step "older login token after logout"
OLDER_AFTER_LOGOUT_STATUS="$(
  http_status GET "${APP_BASE_URL}/api/users/me/bookmarks" "${OLDER_TOKEN_AFTER_LOGOUT_RESPONSE}" \
    -H "Authorization: Bearer ${LOGIN_TOKEN}"
)"
if [[ "${OLDER_AFTER_LOGOUT_STATUS}" != "200" && "${OLDER_AFTER_LOGOUT_STATUS}" != "401" ]]; then
  echo "unexpected older-token-after-logout status: ${OLDER_AFTER_LOGOUT_STATUS}" >&2
  cat "${OLDER_TOKEN_AFTER_LOGOUT_RESPONSE}" >&2
  exit 1
fi

echo
echo "runtime api smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "smoke_email=${SMOKE_EMAIL}"
echo "recommendation_count=${RECOMMENDATION_COUNT}"
echo "presented_after_logout=401/${PRESENTED_AFTER_LOGOUT_ERROR}"
echo "older_login_token_after_logout=${OLDER_AFTER_LOGOUT_STATUS}"
