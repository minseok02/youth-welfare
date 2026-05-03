#!/usr/bin/env bash
set -euo pipefail

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-recommend.click.smoke}"
SMOKE_NAME="${SMOKE_NAME:-Recommendation Click Smoke}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
DB_CONTAINER="${DB_CONTAINER:-youth-welfare-db}"
DB_ROOT_PASSWORD="${DB_ROOT_PASSWORD:-welfare1234!}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
COOKIE_JAR="${ARTIFACT_DIR}/click.cookie"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SIGNUP_RESPONSE="${ARTIFACT_DIR}/signup.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/login.json"
RECOMMEND_RESPONSE="${ARTIFACT_DIR}/recommendations.json"
POLICY_RESPONSE="${ARTIFACT_DIR}/policy.json"
DB_ROW_RESPONSE="${ARTIFACT_DIR}/db-row.txt"

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

extract_recommendation_triplet() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

items = payload.get("data") or []
if not items:
    print("")
    print("")
    print("")
else:
    first = items[0]
    print(first.get("id", ""))
    print(first.get("serviceId", ""))
    print(first.get("logId", ""))
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
ACCESS_TOKEN="$(extract_access_token "${LOGIN_RESPONSE}")"

print_step "recommendations refresh"
RECOMMEND_STATUS="$(
  http_status POST "${APP_BASE_URL}/api/recommendations/refresh" "${RECOMMEND_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
assert_status 200 "${RECOMMEND_STATUS}" "recommendations refresh" "${RECOMMEND_RESPONSE}"

mapfile -t RECOMMENDATION_FIELDS < <(extract_recommendation_triplet "${RECOMMEND_RESPONSE}")
RECOMMENDATION_ID="${RECOMMENDATION_FIELDS[0]:-}"
SERVICE_ID="${RECOMMENDATION_FIELDS[1]:-}"
LOG_ID="${RECOMMENDATION_FIELDS[2]:-}"

if [[ -z "${SERVICE_ID}" || -z "${LOG_ID}" ]]; then
  echo "recommendations refresh returned no clickable rows" >&2
  cat "${RECOMMEND_RESPONSE}" >&2
  exit 1
fi

print_step "policy detail via serviceId=${SERVICE_ID} logId=${LOG_ID}"
POLICY_STATUS="$(
  http_status GET "${APP_BASE_URL}/api/policies/${SERVICE_ID}?logId=${LOG_ID}" "${POLICY_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
assert_status 200 "${POLICY_STATUS}" "policy detail click trace" "${POLICY_RESPONSE}"

print_step "verify recommendation_logs click mark"
docker exec "${DB_CONTAINER}" mysql -uroot "-p${DB_ROOT_PASSWORD}" -N -e \
  "SELECT id, service_id, is_clicked, IFNULL(DATE_FORMAT(clicked_at, '%Y-%m-%d %H:%i:%s'),'NULL') FROM youth_welfare.recommendation_logs WHERE id = ${LOG_ID};" \
  > "${DB_ROW_RESPONSE}"

DB_CLICKED="$(awk 'NR==1 {print $3}' "${DB_ROW_RESPONSE}")"
if [[ "${DB_CLICKED}" != "1" ]]; then
  echo "recommendation log click mark missing for log_id=${LOG_ID}" >&2
  cat "${DB_ROW_RESPONSE}" >&2
  exit 1
fi

echo
echo "recommendation click smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "smoke_email=${SMOKE_EMAIL}"
echo "recommendation_id=${RECOMMENDATION_ID}"
echo "service_id=${SERVICE_ID}"
echo "log_id=${LOG_ID}"
echo "db_row=$(tr '\t' '|' < "${DB_ROW_RESPONSE}")"
