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
BREAKDOWN_RESPONSE="${ARTIFACT_DIR}/recommendation-breakdowns.json"

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

extract_container_admin_allowlist() {
  if ! command -v docker >/dev/null 2>&1; then
    return 0
  fi

  if ! docker ps --format '{{.Names}}' | grep -Fxq "${APP_CONTAINER_NAME}"; then
    return 0
  fi

  docker exec "${APP_CONTAINER_NAME}" /bin/sh -lc 'printf "%s" "${SECURITY_ADMIN_EMAILS:-}"' 2>/dev/null || true
}

assert_breakdown_contract() {
  local response_file="$1"
  local expected_summary_window_days="$2"
  local expected_limit="$3"
  python3 - "$response_file" "$expected_summary_window_days" "$expected_limit" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

data = payload["data"]
expected_window = int(sys.argv[2])
expected_limit = int(sys.argv[3])

assert data["generatedAt"], "generatedAt missing"
assert data["windowDays"] == expected_window, "unexpected windowDays"
assert isinstance(data["sentLogsInWindow"], int), "sentLogsInWindow must be int"
assert isinstance(data["clickedLogsInWindow"], int), "clickedLogsInWindow must be int"
assert isinstance(data["fallbackLogsInWindow"], int), "fallbackLogsInWindow must be int"

traffic_mix = data["trafficMixInWindow"]
for key in (
    "exampleLogsInWindow",
    "boundedLocalLogsInWindow",
    "realNonExampleLogsInWindow",
    "exampleUsersInWindow",
    "boundedLocalUsersInWindow",
    "realNonExampleUsersInWindow",
    "exampleClickedUsersInWindow",
    "boundedLocalClickedUsersInWindow",
    "realNonExampleClickedUsersInWindow",
):
    assert isinstance(traffic_mix[key], int), f"{key} must be int"

for collection_key in (
    "sourceBreakdowns",
    "categoryBreakdowns",
    "weightBreakdowns",
    "recentFallbackSamples",
    "recentClickedSamples",
    "repeatExposureGroups",
):
    assert isinstance(data[collection_key], list), f"{collection_key} must be list"
    assert len(data[collection_key]) <= expected_limit, f"{collection_key} exceeds limit"

valid_cohorts = {"EXAMPLE", "BOUNDED_LOCAL", "REAL_NON_EXAMPLE"}
non_example_present = (
    traffic_mix["boundedLocalUsersInWindow"] > 0
    or traffic_mix["realNonExampleUsersInWindow"] > 0
)

fallback_cohort = "NONE"
clicked_cohort = "NONE"
repeat_cohort = "NONE"

if data["recentFallbackSamples"]:
    first = data["recentFallbackSamples"][0]
    assert first["fallback"] is True, "recentFallbackSamples[0].fallback must be true"
    assert first["userCohort"] in valid_cohorts, "recentFallbackSamples[0].userCohort invalid"
    if not non_example_present:
        assert first["userCohort"] == "EXAMPLE", "fallback sample should be EXAMPLE when non-example cohort is absent"
    fallback_cohort = first["userCohort"]

if data["recentClickedSamples"]:
    first = data["recentClickedSamples"][0]
    assert first["clicked"] is True, "recentClickedSamples[0].clicked must be true"
    assert first["userCohort"] in valid_cohorts, "recentClickedSamples[0].userCohort invalid"
    if not non_example_present:
        assert first["userCohort"] == "EXAMPLE", "clicked sample should be EXAMPLE when non-example cohort is absent"
    clicked_cohort = first["userCohort"]

if data["repeatExposureGroups"]:
    first = data["repeatExposureGroups"][0]
    assert first["exposureCount"] > 1, "repeatExposureGroups[0].exposureCount must be > 1"
    assert first["userCohort"] in valid_cohorts, "repeatExposureGroups[0].userCohort invalid"
    if not non_example_present:
        assert first["userCohort"] == "EXAMPLE", "repeat exposure sample should be EXAMPLE when non-example cohort is absent"
    repeat_cohort = first["userCohort"]

print(data["generatedAt"])
print(data["sentLogsInWindow"])
print(data["clickedLogsInWindow"])
print(data["fallbackLogsInWindow"])
print(traffic_mix["exampleLogsInWindow"])
print(traffic_mix["boundedLocalUsersInWindow"])
print(traffic_mix["realNonExampleUsersInWindow"])
print(fallback_cohort)
print(clicked_cohort)
print(repeat_cohort)
print(len(data["sourceBreakdowns"]))
print(len(data["categoryBreakdowns"]))
print(len(data["weightBreakdowns"]))
PY
}

smoke_require_command curl
smoke_require_command python3

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

CONTAINER_ADMIN_ALLOWLIST="$(extract_container_admin_allowlist)"

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
  if [[ -n "${CONTAINER_ADMIN_ALLOWLIST}" ]]; then
    echo "current ${APP_CONTAINER_NAME} SECURITY_ADMIN_EMAILS=${CONTAINER_ADMIN_ALLOWLIST}" >&2
  else
    echo "current ${APP_CONTAINER_NAME} SECURITY_ADMIN_EMAILS is empty or container was not inspectable" >&2
  fi
  echo "for local docker validation, restart app with:" >&2
  echo "  SECURITY_ADMIN_EMAILS=${ADMIN_EMAIL} docker compose up -d --force-recreate app" >&2
  exit 1
fi

smoke_print_step "recommendation breakdowns"
BREAKDOWN_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/recommendation-breakdowns?summaryWindowDays=${SUMMARY_WINDOW_DAYS}&limit=${BREAKDOWN_LIMIT}" "${BREAKDOWN_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${BREAKDOWN_STATUS}" "recommendation breakdowns" "${BREAKDOWN_RESPONSE}"

BREAKDOWN_ASSERT_OUTPUT="$(
  assert_breakdown_contract "${BREAKDOWN_RESPONSE}" "${SUMMARY_WINDOW_DAYS}" "${BREAKDOWN_LIMIT}"
)"
mapfile -t BREAKDOWN_VALUES <<< "${BREAKDOWN_ASSERT_OUTPUT}"

echo
echo "admin recommendation breakdowns smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "admin_email=${ADMIN_EMAIL}"
echo "admin_roles=${ADMIN_ROLES}"
echo "generated_at=${BREAKDOWN_VALUES[0]}"
echo "sent_logs_in_window=${BREAKDOWN_VALUES[1]}"
echo "clicked_logs_in_window=${BREAKDOWN_VALUES[2]}"
echo "fallback_logs_in_window=${BREAKDOWN_VALUES[3]}"
echo "example_logs_in_window=${BREAKDOWN_VALUES[4]}"
echo "bounded_local_users_in_window=${BREAKDOWN_VALUES[5]}"
echo "real_non_example_users_in_window=${BREAKDOWN_VALUES[6]}"
echo "recent_fallback_sample_user_cohort=${BREAKDOWN_VALUES[7]}"
echo "recent_clicked_sample_user_cohort=${BREAKDOWN_VALUES[8]}"
echo "repeat_exposure_user_cohort=${BREAKDOWN_VALUES[9]}"
echo "source_breakdown_count=${BREAKDOWN_VALUES[10]}"
echo "category_breakdown_count=${BREAKDOWN_VALUES[11]}"
echo "weight_breakdown_count=${BREAKDOWN_VALUES[12]}"
echo "summary_window_days=${SUMMARY_WINDOW_DAYS}"
echo "breakdown_limit=${BREAKDOWN_LIMIT}"
if [[ -n "${CONTAINER_ADMIN_ALLOWLIST}" ]]; then
  echo "container_security_admin_emails=${CONTAINER_ADMIN_ALLOWLIST}"
fi
