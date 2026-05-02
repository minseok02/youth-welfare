#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

RUN_RUNTIME_API_SMOKE="${RUN_RUNTIME_API_SMOKE:-true}"
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
