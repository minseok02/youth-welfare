#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
FRONTEND_PUBLIC_BASE_URL="${FRONTEND_PUBLIC_BASE_URL:-https://youthmoa.kr}"
DEEP_ROOT="${DEEP_ROOT:-${ROOT_DIR}/tmp/performance/deep-observation-suite}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${DEEP_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
DURATIONS_TSV="${ARTIFACT_DIR}/performance-deep-observation-durations.tsv"
SUMMARY_TXT="${ARTIFACT_DIR}/performance-deep-observation-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/performance-deep-observation-summary.json"
LATEST_DIR="${DEEP_ROOT}/latest"
LATEST_SUMMARY_TXT="${DEEP_ROOT}/latest-performance-deep-observation-summary.txt"
LATEST_SUMMARY_JSON="${DEEP_ROOT}/latest-performance-deep-observation-summary.json"

RUN_DB_OBSERVABILITY="${RUN_DB_OBSERVABILITY:-true}"
RUN_REDIS_OBSERVABILITY="${RUN_REDIS_OBSERVABILITY:-true}"
RUN_WEB_INTERACTION="${RUN_WEB_INTERACTION:-true}"
RUN_NGINX_LOG_OBSERVABILITY="${RUN_NGINX_LOG_OBSERVABILITY:-true}"
RUN_JVM_RUNTIME="${RUN_JVM_RUNTIME:-true}"

mkdir -p "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"
printf 'label\texit_code\tduration_ms\toutput_file\n' > "${DURATIONS_TSV}"

run_deep_step() {
  local label="$1"
  local output_file="$2"
  shift 2
  set +e
  perf_duration_step "${label}" "${output_file}" "$@" >> "${DURATIONS_TSV}"
  set -e
  return 0
}

if [[ "${RUN_DB_OBSERVABILITY}" == "true" ]]; then
  run_deep_step \
    "db_observability" \
    "${ARTIFACT_DIR}/db-observability.out" \
    env ENV_FILE="${ENV_FILE:-.env.production}" SMOKE_DB_MODE="${SMOKE_DB_MODE:-postgres}" ARTIFACT_DIR="${ARTIFACT_DIR}/db-observability" \
      bash "${ROOT_DIR}/deploy/performance/run-local-db-observability-baseline.sh"
fi

if [[ "${RUN_REDIS_OBSERVABILITY}" == "true" ]]; then
  run_deep_step \
    "redis_observability" \
    "${ARTIFACT_DIR}/redis-observability.out" \
    env ARTIFACT_DIR="${ARTIFACT_DIR}/redis-observability" \
      bash "${ROOT_DIR}/deploy/performance/run-local-redis-observability-baseline.sh"
fi

if [[ "${RUN_WEB_INTERACTION}" == "true" ]]; then
  run_deep_step \
    "web_interaction" \
    "${ARTIFACT_DIR}/web-interaction.out" \
    env FRONTEND_PUBLIC_BASE_URL="${FRONTEND_PUBLIC_BASE_URL}" ARTIFACT_DIR="${ARTIFACT_DIR}/web-interaction" \
      bash "${ROOT_DIR}/deploy/performance/run-local-web-interaction-baseline.sh"
fi

if [[ "${RUN_NGINX_LOG_OBSERVABILITY}" == "true" ]]; then
  run_deep_step \
    "nginx_log_observability" \
    "${ARTIFACT_DIR}/nginx-log-observability.out" \
    env ARTIFACT_DIR="${ARTIFACT_DIR}/nginx-log-observability" \
      bash "${ROOT_DIR}/deploy/performance/run-local-nginx-log-observability-baseline.sh"
fi

if [[ "${RUN_JVM_RUNTIME}" == "true" ]]; then
  run_deep_step \
    "jvm_runtime" \
    "${ARTIFACT_DIR}/jvm-runtime.out" \
    env APP_BASE_URL="${APP_BASE_URL}" ARTIFACT_DIR="${ARTIFACT_DIR}/jvm-runtime" \
      bash "${ROOT_DIR}/deploy/performance/run-local-jvm-runtime-baseline.sh"
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
summary_json.write_text(json.dumps({
    "context": context,
    "status": "failed" if failed else "passed",
    "steps": rows,
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = [f"performance_deep_observation_suite={'failed' if failed else 'passed'}"]
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
