#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

REFRESH_ROOT="${REFRESH_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-baseline-refresh}"
DRIFT_CHECK_ROOT="${DRIFT_CHECK_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-baseline-refresh-drift-check}"
STATUS_ROOT="${STATUS_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-latest-status}"
STATUS_EXPORT_SCRIPT="${STATUS_EXPORT_SCRIPT:-${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-exclusion-latest-status-export.sh}"

REFRESH_SUMMARY="${REFRESH_SUMMARY:-${REFRESH_ROOT}/latest-baseline-refresh-summary.txt}"
DRIFT_SUMMARY="${DRIFT_SUMMARY:-${DRIFT_CHECK_ROOT}/latest-baseline-refresh-drift-summary.txt}"
STATUS_JSON="${STATUS_JSON:-${STATUS_ROOT}/latest-status.json}"
AUTO_REFRESH_STATUS_JSON_IF_STALE="${AUTO_REFRESH_STATUS_JSON_IF_STALE:-false}"

AUTO_REFRESH_STATUS_JSON_IF_STALE="$(smoke_normalize_bool "${AUTO_REFRESH_STATUS_JSON_IF_STALE}")"

if [[ ! -f "${REFRESH_SUMMARY}" ]]; then
  echo "latest refresh summary not found: ${REFRESH_SUMMARY}" >&2
  exit 1
fi

if [[ ! -f "${DRIFT_SUMMARY}" ]]; then
  echo "latest drift summary not found: ${DRIFT_SUMMARY}" >&2
  exit 1
fi

if [[ "${AUTO_REFRESH_STATUS_JSON_IF_STALE}" == "true" ]]; then
  if [[ ! -f "${STATUS_JSON}" || "${STATUS_JSON}" -ot "${REFRESH_SUMMARY}" || "${STATUS_JSON}" -ot "${DRIFT_SUMMARY}" ]]; then
    bash "${STATUS_EXPORT_SCRIPT}" >/dev/null
  fi
fi

smoke_require_command python3

python3 - "${REFRESH_SUMMARY}" "${DRIFT_SUMMARY}" "${STATUS_JSON}" <<'PY'
import sys
import json
from pathlib import Path

refresh_path = Path(sys.argv[1])
drift_path = Path(sys.argv[2])
status_json_path = Path(sys.argv[3])


def parse_kv(path: Path) -> dict[str, str]:
    data = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key] = value
    return data


refresh = parse_kv(refresh_path)
drift = parse_kv(drift_path)
generated_at_utc = ""
generated_at_kst = ""
status_json_stale_relative_to_summaries = ""
status_json_recommended_action = ""
effective_operator_next_step = ""
gate_action_class = ""
gate_policy_status = ""
gate_policy_reason = ""
status_data = {}

interpretation_changed = drift.get("interpretation_changed", "")
stable_baseline_changed = drift.get("stable_baseline_changed", "")
latest_observation_changed = drift.get("latest_observation_changed", "")
stable_dashboard_real_user_gate = refresh.get("stable_dashboard_real_user_gate", "")
stable_breakdown_real_user_cohort_gate = refresh.get("stable_breakdown_real_user_cohort_gate", "")

operator_next_step = "BASELINE_STABLE_NO_ACTION"
if interpretation_changed == "true" or stable_baseline_changed == "true":
    operator_next_step = "INVESTIGATE_STABLE_BASELINE_DRIFT"
elif (
    stable_dashboard_real_user_gate == "DEFERRED_NO_REAL_USER_TRAFFIC"
    or stable_breakdown_real_user_cohort_gate == "DEFERRED_NO_REAL_USER_COHORT"
):
    operator_next_step = "WAIT_FOR_REAL_USER_TRAFFIC"
elif latest_observation_changed == "true":
    operator_next_step = "OBSERVE_FRESH_WINDOW_VOLATILITY"

if status_json_path.is_file():
    status_data = json.loads(status_json_path.read_text(encoding="utf-8"))
    generated_at_utc = status_data.get("generated_at_utc", "")
    generated_at_kst = status_data.get("generated_at_kst", "")
    operator_next_step = status_data.get("operator_next_step", operator_next_step)
    effective_operator_next_step = status_data.get("effective_operator_next_step", operator_next_step)
    gate_action_class = status_data.get("gate_action_class", "")
    gate_policy_status = status_data.get("gate_policy_status", "")
    gate_policy_reason = status_data.get("gate_policy_reason", "")
    newest_summary_mtime = max(refresh_path.stat().st_mtime, drift_path.stat().st_mtime)
    status_json_stale_relative_to_summaries = "true" if status_json_path.stat().st_mtime < newest_summary_mtime else "false"
    if status_json_stale_relative_to_summaries == "true":
        status_json_recommended_action = "RERUN_LATEST_STATUS_EXPORT"

if not gate_action_class:
    if effective_operator_next_step == "USE_RECENT_WINDOW_AS_SUPPLEMENTAL_REVIEW_CONTEXT":
        gate_action_class = "READ_PRIMARY_AND_SUPPLEMENTAL_REVIEW_GATES"
    elif effective_operator_next_step == "WAIT_FOR_REAL_USER_LEADER_SIGNAL":
        gate_action_class = "WAIT_FOR_REAL_USER_LEADER_SIGNAL"
    elif effective_operator_next_step == "RUN_REAL_USER_RECHECK_DECISION":
        gate_action_class = "RUN_REAL_USER_RECHECK"
    elif effective_operator_next_step == "OBSERVE_FRESH_WINDOW_VOLATILITY":
        gate_action_class = "OBSERVE_FRESH_WINDOW_VOLATILITY"
    elif effective_operator_next_step == "KEEP_TRACING_CURRENT_TARGET_LEADER_PATH":
        gate_action_class = "TRACE_CURRENT_TARGET_LEADER_PATH"
    elif effective_operator_next_step == "INVESTIGATE_STABLE_BASELINE_DRIFT":
        gate_action_class = "INVESTIGATE_BASELINE_DRIFT"
    else:
        gate_action_class = "KEEP_BASELINE_MONITORING"

review_gate_context = status_data.get("review_gate_context", {})
review_gate_interpretation_class = review_gate_context.get("review_gate_interpretation_class", "")
review_gate_policy_candidate_status = review_gate_context.get("review_gate_policy_candidate_status", "")
review_gate_policy_candidate_reason = review_gate_context.get("review_gate_policy_candidate_reason", "")
review_gate_policy_promotion_status = review_gate_context.get("review_gate_policy_promotion_status", "")
review_gate_policy_promotion_reason = review_gate_context.get("review_gate_policy_promotion_reason", "")
review_gate_policy_promotion_action_status = review_gate_context.get("review_gate_policy_promotion_action_status", "")
review_gate_policy_promotion_action_reason = review_gate_context.get("review_gate_policy_promotion_action_reason", "")
review_gate_policy_promotion_readiness_status = review_gate_context.get("review_gate_policy_promotion_readiness_status", "")
review_gate_policy_promotion_readiness_reason = review_gate_context.get("review_gate_policy_promotion_readiness_reason", "")
review_gate_policy_promotion_execution_status = review_gate_context.get("review_gate_policy_promotion_execution_status", "")
review_gate_policy_promotion_execution_reason = review_gate_context.get("review_gate_policy_promotion_execution_reason", "")
review_gate_policy_promotion_approval_criteria_status = review_gate_context.get("review_gate_policy_promotion_approval_criteria_status", "")
review_gate_policy_promotion_approval_criteria_reason = review_gate_context.get("review_gate_policy_promotion_approval_criteria_reason", "")
review_gate_policy_promotion_approval_status = review_gate_context.get("review_gate_policy_promotion_approval_status", "")
review_gate_policy_promotion_approval_reason = review_gate_context.get("review_gate_policy_promotion_approval_reason", "")
review_gate_policy_promotion_approval_decision_status = review_gate_context.get("review_gate_policy_promotion_approval_decision_status", "")
review_gate_policy_promotion_approval_decision_reason = review_gate_context.get("review_gate_policy_promotion_approval_decision_reason", "")
review_gate_policy_promotion_approval_record_status = review_gate_context.get("review_gate_policy_promotion_approval_record_status", "")
review_gate_policy_promotion_approval_record_reason = review_gate_context.get("review_gate_policy_promotion_approval_record_reason", "")
review_gate_policy_promotion_review_run_status = review_gate_context.get("review_gate_policy_promotion_review_run_status", "")
review_gate_policy_promotion_review_run_reason = review_gate_context.get("review_gate_policy_promotion_review_run_reason", "")
review_gate_policy_promotion_review_run_criteria_status = review_gate_context.get("review_gate_policy_promotion_review_run_criteria_status", "")
review_gate_policy_promotion_review_run_criteria_reason = review_gate_context.get("review_gate_policy_promotion_review_run_criteria_reason", "")
review_gate_policy_promotion_review_run_decision_status = review_gate_context.get("review_gate_policy_promotion_review_run_decision_status", "")
review_gate_policy_promotion_review_run_decision_reason = review_gate_context.get("review_gate_policy_promotion_review_run_decision_reason", "")
if not gate_policy_status:
    if interpretation_changed == "true" or stable_baseline_changed == "true":
        gate_policy_status = "BASELINE_DRIFT_BLOCKING"
        gate_policy_reason = "INTERPRETATION_CHANGED" if interpretation_changed == "true" else "STABLE_BASELINE_CHANGED"
    elif review_gate_interpretation_class == "HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR":
        gate_policy_status = "PRIMARY_BLOCKED_SUPPLEMENTAL_CLEAR"
        gate_policy_reason = review_gate_interpretation_class
    elif review_gate_interpretation_class == "PRIMARY_AND_RECENT_WINDOW_BOTH_BLOCKING":
        gate_policy_status = "PRIMARY_AND_RECENT_WINDOW_BLOCKING"
        gate_policy_reason = review_gate_interpretation_class
    elif review_gate_interpretation_class == "PRIMARY_BLOCKER_WITHOUT_SUPPLEMENTAL_SIGNAL":
        gate_policy_status = "PRIMARY_BLOCKER_ONLY"
        gate_policy_reason = review_gate_interpretation_class
    else:
        gate_policy_status = "BASELINE_MONITORING"
        gate_policy_reason = review_gate_interpretation_class or "NO_SPECIAL_REVIEW_GATE_SPLIT"

print(f"refresh_summary={refresh_path}")
print(f"drift_summary={drift_path}")
print(f"status_json={status_json_path if status_json_path.is_file() else ''}")
print(f"generated_at_utc={generated_at_utc}")
print(f"generated_at_kst={generated_at_kst}")
print(f"operator_next_step={operator_next_step}")
print(f"effective_operator_next_step={effective_operator_next_step}")
print(f"gate_action_class={gate_action_class}")
print(f"gate_policy_status={gate_policy_status}")
print(f"gate_policy_reason={gate_policy_reason}")
print(f"status_json_stale_relative_to_summaries={status_json_stale_relative_to_summaries}")
print(f"status_json_recommended_action={status_json_recommended_action}")
print(f"latest_drift_class={refresh.get('drift_class', '')}")
print(f"latest_recommended_reading={refresh.get('recommended_reading', '')}")
print(f"stable_baseline_zero_ai_reason_buckets={refresh.get('stable_baseline_zero_ai_reason_buckets', '')}")
print(f"stable_dashboard_real_user_gate={refresh.get('stable_dashboard_real_user_gate', '')}")
print(f"stable_breakdown_real_user_cohort_gate={refresh.get('stable_breakdown_real_user_cohort_gate', '')}")
print(f"latest_fresh_top_ai_zero_count={refresh.get('latest_fresh_top_ai_zero_count', '')}")
print(f"latest_ai_zero_count={refresh.get('latest_ai_zero_count', '')}")
print(f"latest_ai_zero_reason_buckets={refresh.get('latest_ai_zero_reason_buckets', '')}")
print(f"latest_volatility_reference_frequency={refresh.get('volatility_reference_frequency', '')}")
print(f"latest_drift_detected={drift.get('drift_detected', '')}")
print(f"latest_interpretation_changed={drift.get('interpretation_changed', '')}")
print(f"latest_stable_baseline_changed={drift.get('stable_baseline_changed', '')}")
print(f"latest_observation_changed={drift.get('latest_observation_changed', '')}")
print(f"latest_changed_keys={drift.get('changed_keys', '')}")
print(f"latest_stable_changed_keys={drift.get('stable_changed_keys', '')}")
print(f"latest_observation_changed_keys={drift.get('latest_changed_keys', '')}")
print(f"primary_review_gate_blocker_class={review_gate_context.get('primary_review_gate_blocker_class', '')}")
print(f"primary_review_gate_operator_next_step={review_gate_context.get('primary_review_gate_operator_next_step', '')}")
print(f"review_gate_interpretation_class={review_gate_context.get('review_gate_interpretation_class', '')}")
print(f"review_gate_operating_mode={review_gate_context.get('review_gate_operating_mode', '')}")
print(f"review_gate_policy_candidate_status={review_gate_policy_candidate_status}")
print(f"review_gate_policy_candidate_reason={review_gate_policy_candidate_reason}")
print(f"review_gate_policy_promotion_status={review_gate_policy_promotion_status}")
print(f"review_gate_policy_promotion_reason={review_gate_policy_promotion_reason}")
print(f"review_gate_policy_promotion_action_status={review_gate_policy_promotion_action_status}")
print(f"review_gate_policy_promotion_action_reason={review_gate_policy_promotion_action_reason}")
print(f"review_gate_policy_promotion_readiness_status={review_gate_policy_promotion_readiness_status}")
print(f"review_gate_policy_promotion_readiness_reason={review_gate_policy_promotion_readiness_reason}")
print(f"review_gate_policy_promotion_execution_status={review_gate_policy_promotion_execution_status}")
print(f"review_gate_policy_promotion_execution_reason={review_gate_policy_promotion_execution_reason}")
print(f"review_gate_policy_promotion_approval_criteria_status={review_gate_policy_promotion_approval_criteria_status}")
print(f"review_gate_policy_promotion_approval_criteria_reason={review_gate_policy_promotion_approval_criteria_reason}")
print(f"review_gate_policy_promotion_approval_status={review_gate_policy_promotion_approval_status}")
print(f"review_gate_policy_promotion_approval_reason={review_gate_policy_promotion_approval_reason}")
print(f"review_gate_policy_promotion_approval_decision_status={review_gate_policy_promotion_approval_decision_status}")
print(f"review_gate_policy_promotion_approval_decision_reason={review_gate_policy_promotion_approval_decision_reason}")
print(f"review_gate_policy_promotion_approval_record_status={review_gate_policy_promotion_approval_record_status}")
print(f"review_gate_policy_promotion_approval_record_reason={review_gate_policy_promotion_approval_record_reason}")
print(f"review_gate_policy_promotion_review_run_status={review_gate_policy_promotion_review_run_status}")
print(f"review_gate_policy_promotion_review_run_reason={review_gate_policy_promotion_review_run_reason}")
print(f"review_gate_policy_promotion_review_run_criteria_status={review_gate_policy_promotion_review_run_criteria_status}")
print(f"review_gate_policy_promotion_review_run_criteria_reason={review_gate_policy_promotion_review_run_criteria_reason}")
print(f"review_gate_policy_promotion_review_run_decision_status={review_gate_policy_promotion_review_run_decision_status}")
print(f"review_gate_policy_promotion_review_run_decision_reason={review_gate_policy_promotion_review_run_decision_reason}")
print(f"primary_mixed_top1_leader_service_id={review_gate_context.get('primary_mixed_top1_leader_service_id', '')}")
print(f"primary_mixed_top1_leader_title={review_gate_context.get('primary_mixed_top1_leader_title', '')}")
print(f"primary_mixed_top1_leader_share_pct={review_gate_context.get('primary_mixed_top1_leader_share_pct', '')}")
print(f"primary_mixed_top1_leader_real_user_users={review_gate_context.get('primary_mixed_top1_leader_real_user_users', '')}")
print(f"recent_window_recommendation_review_reading={review_gate_context.get('recent_window_recommendation_review_reading', '')}")
print(f"recent_window_hours={review_gate_context.get('recent_window_hours', '')}")
print(f"recent_window_top1_leader_service_id={review_gate_context.get('recent_window_top1_leader_service_id', '')}")
print(f"recent_window_top1_leader_title={review_gate_context.get('recent_window_top1_leader_title', '')}")
print(f"recent_window_top1_leader_share_pct={review_gate_context.get('recent_window_top1_leader_share_pct', '')}")
print(f"recent_window_top1_leader_real_user_users={review_gate_context.get('recent_window_top1_leader_real_user_users', '')}")
print(f"recent_window_target_top1_users={review_gate_context.get('recent_window_target_top1_users', '')}")
print(f"historical_example_dominance_detected={str(review_gate_context.get('historical_example_dominance_detected', '')).lower() if review_gate_context else ''}")
PY
