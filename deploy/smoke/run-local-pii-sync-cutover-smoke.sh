#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BASE_COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"
INNER_SMOKE_SCRIPT="${ROOT_DIR}/deploy/smoke/user-pii-sync-cutover-smoke.sh"
REPO_ENV_FILE="${ROOT_DIR}/.env"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
SMOKE_RESET_DB="${SMOKE_RESET_DB:-false}"
SMOKE_BUILD_APP="${SMOKE_BUILD_APP:-true}"
APP_HEALTH_TIMEOUT_SECONDS="${APP_HEALTH_TIMEOUT_SECONDS:-120}"
APPLY_PII_SYNC_QUEUE_MIGRATION="${APPLY_PII_SYNC_QUEUE_MIGRATION:-true}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing required command: $1" >&2
    exit 1
  fi
}

require_command docker
require_command curl
require_command python3

TEMP_ENV_CREATED="false"
OVERRIDE_COMPOSE_FILE="$(mktemp)"

yaml_quote() {
  local value="$1"
  value="${value//\'/\'\'}"
  printf "'%s'" "${value}"
}

cleanup() {
  rm -f "${OVERRIDE_COMPOSE_FILE}"
  if [[ "${TEMP_ENV_CREATED}" == "true" ]]; then
    rm -f "${REPO_ENV_FILE}"
  fi
}
trap cleanup EXIT

if [[ ! -f "${REPO_ENV_FILE}" ]]; then
  : > "${REPO_ENV_FILE}"
  TEMP_ENV_CREATED="true"
fi

export DB_PASSWORD="${DB_PASSWORD:-smoke-db-password-2026!}"
export DB_USERNAME="${DB_USERNAME:-app_core_rw}"
export DB_MIGRATION_USERNAME="${DB_MIGRATION_USERNAME:-migration_admin}"
export DB_MIGRATION_PASSWORD="${DB_MIGRATION_PASSWORD:-${DB_PASSWORD}}"
export APP_PII_DB_URL="${APP_PII_DB_URL:-jdbc:mysql://db:3306/youth_welfare_pii?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul}"
export NOTIFICATION_PII_DB_URL="${NOTIFICATION_PII_DB_URL:-${APP_PII_DB_URL}}"
export DB_APP_PII_USERNAME="${DB_APP_PII_USERNAME:-app_pii_rw}"
export DB_APP_PII_PASSWORD="${DB_APP_PII_PASSWORD:-${DB_PASSWORD}}"
export DB_NOTIFICATION_PII_RO_USERNAME="${DB_NOTIFICATION_PII_RO_USERNAME:-notification_pii_ro}"
export DB_NOTIFICATION_PII_RO_PASSWORD="${DB_NOTIFICATION_PII_RO_PASSWORD:-${DB_PASSWORD}}"
export JWT_SECRET="${JWT_SECRET:-smoke-local-jwt-secret-must-be-at-least-32-bytes!!}"
export AES_SECRET_KEY="${AES_SECRET_KEY:-smoke-aes-256-secret-key-32bytes}"
export OPENAI_API_KEY="${OPENAI_API_KEY:-sk-smoke-placeholder}"
export GMAIL_USERNAME="${GMAIL_USERNAME:-smoke@example.com}"
export GMAIL_PASSWORD="${GMAIL_PASSWORD:-smoke-app-password}"
export YOUTH_API_KEY="${YOUTH_API_KEY:-smoke-youth-api-key}"
export BOKJIRO_API_KEY="${BOKJIRO_API_KEY:-smoke-bokjiro-api-key}"
export SECURITY_ADMIN_EMAILS="${SECURITY_ADMIN_EMAILS:-admin@example.com}"
export USER_PII_SYNC_RETRY_ENABLED="${USER_PII_SYNC_RETRY_ENABLED:-true}"
export USER_PII_SYNC_RETRY_BATCH_SIZE="${USER_PII_SYNC_RETRY_BATCH_SIZE:-100}"
export USER_PII_SYNC_RETRY_INITIAL_DELAY_MS="${USER_PII_SYNC_RETRY_INITIAL_DELAY_MS:-60000}"
export USER_PII_SYNC_RETRY_FIXED_DELAY_MS="${USER_PII_SYNC_RETRY_FIXED_DELAY_MS:-300000}"
export DB_QUERY_USERNAME="${DB_QUERY_USERNAME:-${DB_MIGRATION_USERNAME}}"
export DB_QUERY_PASSWORD="${DB_QUERY_PASSWORD:-${DB_MIGRATION_PASSWORD}}"

cat <<EOF > "${OVERRIDE_COMPOSE_FILE}"
services:
  app:
    environment:
      APP_BASE_URL: $(yaml_quote "${APP_BASE_URL}")
      APP_PII_DB_URL: $(yaml_quote "${APP_PII_DB_URL}")
      NOTIFICATION_PII_DB_URL: $(yaml_quote "${NOTIFICATION_PII_DB_URL}")
      JWT_SECRET: $(yaml_quote "${JWT_SECRET}")
      AES_SECRET_KEY: $(yaml_quote "${AES_SECRET_KEY}")
      OPENAI_API_KEY: $(yaml_quote "${OPENAI_API_KEY}")
      GMAIL_USERNAME: $(yaml_quote "${GMAIL_USERNAME}")
      GMAIL_PASSWORD: $(yaml_quote "${GMAIL_PASSWORD}")
      YOUTH_API_KEY: $(yaml_quote "${YOUTH_API_KEY}")
      BOKJIRO_API_KEY: $(yaml_quote "${BOKJIRO_API_KEY}")
      SECURITY_ADMIN_EMAILS: $(yaml_quote "${SECURITY_ADMIN_EMAILS}")
      USER_PII_SYNC_RETRY_ENABLED: $(yaml_quote "${USER_PII_SYNC_RETRY_ENABLED}")
      USER_PII_SYNC_RETRY_BATCH_SIZE: $(yaml_quote "${USER_PII_SYNC_RETRY_BATCH_SIZE}")
      USER_PII_SYNC_RETRY_INITIAL_DELAY_MS: $(yaml_quote "${USER_PII_SYNC_RETRY_INITIAL_DELAY_MS}")
      USER_PII_SYNC_RETRY_FIXED_DELAY_MS: $(yaml_quote "${USER_PII_SYNC_RETRY_FIXED_DELAY_MS}")
EOF

wait_for_app_health() {
  local health_file
  local waited=0
  local sleep_seconds=2
  local status_code

  health_file="$(mktemp)"

  while (( waited < APP_HEALTH_TIMEOUT_SECONDS )); do
    status_code="$(curl -fsS -o "${health_file}" -w "%{http_code}" "${APP_BASE_URL}/actuator/health" 2>/dev/null || true)"
    if [[ "${status_code}" == "200" ]]; then
      if python3 - "${health_file}" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    data = json.load(fp)

sys.exit(0 if data.get("status") == "UP" else 1)
PY
      then
        echo "app health check passed"
        rm -f "${health_file}"
        return 0
      fi
    fi
    sleep "${sleep_seconds}"
    waited=$((waited + sleep_seconds))
  done

  echo "app health did not reach UP within ${APP_HEALTH_TIMEOUT_SECONDS}s: ${APP_BASE_URL}/actuator/health" >&2
  cat "${health_file}" >&2 || true
  rm -f "${health_file}"
  docker logs youth-welfare-app --tail 100 >&2 || true
  exit 1
}

if [[ "${SMOKE_RESET_DB}" == "true" ]]; then
  docker compose -f "${BASE_COMPOSE_FILE}" -f "${OVERRIDE_COMPOSE_FILE}" down -v --remove-orphans
fi

if [[ "${SMOKE_BUILD_APP}" == "true" ]]; then
  docker compose -f "${BASE_COMPOSE_FILE}" -f "${OVERRIDE_COMPOSE_FILE}" up -d --build db redis app
else
  docker compose -f "${BASE_COMPOSE_FILE}" -f "${OVERRIDE_COMPOSE_FILE}" up -d db redis app
fi

wait_for_app_health

APP_BASE_URL="${APP_BASE_URL}" \
APP_HEALTH_TIMEOUT_SECONDS="${APP_HEALTH_TIMEOUT_SECONDS}" \
APPLY_PII_SYNC_QUEUE_MIGRATION="${APPLY_PII_SYNC_QUEUE_MIGRATION}" \
DB_MIGRATION_USERNAME="${DB_MIGRATION_USERNAME}" \
DB_MIGRATION_PASSWORD="${DB_MIGRATION_PASSWORD}" \
DB_QUERY_USERNAME="${DB_QUERY_USERNAME}" \
DB_QUERY_PASSWORD="${DB_QUERY_PASSWORD}" \
"${INNER_SMOKE_SCRIPT}"
