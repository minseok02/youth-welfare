#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env.production}"
COMPOSE_FILE="${COMPOSE_FILE:-${ROOT_DIR}/docker-compose.prod.elasticache.yml}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-https://youthmoa.kr}"
VERIFY_RUNTIME_ENV_RENDER="${VERIFY_RUNTIME_ENV_RENDER:-true}"
VERIFY_DB="${VERIFY_DB:-true}"
VERIFY_EDGE="${VERIFY_EDGE:-true}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"

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

VERIFY_RUNTIME_ENV_RENDER="$(normalize_bool "${VERIFY_RUNTIME_ENV_RENDER}")"
VERIFY_DB="$(normalize_bool "${VERIFY_DB}")"
VERIFY_EDGE="$(normalize_bool "${VERIFY_EDGE}")"
KEEP_ARTIFACTS="$(normalize_bool "${KEEP_ARTIFACTS}")"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
SUMMARY_FILE="${ARTIFACT_DIR}/prod-cutover-summary.txt"
RUNTIME_ENV_RENDER_TMP_DIR=""

cleanup() {
  if [[ -n "${RUNTIME_ENV_RENDER_TMP_DIR}" ]]; then
    rm -rf "${RUNTIME_ENV_RENDER_TMP_DIR}"
  fi
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

mkdir -p "${ARTIFACT_DIR}"
chmod 700 "${ARTIFACT_DIR}"

run_step() {
  local label="$1"
  local output_file="$2"
  local command_status
  local redact_status
  local tee_status
  local -a pipe_status
  shift 2

  echo
  echo "[prod-cutover] ${label}"
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

if [[ "${VERIFY_RUNTIME_ENV_RENDER}" == "true" ]]; then
  RUNTIME_ENV_RENDER_TMP_DIR="$(mktemp -d)"
  chmod 700 "${RUNTIME_ENV_RENDER_TMP_DIR}"
  run_step "runtime env render check" \
    "${ARTIFACT_DIR}/render-app-runtime-env.txt" \
    env SOURCE_ENV_FILE="${ENV_FILE}" TARGET_ENV_FILE="${RUNTIME_ENV_RENDER_TMP_DIR}/.env.runtime.production" STRICT_REQUIRED_RUNTIME_ENV=true \
    bash "${ROOT_DIR}/deploy/env/render-app-runtime-env.sh"
fi

run_step "env preflight" \
  "${ARTIFACT_DIR}/preflight-runtime-cutover-env.txt" \
  env ENV_FILE="${ENV_FILE}" COMPOSE_FILE="${COMPOSE_FILE}" PRINT_SUMMARY=true \
  bash "${ROOT_DIR}/deploy/smoke/preflight-runtime-cutover-env.sh"

if [[ "${VERIFY_DB}" == "true" ]]; then
  run_step "rds runtime privilege verify" \
    "${ARTIFACT_DIR}/verify-rds-runtime-privileges.txt" \
    env ENV_FILE="${ENV_FILE}" PRINT_SUMMARY=true \
    bash "${ROOT_DIR}/deploy/postgres/verify-rds-runtime-privileges.sh"
fi

if [[ "${VERIFY_EDGE}" == "true" ]]; then
  run_step "nginx edge baseline verify" \
    "${ARTIFACT_DIR}/verify-edge-baseline.txt" \
    env PUBLIC_BASE_URL="${PUBLIC_BASE_URL}" PRINT_SUMMARY=true \
    bash "${ROOT_DIR}/deploy/nginx/verify-edge-baseline.sh"
fi

{
  echo "prod cutover verification passed"
  echo "env_file=${ENV_FILE}"
  echo "compose_file=${COMPOSE_FILE}"
  echo "public_base_url=${PUBLIC_BASE_URL}"
  echo "verify_runtime_env_render=${VERIFY_RUNTIME_ENV_RENDER}"
  echo "verify_db=${VERIFY_DB}"
  echo "verify_edge=${VERIFY_EDGE}"
  echo "artifact_dir=${ARTIFACT_DIR}"
  if [[ "${VERIFY_RUNTIME_ENV_RENDER}" == "true" ]]; then
    echo "runtime_env_render_output=${ARTIFACT_DIR}/render-app-runtime-env.txt"
  fi
  echo "preflight_output=${ARTIFACT_DIR}/preflight-runtime-cutover-env.txt"
  if [[ "${VERIFY_DB}" == "true" ]]; then
    echo "db_verify_output=${ARTIFACT_DIR}/verify-rds-runtime-privileges.txt"
  fi
  if [[ "${VERIFY_EDGE}" == "true" ]]; then
    echo "edge_verify_output=${ARTIFACT_DIR}/verify-edge-baseline.txt"
  fi
} | tee "${SUMMARY_FILE}"
smoke_sanitize_artifacts "${ARTIFACT_DIR}"
