#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
HEALTH_URL="${HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
E2E_ADMIN_EMAIL="${E2E_ADMIN_EMAIL:-${ADMIN_EMAIL:-admin@example.com}}"
E2E_ADMIN_PASSWORD="${E2E_ADMIN_PASSWORD:-${ADMIN_PASSWORD:-password123!}}"
E2E_USER_EMAIL="${E2E_USER_EMAIL:-${SMOKE_EMAIL:-playwright.user@example.com}}"
E2E_USER_PASSWORD="${E2E_USER_PASSWORD:-${SMOKE_PASSWORD:-Password123!}}"
SECURITY_ADMIN_EMAILS="${SECURITY_ADMIN_EMAILS:-${E2E_ADMIN_EMAIL}}"
ADMIN_EMAIL_FILE="${ADMIN_EMAIL_FILE:-/tmp/youth-welfare-admin-smoke-email}"
ADMIN_PASSWORD_FILE="${ADMIN_PASSWORD_FILE:-/tmp/youth-welfare-admin-smoke-password}"

export APP_BASE_URL E2E_ADMIN_EMAIL E2E_ADMIN_PASSWORD E2E_USER_EMAIL E2E_USER_PASSWORD SECURITY_ADMIN_EMAILS

reset_login_account_state() {
  local email="$1"
  local quoted_email

  quoted_email="$(smoke_sql_quote "${email}")"
  smoke_db_query "UPDATE users SET login_fail_count = 0, locked_until = NULL WHERE email = ${quoted_email};" >/dev/null
  smoke_db_query "UPDATE auth_users SET login_fail_count = 0, locked_until = NULL WHERE user_key IN (SELECT user_key FROM users WHERE email = ${quoted_email});" >/dev/null
}

clear_login_rate_limit_keys() {
  local redis_container_name="${REDIS_CONTAINER_NAME:-youth-welfare-redis}"

  smoke_require_command docker
  docker exec "${redis_container_name}" sh -lc \
    "redis-cli --scan --pattern 'auth:rate-limit:login:*' | xargs -r redis-cli DEL >/dev/null"
}

ensure_signup_user() {
  local app_base_url="$1"
  local email="$2"
  local password="$3"
  local signup_dir
  local signup_response
  local signup_status
  local existing_user_count
  local existing_auth_count
  local email_hash

  existing_user_count="$(smoke_db_query "SELECT COUNT(*) FROM users WHERE email = $(smoke_sql_quote "${email}");")"
  email_hash="$(smoke_sha256_hex "${email}")"
  existing_auth_count="$(smoke_db_query "SELECT COUNT(*) FROM auth_users WHERE email_lookup_hash = $(smoke_sql_quote "${email_hash}");")"

  if [[ "${existing_user_count}" != "0" || "${existing_auth_count}" != "0" ]]; then
    if [[ "${existing_user_count}" != "${existing_auth_count}" ]]; then
      echo "playwright bootstrap refused: users/auth mismatch for ${email}" >&2
      exit 1
    fi
    return 0
  fi

  signup_dir="$(mktemp -d)"
  signup_response="${signup_dir}/signup.json"

  smoke_seed_verified_email "${email}"
  signup_status="$(
    smoke_http_status POST "${app_base_url}/api/auth/signup" "${signup_response}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"email\": \"${email}\",
        \"password\": \"${password}\",
        \"name\": \"테스트유저\",
        \"birthDate\": \"1999-02-10\",
        \"sido\": \"서울특별시\",
        \"sgg\": \"중구\",
        \"incomeLevel\": 5,
        \"employmentStatus\": \"미취업\",
        \"householdType\": \"1인 가구\"
      }"
  )"

  if [[ "${signup_status}" != "200" ]]; then
    echo "playwright bootstrap signup failed for ${email}: expected 200, got ${signup_status}" >&2
    cat "${signup_response}" >&2
    rm -rf "${signup_dir}"
    exit 1
  fi

  rm -rf "${signup_dir}"
}

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

health_response="${tmp_dir}/health.json"
health_stderr="${tmp_dir}/health.stderr"

smoke_print_step "wait for backend health"
smoke_wait_for_health 60 2 "${HEALTH_URL}" "${health_response}" "${health_stderr}" >/dev/null

smoke_print_step "ensure admin account"
smoke_ensure_admin_account "${APP_BASE_URL}" "${E2E_ADMIN_EMAIL}" "${E2E_ADMIN_PASSWORD}"
printf '%s\n' "${E2E_ADMIN_EMAIL}" > "${ADMIN_EMAIL_FILE}"
printf '%s\n' "${E2E_ADMIN_PASSWORD}" > "${ADMIN_PASSWORD_FILE}"

smoke_print_step "ensure e2e user account"
ensure_signup_user "${APP_BASE_URL}" "${E2E_USER_EMAIL}" "${E2E_USER_PASSWORD}"

smoke_print_step "reset auth throttle state"
reset_login_account_state "${E2E_ADMIN_EMAIL}"
reset_login_account_state "${E2E_USER_EMAIL}"
clear_login_rate_limit_keys

smoke_print_step "ensure searchable policy fixture"
smoke_ensure_policy_fixture

echo "admin_email=${E2E_ADMIN_EMAIL}"
echo "user_email=${E2E_USER_EMAIL}"
echo "app_base_url=${APP_BASE_URL}"
