#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-housing.standard.code.audit}"
SMOKE_NAME="${SMOKE_NAME:-주거코드점검}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_REGION_CODE="${SMOKE_REGION_CODE:-28110}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
SMOKE_INTEREST_FIELD="${SMOKE_INTEREST_FIELD:-주거}"
SMOKE_PRIORITY_CODE="${SMOKE_PRIORITY_CODE:-HOUSING}"
SMOKE_HOUSE_TENURE_CODE="${SMOKE_HOUSE_TENURE_CODE:-3}"
SMOKE_HOUSING_TYPE_CODE="${SMOKE_HOUSING_TYPE_CODE:-}"
SMOKE_BASIC_LIVING_RECIPIENT_TYPE_CODE="${SMOKE_BASIC_LIVING_RECIPIENT_TYPE_CODE:-}"
SMOKE_DISABILITY_GRADE_CODE="${SMOKE_DISABILITY_GRADE_CODE:-}"
MIN_POSITIVE_RULE_DELTA_ROWS="${MIN_POSITIVE_RULE_DELTA_ROWS:-1}"
MIN_MAX_RULE_DELTA="${MIN_MAX_RULE_DELTA:-7.99}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
COOKIE_JAR="${ARTIFACT_DIR}/user.cookie"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SIGNUP_RESPONSE="${ARTIFACT_DIR}/signup.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/login.json"
PROFILE_BASELINE_RESPONSE="${ARTIFACT_DIR}/profile-baseline.json"
PROFILE_ENHANCED_RESPONSE="${ARTIFACT_DIR}/profile-enhanced.json"
PRIORITIES_RESPONSE="${ARTIFACT_DIR}/priorities.json"
REFRESH_BASELINE_RESPONSE="${ARTIFACT_DIR}/refresh-baseline.json"
REFRESH_ENHANCED_RESPONSE="${ARTIFACT_DIR}/refresh-enhanced.json"
BASELINE_BATCH_TSV="${ARTIFACT_DIR}/baseline-batch.tsv"
ENHANCED_BATCH_TSV="${ARTIFACT_DIR}/enhanced-batch.tsv"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

metric() {
  local key="$1"
  local value="$2"
  printf 'METRIC %s=%s\n' "${key}" "${value}"
}

export_latest_batch_tsv() {
  local email="$1"
  local output_file="$2"
  smoke_db_query "
    with target_user as (
      select u.user_key
      from users u
      where u.email = '${email}'
    ),
    latest_batch as (
      select max(recommended_at) as recommended_at
      from user_recommendations
      where user_key = (select user_key from target_user)
    )
    select
      ws.id,
      ws.source_type,
      replace(ws.title, E'\t', ' '),
      coalesce(ur.rule_weighted_score::text, ''),
      coalesce(ur.ai_score::text, ''),
      coalesce(ur.final_score::text, ''),
      row_number() over (order by ur.final_score desc, ur.id asc)
    from user_recommendations ur
    join latest_batch lb
      on lb.recommended_at = ur.recommended_at
    join welfare_services ws
      on ws.id = ur.service_id
    where ur.user_key = (select user_key from target_user)
    order by ur.final_score desc, ur.id asc;
  " > "${output_file}"
}

compare_batch_exports() {
  local baseline_file="$1"
  local enhanced_file="$2"
  python3 - "${baseline_file}" "${enhanced_file}" <<'PY'
import csv
import json
import sys

baseline_file, enhanced_file = sys.argv[1], sys.argv[2]

def load_rows(path):
    rows = {}
    with open(path, "r", encoding="utf-8") as fp:
        reader = csv.reader(fp, delimiter="\t")
        for cols in reader:
            if len(cols) != 7:
                continue
            service_id, source_type, title, rule_score, ai_score, final_score, final_rank = cols
            rows[service_id] = {
                "service_id": service_id,
                "source_type": source_type,
                "title": title,
                "rule_score": float(rule_score or 0),
                "ai_score": float(ai_score or 0),
                "final_score": float(final_score or 0),
                "final_rank": int(final_rank or 0),
            }
    return rows

baseline = load_rows(baseline_file)
enhanced = load_rows(enhanced_file)
common_ids = [service_id for service_id in enhanced if service_id in baseline]
compared = []
for service_id in common_ids:
    prev_row = baseline[service_id]
    curr_row = enhanced[service_id]
    compared.append({
        "service_id": service_id,
        "source_type": curr_row["source_type"],
        "title": curr_row["title"],
        "previous_rule": prev_row["rule_score"],
        "current_rule": curr_row["rule_score"],
        "rule_delta": curr_row["rule_score"] - prev_row["rule_score"],
        "previous_final": prev_row["final_score"],
        "current_final": curr_row["final_score"],
        "final_delta": curr_row["final_score"] - prev_row["final_score"],
        "previous_rank": prev_row["final_rank"],
        "current_rank": curr_row["final_rank"],
    })

positive_rule = [row for row in compared if row["rule_delta"] > 0.0001]
positive_final = [row for row in compared if row["final_delta"] > 0.0001]
positive_rule.sort(key=lambda row: (-row["rule_delta"], row["current_rank"]))

def emit(key, value):
    print(f"{key}\t{value}")

emit("baseline_batch_rows", len(baseline))
emit("enhanced_batch_rows", len(enhanced))
emit("comparable_service_rows", len(compared))
emit("positive_rule_delta_rows", len(positive_rule))
emit("positive_final_delta_rows", len(positive_final))
emit("max_rule_delta", f"{max((row['rule_delta'] for row in compared), default=0):.5f}")
emit("max_final_delta", f"{max((row['final_delta'] for row in compared), default=0):.5f}")
if positive_rule:
    summary = " || ".join(
        f"{row['service_id']}:{row['source_type']}:{row['title']}:{row['previous_rule']:.5f}->{row['current_rule']:.5f}:{row['rule_delta']:.5f}:{row['previous_rank']}->{row['current_rank']}"
        for row in positive_rule[:10]
    )
else:
    summary = "empty"
emit("top_positive_rule_delta_rows", summary)
PY
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

smoke_print_step "login"
LOGIN_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${LOGIN_RESPONSE}" \
    -c "${COOKIE_JAR}" \
    -A "youth-welfare-smoke/${SMOKE_EMAIL}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\"
    }"
)"
smoke_assert_status 200 "${LOGIN_STATUS}" "login" "${LOGIN_RESPONSE}"
ACCESS_TOKEN="$(python3 - "${LOGIN_RESPONSE}" <<'PY'
import json
import sys
with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)
print(payload["data"]["accessToken"])
PY
)"

smoke_print_step "priority update (${SMOKE_PRIORITY_CODE})"
PRIORITIES_STATUS="$(
  smoke_http_status PUT "${APP_BASE_URL}/api/users/me/priorities" "${PRIORITIES_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d "{\"priorityCodes\":[\"${SMOKE_PRIORITY_CODE}\"]}"
)"
smoke_assert_status 200 "${PRIORITIES_STATUS}" "priority update" "${PRIORITIES_RESPONSE}"

smoke_print_step "baseline profile update"
PROFILE_BASELINE_STATUS="$(
  smoke_http_status PUT "${APP_BASE_URL}/api/users/me" "${PROFILE_BASELINE_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"regionCode\": \"${SMOKE_REGION_CODE}\",
      \"sido\": \"${SMOKE_SIDO}\",
      \"sgg\": \"${SMOKE_SGG}\",
      \"incomeLevel\": ${SMOKE_INCOME_LEVEL},
      \"householdType\": \"${SMOKE_HOUSEHOLD_TYPE}\",
      \"employmentStatus\": \"${SMOKE_EMPLOYMENT_STATUS}\",
      \"notificationYn\": false,
      \"notificationMinScore\": 0.5,
      \"displayCount\": 30,
      \"optionalProfileConsentAgreed\": true,
      \"sensitiveInfoConsentAgreed\": true,
      \"interestFields\": [\"${SMOKE_INTEREST_FIELD}\"],
      \"targetTypes\": []
    }"
)"
smoke_assert_status 200 "${PROFILE_BASELINE_STATUS}" "baseline profile update" "${PROFILE_BASELINE_RESPONSE}"

smoke_print_step "baseline refresh"
REFRESH_BASELINE_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/recommendations/refresh" "${REFRESH_BASELINE_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${REFRESH_BASELINE_STATUS}" "baseline refresh" "${REFRESH_BASELINE_RESPONSE}"
export_latest_batch_tsv "${SMOKE_EMAIL}" "${BASELINE_BATCH_TSV}"

sleep 1

smoke_print_step "enhanced profile update"
enhanced_payload="$(
  python3 - <<PY
import json
payload = {
    "regionCode": "${SMOKE_REGION_CODE}",
    "sido": "${SMOKE_SIDO}",
    "sgg": "${SMOKE_SGG}",
    "incomeLevel": ${SMOKE_INCOME_LEVEL},
    "householdType": "${SMOKE_HOUSEHOLD_TYPE}",
    "employmentStatus": "${SMOKE_EMPLOYMENT_STATUS}",
    "notificationYn": False,
    "notificationMinScore": 0.5,
    "displayCount": 30,
    "optionalProfileConsentAgreed": True,
    "sensitiveInfoConsentAgreed": True,
    "interestFields": ["${SMOKE_INTEREST_FIELD}"],
    "targetTypes": [],
}
if "${SMOKE_HOUSE_TENURE_CODE}":
    payload["houseTenureCode"] = "${SMOKE_HOUSE_TENURE_CODE}"
if "${SMOKE_HOUSING_TYPE_CODE}":
    payload["housingTypeCode"] = "${SMOKE_HOUSING_TYPE_CODE}"
if "${SMOKE_BASIC_LIVING_RECIPIENT_TYPE_CODE}":
    payload["basicLivingRecipientTypeCode"] = "${SMOKE_BASIC_LIVING_RECIPIENT_TYPE_CODE}"
if "${SMOKE_DISABILITY_GRADE_CODE}":
    payload["disabilityGradeCode"] = "${SMOKE_DISABILITY_GRADE_CODE}"
print(json.dumps(payload, ensure_ascii=False))
PY
)"
PROFILE_ENHANCED_STATUS="$(
  smoke_http_status PUT "${APP_BASE_URL}/api/users/me" "${PROFILE_ENHANCED_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d "${enhanced_payload}"
)"
smoke_assert_status 200 "${PROFILE_ENHANCED_STATUS}" "enhanced profile update" "${PROFILE_ENHANCED_RESPONSE}"

sleep 1

smoke_print_step "enhanced refresh"
REFRESH_ENHANCED_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/recommendations/refresh" "${REFRESH_ENHANCED_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${REFRESH_ENHANCED_STATUS}" "enhanced refresh" "${REFRESH_ENHANCED_RESPONSE}"
export_latest_batch_tsv "${SMOKE_EMAIL}" "${ENHANCED_BATCH_TSV}"

METRICS="$(compare_batch_exports "${BASELINE_BATCH_TSV}" "${ENHANCED_BATCH_TSV}")"
echo "${METRICS}" | while IFS=$'\t' read -r key value; do
  [[ -n "${key}" ]] || continue
  metric "${key}" "${value}"
done

positive_rule_delta_rows="$(echo "${METRICS}" | awk -F $'\t' '$1=="positive_rule_delta_rows"{print $2}')"
max_rule_delta="$(echo "${METRICS}" | awk -F $'\t' '$1=="max_rule_delta"{print $2}')"

python3 - "${positive_rule_delta_rows}" "${max_rule_delta}" "${MIN_POSITIVE_RULE_DELTA_ROWS}" "${MIN_MAX_RULE_DELTA}" <<'PY'
import sys

positive_rule_delta_rows = int(sys.argv[1] or "0")
max_rule_delta = float(sys.argv[2] or "0")
min_positive_rule_delta_rows = int(sys.argv[3] or "0")
min_max_rule_delta = float(sys.argv[4] or "0")

if positive_rule_delta_rows < min_positive_rule_delta_rows:
    raise SystemExit(
        "housing standard code effect below expected row threshold: "
        f"positive_rule_delta_rows={positive_rule_delta_rows} min={min_positive_rule_delta_rows}"
    )
if max_rule_delta < min_max_rule_delta:
    raise SystemExit(
        "housing standard code bonus below expected threshold: "
        f"max_rule_delta={max_rule_delta} min={min_max_rule_delta}"
    )
PY

echo "OK housing_standard_code_effect_audit"
