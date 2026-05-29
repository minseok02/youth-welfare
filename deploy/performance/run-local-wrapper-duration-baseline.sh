#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
WRAPPER_BASELINE_ROOT="${WRAPPER_BASELINE_ROOT:-${ROOT_DIR}/tmp/performance/wrapper-duration}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${WRAPPER_BASELINE_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
DURATIONS_TSV="${ARTIFACT_DIR}/wrapper-durations.tsv"
SUMMARY_TXT="${ARTIFACT_DIR}/wrapper-duration-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/wrapper-duration-summary.json"
LATEST_DIR="${WRAPPER_BASELINE_ROOT}/latest"
LATEST_SUMMARY_TXT="${WRAPPER_BASELINE_ROOT}/latest-wrapper-duration-summary.txt"
LATEST_SUMMARY_JSON="${WRAPPER_BASELINE_ROOT}/latest-wrapper-duration-summary.json"

RUN_OBSERVATION="${RUN_OBSERVATION:-true}"
RUN_CURRENT_PRIORITY="${RUN_CURRENT_PRIORITY:-true}"
FRONTEND_E2E_MODE="${FRONTEND_E2E_MODE:-deployed-origin}"
FRONTEND_PUBLIC_BASE_URL="${FRONTEND_PUBLIC_BASE_URL:-https://youthmoa.kr}"

mkdir -p "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"
printf 'label\texit_code\tduration_ms\toutput_file\n' > "${DURATIONS_TSV}"

run_wrapper_step() {
  local label="$1"
  local output_file="$2"
  shift 2
  set +e
  perf_duration_step "${label}" "${output_file}" "$@" >> "${DURATIONS_TSV}"
  set -e
  return 0
}

if [[ "${RUN_OBSERVATION}" == "true" ]]; then
  run_wrapper_step \
    "recommendation_observation" \
    "${ARTIFACT_DIR}/recommendation-observation.out" \
    env ENV_FILE="${ENV_FILE:-.env.production}" SMOKE_DB_MODE="${SMOKE_DB_MODE:-postgres}" APP_BASE_URL="${APP_BASE_URL}" \
      bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-observation-suite.sh"
fi

if [[ "${RUN_CURRENT_PRIORITY}" == "true" ]]; then
  run_wrapper_step \
    "current_priority" \
    "${ARTIFACT_DIR}/current-priority.out" \
    env ENV_FILE="${ENV_FILE:-.env.production}" SMOKE_DB_MODE="${SMOKE_DB_MODE:-postgres}" APP_BASE_URL="${APP_BASE_URL}" \
      FRONTEND_E2E_MODE="${FRONTEND_E2E_MODE}" FRONTEND_PUBLIC_BASE_URL="${FRONTEND_PUBLIC_BASE_URL}" \
      E2E_ADMIN_EMAIL="${E2E_ADMIN_EMAIL:-$(cat /tmp/youth-welfare-admin-smoke-email 2>/dev/null || true)}" \
      E2E_ADMIN_PASSWORD="${E2E_ADMIN_PASSWORD:-$(cat /tmp/youth-welfare-admin-smoke-password 2>/dev/null || true)}" \
      E2E_USER_EMAIL="${E2E_USER_EMAIL:-playwright.user@example.com}" \
      E2E_USER_PASSWORD="${E2E_USER_PASSWORD:-Password123!}" \
      bash "${ROOT_DIR}/deploy/smoke/run-local-current-priority-suite.sh"
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

summary = {"context": context, "wrappers": rows}
summary_json.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

failed = [row for row in rows if row["exit_code"] != "0"]
lines = [f"wrapper_duration_baseline={'failed' if failed else 'passed'}"]
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
