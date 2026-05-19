#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

SNAPSHOT_ROOT="${SNAPSHOT_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-snapshot}"
BASELINE_SUMMARY="${BASELINE_SUMMARY:-}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"

RUN_TS_UTC="$(date -u +%Y%m%dT%H%M%SZ)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${SNAPSHOT_ROOT}/${RUN_TS_UTC}}"
SNAPSHOT_STDOUT="${ARTIFACT_DIR}/snapshot.stdout"
COMPARE_STDOUT="${ARTIFACT_DIR}/compare.stdout"

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

find_recent_summaries() {
  find "${SNAPSHOT_ROOT}" -type f -name 'ai-exclusion-snapshot-summary.txt' | sort
}

extract_key_value() {
  local output_file="$1"
  local key="$2"
  python3 - "${output_file}" "${key}" <<'PY'
import sys

output_file, key = sys.argv[1], sys.argv[2]
prefix = f"{key}="

with open(output_file, "r", encoding="utf-8") as fp:
    for raw_line in fp:
        line = raw_line.strip()
        if line.startswith(prefix):
            print(line[len(prefix):])
            break
PY
}

KEEP_ARTIFACTS="$(normalize_flag "${KEEP_ARTIFACTS}")"

if [[ -z "${BASELINE_SUMMARY}" ]]; then
  mapfile -t SNAPSHOT_FILES < <(find_recent_summaries)
  if (( ${#SNAPSHOT_FILES[@]} < 1 )); then
    echo "need BASELINE_SUMMARY or at least one snapshot summary under ${SNAPSHOT_ROOT}" >&2
    exit 1
  fi
  BASELINE_SUMMARY="${SNAPSHOT_FILES[$((${#SNAPSHOT_FILES[@]} - 1))]}"
fi

if [[ ! -f "${BASELINE_SUMMARY}" ]]; then
  echo "baseline summary not found: ${BASELINE_SUMMARY}" >&2
  exit 1
fi

mkdir -p "${ARTIFACT_DIR}"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" != "true" ]]; then
    rm -rf "${ARTIFACT_DIR}"
  fi
}
trap cleanup EXIT

smoke_require_command bash
smoke_require_command python3

smoke_print_step "recommendation ai exclusion snapshot"

env \
  APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}" \
  TARGET_USER_KEY="${TARGET_USER_KEY:-}" \
  USER_EMAIL="${USER_EMAIL:-}" \
  USER_PASSWORD="${USER_PASSWORD:-}" \
  USER_ACCESS_TOKEN="${USER_ACCESS_TOKEN:-}" \
  ADMIN_EMAIL="${ADMIN_EMAIL:-}" \
  ADMIN_PASSWORD="${ADMIN_PASSWORD:-}" \
  TARGET_SERVICE_IDS_CSV="${TARGET_SERVICE_IDS_CSV:-3288,3289,3290,5837}" \
  TOP_REFRESH_LIMIT="${TOP_REFRESH_LIMIT:-20}" \
  TOP_N="${TOP_N:-20}" \
  SAMPLE_LIMIT="${SAMPLE_LIMIT:-10}" \
  BASELINE_COHORT="${BASELINE_COHORT:-non_example}" \
  TARGET_COHORT="${TARGET_COHORT:-real_user}" \
  KEEP_ARTIFACTS=true \
  ARTIFACT_DIR="${ARTIFACT_DIR}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-exclusion-snapshot.sh" \
  | tee "${SNAPSHOT_STDOUT}"

TARGET_SUMMARY="$(extract_key_value "${SNAPSHOT_STDOUT}" "summary_output")"

if [[ -z "${TARGET_SUMMARY}" || ! -f "${TARGET_SUMMARY}" ]]; then
  echo "target summary not found after snapshot run: ${TARGET_SUMMARY}" >&2
  exit 1
fi

echo
smoke_print_step "recommendation ai exclusion snapshot compare"

BASELINE_SUMMARY="${BASELINE_SUMMARY}" \
TARGET_SUMMARY="${TARGET_SUMMARY}" \
bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-exclusion-snapshot-compare.sh" \
  | tee "${COMPARE_STDOUT}"

DRIFT_DETECTED="$(extract_key_value "${COMPARE_STDOUT}" "drift_detected")"
CHANGED_KEYS="$(extract_key_value "${COMPARE_STDOUT}" "changed_keys")"

echo
echo "recommendation ai exclusion drift check passed"
echo "baseline_summary=${BASELINE_SUMMARY}"
echo "target_summary=${TARGET_SUMMARY}"
echo "drift_detected=${DRIFT_DETECTED}"
echo "changed_keys=${CHANGED_KEYS}"
echo "artifact_dir=${ARTIFACT_DIR}"
