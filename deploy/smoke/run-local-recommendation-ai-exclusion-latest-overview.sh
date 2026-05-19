#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

STATUS_EXPORT_SCRIPT="${STATUS_EXPORT_SCRIPT:-${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-exclusion-latest-status-export.sh}"
LATEST_STATUS_SCRIPT="${LATEST_STATUS_SCRIPT:-${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-exclusion-latest-status.sh}"
LATEST_GATE_SCRIPT="${LATEST_GATE_SCRIPT:-${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-exclusion-latest-gate.sh}"
REAL_USER_READINESS_SCRIPT="${REAL_USER_READINESS_SCRIPT:-${ROOT_DIR}/deploy/smoke/run-local-real-user-exclusion-readiness-check.sh}"
OVERVIEW_ROOT="${OVERVIEW_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-latest-overview}"
APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"

AUTO_REFRESH_STATUS_JSON_IF_STALE="${AUTO_REFRESH_STATUS_JSON_IF_STALE:-true}"
INCLUDE_REAL_USER_READINESS="${INCLUDE_REAL_USER_READINESS:-auto}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OVERVIEW_ROOT}/${RUN_TS_UTC}}"
STATUS_OUT="${ARTIFACT_DIR}/latest-status.out"
GATE_OUT="${ARTIFACT_DIR}/latest-gate.out"
STRICT_GATE_OUT="${ARTIFACT_DIR}/latest-gate-strict.out"
READINESS_OUT="${ARTIFACT_DIR}/real-user-readiness.out"
READINESS_RAW_OUT="${ARTIFACT_DIR}/real-user-readiness-raw.out"
SUMMARY_OUT="${ARTIFACT_DIR}/latest-overview-summary.txt"
NOTE_OUT="${ARTIFACT_DIR}/latest-overview-note.md"
JSON_OUT="${ARTIFACT_DIR}/latest-overview.json"
LATEST_ARTIFACT_LINK="${OVERVIEW_ROOT}/latest"
LATEST_SUMMARY_LINK="${OVERVIEW_ROOT}/latest-overview-summary.txt"
LATEST_NOTE_LINK="${OVERVIEW_ROOT}/latest-overview-note.md"
LATEST_JSON_LINK="${OVERVIEW_ROOT}/latest-overview.json"

AUTO_REFRESH_STATUS_JSON_IF_STALE="$(smoke_normalize_bool "${AUTO_REFRESH_STATUS_JSON_IF_STALE}")"
INCLUDE_REAL_USER_READINESS="$(smoke_normalize_tri_state "${INCLUDE_REAL_USER_READINESS}")"
mkdir -p "${ARTIFACT_DIR}"
smoke_require_command python3
smoke_resolve_admin_credentials "${ROOT_DIR}"

smoke_print_step "latest status export"
bash "${STATUS_EXPORT_SCRIPT}" >/dev/null

echo "auto_refresh_status_json_if_stale=${AUTO_REFRESH_STATUS_JSON_IF_STALE}"
echo "include_real_user_readiness=${INCLUDE_REAL_USER_READINESS}"

smoke_print_step "latest status"
AUTO_REFRESH_STATUS_JSON_IF_STALE="${AUTO_REFRESH_STATUS_JSON_IF_STALE}" \
  bash "${LATEST_STATUS_SCRIPT}" | tee "${STATUS_OUT}"

smoke_print_step "latest gate"
AUTO_REFRESH_STATUS_JSON_IF_STALE="${AUTO_REFRESH_STATUS_JSON_IF_STALE}" \
  bash "${LATEST_GATE_SCRIPT}" | tee "${GATE_OUT}"

smoke_print_step "latest gate (strict)"
if AUTO_REFRESH_STATUS_JSON_IF_STALE="${AUTO_REFRESH_STATUS_JSON_IF_STALE}" \
  FAIL_ON_LATEST_OBSERVATION_CHANGE=true \
  bash "${LATEST_GATE_SCRIPT}" | tee "${STRICT_GATE_OUT}"; then
  strict_exit_code=0
  :
else
  strict_exit_code=$?
fi
echo "strict_gate_exit_code=${strict_exit_code}" | tee -a "${STRICT_GATE_OUT}"

real_user_readiness_included="false"
real_user_readiness_skip_reason=""
real_user_readiness_detected_app_base_url="${APP_BASE_URL:-}"
real_user_readiness_detected_admin_email="${ADMIN_EMAIL:-}"
real_user_readiness_has_admin_password="false"
if [[ -n "${ADMIN_PASSWORD:-}" ]]; then
  real_user_readiness_has_admin_password="true"
fi

resolve_readiness_skip_reason() {
  local mode="$1"
  local has_app_base_url="$2"
  local has_admin_email="$3"
  local has_admin_password="$4"

  if [[ "${has_app_base_url}" != "true" ]]; then
    if [[ "${mode}" == "auto" ]]; then
      printf '%s' "AUTO_SKIP_MISSING_APP_BASE_URL"
    else
      printf '%s' "MISSING_APP_BASE_URL"
    fi
    return
  fi

  if [[ "${has_admin_email}" != "true" && "${has_admin_password}" != "true" ]]; then
    if [[ "${mode}" == "auto" ]]; then
      printf '%s' "AUTO_SKIP_MISSING_ADMIN_EMAIL_AND_PASSWORD"
    else
      printf '%s' "MISSING_ADMIN_EMAIL_AND_PASSWORD"
    fi
    return
  fi

  if [[ "${has_admin_email}" != "true" ]]; then
    if [[ "${mode}" == "auto" ]]; then
      printf '%s' "AUTO_SKIP_MISSING_ADMIN_EMAIL"
    else
      printf '%s' "MISSING_ADMIN_EMAIL"
    fi
    return
  fi

  if [[ "${has_admin_password}" != "true" ]]; then
    if [[ "${mode}" == "auto" ]]; then
      printf '%s' "AUTO_SKIP_MISSING_ADMIN_PASSWORD"
    else
      printf '%s' "MISSING_ADMIN_PASSWORD"
    fi
    return
  fi

  printf '%s' ""
}

if [[ "${INCLUDE_REAL_USER_READINESS}" == "true" ]]; then
  if [[ -z "${APP_BASE_URL:-}" || -z "${ADMIN_EMAIL:-}" || -z "${ADMIN_PASSWORD:-}" ]]; then
    real_user_readiness_skip_reason="$(resolve_readiness_skip_reason "forced" "$( [[ -n "${APP_BASE_URL:-}" ]] && echo true || echo false )" "$( [[ -n "${ADMIN_EMAIL:-}" ]] && echo true || echo false )" "$( [[ -n "${ADMIN_PASSWORD:-}" ]] && echo true || echo false )")"
  else
    real_user_readiness_included="true"
  fi
elif [[ "${INCLUDE_REAL_USER_READINESS}" == "auto" ]]; then
  if [[ -n "${APP_BASE_URL:-}" && -n "${ADMIN_EMAIL:-}" && -n "${ADMIN_PASSWORD:-}" ]]; then
    real_user_readiness_included="true"
  else
    real_user_readiness_skip_reason="$(resolve_readiness_skip_reason "auto" "$( [[ -n "${APP_BASE_URL:-}" ]] && echo true || echo false )" "$( [[ -n "${ADMIN_EMAIL:-}" ]] && echo true || echo false )" "$( [[ -n "${ADMIN_PASSWORD:-}" ]] && echo true || echo false )")"
  fi
else
  real_user_readiness_skip_reason="REAL_USER_READINESS_DISABLED"
fi

  if [[ "${real_user_readiness_included}" == "true" ]]; then
  smoke_print_step "real-user readiness"
  APP_BASE_URL="${APP_BASE_URL}" \
  ADMIN_EMAIL="${ADMIN_EMAIL}" \
  ADMIN_PASSWORD="${ADMIN_PASSWORD}" \
    bash "${REAL_USER_READINESS_SCRIPT}" | tee "${READINESS_RAW_OUT}"
  {
    echo "real_user_readiness_included=true"
    echo "real_user_readiness_skip_reason="
    echo "real_user_readiness_detected_app_base_url=${real_user_readiness_detected_app_base_url}"
    echo "real_user_readiness_detected_admin_email=${real_user_readiness_detected_admin_email}"
    echo "real_user_readiness_has_admin_password=${real_user_readiness_has_admin_password}"
    cat "${READINESS_RAW_OUT}"
  } > "${READINESS_OUT}"
else
  printf 'real_user_readiness_included=%s\n' "${real_user_readiness_included}" > "${READINESS_OUT}"
  printf 'real_user_readiness_skip_reason=%s\n' "${real_user_readiness_skip_reason}" >> "${READINESS_OUT}"
  printf 'real_user_readiness_detected_app_base_url=%s\n' "${real_user_readiness_detected_app_base_url}" >> "${READINESS_OUT}"
  printf 'real_user_readiness_detected_admin_email=%s\n' "${real_user_readiness_detected_admin_email}" >> "${READINESS_OUT}"
  printf 'real_user_readiness_has_admin_password=%s\n' "${real_user_readiness_has_admin_password}" >> "${READINESS_OUT}"
  echo
  echo "[real-user readiness]"
  cat "${READINESS_OUT}"
fi

python3 - "${STATUS_OUT}" "${GATE_OUT}" "${STRICT_GATE_OUT}" "${READINESS_OUT}" "${SUMMARY_OUT}" "${NOTE_OUT}" "${JSON_OUT}" <<'PY'
import sys
import json
from pathlib import Path

status_out = Path(sys.argv[1])
gate_out = Path(sys.argv[2])
strict_gate_out = Path(sys.argv[3])
readiness_out = Path(sys.argv[4])
summary_out = Path(sys.argv[5])
note_out = Path(sys.argv[6])
json_out = Path(sys.argv[7])

def parse_kv(path: Path) -> dict[str, str]:
    data = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key] = value
    return data

status = parse_kv(status_out)
gate = parse_kv(gate_out)
strict_gate = parse_kv(strict_gate_out)
readiness = parse_kv(readiness_out)

readiness_next_action = ""
readiness_skip_reason = readiness.get("real_user_readiness_skip_reason", "")
if readiness.get("real_user_readiness_included", "") == "true":
    readiness_next_action = "WAIT_FOR_REAL_USER_GATE_OR_TRAFFIC"
elif readiness_skip_reason == "AUTO_SKIP_MISSING_ADMIN_PASSWORD":
    readiness_next_action = "SET_ADMIN_PASSWORD_OR_ADMIN_PASSWORD_FILE"
elif readiness_skip_reason == "AUTO_SKIP_MISSING_ADMIN_EMAIL":
    readiness_next_action = "SET_ADMIN_EMAIL_OR_SECURITY_ADMIN_EMAILS"
elif readiness_skip_reason == "AUTO_SKIP_MISSING_ADMIN_EMAIL_AND_PASSWORD":
    readiness_next_action = "SET_ADMIN_EMAIL_AND_ADMIN_PASSWORD"
elif readiness_skip_reason == "AUTO_SKIP_MISSING_APP_BASE_URL":
    readiness_next_action = "SET_APP_BASE_URL"
elif readiness_skip_reason == "MISSING_ADMIN_PASSWORD":
    readiness_next_action = "SET_ADMIN_PASSWORD_OR_ADMIN_PASSWORD_FILE"
elif readiness_skip_reason == "MISSING_ADMIN_EMAIL":
    readiness_next_action = "SET_ADMIN_EMAIL_OR_SECURITY_ADMIN_EMAILS"
elif readiness_skip_reason == "MISSING_ADMIN_EMAIL_AND_PASSWORD":
    readiness_next_action = "SET_ADMIN_EMAIL_AND_ADMIN_PASSWORD"
elif readiness_skip_reason == "MISSING_APP_BASE_URL":
    readiness_next_action = "SET_APP_BASE_URL"
elif readiness_skip_reason == "REAL_USER_READINESS_DISABLED":
    readiness_next_action = "ENABLE_INCLUDE_REAL_USER_READINESS"

lines = [
    f"generated_at_utc={status.get('generated_at_utc', '')}",
    f"generated_at_kst={status.get('generated_at_kst', '')}",
    f"operator_next_step={status.get('operator_next_step', '')}",
    f"status_json_stale_relative_to_summaries={status.get('status_json_stale_relative_to_summaries', '')}",
    f"latest_drift_class={status.get('latest_drift_class', '')}",
    f"latest_recommended_reading={status.get('latest_recommended_reading', '')}",
    f"stable_baseline_zero_ai_reason_buckets={status.get('stable_baseline_zero_ai_reason_buckets', '')}",
    f"stable_dashboard_real_user_gate={status.get('stable_dashboard_real_user_gate', '')}",
    f"stable_breakdown_real_user_cohort_gate={status.get('stable_breakdown_real_user_cohort_gate', '')}",
    f"latest_fresh_top_ai_zero_count={status.get('latest_fresh_top_ai_zero_count', '')}",
    f"latest_ai_zero_count={status.get('latest_ai_zero_count', '')}",
    f"latest_ai_zero_reason_buckets={status.get('latest_ai_zero_reason_buckets', '')}",
    f"gate_status={gate.get('gate_status', '')}",
    f"gate_reason={gate.get('gate_reason', '')}",
    f"strict_gate_status={strict_gate.get('gate_status', '')}",
    f"strict_gate_reason={strict_gate.get('gate_reason', '')}",
    f"strict_gate_exit_code={strict_gate.get('strict_gate_exit_code', '')}",
    f"real_user_readiness_included={readiness.get('real_user_readiness_included', '')}",
    f"real_user_readiness_skip_reason={readiness.get('real_user_readiness_skip_reason', '')}",
    f"real_user_readiness_next_action={readiness_next_action}",
    f"real_user_readiness_detected_app_base_url={readiness.get('real_user_readiness_detected_app_base_url', '')}",
    f"real_user_readiness_detected_admin_email={readiness.get('real_user_readiness_detected_admin_email', '')}",
    f"real_user_readiness_has_admin_password={readiness.get('real_user_readiness_has_admin_password', '')}",
    f"real_user_dashboard_gate={readiness.get('dashboard_real_user_gate', '')}",
    f"real_user_breakdown_cohort_gate={readiness.get('breakdown_real_user_cohort_gate', '')}",
    f"real_user_distribution_executed={readiness.get('real_user_distribution_executed', '')}",
]

summary_out.write_text("\n".join(lines) + "\n", encoding="utf-8")

note_lines = [
    "# AI Exclusion Latest Overview",
    "",
    f"- generated_at_utc: `{status.get('generated_at_utc', '')}`",
    f"- generated_at_kst: `{status.get('generated_at_kst', '')}`",
    f"- operator_next_step: `{status.get('operator_next_step', '')}`",
    f"- status_json_stale_relative_to_summaries: `{status.get('status_json_stale_relative_to_summaries', '')}`",
    "",
    "## Baseline",
    "",
    f"- latest_drift_class: `{status.get('latest_drift_class', '')}`",
    f"- latest_recommended_reading: `{status.get('latest_recommended_reading', '')}`",
    f"- stable_baseline_zero_ai_reason_buckets: `{status.get('stable_baseline_zero_ai_reason_buckets', '')}`",
    f"- stable_dashboard_real_user_gate: `{status.get('stable_dashboard_real_user_gate', '')}`",
    f"- stable_breakdown_real_user_cohort_gate: `{status.get('stable_breakdown_real_user_cohort_gate', '')}`",
    "",
    "## Latest Observation",
    "",
    f"- latest_fresh_top_ai_zero_count: `{status.get('latest_fresh_top_ai_zero_count', '')}`",
    f"- latest_ai_zero_count: `{status.get('latest_ai_zero_count', '')}`",
    f"- latest_ai_zero_reason_buckets: `{status.get('latest_ai_zero_reason_buckets', '')}`",
    "",
    "## Gates",
    "",
    f"- gate_status: `{gate.get('gate_status', '')}`",
    f"- gate_reason: `{gate.get('gate_reason', '')}`",
    f"- strict_gate_status: `{strict_gate.get('gate_status', '')}`",
    f"- strict_gate_reason: `{strict_gate.get('gate_reason', '')}`",
    f"- strict_gate_exit_code: `{strict_gate.get('strict_gate_exit_code', '')}`",
    "",
    "## Real User Readiness",
    "",
    f"- real_user_readiness_included: `{readiness.get('real_user_readiness_included', '')}`",
    f"- real_user_readiness_skip_reason: `{readiness.get('real_user_readiness_skip_reason', '')}`",
    f"- real_user_readiness_next_action: `{readiness_next_action}`",
    f"- real_user_readiness_detected_app_base_url: `{readiness.get('real_user_readiness_detected_app_base_url', '')}`",
    f"- real_user_readiness_detected_admin_email: `{readiness.get('real_user_readiness_detected_admin_email', '')}`",
    f"- real_user_readiness_has_admin_password: `{readiness.get('real_user_readiness_has_admin_password', '')}`",
    f"- real_user_dashboard_gate: `{readiness.get('dashboard_real_user_gate', '')}`",
    f"- real_user_breakdown_cohort_gate: `{readiness.get('breakdown_real_user_cohort_gate', '')}`",
    f"- real_user_distribution_executed: `{readiness.get('real_user_distribution_executed', '')}`",
]
note_out.write_text("\n".join(note_lines) + "\n", encoding="utf-8")

json_payload = {
    "generated_at_utc": status.get("generated_at_utc", ""),
    "generated_at_kst": status.get("generated_at_kst", ""),
    "operator_next_step": status.get("operator_next_step", ""),
    "status_json_stale_relative_to_summaries": status.get("status_json_stale_relative_to_summaries", ""),
    "baseline": {
        "latest_drift_class": status.get("latest_drift_class", ""),
        "latest_recommended_reading": status.get("latest_recommended_reading", ""),
        "stable_baseline_zero_ai_reason_buckets": status.get("stable_baseline_zero_ai_reason_buckets", ""),
        "stable_dashboard_real_user_gate": status.get("stable_dashboard_real_user_gate", ""),
        "stable_breakdown_real_user_cohort_gate": status.get("stable_breakdown_real_user_cohort_gate", ""),
    },
    "latest_observation": {
        "latest_fresh_top_ai_zero_count": status.get("latest_fresh_top_ai_zero_count", ""),
        "latest_ai_zero_count": status.get("latest_ai_zero_count", ""),
        "latest_ai_zero_reason_buckets": status.get("latest_ai_zero_reason_buckets", ""),
    },
    "gates": {
        "gate_status": gate.get("gate_status", ""),
        "gate_reason": gate.get("gate_reason", ""),
        "strict_gate_status": strict_gate.get("gate_status", ""),
        "strict_gate_reason": strict_gate.get("gate_reason", ""),
        "strict_gate_exit_code": strict_gate.get("strict_gate_exit_code", ""),
    },
    "real_user_readiness": {
        "real_user_readiness_included": readiness.get("real_user_readiness_included", ""),
        "real_user_readiness_skip_reason": readiness.get("real_user_readiness_skip_reason", ""),
        "real_user_readiness_next_action": readiness_next_action,
        "real_user_readiness_detected_app_base_url": readiness.get("real_user_readiness_detected_app_base_url", ""),
        "real_user_readiness_detected_admin_email": readiness.get("real_user_readiness_detected_admin_email", ""),
        "real_user_readiness_has_admin_password": readiness.get("real_user_readiness_has_admin_password", ""),
        "dashboard_real_user_gate": readiness.get("dashboard_real_user_gate", ""),
        "breakdown_real_user_cohort_gate": readiness.get("breakdown_real_user_cohort_gate", ""),
        "real_user_distribution_executed": readiness.get("real_user_distribution_executed", ""),
    },
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

mkdir -p "${OVERVIEW_ROOT}"
smoke_update_links \
  "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}" \
  "${SUMMARY_OUT}" "${LATEST_SUMMARY_LINK}" \
  "${NOTE_OUT}" "${LATEST_NOTE_LINK}" \
  "${JSON_OUT}" "${LATEST_JSON_LINK}"

echo
echo "overview_artifact_dir=${ARTIFACT_DIR}"
echo "overview_summary=${SUMMARY_OUT}"
echo "overview_note=${NOTE_OUT}"
echo "overview_json=${JSON_OUT}"
echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
echo "latest_note_link=${LATEST_NOTE_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
