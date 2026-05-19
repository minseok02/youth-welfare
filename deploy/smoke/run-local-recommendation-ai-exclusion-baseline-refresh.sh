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
RUN_COUNT="${RUN_COUNT:-2}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"

RUN_TS_UTC="$(smoke_now_ts_utc)"
REFRESH_ROOT="${REFRESH_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-baseline-refresh}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${REFRESH_ROOT}/${RUN_TS_UTC}}"
VOLATILITY_DIR="${ARTIFACT_DIR}/volatility"
VOLATILITY_OUTPUT="${ARTIFACT_DIR}/volatility.out"
REPORT_OUTPUT="${ARTIFACT_DIR}/baseline-report.out"
SUMMARY_OUTPUT="${ARTIFACT_DIR}/baseline-refresh-summary.txt"
LATEST_ARTIFACT_LINK="${REFRESH_ROOT}/latest"
LATEST_SUMMARY_LINK="${REFRESH_ROOT}/latest-baseline-refresh-summary.txt"
LATEST_REPORT_LINK="${REFRESH_ROOT}/latest-baseline-report.out"

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

mkdir -p "${ARTIFACT_DIR}"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" != "true" ]]; then
    rm -rf "${ARTIFACT_DIR}"
  fi
}
trap cleanup EXIT

smoke_require_command bash
smoke_require_command python3

smoke_print_step "ai exclusion volatility audit"

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
RUN_COUNT="${RUN_COUNT}" \
KEEP_ARTIFACTS=true \
ARTIFACT_DIR="${VOLATILITY_DIR}" \
bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-exclusion-volatility-audit.sh" \
  | tee "${VOLATILITY_OUTPUT}"

VOLATILITY_SUMMARY="${VOLATILITY_DIR}/volatility-summary.txt"
VOLATILITY_BASELINE_SUMMARY="$(extract_key_value "${VOLATILITY_SUMMARY}" "baseline_summary")"

if [[ -z "${VOLATILITY_SUMMARY}" || ! -f "${VOLATILITY_SUMMARY}" ]]; then
  echo "volatility summary not found after refresh: ${VOLATILITY_SUMMARY}" >&2
  exit 1
fi

BASELINE_SUMMARY="${VOLATILITY_BASELINE_SUMMARY:-$(extract_key_value "${VOLATILITY_SUMMARY}" "baseline_summary")}"
TARGET_SUMMARY="$(extract_key_value "${VOLATILITY_SUMMARY}" "run_$(printf '%02d' "${RUN_COUNT}")_target_summary")"
VOLATILITY_ARTIFACT_DIR="$(extract_key_value "${VOLATILITY_SUMMARY}" "artifact_dir")"

if [[ -z "${BASELINE_SUMMARY}" || ! -f "${BASELINE_SUMMARY}" ]]; then
  echo "baseline summary missing from volatility summary: ${BASELINE_SUMMARY}" >&2
  exit 1
fi

if [[ -z "${TARGET_SUMMARY}" && -n "${VOLATILITY_ARTIFACT_DIR}" ]]; then
  TARGET_SUMMARY="${VOLATILITY_ARTIFACT_DIR}/run-$(printf '%02d' "${RUN_COUNT}")/ai-exclusion-snapshot-summary.txt"
fi

if [[ -z "${TARGET_SUMMARY}" || ! -f "${TARGET_SUMMARY}" ]]; then
  echo "target summary missing from volatility summary: ${TARGET_SUMMARY}" >&2
  exit 1
fi

echo
smoke_print_step "ai exclusion baseline report"

VOLATILITY_SUMMARY="${VOLATILITY_SUMMARY}" \
BASELINE_SUMMARY="${BASELINE_SUMMARY}" \
TARGET_SUMMARY="${TARGET_SUMMARY}" \
bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-exclusion-baseline-report.sh" \
  | tee "${REPORT_OUTPUT}"

python3 - "${REPORT_OUTPUT}" "${VOLATILITY_SUMMARY}" "${BASELINE_SUMMARY}" "${TARGET_SUMMARY}" "${SUMMARY_OUTPUT}" <<'PY'
import sys
from pathlib import Path

report_output = Path(sys.argv[1])
volatility_summary = sys.argv[2]
baseline_summary = sys.argv[3]
target_summary = sys.argv[4]
summary_output = Path(sys.argv[5])

wanted_keys = [
    "drift_class",
    "recommended_reading",
    "stable_baseline_zero_ai_reason_buckets",
    "stable_dashboard_real_user_gate",
    "stable_breakdown_real_user_cohort_gate",
    "latest_fresh_top_ai_zero_count",
    "latest_ai_zero_count",
    "latest_ai_zero_reason_buckets",
    "volatility_reference_frequency",
]

values = {}
for raw_line in report_output.read_text(encoding="utf-8").splitlines():
    line = raw_line.strip()
    if "=" not in line:
        continue
    key, value = line.split("=", 1)
    values[key] = value

lines = [
    f"volatility_summary={volatility_summary}",
    f"baseline_summary={baseline_summary}",
    f"target_summary={target_summary}",
]
for key in wanted_keys:
    lines.append(f"{key}={values.get(key, '')}")

summary_output.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"summary_output={summary_output}")
PY

mkdir -p "${REFRESH_ROOT}"
smoke_update_links \
  "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}" \
  "${SUMMARY_OUTPUT}" "${LATEST_SUMMARY_LINK}" \
  "${REPORT_OUTPUT}" "${LATEST_REPORT_LINK}"

echo
echo "recommendation ai exclusion baseline refresh passed"
echo "artifact_dir=${ARTIFACT_DIR}"
echo "volatility_summary=${VOLATILITY_SUMMARY}"
echo "baseline_summary=${BASELINE_SUMMARY}"
echo "target_summary=${TARGET_SUMMARY}"
echo "baseline_report=${REPORT_OUTPUT}"
echo "summary_output=${SUMMARY_OUTPUT}"
echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
echo "latest_report_link=${LATEST_REPORT_LINK}"
