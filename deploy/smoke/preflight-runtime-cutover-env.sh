#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-${ROOT_DIR}/docker-compose.yml}"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env}"
SKIP_COMPOSE_CONFIG="${SKIP_COMPOSE_CONFIG:-false}"
PRINT_SUMMARY="${PRINT_SUMMARY:-false}"
COMPOSE_CONFIG_STATUS="not-run"

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

RECOMMENDATION_REVIEW_GATE_COMMAND_DB_URL="${RECOMMENDATION_REVIEW_GATE_COMMAND_DB_URL:-${DB_URL:-}}"
DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME="${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME:-recommendation_review_gate_command_rw}"
DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD="${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD:-${DB_PASSWORD:-}}"
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

extract_jdbc_kind() {
  local name="$1"
  local jdbc_url="$2"

  if [[ -z "${jdbc_url}" ]]; then
    echo "${name} must not be blank" >&2
    exit 1
  fi

  if [[ "${jdbc_url}" == jdbc:postgresql://* ]]; then
    printf "postgresql\n"
    return 0
  fi

  echo "${name} must be a jdbc:postgresql:// URL: ${jdbc_url}" >&2
  exit 1
}

extract_database_name() {
  local name="$1"
  local jdbc_url="$2"
  local remainder database_name jdbc_kind

  jdbc_kind="$(extract_jdbc_kind "${name}" "${jdbc_url}")"
  remainder="${jdbc_url#jdbc:postgresql://}"

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

extract_host_port() {
  local name="$1"
  local jdbc_url="$2"
  local remainder host_port jdbc_kind

  jdbc_kind="$(extract_jdbc_kind "${name}" "${jdbc_url}")"
  remainder="${jdbc_url#jdbc:postgresql://}"

  if [[ "${remainder}" != */* ]]; then
    echo "${name} must contain host/database: ${jdbc_url}" >&2
    exit 1
  fi

  host_port="${remainder%%/*}"
  if [[ -z "${host_port}" ]]; then
    echo "${name} must contain host/database: ${jdbc_url}" >&2
    exit 1
  fi

  printf "%s\n" "${host_port}"
}

extract_postgres_current_schema() {
  local jdbc_url="$1"
  local query_string current_schema

  query_string="${jdbc_url#*\?}"
  if [[ "${query_string}" == "${jdbc_url}" ]]; then
    printf "\n"
    return 0
  fi

  current_schema="$(printf "%s" "${query_string}" | tr '&' '\n' | awk -F= '$1=="currentSchema" {print $2; exit}')"
  printf "%s\n" "${current_schema}"
}

assert_runtime_core_target() {
  local name="$1"
  local jdbc_url="$2"
  local jdbc_kind database_name current_schema

  jdbc_kind="$(extract_jdbc_kind "${name}" "${jdbc_url}")"
  database_name="$(extract_database_name "${name}" "${jdbc_url}")"
  if [[ "${database_name}" != "youth_welfare" ]]; then
    echo "${name} must point to 'youth_welfare' but was '${database_name}': ${jdbc_url}" >&2
    exit 1
  fi

  if [[ "${jdbc_kind}" == "postgresql" ]]; then
    current_schema="$(extract_postgres_current_schema "${jdbc_url}")"
    if [[ -n "${current_schema}" && "${current_schema}" != "public" ]]; then
      echo "${name} must target the runtime public schema (no currentSchema or currentSchema=public) but was '${current_schema}': ${jdbc_url}" >&2
      exit 1
    fi
  fi
}

assert_runtime_pii_target() {
  local name="$1"
  local jdbc_url="$2"
  local database_name current_schema

  extract_jdbc_kind "${name}" "${jdbc_url}" >/dev/null
  database_name="$(extract_database_name "${name}" "${jdbc_url}")"

  if [[ "${database_name}" != "youth_welfare" ]]; then
    echo "${name} must point to 'youth_welfare' with currentSchema=youth_welfare_pii but was '${database_name}': ${jdbc_url}" >&2
    exit 1
  fi

  current_schema="$(extract_postgres_current_schema "${jdbc_url}")"
  if [[ "${current_schema}" != "youth_welfare_pii" ]]; then
    echo "${name} must set currentSchema=youth_welfare_pii but was '${current_schema:-<missing>}': ${jdbc_url}" >&2
    exit 1
  fi
}

password_state() {
  local value="${1:-}"
  if [[ -n "${value}" ]]; then
    printf "set\n"
    return 0
  fi

  printf "missing\n"
}

validate_env() {
  require_non_empty DB_URL "${DB_URL:-}"
  require_non_empty DB_USERNAME "${DB_USERNAME:-}"
  require_non_empty DB_PASSWORD "${DB_PASSWORD:-}"
  require_non_empty APP_PII_DB_URL "${APP_PII_DB_URL:-}"
  require_non_empty NOTIFICATION_PII_DB_URL "${NOTIFICATION_PII_DB_URL:-}"
  require_non_empty ADMIN_RO_DB_URL "${ADMIN_RO_DB_URL:-}"
  require_non_empty CHAT_SESSION_CLEANUP_DB_URL "${CHAT_SESSION_CLEANUP_DB_URL:-}"
  require_non_empty CLUSTER_AI_CLEANUP_DB_URL "${CLUSTER_AI_CLEANUP_DB_URL:-}"
  require_non_empty RECOMMENDATION_RETENTION_CLEANUP_DB_URL "${RECOMMENDATION_RETENTION_CLEANUP_DB_URL:-}"
  require_non_empty COLLECT_EXECUTION_LOCK_CLEANUP_DB_URL "${COLLECT_EXECUTION_LOCK_CLEANUP_DB_URL:-}"
  require_non_empty WEB_PUSH_SUBSCRIPTION_CLEANUP_DB_URL "${WEB_PUSH_SUBSCRIPTION_CLEANUP_DB_URL:-}"
  require_non_empty DB_MIGRATION_USERNAME "${DB_MIGRATION_USERNAME:-}"
  require_non_empty DB_MIGRATION_PASSWORD "${DB_MIGRATION_PASSWORD:-}"
  require_non_empty DB_ADMIN_RO_USERNAME "${DB_ADMIN_RO_USERNAME:-}"
  require_non_empty DB_ADMIN_RO_PASSWORD "${DB_ADMIN_RO_PASSWORD:-}"
  require_non_empty RECOMMENDATION_REVIEW_GATE_COMMAND_DB_URL "${RECOMMENDATION_REVIEW_GATE_COMMAND_DB_URL:-}"
  require_non_empty DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME "${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME:-}"
  require_non_empty DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD "${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD:-}"
  require_non_empty DB_CHAT_SESSION_CLEANUP_USERNAME "${DB_CHAT_SESSION_CLEANUP_USERNAME:-}"
  require_non_empty DB_CHAT_SESSION_CLEANUP_PASSWORD "${DB_CHAT_SESSION_CLEANUP_PASSWORD:-}"
  require_non_empty DB_CLUSTER_AI_CLEANUP_USERNAME "${DB_CLUSTER_AI_CLEANUP_USERNAME:-}"
  require_non_empty DB_CLUSTER_AI_CLEANUP_PASSWORD "${DB_CLUSTER_AI_CLEANUP_PASSWORD:-}"
  require_non_empty DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME "${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME:-}"
  require_non_empty DB_RECOMMENDATION_RETENTION_CLEANUP_PASSWORD "${DB_RECOMMENDATION_RETENTION_CLEANUP_PASSWORD:-}"
  require_non_empty DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME "${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME:-}"
  require_non_empty DB_COLLECT_EXECUTION_LOCK_CLEANUP_PASSWORD "${DB_COLLECT_EXECUTION_LOCK_CLEANUP_PASSWORD:-}"
  require_non_empty DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME "${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME:-}"
  require_non_empty DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_PASSWORD "${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_PASSWORD:-}"
  require_non_empty DB_APP_PII_USERNAME "${DB_APP_PII_USERNAME:-}"
  require_non_empty DB_APP_PII_PASSWORD "${DB_APP_PII_PASSWORD:-}"
  require_non_empty DB_NOTIFICATION_PII_RO_USERNAME "${DB_NOTIFICATION_PII_RO_USERNAME:-}"
  require_non_empty DB_NOTIFICATION_PII_RO_PASSWORD "${DB_NOTIFICATION_PII_RO_PASSWORD:-}"

  assert_equals DB_USERNAME "${DB_USERNAME}" "app_core_rw"
  assert_equals DB_MIGRATION_USERNAME "${DB_MIGRATION_USERNAME}" "migration_admin"
  assert_equals DB_ADMIN_RO_USERNAME "${DB_ADMIN_RO_USERNAME}" "admin_dashboard_ro"
  assert_equals DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME "${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME}" "recommendation_review_gate_command_rw"
  assert_equals DB_CHAT_SESSION_CLEANUP_USERNAME "${DB_CHAT_SESSION_CLEANUP_USERNAME}" "chat_session_cleanup_rw"
  assert_equals DB_CLUSTER_AI_CLEANUP_USERNAME "${DB_CLUSTER_AI_CLEANUP_USERNAME}" "cluster_ai_cleanup_rw"
  assert_equals DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME "${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME}" "recommendation_retention_cleanup_rw"
  assert_equals DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME "${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME}" "collect_execution_lock_cleanup_rw"
  assert_equals DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME "${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME}" "web_push_subscription_cleanup_rw"
  assert_equals DB_APP_PII_USERNAME "${DB_APP_PII_USERNAME}" "app_pii_rw"
  assert_equals DB_NOTIFICATION_PII_RO_USERNAME "${DB_NOTIFICATION_PII_RO_USERNAME}" "notification_pii_ro"

  assert_runtime_core_target DB_URL "${DB_URL}"
  assert_runtime_core_target ADMIN_RO_DB_URL "${ADMIN_RO_DB_URL}"
  assert_runtime_core_target RECOMMENDATION_REVIEW_GATE_COMMAND_DB_URL "${RECOMMENDATION_REVIEW_GATE_COMMAND_DB_URL}"
  assert_runtime_core_target CHAT_SESSION_CLEANUP_DB_URL "${CHAT_SESSION_CLEANUP_DB_URL}"
  assert_runtime_core_target CLUSTER_AI_CLEANUP_DB_URL "${CLUSTER_AI_CLEANUP_DB_URL}"
  assert_runtime_core_target RECOMMENDATION_RETENTION_CLEANUP_DB_URL "${RECOMMENDATION_RETENTION_CLEANUP_DB_URL}"
  assert_runtime_core_target COLLECT_EXECUTION_LOCK_CLEANUP_DB_URL "${COLLECT_EXECUTION_LOCK_CLEANUP_DB_URL}"
  assert_runtime_core_target WEB_PUSH_SUBSCRIPTION_CLEANUP_DB_URL "${WEB_PUSH_SUBSCRIPTION_CLEANUP_DB_URL}"
  assert_runtime_pii_target APP_PII_DB_URL "${APP_PII_DB_URL}"
  assert_runtime_pii_target NOTIFICATION_PII_DB_URL "${NOTIFICATION_PII_DB_URL}"

  if [[ "${DB_USERNAME}" == "${DB_APP_PII_USERNAME}" || "${DB_USERNAME}" == "${DB_NOTIFICATION_PII_RO_USERNAME}" ]]; then
    echo "runtime secondary datasource usernames must not collapse to DB_USERNAME" >&2
    exit 1
  fi

  if [[ "${APP_PII_DB_URL}" == "${DB_URL}" || "${NOTIFICATION_PII_DB_URL}" == "${DB_URL}" ]]; then
    echo "secondary datasource URLs must not reuse DB_URL exactly; they must target the PII schema" >&2
    exit 1
  fi
}

describe_runtime_target() {
  local name="$1"
  local jdbc_url="$2"
  local jdbc_kind host_port database_name current_schema

  jdbc_kind="$(extract_jdbc_kind "${name}" "${jdbc_url}")"
  host_port="$(extract_host_port "${name}" "${jdbc_url}")"
  database_name="$(extract_database_name "${name}" "${jdbc_url}")"
  if [[ "${jdbc_kind}" == "postgresql" ]]; then
    current_schema="$(extract_postgres_current_schema "${jdbc_url}")"
    if [[ -n "${current_schema}" ]]; then
      printf "%s / %s / schema=%s\n" "${host_port}" "${database_name}" "${current_schema}"
      return 0
    fi
  fi
  printf "%s / %s\n" "${host_port}" "${database_name}"
}

validate_compose_config() {
  if [[ "${SKIP_COMPOSE_CONFIG}" == "true" ]]; then
    COMPOSE_CONFIG_STATUS="skipped (SKIP_COMPOSE_CONFIG=true)"
    return 0
  fi

  if ! command -v docker >/dev/null 2>&1; then
    COMPOSE_CONFIG_STATUS="skipped (docker not found)"
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

  COMPOSE_CONFIG_STATUS="passed"
}

print_summary() {
  local env_source

  if [[ -n "${ENV_FILE}" ]]; then
    env_source="${ENV_FILE}"
  else
    env_source="shell env"
  fi

  cat <<EOF
runtime cutover env summary
- env source: ${env_source}
- compose config: ${COMPOSE_CONFIG_STATUS}
- DB_URL target: $(describe_runtime_target DB_URL "${DB_URL}")
- ADMIN_RO_DB_URL target: $(describe_runtime_target ADMIN_RO_DB_URL "${ADMIN_RO_DB_URL}")
- RECOMMENDATION_REVIEW_GATE_COMMAND_DB_URL target: $(describe_runtime_target RECOMMENDATION_REVIEW_GATE_COMMAND_DB_URL "${RECOMMENDATION_REVIEW_GATE_COMMAND_DB_URL}")
- CHAT_SESSION_CLEANUP_DB_URL target: $(describe_runtime_target CHAT_SESSION_CLEANUP_DB_URL "${CHAT_SESSION_CLEANUP_DB_URL}")
- CLUSTER_AI_CLEANUP_DB_URL target: $(describe_runtime_target CLUSTER_AI_CLEANUP_DB_URL "${CLUSTER_AI_CLEANUP_DB_URL}")
- RECOMMENDATION_RETENTION_CLEANUP_DB_URL target: $(describe_runtime_target RECOMMENDATION_RETENTION_CLEANUP_DB_URL "${RECOMMENDATION_RETENTION_CLEANUP_DB_URL}")
- COLLECT_EXECUTION_LOCK_CLEANUP_DB_URL target: $(describe_runtime_target COLLECT_EXECUTION_LOCK_CLEANUP_DB_URL "${COLLECT_EXECUTION_LOCK_CLEANUP_DB_URL}")
- WEB_PUSH_SUBSCRIPTION_CLEANUP_DB_URL target: $(describe_runtime_target WEB_PUSH_SUBSCRIPTION_CLEANUP_DB_URL "${WEB_PUSH_SUBSCRIPTION_CLEANUP_DB_URL}")
- APP_PII_DB_URL target: $(describe_runtime_target APP_PII_DB_URL "${APP_PII_DB_URL}")
- NOTIFICATION_PII_DB_URL target: $(describe_runtime_target NOTIFICATION_PII_DB_URL "${NOTIFICATION_PII_DB_URL}")
- DB_USERNAME: ${DB_USERNAME}
- DB_MIGRATION_USERNAME: ${DB_MIGRATION_USERNAME}
- DB_ADMIN_RO_USERNAME: ${DB_ADMIN_RO_USERNAME}
- DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME: ${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME}
- DB_CHAT_SESSION_CLEANUP_USERNAME: ${DB_CHAT_SESSION_CLEANUP_USERNAME}
- DB_CLUSTER_AI_CLEANUP_USERNAME: ${DB_CLUSTER_AI_CLEANUP_USERNAME}
- DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME: ${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME}
- DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME: ${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME}
- DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME: ${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME}
- DB_APP_PII_USERNAME: ${DB_APP_PII_USERNAME}
- DB_NOTIFICATION_PII_RO_USERNAME: ${DB_NOTIFICATION_PII_RO_USERNAME}
- DB_PASSWORD: $(password_state "${DB_PASSWORD:-}")
- DB_MIGRATION_PASSWORD: $(password_state "${DB_MIGRATION_PASSWORD:-}")
- DB_ADMIN_RO_PASSWORD: $(password_state "${DB_ADMIN_RO_PASSWORD:-}")
- DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD: $(password_state "${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD:-}")
- DB_CHAT_SESSION_CLEANUP_PASSWORD: $(password_state "${DB_CHAT_SESSION_CLEANUP_PASSWORD:-}")
- DB_CLUSTER_AI_CLEANUP_PASSWORD: $(password_state "${DB_CLUSTER_AI_CLEANUP_PASSWORD:-}")
- DB_RECOMMENDATION_RETENTION_CLEANUP_PASSWORD: $(password_state "${DB_RECOMMENDATION_RETENTION_CLEANUP_PASSWORD:-}")
- DB_COLLECT_EXECUTION_LOCK_CLEANUP_PASSWORD: $(password_state "${DB_COLLECT_EXECUTION_LOCK_CLEANUP_PASSWORD:-}")
- DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_PASSWORD: $(password_state "${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_PASSWORD:-}")
- DB_APP_PII_PASSWORD: $(password_state "${DB_APP_PII_PASSWORD:-}")
- DB_NOTIFICATION_PII_RO_PASSWORD: $(password_state "${DB_NOTIFICATION_PII_RO_PASSWORD:-}")
EOF
}

validate_env
validate_compose_config

if [[ "${PRINT_SUMMARY}" == "true" ]]; then
  print_summary
fi

echo "runtime cutover env preflight passed"
