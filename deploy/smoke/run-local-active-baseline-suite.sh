#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_BACKEND_TESTS="${RUN_BACKEND_TESTS:-true}"
RUN_FRONTEND_BASELINE="${RUN_FRONTEND_BASELINE:-true}"
RUN_OPS_BASELINE="${RUN_OPS_BASELINE:-true}"
RUN_COLLECT_LEGACY_REPAIR="${RUN_COLLECT_LEGACY_REPAIR:-true}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
SUMMARY_OUT="${ARTIFACT_DIR}/active-baseline-summary.txt"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

run_command_step() {
  local label="$1"
  local output_file="$2"
  shift 2

  smoke_print_step "${label}"
  "$@" | tee "${output_file}"
}

run_script_step() {
  local label="$1"
  local output_file="$2"
  shift 2

  smoke_print_step "${label}"
  "$@" | tee "${output_file}"
}

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
RUN_BACKEND_TESTS="$(smoke_normalize_bool "${RUN_BACKEND_TESTS}")"
RUN_FRONTEND_BASELINE="$(smoke_normalize_bool "${RUN_FRONTEND_BASELINE}")"
RUN_OPS_BASELINE="$(smoke_normalize_bool "${RUN_OPS_BASELINE}")"
RUN_COLLECT_LEGACY_REPAIR="$(smoke_normalize_bool "${RUN_COLLECT_LEGACY_REPAIR}")"

smoke_require_command bash
smoke_require_command tee

mkdir -p "${ARTIFACT_DIR}"

if [[ "${RUN_BACKEND_TESTS}" == "true" ]]; then
  run_command_step \
    "backend tests" \
    "${ARTIFACT_DIR}/backend-test.txt" \
    bash -lc "cd '${ROOT_DIR}/backend' && ./gradlew test --no-daemon"
fi

if [[ "${RUN_FRONTEND_BASELINE}" == "true" ]]; then
  run_command_step \
    "frontend lint/build/e2e" \
    "${ARTIFACT_DIR}/frontend-baseline.txt" \
    bash -lc "cd '${ROOT_DIR}/frontend' && npm run lint && npm run build && npm run test:e2e"
fi

if [[ "${RUN_OPS_BASELINE}" == "true" ]]; then
  run_script_step \
    "ops baseline suite" \
    "${ARTIFACT_DIR}/ops-baseline.txt" \
    env ARTIFACT_DIR="${ARTIFACT_DIR}/ops-baseline-artifacts" \
    APP_BASE_URL="${APP_BASE_URL}" \
    KEEP_ARTIFACTS=true \
    bash "${ROOT_DIR}/deploy/smoke/run-local-ops-baseline-suite.sh"
fi

if [[ "${RUN_COLLECT_LEGACY_REPAIR}" == "true" ]]; then
  run_script_step \
    "collect legacy repair suite" \
    "${ARTIFACT_DIR}/collect-legacy-repair.txt" \
    env ARTIFACT_DIR="${ARTIFACT_DIR}/collect-legacy-repair-artifacts" \
    APP_BASE_URL="${APP_BASE_URL}" \
    KEEP_ARTIFACTS=true \
    bash "${ROOT_DIR}/deploy/smoke/run-local-collect-legacy-repair-suite.sh"
fi

{
  echo "active_baseline_suite=passed"
  echo "app_base_url=${APP_BASE_URL}"
  echo "artifact_dir=${ARTIFACT_DIR}"
  echo "run_backend_tests=${RUN_BACKEND_TESTS}"
  echo "run_frontend_baseline=${RUN_FRONTEND_BASELINE}"
  echo "run_ops_baseline=${RUN_OPS_BASELINE}"
  echo "run_collect_legacy_repair=${RUN_COLLECT_LEGACY_REPAIR}"
  if [[ "${RUN_BACKEND_TESTS}" == "true" ]]; then
    echo "backend_test_stdout=${ARTIFACT_DIR}/backend-test.txt"
  fi
  if [[ "${RUN_FRONTEND_BASELINE}" == "true" ]]; then
    echo "frontend_baseline_stdout=${ARTIFACT_DIR}/frontend-baseline.txt"
  fi
  if [[ "${RUN_OPS_BASELINE}" == "true" ]]; then
    echo "ops_baseline_stdout=${ARTIFACT_DIR}/ops-baseline.txt"
  fi
  if [[ "${RUN_COLLECT_LEGACY_REPAIR}" == "true" ]]; then
    echo "collect_legacy_repair_stdout=${ARTIFACT_DIR}/collect-legacy-repair.txt"
  fi
} | tee "${SUMMARY_OUT}"
