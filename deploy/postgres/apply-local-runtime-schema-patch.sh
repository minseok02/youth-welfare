#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PATCH_DIR="${PATCH_DIR:-${ROOT_DIR}/deploy/postgres/patches}"
DB_CONTAINER="${DB_CONTAINER:-youth-welfare-db}"
DB_NAME="${DB_NAME:-youth_welfare}"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker command not found" >&2
  exit 1
fi

if ! docker inspect "${DB_CONTAINER}" >/dev/null 2>&1; then
  echo "db container not found: ${DB_CONTAINER}" >&2
  exit 1
fi

if [[ "$(docker inspect -f '{{.State.Running}}' "${DB_CONTAINER}")" != "true" ]]; then
  echo "db container is not running: ${DB_CONTAINER}" >&2
  exit 1
fi

if [[ ! -d "${PATCH_DIR}" ]]; then
  echo "patch directory not found: ${PATCH_DIR}" >&2
  exit 1
fi

load_env_value() {
  local key="$1"
  local fallback="${2:-}"
  if [[ -n "${!key:-}" ]]; then
    printf '%s' "${!key}"
    return 0
  fi
  if [[ -f "${ENV_FILE}" ]]; then
    local line
    line="$(grep -E "^${key}=" "${ENV_FILE}" | tail -n 1 || true)"
    if [[ -n "${line}" ]]; then
      printf '%s' "${line#*=}"
      return 0
    fi
  fi
  printf '%s' "${fallback}"
}

ADMIN_RO_USERNAME="${DB_ADMIN_RO_USERNAME:-$(load_env_value DB_ADMIN_RO_USERNAME admin_dashboard_ro)}"
DB_PASSWORD_VALUE="$(load_env_value DB_PASSWORD '')"
ADMIN_RO_PASSWORD="${DB_ADMIN_RO_PASSWORD:-$(load_env_value DB_ADMIN_RO_PASSWORD "${DB_PASSWORD_VALUE}")}"
CLUSTER_AI_CLEANUP_USERNAME="${DB_CLUSTER_AI_CLEANUP_USERNAME:-$(load_env_value DB_CLUSTER_AI_CLEANUP_USERNAME cluster_ai_cleanup_rw)}"
CLUSTER_AI_CLEANUP_PASSWORD="${DB_CLUSTER_AI_CLEANUP_PASSWORD:-$(load_env_value DB_CLUSTER_AI_CLEANUP_PASSWORD "${DB_PASSWORD_VALUE}")}"
RECOMMENDATION_RETENTION_CLEANUP_USERNAME="${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME:-$(load_env_value DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME recommendation_retention_cleanup_rw)}"
RECOMMENDATION_RETENTION_CLEANUP_PASSWORD="${DB_RECOMMENDATION_RETENTION_CLEANUP_PASSWORD:-$(load_env_value DB_RECOMMENDATION_RETENTION_CLEANUP_PASSWORD "${DB_PASSWORD_VALUE}")}"
COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME="${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME:-$(load_env_value DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME collect_execution_lock_cleanup_rw)}"
COLLECT_EXECUTION_LOCK_CLEANUP_PASSWORD="${DB_COLLECT_EXECUTION_LOCK_CLEANUP_PASSWORD:-$(load_env_value DB_COLLECT_EXECUTION_LOCK_CLEANUP_PASSWORD "${DB_PASSWORD_VALUE}")}"
WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME="${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME:-$(load_env_value DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME web_push_subscription_cleanup_rw)}"
WEB_PUSH_SUBSCRIPTION_CLEANUP_PASSWORD="${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_PASSWORD:-$(load_env_value DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_PASSWORD "${DB_PASSWORD_VALUE}")}"
MIGRATION_USERNAME="${DB_MIGRATION_USERNAME:-$(load_env_value DB_MIGRATION_USERNAME migration_admin)}"

shopt -s nullglob
patches=("${PATCH_DIR}"/*.sql)
shopt -u nullglob

if [[ ${#patches[@]} -eq 0 ]]; then
  echo "no patch files found in ${PATCH_DIR}" >&2
  exit 1
fi

for patch in "${patches[@]}"; do
  echo "applying $(basename "${patch}")"
  docker exec -i "${DB_CONTAINER}" psql \
    -v ON_ERROR_STOP=1 \
    -v "admin_ro_username=${ADMIN_RO_USERNAME}" \
    -v "admin_ro_password=${ADMIN_RO_PASSWORD}" \
    -v "cluster_ai_cleanup_username=${CLUSTER_AI_CLEANUP_USERNAME}" \
    -v "cluster_ai_cleanup_password=${CLUSTER_AI_CLEANUP_PASSWORD}" \
    -v "recommendation_retention_cleanup_username=${RECOMMENDATION_RETENTION_CLEANUP_USERNAME}" \
    -v "recommendation_retention_cleanup_password=${RECOMMENDATION_RETENTION_CLEANUP_PASSWORD}" \
    -v "collect_execution_lock_cleanup_username=${COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME}" \
    -v "collect_execution_lock_cleanup_password=${COLLECT_EXECUTION_LOCK_CLEANUP_PASSWORD}" \
    -v "web_push_subscription_cleanup_username=${WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME}" \
    -v "web_push_subscription_cleanup_password=${WEB_PUSH_SUBSCRIPTION_CLEANUP_PASSWORD}" \
    -v "migration_username=${MIGRATION_USERNAME}" \
    -U postgres -d "${DB_NAME}" < "${patch}"
done

echo "local runtime schema patch applied"
