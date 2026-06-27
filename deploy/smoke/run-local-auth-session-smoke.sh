#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

RUN_RUNTIME_API_SMOKE="${RUN_RUNTIME_API_SMOKE:-true}"
RUN_LOGIN_FAILURE_TRACKING_SMOKE="${RUN_LOGIN_FAILURE_TRACKING_SMOKE:-true}"
RUN_ACCOUNT_LOCKOUT_SMOKE="${RUN_ACCOUNT_LOCKOUT_SMOKE:-true}"
RUN_WITHDRAW_SMOKE="${RUN_WITHDRAW_SMOKE:-true}"
RUN_ADMIN_FORCED_LOGOUT_SMOKE="${RUN_ADMIN_FORCED_LOGOUT_SMOKE:-true}"
AUTH_SESSION_ROOT="${AUTH_SESSION_ROOT:-${ROOT_DIR}/tmp/auth-session-smoke}"
RUN_TS_UTC="${RUN_TS_UTC:-$(smoke_now_ts_utc)}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${AUTH_SESSION_ROOT}/${RUN_TS_UTC}}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"

RUNTIME_API_ARTIFACT_DIR="${ARTIFACT_DIR}/runtime-api"
LOGIN_FAILURE_TRACKING_ARTIFACT_DIR="${ARTIFACT_DIR}/login-failure-tracking"
ACCOUNT_LOCKOUT_ARTIFACT_DIR="${ARTIFACT_DIR}/account-lockout"
WITHDRAW_ARTIFACT_DIR="${ARTIFACT_DIR}/withdraw"
ADMIN_FORCED_LOGOUT_ARTIFACT_DIR="${ARTIFACT_DIR}/admin-forced-logout"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
RUN_RUNTIME_API_SMOKE="$(smoke_normalize_bool "${RUN_RUNTIME_API_SMOKE}")"
RUN_LOGIN_FAILURE_TRACKING_SMOKE="$(smoke_normalize_bool "${RUN_LOGIN_FAILURE_TRACKING_SMOKE}")"
RUN_ACCOUNT_LOCKOUT_SMOKE="$(smoke_normalize_bool "${RUN_ACCOUNT_LOCKOUT_SMOKE}")"
RUN_WITHDRAW_SMOKE="$(smoke_normalize_bool "${RUN_WITHDRAW_SMOKE}")"
RUN_ADMIN_FORCED_LOGOUT_SMOKE="$(smoke_normalize_bool "${RUN_ADMIN_FORCED_LOGOUT_SMOKE}")"

trap cleanup EXIT

smoke_require_command bash
mkdir -p "${ARTIFACT_DIR}"

run_step() {
  local label="$1"
  local script_path="$2"
  local child_artifact_dir="$3"

  printf '\n=== %s ===\n' "${label}"
  mkdir -p "${child_artifact_dir}"
  KEEP_ARTIFACTS=true ARTIFACT_DIR="${child_artifact_dir}" "${script_path}"
}

print_step_artifact_dir() {
  local key="$1"
  local enabled="$2"
  local child_artifact_dir="$3"
  local value=""

  if [[ "${enabled}" == "true" ]]; then
    value="${child_artifact_dir}"
  fi

  echo "${key}=${value}"
}

if [[ "${RUN_RUNTIME_API_SMOKE}" == "true" ]]; then
  run_step \
    "runtime api smoke (logout / refresh invalidation / presented access revoke)" \
    "${ROOT_DIR}/deploy/smoke/run-local-runtime-api-smoke.sh" \
    "${RUNTIME_API_ARTIFACT_DIR}"
fi

if [[ "${RUN_LOGIN_FAILURE_TRACKING_SMOKE}" == "true" ]]; then
  run_step \
    "login failure tracking smoke (A004 persistence / success reset)" \
    "${ROOT_DIR}/deploy/smoke/run-local-login-failure-tracking-smoke.sh" \
    "${LOGIN_FAILURE_TRACKING_ARTIFACT_DIR}"
fi

if [[ "${RUN_ACCOUNT_LOCKOUT_SMOKE}" == "true" ]]; then
  run_step \
    "account lockout smoke (threshold lock / A005)" \
    "${ROOT_DIR}/deploy/smoke/run-local-account-lockout-smoke.sh" \
    "${ACCOUNT_LOCKOUT_ARTIFACT_DIR}"
fi

if [[ "${RUN_WITHDRAW_SMOKE}" == "true" ]]; then
  run_step \
    "withdraw smoke (A006 / U003 / withdrawn mask)" \
    "${ROOT_DIR}/deploy/smoke/run-local-withdraw-smoke.sh" \
    "${WITHDRAW_ARTIFACT_DIR}"
fi

if [[ "${RUN_ADMIN_FORCED_LOGOUT_SMOKE}" == "true" ]]; then
  run_step \
    "admin forced logout smoke (A006 / A003 / relogin)" \
    "${ROOT_DIR}/deploy/smoke/run-local-admin-forced-logout-smoke.sh" \
    "${ADMIN_FORCED_LOGOUT_ARTIFACT_DIR}"
fi

printf '\nall auth/session smoke scripts passed\n'
echo "app_base_url=${APP_BASE_URL:-}"
echo "auth_session_artifact_dir=${ARTIFACT_DIR}"
echo "run_runtime_api_smoke=${RUN_RUNTIME_API_SMOKE}"
echo "run_login_failure_tracking_smoke=${RUN_LOGIN_FAILURE_TRACKING_SMOKE}"
echo "run_account_lockout_smoke=${RUN_ACCOUNT_LOCKOUT_SMOKE}"
echo "run_withdraw_smoke=${RUN_WITHDRAW_SMOKE}"
echo "run_admin_forced_logout_smoke=${RUN_ADMIN_FORCED_LOGOUT_SMOKE}"
print_step_artifact_dir "runtime_api_artifact_dir" "${RUN_RUNTIME_API_SMOKE}" "${RUNTIME_API_ARTIFACT_DIR}"
print_step_artifact_dir "login_failure_tracking_artifact_dir" "${RUN_LOGIN_FAILURE_TRACKING_SMOKE}" "${LOGIN_FAILURE_TRACKING_ARTIFACT_DIR}"
print_step_artifact_dir "account_lockout_artifact_dir" "${RUN_ACCOUNT_LOCKOUT_SMOKE}" "${ACCOUNT_LOCKOUT_ARTIFACT_DIR}"
print_step_artifact_dir "withdraw_artifact_dir" "${RUN_WITHDRAW_SMOKE}" "${WITHDRAW_ARTIFACT_DIR}"
print_step_artifact_dir "admin_forced_logout_artifact_dir" "${RUN_ADMIN_FORCED_LOGOUT_SMOKE}" "${ADMIN_FORCED_LOGOUT_ARTIFACT_DIR}"
