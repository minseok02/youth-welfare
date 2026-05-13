#!/usr/bin/env bash

smoke_require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing required command: $1" >&2
    exit 1
  }
}

smoke_http_status() {
  local method="$1"
  local url="$2"
  local output_file="$3"
  shift 3
  curl -sS -o "${output_file}" -w "%{http_code}" -X "${method}" "$url" "$@"
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
      if [[ "${status}" == "200" ]]; then
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

smoke_db_query() {
  local sql="$1"
  local db_container_name="${DB_CONTAINER_NAME:-${POSTGRES_CONTAINER_NAME:-youth-welfare-db}}"
  local db_name="${DB_NAME:-youth_welfare}"
  local db_query_username="${DB_QUERY_USERNAME:-migration_admin}"
  local db_query_password="${DB_QUERY_PASSWORD:-welfare1234!}"

  smoke_require_command docker

  docker exec -e PGPASSWORD="${db_query_password}" "${db_container_name}" \
    psql -U "${db_query_username}" -d "${db_name}" -At -F $'\t' -c "${sql}"
}

smoke_db_apply_file() {
  local file_path="$1"
  local db_container_name="${DB_CONTAINER_NAME:-${POSTGRES_CONTAINER_NAME:-youth-welfare-db}}"
  local db_name="${DB_NAME:-youth_welfare}"
  local db_query_username="${DB_QUERY_USERNAME:-migration_admin}"
  local db_query_password="${DB_QUERY_PASSWORD:-welfare1234!}"

  smoke_require_command docker

  docker exec -i -e PGPASSWORD="${db_query_password}" "${db_container_name}" \
    psql -v ON_ERROR_STOP=1 -U "${db_query_username}" -d "${db_name}" -f - < "${file_path}"
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
