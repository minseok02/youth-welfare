#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env.production}"
APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-https://youthmoa.kr}"
SMOKE_DB_MODE="${SMOKE_DB_MODE:-postgres}"
SMOKE_TRUSTED_ORIGIN="${SMOKE_TRUSTED_ORIGIN:-https://youthmoa.kr}"
SMOKE_TRUSTED_REFERER="${SMOKE_TRUSTED_REFERER:-${SMOKE_TRUSTED_ORIGIN}/}"
ALLOW_ADMIN_JWT_MINT="${ALLOW_ADMIN_JWT_MINT:-true}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"

RUN_CUTOVER_VERIFY="${RUN_CUTOVER_VERIFY:-true}"
RUN_RUNTIME_API_SMOKE="${RUN_RUNTIME_API_SMOKE:-true}"
RUN_AUTH_OBSERVATION="${RUN_AUTH_OBSERVATION:-true}"

ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/tmp/prod-runtime-smoke/$(smoke_now_ts_utc)}"
SUMMARY_FILE="${ARTIFACT_DIR}/prod-runtime-smoke-summary.txt"

normalize_flags() {
  KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
  RUN_CUTOVER_VERIFY="$(smoke_normalize_bool "${RUN_CUTOVER_VERIFY}")"
  RUN_RUNTIME_API_SMOKE="$(smoke_normalize_bool "${RUN_RUNTIME_API_SMOKE}")"
  RUN_AUTH_OBSERVATION="$(smoke_normalize_bool "${RUN_AUTH_OBSERVATION}")"
}

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

run_step() {
  local label="$1"
  local output_file="$2"
  local command_status
  local redact_status
  local tee_status
  local -a pipe_status
  shift 2

  smoke_print_step "prod runtime smoke: ${label}"
  set +e
  "$@" 2>&1 | smoke_redact_stream_for_log | tee "${output_file}"
  pipe_status=("${PIPESTATUS[@]}")
  command_status="${pipe_status[0]}"
  redact_status="${pipe_status[1]}"
  tee_status="${pipe_status[2]}"
  set -e
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"

  if [[ "${command_status}" != "0" ]]; then
    return "${command_status}"
  fi
  if [[ "${redact_status}" != "0" ]]; then
    return "${redact_status}"
  fi
  return "${tee_status}"
}

normalize_flags
smoke_require_command bash
smoke_require_command tee
mkdir -p "${ARTIFACT_DIR}"
chmod 700 "${ARTIFACT_DIR}"

if [[ "${RUN_CUTOVER_VERIFY}" == "true" ]]; then
  run_step "cutover verification" \
    "${ARTIFACT_DIR}/cutover-verification.txt" \
    env ENV_FILE="${ENV_FILE}" PUBLIC_BASE_URL="${PUBLIC_BASE_URL}" \
    bash "${ROOT_DIR}/deploy/smoke/run-prod-cutover-verification.sh"
fi

if [[ "${RUN_RUNTIME_API_SMOKE}" == "true" ]]; then
  run_step "runtime API smoke" \
    "${ARTIFACT_DIR}/runtime-api-smoke.txt" \
    env ENV_FILE="${ENV_FILE}" SMOKE_DB_MODE="${SMOKE_DB_MODE}" \
    APP_BASE_URL="${APP_BASE_URL}" \
    SMOKE_TRUSTED_ORIGIN="${SMOKE_TRUSTED_ORIGIN}" \
    SMOKE_TRUSTED_REFERER="${SMOKE_TRUSTED_REFERER}" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-runtime-api-smoke.sh"
fi

if [[ "${RUN_AUTH_OBSERVATION}" == "true" ]]; then
  run_step "auth observation" \
    "${ARTIFACT_DIR}/auth-observation.txt" \
    env ENV_FILE="${ENV_FILE}" SMOKE_DB_MODE="${SMOKE_DB_MODE}" \
    ALLOW_ADMIN_JWT_MINT="${ALLOW_ADMIN_JWT_MINT}" \
    APP_BASE_URL="${APP_BASE_URL}" \
    SMOKE_TRUSTED_ORIGIN="${SMOKE_TRUSTED_ORIGIN}" \
    SMOKE_TRUSTED_REFERER="${SMOKE_TRUSTED_REFERER}" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-auth-observation-suite.sh"
fi

{
  echo "prod_runtime_smoke_suite=passed"
  echo "artifact_dir=${ARTIFACT_DIR}"
  echo "env_file=${ENV_FILE}"
  echo "app_base_url=${APP_BASE_URL}"
  echo "public_base_url=${PUBLIC_BASE_URL}"
  echo "smoke_db_mode=${SMOKE_DB_MODE}"
  echo "smoke_trusted_origin=${SMOKE_TRUSTED_ORIGIN}"
  echo "run_cutover_verify=${RUN_CUTOVER_VERIFY}"
  echo "run_runtime_api_smoke=${RUN_RUNTIME_API_SMOKE}"
  echo "run_auth_observation=${RUN_AUTH_OBSERVATION}"
  if [[ "${RUN_CUTOVER_VERIFY}" == "true" ]]; then
    echo "cutover_output=${ARTIFACT_DIR}/cutover-verification.txt"
  fi
  if [[ "${RUN_RUNTIME_API_SMOKE}" == "true" ]]; then
    echo "runtime_api_output=${ARTIFACT_DIR}/runtime-api-smoke.txt"
  fi
  if [[ "${RUN_AUTH_OBSERVATION}" == "true" ]]; then
    echo "auth_observation_output=${ARTIFACT_DIR}/auth-observation.txt"
  fi
} | tee "${SUMMARY_FILE}"
smoke_sanitize_artifacts "${ARTIFACT_DIR}"
