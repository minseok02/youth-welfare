#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

RUN_RUNTIME_API_SMOKE="${RUN_RUNTIME_API_SMOKE:-true}"
RUN_LOGIN_FAILURE_TRACKING_SMOKE="${RUN_LOGIN_FAILURE_TRACKING_SMOKE:-true}"
RUN_ACCOUNT_LOCKOUT_SMOKE="${RUN_ACCOUNT_LOCKOUT_SMOKE:-true}"
RUN_WITHDRAW_SMOKE="${RUN_WITHDRAW_SMOKE:-true}"
RUN_ADMIN_FORCED_LOGOUT_SMOKE="${RUN_ADMIN_FORCED_LOGOUT_SMOKE:-true}"

run_step() {
  local label="$1"
  local script_path="$2"

  printf '\n=== %s ===\n' "${label}"
  "${script_path}"
}

if [[ "${RUN_RUNTIME_API_SMOKE}" == "true" ]]; then
  run_step \
    "runtime api smoke (logout / refresh invalidation / presented access revoke)" \
    "${ROOT_DIR}/deploy/smoke/run-local-runtime-api-smoke.sh"
fi

if [[ "${RUN_LOGIN_FAILURE_TRACKING_SMOKE}" == "true" ]]; then
  run_step \
    "login failure tracking smoke (A004 persistence / success reset)" \
    "${ROOT_DIR}/deploy/smoke/run-local-login-failure-tracking-smoke.sh"
fi

if [[ "${RUN_ACCOUNT_LOCKOUT_SMOKE}" == "true" ]]; then
  run_step \
    "account lockout smoke (threshold lock / A005)" \
    "${ROOT_DIR}/deploy/smoke/run-local-account-lockout-smoke.sh"
fi

if [[ "${RUN_WITHDRAW_SMOKE}" == "true" ]]; then
  run_step \
    "withdraw smoke (A006 / U003 / withdrawn mask)" \
    "${ROOT_DIR}/deploy/smoke/run-local-withdraw-smoke.sh"
fi

if [[ "${RUN_ADMIN_FORCED_LOGOUT_SMOKE}" == "true" ]]; then
  run_step \
    "admin forced logout smoke (A006 / A003 / relogin)" \
    "${ROOT_DIR}/deploy/smoke/run-local-admin-forced-logout-smoke.sh"
fi

printf '\nall auth/session smoke scripts passed\n'
echo "app_base_url=${APP_BASE_URL:-}"
echo "run_runtime_api_smoke=${RUN_RUNTIME_API_SMOKE}"
echo "run_login_failure_tracking_smoke=${RUN_LOGIN_FAILURE_TRACKING_SMOKE}"
echo "run_account_lockout_smoke=${RUN_ACCOUNT_LOCKOUT_SMOKE}"
echo "run_withdraw_smoke=${RUN_WITHDRAW_SMOKE}"
echo "run_admin_forced_logout_smoke=${RUN_ADMIN_FORCED_LOGOUT_SMOKE}"
