#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-rec.matrix}"
REQUIRE_RECOMMENDATION_CHANGE="${REQUIRE_RECOMMENDATION_CHANGE:-false}"
REQUIRE_REGION_MATCH="${REQUIRE_REGION_MATCH:-false}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SUMMARY_RESPONSE="${ARTIFACT_DIR}/summary.tsv"
COOKIE_DIR="${ARTIFACT_DIR}/cookies"
mkdir -p "${COOKIE_DIR}"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ "${KEEP_ARTIFACTS:-false}" == "true" ]]; then
    echo "artifacts kept: ${ARTIFACT_DIR}" >&2
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

json_get_access_token() {
  python3 - "$1" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)
print(payload["data"]["accessToken"])
PY
}

json_top_ids() {
  python3 - "$1" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)
items = payload.get("data") or []
print(",".join(str(item.get("serviceId") or item.get("id")) for item in items[:10] if item.get("serviceId") or item.get("id")))
PY
}

json_count() {
  python3 - "$1" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)
print(len(payload.get("data") or []))
PY
}

region_match_count() {
  local ids_csv="$1"
  local region_code="$2"
  [[ -n "${ids_csv}" ]] || {
    printf '%s' "0"
    return 0
  }
  smoke_db_query "
    select count(distinct sr.service_id)
    from service_regions sr
    where sr.service_id in (${ids_csv})
      and (
        sr.region_code = $(smoke_sql_quote "${region_code}")
        or substring(sr.region_code from 1 for 2) = substring($(smoke_sql_quote "${region_code}") from 1 for 2)
      );
  "
}

run_case() {
  local case_key="$1"
  local name="$2"
  local birth_date="$3"
  local sido="$4"
  local sgg="$5"
  local region_code="$6"
  local income_level="$7"
  local employment_status="$8"
  local household_type="$9"
  local interest_fields="${10}"
  local target_types="${11}"
  local priorities_a="${12}"
  local priorities_b="${13}"

  local email cookie signup_response login_response profile_a_response priority_a_response refresh_a_response priority_b_response refresh_b_response
  local signup_status login_status profile_a_status priority_a_status refresh_a_status priority_b_status refresh_b_status
  local token ids_a ids_b count_a count_b region_a region_b changed

  email="$(smoke_build_email "${SMOKE_EMAIL_PREFIX}.${case_key}")"
  cookie="${COOKIE_DIR}/${case_key}.cookie"
  signup_response="${ARTIFACT_DIR}/${case_key}.signup.json"
  login_response="${ARTIFACT_DIR}/${case_key}.login.json"
  profile_a_response="${ARTIFACT_DIR}/${case_key}.profile-a.json"
  priority_a_response="${ARTIFACT_DIR}/${case_key}.priority-a.json"
  refresh_a_response="${ARTIFACT_DIR}/${case_key}.refresh-a.json"
  priority_b_response="${ARTIFACT_DIR}/${case_key}.priority-b.json"
  refresh_b_response="${ARTIFACT_DIR}/${case_key}.refresh-b.json"

  smoke_print_step "signup ${case_key}"
  smoke_seed_verified_email "${email}"
  signup_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/auth/signup" "${signup_response}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"email\": \"${email}\",
        \"password\": \"${SMOKE_PASSWORD}\",
        \"name\": \"${name}\",
        \"birthDate\": \"${birth_date}\",
        \"privacyNoticeConfirmed\": true,
        \"optionalProfileConsentAgreed\": true,
        \"sido\": \"${sido}\",
        \"sgg\": \"${sgg}\",
        \"incomeLevel\": ${income_level},
        \"employmentStatus\": \"${employment_status}\",
        \"householdType\": \"${household_type}\"
      }"
  )"
  smoke_assert_status 200 "${signup_status}" "signup ${case_key}" "${signup_response}"

  login_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${login_response}" \
      -c "${cookie}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"email\": \"${email}\",
        \"password\": \"${SMOKE_PASSWORD}\"
      }"
  )"
  smoke_assert_status 200 "${login_status}" "login ${case_key}" "${login_response}"
  token="$(json_get_access_token "${login_response}")"

  smoke_print_step "case ${case_key}: baseline profile and priorities"
  profile_a_status="$(
    smoke_http_status PUT "${APP_BASE_URL}/api/users/me" "${profile_a_response}" \
      -H "Authorization: Bearer ${token}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"name\": \"${name}\",
        \"birthDate\": \"${birth_date}\",
        \"sido\": \"${sido}\",
        \"sgg\": \"${sgg}\",
        \"regionCode\": \"${region_code}\",
        \"incomeLevel\": ${income_level},
        \"employmentStatus\": \"${employment_status}\",
        \"householdType\": \"${household_type}\",
        \"interestFields\": ${interest_fields},
        \"targetTypes\": ${target_types},
        \"displayCount\": 10
      }"
  )"
  smoke_assert_status 200 "${profile_a_status}" "profile baseline ${case_key}" "${profile_a_response}"

  priority_a_status="$(
    smoke_http_status PUT "${APP_BASE_URL}/api/users/me/priorities" "${priority_a_response}" \
      -H "Authorization: Bearer ${token}" \
      -H 'Content-Type: application/json' \
      -d "{\"priorityCodes\": ${priorities_a}}"
  )"
  smoke_assert_status 200 "${priority_a_status}" "priorities baseline ${case_key}" "${priority_a_response}"

  refresh_a_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/recommendations/refresh?personal=true" "${refresh_a_response}" \
      -H "Authorization: Bearer ${token}"
  )"
  smoke_assert_status 200 "${refresh_a_status}" "refresh baseline ${case_key}" "${refresh_a_response}"
  ids_a="$(json_top_ids "${refresh_a_response}")"
  count_a="$(json_count "${refresh_a_response}")"
  region_a="$(region_match_count "${ids_a}" "${region_code}")"

  smoke_print_step "case ${case_key}: changed priorities"
  priority_b_status="$(
    smoke_http_status PUT "${APP_BASE_URL}/api/users/me/priorities" "${priority_b_response}" \
      -H "Authorization: Bearer ${token}" \
      -H 'Content-Type: application/json' \
      -d "{\"priorityCodes\": ${priorities_b}}"
  )"
  smoke_assert_status 200 "${priority_b_status}" "priorities changed ${case_key}" "${priority_b_response}"

  refresh_b_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/recommendations/refresh?personal=true" "${refresh_b_response}" \
      -H "Authorization: Bearer ${token}"
  )"
  smoke_assert_status 200 "${refresh_b_status}" "refresh changed ${case_key}" "${refresh_b_response}"
  ids_b="$(json_top_ids "${refresh_b_response}")"
  count_b="$(json_count "${refresh_b_response}")"
  region_b="$(region_match_count "${ids_b}" "${region_code}")"

  changed="false"
  if [[ "${ids_a}" != "${ids_b}" ]]; then
    changed="true"
  fi

  if [[ "${REQUIRE_RECOMMENDATION_CHANGE}" == "true" && "${changed}" != "true" ]]; then
    echo "case ${case_key} did not change top recommendations after priority update" >&2
    echo "before=${ids_a}" >&2
    echo "after=${ids_b}" >&2
    exit 1
  fi

  if [[ "${REQUIRE_REGION_MATCH}" == "true" && "${region_b}" == "0" ]]; then
    echo "case ${case_key} has no top recommendation matching region ${region_code}" >&2
    echo "after=${ids_b}" >&2
    exit 1
  fi

  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "${case_key}" "${region_code}" "${count_a}" "${count_b}" "${changed}" "${region_a}" "${region_b}" >> "${SUMMARY_RESPONSE}"
}

smoke_require_command curl
smoke_require_command python3

smoke_print_step "health check"
health_status="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${health_status}" "health check" "${HEALTH_RESPONSE}"

printf 'case\tregionCode\tbaselineCount\tchangedCount\ttopChanged\tbaselineRegionMatches\tchangedRegionMatches\n' > "${SUMMARY_RESPONSE}"

run_case "seoul-job" "서울점검" "2000-02-14" "서울특별시" "관악구" "11620" 5 "미취업" "1인 가구" '["일자리","교육"]' '["청년"]' '["JOB","EDUCATION"]' '["HOUSING","FINANCE"]'
run_case "incheon-edu" "인천점검" "2003-05-20" "인천광역시" "중구" "28110" 4 "재학" "1인 가구" '["교육","문화"]' '["대학생","청년"]' '["EDUCATION","CULTURE"]' '["JOB","HOUSING"]'
run_case "busan-house" "부산점검" "1998-09-08" "부산광역시" "해운대구" "26350" 3 "재직" "신혼부부" '["주거","생활지원"]' '["신혼부부","청년"]' '["HOUSING","FINANCE"]' '["EDUCATION","JOB"]'

smoke_print_step "recommendation condition matrix summary"
cat "${SUMMARY_RESPONSE}"
