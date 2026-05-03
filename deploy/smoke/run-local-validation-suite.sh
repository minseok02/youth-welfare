#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

VALIDATION_PROFILE="${VALIDATION_PROFILE:-full}"
RUN_AUTH_SESSION_SMOKE="${RUN_AUTH_SESSION_SMOKE:-}"
RUN_RECOMMENDATION_CLICK_SMOKE="${RUN_RECOMMENDATION_CLICK_SMOKE:-}"
RUN_ADMIN_DASHBOARD_SMOKE="${RUN_ADMIN_DASHBOARD_SMOKE:-}"
RUN_REPLAY_SMOKE="${RUN_REPLAY_SMOKE:-}"
SUITE_START_EPOCH="$(date +%s)"
STEP_SUMMARY_LINES=()
CURRENT_STEP_LABEL=""

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
  local step_start
  local step_end
  local step_duration

  CURRENT_STEP_LABEL="${label}"
  smoke_print_step "${label}"
  step_start="$(date +%s)"
  bash "${script_path}"
  step_end="$(date +%s)"
  step_duration="$((step_end - step_start))"
  STEP_SUMMARY_LINES+=("${label}|${step_duration}")
  CURRENT_STEP_LABEL=""
}

on_error() {
  local failed_at_epoch
  failed_at_epoch="$(date +%s)"
  echo
  echo "local validation suite failed" >&2
  if [[ -n "${CURRENT_STEP_LABEL}" ]]; then
    echo "failed_step=${CURRENT_STEP_LABEL}" >&2
  fi
  echo "elapsed_before_failure_seconds=$((failed_at_epoch - SUITE_START_EPOCH))" >&2
}

trap on_error ERR

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

SUITE_END_EPOCH="$(date +%s)"
SUITE_DURATION_SECONDS="$((SUITE_END_EPOCH - SUITE_START_EPOCH))"

printf '\nlocal validation suite passed\n'
printf 'suite_duration_seconds=%s\n' "${SUITE_DURATION_SECONDS}"

for step_summary in "${STEP_SUMMARY_LINES[@]}"; do
  printf 'step_duration_seconds=%s\n' "${step_summary}"
done
