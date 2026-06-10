#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
APP_CONTAINER_NAME="${APP_CONTAINER_NAME:-youth-welfare-app}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"

smoke_resolve_admin_credentials "${ROOT_DIR}"
smoke_resolve_admin_access_token "${ROOT_DIR}"
: "${ADMIN_EMAIL:?ADMIN_EMAIL is empty; export ADMIN_EMAIL or set SECURITY_ADMIN_EMAILS/.env or /tmp/youth-welfare-admin-smoke-email}"
if [[ -z "${ADMIN_ACCESS_TOKEN:-}" ]]; then
  : "${ADMIN_PASSWORD:?ADMIN_PASSWORD is empty; export ADMIN_PASSWORD or set /tmp/youth-welfare-admin-smoke-password}"
fi
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS:-14}"
COLLECT_LIMIT="${COLLECT_LIMIT:-5}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/admin-login.json"
COLLECT_RESPONSE="${ARTIFACT_DIR}/collect-failures.json"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"

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

assert_collect_contract() {
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
assert isinstance(data["failedJobsInWindow"], int), "failedJobsInWindow must be int"
assert isinstance(data["partialSuccessJobsInWindow"], int), "partialSuccessJobsInWindow must be int"

job_breakdowns = data["jobBreakdowns"]
streaks = data["currentJobStreaks"]
circuit_statuses = data["circuitStatuses"]
recent_samples = data["recentSamples"]
error_code_breakdowns = data["errorCodeBreakdowns"]
lanes = data["collectSourceLanes"]

assert isinstance(job_breakdowns, list), "jobBreakdowns must be list"
assert isinstance(streaks, list), "currentJobStreaks must be list"
assert isinstance(circuit_statuses, list), "circuitStatuses must be list"
assert isinstance(recent_samples, list), "recentSamples must be list"
assert isinstance(error_code_breakdowns, list), "errorCodeBreakdowns must be list"
assert isinstance(lanes, list), "collectSourceLanes must be list"
assert len(job_breakdowns) <= expected_limit, "jobBreakdowns exceeds limit"
assert len(streaks) <= expected_limit, "currentJobStreaks exceeds limit"
assert len(recent_samples) <= expected_limit, "recentSamples exceeds limit"
assert len(error_code_breakdowns) <= expected_limit, "errorCodeBreakdowns exceeds limit"
assert len(lanes) >= 4, "collectSourceLanes unexpectedly short"

lane_keys = set()
for lane in lanes:
    lane_key = lane["laneKey"]
    lane_keys.add(lane_key)
    assert lane["executionMode"] in {"SCHEDULED", "ROTATION", "MANUAL"}, f"unexpected executionMode: {lane['executionMode']}"
    assert lane["laneType"] in {"SNAPSHOT", "DETAIL", "ENRICHMENT", "MAINTENANCE"}, f"unexpected laneType: {lane['laneType']}"
    assert isinstance(lane["label"], str) and lane["label"], "label missing"
    assert isinstance(lane["resourceProfile"], str) and lane["resourceProfile"], "resourceProfile missing"
    assert isinstance(lane["governanceReason"], str) and lane["governanceReason"], "governanceReason missing"
    assert isinstance(lane["configEntries"], list), "configEntries must be list"
    for entry in lane["configEntries"]:
        assert isinstance(entry["label"], str) and entry["label"], "configEntries.label missing"
        assert isinstance(entry["value"], str) and entry["value"], "configEntries.value missing"
    latest = lane.get("latestRun")
    if latest is not None:
        assert isinstance(latest["status"], str) and latest["status"], "latestRun.status missing"
        for key in ("requestedCount", "savedCount", "skippedCount", "filteredCount", "failedCount"):
            assert isinstance(latest[key], int), f"latestRun.{key} must be int"

required_lane_keys = {
    "YOUTH",
    "BOKJIRO_CENTRAL",
    "BOKJIRO_LOCAL",
    "BOKJIRO_DETAIL",
    "GOV24",
    "YOUTH_DETAILS",
}
assert required_lane_keys.issubset(lane_keys), f"missing lane keys: {sorted(required_lane_keys - lane_keys)}"

open_collect_circuits = sum(1 for circuit in circuit_statuses if circuit["open"] is True)
lane_count = len(lanes)

print(data["generatedAt"])
print(data["failedJobsInWindow"])
print(data["partialSuccessJobsInWindow"])
print(open_collect_circuits)
print(lane_count)
print(",".join(sorted(lane_keys)))
PY
}

smoke_require_command curl
smoke_require_command python3

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

CONTAINER_ADMIN_ALLOWLIST="$(extract_container_admin_allowlist)"

if [[ -n "${ADMIN_ACCESS_TOKEN:-}" ]]; then
  smoke_print_step "admin access token reuse (${ADMIN_EMAIL})"
  ADMIN_TOKEN="${ADMIN_ACCESS_TOKEN}"
  ADMIN_ROLES="$(smoke_extract_jwt_roles_from_token "${ADMIN_TOKEN}")"
else
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
fi

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

smoke_print_step "collect failures"
COLLECT_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/collect-failures?summaryWindowDays=${SUMMARY_WINDOW_DAYS}&limit=${COLLECT_LIMIT}" "${COLLECT_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${COLLECT_STATUS}" "collect failures" "${COLLECT_RESPONSE}"

COLLECT_ASSERT_OUTPUT="$(
  assert_collect_contract "${COLLECT_RESPONSE}" "${SUMMARY_WINDOW_DAYS}" "${COLLECT_LIMIT}"
)"
mapfile -t COLLECT_VALUES <<< "${COLLECT_ASSERT_OUTPUT}"

echo
echo "admin collect failures smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "admin_email=${ADMIN_EMAIL}"
echo "generated_at=${COLLECT_VALUES[0]}"
echo "failed_jobs_in_window=${COLLECT_VALUES[1]}"
echo "partial_success_jobs_in_window=${COLLECT_VALUES[2]}"
echo "open_collect_circuits=${COLLECT_VALUES[3]}"
echo "lane_count=${COLLECT_VALUES[4]}"
echo "lane_keys=${COLLECT_VALUES[5]}"
echo "artifact_dir=${ARTIFACT_DIR}"
