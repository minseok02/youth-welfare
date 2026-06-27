#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

STANDARD_CODE_SCRIPT="${ROOT_DIR}/deploy/smoke/run-nightly-standard-code-observation.sh"
POLICY_QUALITY_SCRIPT="${ROOT_DIR}/deploy/smoke/run-nightly-policy-quality-observation.sh"
POLICY_DATA_TRIAGE_SCRIPT="${ROOT_DIR}/deploy/smoke/run-local-policy-data-triage-observation-suite.sh"
COLLECT_GOVERNANCE_SCRIPT="${ROOT_DIR}/deploy/smoke/run-local-collect-governance-observation-suite.sh"
AUTH_OBSERVATION_SCRIPT="${ROOT_DIR}/deploy/smoke/run-local-auth-observation-suite.sh"
FRONTEND_OBSERVATION_SCRIPT="${ROOT_DIR}/deploy/smoke/run-local-frontend-observation-suite.sh"
OPERATIONAL_DB_AUDIT_SCRIPT="${ROOT_DIR}/deploy/postgres/audit-operational-db-state.sh"

NIGHTLY_OPS_HANDOFF_LOG_ROOT="${NIGHTLY_OPS_HANDOFF_LOG_ROOT:-/var/log/youth-welfare/nightly-ops-handoff}"
RUN_TS_UTC="${RUN_TS_UTC:-$(smoke_now_ts_utc)}"
SUMMARY_DATE="${SUMMARY_DATE:-$(date +%F)}"
SUMMARY_TS_KST="${SUMMARY_TS_KST:-$(smoke_now_iso_kst)}"
RUN_DIR="${RUN_DIR:-${NIGHTLY_OPS_HANDOFF_LOG_ROOT}/artifacts/${RUN_TS_UTC}}"
SUMMARY_APPEND_FILE="${SUMMARY_APPEND_FILE:-${NIGHTLY_OPS_HANDOFF_LOG_ROOT}/nightly-summary-${SUMMARY_DATE}.log}"

STANDARD_CODE_OUTPUT="${RUN_DIR}/standard-code-observation.out"
POLICY_QUALITY_OUTPUT="${RUN_DIR}/policy-quality-observation.out"
POLICY_DATA_TRIAGE_OUTPUT="${RUN_DIR}/policy-data-triage-observation.out"
COLLECT_GOVERNANCE_OUTPUT="${RUN_DIR}/collect-governance-observation.out"
AUTH_OBSERVATION_OUTPUT="${RUN_DIR}/auth-observation.out"
FRONTEND_OBSERVATION_OUTPUT="${RUN_DIR}/frontend-observation.out"
OPERATIONAL_DB_AUDIT_OUTPUT="${RUN_DIR}/operational-db-audit.out"

RUN_STANDARD_CODE_OBSERVATION="${RUN_STANDARD_CODE_OBSERVATION:-true}"
RUN_POLICY_QUALITY_OBSERVATION="${RUN_POLICY_QUALITY_OBSERVATION:-true}"
RUN_POLICY_DATA_TRIAGE_OBSERVATION="${RUN_POLICY_DATA_TRIAGE_OBSERVATION:-true}"
RUN_COLLECT_GOVERNANCE_OBSERVATION="${RUN_COLLECT_GOVERNANCE_OBSERVATION:-true}"
RUN_AUTH_OBSERVATION="${RUN_AUTH_OBSERVATION:-true}"
RUN_FRONTEND_OBSERVATION="${RUN_FRONTEND_OBSERVATION:-false}"
RUN_OPERATIONAL_DB_AUDIT="${RUN_OPERATIONAL_DB_AUDIT:-true}"

cleanup() {
  smoke_sanitize_artifacts "${RUN_DIR}"
}
trap cleanup EXIT

mkdir -p "${NIGHTLY_OPS_HANDOFF_LOG_ROOT}" "${RUN_DIR}" "$(dirname "${SUMMARY_APPEND_FILE}")"

export ENV_FILE="${ENV_FILE:-.env.production}"
export SMOKE_DB_MODE="${SMOKE_DB_MODE:-postgres}"
export APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
export FRONTEND_E2E_MODE="${FRONTEND_E2E_MODE:-deployed-origin}"
export FRONTEND_PUBLIC_BASE_URL="${FRONTEND_PUBLIC_BASE_URL:-https://youthmoa.kr}"
export KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"

smoke_login_admin_access_token "${ROOT_DIR}" "${APP_BASE_URL}"

read_summary_value() {
  local file="$1"
  local key="$2"
  python3 - "${file}" "${key}" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
key = sys.argv[2]
if not path.exists():
    print("")
    raise SystemExit(0)
for raw_line in path.read_text(encoding="utf-8").splitlines():
    if raw_line.startswith(f"{key}="):
        print(raw_line.split("=", 1)[1].strip())
        raise SystemExit(0)
print("")
PY
}

run_step() {
  local enabled="$1"
  local label="$2"
  local script_path="$3"
  local output_file="$4"

  if [[ "$(smoke_normalize_bool "${enabled}")" != "true" ]]; then
    printf '%s=skipped\n' "${label}" | tee "${output_file}" >/dev/null
    smoke_sanitize_artifacts "${RUN_DIR}"
    return 0
  fi

  bash "${script_path}" | tee "${output_file}"
  smoke_sanitize_artifacts "${RUN_DIR}"
}

printf '[%s] nightly ops handoff start\n' "${SUMMARY_TS_KST}" | tee -a "${SUMMARY_APPEND_FILE}"

run_step "${RUN_STANDARD_CODE_OBSERVATION}" "standard_code_observation" "${STANDARD_CODE_SCRIPT}" "${STANDARD_CODE_OUTPUT}"
run_step "${RUN_POLICY_QUALITY_OBSERVATION}" "policy_quality_observation" "${POLICY_QUALITY_SCRIPT}" "${POLICY_QUALITY_OUTPUT}"
run_step "${RUN_POLICY_DATA_TRIAGE_OBSERVATION}" "policy_data_triage_observation" "${POLICY_DATA_TRIAGE_SCRIPT}" "${POLICY_DATA_TRIAGE_OUTPUT}"
run_step "${RUN_COLLECT_GOVERNANCE_OBSERVATION}" "collect_governance_observation" "${COLLECT_GOVERNANCE_SCRIPT}" "${COLLECT_GOVERNANCE_OUTPUT}"
run_step "${RUN_AUTH_OBSERVATION}" "auth_observation" "${AUTH_OBSERVATION_SCRIPT}" "${AUTH_OBSERVATION_OUTPUT}"
run_step "${RUN_FRONTEND_OBSERVATION}" "frontend_observation" "${FRONTEND_OBSERVATION_SCRIPT}" "${FRONTEND_OBSERVATION_OUTPUT}"
run_step "${RUN_OPERATIONAL_DB_AUDIT}" "operational_db_audit" "${OPERATIONAL_DB_AUDIT_SCRIPT}" "${OPERATIONAL_DB_AUDIT_OUTPUT}"

OPS_SUMMARY="${ROOT_DIR}/tmp/ops-observation/latest-ops-observation-summary.txt"
POLICY_SUMMARY="${ROOT_DIR}/tmp/policy-quality-observation/latest-policy-quality-observation-summary.txt"
POLICY_DATA_TRIAGE_SUMMARY="${ROOT_DIR}/tmp/policy-data-triage-observation/latest-policy-data-triage-observation-summary.txt"
COLLECT_SUMMARY="${ROOT_DIR}/tmp/collect-governance-observation/latest-collect-governance-observation-summary.txt"
AUTH_SUMMARY="${ROOT_DIR}/tmp/auth-observation/latest-auth-observation-summary.txt"
FRONTEND_SUMMARY="${ROOT_DIR}/tmp/frontend-observation/latest-frontend-observation-summary.txt"

OPS_STATUS="$(read_summary_value "${OPS_SUMMARY}" "ops_observation_suite")"
OPS_ATTENTION_KEYS="$(read_summary_value "${OPS_SUMMARY}" "attention_feed_item_keys")"
OPS_ATTENTION_TITLES="$(read_summary_value "${OPS_SUMMARY}" "attention_feed_item_titles")"
OPS_STANDARD_CODE_MISSING_ALL="$(read_summary_value "${OPS_SUMMARY}" "user_profile_standard_code_users_missing_all_standard_codes")"
OPS_ADOPTION_SHARE="$(read_summary_value "${OPS_SUMMARY}" "recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code_share_pct")"
POLICY_STATUS="$(read_summary_value "${POLICY_SUMMARY}" "policy_quality_observation_suite")"
POLICY_DECISION_CLASS="$(read_summary_value "${POLICY_SUMMARY}" "decision_class")"
POLICY_TRIAGE_STATUS="$(read_summary_value "${POLICY_DATA_TRIAGE_SUMMARY}" "policy_data_triage_observation_suite")"
POLICY_TRIAGE_DECISION_CLASS="$(read_summary_value "${POLICY_DATA_TRIAGE_SUMMARY}" "decision_class")"
POLICY_TRIAGE_NEXT_ACTION="$(read_summary_value "${POLICY_DATA_TRIAGE_SUMMARY}" "next_action")"
COLLECT_STATUS="$(read_summary_value "${COLLECT_SUMMARY}" "collect_governance_observation_suite")"
COLLECT_DECISION_CLASS="$(read_summary_value "${COLLECT_SUMMARY}" "decision_class")"
AUTH_STATUS="$(read_summary_value "${AUTH_SUMMARY}" "auth_observation_suite")"
AUTH_DECISION_CLASS="$(read_summary_value "${AUTH_SUMMARY}" "decision_class")"
FRONTEND_STATUS="$(read_summary_value "${FRONTEND_SUMMARY}" "frontend_observation_suite")"
FRONTEND_DECISION_CLASS="$(read_summary_value "${FRONTEND_SUMMARY}" "decision_class")"

if [[ "$(smoke_normalize_bool "${RUN_AUTH_OBSERVATION}")" != "true" ]]; then
  AUTH_STATUS="skipped"
  AUTH_DECISION_CLASS="skipped"
fi

if [[ "$(smoke_normalize_bool "${RUN_POLICY_DATA_TRIAGE_OBSERVATION}")" != "true" ]]; then
  POLICY_TRIAGE_STATUS="skipped"
  POLICY_TRIAGE_DECISION_CLASS="skipped"
  POLICY_TRIAGE_NEXT_ACTION="skipped"
fi

if [[ "$(smoke_normalize_bool "${RUN_FRONTEND_OBSERVATION}")" != "true" ]]; then
  FRONTEND_STATUS="skipped"
  FRONTEND_DECISION_CLASS="skipped"
fi

if [[ "$(smoke_normalize_bool "${RUN_OPERATIONAL_DB_AUDIT}")" == "true" ]]; then
  DB_AUDIT_STATUS="ok"
else
  DB_AUDIT_STATUS="skipped"
fi

{
  printf '[%s] ops=%s policy=%s policy_triage=%s collect=%s auth=%s frontend=%s db_audit=%s\n' \
    "${SUMMARY_TS_KST}" \
    "${OPS_STATUS:-unknown}" \
    "${POLICY_STATUS:-unknown}" \
    "${POLICY_TRIAGE_STATUS:-unknown}" \
    "${COLLECT_STATUS:-unknown}" \
    "${AUTH_STATUS:-unknown}" \
    "${FRONTEND_STATUS:-skipped}" \
    "${DB_AUDIT_STATUS:-unknown}"
  printf '  attention_keys=%s missing_all_standard_codes=%s adoption_any_share_pct=%s\n' \
    "${OPS_ATTENTION_KEYS:-}" \
    "${OPS_STANDARD_CODE_MISSING_ALL:-}" \
    "${OPS_ADOPTION_SHARE:-}"
  printf '  attention_titles=%s\n' "${OPS_ATTENTION_TITLES:-}"
  printf '  policy_decision=%s policy_triage_decision=%s collect_decision=%s auth_decision=%s frontend_decision=%s\n' \
    "${POLICY_DECISION_CLASS:-}" \
    "${POLICY_TRIAGE_DECISION_CLASS:-}" \
    "${COLLECT_DECISION_CLASS:-}" \
    "${AUTH_DECISION_CLASS:-}" \
    "${FRONTEND_DECISION_CLASS:-}"
  printf '  policy_triage_next_action=%s\n' "${POLICY_TRIAGE_NEXT_ACTION:-}"
  printf '  standard_code_output=%s\n' "${STANDARD_CODE_OUTPUT}"
  printf '  policy_quality_output=%s\n' "${POLICY_QUALITY_OUTPUT}"
  printf '  policy_data_triage_output=%s\n' "${POLICY_DATA_TRIAGE_OUTPUT}"
  printf '  collect_governance_output=%s\n' "${COLLECT_GOVERNANCE_OUTPUT}"
  printf '  auth_observation_output=%s\n' "${AUTH_OBSERVATION_OUTPUT}"
  printf '  frontend_observation_output=%s\n' "${FRONTEND_OBSERVATION_OUTPUT}"
  printf '  operational_db_audit_output=%s\n' "${OPERATIONAL_DB_AUDIT_OUTPUT}"
} | tee -a "${SUMMARY_APPEND_FILE}"

smoke_sanitize_artifacts "${RUN_DIR}"
