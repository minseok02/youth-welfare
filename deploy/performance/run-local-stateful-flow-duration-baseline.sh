#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
STATEFUL_FLOW_ROOT="${STATEFUL_FLOW_ROOT:-${ROOT_DIR}/tmp/performance/stateful-flow-duration}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${STATEFUL_FLOW_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
DURATIONS_TSV="${ARTIFACT_DIR}/stateful-flow-durations.tsv"
SUMMARY_TXT="${ARTIFACT_DIR}/stateful-flow-duration-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/stateful-flow-duration-summary.json"
LATEST_DIR="${STATEFUL_FLOW_ROOT}/latest"
LATEST_SUMMARY_TXT="${STATEFUL_FLOW_ROOT}/latest-stateful-flow-duration-summary.txt"
LATEST_SUMMARY_JSON="${STATEFUL_FLOW_ROOT}/latest-stateful-flow-duration-summary.json"

RUN_RUNTIME_API="${RUN_RUNTIME_API:-true}"
RUN_AUTH_SESSION="${RUN_AUTH_SESSION:-false}"
RUN_BOOKMARK_CONSISTENCY="${RUN_BOOKMARK_CONSISTENCY:-false}"
RUN_ADMIN_FORCED_LOGOUT="${RUN_ADMIN_FORCED_LOGOUT:-false}"

mkdir -p "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"
printf 'label\texit_code\tduration_ms\toutput_file\n' > "${DURATIONS_TSV}"

run_flow_step() {
  local label="$1"
  local output_file="$2"
  shift 2
  set +e
  perf_duration_step "${label}" "${output_file}" "$@" >> "${DURATIONS_TSV}"
  set -e
  return 0
}

if [[ "${RUN_RUNTIME_API}" == "true" ]]; then
  run_flow_step \
    "runtime_api_smoke" \
    "${ARTIFACT_DIR}/runtime-api-smoke.out" \
    env ENV_FILE="${ENV_FILE:-.env.production}" APP_BASE_URL="${APP_BASE_URL}" \
      bash "${ROOT_DIR}/deploy/smoke/run-local-runtime-api-smoke.sh"
fi

if [[ "${RUN_AUTH_SESSION}" == "true" ]]; then
  run_flow_step \
    "auth_session_smoke" \
    "${ARTIFACT_DIR}/auth-session-smoke.out" \
    env ENV_FILE="${ENV_FILE:-.env.production}" APP_BASE_URL="${APP_BASE_URL}" \
      bash "${ROOT_DIR}/deploy/smoke/run-local-auth-session-smoke.sh"
fi

if [[ "${RUN_BOOKMARK_CONSISTENCY}" == "true" ]]; then
  run_flow_step \
    "bookmark_consistency_smoke" \
    "${ARTIFACT_DIR}/bookmark-consistency-smoke.out" \
    env ENV_FILE="${ENV_FILE:-.env.production}" APP_BASE_URL="${APP_BASE_URL}" \
      bash "${ROOT_DIR}/deploy/smoke/run-local-bookmark-consistency-smoke.sh"
fi

if [[ "${RUN_ADMIN_FORCED_LOGOUT}" == "true" ]]; then
  run_flow_step \
    "admin_forced_logout_smoke" \
    "${ARTIFACT_DIR}/admin-forced-logout-smoke.out" \
    env ENV_FILE="${ENV_FILE:-.env.production}" APP_BASE_URL="${APP_BASE_URL}" \
      bash "${ROOT_DIR}/deploy/smoke/run-local-admin-forced-logout-smoke.sh"
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
payload = {"context": context, "status": "failed" if failed else "passed", "flows": rows}
summary_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = [f"stateful_flow_duration_baseline={'failed' if failed else 'passed'}"]
for row in rows:
    seconds = int(row["duration_ms"]) / 1000
    lines.append(f"{row['label']} exit_code={row['exit_code']} duration_ms={row['duration_ms']} duration_seconds={seconds:.3f}")
if not rows:
    lines.append("flow_count=0")
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
