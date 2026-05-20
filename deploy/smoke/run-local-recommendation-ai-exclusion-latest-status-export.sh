#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

REFRESH_ROOT="${REFRESH_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-baseline-refresh}"
DRIFT_CHECK_ROOT="${DRIFT_CHECK_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-baseline-refresh-drift-check}"
STATUS_ROOT="${STATUS_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-latest-status}"
REVIEW_GATE_BLOCKER_ROOT="${REVIEW_GATE_BLOCKER_ROOT:-${ROOT_DIR}/tmp/recommendation-review-gate-blocker-audit}"
REVIEW_GATE_RECENT_WINDOW_ROOT="${REVIEW_GATE_RECENT_WINDOW_ROOT:-${ROOT_DIR}/tmp/recommendation-review-gate-recent-window-audit}"

REFRESH_SUMMARY="${REFRESH_SUMMARY:-${REFRESH_ROOT}/latest-baseline-refresh-summary.txt}"
DRIFT_SUMMARY="${DRIFT_SUMMARY:-${DRIFT_CHECK_ROOT}/latest-baseline-refresh-drift-summary.txt}"
REVIEW_GATE_BLOCKER_SUMMARY="${REVIEW_GATE_BLOCKER_SUMMARY:-${REVIEW_GATE_BLOCKER_ROOT}/latest-review-gate-blocker-summary.txt}"
REVIEW_GATE_RECENT_WINDOW_SUMMARY="${REVIEW_GATE_RECENT_WINDOW_SUMMARY:-${REVIEW_GATE_RECENT_WINDOW_ROOT}/latest-review-gate-recent-window-summary.txt}"

RUN_TS_UTC="$(smoke_now_ts_utc)"
GENERATED_AT_UTC="$(smoke_now_iso_utc)"
GENERATED_AT_KST="$(smoke_now_iso_kst)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${STATUS_ROOT}/${RUN_TS_UTC}}"
OUTPUT_MD="${ARTIFACT_DIR}/latest-status-note.md"
OUTPUT_JSON="${ARTIFACT_DIR}/latest-status.json"
LATEST_ARTIFACT_LINK="${STATUS_ROOT}/latest"
LATEST_NOTE_LINK="${STATUS_ROOT}/latest-status-note.md"
LATEST_JSON_LINK="${STATUS_ROOT}/latest-status.json"

if [[ ! -f "${REFRESH_SUMMARY}" ]]; then
  echo "latest refresh summary not found: ${REFRESH_SUMMARY}" >&2
  exit 1
fi

if [[ ! -f "${DRIFT_SUMMARY}" ]]; then
  echo "latest drift summary not found: ${DRIFT_SUMMARY}" >&2
  exit 1
fi

mkdir -p "${ARTIFACT_DIR}"
smoke_require_command python3

python3 - "${REFRESH_SUMMARY}" "${DRIFT_SUMMARY}" "${REVIEW_GATE_BLOCKER_SUMMARY}" "${REVIEW_GATE_RECENT_WINDOW_SUMMARY}" "${OUTPUT_MD}" "${OUTPUT_JSON}" "${GENERATED_AT_UTC}" "${GENERATED_AT_KST}" <<'PY'
import sys
import json
from pathlib import Path

refresh_path = Path(sys.argv[1])
drift_path = Path(sys.argv[2])
review_gate_blocker_path = Path(sys.argv[3])
review_gate_recent_window_path = Path(sys.argv[4])
output_md = Path(sys.argv[5])
output_json = Path(sys.argv[6])
generated_at_utc = sys.argv[7]
generated_at_kst = sys.argv[8]


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
review_gate_blocker = parse_kv(review_gate_blocker_path) if review_gate_blocker_path.is_file() else {}
review_gate_recent_window = parse_kv(review_gate_recent_window_path) if review_gate_recent_window_path.is_file() else {}

interpretation_changed = drift.get("interpretation_changed", "")
stable_baseline_changed = drift.get("stable_baseline_changed", "")
latest_observation_changed = drift.get("latest_observation_changed", "")
stable_dashboard_real_user_gate = refresh.get("stable_dashboard_real_user_gate", "")
stable_breakdown_real_user_cohort_gate = refresh.get("stable_breakdown_real_user_cohort_gate", "")
recent_window_recommendation_review_reading = review_gate_recent_window.get("blocker_class", "")
historical_example_dominance_detected = (
    review_gate_blocker.get("blocker_class", "") == "MIXED_BATCH_NON_REAL_DOMINANCE_WITH_NO_REAL_USER_PATH"
    and recent_window_recommendation_review_reading == "RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE"
)

review_gate_interpretation_class = "NO_SPECIAL_REVIEW_GATE_SPLIT"
review_gate_operating_mode = "USE_PRIMARY_REVIEW_GATE_ONLY"
if historical_example_dominance_detected:
    review_gate_interpretation_class = "HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR"
    review_gate_operating_mode = "PRIMARY_BASELINE_WITH_SUPPLEMENTAL_RECENT_WINDOW"
elif (
    review_gate_blocker.get("blocker_class", "") == "MIXED_BATCH_NON_REAL_DOMINANCE_WITH_NO_REAL_USER_PATH"
    and recent_window_recommendation_review_reading == "RECENT_WINDOW_STILL_TARGET_DOMINANT"
):
    review_gate_interpretation_class = "PRIMARY_AND_RECENT_WINDOW_BOTH_BLOCKING"
    review_gate_operating_mode = "KEEP_PRIMARY_TARGET_LEADER_TRACING"
elif review_gate_blocker.get("blocker_class", "") == "MIXED_BATCH_NON_REAL_DOMINANCE_WITH_NO_REAL_USER_PATH":
    review_gate_interpretation_class = "PRIMARY_BLOCKER_WITHOUT_SUPPLEMENTAL_SIGNAL"
    review_gate_operating_mode = "USE_PRIMARY_REVIEW_GATE_ONLY"

review_gate_policy_candidate_status = "NOT_A_POLICY_GATE_CANDIDATE"
review_gate_policy_candidate_reason = "NO_SPECIAL_REVIEW_GATE_SPLIT"
if review_gate_interpretation_class == "HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR":
    review_gate_policy_candidate_status = "RECENT_WINDOW_POLICY_CANDIDATE"
    review_gate_policy_candidate_reason = "PRIMARY_GATE_BLOCKED_BY_STALE_ALL_TIME_EXAMPLE_REFERENCE_BUT_RECENT_WINDOW_CLEAR"
elif review_gate_interpretation_class == "PRIMARY_AND_RECENT_WINDOW_BOTH_BLOCKING":
    review_gate_policy_candidate_status = "NOT_A_POLICY_GATE_CANDIDATE"
    review_gate_policy_candidate_reason = "RECENT_WINDOW_STILL_BLOCKED_BY_TARGET_DOMINANCE"
elif review_gate_interpretation_class == "PRIMARY_BLOCKER_WITHOUT_SUPPLEMENTAL_SIGNAL":
    review_gate_policy_candidate_status = "NOT_A_POLICY_GATE_CANDIDATE"
    review_gate_policy_candidate_reason = "PRIMARY_BLOCKER_HAS_NO_RECENT_WINDOW_CLEAR_SIGNAL"

review_gate_policy_promotion_status = "KEEP_PRIMARY_BASELINE"
review_gate_policy_promotion_reason = "RECENT_WINDOW_CANDIDATE_HAS_NOT_CLEARED_PRIMARY_BASELINE_REQUIREMENTS"
if review_gate_policy_candidate_status == "RECENT_WINDOW_POLICY_CANDIDATE":
    review_gate_policy_promotion_status = "REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW"
    review_gate_policy_promotion_reason = "RECENT_WINDOW_IS_A_CANDIDATE_BUT_PRIMARY_BASELINE_IS_STILL_ALL_TIME_LATEST"

review_gate_policy_promotion_action_status = "KEEP_PRIMARY_BASELINE"
review_gate_policy_promotion_action_reason = "RECENT_WINDOW_POLICY_PROMOTION_CONDITIONS_NOT_MET"
if review_gate_policy_promotion_status == "REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW":
    review_gate_policy_promotion_action_status = "KEEP_PRIMARY_BASELINE"
    review_gate_policy_promotion_action_reason = "PROMOTION_STILL_REQUIRES_EXPLICIT_POLICY_REVIEW"
elif review_gate_policy_promotion_status == "PROMOTION_READY":
    review_gate_policy_promotion_action_status = "RUN_BOUNDED_PROMOTION_REVIEW"
    review_gate_policy_promotion_action_reason = "PROMOTION_PREREQUISITES_MET_FOR_BOUNDED_REVIEW"

review_gate_policy_promotion_readiness_status = "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW"
review_gate_policy_promotion_readiness_reason = "BOUNDED_PROMOTION_REVIEW_PREREQUISITES_NOT_MET"
if review_gate_policy_candidate_status != "RECENT_WINDOW_POLICY_CANDIDATE":
    review_gate_policy_promotion_readiness_reason = "RECENT_WINDOW_POLICY_CANDIDATE_NOT_CONFIRMED"
elif review_gate_policy_promotion_status == "REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW":
    review_gate_policy_promotion_readiness_status = "READY_FOR_BOUNDED_PROMOTION_REVIEW"
    review_gate_policy_promotion_readiness_reason = "EXPLICIT_POLICY_REVIEW_PENDING_WITH_BOUNDED_REVIEW_PREREQUISITES_MET"
elif review_gate_policy_promotion_status == "PROMOTION_READY":
    review_gate_policy_promotion_readiness_status = "READY_FOR_BOUNDED_PROMOTION_REVIEW"
    review_gate_policy_promotion_readiness_reason = "BOUNDED_PROMOTION_REVIEW_PREREQUISITES_MET"

review_gate_policy_promotion_execution_status = "DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW"
review_gate_policy_promotion_execution_reason = review_gate_policy_promotion_readiness_reason
if review_gate_policy_promotion_readiness_status == "READY_FOR_BOUNDED_PROMOTION_REVIEW":
    if review_gate_policy_promotion_status == "REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW":
        review_gate_policy_promotion_execution_status = "AWAIT_EXPLICIT_POLICY_REVIEW_DECISION"
        review_gate_policy_promotion_execution_reason = "READINESS_MET_BUT_EXPLICIT_POLICY_REVIEW_DECISION_IS_STILL_PENDING"
    elif review_gate_policy_promotion_status == "PROMOTION_READY":
        review_gate_policy_promotion_execution_status = "RUN_BOUNDED_PROMOTION_REVIEW"
        review_gate_policy_promotion_execution_reason = "READINESS_MET_AND_PROMOTION_IS_READY_FOR_BOUNDED_REVIEW"

review_gate_policy_promotion_approval_status = "PROMOTION_APPROVAL_NOT_APPLICABLE"
review_gate_policy_promotion_approval_reason = review_gate_policy_promotion_execution_reason
if review_gate_policy_promotion_execution_status == "AWAIT_EXPLICIT_POLICY_REVIEW_DECISION":
    review_gate_policy_promotion_approval_status = "PENDING_EXPLICIT_PROMOTION_APPROVAL"
    review_gate_policy_promotion_approval_reason = "EXECUTION_READY_BUT_EXPLICIT_PROMOTION_APPROVAL_NOT_RECORDED"
elif review_gate_policy_promotion_execution_status == "RUN_BOUNDED_PROMOTION_REVIEW":
    review_gate_policy_promotion_approval_status = "BOUNDED_PROMOTION_REVIEW_APPROVED"
    review_gate_policy_promotion_approval_reason = "EXECUTION_STATUS_ALREADY_ALLOWS_BOUNDED_PROMOTION_REVIEW"

review_gate_policy_promotion_approval_criteria_status = "NOT_READY_FOR_EXPLICIT_PROMOTION_APPROVAL"
review_gate_policy_promotion_approval_criteria_reason = review_gate_policy_promotion_readiness_reason
if review_gate_policy_promotion_readiness_status == "READY_FOR_BOUNDED_PROMOTION_REVIEW":
    if review_gate_policy_candidate_status != "RECENT_WINDOW_POLICY_CANDIDATE":
        review_gate_policy_promotion_approval_criteria_reason = "RECENT_WINDOW_POLICY_CANDIDATE_NOT_CONFIRMED"
    elif review_gate_policy_promotion_status not in {"REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW", "PROMOTION_READY"}:
        review_gate_policy_promotion_approval_criteria_reason = "PROMOTION_REVIEW_STATE_NOT_ACTIVE"
    elif review_gate_policy_promotion_execution_status not in {"AWAIT_EXPLICIT_POLICY_REVIEW_DECISION", "RUN_BOUNDED_PROMOTION_REVIEW"}:
        review_gate_policy_promotion_approval_criteria_reason = "PROMOTION_EXECUTION_LAYER_NOT_READY_FOR_EXPLICIT_APPROVAL"
    else:
        review_gate_policy_promotion_approval_criteria_status = "READY_FOR_EXPLICIT_PROMOTION_APPROVAL"
        if review_gate_policy_promotion_status == "PROMOTION_READY":
            review_gate_policy_promotion_approval_criteria_reason = "PROMOTION_READY_AND_EXECUTION_LAYER_ALIGNED"
        else:
            review_gate_policy_promotion_approval_criteria_reason = "PRIMARY_STALENESS_AND_RECENT_WINDOW_SIGNAL_CONFIRMED"

review_gate_policy_promotion_approval_decision_status = "APPROVAL_DECISION_NOT_APPLICABLE"
review_gate_policy_promotion_approval_decision_reason = review_gate_policy_promotion_approval_reason
if review_gate_policy_promotion_approval_criteria_status != "READY_FOR_EXPLICIT_PROMOTION_APPROVAL":
    review_gate_policy_promotion_approval_decision_status = "APPROVAL_DECISION_NOT_READY"
    review_gate_policy_promotion_approval_decision_reason = review_gate_policy_promotion_approval_criteria_reason
elif review_gate_policy_promotion_approval_status == "PENDING_EXPLICIT_PROMOTION_APPROVAL":
    review_gate_policy_promotion_approval_decision_status = "AWAIT_EXPLICIT_PROMOTION_APPROVAL_DECISION"
    review_gate_policy_promotion_approval_decision_reason = "APPROVAL_CRITERIA_MET_BUT_EXPLICIT_APPROVAL_NOT_RECORDED"
elif review_gate_policy_promotion_approval_status == "BOUNDED_PROMOTION_REVIEW_APPROVED":
    review_gate_policy_promotion_approval_decision_status = "APPROVED_FOR_BOUNDED_PROMOTION_REVIEW"
    review_gate_policy_promotion_approval_decision_reason = "EXPLICIT_PROMOTION_APPROVAL_RECORDED"

review_gate_policy_promotion_approval_record_status = "APPROVAL_RECORD_NOT_APPLICABLE"
review_gate_policy_promotion_approval_record_reason = review_gate_policy_promotion_approval_decision_reason
if review_gate_policy_promotion_approval_decision_status == "APPROVAL_DECISION_NOT_READY":
    review_gate_policy_promotion_approval_record_status = "APPROVAL_RECORD_NOT_READY"
    review_gate_policy_promotion_approval_record_reason = review_gate_policy_promotion_approval_decision_reason
elif review_gate_policy_promotion_approval_decision_status == "AWAIT_EXPLICIT_PROMOTION_APPROVAL_DECISION":
    review_gate_policy_promotion_approval_record_status = "PENDING_EXPLICIT_PROMOTION_APPROVAL_RECORD"
    review_gate_policy_promotion_approval_record_reason = "APPROVAL_DECISION_PENDING_AND_RECORD_NOT_WRITTEN"
elif review_gate_policy_promotion_approval_decision_status == "APPROVED_FOR_BOUNDED_PROMOTION_REVIEW":
    review_gate_policy_promotion_approval_record_status = "EXPLICIT_PROMOTION_APPROVAL_RECORDED"
    review_gate_policy_promotion_approval_record_reason = "APPROVAL_RECORD_ALLOWS_BOUNDED_PROMOTION_REVIEW"

review_gate_policy_promotion_review_run_status = "BOUNDED_PROMOTION_REVIEW_RUN_NOT_APPLICABLE"
review_gate_policy_promotion_review_run_reason = review_gate_policy_promotion_approval_record_reason
if review_gate_policy_promotion_approval_record_status == "APPROVAL_RECORD_NOT_READY":
    review_gate_policy_promotion_review_run_status = "BOUNDED_PROMOTION_REVIEW_RUN_NOT_READY"
    review_gate_policy_promotion_review_run_reason = review_gate_policy_promotion_approval_record_reason
elif review_gate_policy_promotion_approval_record_status == "PENDING_EXPLICIT_PROMOTION_APPROVAL_RECORD":
    review_gate_policy_promotion_review_run_status = "PENDING_BOUNDED_PROMOTION_REVIEW_RUN"
    review_gate_policy_promotion_review_run_reason = "EXPLICIT_APPROVAL_RECORD_NOT_WRITTEN_FOR_BOUNDED_REVIEW_RUN"
elif review_gate_policy_promotion_approval_record_status == "EXPLICIT_PROMOTION_APPROVAL_RECORDED":
    review_gate_policy_promotion_review_run_status = "AWAIT_BOUNDED_PROMOTION_REVIEW_RUN"
    review_gate_policy_promotion_review_run_reason = "APPROVAL_RECORDED_BUT_BOUNDED_REVIEW_RUN_NOT_EXECUTED"

review_gate_policy_promotion_review_run_criteria_status = "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN"
review_gate_policy_promotion_review_run_criteria_reason = review_gate_policy_promotion_approval_criteria_reason
if review_gate_policy_promotion_approval_criteria_status == "READY_FOR_EXPLICIT_PROMOTION_APPROVAL":
    if review_gate_policy_promotion_approval_record_status == "PENDING_EXPLICIT_PROMOTION_APPROVAL_RECORD":
        review_gate_policy_promotion_review_run_criteria_status = "READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN"
        review_gate_policy_promotion_review_run_criteria_reason = "BOUNDED_REVIEW_RUN_PREREQUISITES_MET_BUT_APPROVAL_RECORD_PENDING"
    elif review_gate_policy_promotion_approval_record_status == "EXPLICIT_PROMOTION_APPROVAL_RECORDED":
        review_gate_policy_promotion_review_run_criteria_status = "READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN"
        review_gate_policy_promotion_review_run_criteria_reason = "EXPLICIT_APPROVAL_RECORD_SUPPORTS_BOUNDED_REVIEW_RUN"
    else:
        review_gate_policy_promotion_review_run_criteria_reason = review_gate_policy_promotion_approval_record_reason
else:
    review_gate_policy_promotion_review_run_criteria_reason = review_gate_policy_promotion_approval_criteria_reason

review_gate_policy_promotion_review_run_decision_status = "BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_APPLICABLE"
review_gate_policy_promotion_review_run_decision_reason = review_gate_policy_promotion_review_run_reason
if review_gate_policy_promotion_review_run_criteria_status != "READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN":
    review_gate_policy_promotion_review_run_decision_status = "BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_READY"
    review_gate_policy_promotion_review_run_decision_reason = review_gate_policy_promotion_review_run_criteria_reason
elif review_gate_policy_promotion_review_run_status == "PENDING_BOUNDED_PROMOTION_REVIEW_RUN":
    review_gate_policy_promotion_review_run_decision_status = "AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_DECISION"
    review_gate_policy_promotion_review_run_decision_reason = "REVIEW_RUN_CRITERIA_MET_BUT_APPROVAL_RECORD_NOT_WRITTEN"
elif review_gate_policy_promotion_review_run_status == "AWAIT_BOUNDED_PROMOTION_REVIEW_RUN":
    review_gate_policy_promotion_review_run_decision_status = "BOUNDED_PROMOTION_REVIEW_RUN_APPROVED"
    review_gate_policy_promotion_review_run_decision_reason = "APPROVAL_RECORD_SUPPORTS_BOUNDED_REVIEW_RUN_EXECUTION"

gate_policy_status = "BASELINE_MONITORING"
gate_policy_reason = "NO_SPECIAL_REVIEW_GATE_SPLIT"
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

effective_operator_next_step = operator_next_step
if interpretation_changed == "true" or stable_baseline_changed == "true":
    effective_operator_next_step = "INVESTIGATE_STABLE_BASELINE_DRIFT"
elif historical_example_dominance_detected:
    effective_operator_next_step = "USE_RECENT_WINDOW_AS_SUPPLEMENTAL_REVIEW_CONTEXT"
elif review_gate_blocker.get("blocker_class", "") == "MIXED_BATCH_NON_REAL_DOMINANCE_WITH_NO_REAL_USER_PATH":
    effective_operator_next_step = review_gate_blocker.get(
        "operator_next_step",
        "INVESTIGATE_SAME_PROFILE_EXAMPLE_VS_REAL_USER_DIFFERENTIAL",
    )
elif recent_window_recommendation_review_reading == "RECENT_WINDOW_STILL_TARGET_DOMINANT":
    effective_operator_next_step = "KEEP_TRACING_CURRENT_TARGET_LEADER_PATH"

gate_action_class = "KEEP_BASELINE_MONITORING"
if interpretation_changed == "true" or stable_baseline_changed == "true":
    gate_action_class = "INVESTIGATE_BASELINE_DRIFT"
elif effective_operator_next_step == "USE_RECENT_WINDOW_AS_SUPPLEMENTAL_REVIEW_CONTEXT":
    gate_action_class = "READ_PRIMARY_AND_SUPPLEMENTAL_REVIEW_GATES"
elif effective_operator_next_step == "WAIT_FOR_REAL_USER_LEADER_SIGNAL":
    gate_action_class = "WAIT_FOR_REAL_USER_LEADER_SIGNAL"
elif effective_operator_next_step == "RUN_REAL_USER_RECHECK_DECISION":
    gate_action_class = "RUN_REAL_USER_RECHECK"
elif effective_operator_next_step == "OBSERVE_FRESH_WINDOW_VOLATILITY":
    gate_action_class = "OBSERVE_FRESH_WINDOW_VOLATILITY"
elif effective_operator_next_step == "KEEP_TRACING_CURRENT_TARGET_LEADER_PATH":
    gate_action_class = "TRACE_CURRENT_TARGET_LEADER_PATH"

lines = [
    "# AI Exclusion Latest Status",
    "",
    f"- generated_at_utc: `{generated_at_utc}`",
    f"- generated_at_kst: `{generated_at_kst}`",
    f"- operator_next_step: `{operator_next_step}`",
    f"- effective_operator_next_step: `{effective_operator_next_step}`",
    f"- gate_action_class: `{gate_action_class}`",
    f"- gate_policy_status: `{gate_policy_status}`",
    f"- gate_policy_reason: `{gate_policy_reason}`",
    f"- refresh_summary: `{refresh_path}`",
    f"- drift_summary: `{drift_path}`",
    f"- latest_drift_class: `{refresh.get('drift_class', '')}`",
    f"- latest_recommended_reading: `{refresh.get('recommended_reading', '')}`",
    "",
    "## Stable Baseline",
    "",
    f"- zero_ai_reason_buckets: `{refresh.get('stable_baseline_zero_ai_reason_buckets', '')}`",
    f"- dashboard_real_user_gate: `{refresh.get('stable_dashboard_real_user_gate', '')}`",
    f"- breakdown_real_user_cohort_gate: `{refresh.get('stable_breakdown_real_user_cohort_gate', '')}`",
    "",
    "## Latest Observation",
    "",
    f"- fresh_top_ai_zero_count: `{refresh.get('latest_fresh_top_ai_zero_count', '')}`",
    f"- ai_zero_count: `{refresh.get('latest_ai_zero_count', '')}`",
    f"- ai_zero_reason_buckets: `{refresh.get('latest_ai_zero_reason_buckets', '')}`",
    f"- volatility_reference_frequency: `{refresh.get('volatility_reference_frequency', '')}`",
    "",
    "## Latest Drift Check",
    "",
    f"- drift_detected: `{drift.get('drift_detected', '')}`",
    f"- interpretation_changed: `{drift.get('interpretation_changed', '')}`",
    f"- stable_baseline_changed: `{drift.get('stable_baseline_changed', '')}`",
    f"- latest_observation_changed: `{drift.get('latest_observation_changed', '')}`",
    f"- changed_keys: `{drift.get('changed_keys', '')}`",
    f"- stable_changed_keys: `{drift.get('stable_changed_keys', '')}`",
    f"- latest_observation_changed_keys: `{drift.get('latest_changed_keys', '')}`",
    "",
    "## Review Gate Context",
    "",
    f"- primary_review_gate_blocker_class: `{review_gate_blocker.get('blocker_class', '')}`",
    f"- primary_review_gate_operator_next_step: `{review_gate_blocker.get('operator_next_step', '')}`",
    f"- review_gate_interpretation_class: `{review_gate_interpretation_class}`",
    f"- review_gate_operating_mode: `{review_gate_operating_mode}`",
    f"- review_gate_policy_candidate_status: `{review_gate_policy_candidate_status}`",
    f"- review_gate_policy_candidate_reason: `{review_gate_policy_candidate_reason}`",
    f"- review_gate_policy_promotion_status: `{review_gate_policy_promotion_status}`",
    f"- review_gate_policy_promotion_reason: `{review_gate_policy_promotion_reason}`",
    f"- review_gate_policy_promotion_action_status: `{review_gate_policy_promotion_action_status}`",
    f"- review_gate_policy_promotion_action_reason: `{review_gate_policy_promotion_action_reason}`",
    f"- review_gate_policy_promotion_readiness_status: `{review_gate_policy_promotion_readiness_status}`",
    f"- review_gate_policy_promotion_readiness_reason: `{review_gate_policy_promotion_readiness_reason}`",
    f"- review_gate_policy_promotion_execution_status: `{review_gate_policy_promotion_execution_status}`",
    f"- review_gate_policy_promotion_execution_reason: `{review_gate_policy_promotion_execution_reason}`",
    f"- review_gate_policy_promotion_approval_criteria_status: `{review_gate_policy_promotion_approval_criteria_status}`",
    f"- review_gate_policy_promotion_approval_criteria_reason: `{review_gate_policy_promotion_approval_criteria_reason}`",
    f"- review_gate_policy_promotion_approval_status: `{review_gate_policy_promotion_approval_status}`",
    f"- review_gate_policy_promotion_approval_reason: `{review_gate_policy_promotion_approval_reason}`",
    f"- review_gate_policy_promotion_approval_decision_status: `{review_gate_policy_promotion_approval_decision_status}`",
    f"- review_gate_policy_promotion_approval_decision_reason: `{review_gate_policy_promotion_approval_decision_reason}`",
    f"- review_gate_policy_promotion_approval_record_status: `{review_gate_policy_promotion_approval_record_status}`",
    f"- review_gate_policy_promotion_approval_record_reason: `{review_gate_policy_promotion_approval_record_reason}`",
    f"- review_gate_policy_promotion_review_run_status: `{review_gate_policy_promotion_review_run_status}`",
    f"- review_gate_policy_promotion_review_run_reason: `{review_gate_policy_promotion_review_run_reason}`",
    f"- review_gate_policy_promotion_review_run_criteria_status: `{review_gate_policy_promotion_review_run_criteria_status}`",
    f"- review_gate_policy_promotion_review_run_criteria_reason: `{review_gate_policy_promotion_review_run_criteria_reason}`",
    f"- review_gate_policy_promotion_review_run_decision_status: `{review_gate_policy_promotion_review_run_decision_status}`",
    f"- review_gate_policy_promotion_review_run_decision_reason: `{review_gate_policy_promotion_review_run_decision_reason}`",
    f"- primary_mixed_top1_leader_service_id: `{review_gate_blocker.get('mixed_top1_leader_service_id', '')}`",
    f"- primary_mixed_top1_leader_title: `{review_gate_blocker.get('mixed_top1_leader_title', '')}`",
    f"- primary_mixed_top1_leader_share_pct: `{review_gate_blocker.get('mixed_top1_leader_share_pct', '')}`",
    f"- primary_mixed_top1_leader_real_user_users: `{review_gate_blocker.get('mixed_top1_leader_real_user_users', '')}`",
    f"- recent_window_recommendation_review_reading: `{recent_window_recommendation_review_reading}`",
    f"- recent_window_hours: `{review_gate_recent_window.get('recent_window_hours', '')}`",
    f"- recent_window_top1_leader_service_id: `{review_gate_recent_window.get('recent_top1_leader_service_id', '')}`",
    f"- recent_window_top1_leader_title: `{review_gate_recent_window.get('recent_top1_leader_title', '')}`",
    f"- recent_window_top1_leader_share_pct: `{review_gate_recent_window.get('recent_top1_leader_share_pct', '')}`",
    f"- recent_window_top1_leader_real_user_users: `{review_gate_recent_window.get('recent_top1_leader_real_user_users', '')}`",
    f"- recent_window_target_top1_users: `{review_gate_recent_window.get('recent_target_top1_users', '')}`",
    f"- historical_example_dominance_detected: `{str(historical_example_dominance_detected).lower()}`",
]

output_md.write_text("\n".join(lines) + "\n", encoding="utf-8")
json_payload = {
    "generated_at_utc": generated_at_utc,
    "generated_at_kst": generated_at_kst,
    "operator_next_step": operator_next_step,
    "effective_operator_next_step": effective_operator_next_step,
    "gate_action_class": gate_action_class,
    "gate_policy_status": gate_policy_status,
    "gate_policy_reason": gate_policy_reason,
    "status_json_stale_relative_to_summaries": "false",
    "status_json_recommended_action": "",
    "refresh_summary": str(refresh_path),
    "drift_summary": str(drift_path),
    "review_gate_blocker_summary": str(review_gate_blocker_path) if review_gate_blocker_path.is_file() else "",
    "review_gate_recent_window_summary": str(review_gate_recent_window_path) if review_gate_recent_window_path.is_file() else "",
    "latest_drift_class": refresh.get("drift_class", ""),
    "latest_recommended_reading": refresh.get("recommended_reading", ""),
    "stable_baseline": {
        "zero_ai_reason_buckets": refresh.get("stable_baseline_zero_ai_reason_buckets", ""),
        "dashboard_real_user_gate": refresh.get("stable_dashboard_real_user_gate", ""),
        "breakdown_real_user_cohort_gate": refresh.get("stable_breakdown_real_user_cohort_gate", ""),
    },
    "latest_observation": {
        "fresh_top_ai_zero_count": refresh.get("latest_fresh_top_ai_zero_count", ""),
        "ai_zero_count": refresh.get("latest_ai_zero_count", ""),
        "ai_zero_reason_buckets": refresh.get("latest_ai_zero_reason_buckets", ""),
        "volatility_reference_frequency": refresh.get("volatility_reference_frequency", ""),
    },
    "latest_drift_check": {
        "drift_detected": drift.get("drift_detected", ""),
        "interpretation_changed": drift.get("interpretation_changed", ""),
        "stable_baseline_changed": drift.get("stable_baseline_changed", ""),
        "latest_observation_changed": drift.get("latest_observation_changed", ""),
        "changed_keys": drift.get("changed_keys", ""),
        "stable_changed_keys": drift.get("stable_changed_keys", ""),
        "latest_observation_changed_keys": drift.get("latest_changed_keys", ""),
    },
    "review_gate_context": {
        "primary_review_gate_blocker_class": review_gate_blocker.get("blocker_class", ""),
        "primary_review_gate_operator_next_step": review_gate_blocker.get("operator_next_step", ""),
        "review_gate_interpretation_class": review_gate_interpretation_class,
        "review_gate_operating_mode": review_gate_operating_mode,
        "review_gate_policy_candidate_status": review_gate_policy_candidate_status,
        "review_gate_policy_candidate_reason": review_gate_policy_candidate_reason,
        "review_gate_policy_promotion_status": review_gate_policy_promotion_status,
        "review_gate_policy_promotion_reason": review_gate_policy_promotion_reason,
        "review_gate_policy_promotion_action_status": review_gate_policy_promotion_action_status,
        "review_gate_policy_promotion_action_reason": review_gate_policy_promotion_action_reason,
        "review_gate_policy_promotion_readiness_status": review_gate_policy_promotion_readiness_status,
        "review_gate_policy_promotion_readiness_reason": review_gate_policy_promotion_readiness_reason,
        "review_gate_policy_promotion_execution_status": review_gate_policy_promotion_execution_status,
        "review_gate_policy_promotion_execution_reason": review_gate_policy_promotion_execution_reason,
        "review_gate_policy_promotion_approval_criteria_status": review_gate_policy_promotion_approval_criteria_status,
        "review_gate_policy_promotion_approval_criteria_reason": review_gate_policy_promotion_approval_criteria_reason,
        "review_gate_policy_promotion_approval_status": review_gate_policy_promotion_approval_status,
        "review_gate_policy_promotion_approval_reason": review_gate_policy_promotion_approval_reason,
        "review_gate_policy_promotion_approval_decision_status": review_gate_policy_promotion_approval_decision_status,
        "review_gate_policy_promotion_approval_decision_reason": review_gate_policy_promotion_approval_decision_reason,
        "review_gate_policy_promotion_approval_record_status": review_gate_policy_promotion_approval_record_status,
        "review_gate_policy_promotion_approval_record_reason": review_gate_policy_promotion_approval_record_reason,
        "review_gate_policy_promotion_review_run_status": review_gate_policy_promotion_review_run_status,
        "review_gate_policy_promotion_review_run_reason": review_gate_policy_promotion_review_run_reason,
        "review_gate_policy_promotion_review_run_criteria_status": review_gate_policy_promotion_review_run_criteria_status,
        "review_gate_policy_promotion_review_run_criteria_reason": review_gate_policy_promotion_review_run_criteria_reason,
        "review_gate_policy_promotion_review_run_decision_status": review_gate_policy_promotion_review_run_decision_status,
        "review_gate_policy_promotion_review_run_decision_reason": review_gate_policy_promotion_review_run_decision_reason,
        "primary_mixed_top1_leader_service_id": review_gate_blocker.get("mixed_top1_leader_service_id", ""),
        "primary_mixed_top1_leader_title": review_gate_blocker.get("mixed_top1_leader_title", ""),
        "primary_mixed_top1_leader_share_pct": review_gate_blocker.get("mixed_top1_leader_share_pct", ""),
        "primary_mixed_top1_leader_real_user_users": review_gate_blocker.get("mixed_top1_leader_real_user_users", ""),
        "recent_window_recommendation_review_reading": recent_window_recommendation_review_reading,
        "recent_window_hours": review_gate_recent_window.get("recent_window_hours", ""),
        "recent_window_top1_leader_service_id": review_gate_recent_window.get("recent_top1_leader_service_id", ""),
        "recent_window_top1_leader_title": review_gate_recent_window.get("recent_top1_leader_title", ""),
        "recent_window_top1_leader_share_pct": review_gate_recent_window.get("recent_top1_leader_share_pct", ""),
        "recent_window_top1_leader_real_user_users": review_gate_recent_window.get("recent_top1_leader_real_user_users", ""),
        "recent_window_target_top1_users": review_gate_recent_window.get("recent_target_top1_users", ""),
        "historical_example_dominance_detected": historical_example_dominance_detected,
    },
}
output_json.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(output_md)
print()
print(output_md.read_text(encoding="utf-8"), end="")
print()
print(output_json)
PY

mkdir -p "${STATUS_ROOT}"
smoke_update_links \
  "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}" \
  "${OUTPUT_MD}" "${LATEST_NOTE_LINK}" \
  "${OUTPUT_JSON}" "${LATEST_JSON_LINK}"

echo
echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_note_link=${LATEST_NOTE_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
