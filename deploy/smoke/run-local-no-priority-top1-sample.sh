#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-recommend.nopriority.sample}"
SMOKE_NAME="${SMOKE_NAME:-무우선순위}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
SAMPLE_COUNT="${SAMPLE_COUNT:-8}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SUMMARY_TSV="${ARTIFACT_DIR}/summary.tsv"

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

query_top1_row() {
  local email="$1"
  smoke_db_query "
    SELECT u.user_key,
           COALESCE(priority_counts.priority_count, 0),
           COALESCE(top1.service_id::text, ''),
           COALESCE(top1.title, ''),
           COALESCE(top1.source_type, ''),
           COALESCE(top1.category, ''),
           COALESCE(top1.final_score::text, '')
    FROM users u
    LEFT JOIN LATERAL (
      SELECT COUNT(*) AS priority_count
      FROM user_priorities up
      WHERE up.user_key = u.user_key
    ) priority_counts ON true
    LEFT JOIN LATERAL (
      SELECT ur.service_id,
             ws.title,
             ws.source_type,
             COALESCE(ws.unified_category, '기타') AS category,
             ur.final_score
      FROM user_recommendations ur
      JOIN welfare_services ws
        ON ws.id = ur.service_id
      WHERE ur.user_key = u.user_key
      ORDER BY ur.recommended_at DESC, ur.final_score DESC, ur.id DESC
      LIMIT 1
    ) top1 ON true
    WHERE u.email = '${email}';
  "
}

summarize_results() {
  local summary_file="$1"
  python3 - "${summary_file}" <<'PY'
import csv
import sys
from collections import Counter

rows = []
with open(sys.argv[1], "r", encoding="utf-8") as fp:
    reader = csv.DictReader(fp, delimiter="\t")
    rows = list(reader)

if not rows:
    raise SystemExit("no rows captured")

top1_counter = Counter()
category_counter = Counter()
source_counter = Counter()

for row in rows:
    key = (
        row["top1_service_id"],
        row["top1_title"],
        row["top1_source"],
        row["top1_category"],
    )
    top1_counter[key] += 1
    category_counter[row["top1_category"]] += 1
    source_counter[row["top1_source"]] += 1

leader, leader_count = top1_counter.most_common(1)[0]
sample_count = len(rows)
share_pct = round(leader_count * 100.0 / sample_count, 2)

print(f"sample_count={sample_count}")
print(f"leader_service_id={leader[0]}")
print(f"leader_title={leader[1]}")
print(f"leader_source={leader[2]}")
print(f"leader_category={leader[3]}")
print(f"leader_users={leader_count}")
print(f"leader_share_pct={share_pct}")
print("[top1_distribution]")
for (service_id, title, source, category), count in top1_counter.most_common():
    print(f"{service_id}|{title}|{source}|{category}|{count}")
print("[category_distribution]")
for category, count in category_counter.most_common():
    print(f"{category}|{count}")
print("[source_distribution]")
for source, count in source_counter.most_common():
    print(f"{source}|{count}")
PY
}

smoke_require_command curl
smoke_require_command python3

printf 'sample_index\temail\tuser_key\tpriority_count\trecommendation_count\ttop1_service_id\ttop1_title\ttop1_source\ttop1_category\ttop1_final_score\n' > "${SUMMARY_TSV}"

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

for sample_index in $(seq 1 "${SAMPLE_COUNT}"); do
  email="$(smoke_build_email "${SMOKE_EMAIL_PREFIX}")"
  signup_response="${ARTIFACT_DIR}/signup-${sample_index}.json"
  login_response="${ARTIFACT_DIR}/login-${sample_index}.json"
  refresh_response="${ARTIFACT_DIR}/refresh-${sample_index}.json"
  cookie_jar="${ARTIFACT_DIR}/cookie-${sample_index}.jar"

  smoke_print_step "sample ${sample_index}/${SAMPLE_COUNT} signup ${email}"
  smoke_seed_verified_email "${email}"
  signup_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/auth/signup" "${signup_response}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"email\": \"${email}\",
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
  smoke_assert_status 200 "${signup_status}" "signup sample ${sample_index}" "${signup_response}"

  login_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${login_response}" \
      -c "${cookie_jar}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"email\": \"${email}\",
        \"password\": \"${SMOKE_PASSWORD}\"
      }"
  )"
  smoke_assert_status 200 "${login_status}" "login sample ${sample_index}" "${login_response}"
  access_token="$(extract_access_token "${login_response}")"

  refresh_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/recommendations/refresh" "${refresh_response}" \
      -H "Authorization: Bearer ${access_token}"
  )"
  smoke_assert_status 200 "${refresh_status}" "recommendations refresh sample ${sample_index}" "${refresh_response}"
  recommendation_count="$(extract_recommendation_count "${refresh_response}")"

  top1_row="$(query_top1_row "${email}")"
  if [[ -z "${top1_row}" ]]; then
    echo "missing top1 row for ${email}" >&2
    exit 1
  fi

  IFS=$'\t' read -r user_key priority_count top1_service_id top1_title top1_source top1_category top1_final_score <<< "${top1_row}"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "${sample_index}" \
    "${email}" \
    "${user_key}" \
    "${priority_count}" \
    "${recommendation_count}" \
    "${top1_service_id}" \
    "${top1_title}" \
    "${top1_source}" \
    "${top1_category}" \
    "${top1_final_score}" \
    >> "${SUMMARY_TSV}"
done

echo
echo "no-priority top1 sample passed"
echo "app_base_url=${APP_BASE_URL}"
echo "sample_count=${SAMPLE_COUNT}"
summarize_results "${SUMMARY_TSV}"
