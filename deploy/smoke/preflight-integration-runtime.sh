#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-5433}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"

have_command() {
  command -v "$1" >/dev/null 2>&1
}

is_wsl() {
  [[ -n "${WSL_INTEROP:-}" ]] || grep -qiE "(microsoft|wsl)" /proc/version 2>/dev/null
}

can_connect() {
  local host="$1"
  local port="$2"
  (echo >"/dev/tcp/${host}/${port}") >/dev/null 2>&1
}

status_label() {
  local ok="$1"
  if [[ "${ok}" == "true" ]]; then
    printf "ok"
    return 0
  fi

  printf "missing"
}

print_summary() {
  local docker_available="$1"
  local compose_available="$2"
  local db_ready="$3"
  local redis_ready="$4"

  cat <<EOF
integration runtime preflight summary
- repo root: ${ROOT_DIR}
- docker command: $(status_label "${docker_available}")
- docker compose: $(status_label "${compose_available}")
- PostgreSQL ${DB_HOST}:${DB_PORT}: $(status_label "${db_ready}")
- Redis ${REDIS_HOST}:${REDIS_PORT}: $(status_label "${redis_ready}")
EOF
}

main() {
  local docker_available="false"
  local compose_available="false"
  local db_ready="false"
  local redis_ready="false"

  if have_command docker; then
    docker_available="true"
    if docker compose version >/dev/null 2>&1; then
      compose_available="true"
    fi
  fi

  if can_connect "${DB_HOST}" "${DB_PORT}"; then
    db_ready="true"
  fi

  if can_connect "${REDIS_HOST}" "${REDIS_PORT}"; then
    redis_ready="true"
  fi

  print_summary "${docker_available}" "${compose_available}" "${db_ready}" "${redis_ready}"

  if [[ "${db_ready}" == "true" && "${redis_ready}" == "true" ]]; then
    echo "integration runtime preflight passed"
    return 0
  fi

  echo >&2
  echo "integration runtime preflight failed" >&2

  if [[ "${docker_available}" == "false" ]]; then
    echo "- docker command is not available in this shell" >&2
    if is_wsl; then
      echo "- this looks like WSL; enable Docker Desktop WSL integration first" >&2
    fi
  elif [[ "${compose_available}" == "false" ]]; then
    echo "- docker is available but 'docker compose' is not working" >&2
  else
    echo "- start local runtime from repo root: docker compose up -d db redis" >&2
  fi

  if [[ "${db_ready}" == "false" ]]; then
    echo "- PostgreSQL is not reachable at ${DB_HOST}:${DB_PORT}" >&2
  fi

  if [[ "${redis_ready}" == "false" ]]; then
    echo "- Redis is not reachable at ${REDIS_HOST}:${REDIS_PORT}" >&2
  fi

  echo "- after runtime is ready, rerun: cd backend && ./gradlew integrationTest --no-daemon" >&2
  exit 1
}

main "$@"
