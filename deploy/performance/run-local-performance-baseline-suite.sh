#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
PERFORMANCE_ROOT="${PERFORMANCE_ROOT:-${ROOT_DIR}/tmp/performance/full-suite}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${PERFORMANCE_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
DURATIONS_TSV="${ARTIFACT_DIR}/performance-suite-durations.tsv"
SUMMARY_TXT="${ARTIFACT_DIR}/performance-baseline-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/performance-baseline-summary.json"
LATEST_DIR="${PERFORMANCE_ROOT}/latest"
LATEST_SUMMARY_TXT="${PERFORMANCE_ROOT}/latest-performance-baseline-summary.txt"
LATEST_SUMMARY_JSON="${PERFORMANCE_ROOT}/latest-performance-baseline-summary.json"

RUN_API_BASELINE="${RUN_API_BASELINE:-true}"
RUN_DB_BASELINE="${RUN_DB_BASELINE:-true}"
RUN_REDIS_BASELINE="${RUN_REDIS_BASELINE:-true}"
RUN_WRAPPER_BASELINE="${RUN_WRAPPER_BASELINE:-false}"

mkdir -p "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"
printf 'label\texit_code\tduration_ms\toutput_file\n' > "${DURATIONS_TSV}"

run_suite_step() {
  local label="$1"
  local output_file="$2"
  shift 2
  set +e
  perf_duration_step "${label}" "${output_file}" "$@" >> "${DURATIONS_TSV}"
  set -e
  return 0
}

if [[ "${RUN_API_BASELINE}" == "true" ]]; then
  run_suite_step \
    "api_latency" \
    "${ARTIFACT_DIR}/api-latency.out" \
    env APP_BASE_URL="${APP_BASE_URL}" ARTIFACT_DIR="${ARTIFACT_DIR}/api-latency" RUNS="${RUNS:-7}" WARMUP_RUNS="${WARMUP_RUNS:-1}" \
      bash "${ROOT_DIR}/deploy/performance/run-local-api-latency-baseline.sh"
fi

if [[ "${RUN_DB_BASELINE}" == "true" ]]; then
  run_suite_step \
    "db_query" \
    "${ARTIFACT_DIR}/db-query.out" \
    env ENV_FILE="${ENV_FILE:-.env.production}" SMOKE_DB_MODE="${SMOKE_DB_MODE:-postgres}" ARTIFACT_DIR="${ARTIFACT_DIR}/db-query" \
      bash "${ROOT_DIR}/deploy/performance/run-local-db-query-baseline.sh"
fi

if [[ "${RUN_REDIS_BASELINE}" == "true" ]]; then
  run_suite_step \
    "redis" \
    "${ARTIFACT_DIR}/redis.out" \
    env ARTIFACT_DIR="${ARTIFACT_DIR}/redis" \
      bash "${ROOT_DIR}/deploy/performance/run-local-redis-baseline.sh"
fi

if [[ "${RUN_WRAPPER_BASELINE}" == "true" ]]; then
  run_suite_step \
    "wrapper_duration" \
    "${ARTIFACT_DIR}/wrapper-duration.out" \
    env ENV_FILE="${ENV_FILE:-.env.production}" SMOKE_DB_MODE="${SMOKE_DB_MODE:-postgres}" APP_BASE_URL="${APP_BASE_URL}" ARTIFACT_DIR="${ARTIFACT_DIR}/wrapper-duration" \
      bash "${ROOT_DIR}/deploy/performance/run-local-wrapper-duration-baseline.sh"
fi

python3 - "${DURATIONS_TSV}" "${SUMMARY_TXT}" "${SUMMARY_JSON}" "${CONTEXT_TXT}" <<'PY'
import csv, json, sys
from pathlib import Path

durations_path, summary_txt, summary_json, context_path = map(Path, sys.argv[1:5])
rows = list(csv.DictReader(durations_path.open(encoding="utf-8"), delimiter="\t"))
context = {}
for line in context_path.read_text(encoding="utf-8").splitlines():
    if "=" in line:
        k, v = line.split("=", 1)
        context[k] = v

summary = {"context": context, "steps": rows}
summary_json.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

failed = [row for row in rows if row["exit_code"] != "0"]
lines = [f"performance_baseline_suite={'failed' if failed else 'passed'}"]
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
