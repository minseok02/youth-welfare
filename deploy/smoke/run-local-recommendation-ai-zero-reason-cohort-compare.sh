#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

BASELINE_COHORT="${BASELINE_COHORT:-non_example}"
TARGET_COHORT="${TARGET_COHORT:-real_user}"
TOP_N="${TOP_N:-20}"
SAMPLE_LIMIT="${SAMPLE_LIMIT:-10}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
BASELINE_OUTPUT="${ARTIFACT_DIR}/baseline.out"
TARGET_OUTPUT="${ARTIFACT_DIR}/target.out"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

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

smoke_require_command bash

smoke_print_step "baseline cohort distribution (${BASELINE_COHORT})"
USER_COHORT="${BASELINE_COHORT}" \
TOP_N="${TOP_N}" \
SAMPLE_LIMIT="${SAMPLE_LIMIT}" \
bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-zero-reason-distribution-audit.sh" | tee "${BASELINE_OUTPUT}"

smoke_print_step "target cohort distribution (${TARGET_COHORT})"
USER_COHORT="${TARGET_COHORT}" \
TOP_N="${TOP_N}" \
SAMPLE_LIMIT="${SAMPLE_LIMIT}" \
bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-zero-reason-distribution-audit.sh" | tee "${TARGET_OUTPUT}"

BASELINE_SCOPE_USERS="$(extract_key_value "${BASELINE_OUTPUT}" "scope_users")"
BASELINE_ZERO_ROWS="$(extract_key_value "${BASELINE_OUTPUT}" "zero_ai_rows")"
BASELINE_BUCKETS="$(extract_key_value "${BASELINE_OUTPUT}" "zero_ai_reason_buckets")"

TARGET_SCOPE_USERS="$(extract_key_value "${TARGET_OUTPUT}" "scope_users")"
TARGET_ZERO_ROWS="$(extract_key_value "${TARGET_OUTPUT}" "zero_ai_rows")"
TARGET_BUCKETS="$(extract_key_value "${TARGET_OUTPUT}" "zero_ai_reason_buckets")"

echo
echo "baseline_cohort=${BASELINE_COHORT}"
echo "baseline_scope_users=${BASELINE_SCOPE_USERS}"
echo "baseline_zero_ai_rows=${BASELINE_ZERO_ROWS}"
echo "baseline_zero_ai_reason_buckets=${BASELINE_BUCKETS}"
echo "target_cohort=${TARGET_COHORT}"
echo "target_scope_users=${TARGET_SCOPE_USERS}"
echo "target_zero_ai_rows=${TARGET_ZERO_ROWS}"
echo "target_zero_ai_reason_buckets=${TARGET_BUCKETS}"
