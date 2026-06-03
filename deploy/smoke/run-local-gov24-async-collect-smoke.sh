#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-60}"
POLL_MAX_ATTEMPTS="${POLL_MAX_ATTEMPTS:-60}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-3}"
MAX_TRIGGER_DURATION_MS="${MAX_TRIGGER_DURATION_MS:-5000}"
SOURCE_KEY="${SOURCE_KEY:-gov24}"

smoke_resolve_admin_credentials "${ROOT_DIR}"
smoke_resolve_admin_access_token "${ROOT_DIR}"

if [[ -z "${ADMIN_ACCESS_TOKEN:-}" ]]; then
  : "${ADMIN_EMAIL:?ADMIN_EMAIL is empty; export ADMIN_EMAIL or set /tmp/youth-welfare-admin-smoke-email}"
  : "${ADMIN_PASSWORD:?ADMIN_PASSWORD is empty; export ADMIN_PASSWORD or set /tmp/youth-welfare-admin-smoke-password}"
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

HEALTH_RESPONSE="${TMP_DIR}/health.json"
HEALTH_STDERR="${TMP_DIR}/health.stderr"
LOGIN_RESPONSE="${TMP_DIR}/login.json"
TRIGGER_RESPONSE="${TMP_DIR}/trigger.json"
STATUS_RESPONSE="${TMP_DIR}/status.json"

metric() {
  local key="$1"
  local value="$2"
  printf 'METRIC %s=%s\n' "${key}" "${value}"
}

extract_json_field() {
  local response_file="$1"
  local python_expr="$2"
  python3 - "${response_file}" "${python_expr}" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

print(eval(sys.argv[2], {"payload": payload}))
PY
}

smoke_print_step "app health"
smoke_wait_for_health "${HEALTH_RETRY_COUNT}" 2 "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${HEALTH_STDERR}" >/dev/null

if [[ -n "${ADMIN_ACCESS_TOKEN:-}" ]]; then
  ADMIN_TOKEN="${ADMIN_ACCESS_TOKEN}"
else
  smoke_print_step "admin login (${ADMIN_EMAIL})"
  LOGIN_STATUS="$(
    smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${LOGIN_RESPONSE}" \
      -H 'Content-Type: application/json' \
      --data-binary @- <<JSON
{
  "email": "${ADMIN_EMAIL}",
  "password": "${ADMIN_PASSWORD}"
}
JSON
  )"
  smoke_assert_status 200 "${LOGIN_STATUS}" "admin login" "${LOGIN_RESPONSE}"
  ADMIN_TOKEN="$(python3 - "${LOGIN_RESPONSE}" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

print(payload["data"]["accessToken"])
PY
  )"
fi

smoke_print_step "async collect trigger"
trigger_start_ms="$(smoke_now_ms)"
TRIGGER_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/admin/collect/${SOURCE_KEY}/async" "${TRIGGER_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
trigger_end_ms="$(smoke_now_ms)"
trigger_duration_ms=$((trigger_end_ms - trigger_start_ms))
smoke_assert_status 202 "${TRIGGER_STATUS}" "async collect trigger" "${TRIGGER_RESPONSE}"

trigger_state="$(extract_json_field "${TRIGGER_RESPONSE}" "payload['data']['state']")"
trigger_active="$(extract_json_field "${TRIGGER_RESPONSE}" "str(payload['data']['active']).lower()")"

metric "gov24_async_collect_trigger_status" "${TRIGGER_STATUS}"
metric "gov24_async_collect_trigger_duration_ms" "${trigger_duration_ms}"
metric "gov24_async_collect_initial_state" "${trigger_state}"
metric "gov24_async_collect_initial_active" "${trigger_active}"

if (( trigger_duration_ms > MAX_TRIGGER_DURATION_MS )); then
  echo "async collect trigger too slow: duration_ms=${trigger_duration_ms} max=${MAX_TRIGGER_DURATION_MS}" >&2
  exit 1
fi

final_state=""
final_active=""
latest_log_id=""
latest_log_metadata=""
poll_attempt=0

smoke_print_step "async collect status poll"
for (( attempt=1; attempt<=POLL_MAX_ATTEMPTS; attempt++ )); do
  poll_attempt="${attempt}"
  STATUS_HTTP="$(
    smoke_http_status GET "${APP_BASE_URL}/api/admin/collect/${SOURCE_KEY}/async-status" "${STATUS_RESPONSE}" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}"
  )"
  smoke_assert_status 200 "${STATUS_HTTP}" "async collect status" "${STATUS_RESPONSE}"

  final_state="$(extract_json_field "${STATUS_RESPONSE}" "payload['data']['state']")"
  final_active="$(extract_json_field "${STATUS_RESPONSE}" "str(payload['data']['active']).lower()")"
  latest_log_id="$(extract_json_field "${STATUS_RESPONSE}" "payload['data']['latestLog']['id'] if payload['data'].get('latestLog') else ''")"
  latest_log_metadata="$(extract_json_field "${STATUS_RESPONSE}" "payload['data']['latestLog']['metadataJson'] if payload['data'].get('latestLog') else ''")"

  printf 'POLL attempt=%s state=%s active=%s latestLogId=%s\n' "${attempt}" "${final_state}" "${final_active}" "${latest_log_id}"

  if [[ "${final_state}" == "SUCCEEDED" || "${final_state}" == "FAILED" ]]; then
    break
  fi
  sleep "${POLL_INTERVAL_SECONDS}"
done

metric "gov24_async_collect_poll_attempts" "${poll_attempt}"
metric "gov24_async_collect_final_state" "${final_state}"
metric "gov24_async_collect_final_active" "${final_active}"
metric "gov24_async_collect_latest_log_id" "${latest_log_id}"

python3 - "${final_state}" "${final_active}" "${latest_log_metadata}" <<'PY'
import json
import sys

state = sys.argv[1]
active = sys.argv[2]
metadata = sys.argv[3]

if state != "SUCCEEDED":
    raise SystemExit(f"async collect did not succeed: state={state}")
if active != "false":
    raise SystemExit(f"async collect should be inactive after success: active={active}")

metadata_json = json.loads(metadata)
required_keys = {
    "chunkSize",
    "chunkPauseMs",
    "chunkCount",
    "elapsedMs",
    "totalCount",
    "staleDeletedCount",
}
missing = sorted(required_keys - metadata_json.keys())
if missing:
    raise SystemExit(f"async collect metadata missing keys: {missing}")
PY

echo "OK gov24_async_collect_smoke"
