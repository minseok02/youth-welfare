#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
APP_CONTAINER_NAME="${APP_CONTAINER_NAME:-youth-welfare-app}"

smoke_resolve_admin_credentials "${ROOT_DIR}"
: "${ADMIN_EMAIL:?ADMIN_EMAIL is empty; export ADMIN_EMAIL or set SECURITY_ADMIN_EMAILS/.env or /tmp/youth-welfare-admin-smoke-email}"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD is empty; export ADMIN_PASSWORD or set /tmp/youth-welfare-admin-smoke-password}"
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS:-14}"
BREAKDOWN_LIMIT="${BREAKDOWN_LIMIT:-3}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/admin-login.json"
WRITE_RESPONSE="${ARTIFACT_DIR}/approval-write.json"
CLEAR_RESPONSE="${ARTIFACT_DIR}/approval-clear.json"
SUMMARY_BASELINE_RESPONSE="${ARTIFACT_DIR}/summary-baseline.json"
SUMMARY_APPROVED_RESPONSE="${ARTIFACT_DIR}/summary-approved.json"
SUMMARY_CLEARED_RESPONSE="${ARTIFACT_DIR}/summary-cleared.json"
BREAKDOWN_BASELINE_RESPONSE="${ARTIFACT_DIR}/breakdown-baseline.json"
BREAKDOWN_APPROVED_RESPONSE="${ARTIFACT_DIR}/breakdown-approved.json"
BREAKDOWN_CLEARED_RESPONSE="${ARTIFACT_DIR}/breakdown-cleared.json"

WRITE_EXECUTED=false

ensure_approval_table_exists() {
  local relation_name
  relation_name="$(docker exec youth-welfare-db psql -U postgres -d youth_welfare -Atqc \
    "select coalesce(to_regclass('public.recommendation_review_gate_promotion_approvals')::text, '')")"
  if [[ "${relation_name}" != "recommendation_review_gate_promotion_approvals" ]]; then
    echo "recommendation_review_gate_promotion_approvals table is missing; run deploy/postgres/apply-local-runtime-schema-patch.sh first" >&2
    exit 1
  fi
}

cleanup() {
  if [[ "${WRITE_EXECUTED}" == "true" ]]; then
    smoke_http_status DELETE \
      "${APP_BASE_URL}/api/admin/dashboard/recommendation-review-gate/promotion-approval-record" \
      "${ARTIFACT_DIR}/cleanup-clear.json" \
      -H "Authorization: Bearer ${ADMIN_TOKEN:-}" >/dev/null 2>&1 || true
  fi
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

extract_jwt_roles() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import base64
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    token = json.load(fp)["data"]["accessToken"]

payload = token.split(".")[1]
payload += "=" * (-len(payload) % 4)
decoded = json.loads(base64.urlsafe_b64decode(payload))
print(",".join(decoded.get("roles") or []))
PY
}

extract_gate_tuple() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    data = json.load(fp)["data"]
    gate_data = data.get("recommendation", data)

print(gate_data["reviewGatePolicyPromotionApprovalDecisionStatus"])
print(gate_data["reviewGatePolicyPromotionApprovalRecordStatus"])
print(gate_data["reviewGatePolicyPromotionReviewRunStatus"])
print(gate_data["reviewGatePolicyPromotionReviewRunApprovalRecordWriteStatus"])
PY
}

assert_write_response() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    data = json.load(fp)["data"]

assert data["approvalKey"] == "RECENT_WINDOW_BOUNDED_PROMOTION_REVIEW"
assert data["approvalStatus"] == "APPROVED_FOR_BOUNDED_PROMOTION_REVIEW"
assert data["approvalScope"] == "RECOMMENDATION_REVIEW_GATE_POLICY_PROMOTION"
assert data["recorded"] is True
assert data["approvedByUserKey"]
assert data["approvedAt"]
print(data["approvedByUserKey"])
print(data["approvedAt"])
PY
}

assert_clear_response() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    data = json.load(fp)["data"]

assert data["approvalKey"] == "RECENT_WINDOW_BOUNDED_PROMOTION_REVIEW"
assert data["cleared"] is True
print("true")
PY
}

assert_approved_tuple() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    data = json.load(fp)["data"]
    gate_data = data.get("recommendation", data)

assert gate_data["reviewGatePolicyPromotionApprovalDecisionStatus"] == "APPROVED_FOR_BOUNDED_PROMOTION_REVIEW"
assert gate_data["reviewGatePolicyPromotionApprovalRecordStatus"] == "EXPLICIT_PROMOTION_APPROVAL_RECORDED"
assert gate_data["reviewGatePolicyPromotionReviewRunStatus"] == "AWAIT_BOUNDED_PROMOTION_REVIEW_RUN"
assert gate_data["reviewGatePolicyPromotionReviewRunApprovalRecordWriteStatus"] == "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE_COMPLETED"
print("approved")
PY
}

assert_tuple_equals() {
  local response_file="$1"
  local expected_decision="$2"
  local expected_record="$3"
  local expected_run="$4"
  local expected_write="$5"
  python3 - "$response_file" "$expected_decision" "$expected_record" "$expected_run" "$expected_write" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    data = json.load(fp)["data"]
    gate_data = data.get("recommendation", data)

actual = (
    gate_data["reviewGatePolicyPromotionApprovalDecisionStatus"],
    gate_data["reviewGatePolicyPromotionApprovalRecordStatus"],
    gate_data["reviewGatePolicyPromotionReviewRunStatus"],
    gate_data["reviewGatePolicyPromotionReviewRunApprovalRecordWriteStatus"],
)
expected = tuple(sys.argv[2:6])
assert actual == expected, f"expected={expected} actual={actual}"
print("|".join(actual))
PY
}

smoke_require_command python3

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

smoke_print_step "verify approval record table exists"
ensure_approval_table_exists

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
ADMIN_TOKEN="$(extract_access_token "${LOGIN_RESPONSE}")"
ADMIN_ROLES="$(extract_jwt_roles "${LOGIN_RESPONSE}")"
if [[ ",${ADMIN_ROLES}," != *",ROLE_ADMIN,"* ]]; then
  echo "admin login succeeded but ROLE_ADMIN is missing for ${ADMIN_EMAIL}" >&2
  exit 1
fi

smoke_print_step "capture baseline summary"
SUMMARY_BASELINE_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/summary?summaryWindowDays=${SUMMARY_WINDOW_DAYS}&trendWindowDays=1&trendWindowDays=7&trendWindowDays=30" "${SUMMARY_BASELINE_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${SUMMARY_BASELINE_STATUS}" "baseline summary" "${SUMMARY_BASELINE_RESPONSE}"
mapfile -t BASELINE_SUMMARY_TUPLE <<< "$(extract_gate_tuple "${SUMMARY_BASELINE_RESPONSE}")"

smoke_print_step "capture baseline breakdowns"
BREAKDOWN_BASELINE_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/recommendation-breakdowns?summaryWindowDays=${SUMMARY_WINDOW_DAYS}&limit=${BREAKDOWN_LIMIT}" "${BREAKDOWN_BASELINE_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${BREAKDOWN_BASELINE_STATUS}" "baseline breakdowns" "${BREAKDOWN_BASELINE_RESPONSE}"
mapfile -t BASELINE_BREAKDOWN_TUPLE <<< "$(extract_gate_tuple "${BREAKDOWN_BASELINE_RESPONSE}")"

smoke_print_step "write explicit promotion approval record"
APPROVAL_NOTE="bounded promotion review approved @ $(smoke_now_iso_utc)"
WRITE_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/admin/dashboard/recommendation-review-gate/promotion-approval-record" "${WRITE_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"approvalNote\": \"${APPROVAL_NOTE}\"
    }"
)"
smoke_assert_status 200 "${WRITE_STATUS}" "approval record write" "${WRITE_RESPONSE}"
WRITE_EXECUTED=true
mapfile -t WRITE_VALUES <<< "$(assert_write_response "${WRITE_RESPONSE}")"

smoke_print_step "verify approved summary"
SUMMARY_APPROVED_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/summary?summaryWindowDays=${SUMMARY_WINDOW_DAYS}&trendWindowDays=1&trendWindowDays=7&trendWindowDays=30" "${SUMMARY_APPROVED_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${SUMMARY_APPROVED_STATUS}" "approved summary" "${SUMMARY_APPROVED_RESPONSE}"
assert_approved_tuple "${SUMMARY_APPROVED_RESPONSE}" >/dev/null

smoke_print_step "verify approved breakdowns"
BREAKDOWN_APPROVED_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/recommendation-breakdowns?summaryWindowDays=${SUMMARY_WINDOW_DAYS}&limit=${BREAKDOWN_LIMIT}" "${BREAKDOWN_APPROVED_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${BREAKDOWN_APPROVED_STATUS}" "approved breakdowns" "${BREAKDOWN_APPROVED_RESPONSE}"
assert_approved_tuple "${BREAKDOWN_APPROVED_RESPONSE}" >/dev/null

smoke_print_step "clear explicit promotion approval record"
CLEAR_STATUS="$(
  smoke_http_status DELETE "${APP_BASE_URL}/api/admin/dashboard/recommendation-review-gate/promotion-approval-record" "${CLEAR_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${CLEAR_STATUS}" "approval record clear" "${CLEAR_RESPONSE}"
assert_clear_response "${CLEAR_RESPONSE}" >/dev/null
WRITE_EXECUTED=false

smoke_print_step "verify cleared summary"
SUMMARY_CLEARED_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/summary?summaryWindowDays=${SUMMARY_WINDOW_DAYS}&trendWindowDays=1&trendWindowDays=7&trendWindowDays=30" "${SUMMARY_CLEARED_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${SUMMARY_CLEARED_STATUS}" "cleared summary" "${SUMMARY_CLEARED_RESPONSE}"
assert_tuple_equals \
  "${SUMMARY_CLEARED_RESPONSE}" \
  "${BASELINE_SUMMARY_TUPLE[0]}" \
  "${BASELINE_SUMMARY_TUPLE[1]}" \
  "${BASELINE_SUMMARY_TUPLE[2]}" \
  "${BASELINE_SUMMARY_TUPLE[3]}" >/dev/null

smoke_print_step "verify cleared breakdowns"
BREAKDOWN_CLEARED_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/recommendation-breakdowns?summaryWindowDays=${SUMMARY_WINDOW_DAYS}&limit=${BREAKDOWN_LIMIT}" "${BREAKDOWN_CLEARED_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${BREAKDOWN_CLEARED_STATUS}" "cleared breakdowns" "${BREAKDOWN_CLEARED_RESPONSE}"
assert_tuple_equals \
  "${BREAKDOWN_CLEARED_RESPONSE}" \
  "${BASELINE_BREAKDOWN_TUPLE[0]}" \
  "${BASELINE_BREAKDOWN_TUPLE[1]}" \
  "${BASELINE_BREAKDOWN_TUPLE[2]}" \
  "${BASELINE_BREAKDOWN_TUPLE[3]}" >/dev/null

echo
echo "recommendation review gate promotion approval record smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "admin_email=${ADMIN_EMAIL}"
echo "admin_roles=${ADMIN_ROLES}"
echo "approval_record_key=RECENT_WINDOW_BOUNDED_PROMOTION_REVIEW"
echo "approval_record_actor=${WRITE_VALUES[0]}"
echo "approval_record_approved_at=${WRITE_VALUES[1]}"
echo "baseline_summary_approval_decision_status=${BASELINE_SUMMARY_TUPLE[0]}"
echo "baseline_summary_approval_record_status=${BASELINE_SUMMARY_TUPLE[1]}"
echo "baseline_summary_review_run_status=${BASELINE_SUMMARY_TUPLE[2]}"
echo "baseline_summary_write_status=${BASELINE_SUMMARY_TUPLE[3]}"
echo "approved_summary_approval_decision_status=APPROVED_FOR_BOUNDED_PROMOTION_REVIEW"
echo "approved_summary_write_status=BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE_COMPLETED"
echo "cleared_summary_approval_decision_status=${BASELINE_SUMMARY_TUPLE[0]}"
echo "cleared_summary_write_status=${BASELINE_SUMMARY_TUPLE[3]}"
