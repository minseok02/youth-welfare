#!/usr/bin/env bash

smoke_trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf "%s" "${value}"
}

smoke_unquote() {
  local value="$1"
  if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf "%s" "${value}"
}

smoke_first_csv_value() {
  local value="$1"
  value="${value%%,*}"
  smoke_trim "${value}"
}

smoke_first_file_line() {
  local file_path="$1"
  local value
  IFS= read -r value < "${file_path}" || value=""
  smoke_trim "${value}"
}

smoke_load_env_file() {
  local env_file="$1"
  local line key value

  [[ -f "${env_file}" ]] || return 0

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "$(smoke_trim "${line}")" ]] && continue
    [[ "$(smoke_trim "${line}")" == \#* ]] && continue
    [[ "${line}" != *=* ]] && continue

    key="$(smoke_trim "${line%%=*}")"
    value="${line#*=}"
    value="$(smoke_unquote "${value}")"

    if [[ "${key}" == export\ * ]]; then
      key="$(smoke_trim "${key#export }")"
    fi

    if [[ ! "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      continue
    fi

    if [[ -z "${!key+x}" ]]; then
      export "${key}=${value}"
    fi
  done < "${env_file}"
}

smoke_load_env_value() {
  local env_file="$1"
  local lookup_key="$2"
  local default_value="${3:-}"
  local line key value
  local result="${default_value}"

  [[ -f "${env_file}" ]] || {
    printf "%s" "${result}"
    return 0
  }

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "$(smoke_trim "${line}")" ]] && continue
    [[ "$(smoke_trim "${line}")" == \#* ]] && continue
    [[ "${line}" != *=* ]] && continue

    key="$(smoke_trim "${line%%=*}")"
    value="${line#*=}"
    value="$(smoke_unquote "${value}")"

    if [[ "${key}" == export\ * ]]; then
      key="$(smoke_trim "${key#export }")"
    fi

    if [[ "${key}" == "${lookup_key}" ]]; then
      result="${value}"
    fi
  done < "${env_file}"

  printf "%s" "${result}"
}

smoke_default_root_dir() {
  cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd
}

smoke_resolve_admin_credentials() {
  local root_dir="$1"
  local env_file="${ENV_FILE:-${root_dir}/.env}"
  local admin_email_file="${ADMIN_EMAIL_FILE:-/tmp/youth-welfare-admin-smoke-email}"
  local admin_password_file="${ADMIN_PASSWORD_FILE:-/tmp/youth-welfare-admin-smoke-password}"
  local env_admin_email
  local env_admin_password
  local env_security_admin_emails

  env_admin_email="$(smoke_load_env_value "${env_file}" ADMIN_EMAIL)"
  env_admin_password="$(smoke_load_env_value "${env_file}" ADMIN_PASSWORD)"
  env_security_admin_emails="$(smoke_load_env_value "${env_file}" SECURITY_ADMIN_EMAILS)"

  if [[ -z "${SECURITY_ADMIN_EMAILS:-}" && -n "${env_security_admin_emails}" ]]; then
    export SECURITY_ADMIN_EMAILS
    SECURITY_ADMIN_EMAILS="${env_security_admin_emails}"
  fi

  if [[ -z "${ADMIN_EMAIL:-}" && -r "${admin_email_file}" ]]; then
    export ADMIN_EMAIL
    ADMIN_EMAIL="$(smoke_first_file_line "${admin_email_file}")"
  fi

  if [[ -z "${ADMIN_PASSWORD:-}" && -r "${admin_password_file}" ]]; then
    export ADMIN_PASSWORD
    ADMIN_PASSWORD="$(smoke_first_file_line "${admin_password_file}")"
  fi

  if [[ -z "${ADMIN_EMAIL:-}" && -n "${env_admin_email}" ]]; then
    export ADMIN_EMAIL
    ADMIN_EMAIL="${env_admin_email}"
  fi

  if [[ -z "${ADMIN_PASSWORD:-}" && -n "${env_admin_password}" ]]; then
    export ADMIN_PASSWORD
    ADMIN_PASSWORD="${env_admin_password}"
  fi

  if [[ -z "${ADMIN_EMAIL:-}" && -n "${SECURITY_ADMIN_EMAILS:-}" ]]; then
    export ADMIN_EMAIL
    ADMIN_EMAIL="$(smoke_first_csv_value "${SECURITY_ADMIN_EMAILS}")"
  fi
}

smoke_require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing required command: $1" >&2
    exit 1
  }
}

smoke_normalize_bool() {
  local value="${1,,}"
  case "${value}" in
    true|false) printf '%s' "${value}" ;;
    *)
      echo "unsupported flag value: ${1}" >&2
      exit 1
      ;;
  esac
}

smoke_normalize_tri_state() {
  local value="${1,,}"
  case "${value}" in
    true|false|auto) printf '%s' "${value}" ;;
    *)
      echo "unsupported tri-state value: ${1}" >&2
      exit 1
      ;;
  esac
}

smoke_now_ts_utc() {
  date -u +%Y%m%dT%H%M%SZ
}

smoke_now_iso_utc() {
  date -u +%Y-%m-%dT%H:%M:%SZ
}

smoke_now_iso_kst() {
  TZ=Asia/Seoul date +%Y-%m-%dT%H:%M:%S%z
}

smoke_update_links() {
  if (( $# == 0 || $# % 2 != 0 )); then
    echo "smoke_update_links requires target/link pairs" >&2
    exit 1
  fi

  local target link
  while (( $# > 0 )); do
    target="$1"
    link="$2"
    ln -sfn "${target}" "${link}"
    shift 2
  done
}

smoke_http_status() {
  local method="$1"
  local url="$2"
  local output_file="$3"
  shift 3
  curl -sS -o "${output_file}" -w "%{http_code}" -X "${method}" "$url" "$@"
}

smoke_health_status_is_up() {
  local health_response_file="$1"

  smoke_require_command python3

  python3 - "${health_response_file}" <<'PY'
import json
import sys

try:
    with open(sys.argv[1], "r", encoding="utf-8") as fp:
        payload = json.load(fp)
except Exception:
    sys.exit(1)

sys.exit(0 if payload.get("status") == "UP" else 1)
PY
}

smoke_wait_for_health() {
  local retries="$1"
  local delay_seconds="$2"
  local health_url="$3"
  local health_response_file="$4"
  local health_stderr_file="$5"
  local status=""
  local attempt=1

  while (( attempt <= retries )); do
    if status="$(smoke_http_status GET "${health_url}" "${health_response_file}" 2>"${health_stderr_file}")"; then
      if [[ "${status}" == "200" ]] && smoke_health_status_is_up "${health_response_file}"; then
        printf '%s' "${status}"
        return 0
      fi
    fi

    if (( attempt == retries )); then
      echo "health check failed after ${retries} attempts" >&2
      if [[ -s "${health_stderr_file}" ]]; then
        cat "${health_stderr_file}" >&2
      fi
      if [[ -f "${health_response_file}" ]]; then
        cat "${health_response_file}" >&2
      fi
      return 1
    fi

    sleep "${delay_seconds}"
    attempt=$((attempt + 1))
  done
}

smoke_print_step() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

smoke_assert_status() {
  local expected="$1"
  local actual="$2"
  local context="$3"
  local file_path="$4"
  if [[ "${expected}" != "${actual}" ]]; then
    echo "${context} failed: expected ${expected}, got ${actual}" >&2
    cat "${file_path}" >&2
    exit 1
  fi
}

smoke_db_container_available() {
  local db_container_name="$1"

  command -v docker >/dev/null 2>&1 || return 1
  docker ps --format '{{.Names}}' | grep -Fxq "${db_container_name}"
}

smoke_resolve_db_mode() {
  local requested_mode="${SMOKE_DB_MODE:-auto}"
  local db_container_name="${DB_CONTAINER_NAME:-${POSTGRES_CONTAINER_NAME:-youth-welfare-db}}"

  case "${requested_mode}" in
    auto)
      if smoke_db_container_available "${db_container_name}"; then
        printf '%s' "docker"
      else
        printf '%s' "postgres"
      fi
      ;;
    docker|postgres)
      printf '%s' "${requested_mode}"
      ;;
    *)
      echo "unsupported SMOKE_DB_MODE: ${requested_mode} (expected auto, docker, or postgres)" >&2
      exit 1
      ;;
  esac
}

smoke_jdbc_to_postgres_url() {
  local raw_url="$1"

  smoke_require_command python3

  python3 - "${raw_url}" <<'PY'
import sys
from urllib.parse import parse_qsl, quote, urlencode, urlparse

raw = sys.argv[1].strip()
if raw.startswith("jdbc:"):
    raw = raw[5:]

parsed = urlparse(raw)
if parsed.scheme not in {"postgres", "postgresql"}:
    raise SystemExit(f"unsupported postgres url scheme: {parsed.scheme}")

params = []
search_path = None
for key, value in parse_qsl(parsed.query, keep_blank_values=True):
    if key == "currentSchema" and value and search_path is None:
        search_path = value
        continue
    params.append((key, value))

if search_path:
    params.append(("options", f"-csearch_path={search_path}"))

query = urlencode(params, doseq=True, quote_via=quote)
url = f"postgresql://{parsed.netloc}{parsed.path}"
if query:
    url = f"{url}?{query}"
print(url)
PY
}

smoke_resolve_db_connection_url() {
  local env_file="${ENV_FILE:-$(smoke_default_root_dir)/.env}"
  local db_direct_url="${DB_DIRECT_URL:-}"
  local db_url="${DB_URL:-}"

  if [[ -z "${db_direct_url}" ]]; then
    db_direct_url="$(smoke_load_env_value "${env_file}" DB_DIRECT_URL)"
  fi
  if [[ -z "${db_url}" ]]; then
    db_url="$(smoke_load_env_value "${env_file}" DB_URL)"
  fi

  if [[ -n "${db_direct_url}" ]]; then
    smoke_jdbc_to_postgres_url "${db_direct_url}"
    return 0
  fi

  if [[ -n "${db_url}" ]]; then
    smoke_jdbc_to_postgres_url "${db_url}"
    return 0
  fi

  return 1
}

smoke_run_psql_direct() {
  local db_query_password="$1"
  shift

  if command -v psql >/dev/null 2>&1; then
    PGPASSWORD="${db_query_password}" psql "$@"
    return 0
  fi

  smoke_require_command docker

  docker run --rm --network host \
    -e PGPASSWORD="${db_query_password}" \
    "${SMOKE_PSQL_DOCKER_IMAGE:-postgres:16}" \
    psql "$@"
}

smoke_db_query() {
  local sql="$1"
  local db_mode
  local db_container_name="${DB_CONTAINER_NAME:-${POSTGRES_CONTAINER_NAME:-youth-welfare-db}}"
  local db_name="${DB_NAME:-youth_welfare}"
  local env_file="${ENV_FILE:-$(smoke_default_root_dir)/.env}"
  local db_query_username="${DB_QUERY_USERNAME:-}"
  local db_query_password="${DB_QUERY_PASSWORD:-}"
  local db_connection_url=""

  if [[ -z "${db_query_username}" ]]; then
    db_query_username="$(smoke_load_env_value "${env_file}" DB_QUERY_USERNAME)"
  fi
  if [[ -z "${db_query_username}" ]]; then
    db_query_username="$(smoke_load_env_value "${env_file}" DB_MIGRATION_USERNAME migration_admin)"
  fi

  if [[ -z "${db_query_password}" ]]; then
    db_query_password="$(smoke_load_env_value "${env_file}" DB_QUERY_PASSWORD)"
  fi
  if [[ -z "${db_query_password}" ]]; then
    db_query_password="$(smoke_load_env_value "${env_file}" DB_MIGRATION_PASSWORD)"
  fi
  if [[ -z "${db_query_password}" ]]; then
    db_query_password="$(smoke_load_env_value "${env_file}" DB_PASSWORD welfare1234!)"
  fi

  db_mode="$(smoke_resolve_db_mode)"

  if [[ "${db_mode}" == "docker" ]]; then
    smoke_require_command docker
    docker exec -e PGPASSWORD="${db_query_password}" "${db_container_name}" \
      psql -U "${db_query_username}" -d "${db_name}" -At -F $'\t' -c "${sql}"
    return 0
  fi

  db_connection_url="$(smoke_resolve_db_connection_url)" || {
    echo "DB_DIRECT_URL or DB_URL is required for SMOKE_DB_MODE=postgres" >&2
    exit 1
  }

  smoke_run_psql_direct "${db_query_password}" \
    --username "${db_query_username}" --dbname "${db_connection_url}" -At -F $'\t' -c "${sql}"
}

smoke_db_apply_file() {
  local file_path="$1"
  local db_mode
  local db_container_name="${DB_CONTAINER_NAME:-${POSTGRES_CONTAINER_NAME:-youth-welfare-db}}"
  local db_name="${DB_NAME:-youth_welfare}"
  local env_file="${ENV_FILE:-$(smoke_default_root_dir)/.env}"
  local db_query_username="${DB_QUERY_USERNAME:-}"
  local db_query_password="${DB_QUERY_PASSWORD:-}"
  local db_connection_url=""

  if [[ -z "${db_query_username}" ]]; then
    db_query_username="$(smoke_load_env_value "${env_file}" DB_QUERY_USERNAME)"
  fi
  if [[ -z "${db_query_username}" ]]; then
    db_query_username="$(smoke_load_env_value "${env_file}" DB_MIGRATION_USERNAME migration_admin)"
  fi

  if [[ -z "${db_query_password}" ]]; then
    db_query_password="$(smoke_load_env_value "${env_file}" DB_QUERY_PASSWORD)"
  fi
  if [[ -z "${db_query_password}" ]]; then
    db_query_password="$(smoke_load_env_value "${env_file}" DB_MIGRATION_PASSWORD)"
  fi
  if [[ -z "${db_query_password}" ]]; then
    db_query_password="$(smoke_load_env_value "${env_file}" DB_PASSWORD welfare1234!)"
  fi

  db_mode="$(smoke_resolve_db_mode)"

  if [[ "${db_mode}" == "docker" ]]; then
    smoke_require_command docker
    docker exec -i -e PGPASSWORD="${db_query_password}" "${db_container_name}" \
      psql -v ON_ERROR_STOP=1 -U "${db_query_username}" -d "${db_name}" -f - < "${file_path}"
    return 0
  fi

  db_connection_url="$(smoke_resolve_db_connection_url)" || {
    echo "DB_DIRECT_URL or DB_URL is required for SMOKE_DB_MODE=postgres" >&2
    exit 1
  }

  smoke_run_psql_direct "${db_query_password}" \
    --set ON_ERROR_STOP=1 --username "${db_query_username}" --dbname "${db_connection_url}" -f "${file_path}"
}

smoke_seed_verified_email() {
  local raw_email="$1"
  local redis_container_name="${REDIS_CONTAINER_NAME:-youth-welfare-redis}"
  local verified_ttl_seconds="${EMAIL_VERIFIED_TTL_SECONDS:-900}"
  local hash

  smoke_require_command docker
  smoke_require_command python3

  hash="$(
    python3 - "${raw_email}" <<'PY'
import hashlib
import sys

normalized = sys.argv[1].strip().lower()
print(hashlib.sha256(normalized.encode("utf-8")).hexdigest())
PY
  )"

  docker exec "${redis_container_name}" \
    redis-cli SETEX "email-verify:verified:${hash}" "${verified_ttl_seconds}" 1 >/dev/null
}

smoke_unique_suffix() {
  smoke_require_command python3

  python3 <<'PY'
import uuid

print(uuid.uuid4().hex[:14])
PY
}

smoke_build_email() {
  local email_prefix="$1"
  local email_domain="${SMOKE_EMAIL_DOMAIN:-example.com}"
  smoke_require_command python3

  python3 - "${email_prefix}" "$(smoke_unique_suffix)" "${email_domain}" <<'PY'
import re
import sys

prefix = sys.argv[1].strip().lower()
suffix = sys.argv[2].strip().lower()
domain = sys.argv[3].strip().lower()

safe_prefix = re.sub(r"[^a-z0-9]+", ".", prefix)
safe_prefix = re.sub(r"\.+", ".", safe_prefix).strip(".") or "smoke"
safe_prefix = safe_prefix[:12].rstrip(".") or "smoke"

safe_domain = re.sub(r"[^a-z0-9.-]+", "-", domain)
safe_domain = re.sub(r"\.{2,}", ".", safe_domain).strip(".-") or "example.com"

if "." not in safe_domain:
    safe_domain = f"{safe_domain}.com"

print(f"{safe_prefix}.{suffix}@{safe_domain}")
PY
}
