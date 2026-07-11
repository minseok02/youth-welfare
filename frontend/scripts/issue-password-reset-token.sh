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
  email_hash="$(smoke_sha256_hex "${email}")"
  user_key="$(
    smoke_db_query \
      "SELECT user_key FROM auth_users WHERE email_lookup_hash = $(smoke_sql_quote "${email_hash}") LIMIT 1;"
  )"
fi

if [[ -z "${user_key}" ]]; then
  echo "user not found for ${email}" >&2
  exit 1
fi

previous_token="$(
  smoke_redis_cli GET "password-reset:user:${user_key}" | tr -d '\r'
)"

if [[ -n "${previous_token}" ]]; then
  smoke_redis_cli DEL "password-reset:${previous_token}" "password-reset:user:${user_key}" >/dev/null
fi

token="$(python3 - <<'PY'
import uuid
print(uuid.uuid4())
PY
)"

ttl_seconds="${PASSWORD_RESET_TTL_SECONDS:-1800}"

smoke_redis_cli SETEX "password-reset:${token}" "${ttl_seconds}" "${user_key}" >/dev/null
smoke_redis_cli SETEX "password-reset:user:${user_key}" "${ttl_seconds}" "${token}" >/dev/null

printf '%s\n' "${token}"
