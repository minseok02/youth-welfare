#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
APP_CONTAINER_NAME="${APP_CONTAINER_NAME:-youth-welfare-app}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-60}"
GOV24_SOURCE_ID="${GOV24_SOURCE_ID:-}"

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
COLLECT_RESPONSE="${TMP_DIR}/collect.json"
LOG_FILE="${TMP_DIR}/app.log"

metric() {
  local key="$1"
  local value="$2"
  printf 'METRIC %s=%s\n' "${key}" "${value}"
}

url_encode() {
  python3 - "$1" <<'PY'
import sys
from urllib.parse import quote

print(quote(sys.argv[1], safe=""))
PY
}

extract_collect_counts() {
  local response_file="$1"
  python3 - "${response_file}" <<'PY'
import json
import re
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

data = str(payload.get("data", ""))
pattern = re.compile(r"requested=(\d+)\s+saved=(\d+)\s+skipped=(\d+)\s+failed=(\d+)")
match = pattern.search(data)
if not match:
    raise SystemExit(f"collect response did not include counts: {data}")
print("\t".join(match.groups()))
PY
}

pick_gov24_source_id() {
  smoke_db_query "
    select ws.source_id
    from welfare_services ws
    join raw_api_payloads rap
      on rap.source_type = ws.source_type
     and rap.source_id = ws.source_id
     and rap.api_category = 'LIST'
    where ws.source_type = 'GOV24'
      and ws.source_id is not null
      and ws.source_id <> ''
    order by ws.updated_at desc nulls last, ws.id desc
    limit 1;
  "
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

if [[ -z "${GOV24_SOURCE_ID}" ]]; then
  GOV24_SOURCE_ID="$(pick_gov24_source_id)"
fi
if [[ -z "${GOV24_SOURCE_ID}" ]]; then
  echo "gov24 source id not found" >&2
  exit 1
fi

encoded_source_id="$(url_encode "${GOV24_SOURCE_ID}")"
log_since_utc="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

smoke_print_step "gov24 detail collect sourceId=${GOV24_SOURCE_ID}"
COLLECT_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/admin/collect/gov24-details?sourceId=${encoded_source_id}" "${COLLECT_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${COLLECT_STATUS}" "gov24 detail collect" "${COLLECT_RESPONSE}"

IFS=$'\t' read -r requested saved skipped failed <<<"$(extract_collect_counts "${COLLECT_RESPONSE}")"

embedding_warning_count="not_checked"
error_count="not_checked"
if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -Fxq "${APP_CONTAINER_NAME}"; then
  docker logs --since "${log_since_utc}" "${APP_CONTAINER_NAME}" > "${LOG_FILE}" 2>&1 || true
  embedding_warning_count="$(python3 - "${LOG_FILE}" <<'PY'
import sys

with open(sys.argv[1], "r", encoding="utf-8", errors="replace") as fp:
    print(sum(1 for line in fp if "batch embedding refresh skipped" in line))
PY
)"
  error_count="$(python3 - "${LOG_FILE}" <<'PY'
import sys

with open(sys.argv[1], "r", encoding="utf-8", errors="replace") as fp:
    print(sum(1 for line in fp if "ERROR" in line or "Exception" in line))
PY
)"
fi

metric "gov24_collect_embedding_boundary_status" "${COLLECT_STATUS}"
metric "gov24_collect_embedding_boundary_source_id" "${GOV24_SOURCE_ID}"
metric "gov24_collect_embedding_boundary_requested" "${requested}"
metric "gov24_collect_embedding_boundary_saved" "${saved}"
metric "gov24_collect_embedding_boundary_skipped" "${skipped}"
metric "gov24_collect_embedding_boundary_failed" "${failed}"
metric "gov24_collect_embedding_boundary_warning_count" "${embedding_warning_count}"
metric "gov24_collect_embedding_boundary_error_count" "${error_count}"

python3 - "${requested}" "${saved}" "${skipped}" "${failed}" <<'PY'
import sys

requested = int(sys.argv[1])
saved = int(sys.argv[2])
skipped = int(sys.argv[3])
failed = int(sys.argv[4])

if requested != 1:
    raise SystemExit(f"gov24 detail collect should request exactly one source: requested={requested}")
if saved != 1:
    raise SystemExit(f"gov24 detail collect should save exactly one source: saved={saved}")
if skipped != 0 or failed != 0:
    raise SystemExit(f"gov24 detail collect should not skip/fail: skipped={skipped} failed={failed}")
PY

echo "OK gov24_collect_embedding_boundary_smoke"
