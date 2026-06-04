#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

POLICY_QUALITY_SCRIPT="${ROOT_DIR}/deploy/smoke/run-local-policy-quality-observation-suite.sh"
POLICY_SEARCH_SCENARIO_SCRIPT="${ROOT_DIR}/deploy/smoke/run-local-policy-search-scenario-audit.sh"

POLICY_QUALITY_LOG_ROOT="${POLICY_QUALITY_LOG_ROOT:-/var/log/youth-welfare/policy-quality-observation}"
RUN_TS_UTC="${RUN_TS_UTC:-$(smoke_now_ts_utc)}"
SUMMARY_DATE="${SUMMARY_DATE:-$(date +%F)}"
SUMMARY_TS_KST="${SUMMARY_TS_KST:-$(smoke_now_iso_kst)}"
RUN_DIR="${RUN_DIR:-${POLICY_QUALITY_LOG_ROOT}/artifacts/${RUN_TS_UTC}}"
SUMMARY_APPEND_FILE="${SUMMARY_APPEND_FILE:-${POLICY_QUALITY_LOG_ROOT}/nightly-summary-${SUMMARY_DATE}.log}"
POLICY_QUALITY_OUTPUT="${RUN_DIR}/policy-quality-observation.out"
POLICY_SEARCH_SCENARIO_OUTPUT="${RUN_DIR}/policy-search-scenario.out"

mkdir -p "${POLICY_QUALITY_LOG_ROOT}" "${RUN_DIR}" "$(dirname "${SUMMARY_APPEND_FILE}")"

export ENV_FILE="${ENV_FILE:-.env.production}"
export SMOKE_DB_MODE="${SMOKE_DB_MODE:-postgres}"
export APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
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

printf '[%s] policy quality observation start\n' "${SUMMARY_TS_KST}" | tee -a "${SUMMARY_APPEND_FILE}"

bash "${POLICY_QUALITY_SCRIPT}" | tee "${POLICY_QUALITY_OUTPUT}"
bash "${POLICY_SEARCH_SCENARIO_SCRIPT}" | tee "${POLICY_SEARCH_SCENARIO_OUTPUT}"

POLICY_QUALITY_SUMMARY="${ROOT_DIR}/tmp/policy-quality-observation/latest-policy-quality-observation-summary.txt"
POLICY_SEARCH_SCENARIO_SUMMARY="${ROOT_DIR}/tmp/policy-search-scenario-audit/latest-policy-search-scenario-summary.txt"

OBSERVATION_STATUS="$(read_summary_value "${POLICY_QUALITY_SUMMARY}" "policy_quality_observation_suite")"
DECISION_CLASS="$(read_summary_value "${POLICY_QUALITY_SUMMARY}" "decision_class")"
TOP1_HIT_RATE="$(read_summary_value "${POLICY_QUALITY_SUMMARY}" "top1_hit_rate")"
TOP3_HIT_RATE="$(read_summary_value "${POLICY_QUALITY_SUMMARY}" "top3_hit_rate")"
BRANCH_HIT_RATE="$(read_summary_value "${POLICY_QUALITY_SUMMARY}" "branch_suggestion_hit_rate")"
EMPTY_RESULT_COUNT="$(read_summary_value "${POLICY_QUALITY_SUMMARY}" "empty_result_count")"
NEXT_ACTION="$(read_summary_value "${POLICY_QUALITY_SUMMARY}" "next_action")"
SEARCH_SCENARIO_DECISION="$(read_summary_value "${POLICY_SEARCH_SCENARIO_SUMMARY}" "decision_class")"
SEARCH_SCENARIO_REGION_MISMATCH="$(read_summary_value "${POLICY_SEARCH_SCENARIO_SUMMARY}" "region_filter_mismatch_count")"
SEARCH_SCENARIO_MISSING_PROVIDER="$(read_summary_value "${POLICY_SEARCH_SCENARIO_SUMMARY}" "missing_provider_count")"
SEARCH_SCENARIO_MISSING_REGION="$(read_summary_value "${POLICY_SEARCH_SCENARIO_SUMMARY}" "missing_region_for_local_count")"

{
  printf '[%s] policy_quality=%s decision_class=%s top1=%s top3=%s branch=%s empty_results=%s\n' \
    "${SUMMARY_TS_KST}" \
    "${OBSERVATION_STATUS:-unknown}" \
    "${DECISION_CLASS:-unknown}" \
    "${TOP1_HIT_RATE:-}" \
    "${TOP3_HIT_RATE:-}" \
    "${BRANCH_HIT_RATE:-}" \
    "${EMPTY_RESULT_COUNT:-}"
  printf '  next_action=%s\n' "${NEXT_ACTION:-}"
  printf '  search_scenario_decision=%s region_mismatch=%s missing_provider=%s missing_region=%s\n' \
    "${SEARCH_SCENARIO_DECISION:-unknown}" \
    "${SEARCH_SCENARIO_REGION_MISMATCH:-}" \
    "${SEARCH_SCENARIO_MISSING_PROVIDER:-}" \
    "${SEARCH_SCENARIO_MISSING_REGION:-}"
  printf '  policy_quality_output=%s\n' "${POLICY_QUALITY_OUTPUT}"
  printf '  policy_search_scenario_output=%s\n' "${POLICY_SEARCH_SCENARIO_OUTPUT}"
} | tee -a "${SUMMARY_APPEND_FILE}"
