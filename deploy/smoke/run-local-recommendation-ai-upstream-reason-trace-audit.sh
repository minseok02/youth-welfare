#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_CONTAINER_NAME="${APP_CONTAINER_NAME:-youth-welfare-app}"
TOP_REFRESH_LIMIT="${TOP_REFRESH_LIMIT:-20}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"

normalize_bool() {
  local value="${1,,}"
  case "${value}" in
    true|false) printf '%s' "${value}" ;;
    *)
      echo "unsupported boolean value: ${1}" >&2
      exit 1
      ;;
  esac
}

KEEP_ARTIFACTS="$(normalize_bool "${KEEP_ARTIFACTS}")"
smoke_require_command docker

LOG_SINCE_UTC="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"

APP_CONTAINER_NAME="${APP_CONTAINER_NAME}" \
TOP_REFRESH_LIMIT="${TOP_REFRESH_LIMIT}" \
KEEP_ARTIFACTS="${KEEP_ARTIFACTS}" \
APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}" \
APP_HEALTH_URL="${APP_HEALTH_URL:-}" \
TARGET_USER_KEY="${TARGET_USER_KEY:-}" \
USER_EMAIL="${USER_EMAIL:-}" \
USER_PASSWORD="${USER_PASSWORD:-}" \
USER_ACCESS_TOKEN="${USER_ACCESS_TOKEN:-}" \
ADMIN_EMAIL="${ADMIN_EMAIL:-}" \
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}" \
bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-reason-coverage-audit.sh"

printf '\n[UPSTREAM REASON COVERAGE LOGS]\n'
docker logs "${APP_CONTAINER_NAME}" --since "${LOG_SINCE_UTC}" 2>&1 \
  | grep -F "[RealtimeAiGateway][reason-coverage]" \
  | tail -n 10 || true
