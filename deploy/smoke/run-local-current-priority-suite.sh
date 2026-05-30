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
DURATIONS_TSV="${ARTIFACT_DIR}/current-priority-durations.tsv"
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
printf 'label\texit_code\tduration_ms\toutput_file\n' > "${DURATIONS_TSV}"

run_priority_step() {
  local label="$1"
  local output_file="$2"
  shift 2

  set +e
  smoke_duration_step "${label}" "${output_file}" "$@" >> "${DURATIONS_TSV}"
  local exit_code=$?
  set -e
  cat "${output_file}"
  return "${exit_code}"
}

if [[ "${RUN_ACTIVE_BASELINE}" == "true" ]]; then
  smoke_print_step "active baseline"
  run_priority_step \
    "active_baseline" \
    "${ARTIFACT_DIR}/active-baseline.out" \
    env \
    APP_BASE_URL="${APP_BASE_URL}" \
    KEEP_ARTIFACTS=true \
    ARTIFACT_DIR="${ARTIFACT_DIR}/active-baseline-artifact" \
    FRONTEND_E2E_MODE="${FRONTEND_E2E_MODE:-local-dev}" \
    FRONTEND_PUBLIC_BASE_URL="${FRONTEND_PUBLIC_BASE_URL:-${PUBLIC_BASE_URL:-}}" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-active-baseline-suite.sh"
fi

if [[ "${RUN_RECOMMENDATION_OBSERVATION}" == "true" ]]; then
  smoke_print_step "recommendation observation"
  run_priority_step \
    "recommendation_observation" \
    "${ARTIFACT_DIR}/recommendation-observation.out" \
    env \
    APP_BASE_URL="${APP_BASE_URL}" \
    KEEP_ARTIFACTS=true \
    ARTIFACT_DIR="${ARTIFACT_DIR}/recommendation-observation-artifact" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-observation-suite.sh"
fi

python3 - "${DURATIONS_TSV}" "${SUMMARY_OUT}" "${JSON_OUT}" "${ARTIFACT_DIR}" "${APP_BASE_URL}" "${RUN_ACTIVE_BASELINE}" "${RUN_RECOMMENDATION_OBSERVATION}" <<'PY'
import csv
import json
import sys
from pathlib import Path

durations_path = Path(sys.argv[1])
summary_out = Path(sys.argv[2])
json_out = Path(sys.argv[3])
artifact_dir = sys.argv[4]
app_base_url = sys.argv[5]
run_active_baseline = sys.argv[6]
run_recommendation_observation = sys.argv[7]

rows = list(csv.DictReader(durations_path.open(encoding="utf-8"), delimiter="\t"))
failed = [row for row in rows if row["exit_code"] != "0"]

lines = [
    f"current_priority_suite={'failed' if failed else 'passed'}",
    f"artifact_dir={artifact_dir}",
    f"durations_tsv={artifact_dir}/current-priority-durations.tsv",
    f"app_base_url={app_base_url}",
    f"run_active_baseline={run_active_baseline}",
    f"run_recommendation_observation={run_recommendation_observation}",
]

for row in rows:
    seconds = int(row["duration_ms"]) / 1000
    lines.append(f"{row['label']}_duration_ms={row['duration_ms']}")
    lines.append(f"{row['label']}_duration_seconds={seconds:.3f}")

if run_active_baseline == "true":
    lines.append(f"active_baseline_stdout={artifact_dir}/active-baseline.out")
if run_recommendation_observation == "true":
    lines.append(f"recommendation_observation_stdout={artifact_dir}/recommendation-observation.out")

summary_out.write_text("\n".join(lines) + "\n", encoding="utf-8")
json_out.write_text(json.dumps({
    "artifact_dir": artifact_dir,
    "app_base_url": app_base_url,
    "run_active_baseline": run_active_baseline,
    "run_recommendation_observation": run_recommendation_observation,
    "steps": rows,
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(summary_out.read_text(encoding="utf-8"), end="")
if failed:
    raise SystemExit(1)
PY

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
