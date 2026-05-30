#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
FRONTEND_OBSERVATION_ROOT="${FRONTEND_OBSERVATION_ROOT:-${ROOT_DIR}/tmp/frontend-observation}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${FRONTEND_OBSERVATION_ROOT}/${RUN_TS_UTC}}"

RUN_FRONTEND_LINT="${RUN_FRONTEND_LINT:-true}"
RUN_FRONTEND_BUILD="${RUN_FRONTEND_BUILD:-true}"
RUN_FRONTEND_E2E="${RUN_FRONTEND_E2E:-true}"
FRONTEND_E2E_MODE="${FRONTEND_E2E_MODE:-local-dev}"
FRONTEND_PUBLIC_BASE_URL="${FRONTEND_PUBLIC_BASE_URL:-${PUBLIC_BASE_URL:-}}"
RUN_FRONTEND_ADMIN_E2E="${RUN_FRONTEND_ADMIN_E2E:-false}"
FRONTEND_OBSERVATION_APP_BASE_URL="${FRONTEND_OBSERVATION_APP_BASE_URL:-${APP_BASE_URL:-http://127.0.0.1:8082}}"
FRONTEND_OBSERVATION_HEALTH_URL="${FRONTEND_OBSERVATION_HEALTH_URL:-${HEALTH_URL:-${FRONTEND_OBSERVATION_APP_BASE_URL}/actuator/health}}"

SUMMARY_OUT="${ARTIFACT_DIR}/frontend-observation-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/frontend-observation.json"
NOTE_OUT="${ARTIFACT_DIR}/frontend-observation-note.md"
DURATIONS_TSV="${ARTIFACT_DIR}/frontend-observation-durations.tsv"

LATEST_ARTIFACT_LINK="${FRONTEND_OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${FRONTEND_OBSERVATION_ROOT}/latest-frontend-observation-summary.txt"
LATEST_JSON_LINK="${FRONTEND_OBSERVATION_ROOT}/latest-frontend-observation.json"
LATEST_NOTE_LINK="${FRONTEND_OBSERVATION_ROOT}/latest-frontend-observation-note.md"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

run_command_step() {
  local label="$1"
  local output_file="$2"
  shift 2

  smoke_print_step "${label}"
  set +e
  smoke_duration_step "${label}" "${output_file}" "$@" >> "${DURATIONS_TSV}"
  local exit_code=$?
  set -e
  cat "${output_file}"
  return "${exit_code}"
}

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
RUN_FRONTEND_LINT="$(smoke_normalize_bool "${RUN_FRONTEND_LINT}")"
RUN_FRONTEND_BUILD="$(smoke_normalize_bool "${RUN_FRONTEND_BUILD}")"
RUN_FRONTEND_E2E="$(smoke_normalize_bool "${RUN_FRONTEND_E2E}")"
RUN_FRONTEND_ADMIN_E2E="$(smoke_normalize_bool "${RUN_FRONTEND_ADMIN_E2E}")"

smoke_require_command bash
smoke_require_command python3
smoke_require_command tee

case "${FRONTEND_E2E_MODE}" in
  local-dev|deployed-origin|skip)
    ;;
  *)
    echo "FRONTEND_E2E_MODE must be one of: local-dev, deployed-origin, skip" >&2
    exit 1
    ;;
esac

mkdir -p "${ARTIFACT_DIR}"
printf 'label\texit_code\tduration_ms\toutput_file\n' > "${DURATIONS_TSV}"

if [[ "${RUN_FRONTEND_LINT}" == "true" ]]; then
  run_command_step \
    "frontend_lint" \
    "${ARTIFACT_DIR}/frontend-lint.txt" \
    bash -lc "cd '${ROOT_DIR}/frontend' && npm run lint"
fi

if [[ "${RUN_FRONTEND_BUILD}" == "true" ]]; then
  run_command_step \
    "frontend_build" \
    "${ARTIFACT_DIR}/frontend-build.txt" \
    bash -lc "cd '${ROOT_DIR}/frontend' && npm run build"
fi

if [[ "${RUN_FRONTEND_E2E}" == "true" ]]; then
  case "${FRONTEND_E2E_MODE}" in
    local-dev)
      run_command_step \
        "frontend_e2e" \
        "${ARTIFACT_DIR}/frontend-e2e.txt" \
        bash -lc "cd '${ROOT_DIR}/frontend' && npm run test:e2e"
      ;;
    deployed-origin)
      if [[ -z "${FRONTEND_PUBLIC_BASE_URL}" ]]; then
        echo "FRONTEND_PUBLIC_BASE_URL or PUBLIC_BASE_URL is required for FRONTEND_E2E_MODE=deployed-origin" >&2
        exit 1
      fi
      if [[ -z "${E2E_USER_EMAIL:-}" ]]; then
        E2E_USER_EMAIL="playwright.frontend.observation.${RUN_TS_UTC,,}@example.com"
      fi
      if [[ -z "${E2E_USER_PASSWORD:-}" ]]; then
        E2E_USER_PASSWORD="Password123!"
      fi
      PLAYWRIGHT_GREP_INVERT="@dev-only"
      if [[ "${RUN_FRONTEND_ADMIN_E2E}" != "true" ]]; then
        PLAYWRIGHT_GREP_INVERT="${PLAYWRIGHT_GREP_INVERT}|@admin-required"
      fi
      run_command_step \
        "frontend_e2e_bootstrap" \
        "${ARTIFACT_DIR}/frontend-e2e-bootstrap.txt" \
        bash -lc "cd '${ROOT_DIR}/frontend' && APP_BASE_URL='${FRONTEND_OBSERVATION_APP_BASE_URL}' HEALTH_URL='${FRONTEND_OBSERVATION_HEALTH_URL}' RUN_ADMIN_SETUP='${RUN_FRONTEND_ADMIN_E2E}' E2E_USER_EMAIL='${E2E_USER_EMAIL}' E2E_USER_PASSWORD='${E2E_USER_PASSWORD}' ENV_FILE='${ENV_FILE:-}' DB_QUERY_USERNAME='${DB_QUERY_USERNAME:-}' DB_QUERY_PASSWORD='${DB_QUERY_PASSWORD:-}' bash ./scripts/bootstrap-playwright-smoke-data.sh"
      run_command_step \
        "frontend_e2e" \
        "${ARTIFACT_DIR}/frontend-e2e.txt" \
        bash -lc "cd '${ROOT_DIR}/frontend' && ENV_FILE='${ENV_FILE:-}' E2E_USER_EMAIL='${E2E_USER_EMAIL}' E2E_USER_PASSWORD='${E2E_USER_PASSWORD}' PLAYWRIGHT_SKIP_WEBSERVER=true PLAYWRIGHT_BASE_URL='${FRONTEND_PUBLIC_BASE_URL}' PLAYWRIGHT_GREP_INVERT='${PLAYWRIGHT_GREP_INVERT}' npm run test:e2e"
      ;;
    skip)
      cat <<'EOF' > "${ARTIFACT_DIR}/frontend-e2e.txt"
frontend_e2e=skipped
EOF
      printf 'frontend_e2e\t0\t0\t%s\n' "${ARTIFACT_DIR}/frontend-e2e.txt" >> "${DURATIONS_TSV}"
      cat "${ARTIFACT_DIR}/frontend-e2e.txt"
      ;;
  esac
fi

python3 - "${DURATIONS_TSV}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" "${RUN_TS_UTC}" "${RUN_FRONTEND_LINT}" "${RUN_FRONTEND_BUILD}" "${RUN_FRONTEND_E2E}" "${FRONTEND_E2E_MODE}" "${FRONTEND_PUBLIC_BASE_URL}" "${RUN_FRONTEND_ADMIN_E2E}" "${FRONTEND_OBSERVATION_APP_BASE_URL}" "${FRONTEND_OBSERVATION_HEALTH_URL}" <<'PY'
import csv
import json
import sys
from pathlib import Path

durations_path = Path(sys.argv[1])
summary_out = Path(sys.argv[2])
json_out = Path(sys.argv[3])
note_out = Path(sys.argv[4])
artifact_dir = sys.argv[5]
generated_at_utc = sys.argv[6]
run_frontend_lint = sys.argv[7]
run_frontend_build = sys.argv[8]
run_frontend_e2e = sys.argv[9]
frontend_e2e_mode = sys.argv[10]
frontend_public_base_url = sys.argv[11]
run_frontend_admin_e2e = sys.argv[12]
frontend_observation_app_base_url = sys.argv[13]
frontend_observation_health_url = sys.argv[14]

rows = list(csv.DictReader(durations_path.open(encoding="utf-8"), delimiter="\t"))
suite_duration_ms = sum(int(row["duration_ms"]) for row in rows)
suite_duration_seconds = suite_duration_ms / 1000

enabled_labels = []
if run_frontend_lint == "true":
    enabled_labels.append("frontend_lint")
if run_frontend_build == "true":
    enabled_labels.append("frontend_build")
if run_frontend_e2e == "true":
    enabled_labels.append("frontend_e2e")
enabled_value = ",".join(enabled_labels) if enabled_labels else "(none)"

decision_class = "BASELINE_HEALTHY"
operator_reading = (
    "Frontend baseline is healthy. Keep lint/build/browser smoke green and only reopen UI flow tuning if a new regression appears."
)
next_action = "docs/frontend/frontend-qa-current-state.md"

lines = [
    "frontend_observation_suite=passed",
    f"artifact_dir={artifact_dir}",
    f"generated_at_utc={generated_at_utc}",
    f"suite_duration_ms={suite_duration_ms}",
    f"suite_duration_seconds={suite_duration_seconds:.3f}",
    f"enabled_smoke_steps={enabled_value}",
    f"run_frontend_lint={run_frontend_lint}",
    f"run_frontend_build={run_frontend_build}",
    f"run_frontend_e2e={run_frontend_e2e}",
    f"frontend_e2e_mode={frontend_e2e_mode}",
    f"frontend_public_base_url={frontend_public_base_url}",
    f"run_frontend_admin_e2e={run_frontend_admin_e2e}",
    f"frontend_observation_app_base_url={frontend_observation_app_base_url}",
    f"frontend_observation_health_url={frontend_observation_health_url}",
]

for row in rows:
    seconds = int(row["duration_ms"]) / 1000
    lines.append(f"{row['label']}_duration_ms={row['duration_ms']}")
    lines.append(f"{row['label']}_duration_seconds={seconds:.3f}")

lines.extend([
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    f"next_action={next_action}",
])

summary_out.write_text("\n".join(lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": artifact_dir,
    "generated_at_utc": generated_at_utc,
    "suite_duration_ms": suite_duration_ms,
    "suite_duration_seconds": suite_duration_seconds,
    "enabled_smoke_steps": enabled_labels,
    "run_frontend_lint": run_frontend_lint,
    "run_frontend_build": run_frontend_build,
    "run_frontend_e2e": run_frontend_e2e,
    "frontend_e2e_mode": frontend_e2e_mode,
    "frontend_public_base_url": frontend_public_base_url,
    "run_frontend_admin_e2e": run_frontend_admin_e2e,
    "frontend_observation_app_base_url": frontend_observation_app_base_url,
    "frontend_observation_health_url": frontend_observation_health_url,
    "decision_class": decision_class,
    "operator_reading": operator_reading,
    "next_action": next_action,
}
for row in rows:
    json_payload[f"{row['label']}_duration_ms"] = int(row["duration_ms"])
    json_payload[f"{row['label']}_duration_seconds"] = int(row["duration_ms"]) / 1000

json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Frontend Observation",
    "",
    f"- `decision_class`: `{decision_class}`",
    f"- `enabled_smoke_steps`: `{enabled_value}`",
    f"- `suite_duration_ms`: `{suite_duration_ms}`",
    f"- `frontend_e2e_mode`: `{frontend_e2e_mode}`",
    f"- `run_frontend_admin_e2e`: `{run_frontend_admin_e2e}`",
    f"- `next_action`: `{next_action}`",
    "",
    "## Operator Reading",
    "",
    operator_reading,
]
note_out.write_text("\n".join(note_lines) + "\n", encoding="utf-8")
PY

smoke_publish_dir_snapshot "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
smoke_publish_file "${SUMMARY_OUT}" "${LATEST_SUMMARY_LINK}"
smoke_publish_file "${JSON_OUT}" "${LATEST_JSON_LINK}"
smoke_publish_file "${NOTE_OUT}" "${LATEST_NOTE_LINK}"

cat "${SUMMARY_OUT}"
echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
echo "latest_note_link=${LATEST_NOTE_LINK}"
