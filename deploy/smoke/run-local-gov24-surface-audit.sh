#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-gov24.surface.audit}"
SMOKE_NAME="${SMOKE_NAME:-표면점검}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
SEARCH_KEYWORD="${SEARCH_KEYWORD:-청년}"
SEARCH_SIZE="${SEARCH_SIZE:-20}"
LIST_SIZE="${LIST_SIZE:-20}"
RECOMMEND_SIZE="${RECOMMEND_SIZE:-10}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
COOKIE_JAR="${ARTIFACT_DIR}/surface.cookie"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SEARCH_RESPONSE="${ARTIFACT_DIR}/search.json"
SEARCH_REQUEST="${ARTIFACT_DIR}/search-request.json"
GOV24_SEARCH_RESPONSE="${ARTIFACT_DIR}/search-gov24.json"
GOV24_SEARCH_REQUEST="${ARTIFACT_DIR}/search-gov24-request.json"
LIST_RESPONSE="${ARTIFACT_DIR}/list.json"
DETAIL_RESPONSE="${ARTIFACT_DIR}/detail.json"
SIGNUP_RESPONSE="${ARTIFACT_DIR}/signup.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/login.json"
REFRESH_RESPONSE="${ARTIFACT_DIR}/recommend-refresh.json"

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

smoke_require_command curl
smoke_require_command python3

SMOKE_EMAIL="$(smoke_build_email "${SMOKE_EMAIL_PREFIX}")"

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

smoke_print_step "policy search keyword=${SEARCH_KEYWORD}"
python3 - "${SEARCH_KEYWORD}" "${SEARCH_SIZE}" "${SEARCH_REQUEST}" <<'PY'
import json
import sys

keyword, size, out_path = sys.argv[1], int(sys.argv[2]), sys.argv[3]
with open(out_path, "w", encoding="utf-8") as fp:
    json.dump({"keyword": keyword, "size": size}, fp, ensure_ascii=False)
PY
SEARCH_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/policies/search" "${SEARCH_RESPONSE}" \
    -H 'Content-Type: application/json' \
    -d @"${SEARCH_REQUEST}"
)"
smoke_assert_status 200 "${SEARCH_STATUS}" "policy search" "${SEARCH_RESPONSE}"

smoke_print_step "policy search sourceType=GOV24"
python3 - "${SEARCH_KEYWORD}" "${SEARCH_SIZE}" "${GOV24_SEARCH_REQUEST}" <<'PY'
import json
import sys

keyword, size, out_path = sys.argv[1], int(sys.argv[2]), sys.argv[3]
with open(out_path, "w", encoding="utf-8") as fp:
    json.dump({"keyword": keyword, "sourceType": "GOV24", "size": size}, fp, ensure_ascii=False)
PY
GOV24_SEARCH_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/policies/search" "${GOV24_SEARCH_RESPONSE}" \
    -H 'Content-Type: application/json' \
    -d @"${GOV24_SEARCH_REQUEST}"
)"
smoke_assert_status 200 "${GOV24_SEARCH_STATUS}" "policy search sourceType=GOV24" "${GOV24_SEARCH_RESPONSE}"

smoke_print_step "policy list"
LIST_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/policies?size=${LIST_SIZE}" "${LIST_RESPONSE}"
)"
smoke_assert_status 200 "${LIST_STATUS}" "policy list" "${LIST_RESPONSE}"

GOV24_POLICY_ID="$(
  python3 - "${GOV24_SEARCH_RESPONSE}" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

items = (payload.get("data") or {}).get("content") or []
print(items[0]["id"] if items else "")
PY
)"

if [[ -z "${GOV24_POLICY_ID}" ]]; then
  echo "gov24 filtered search returned no rows" >&2
  exit 1
fi

smoke_print_step "gov24 detail id=${GOV24_POLICY_ID}"
DETAIL_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/policies/${GOV24_POLICY_ID}" "${DETAIL_RESPONSE}"
)"
smoke_assert_status 200 "${DETAIL_STATUS}" "gov24 detail" "${DETAIL_RESPONSE}"

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

smoke_print_step "recommendation refresh"
REFRESH_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/recommendations/refresh" "${REFRESH_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${REFRESH_STATUS}" "recommendation refresh" "${REFRESH_RESPONSE}"

python3 - "${SEARCH_RESPONSE}" "${GOV24_SEARCH_RESPONSE}" "${LIST_RESPONSE}" "${DETAIL_RESPONSE}" "${REFRESH_RESPONSE}" "${RECOMMEND_SIZE}" <<'PY'
import json
import sys
from collections import Counter

search_file, gov24_search_file, list_file, detail_file, refresh_file, recommend_size = sys.argv[1:]
recommend_size = int(recommend_size)

def load(path):
    with open(path, "r", encoding="utf-8") as fp:
        return json.load(fp)

def content(payload):
    data = payload.get("data") or {}
    if isinstance(data, dict):
        return data.get("content") or []
    return data or []

def metric(key, value):
    print(f"METRIC {key}={value}")

search_items = content(load(search_file))
gov24_search_items = content(load(gov24_search_file))
list_items = content(load(list_file))
detail = load(detail_file).get("data") or {}
refresh_items = (load(refresh_file).get("data") or [])[:recommend_size]

search_counter = Counter((item.get("sourceType") or "") for item in search_items)
list_counter = Counter((item.get("sourceType") or "") for item in list_items)
recommend_counter = Counter((item.get("sourceType") or "") for item in refresh_items)

metric("search_total", len(search_items))
metric("search_gov24", search_counter.get("GOV24", 0))
metric("search_gov24_share_pct", round(search_counter.get("GOV24", 0) * 100.0 / len(search_items), 2) if search_items else 0)
metric("search_distinct_sources", ",".join(f"{k}:{v}" for k, v in sorted(search_counter.items())))

metric("gov24_filtered_total", len(gov24_search_items))
metric("gov24_filtered_non_gov24", sum(1 for item in gov24_search_items if item.get("sourceType") != "GOV24"))

metric("list_total", len(list_items))
metric("list_gov24", list_counter.get("GOV24", 0))
metric("list_gov24_share_pct", round(list_counter.get("GOV24", 0) * 100.0 / len(list_items), 2) if list_items else 0)
metric("list_distinct_sources", ",".join(f"{k}:{v}" for k, v in sorted(list_counter.items())))

filled_detail = sum(1 for key in ["targetDetail", "selectionCriteria", "homepageUrl", "contactList", "relatedLaw", "formFiles"] if detail.get(key))
filled_labels = sum(1 for key in ["gov24ServiceFieldLabel", "gov24UserTypeLabel", "gov24BenefitTypeLabel"] if detail.get(key))
metric("detail_source_type", detail.get("sourceType") or "")
metric("detail_filled_fields", filled_detail)
metric("detail_filled_gov24_labels", filled_labels)
metric("detail_has_reference_urls", 1 if detail.get("referenceUrlsJson") else 0)

metric("recommend_topn_total", len(refresh_items))
metric("recommend_topn_gov24", recommend_counter.get("GOV24", 0))
metric("recommend_topn_gov24_share_pct", round(recommend_counter.get("GOV24", 0) * 100.0 / len(refresh_items), 2) if refresh_items else 0)
metric("recommend_topn_distinct_sources", ",".join(f"{k}:{v}" for k, v in sorted(recommend_counter.items())))
PY

echo
echo "gov24 surface audit passed"
echo "app_base_url=${APP_BASE_URL}"
echo "search_keyword=${SEARCH_KEYWORD}"
echo "gov24_policy_id=${GOV24_POLICY_ID}"
echo "smoke_email=${SMOKE_EMAIL}"
