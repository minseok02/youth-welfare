#!/usr/bin/env bash
set -euo pipefail
set +H

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env}"
SUITE_SCRIPT="${ROOT_DIR}/deploy/smoke/run-local-validation-suite.sh"
ADMIN_EMAIL_FILE="${ADMIN_EMAIL_FILE:-/tmp/youth-welfare-admin-smoke-email}"
ADMIN_PASSWORD_FILE="${ADMIN_PASSWORD_FILE:-/tmp/youth-welfare-admin-smoke-password}"

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
    [[ "${line}" != *=* ]] && continue

    key="$(trim "${line%%=*}")"
    value="${line#*=}"
    value="$(unquote "${value}")"

    if [[ "${key}" == export\ * ]]; then
      key="$(trim "${key#export }")"
    fi

    if [[ ! "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      continue
    fi

    if [[ -z "${!key+x}" ]]; then
      export "${key}=${value}"
    fi
  done < "${ENV_FILE}"
}

first_csv_value() {
  local value="$1"
  value="${value%%,*}"
  trim "${value}"
}

first_file_line() {
  local file_path="$1"
  local value
  IFS= read -r value < "${file_path}" || value=""
  trim "${value}"
}

load_env_file

if [[ -z "${DB_ROOT_PASSWORD+x}" && -n "${DB_PASSWORD:-}" ]]; then
  export DB_ROOT_PASSWORD="${DB_PASSWORD}"
fi

if [[ -z "${DB_QUERY_USERNAME+x}" ]]; then
  export DB_QUERY_USERNAME="${DB_MIGRATION_USERNAME:-migration_admin}"
fi

if [[ -z "${DB_QUERY_PASSWORD+x}" ]]; then
  export DB_QUERY_PASSWORD="${DB_MIGRATION_PASSWORD:-${DB_PASSWORD:-}}"
fi

if [[ -z "${ADMIN_EMAIL+x}" && -r "${ADMIN_EMAIL_FILE}" ]]; then
  export ADMIN_EMAIL="$(first_file_line "${ADMIN_EMAIL_FILE}")"
fi

if [[ -z "${ADMIN_PASSWORD+x}" && -r "${ADMIN_PASSWORD_FILE}" ]]; then
  export ADMIN_PASSWORD="$(first_file_line "${ADMIN_PASSWORD_FILE}")"
fi

if [[ -z "${ADMIN_EMAIL+x}" && -n "${SECURITY_ADMIN_EMAILS:-}" ]]; then
  export ADMIN_EMAIL="$(first_csv_value "${SECURITY_ADMIN_EMAILS}")"
fi

if [[ -z "${DB_QUERY_PASSWORD:-}" ]]; then
  echo "DB_QUERY_PASSWORD is empty; set DB_MIGRATION_PASSWORD or DB_PASSWORD in ${ENV_FILE}" >&2
  exit 1
fi

if [[ -z "${DB_ROOT_PASSWORD:-}" ]]; then
  echo "DB_ROOT_PASSWORD is empty; set DB_PASSWORD in ${ENV_FILE} or pass DB_ROOT_PASSWORD explicitly" >&2
  exit 1
fi

echo "loaded validation env from ${ENV_FILE}"
echo "db_query_username=${DB_QUERY_USERNAME}"
echo "admin_email=${ADMIN_EMAIL:-<unset>}"
echo "secret_values=masked"

exec "${SUITE_SCRIPT}" "$@"
