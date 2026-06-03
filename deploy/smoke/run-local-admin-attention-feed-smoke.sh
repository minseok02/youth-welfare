#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
APP_CONTAINER_NAME="${APP_CONTAINER_NAME:-youth-welfare-app}"

smoke_resolve_admin_credentials "${ROOT_DIR}"
smoke_resolve_admin_access_token "${ROOT_DIR}"
: "${ADMIN_EMAIL:?ADMIN_EMAIL is empty; export ADMIN_EMAIL or set SECURITY_ADMIN_EMAILS/.env or /tmp/youth-welfare-admin-smoke-email}"
if [[ -z "${ADMIN_ACCESS_TOKEN:-}" ]]; then
  : "${ADMIN_PASSWORD:?ADMIN_PASSWORD is empty; export ADMIN_PASSWORD or set /tmp/youth-welfare-admin-smoke-password}"
fi

HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/admin-login.json"
ATTENTION_RESPONSE="${ARTIFACT_DIR}/attention-feed.json"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
mkdir -p "${ARTIFACT_DIR}"

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

assert_attention_contract() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

data = payload["data"]
items = data["items"]

assert data["generatedAt"], "generatedAt missing"
assert isinstance(data["itemCount"], int), "itemCount must be int"
assert isinstance(items, list), "items must be list"
assert data["itemCount"] == len(items), "itemCount mismatch"

warning_count = 0
keys = []
titles = []
for item in items:
    assert isinstance(item["key"], str) and item["key"], "item.key missing"
    assert isinstance(item["severity"], str) and item["severity"], "item.severity missing"
    assert isinstance(item["title"], str) and item["title"], "item.title missing"
    assert isinstance(item["message"], str) and item["message"], "item.message missing"
    assert isinstance(item["targetId"], str), "item.targetId must be string"
    assert isinstance(item["source"], str) and item["source"], "item.source missing"
    if item["severity"] == "warning":
        warning_count += 1
    keys.append(item["key"])
    titles.append(item["title"])

print(data["generatedAt"])
print(data["itemCount"])
print(warning_count)
print(",".join(keys))
print(" || ".join(titles))
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
  exit 1
fi

smoke_print_step "attention feed"
ATTENTION_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/attention-feed" "${ATTENTION_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${ATTENTION_STATUS}" "attention feed" "${ATTENTION_RESPONSE}"

ATTENTION_ASSERT_OUTPUT="$(
  assert_attention_contract "${ATTENTION_RESPONSE}"
)"
mapfile -t ATTENTION_VALUES <<< "${ATTENTION_ASSERT_OUTPUT}"

echo
echo "admin attention feed smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "admin_email=${ADMIN_EMAIL}"
echo "generated_at=${ATTENTION_VALUES[0]}"
echo "attention_item_count=${ATTENTION_VALUES[1]}"
echo "attention_warning_item_count=${ATTENTION_VALUES[2]}"
echo "attention_item_keys=${ATTENTION_VALUES[3]}"
echo "attention_item_titles=${ATTENTION_VALUES[4]}"
echo "attention_response=${ATTENTION_RESPONSE}"
echo "artifact_dir=${ARTIFACT_DIR}"
