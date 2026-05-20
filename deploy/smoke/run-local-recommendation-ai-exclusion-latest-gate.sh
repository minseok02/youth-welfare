#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

STATUS_ROOT="${STATUS_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-latest-status}"
STATUS_JSON="${STATUS_JSON:-${STATUS_ROOT}/latest-status.json}"
FAIL_ON_LATEST_OBSERVATION_CHANGE="${FAIL_ON_LATEST_OBSERVATION_CHANGE:-false}"
STATUS_EXPORT_SCRIPT="${STATUS_EXPORT_SCRIPT:-${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-exclusion-latest-status-export.sh}"
AUTO_REFRESH_STATUS_JSON_IF_STALE="${AUTO_REFRESH_STATUS_JSON_IF_STALE:-false}"

FAIL_ON_LATEST_OBSERVATION_CHANGE="$(smoke_normalize_bool "${FAIL_ON_LATEST_OBSERVATION_CHANGE}")"
AUTO_REFRESH_STATUS_JSON_IF_STALE="$(smoke_normalize_bool "${AUTO_REFRESH_STATUS_JSON_IF_STALE}")"

if [[ ! -f "${STATUS_JSON}" ]]; then
  if [[ "${AUTO_REFRESH_STATUS_JSON_IF_STALE}" == "true" ]]; then
    bash "${STATUS_EXPORT_SCRIPT}" >/dev/null
  else
    echo "latest status json not found: ${STATUS_JSON}" >&2
    exit 1
  fi
fi

if [[ "${AUTO_REFRESH_STATUS_JSON_IF_STALE}" == "true" ]]; then
  REFRESH_SUMMARY="$(python3 - "${STATUS_JSON}" <<'PY'
import json
import sys
from pathlib import Path
status_json = Path(sys.argv[1])
if not status_json.is_file():
    print("")
    raise SystemExit(0)
data = json.loads(status_json.read_text(encoding="utf-8"))
print(data.get("refresh_summary", ""))
PY
)"
  DRIFT_SUMMARY="$(python3 - "${STATUS_JSON}" <<'PY'
import json
import sys
from pathlib import Path
status_json = Path(sys.argv[1])
if not status_json.is_file():
    print("")
    raise SystemExit(0)
data = json.loads(status_json.read_text(encoding="utf-8"))
print(data.get("drift_summary", ""))
PY
)"
  if [[ -n "${REFRESH_SUMMARY}" && -f "${REFRESH_SUMMARY}" && "${STATUS_JSON}" -ot "${REFRESH_SUMMARY}" ]]; then
    bash "${STATUS_EXPORT_SCRIPT}" >/dev/null
  elif [[ -n "${DRIFT_SUMMARY}" && -f "${DRIFT_SUMMARY}" && "${STATUS_JSON}" -ot "${DRIFT_SUMMARY}" ]]; then
    bash "${STATUS_EXPORT_SCRIPT}" >/dev/null
  fi
fi

smoke_require_command python3

python3 - "${STATUS_JSON}" "${FAIL_ON_LATEST_OBSERVATION_CHANGE}" <<'PY'
import json
import sys
from pathlib import Path

status_json = Path(sys.argv[1])
fail_on_latest_observation_change = sys.argv[2] == "true"

data = json.loads(status_json.read_text(encoding="utf-8"))
drift = data.get("latest_drift_check", {})
refresh_summary = Path(data.get("refresh_summary", ""))
drift_summary = Path(data.get("drift_summary", ""))

interpretation_changed = drift.get("interpretation_changed", "")
stable_baseline_changed = drift.get("stable_baseline_changed", "")
latest_observation_changed = drift.get("latest_observation_changed", "")
status_json_stale_relative_to_summaries = "false"
status_json_recommended_action = ""

if refresh_summary.is_file() and drift_summary.is_file():
    newest_summary_mtime = max(refresh_summary.stat().st_mtime, drift_summary.stat().st_mtime)
    if status_json.stat().st_mtime < newest_summary_mtime:
        status_json_stale_relative_to_summaries = "true"
        status_json_recommended_action = "RERUN_LATEST_STATUS_EXPORT"

status = "PASS"
reason = "LATEST_STATUS_OK"
exit_code = 0

if interpretation_changed == "true":
    status = "FAIL"
    reason = "INTERPRETATION_CHANGED"
    exit_code = 1
elif stable_baseline_changed == "true":
    status = "FAIL"
    reason = "STABLE_BASELINE_CHANGED"
    exit_code = 1
elif fail_on_latest_observation_change and latest_observation_changed == "true":
    status = "FAIL"
    reason = "LATEST_OBSERVATION_CHANGED"
    exit_code = 1

gate_action_class = data.get("gate_action_class", "")
if reason in {"INTERPRETATION_CHANGED", "STABLE_BASELINE_CHANGED"}:
    gate_action_class = "INVESTIGATE_BASELINE_DRIFT"
elif fail_on_latest_observation_change and latest_observation_changed == "true":
    gate_action_class = "OBSERVE_FRESH_WINDOW_VOLATILITY"
elif not gate_action_class:
    effective_operator_next_step = data.get('effective_operator_next_step', data.get('operator_next_step', ''))
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

gate_policy_status = data.get("gate_policy_status", "")
gate_policy_reason = data.get("gate_policy_reason", "")
review_gate_context = data.get("review_gate_context", {})
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

print(f"status_json={status_json}")
print(f"gate_status={status}")
print(f"gate_reason={reason}")
print(f"generated_at_utc={data.get('generated_at_utc', '')}")
print(f"generated_at_kst={data.get('generated_at_kst', '')}")
print(f"operator_next_step={data.get('operator_next_step', '')}")
print(f"effective_operator_next_step={data.get('effective_operator_next_step', data.get('operator_next_step', ''))}")
print(f"gate_action_class={gate_action_class}")
print(f"gate_policy_status={gate_policy_status}")
print(f"gate_policy_reason={gate_policy_reason}")
print(f"status_json_stale_relative_to_summaries={status_json_stale_relative_to_summaries}")
print(f"status_json_recommended_action={status_json_recommended_action}")
print(f"latest_drift_class={data.get('latest_drift_class', '')}")
print(f"latest_recommended_reading={data.get('latest_recommended_reading', '')}")
print(f"interpretation_changed={interpretation_changed}")
print(f"stable_baseline_changed={stable_baseline_changed}")
print(f"latest_observation_changed={latest_observation_changed}")
print(f"changed_keys={drift.get('changed_keys', '')}")
print(f"primary_review_gate_blocker_class={review_gate_context.get('primary_review_gate_blocker_class', '')}")
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
print(f"recent_window_recommendation_review_reading={review_gate_context.get('recent_window_recommendation_review_reading', '')}")
print(f"historical_example_dominance_detected={str(review_gate_context.get('historical_example_dominance_detected', '')).lower() if review_gate_context else ''}")

sys.exit(exit_code)
PY
