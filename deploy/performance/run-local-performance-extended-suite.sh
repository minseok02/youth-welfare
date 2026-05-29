#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
EXTERNAL_BASE_URL="${EXTERNAL_BASE_URL:-https://youthmoa.kr}"
FRONTEND_PUBLIC_BASE_URL="${FRONTEND_PUBLIC_BASE_URL:-${EXTERNAL_BASE_URL}}"
EXTENDED_ROOT="${EXTENDED_ROOT:-${ROOT_DIR}/tmp/performance/extended-suite}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${EXTENDED_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
DURATIONS_TSV="${ARTIFACT_DIR}/performance-extended-durations.tsv"
SUMMARY_TXT="${ARTIFACT_DIR}/performance-extended-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/performance-extended-summary.json"
LATEST_DIR="${EXTENDED_ROOT}/latest"
LATEST_SUMMARY_TXT="${EXTENDED_ROOT}/latest-performance-extended-summary.txt"
LATEST_SUMMARY_JSON="${EXTENDED_ROOT}/latest-performance-extended-summary.json"

RUN_API_LOAD_BASELINE="${RUN_API_LOAD_BASELINE:-true}"
RUN_WEB_VITALS_BASELINE="${RUN_WEB_VITALS_BASELINE:-true}"
RUN_JVM_RUNTIME_BASELINE="${RUN_JVM_RUNTIME_BASELINE:-true}"
RUN_EDGE_BASELINE="${RUN_EDGE_BASELINE:-true}"
RUN_STATEFUL_FLOW_BASELINE="${RUN_STATEFUL_FLOW_BASELINE:-false}"

mkdir -p "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"
printf 'label\texit_code\tduration_ms\toutput_file\n' > "${DURATIONS_TSV}"

run_extended_step() {
  local label="$1"
  local output_file="$2"
  shift 2
  set +e
  perf_duration_step "${label}" "${output_file}" "$@" >> "${DURATIONS_TSV}"
  set -e
  return 0
}

if [[ "${RUN_API_LOAD_BASELINE}" == "true" ]]; then
  run_extended_step \
    "api_load" \
    "${ARTIFACT_DIR}/api-load.out" \
    env APP_BASE_URL="${APP_BASE_URL}" ARTIFACT_DIR="${ARTIFACT_DIR}/api-load" \
      DURATION_SECONDS="${DURATION_SECONDS:-20}" CONCURRENCY="${CONCURRENCY:-4}" \
      bash "${ROOT_DIR}/deploy/performance/run-local-api-load-baseline.sh"
fi

if [[ "${RUN_WEB_VITALS_BASELINE}" == "true" ]]; then
  run_extended_step \
    "web_vitals" \
    "${ARTIFACT_DIR}/web-vitals.out" \
    env FRONTEND_PUBLIC_BASE_URL="${FRONTEND_PUBLIC_BASE_URL}" ARTIFACT_DIR="${ARTIFACT_DIR}/web-vitals" \
      bash "${ROOT_DIR}/deploy/performance/run-local-web-vitals-baseline.sh"
fi

if [[ "${RUN_JVM_RUNTIME_BASELINE}" == "true" ]]; then
  run_extended_step \
    "jvm_runtime" \
    "${ARTIFACT_DIR}/jvm-runtime.out" \
    env APP_BASE_URL="${APP_BASE_URL}" ARTIFACT_DIR="${ARTIFACT_DIR}/jvm-runtime" \
      bash "${ROOT_DIR}/deploy/performance/run-local-jvm-runtime-baseline.sh"
fi

if [[ "${RUN_EDGE_BASELINE}" == "true" ]]; then
  run_extended_step \
    "edge" \
    "${ARTIFACT_DIR}/edge.out" \
    env EXTERNAL_BASE_URL="${EXTERNAL_BASE_URL}" ARTIFACT_DIR="${ARTIFACT_DIR}/edge" \
      bash "${ROOT_DIR}/deploy/performance/run-local-edge-baseline.sh"
fi

if [[ "${RUN_STATEFUL_FLOW_BASELINE}" == "true" ]]; then
  run_extended_step \
    "stateful_flow_duration" \
    "${ARTIFACT_DIR}/stateful-flow-duration.out" \
    env ENV_FILE="${ENV_FILE:-.env.production}" APP_BASE_URL="${APP_BASE_URL}" ARTIFACT_DIR="${ARTIFACT_DIR}/stateful-flow-duration" \
      bash "${ROOT_DIR}/deploy/performance/run-local-stateful-flow-duration-baseline.sh"
fi

python3 - "${DURATIONS_TSV}" "${SUMMARY_TXT}" "${SUMMARY_JSON}" "${CONTEXT_TXT}" <<'PY'
import csv
import json
import sys
from pathlib import Path

durations_path, summary_txt, summary_json, context_path = map(Path, sys.argv[1:5])
rows = list(csv.DictReader(durations_path.open(encoding="utf-8"), delimiter="\t"))
context = {}
for line in context_path.read_text(encoding="utf-8").splitlines():
    if "=" in line:
        key, value = line.split("=", 1)
        context[key] = value

failed = [row for row in rows if row["exit_code"] != "0"]
payload = {"context": context, "status": "failed" if failed else "passed", "steps": rows}
summary_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = [f"performance_extended_suite={'failed' if failed else 'passed'}"]
for row in rows:
    seconds = int(row["duration_ms"]) / 1000
    lines.append(f"{row['label']} exit_code={row['exit_code']} duration_ms={row['duration_ms']} duration_seconds={seconds:.3f}")
summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")
if failed:
    raise SystemExit(1)
PY

perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
