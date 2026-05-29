#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
CURRENT_PRIORITY_ROOT="${CURRENT_PRIORITY_ROOT:-${ROOT_DIR}/tmp/current-priority-suite}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_ACTIVE_BASELINE="${RUN_ACTIVE_BASELINE:-true}"
RUN_RECOMMENDATION_OBSERVATION="${RUN_RECOMMENDATION_OBSERVATION:-true}"

RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${CURRENT_PRIORITY_ROOT}/${RUN_TS_UTC}}"
SUMMARY_OUT="${ARTIFACT_DIR}/current-priority-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/current-priority-summary.json"
LATEST_ARTIFACT_LINK="${CURRENT_PRIORITY_ROOT}/latest"
LATEST_SUMMARY_LINK="${CURRENT_PRIORITY_ROOT}/latest-current-priority-summary.txt"
LATEST_JSON_LINK="${CURRENT_PRIORITY_ROOT}/latest-current-priority-summary.json"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
RUN_ACTIVE_BASELINE="$(smoke_normalize_bool "${RUN_ACTIVE_BASELINE}")"
RUN_RECOMMENDATION_OBSERVATION="$(smoke_normalize_bool "${RUN_RECOMMENDATION_OBSERVATION}")"

smoke_require_command bash
smoke_require_command tee
mkdir -p "${ARTIFACT_DIR}"

if [[ "${RUN_ACTIVE_BASELINE}" == "true" ]]; then
  smoke_print_step "active baseline"
  env \
    APP_BASE_URL="${APP_BASE_URL}" \
    KEEP_ARTIFACTS=true \
    ARTIFACT_DIR="${ARTIFACT_DIR}/active-baseline-artifact" \
    FRONTEND_E2E_MODE="${FRONTEND_E2E_MODE:-local-dev}" \
    FRONTEND_PUBLIC_BASE_URL="${FRONTEND_PUBLIC_BASE_URL:-${PUBLIC_BASE_URL:-}}" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-active-baseline-suite.sh" | tee "${ARTIFACT_DIR}/active-baseline.out"
fi

if [[ "${RUN_RECOMMENDATION_OBSERVATION}" == "true" ]]; then
  smoke_print_step "recommendation observation"
  env \
    APP_BASE_URL="${APP_BASE_URL}" \
    KEEP_ARTIFACTS=true \
    ARTIFACT_DIR="${ARTIFACT_DIR}/recommendation-observation-artifact" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-observation-suite.sh" | tee "${ARTIFACT_DIR}/recommendation-observation.out"
fi

{
  echo "current_priority_suite=passed"
  echo "artifact_dir=${ARTIFACT_DIR}"
  echo "app_base_url=${APP_BASE_URL}"
  echo "run_active_baseline=${RUN_ACTIVE_BASELINE}"
  echo "run_recommendation_observation=${RUN_RECOMMENDATION_OBSERVATION}"
  if [[ "${RUN_ACTIVE_BASELINE}" == "true" ]]; then
    echo "active_baseline_stdout=${ARTIFACT_DIR}/active-baseline.out"
  fi
  if [[ "${RUN_RECOMMENDATION_OBSERVATION}" == "true" ]]; then
    echo "recommendation_observation_stdout=${ARTIFACT_DIR}/recommendation-observation.out"
  fi
} | tee "${SUMMARY_OUT}"

python3 - "${SUMMARY_OUT}" "${JSON_OUT}" "${ARTIFACT_DIR}" <<'PY'
import json
import sys
from pathlib import Path

summary_out = Path(sys.argv[1])
json_out = Path(sys.argv[2])
artifact_dir = sys.argv[3]

values = {}
for raw_line in summary_out.read_text(encoding="utf-8").splitlines():
    if "=" not in raw_line:
        continue
    key, value = raw_line.split("=", 1)
    values[key.strip()] = value.strip()

values["artifact_dir"] = artifact_dir
json_out.write_text(json.dumps(values, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

smoke_publish_dir_snapshot "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
smoke_publish_file "${SUMMARY_OUT}" "${LATEST_SUMMARY_LINK}"
smoke_publish_file "${JSON_OUT}" "${LATEST_JSON_LINK}"

echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
