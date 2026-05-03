#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

VALIDATION_PROFILE="${VALIDATION_PROFILE:-full}"
RUN_AUTH_SESSION_SMOKE="${RUN_AUTH_SESSION_SMOKE:-}"
RUN_RECOMMENDATION_CLICK_SMOKE="${RUN_RECOMMENDATION_CLICK_SMOKE:-}"
RUN_ADMIN_DASHBOARD_SMOKE="${RUN_ADMIN_DASHBOARD_SMOKE:-}"
RUN_REPLAY_SMOKE="${RUN_REPLAY_SMOKE:-}"

normalize_flag() {
  local value="${1,,}"
  case "${value}" in
    true|false) printf '%s' "${value}" ;;
    *)
      echo "unsupported flag value: ${1}" >&2
      exit 1
      ;;
  esac
}

resolve_profile_defaults() {
  case "${VALIDATION_PROFILE}" in
    quick)
      RUN_AUTH_SESSION_SMOKE="${RUN_AUTH_SESSION_SMOKE:-true}"
      RUN_RECOMMENDATION_CLICK_SMOKE="${RUN_RECOMMENDATION_CLICK_SMOKE:-true}"
      RUN_ADMIN_DASHBOARD_SMOKE="${RUN_ADMIN_DASHBOARD_SMOKE:-true}"
      RUN_REPLAY_SMOKE="${RUN_REPLAY_SMOKE:-false}"
      ;;
    full)
      RUN_AUTH_SESSION_SMOKE="${RUN_AUTH_SESSION_SMOKE:-true}"
      RUN_RECOMMENDATION_CLICK_SMOKE="${RUN_RECOMMENDATION_CLICK_SMOKE:-true}"
      RUN_ADMIN_DASHBOARD_SMOKE="${RUN_ADMIN_DASHBOARD_SMOKE:-true}"
      RUN_REPLAY_SMOKE="${RUN_REPLAY_SMOKE:-true}"
      ;;
    *)
      echo "unsupported VALIDATION_PROFILE: ${VALIDATION_PROFILE} (expected quick or full)" >&2
      exit 1
      ;;
  esac

  RUN_AUTH_SESSION_SMOKE="$(normalize_flag "${RUN_AUTH_SESSION_SMOKE}")"
  RUN_RECOMMENDATION_CLICK_SMOKE="$(normalize_flag "${RUN_RECOMMENDATION_CLICK_SMOKE}")"
  RUN_ADMIN_DASHBOARD_SMOKE="$(normalize_flag "${RUN_ADMIN_DASHBOARD_SMOKE}")"
  RUN_REPLAY_SMOKE="$(normalize_flag "${RUN_REPLAY_SMOKE}")"
}

run_step() {
  local label="$1"
  local script_path="$2"

  smoke_print_step "${label}"
  bash "${script_path}"
}

resolve_profile_defaults

printf 'validation_profile=%s auth=%s click=%s dashboard=%s replay=%s\n' \
  "${VALIDATION_PROFILE}" \
  "${RUN_AUTH_SESSION_SMOKE}" \
  "${RUN_RECOMMENDATION_CLICK_SMOKE}" \
  "${RUN_ADMIN_DASHBOARD_SMOKE}" \
  "${RUN_REPLAY_SMOKE}"

if [[ "${RUN_AUTH_SESSION_SMOKE}" == "true" ]]; then
  run_step \
    "auth/session smoke wrapper (runtime + withdraw + admin forced logout)" \
    "${ROOT_DIR}/deploy/smoke/run-local-auth-session-smoke.sh"
fi

if [[ "${RUN_RECOMMENDATION_CLICK_SMOKE}" == "true" ]]; then
  run_step \
    "recommendation click smoke" \
    "${ROOT_DIR}/deploy/smoke/run-local-recommendation-click-smoke.sh"
fi

if [[ "${RUN_ADMIN_DASHBOARD_SMOKE}" == "true" ]]; then
  run_step \
    "admin dashboard smoke" \
    "${ROOT_DIR}/deploy/smoke/run-local-admin-dashboard-smoke.sh"
fi

if [[ "${RUN_REPLAY_SMOKE}" == "true" ]]; then
  run_step \
    "education priority replay smoke (run last; may restart DB/app dependencies)" \
    "${ROOT_DIR}/deploy/smoke/run-local-education-priority-replay.sh"
fi

printf '\nlocal validation suite passed\n'
