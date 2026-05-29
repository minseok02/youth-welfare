#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <email>" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

email="$1"
user_key="$(smoke_db_query "SELECT user_key FROM users WHERE email = $(smoke_sql_quote "${email}") LIMIT 1;")"

if [[ -z "${user_key}" ]]; then
  echo "user not found for ${email}" >&2
  exit 1
fi

redis_container_name="${REDIS_CONTAINER_NAME:-youth-welfare-redis}"
smoke_require_command docker

token="$(
  docker exec "${redis_container_name}" \
    redis-cli GET "password-reset:user:${user_key}" | tr -d '\r'
)"

if [[ -z "${token}" ]]; then
  echo "reset token not found for ${email}" >&2
  exit 1
fi

printf '%s\n' "${token}"
