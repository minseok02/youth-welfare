#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS:-14}"
TREND_WINDOW_DAYS_CSV="${TREND_WINDOW_DAYS_CSV:-1,7,30}"
BREAKDOWN_LIMIT="${BREAKDOWN_LIMIT:-3}"
COLLECT_LIMIT="${COLLECT_LIMIT:-5}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
SUITE_SUMMARY="${ARTIFACT_DIR}/ops-baseline-summary.txt"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

normalize_bool() {
  local value="${1,,}"
  case "${value}" in
    true|false) printf '%s' "${value}" ;;
    *)
      echo "unsupported boolean value: ${1}" >&2
      exit 1
      ;;
  esac
}

KEEP_ARTIFACTS="$(normalize_bool "${KEEP_ARTIFACTS}")"

run_step() {
  local label="$1"
  local script_path="$2"
  local step_dir="$3"
  local output_file="${step_dir}/stdout.txt"

  mkdir -p "${step_dir}"
  smoke_print_step "${label}"
  ARTIFACT_DIR="${step_dir}" \
  APP_BASE_URL="${APP_BASE_URL}" \
  SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS}" \
  TREND_WINDOW_DAYS_CSV="${TREND_WINDOW_DAYS_CSV}" \
  BREAKDOWN_LIMIT="${BREAKDOWN_LIMIT}" \
  COLLECT_LIMIT="${COLLECT_LIMIT}" \
  bash "${script_path}" | tee "${output_file}"
}

smoke_require_command bash
smoke_require_command tee

run_step "admin dashboard smoke" \
  "${ROOT_DIR}/deploy/smoke/run-local-admin-dashboard-smoke.sh" \
  "${ARTIFACT_DIR}/admin-dashboard"

run_step "admin collect failures smoke" \
  "${ROOT_DIR}/deploy/smoke/run-local-admin-collect-failures-smoke.sh" \
  "${ARTIFACT_DIR}/admin-collect-failures"

run_step "admin recommendation breakdown smoke" \
  "${ROOT_DIR}/deploy/smoke/run-local-admin-recommendation-breakdowns-smoke.sh" \
  "${ARTIFACT_DIR}/admin-recommendation-breakdowns"

{
  echo "ops baseline suite passed"
  echo "app_base_url=${APP_BASE_URL}"
  echo "summary_window_days=${SUMMARY_WINDOW_DAYS}"
  echo "trend_window_days_csv=${TREND_WINDOW_DAYS_CSV}"
  echo "collect_limit=${COLLECT_LIMIT}"
  echo "breakdown_limit=${BREAKDOWN_LIMIT}"
  echo "artifact_dir=${ARTIFACT_DIR}"
  echo "admin_dashboard_stdout=${ARTIFACT_DIR}/admin-dashboard/stdout.txt"
  echo "admin_collect_failures_stdout=${ARTIFACT_DIR}/admin-collect-failures/stdout.txt"
  echo "admin_recommendation_breakdowns_stdout=${ARTIFACT_DIR}/admin-recommendation-breakdowns/stdout.txt"
} | tee "${SUITE_SUMMARY}"
