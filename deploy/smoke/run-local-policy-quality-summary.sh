#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
APP_CONTAINER_NAME="${APP_CONTAINER_NAME:-youth-welfare-app}"

ADMIN_EMAIL="${ADMIN_EMAIL:-admin@example.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-password123!}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/admin-login.json"
RETRIEVAL_RESPONSE="${ARTIFACT_DIR}/retrieval-evaluation.json"
GATE_RESPONSE="${ARTIFACT_DIR}/retrieval-gate.json"
CATEGORY_RESPONSE="${ARTIFACT_DIR}/category-audit.json"

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

extract_summary() {
  local retrieval_file="$1"
  local gate_file="$2"
  local category_file="$3"
  python3 - "$retrieval_file" "$gate_file" "$category_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    retrieval = json.load(fp)["data"]
with open(sys.argv[2], "r", encoding="utf-8") as fp:
    gate = json.load(fp)["data"]
with open(sys.argv[3], "r", encoding="utf-8") as fp:
    category = json.load(fp)["data"]

top_category = ""
top_category_total_share = ""
top_category_searchable_coverage = ""
if category["topUnifiedCategorySummaries"]:
    top = category["topUnifiedCategorySummaries"][0]
    top_category = top["unifiedCategory"]
    top_category_total_share = top["totalShare"]
    top_category_searchable_coverage = top["searchableCoverage"]

top_youth_broad = ""
top_youth_broad_dominant = ""
top_youth_broad_share = ""
if category["youthBroadCategorySummaries"]:
    top = category["youthBroadCategorySummaries"][0]
    top_youth_broad = top["sourceCategory"]
    top_youth_broad_dominant = top["dominantUnifiedCategory"]
    top_youth_broad_share = top["dominantShare"]

print(retrieval["datasetKey"])
print(retrieval["scenarioCount"])
print(retrieval["top1HitRate"])
print(retrieval["top3HitRate"])
print(retrieval["branchSuggestionHitRate"])
print(retrieval["fallbackCount"])
print(retrieval["emptyResultCount"])
print(gate["passed"])
print("|".join(gate["failureReasons"]))
print(category["totalPolicyCount"])
print(category["searchablePolicyCount"])
print(category["searchablePolicyRatio"])
print(len(category["unifiedCategoryCounts"]))
print(top_category)
print(top_category_total_share)
print(top_category_searchable_coverage)
print(top_youth_broad)
print(top_youth_broad_dominant)
print(top_youth_broad_share)
PY
}

smoke_require_command curl
smoke_require_command python3

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

smoke_print_step "admin login (${ADMIN_EMAIL})"
LOGIN_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${LOGIN_RESPONSE}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${ADMIN_EMAIL}\",
      \"password\": \"${ADMIN_PASSWORD}\"
    }"
)"
smoke_assert_status 200 "${LOGIN_STATUS}" "admin login" "${LOGIN_RESPONSE}"
ADMIN_ACCESS_TOKEN="$(extract_access_token "${LOGIN_RESPONSE}")"

smoke_print_step "retrieval evaluation"
RETRIEVAL_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/admin/policies/retrieval-evaluations/run" "${RETRIEVAL_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${RETRIEVAL_STATUS}" "retrieval evaluation" "${RETRIEVAL_RESPONSE}"

smoke_print_step "retrieval quality gate"
GATE_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/admin/policies/retrieval-evaluations/gate" "${GATE_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${GATE_STATUS}" "retrieval quality gate" "${GATE_RESPONSE}"

smoke_print_step "category audit"
CATEGORY_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/policies/category-audit" "${CATEGORY_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${CATEGORY_STATUS}" "category audit" "${CATEGORY_RESPONSE}"

SUMMARY_OUTPUT="$(extract_summary "${RETRIEVAL_RESPONSE}" "${GATE_RESPONSE}" "${CATEGORY_RESPONSE}")"
mapfile -t VALUES <<< "${SUMMARY_OUTPUT}"

echo
echo "policy quality summary passed"
echo "dataset_key=${VALUES[0]}"
echo "scenario_count=${VALUES[1]}"
echo "top1_hit_rate=${VALUES[2]}"
echo "top3_hit_rate=${VALUES[3]}"
echo "branch_suggestion_hit_rate=${VALUES[4]}"
echo "fallback_count=${VALUES[5]}"
echo "empty_result_count=${VALUES[6]}"
echo "quality_gate_passed=${VALUES[7]}"
echo "quality_gate_failure_reasons=${VALUES[8]}"
echo "category_total_policies=${VALUES[9]}"
echo "category_searchable_policies=${VALUES[10]}"
echo "category_searchable_ratio=${VALUES[11]}"
echo "category_unified_count=${VALUES[12]}"
echo "category_top_unified=${VALUES[13]}"
echo "category_top_unified_total_share=${VALUES[14]}"
echo "category_top_unified_searchable_coverage=${VALUES[15]}"
echo "youth_broad_top_source=${VALUES[16]}"
echo "youth_broad_top_dominant_unified=${VALUES[17]}"
echo "youth_broad_top_dominant_share=${VALUES[18]}"
