#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

OPS_SCRIPT="${ROOT_DIR}/deploy/smoke/run-local-ops-observation-suite.sh"
CURRENT_PRIORITY_SCRIPT="${ROOT_DIR}/deploy/smoke/run-local-current-priority-suite.sh"
BLOCKER_AUDIT_SCRIPT="${ROOT_DIR}/deploy/smoke/run-local-recommendation-review-gate-blocker-audit.sh"

STANDARD_CODE_OBSERVATION_LOG_ROOT="${STANDARD_CODE_OBSERVATION_LOG_ROOT:-/var/log/youth-welfare/standard-code-observation}"
RUN_TS_UTC="${RUN_TS_UTC:-$(smoke_now_ts_utc)}"
SUMMARY_DATE="${SUMMARY_DATE:-$(date +%F)}"
SUMMARY_TS_KST="${SUMMARY_TS_KST:-$(smoke_now_iso_kst)}"
RUN_DIR="${RUN_DIR:-${STANDARD_CODE_OBSERVATION_LOG_ROOT}/artifacts/${RUN_TS_UTC}}"
SUMMARY_APPEND_FILE="${SUMMARY_APPEND_FILE:-${STANDARD_CODE_OBSERVATION_LOG_ROOT}/nightly-summary-${SUMMARY_DATE}.log}"
OPS_OUTPUT="${RUN_DIR}/ops-observation.out"
CURRENT_PRIORITY_OUTPUT="${RUN_DIR}/current-priority.out"
BLOCKER_AUDIT_OUTPUT="${RUN_DIR}/recommendation-review-gate-blocker.out"

mkdir -p "${STANDARD_CODE_OBSERVATION_LOG_ROOT}" "${RUN_DIR}" "$(dirname "${SUMMARY_APPEND_FILE}")"

export ENV_FILE="${ENV_FILE:-.env.production}"
export SMOKE_DB_MODE="${SMOKE_DB_MODE:-postgres}"
export APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
export FRONTEND_E2E_MODE="${FRONTEND_E2E_MODE:-deployed-origin}"
export FRONTEND_PUBLIC_BASE_URL="${FRONTEND_PUBLIC_BASE_URL:-https://youthmoa.kr}"
export KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"

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

printf '[%s] standard-code observation start\n' "${SUMMARY_TS_KST}" | tee -a "${SUMMARY_APPEND_FILE}"

bash "${OPS_SCRIPT}" | tee "${OPS_OUTPUT}"
bash "${CURRENT_PRIORITY_SCRIPT}" | tee "${CURRENT_PRIORITY_OUTPUT}"
bash "${BLOCKER_AUDIT_SCRIPT}" | tee "${BLOCKER_AUDIT_OUTPUT}"

OPS_SUMMARY="${ROOT_DIR}/tmp/ops-observation/latest-ops-observation-summary.txt"
CURRENT_PRIORITY_SUMMARY="${ROOT_DIR}/tmp/current-priority-suite/latest-current-priority-summary.txt"
BLOCKER_SUMMARY="${ROOT_DIR}/tmp/recommendation-review-gate-blocker-audit/latest-review-gate-blocker-summary.txt"

OPS_STATUS="$(read_summary_value "${OPS_SUMMARY}" "ops_observation_suite")"
OPS_STANDARD_CODE_MISSING_ALL="$(read_summary_value "${OPS_SUMMARY}" "user_profile_standard_code_users_missing_all_standard_codes")"
OPS_STANDARD_CODE_WITH_ANY="$(read_summary_value "${OPS_SUMMARY}" "user_profile_standard_code_users_with_any_standard_code")"
OPS_ADOPTION_SHARE="$(read_summary_value "${OPS_SUMMARY}" "recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code_share_pct")"
OPS_ATTENTION_KEYS="$(read_summary_value "${OPS_SUMMARY}" "attention_feed_item_keys")"

CURRENT_PRIORITY_STATUS="$(read_summary_value "${CURRENT_PRIORITY_SUMMARY}" "current_priority_suite")"
CURRENT_PRIORITY_OBSERVATION_STATUS="$(read_summary_value "${CURRENT_PRIORITY_SUMMARY}" "recommendation_standard_code_observation_status")"
CURRENT_PRIORITY_ATTENTION_TITLES="$(read_summary_value "${CURRENT_PRIORITY_SUMMARY}" "active_baseline_attention_feed_item_titles")"
BLOCKER_CLASS="$(read_summary_value "${BLOCKER_SUMMARY}" "blocker_class")"
BLOCKER_NEXT_STEP="$(read_summary_value "${BLOCKER_SUMMARY}" "operator_next_step")"
BLOCKER_MIXED_READINESS="$(read_summary_value "${BLOCKER_SUMMARY}" "mixed_concentration_readiness")"
BLOCKER_REAL_USER_READINESS="$(read_summary_value "${BLOCKER_SUMMARY}" "real_user_concentration_readiness")"

{
  printf '[%s] ops=%s current_priority=%s observation=%s any_standard_code_users=%s missing_all_standard_codes=%s adoption_any_share_pct=%s attention_keys=%s attention_titles=%s\n' \
    "${SUMMARY_TS_KST}" \
    "${OPS_STATUS:-unknown}" \
    "${CURRENT_PRIORITY_STATUS:-unknown}" \
    "${CURRENT_PRIORITY_OBSERVATION_STATUS:-unknown}" \
    "${OPS_STANDARD_CODE_WITH_ANY:-}" \
    "${OPS_STANDARD_CODE_MISSING_ALL:-}" \
    "${OPS_ADOPTION_SHARE:-}" \
    "${OPS_ATTENTION_KEYS:-}" \
    "${CURRENT_PRIORITY_ATTENTION_TITLES:-}"
  printf '  blocker_class=%s blocker_next_step=%s mixed_readiness=%s real_user_readiness=%s\n' \
    "${BLOCKER_CLASS:-}" \
    "${BLOCKER_NEXT_STEP:-}" \
    "${BLOCKER_MIXED_READINESS:-}" \
    "${BLOCKER_REAL_USER_READINESS:-}"
  printf '  ops_output=%s\n' "${OPS_OUTPUT}"
  printf '  current_priority_output=%s\n' "${CURRENT_PRIORITY_OUTPUT}"
  printf '  blocker_audit_output=%s\n' "${BLOCKER_AUDIT_OUTPUT}"
} | tee -a "${SUMMARY_APPEND_FILE}"
