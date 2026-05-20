#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

REFRESH_ROOT="${REFRESH_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-baseline-refresh}"
BASELINE_REFRESH_SUMMARY="${BASELINE_REFRESH_SUMMARY:-}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"

RUN_TS_UTC="$(smoke_now_ts_utc)"
DRIFT_CHECK_ROOT="${DRIFT_CHECK_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-baseline-refresh-drift-check}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${DRIFT_CHECK_ROOT}/${RUN_TS_UTC}}"
REFRESH_DIR="${ARTIFACT_DIR}/refresh"
REFRESH_STDOUT="${ARTIFACT_DIR}/baseline-refresh.out"
COMPARE_STDOUT="${ARTIFACT_DIR}/baseline-refresh-compare.out"
SUMMARY_OUTPUT="${ARTIFACT_DIR}/baseline-refresh-drift-summary.txt"
LATEST_ARTIFACT_LINK="${DRIFT_CHECK_ROOT}/latest"
LATEST_COMPARE_LINK="${DRIFT_CHECK_ROOT}/latest-baseline-refresh-compare.out"
LATEST_SUMMARY_LINK="${DRIFT_CHECK_ROOT}/latest-baseline-refresh-drift-summary.txt"

find_recent_files() {
  local root="$1"
  local pattern="$2"
  find "${root}" -type f -name "${pattern}" | sort
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

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"

if [[ -z "${BASELINE_REFRESH_SUMMARY}" ]]; then
  mapfile -t SUMMARY_FILES < <(find_recent_files "${REFRESH_ROOT}" 'baseline-refresh-summary.txt')
  if (( ${#SUMMARY_FILES[@]} < 1 )); then
    echo "need BASELINE_REFRESH_SUMMARY or at least one baseline-refresh-summary.txt under ${REFRESH_ROOT}" >&2
    exit 1
  fi
  BASELINE_REFRESH_SUMMARY="${SUMMARY_FILES[$((${#SUMMARY_FILES[@]} - 1))]}"
fi

if [[ ! -f "${BASELINE_REFRESH_SUMMARY}" ]]; then
  echo "baseline refresh summary not found: ${BASELINE_REFRESH_SUMMARY}" >&2
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

smoke_print_step "recommendation ai exclusion baseline refresh"

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
RUN_COUNT="${RUN_COUNT:-2}" \
KEEP_ARTIFACTS=true \
ARTIFACT_DIR="${REFRESH_DIR}" \
bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh.sh" \
  | tee "${REFRESH_STDOUT}"

TARGET_REFRESH_SUMMARY="${REFRESH_DIR}/baseline-refresh-summary.txt"

if [[ -z "${TARGET_REFRESH_SUMMARY}" || ! -f "${TARGET_REFRESH_SUMMARY}" ]]; then
  echo "target refresh summary not found after baseline refresh: ${TARGET_REFRESH_SUMMARY}" >&2
  exit 1
fi

echo
smoke_print_step "recommendation ai exclusion baseline refresh compare"

BASELINE_REFRESH_SUMMARY="${BASELINE_REFRESH_SUMMARY}" \
TARGET_REFRESH_SUMMARY="${TARGET_REFRESH_SUMMARY}" \
bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh-compare.sh" \
  | tee "${COMPARE_STDOUT}"

DRIFT_DETECTED="$(extract_key_value "${COMPARE_STDOUT}" "drift_detected")"
CHANGED_KEYS="$(extract_key_value "${COMPARE_STDOUT}" "changed_keys")"
INTERPRETATION_CHANGED="$(extract_key_value "${COMPARE_STDOUT}" "interpretation_changed")"
STABLE_BASELINE_CHANGED="$(extract_key_value "${COMPARE_STDOUT}" "stable_baseline_changed")"
LATEST_OBSERVATION_CHANGED="$(extract_key_value "${COMPARE_STDOUT}" "latest_observation_changed")"

python3 - "${COMPARE_STDOUT}" "${BASELINE_REFRESH_SUMMARY}" "${TARGET_REFRESH_SUMMARY}" "${SUMMARY_OUTPUT}" <<'PY'
import sys
from pathlib import Path

compare_output = Path(sys.argv[1])
baseline_refresh_summary = sys.argv[2]
target_refresh_summary = sys.argv[3]
summary_output = Path(sys.argv[4])

wanted_keys = [
    "drift_detected",
    "interpretation_changed",
    "stable_baseline_changed",
    "latest_observation_changed",
    "changed_keys",
    "interpretation_changed_keys",
    "stable_changed_keys",
    "latest_changed_keys",
    "baseline_drift_class",
    "target_drift_class",
    "baseline_recommended_reading",
    "target_recommended_reading",
    "baseline_stable_baseline_zero_ai_reason_buckets",
    "target_stable_baseline_zero_ai_reason_buckets",
    "baseline_stable_dashboard_real_user_gate",
    "target_stable_dashboard_real_user_gate",
    "baseline_stable_breakdown_real_user_cohort_gate",
    "target_stable_breakdown_real_user_cohort_gate",
    "baseline_latest_fresh_top_ai_zero_count",
    "target_latest_fresh_top_ai_zero_count",
    "baseline_latest_ai_zero_count",
    "target_latest_ai_zero_count",
    "baseline_latest_ai_zero_reason_buckets",
    "target_latest_ai_zero_reason_buckets",
    "baseline_volatility_reference_frequency",
    "target_volatility_reference_frequency",
]

values = {}
for raw_line in compare_output.read_text(encoding="utf-8").splitlines():
    line = raw_line.strip()
    if "=" not in line:
        continue
    key, value = line.split("=", 1)
    values[key] = value

lines = [
    f"baseline_refresh_summary={baseline_refresh_summary}",
    f"target_refresh_summary={target_refresh_summary}",
]
for key in wanted_keys:
    lines.append(f"{key}={values.get(key, '')}")

summary_output.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"summary_output={summary_output}")
PY

mkdir -p "${DRIFT_CHECK_ROOT}"
smoke_update_links \
  "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}" \
  "${COMPARE_STDOUT}" "${LATEST_COMPARE_LINK}" \
  "${SUMMARY_OUTPUT}" "${LATEST_SUMMARY_LINK}"

echo
echo "recommendation ai exclusion baseline refresh drift check passed"
echo "baseline_refresh_summary=${BASELINE_REFRESH_SUMMARY}"
echo "target_refresh_summary=${TARGET_REFRESH_SUMMARY}"
echo "drift_detected=${DRIFT_DETECTED}"
echo "changed_keys=${CHANGED_KEYS}"
echo "interpretation_changed=${INTERPRETATION_CHANGED}"
echo "stable_baseline_changed=${STABLE_BASELINE_CHANGED}"
echo "latest_observation_changed=${LATEST_OBSERVATION_CHANGED}"
echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_compare_link=${LATEST_COMPARE_LINK}"
echo "summary_output=${SUMMARY_OUTPUT}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
