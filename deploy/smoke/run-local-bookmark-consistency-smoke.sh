#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-bookmark.consistency.smoke}"
SMOKE_NAME="${SMOKE_NAME:-북마크점검}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
COOKIE_JAR="${ARTIFACT_DIR}/user.cookie"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SIGNUP_RESPONSE="${ARTIFACT_DIR}/signup.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/login.json"
PRIORITIES_RESPONSE="${ARTIFACT_DIR}/priorities.json"
RECOMMEND_REFRESH_RESPONSE="${ARTIFACT_DIR}/recommend-refresh.json"
RECOMMEND_LIST_RESPONSE="${ARTIFACT_DIR}/recommend-list.json"
TOGGLE_ON_RESPONSE="${ARTIFACT_DIR}/toggle-on.json"
TOGGLE_OFF_RESPONSE="${ARTIFACT_DIR}/toggle-off.json"
POLICY_SEARCH_RESPONSE="${ARTIFACT_DIR}/policy-search.json"
POLICY_DETAIL_RESPONSE="${ARTIFACT_DIR}/policy-detail.json"
BOOKMARKS_RESPONSE="${ARTIFACT_DIR}/bookmarks.json"

cleanup() {
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

extract_json() {
  local response_file="$1"
  local expression="$2"
  python3 - "$response_file" "$expression" <<'PY'
import json
import sys

response_file, expression = sys.argv[1], sys.argv[2]
with open(response_file, "r", encoding="utf-8") as fp:
    payload = json.load(fp)

value = eval(expression, {"__builtins__": {}, "len": len, "next": next, "sum": sum, "str": str}, {"payload": payload})
if value is None:
    print("")
elif isinstance(value, (dict, list)):
    print(json.dumps(value, ensure_ascii=False))
else:
    print(value)
PY
}

assert_policy_state() {
  local response_file="$1"
  local service_id="$2"
  local expected="$3"
  local actual

  actual="$(extract_json "$response_file" "next((item.get('bookmarked') for item in (payload['data']['content'] or []) if str(item.get('id')) == '$service_id'), None)")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "policy list/search bookmark mismatch for service_id=${service_id}: expected ${expected}, got ${actual}" >&2
    cat "$response_file" >&2
    exit 1
  fi
}

assert_recommendation_state() {
  local response_file="$1"
  local service_id="$2"
  local expected="$3"
  local actual

  actual="$(extract_json "$response_file" "next((item.get('bookmarked') if 'bookmarked' in item else item.get('isBookmarked') for item in (payload['data'] or []) if str(item.get('serviceId')) == '$service_id'), None)")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "recommendation bookmark mismatch for service_id=${service_id}: expected ${expected}, got ${actual}" >&2
    cat "$response_file" >&2
    exit 1
  fi
}

assert_detail_state() {
  local response_file="$1"
  local expected="$2"
  local actual

  actual="$(extract_json "$response_file" "payload['data'].get('bookmarked')")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "policy detail bookmark mismatch: expected ${expected}, got ${actual}" >&2
    cat "$response_file" >&2
    exit 1
  fi
}

assert_bookmarks_contains() {
  local response_file="$1"
  local service_id="$2"
  local expected="$3"
  local count

  count="$(extract_json "$response_file" "sum(1 for item in (payload['data'] or []) if str(item.get('id')) == '$service_id')")"
  if [[ "${expected}" == "true" && "${count}" == "0" ]]; then
    echo "bookmarks list missing service_id=${service_id}" >&2
    cat "$response_file" >&2
    exit 1
  fi
  if [[ "${expected}" == "false" && "${count}" != "0" ]]; then
    echo "bookmarks list still contains service_id=${service_id}" >&2
    cat "$response_file" >&2
    exit 1
  fi
}

smoke_require_command curl
smoke_require_command python3
smoke_require_command docker

SMOKE_EMAIL="$(smoke_build_email "${SMOKE_EMAIL_PREFIX}")"

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

smoke_print_step "ensure policy fixture"
smoke_ensure_policy_fixture

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
ACCESS_TOKEN="$(extract_json "${LOGIN_RESPONSE}" 'payload["data"]["accessToken"]')"

smoke_print_step "priorities update"
PRIORITIES_STATUS="$(
  smoke_http_status PUT "${APP_BASE_URL}/api/users/me/priorities" "${PRIORITIES_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d '{"priorityCodes":["EDUCATION","JOB"]}'
)"
smoke_assert_status 200 "${PRIORITIES_STATUS}" "priorities update" "${PRIORITIES_RESPONSE}"

smoke_print_step "recommendations refresh"
RECOMMEND_REFRESH_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/recommendations/refresh" "${RECOMMEND_REFRESH_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${RECOMMEND_REFRESH_STATUS}" "recommendations refresh" "${RECOMMEND_REFRESH_RESPONSE}"

SERVICE_ID="$(extract_json "${RECOMMEND_REFRESH_RESPONSE}" '(payload["data"] or [])[0]["serviceId"] if (payload["data"] or []) else ""')"
SERVICE_TITLE="$(extract_json "${RECOMMEND_REFRESH_RESPONSE}" '(payload["data"] or [])[0]["title"] if (payload["data"] or []) else ""')"

if [[ -z "${SERVICE_ID}" || -z "${SERVICE_TITLE}" ]]; then
  echo "recommendations refresh returned no target row" >&2
  cat "${RECOMMEND_REFRESH_RESPONSE}" >&2
  exit 1
fi

smoke_print_step "toggle bookmark on serviceId=${SERVICE_ID}"
TOGGLE_ON_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/policies/${SERVICE_ID}/bookmark" "${TOGGLE_ON_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${TOGGLE_ON_STATUS}" "toggle bookmark on" "${TOGGLE_ON_RESPONSE}"

smoke_print_step "recommendation list bookmarked=true"
RECOMMEND_LIST_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/recommendations?size=20" "${RECOMMEND_LIST_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${RECOMMEND_LIST_STATUS}" "recommendation list" "${RECOMMEND_LIST_RESPONSE}"
assert_recommendation_state "${RECOMMEND_LIST_RESPONSE}" "${SERVICE_ID}" "True"

smoke_print_step "policy search bookmarked=true"
SEARCH_KEYWORD="$(
  python3 - "${SERVICE_TITLE}" <<'PY'
import sys
from urllib.parse import quote

print(quote(sys.argv[1]))
PY
)"
POLICY_SEARCH_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/policies/search?keyword=${SEARCH_KEYWORD}&size=20" "${POLICY_SEARCH_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${POLICY_SEARCH_STATUS}" "policy search" "${POLICY_SEARCH_RESPONSE}"
assert_policy_state "${POLICY_SEARCH_RESPONSE}" "${SERVICE_ID}" "True"

smoke_print_step "policy detail bookmarked=true"
POLICY_DETAIL_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/policies/${SERVICE_ID}" "${POLICY_DETAIL_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${POLICY_DETAIL_STATUS}" "policy detail" "${POLICY_DETAIL_RESPONSE}"
assert_detail_state "${POLICY_DETAIL_RESPONSE}" "True"

smoke_print_step "my bookmarks contains service"
BOOKMARKS_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/users/me/bookmarks" "${BOOKMARKS_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${BOOKMARKS_STATUS}" "my bookmarks" "${BOOKMARKS_RESPONSE}"
assert_bookmarks_contains "${BOOKMARKS_RESPONSE}" "${SERVICE_ID}" "true"

smoke_print_step "toggle bookmark off serviceId=${SERVICE_ID}"
TOGGLE_OFF_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/policies/${SERVICE_ID}/bookmark" "${TOGGLE_OFF_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${TOGGLE_OFF_STATUS}" "toggle bookmark off" "${TOGGLE_OFF_RESPONSE}"

smoke_print_step "recommendation list bookmarked=false"
RECOMMEND_LIST_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/recommendations?size=20" "${RECOMMEND_LIST_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${RECOMMEND_LIST_STATUS}" "recommendation list after off" "${RECOMMEND_LIST_RESPONSE}"
assert_recommendation_state "${RECOMMEND_LIST_RESPONSE}" "${SERVICE_ID}" "False"

smoke_print_step "policy search bookmarked=false"
POLICY_SEARCH_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/policies/search?keyword=${SEARCH_KEYWORD}&size=20" "${POLICY_SEARCH_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${POLICY_SEARCH_STATUS}" "policy search after off" "${POLICY_SEARCH_RESPONSE}"
assert_policy_state "${POLICY_SEARCH_RESPONSE}" "${SERVICE_ID}" "False"

smoke_print_step "policy detail bookmarked=false"
POLICY_DETAIL_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/policies/${SERVICE_ID}" "${POLICY_DETAIL_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${POLICY_DETAIL_STATUS}" "policy detail after off" "${POLICY_DETAIL_RESPONSE}"
assert_detail_state "${POLICY_DETAIL_RESPONSE}" "False"

smoke_print_step "my bookmarks excludes service"
BOOKMARKS_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/users/me/bookmarks" "${BOOKMARKS_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${BOOKMARKS_STATUS}" "my bookmarks after off" "${BOOKMARKS_RESPONSE}"
assert_bookmarks_contains "${BOOKMARKS_RESPONSE}" "${SERVICE_ID}" "false"

echo
echo "bookmark consistency smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "smoke_email=${SMOKE_EMAIL}"
echo "service_id=${SERVICE_ID}"
echo "service_title=${SERVICE_TITLE}"
