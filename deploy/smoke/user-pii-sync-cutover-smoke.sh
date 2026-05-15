#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"
MIGRATION_FILE="${ROOT_DIR}/backend/src/main/resources/db/migration/V2026_04_28_02__add_user_pii_sync_queue.sql"
ENV_FILE="${ENV_FILE:-}"
QUEUE_BOOTSTRAP_SQL_FILE="$(mktemp)"

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf "%s" "${value}"
}

unquote() {
  local value="$1"
  if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf "%s" "${value}"
}

load_env_file() {
  local line key value

  if [[ ! -f "${ENV_FILE}" ]]; then
    echo "env file not found: ${ENV_FILE}" >&2
    exit 1
  fi

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "$(trim "${line}")" ]] && continue
    [[ "$(trim "${line}")" == \#* ]] && continue

    if [[ "${line}" != *=* ]]; then
      continue
    fi

    key="$(trim "${line%%=*}")"
    value="${line#*=}"
    value="$(unquote "${value}")"

    if [[ "${key}" == export\ * ]]; then
      key="$(trim "${key#export }")"
    fi

    if [[ ! "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      continue
    fi

    if [[ -n "${!key+x}" ]]; then
      continue
    fi

    export "${key}=${value}"
  done < "${ENV_FILE}"
}

if [[ -n "${ENV_FILE}" ]]; then
  load_env_file
fi

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5433}"
DB_NAME="${DB_NAME:-youth_welfare}"
DB_CONTAINER_NAME="${DB_CONTAINER_NAME:-youth-welfare-db}"
REDIS_CONTAINER_NAME="${REDIS_CONTAINER_NAME:-youth-welfare-redis}"
APP_HEALTH_TIMEOUT_SECONDS="${APP_HEALTH_TIMEOUT_SECONDS:-45}"
DB_MIGRATION_USERNAME="${DB_MIGRATION_USERNAME:-}"
DB_MIGRATION_PASSWORD="${DB_MIGRATION_PASSWORD:-}"
DB_QUERY_USERNAME="${DB_QUERY_USERNAME:-${DB_MIGRATION_USERNAME:-${DB_USERNAME:-}}}"
DB_QUERY_PASSWORD="${DB_QUERY_PASSWORD:-${DB_MIGRATION_PASSWORD:-${DB_PASSWORD:-}}}"
APPLY_PII_SYNC_QUEUE_MIGRATION="${APPLY_PII_SYNC_QUEUE_MIGRATION:-false}"
SMOKE_ADMIN_ACCESS_TOKEN="${SMOKE_ADMIN_ACCESS_TOKEN:-}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-SmokePass123!}"
SMOKE_EMAIL="${SMOKE_EMAIL:-codex.pii.sync.$(date +%s)@example.com}"
SMOKE_NAME_BEFORE="${SMOKE_NAME_BEFORE:-동기화전}"
SMOKE_NAME_AFTER="${SMOKE_NAME_AFTER:-동기화후}"
SMOKE_TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-45}"

COOKIE_FILE="$(mktemp)"
SIGNUP_FILE="$(mktemp)"
LOGIN_FILE="$(mktemp)"
PROFILE_FILE="$(mktemp)"
STATUS_FILE="$(mktemp)"

cleanup() {
  rm -f "${COOKIE_FILE}" "${SIGNUP_FILE}" "${LOGIN_FILE}" "${PROFILE_FILE}" "${STATUS_FILE}" "${QUEUE_BOOTSTRAP_SQL_FILE}"
}
trap cleanup EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing required command: $1" >&2
    exit 1
  fi
}

require_non_empty() {
  local name="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    echo "missing required env: ${name}" >&2
    exit 1
  fi
}

require_command curl
require_command python3
require_non_empty DB_QUERY_USERNAME "${DB_QUERY_USERNAME}"
require_non_empty DB_QUERY_PASSWORD "${DB_QUERY_PASSWORD}"

db_exec() {
  local sql="$1"
  sql="${sql//SET NAMES utf8mb4;/}"
  smoke_db_query "${sql}"
}

json_read() {
  local body_file="$1"
  local expr="$2"
  python3 - "$body_file" "$expr" <<'PY'
import json
import sys

body_path = sys.argv[1]
expr = sys.argv[2].split(".")

with open(body_path, "r", encoding="utf-8") as fp:
    data = json.load(fp)

value = data
for key in expr:
    if key:
        value = value[key]

if isinstance(value, bool):
    print("true" if value else "false")
elif value is None:
    print("null")
else:
    print(value)
PY
}

app_health_is_up() {
  local body_file="$1"
  local status_code
  status_code="$(curl -fsS -o "${body_file}" -w "%{http_code}" "${APP_BASE_URL}/actuator/health" 2>/dev/null || true)"
  if [[ "${status_code}" != "200" ]]; then
    return 1
  fi
  [[ "$(json_read "${body_file}" "status")" == "UP" ]]
}

ensure_query_account_scope() {
  if ! db_exec "SELECT 1 FROM users LIMIT 1;" >/dev/null 2>&1; then
    echo "DB_QUERY account cannot read youth_welfare.users; use migration_admin or an explicit cross-schema DB_QUERY account" >&2
    exit 1
  fi

  if ! db_exec "SELECT 1 FROM youth_welfare_pii.user_pii LIMIT 1;" >/dev/null 2>&1; then
    echo "DB_QUERY account cannot read youth_welfare_pii.user_pii; after app_core_rw grant shrink use migration_admin or explicit DB_QUERY_*" >&2
    exit 1
  fi
}

http_status() {
  local method="$1"
  local url="$2"
  local body_file="$3"
  shift 3
  curl -sS -o "${body_file}" -w "%{http_code}" -X "${method}" "${url}" "$@"
}

assert_http_ok() {
  local status_code="$1"
  local body_file="$2"
  if [[ "${status_code}" != "200" ]]; then
    if grep -q '"code":"C002"' "${body_file}" 2>/dev/null; then
      echo "server returned C002; check AES_SECRET_KEY and app logs before rerunning smoke" >&2
    fi
    echo "unexpected HTTP status: ${status_code}" >&2
    cat "${body_file}" >&2
    exit 1
  fi
  if [[ "$(json_read "${body_file}" "success")" != "true" ]]; then
    echo "API returned success=false" >&2
    cat "${body_file}" >&2
    exit 1
  fi
}

wait_for_synced_queue() {
  local user_key="$1"
  local waited=0
  local sleep_seconds=2

  while (( waited < SMOKE_TIMEOUT_SECONDS )); do
    local status attempt_count last_error
    status="$(db_exec "SELECT COALESCE(status, '') FROM user_pii_sync_queue WHERE user_key = '${user_key}' LIMIT 1;")"
    attempt_count="$(db_exec "SELECT COALESCE(attempt_count, 0) FROM user_pii_sync_queue WHERE user_key = '${user_key}' LIMIT 1;")"
    last_error="$(db_exec "SELECT COALESCE(last_error, '') FROM user_pii_sync_queue WHERE user_key = '${user_key}' LIMIT 1;")"

    if [[ "${status}" == "SYNCED" ]]; then
      echo "queue synced: user_key=${user_key} attempt_count=${attempt_count}"
      return 0
    fi

    if [[ "${status}" == "FAILED" ]]; then
      echo "queue currently FAILED: user_key=${user_key} attempt_count=${attempt_count} last_error=${last_error}" >&2
    fi

    sleep "${sleep_seconds}"
    waited=$((waited + sleep_seconds))
  done

  echo "queue did not reach SYNCED within ${SMOKE_TIMEOUT_SECONDS}s for user_key=${user_key}" >&2
  db_exec "SELECT user_key, status, attempt_count, last_enqueued_at, last_attempt_at, last_synced_at, last_error FROM user_pii_sync_queue WHERE user_key = '${user_key}'" >&2
  exit 1
}

wait_for_app_health() {
  local health_file
  local waited=0
  local sleep_seconds=2

  health_file="$(mktemp)"

  while (( waited < APP_HEALTH_TIMEOUT_SECONDS )); do
    if app_health_is_up "${health_file}"; then
      echo "app health check passed"
      rm -f "${health_file}"
      return 0
    fi
    sleep "${sleep_seconds}"
    waited=$((waited + sleep_seconds))
  done

  echo "app health did not reach UP within ${APP_HEALTH_TIMEOUT_SECONDS}s: ${APP_BASE_URL}/actuator/health" >&2
  cat "${health_file}" >&2 || true
  rm -f "${health_file}"
  exit 1
}

queue_table_exists() {
  [[ "$(db_exec "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'user_pii_sync_queue';")" == "1" ]]
}

if [[ "${APPLY_PII_SYNC_QUEUE_MIGRATION}" == "true" ]]; then
  require_non_empty DB_MIGRATION_USERNAME "${DB_MIGRATION_USERNAME}"
  require_non_empty DB_MIGRATION_PASSWORD "${DB_MIGRATION_PASSWORD}"
  if queue_table_exists; then
    echo "queue table already exists; skip legacy migration replay: ${MIGRATION_FILE}"
  else
    cat <<'SQL' > "${QUEUE_BOOTSTRAP_SQL_FILE}"
CREATE TABLE IF NOT EXISTS user_pii_sync_queue (
    id               BIGSERIAL PRIMARY KEY,
    user_key         VARCHAR(32) NOT NULL,
    email_enc        VARCHAR(512),
    name_enc         VARCHAR(512),
    birth_date_enc   VARCHAR(128),
    phone_enc        VARCHAR(512),
    status           VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count    INTEGER NOT NULL DEFAULT 0,
    last_enqueued_at TIMESTAMP,
    last_attempt_at  TIMESTAMP,
    last_synced_at   TIMESTAMP,
    last_error       VARCHAR(500),
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_upsq_user_key UNIQUE (user_key)
);

CREATE INDEX IF NOT EXISTS idx_upsq_status_enqueued ON user_pii_sync_queue (status, last_enqueued_at);
SQL
    echo "applying PostgreSQL queue bootstrap: ${QUEUE_BOOTSTRAP_SQL_FILE}"
    DB_QUERY_USERNAME="${DB_MIGRATION_USERNAME}" \
    DB_QUERY_PASSWORD="${DB_MIGRATION_PASSWORD}" \
    smoke_db_apply_file "${QUEUE_BOOTSTRAP_SQL_FILE}"
  fi
fi

echo "waiting for app health"
wait_for_app_health

echo "verifying queue table exists"
ensure_query_account_scope
db_exec "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'user_pii_sync_queue';" | grep -qx "user_pii_sync_queue"

echo "signing up smoke user: ${SMOKE_EMAIL}"
smoke_seed_verified_email "${SMOKE_EMAIL}"
SIGNUP_STATUS="$(http_status POST "${APP_BASE_URL}/api/auth/signup" "${SIGNUP_FILE}" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"${SMOKE_EMAIL}\",
    \"password\": \"${SMOKE_PASSWORD}\",
    \"name\": \"${SMOKE_NAME_BEFORE}\",
    \"birthDate\": \"2000-05-10\",
    \"sido\": \"서울특별시\",
    \"sgg\": \"관악구\",
    \"incomeLevel\": 4,
    \"employmentStatus\": \"재학중\",
    \"householdType\": \"1인가구\"
  }")"
assert_http_ok "${SIGNUP_STATUS}" "${SIGNUP_FILE}"

echo "logging in smoke user"
LOGIN_STATUS="$(curl -sS -c "${COOKIE_FILE}" -o "${LOGIN_FILE}" -w "%{http_code}" \
  -X POST "${APP_BASE_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"${SMOKE_EMAIL}\",
    \"password\": \"${SMOKE_PASSWORD}\"
  }")"
assert_http_ok "${LOGIN_STATUS}" "${LOGIN_FILE}"
ACCESS_TOKEN="$(json_read "${LOGIN_FILE}" "data.accessToken")"
if [[ -z "${ACCESS_TOKEN}" || "${ACCESS_TOKEN}" == "null" ]]; then
  echo "missing accessToken from login response" >&2
  cat "${LOGIN_FILE}" >&2
  exit 1
fi

echo "updating profile to trigger request-path pii sync"
PROFILE_STATUS="$(http_status PUT "${APP_BASE_URL}/api/users/me" "${PROFILE_FILE}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"${SMOKE_NAME_AFTER}\",
    \"birthDate\": \"2000-05-10\",
    \"sido\": \"서울특별시\",
    \"sgg\": \"관악구\",
    \"incomeLevel\": 4,
    \"employmentStatus\": \"재학중\",
    \"householdType\": \"1인가구\",
    \"notificationYn\": true,
    \"notificationPeriod\": \"DAILY\",
    \"notificationMinScore\": 0.6,
    \"displayCount\": 10,
    \"interestFields\": [\"주거\", \"교육·직업훈련\"]
  }")"
assert_http_ok "${PROFILE_STATUS}" "${PROFILE_FILE}"

USER_KEY="$(db_exec "SELECT user_key FROM users WHERE email = '${SMOKE_EMAIL}' LIMIT 1;")"
require_non_empty USER_KEY "${USER_KEY}"
echo "resolved user_key=${USER_KEY}"

wait_for_synced_queue "${USER_KEY}"

AUTH_ROW_COUNT="$(db_exec "SELECT COUNT(*) FROM auth_users WHERE user_key = '${USER_KEY}';")"
PROFILE_ROW_COUNT="$(db_exec "SELECT COUNT(*) FROM user_profiles WHERE user_key = '${USER_KEY}';")"
PII_ROW_COUNT="$(db_exec "SELECT COUNT(*) FROM youth_welfare_pii.user_pii WHERE user_key = '${USER_KEY}';")"
QUEUE_STATUS="$(db_exec "SELECT status FROM user_pii_sync_queue WHERE user_key = '${USER_KEY}' LIMIT 1;")"
QUEUE_ATTEMPT_COUNT="$(db_exec "SELECT attempt_count FROM user_pii_sync_queue WHERE user_key = '${USER_KEY}' LIMIT 1;")"
PII_MISSING_COUNT="$(db_exec "SELECT COUNT(*) FROM youth_welfare_pii.user_pii WHERE user_key = '${USER_KEY}' AND (email_enc IS NULL OR email_enc = '' OR name_enc IS NULL OR name_enc = '' OR birth_date_enc IS NULL OR birth_date_enc = '');")"

if [[ "${AUTH_ROW_COUNT}" != "1" || "${PROFILE_ROW_COUNT}" != "1" || "${PII_ROW_COUNT}" != "1" ]]; then
  echo "split-table row count mismatch: auth=${AUTH_ROW_COUNT} profile=${PROFILE_ROW_COUNT} pii=${PII_ROW_COUNT}" >&2
  exit 1
fi

if [[ "${QUEUE_STATUS}" != "SYNCED" ]]; then
  echo "queue status mismatch: ${QUEUE_STATUS}" >&2
  exit 1
fi

if [[ "${PII_MISSING_COUNT}" != "0" ]]; then
  echo "pii encrypted fields still missing for user_key=${USER_KEY}" >&2
  exit 1
fi

if [[ -n "${SMOKE_ADMIN_ACCESS_TOKEN}" ]]; then
  echo "checking admin monitoring endpoint"
  STATUS_CODE="$(http_status GET "${APP_BASE_URL}/api/admin/users/pii-sync-status?failedSampleLimit=5" "${STATUS_FILE}" \
    -H "Authorization: Bearer ${SMOKE_ADMIN_ACCESS_TOKEN}")"
  assert_http_ok "${STATUS_CODE}" "${STATUS_FILE}"
fi

echo "withdrawing smoke user"
curl -sS -o /dev/null -X DELETE "${APP_BASE_URL}/api/users/me" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"password\": \"${SMOKE_PASSWORD}\"
  }" || true

echo "smoke success: user_key=${USER_KEY} queue_status=${QUEUE_STATUS} attempt_count=${QUEUE_ATTEMPT_COUNT}"
