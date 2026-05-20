#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
TARGET_SERVICE_IDS_CSV="${TARGET_SERVICE_IDS_CSV:-3288,3289,3290,5837}"
TOP_REFRESH_LIMIT="${TOP_REFRESH_LIMIT:-20}"
TOP_N="${TOP_N:-20}"
SAMPLE_LIMIT="${SAMPLE_LIMIT:-10}"
BASELINE_COHORT="${BASELINE_COHORT:-non_example}"
TARGET_COHORT="${TARGET_COHORT:-real_user}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"

RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-snapshot/${RUN_TS_UTC}}"
SUITE_OUTPUT="${ARTIFACT_DIR}/ai-exclusion-suite.out"
SUMMARY_OUTPUT="${ARTIFACT_DIR}/ai-exclusion-snapshot-summary.txt"

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" != "true" ]]; then
    rm -rf "${ARTIFACT_DIR}"
  fi
}
trap cleanup EXIT

extract_prefixed_key() {
  local output_file="$1"
  local prefix="$2"
  local key="$3"
  python3 - "${output_file}" "${prefix}" "${key}" <<'PY'
import sys

output_file, prefix, key = sys.argv[1], sys.argv[2], sys.argv[3]
prefix_token = f"[{prefix}] "
key_prefix = f"{key}="
metric_prefix = f"METRIC {key}="

with open(output_file, "r", encoding="utf-8") as fp:
    for raw_line in fp:
        line = raw_line.strip()
        if not line.startswith(prefix_token):
            continue
        body = line[len(prefix_token):]
        if body.startswith(metric_prefix):
            print(body[len(metric_prefix):])
            break
        if body.startswith(key_prefix):
            print(body[len(key_prefix):])
            break
PY
}

mkdir -p "${ARTIFACT_DIR}"

smoke_require_command bash
smoke_require_command python3

smoke_print_step "recommendation ai exclusion suite snapshot"

env -u ARTIFACT_DIR \
  APP_BASE_URL="${APP_BASE_URL}" \
  TARGET_USER_KEY="${TARGET_USER_KEY:-}" \
  USER_EMAIL="${USER_EMAIL:-}" \
  USER_PASSWORD="${USER_PASSWORD:-}" \
  USER_ACCESS_TOKEN="${USER_ACCESS_TOKEN:-}" \
  ADMIN_EMAIL="${ADMIN_EMAIL:-}" \
  ADMIN_PASSWORD="${ADMIN_PASSWORD:-}" \
  TARGET_SERVICE_IDS_CSV="${TARGET_SERVICE_IDS_CSV}" \
  TOP_REFRESH_LIMIT="${TOP_REFRESH_LIMIT}" \
  TOP_N="${TOP_N}" \
  SAMPLE_LIMIT="${SAMPLE_LIMIT}" \
  BASELINE_COHORT="${BASELINE_COHORT}" \
  TARGET_COHORT="${TARGET_COHORT}" \
  KEEP_ARTIFACTS=true \
  bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-exclusion-suite.sh" \
  | tee "${SUITE_OUTPUT}"

STAGE_TARGETS="$(extract_prefixed_key "${SUITE_OUTPUT}" "stage-gap" "target_service_ids_csv")"
STAGE_ZERO_COUNT="$(extract_prefixed_key "${SUITE_OUTPUT}" "stage-gap" "fresh_top_ai_zero_count")"
ZERO_COUNT="$(extract_prefixed_key "${SUITE_OUTPUT}" "zero-reason-bucket" "ai_zero_count")"
ZERO_BUCKETS="$(extract_prefixed_key "${SUITE_OUTPUT}" "zero-reason-bucket" "ai_zero_reason_buckets")"
BASELINE_SCOPE_USERS="$(extract_prefixed_key "${SUITE_OUTPUT}" "cohort-compare" "baseline_scope_users")"
BASELINE_BUCKETS="$(extract_prefixed_key "${SUITE_OUTPUT}" "cohort-compare" "baseline_zero_ai_reason_buckets")"
TARGET_SCOPE_USERS="$(extract_prefixed_key "${SUITE_OUTPUT}" "cohort-compare" "target_scope_users")"
TARGET_BUCKETS="$(extract_prefixed_key "${SUITE_OUTPUT}" "cohort-compare" "target_zero_ai_reason_buckets")"
REAL_USER_EXECUTED="$(extract_prefixed_key "${SUITE_OUTPUT}" "real-user-ready" "real_user_distribution_executed")"
DASHBOARD_GATE="$(extract_prefixed_key "${SUITE_OUTPUT}" "real-user-ready" "dashboard_real_user_gate")"
BREAKDOWN_GATE="$(extract_prefixed_key "${SUITE_OUTPUT}" "real-user-ready" "breakdown_real_user_cohort_gate")"

{
  echo "snapshot_ts_utc=${RUN_TS_UTC}"
  echo "artifact_dir=${ARTIFACT_DIR}"
  echo "suite_output=${SUITE_OUTPUT}"
  echo "target_service_ids_csv=${STAGE_TARGETS}"
  echo "fresh_top_ai_zero_count=${STAGE_ZERO_COUNT}"
  echo "ai_zero_count=${ZERO_COUNT}"
  echo "ai_zero_reason_buckets=${ZERO_BUCKETS}"
  echo "baseline_cohort=${BASELINE_COHORT}"
  echo "baseline_scope_users=${BASELINE_SCOPE_USERS}"
  echo "baseline_zero_ai_reason_buckets=${BASELINE_BUCKETS}"
  echo "target_cohort=${TARGET_COHORT}"
  echo "target_scope_users=${TARGET_SCOPE_USERS}"
  echo "target_zero_ai_reason_buckets=${TARGET_BUCKETS}"
  echo "real_user_distribution_executed=${REAL_USER_EXECUTED}"
  echo "dashboard_real_user_gate=${DASHBOARD_GATE}"
  echo "breakdown_real_user_cohort_gate=${BREAKDOWN_GATE}"
} | tee "${SUMMARY_OUTPUT}"

echo
echo "recommendation ai exclusion snapshot passed"
echo "summary_output=${SUMMARY_OUTPUT}"
