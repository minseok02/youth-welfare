#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-${ROOT_DIR}/docker-compose.yml}"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env}"
SKIP_COMPOSE_CONFIG="${SKIP_COMPOSE_CONFIG:-false}"

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

require_non_empty() {
  local name="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    echo "missing required env: ${name}" >&2
    exit 1
  fi
}

assert_equals() {
  local name="$1"
  local actual="$2"
  local expected="$3"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "${name} must be '${expected}' but was '${actual}'" >&2
    exit 1
  fi
}

extract_database_name() {
  local name="$1"
  local jdbc_url="$2"
  local remainder database_name

  if [[ -z "${jdbc_url}" ]]; then
    echo "${name} must not be blank" >&2
    exit 1
  fi

  remainder="${jdbc_url#jdbc:mysql://}"
  if [[ "${remainder}" == "${jdbc_url}" ]]; then
    echo "${name} must be a jdbc:mysql:// URL: ${jdbc_url}" >&2
    exit 1
  fi

  if [[ "${remainder}" != */* ]]; then
    echo "${name} must contain host/database: ${jdbc_url}" >&2
    exit 1
  fi

  database_name="${remainder#*/}"
  database_name="${database_name%%\?*}"
  database_name="${database_name%%;*}"

  if [[ -z "${database_name}" ]]; then
    echo "${name} must contain database/schema name: ${jdbc_url}" >&2
    exit 1
  fi

  printf "%s\n" "${database_name}"
}

assert_database_name() {
  local name="$1"
  local jdbc_url="$2"
  local expected="$3"
  local actual

  actual="$(extract_database_name "${name}" "${jdbc_url}")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "${name} must point to '${expected}' but was '${actual}': ${jdbc_url}" >&2
    exit 1
  fi
}

validate_env() {
  require_non_empty DB_URL "${DB_URL:-}"
  require_non_empty DB_USERNAME "${DB_USERNAME:-}"
  require_non_empty DB_PASSWORD "${DB_PASSWORD:-}"
  require_non_empty APP_PII_DB_URL "${APP_PII_DB_URL:-}"
  require_non_empty NOTIFICATION_PII_DB_URL "${NOTIFICATION_PII_DB_URL:-}"
  require_non_empty DB_MIGRATION_USERNAME "${DB_MIGRATION_USERNAME:-}"
  require_non_empty DB_MIGRATION_PASSWORD "${DB_MIGRATION_PASSWORD:-}"
  require_non_empty DB_APP_PII_USERNAME "${DB_APP_PII_USERNAME:-}"
  require_non_empty DB_APP_PII_PASSWORD "${DB_APP_PII_PASSWORD:-}"
  require_non_empty DB_NOTIFICATION_PII_RO_USERNAME "${DB_NOTIFICATION_PII_RO_USERNAME:-}"
  require_non_empty DB_NOTIFICATION_PII_RO_PASSWORD "${DB_NOTIFICATION_PII_RO_PASSWORD:-}"

  assert_equals DB_USERNAME "${DB_USERNAME}" "app_core_rw"
  assert_equals DB_MIGRATION_USERNAME "${DB_MIGRATION_USERNAME}" "migration_admin"
  assert_equals DB_APP_PII_USERNAME "${DB_APP_PII_USERNAME}" "app_pii_rw"
  assert_equals DB_NOTIFICATION_PII_RO_USERNAME "${DB_NOTIFICATION_PII_RO_USERNAME}" "notification_pii_ro"

  assert_database_name DB_URL "${DB_URL}" "youth_welfare"
  assert_database_name APP_PII_DB_URL "${APP_PII_DB_URL}" "youth_welfare_pii"
  assert_database_name NOTIFICATION_PII_DB_URL "${NOTIFICATION_PII_DB_URL}" "youth_welfare_pii"

  if [[ "${DB_USERNAME}" == "${DB_APP_PII_USERNAME}" || "${DB_USERNAME}" == "${DB_NOTIFICATION_PII_RO_USERNAME}" ]]; then
    echo "runtime secondary datasource usernames must not collapse to DB_USERNAME" >&2
    exit 1
  fi

  if [[ "${APP_PII_DB_URL}" == "${DB_URL}" || "${NOTIFICATION_PII_DB_URL}" == "${DB_URL}" ]]; then
    echo "secondary datasource URLs must not reuse DB_URL exactly; they must point to youth_welfare_pii" >&2
    exit 1
  fi
}

validate_compose_config() {
  if [[ "${SKIP_COMPOSE_CONFIG}" == "true" ]]; then
    return 0
  fi

  if ! command -v docker >/dev/null 2>&1; then
    echo "docker not found; skipping docker compose config preflight" >&2
    return 0
  fi

  if [[ ! -f "${COMPOSE_FILE}" ]]; then
    echo "compose file not found: ${COMPOSE_FILE}" >&2
    exit 1
  fi

  if [[ -n "${ENV_FILE}" ]]; then
    docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" config >/dev/null
  else
    docker compose -f "${COMPOSE_FILE}" config >/dev/null
  fi
}

validate_env
validate_compose_config

echo "runtime cutover env preflight passed"
