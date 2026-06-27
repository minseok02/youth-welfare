#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/auth-observation}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

RUN_RUNTIME_API_SMOKE="${RUN_RUNTIME_API_SMOKE:-true}"
RUN_LOGIN_FAILURE_TRACKING_SMOKE="${RUN_LOGIN_FAILURE_TRACKING_SMOKE:-true}"
RUN_ACCOUNT_LOCKOUT_SMOKE="${RUN_ACCOUNT_LOCKOUT_SMOKE:-true}"
RUN_WITHDRAW_SMOKE="${RUN_WITHDRAW_SMOKE:-true}"
RUN_ADMIN_FORCED_LOGOUT_SMOKE="${RUN_ADMIN_FORCED_LOGOUT_SMOKE:-true}"

SMOKE_OUTPUT="${ARTIFACT_DIR}/auth-session-smoke.out"
SUMMARY_OUT="${ARTIFACT_DIR}/auth-observation-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/auth-observation.json"
NOTE_OUT="${ARTIFACT_DIR}/auth-observation-note.md"
AUTH_SESSION_ARTIFACT_DIR="${ARTIFACT_DIR}/auth-session-artifact"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-auth-observation-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-auth-observation.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-auth-observation-note.md"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"

smoke_require_command bash
smoke_require_command python3
smoke_require_command tee
mkdir -p "${ARTIFACT_DIR}"

smoke_print_step "auth observation"
APP_BASE_URL="${APP_BASE_URL}" \
KEEP_ARTIFACTS=true \
ARTIFACT_DIR="${AUTH_SESSION_ARTIFACT_DIR}" \
RUN_RUNTIME_API_SMOKE="${RUN_RUNTIME_API_SMOKE}" \
RUN_LOGIN_FAILURE_TRACKING_SMOKE="${RUN_LOGIN_FAILURE_TRACKING_SMOKE}" \
RUN_ACCOUNT_LOCKOUT_SMOKE="${RUN_ACCOUNT_LOCKOUT_SMOKE}" \
RUN_WITHDRAW_SMOKE="${RUN_WITHDRAW_SMOKE}" \
RUN_ADMIN_FORCED_LOGOUT_SMOKE="${RUN_ADMIN_FORCED_LOGOUT_SMOKE}" \
bash "${ROOT_DIR}/deploy/smoke/run-local-auth-session-smoke.sh" | tee "${SMOKE_OUTPUT}"

python3 - "${SMOKE_OUTPUT}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" "${AUTH_SESSION_ARTIFACT_DIR}" <<'PY'
import json
import sys
from pathlib import Path

output_path = Path(sys.argv[1])
summary_out = Path(sys.argv[2])
json_out = Path(sys.argv[3])
note_out = Path(sys.argv[4])
artifact_dir = sys.argv[5]
auth_session_artifact_dir = sys.argv[6]

values = {}
for raw_line in output_path.read_text(encoding="utf-8").splitlines():
    if "=" not in raw_line:
        continue
    key, value = raw_line.split("=", 1)
    values[key.strip()] = value.strip()

enabled = {
    "runtime_api_smoke": values.get("run_runtime_api_smoke", ""),
    "login_failure_tracking_smoke": values.get("run_login_failure_tracking_smoke", ""),
    "account_lockout_smoke": values.get("run_account_lockout_smoke", ""),
    "withdraw_smoke": values.get("run_withdraw_smoke", ""),
    "admin_forced_logout_smoke": values.get("run_admin_forced_logout_smoke", ""),
}
enabled_labels = [name for name, enabled_value in enabled.items() if enabled_value == "true"]
enabled_value = ",".join(enabled_labels) if enabled_labels else "(none)"
step_artifact_dirs = {
    "runtime_api_smoke": values.get("runtime_api_artifact_dir", ""),
    "login_failure_tracking_smoke": values.get("login_failure_tracking_artifact_dir", ""),
    "account_lockout_smoke": values.get("account_lockout_artifact_dir", ""),
    "withdraw_smoke": values.get("withdraw_artifact_dir", ""),
    "admin_forced_logout_smoke": values.get("admin_forced_logout_artifact_dir", ""),
}

decision_class = "BASELINE_HEALTHY"
operator_reading = (
    "Auth/session revoke baseline is healthy. Keep logout, withdraw, lockout, "
    "and forced-logout contracts as-is and only reopen auth tuning if a new regression appears."
)
next_action = "docs/auth/auth-session-revocation-current-state.md"

summary_lines = [
    "auth_observation_suite=passed",
    f"artifact_dir={artifact_dir}",
    f"auth_session_artifact_dir={values.get('auth_session_artifact_dir', auth_session_artifact_dir)}",
    f"app_base_url={values.get('app_base_url', '')}",
    f"enabled_smoke_steps={enabled_value}",
    f"run_runtime_api_smoke={values.get('run_runtime_api_smoke', '')}",
    f"run_login_failure_tracking_smoke={values.get('run_login_failure_tracking_smoke', '')}",
    f"run_account_lockout_smoke={values.get('run_account_lockout_smoke', '')}",
    f"run_withdraw_smoke={values.get('run_withdraw_smoke', '')}",
    f"run_admin_forced_logout_smoke={values.get('run_admin_forced_logout_smoke', '')}",
    f"runtime_api_artifact_dir={step_artifact_dirs['runtime_api_smoke']}",
    f"login_failure_tracking_artifact_dir={step_artifact_dirs['login_failure_tracking_smoke']}",
    f"account_lockout_artifact_dir={step_artifact_dirs['account_lockout_smoke']}",
    f"withdraw_artifact_dir={step_artifact_dirs['withdraw_smoke']}",
    f"admin_forced_logout_artifact_dir={step_artifact_dirs['admin_forced_logout_smoke']}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    f"next_action={next_action}",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": artifact_dir,
    "auth_session_artifact_dir": values.get("auth_session_artifact_dir", auth_session_artifact_dir),
    "app_base_url": values.get("app_base_url", ""),
    "enabled_smoke_steps": enabled_labels,
    "step_artifact_dirs": step_artifact_dirs,
    "run_runtime_api_smoke": values.get("run_runtime_api_smoke", ""),
    "run_login_failure_tracking_smoke": values.get("run_login_failure_tracking_smoke", ""),
    "run_account_lockout_smoke": values.get("run_account_lockout_smoke", ""),
    "run_withdraw_smoke": values.get("run_withdraw_smoke", ""),
    "run_admin_forced_logout_smoke": values.get("run_admin_forced_logout_smoke", ""),
    "decision_class": decision_class,
    "operator_reading": operator_reading,
    "next_action": next_action,
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Auth Observation",
    "",
    f"- `decision_class`: `{decision_class}`",
    f"- `enabled_smoke_steps`: `{enabled_value}`",
    f"- `app_base_url`: `{values.get('app_base_url', '')}`",
    f"- `auth_session_artifact_dir`: `{values.get('auth_session_artifact_dir', auth_session_artifact_dir)}`",
    f"- `next_action`: `{next_action}`",
    "",
    "## Operator Reading",
    "",
    operator_reading,
]
note_out.write_text("\n".join(note_lines) + "\n", encoding="utf-8")
PY

smoke_sanitize_artifacts "${ARTIFACT_DIR}"
smoke_publish_dir_snapshot "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
smoke_publish_file "${SUMMARY_OUT}" "${LATEST_SUMMARY_LINK}"
smoke_publish_file "${JSON_OUT}" "${LATEST_JSON_LINK}"
smoke_publish_file "${NOTE_OUT}" "${LATEST_NOTE_LINK}"

cat "${SUMMARY_OUT}"
echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
echo "latest_note_link=${LATEST_NOTE_LINK}"
