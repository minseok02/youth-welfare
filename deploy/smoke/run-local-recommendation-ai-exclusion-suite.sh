#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

TARGET_SERVICE_IDS_CSV="${TARGET_SERVICE_IDS_CSV:-3288,3289,3290,5837}"
TOP_REFRESH_LIMIT="${TOP_REFRESH_LIMIT:-20}"
TOP_N="${TOP_N:-20}"
SAMPLE_LIMIT="${SAMPLE_LIMIT:-10}"
BASELINE_COHORT="${BASELINE_COHORT:-non_example}"
TARGET_COHORT="${TARGET_COHORT:-real_user}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"

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

KEEP_ARTIFACTS="$(normalize_flag "${KEEP_ARTIFACTS}")"

run_prefixed() {
  local suite_name="$1"
  shift
  local output

  if ! output="$("$@" 2>&1)"; then
    while IFS= read -r line; do
      printf '[%s] %s\n' "${suite_name}" "${line}"
    done <<< "${output}"
    return 1
  fi

  while IFS= read -r line; do
    printf '[%s] %s\n' "${suite_name}" "${line}"
  done <<< "${output}"
}

run_prefixed "stage-gap" env \
  APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}" \
  TARGET_USER_KEY="${TARGET_USER_KEY:-}" \
  USER_EMAIL="${USER_EMAIL:-}" \
  USER_PASSWORD="${USER_PASSWORD:-}" \
  USER_ACCESS_TOKEN="${USER_ACCESS_TOKEN:-}" \
  ADMIN_EMAIL="${ADMIN_EMAIL:-}" \
  ADMIN_PASSWORD="${ADMIN_PASSWORD:-}" \
  TARGET_SERVICE_IDS_CSV="${TARGET_SERVICE_IDS_CSV}" \
  TOP_REFRESH_LIMIT="${TOP_REFRESH_LIMIT}" \
  KEEP_ARTIFACTS="${KEEP_ARTIFACTS}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-stage-gap-audit.sh"

run_prefixed "zero-reason-bucket" env \
  APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}" \
  TARGET_USER_KEY="${TARGET_USER_KEY:-}" \
  USER_EMAIL="${USER_EMAIL:-}" \
  USER_PASSWORD="${USER_PASSWORD:-}" \
  USER_ACCESS_TOKEN="${USER_ACCESS_TOKEN:-}" \
  ADMIN_EMAIL="${ADMIN_EMAIL:-}" \
  ADMIN_PASSWORD="${ADMIN_PASSWORD:-}" \
  TOP_REFRESH_LIMIT="${TOP_REFRESH_LIMIT}" \
  KEEP_ARTIFACTS="${KEEP_ARTIFACTS}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-zero-reason-bucket-audit.sh"

run_prefixed "cohort-compare" env \
  BASELINE_COHORT="${BASELINE_COHORT}" \
  TARGET_COHORT="${TARGET_COHORT}" \
  TOP_N="${TOP_N}" \
  SAMPLE_LIMIT="${SAMPLE_LIMIT}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-zero-reason-cohort-compare.sh"

run_prefixed "real-user-ready" env \
  APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}" \
  ADMIN_EMAIL="${ADMIN_EMAIL:-}" \
  ADMIN_PASSWORD="${ADMIN_PASSWORD:-}" \
  TOP_N="${TOP_N}" \
  SAMPLE_LIMIT="${SAMPLE_LIMIT}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-real-user-exclusion-readiness-check.sh"

echo "recommendation ai exclusion suite passed"
