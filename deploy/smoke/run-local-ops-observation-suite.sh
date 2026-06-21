#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
OPS_OBSERVATION_ROOT="${OPS_OBSERVATION_ROOT:-${ROOT_DIR}/tmp/ops-observation}"
RUN_USER_PROFILE_STANDARD_CODE_COVERAGE_AUDIT="${RUN_USER_PROFILE_STANDARD_CODE_COVERAGE_AUDIT:-true}"
RUN_RECOMMENDATION_STANDARD_CODE_OBSERVATION="${RUN_RECOMMENDATION_STANDARD_CODE_OBSERVATION:-true}"
RUN_POLICY_DATA_TRIAGE_OBSERVATION="${RUN_POLICY_DATA_TRIAGE_OBSERVATION:-true}"
RUN_CHAT_OBSERVABILITY_AUDIT="${RUN_CHAT_OBSERVABILITY_AUDIT:-true}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OPS_OBSERVATION_ROOT}/${RUN_TS_UTC}}"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS:-14}"
TREND_WINDOW_DAYS_CSV="${TREND_WINDOW_DAYS_CSV:-1,7,30}"
BREAKDOWN_LIMIT="${BREAKDOWN_LIMIT:-3}"
COLLECT_LIMIT="${COLLECT_LIMIT:-5}"

CHILD_ARTIFACT_DIR="${ARTIFACT_DIR}/ops-baseline-artifact"
CHILD_OUTPUT="${ARTIFACT_DIR}/ops-baseline.txt"
ATTENTION_FEED_OUTPUT="${ARTIFACT_DIR}/attention-feed.out"
STANDARD_CODE_COVERAGE_OUTPUT="${ARTIFACT_DIR}/user-profile-standard-code-coverage.out"
RECOMMENDATION_STANDARD_CODE_OBSERVATION_OUTPUT="${ARTIFACT_DIR}/recommendation-standard-code-observation.out"
POLICY_DATA_TRIAGE_OBSERVATION_OUTPUT="${ARTIFACT_DIR}/policy-data-triage-observation.out"
CHAT_OBSERVABILITY_OUTPUT="${ARTIFACT_DIR}/chat-observability.out"
SUMMARY_OUT="${ARTIFACT_DIR}/ops-observation-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/ops-observation.json"
NOTE_OUT="${ARTIFACT_DIR}/ops-observation-note.md"
DURATIONS_TSV="${ARTIFACT_DIR}/ops-observation-durations.tsv"

LATEST_ARTIFACT_LINK="${OPS_OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OPS_OBSERVATION_ROOT}/latest-ops-observation-summary.txt"
LATEST_JSON_LINK="${OPS_OBSERVATION_ROOT}/latest-ops-observation.json"
LATEST_NOTE_LINK="${OPS_OBSERVATION_ROOT}/latest-ops-observation-note.md"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
RUN_USER_PROFILE_STANDARD_CODE_COVERAGE_AUDIT="$(smoke_normalize_bool "${RUN_USER_PROFILE_STANDARD_CODE_COVERAGE_AUDIT}")"
RUN_RECOMMENDATION_STANDARD_CODE_OBSERVATION="$(smoke_normalize_bool "${RUN_RECOMMENDATION_STANDARD_CODE_OBSERVATION}")"
RUN_POLICY_DATA_TRIAGE_OBSERVATION="$(smoke_normalize_bool "${RUN_POLICY_DATA_TRIAGE_OBSERVATION}")"
RUN_CHAT_OBSERVABILITY_AUDIT="$(smoke_normalize_bool "${RUN_CHAT_OBSERVABILITY_AUDIT}")"
mkdir -p "${ARTIFACT_DIR}" "${CHILD_ARTIFACT_DIR}"
printf 'label\texit_code\tduration_ms\toutput_file\n' > "${DURATIONS_TSV}"

smoke_require_command bash
smoke_require_command python3
smoke_resolve_admin_credentials "${ROOT_DIR}"
smoke_resolve_admin_access_token "${ROOT_DIR}"

smoke_print_step "ops baseline"
set +e
ARTIFACT_DIR="${CHILD_ARTIFACT_DIR}" \
KEEP_ARTIFACTS=true \
APP_BASE_URL="${APP_BASE_URL}" \
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS}" \
TREND_WINDOW_DAYS_CSV="${TREND_WINDOW_DAYS_CSV}" \
BREAKDOWN_LIMIT="${BREAKDOWN_LIMIT}" \
COLLECT_LIMIT="${COLLECT_LIMIT}" \
smoke_duration_step "ops_baseline" "${CHILD_OUTPUT}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-ops-baseline-suite.sh" >> "${DURATIONS_TSV}"
STATUS=$?
set -e
cat "${CHILD_OUTPUT}"
if [[ "${STATUS}" -ne 0 ]]; then
  exit "${STATUS}"
fi

if [[ "${RUN_USER_PROFILE_STANDARD_CODE_COVERAGE_AUDIT}" == "true" ]]; then
  smoke_print_step "user profile standard code coverage"
  set +e
  smoke_duration_step "user_profile_standard_code_coverage" "${STANDARD_CODE_COVERAGE_OUTPUT}" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-user-profile-standard-code-coverage-audit.sh" >> "${DURATIONS_TSV}"
  STATUS=$?
  set -e
  cat "${STANDARD_CODE_COVERAGE_OUTPUT}"
  if [[ "${STATUS}" -ne 0 ]]; then
    exit "${STATUS}"
  fi
fi

smoke_print_step "attention feed"
set +e
APP_BASE_URL="${APP_BASE_URL}" \
KEEP_ARTIFACTS=true \
ARTIFACT_DIR="${ARTIFACT_DIR}/attention-feed-artifact" \
smoke_duration_step "attention_feed" "${ATTENTION_FEED_OUTPUT}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-admin-attention-feed-smoke.sh" >> "${DURATIONS_TSV}"
STATUS=$?
set -e
cat "${ATTENTION_FEED_OUTPUT}"
if [[ "${STATUS}" -ne 0 ]]; then
  exit "${STATUS}"
fi

if [[ "${RUN_RECOMMENDATION_STANDARD_CODE_OBSERVATION}" == "true" ]]; then
  smoke_print_step "recommendation standard code observation"
  set +e
  APP_BASE_URL="${APP_BASE_URL}" \
  KEEP_ARTIFACTS=true \
  ARTIFACT_DIR="${ARTIFACT_DIR}/recommendation-observation-artifact" \
  smoke_duration_step "recommendation_standard_code_observation" "${RECOMMENDATION_STANDARD_CODE_OBSERVATION_OUTPUT}" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-observation-suite.sh" >> "${DURATIONS_TSV}"
  STATUS=$?
  set -e
  cat "${RECOMMENDATION_STANDARD_CODE_OBSERVATION_OUTPUT}"
  if [[ "${STATUS}" -ne 0 ]]; then
    exit "${STATUS}"
  fi
fi

if [[ "${RUN_POLICY_DATA_TRIAGE_OBSERVATION}" == "true" ]]; then
  smoke_print_step "policy data triage observation"
  set +e
  KEEP_ARTIFACTS=true \
  ARTIFACT_DIR="${ARTIFACT_DIR}/policy-data-triage-observation-artifact" \
  smoke_duration_step "policy_data_triage_observation" "${POLICY_DATA_TRIAGE_OBSERVATION_OUTPUT}" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-policy-data-triage-observation-suite.sh" >> "${DURATIONS_TSV}"
  STATUS=$?
  set -e
  cat "${POLICY_DATA_TRIAGE_OBSERVATION_OUTPUT}"
  if [[ "${STATUS}" -ne 0 ]]; then
    exit "${STATUS}"
  fi
fi

if [[ "${RUN_CHAT_OBSERVABILITY_AUDIT}" == "true" ]]; then
  smoke_print_step "chat observability"
  set +e
  KEEP_ARTIFACTS=true \
  ARTIFACT_DIR="${ARTIFACT_DIR}/chat-observability-artifact" \
  smoke_duration_step "chat_observability" "${CHAT_OBSERVABILITY_OUTPUT}" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-chat-observability-audit.sh" >> "${DURATIONS_TSV}"
  STATUS=$?
  set -e
  cat "${CHAT_OBSERVABILITY_OUTPUT}"
  if [[ "${STATUS}" -ne 0 ]]; then
    exit "${STATUS}"
  fi
fi

python3 - "${DURATIONS_TSV}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" "${RUN_TS_UTC}" "${APP_BASE_URL}" "${SUMMARY_WINDOW_DAYS}" "${TREND_WINDOW_DAYS_CSV}" "${BREAKDOWN_LIMIT}" "${COLLECT_LIMIT}" "${CHILD_ARTIFACT_DIR}" "${ATTENTION_FEED_OUTPUT}" "${STANDARD_CODE_COVERAGE_OUTPUT}" "${RUN_USER_PROFILE_STANDARD_CODE_COVERAGE_AUDIT}" "${RECOMMENDATION_STANDARD_CODE_OBSERVATION_OUTPUT}" "${RUN_RECOMMENDATION_STANDARD_CODE_OBSERVATION}" "${POLICY_DATA_TRIAGE_OBSERVATION_OUTPUT}" "${RUN_POLICY_DATA_TRIAGE_OBSERVATION}" "${CHAT_OBSERVABILITY_OUTPUT}" "${RUN_CHAT_OBSERVABILITY_AUDIT}" "${ROOT_DIR}" <<'PY'
import csv
import json
import sys
from pathlib import Path


def read_key_values(path: Path):
    data = {}
    if not path.exists():
        return data
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if line.startswith("METRIC "):
            line = line[len("METRIC "):]
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key.strip()] = value.strip()
    return data


def find_previous_current_priority_summary(current_priority_root: Path):
    if not current_priority_root.exists():
        return None
    run_dirs = sorted(
        [
            path for path in current_priority_root.iterdir()
            if path.is_dir() and path.name != "latest" and (path / "current-priority-summary.txt").exists()
        ],
        key=lambda path: path.name,
    )
    if len(run_dirs) < 2:
        return None
    return run_dirs[-2] / "current-priority-summary.txt"


def build_missing_delta_label(previous_available: bool, delta: int):
    if not previous_available:
        return "이전값 없음"
    if delta > 0:
        return f"{delta} 증가"
    if delta < 0:
        return f"{abs(delta)} 감소"
    return "변화 없음"


def build_observation_transition_label(previous_available: bool, changed: bool, previous_status: str, current_status: str):
    if not previous_available:
        return "이전값 없음"
    if not changed:
        return "변화 없음"
    return f"{previous_status or '—'} -> {current_status or '—'}"


def has_value(value: str):
    return bool((value or "").strip())


def build_promoted_alert(previous_available: bool, missing_delta: int, missing_delta_label: str, current_status: str, status_changed: bool, transition_label: str):
    if not previous_available:
        return None
    observation_worsened = status_changed and current_status != "passed"
    observation_improved = status_changed and current_status == "passed"
    message = f"표준코드 미입력 {missing_delta_label}, priority 관측 {transition_label}"
    if missing_delta > 0 or observation_worsened:
        return {"severity": "warning", "title": "운영 주시 포인트", "message": message}
    if missing_delta < 0 or observation_improved:
        return {"severity": "success", "title": "개선 신호", "message": message}
    return {"severity": "info", "title": "변화 없음", "message": message}


durations_path = Path(sys.argv[1])
summary_out = Path(sys.argv[2])
json_out = Path(sys.argv[3])
note_out = Path(sys.argv[4])
artifact_dir = sys.argv[5]
generated_at_utc = sys.argv[6]
app_base_url = sys.argv[7]
summary_window_days = sys.argv[8]
trend_window_days_csv = sys.argv[9]
breakdown_limit = sys.argv[10]
collect_limit = sys.argv[11]
child_artifact_dir = Path(sys.argv[12])
attention_feed_output = Path(sys.argv[13])
standard_code_coverage_output = Path(sys.argv[14])
run_standard_code_coverage_audit = sys.argv[15] == "true"
recommendation_standard_code_observation_output = Path(sys.argv[16])
run_recommendation_standard_code_observation = sys.argv[17] == "true"
policy_data_triage_observation_output = Path(sys.argv[18])
run_policy_data_triage_observation = sys.argv[19] == "true"
chat_observability_output = Path(sys.argv[20])
run_chat_observability_audit = sys.argv[21] == "true"
root_dir = Path(sys.argv[22])

rows = list(csv.DictReader(durations_path.open(encoding="utf-8"), delimiter="\t"))
suite_duration_ms = sum(int(row["duration_ms"]) for row in rows)
suite_duration_seconds = suite_duration_ms / 1000

dashboard = read_key_values(child_artifact_dir / "admin-dashboard" / "stdout.txt")
collect = read_key_values(child_artifact_dir / "admin-collect-failures" / "stdout.txt")
breakdowns = read_key_values(child_artifact_dir / "admin-recommendation-breakdowns" / "stdout.txt")
standard_code_coverage = (
    read_key_values(standard_code_coverage_output)
    if run_standard_code_coverage_audit and standard_code_coverage_output.exists()
    else {}
)
attention_feed = read_key_values(attention_feed_output) if attention_feed_output.exists() else {}
recommendation_standard_code_observation = (
    read_key_values(recommendation_standard_code_observation_output)
    if run_recommendation_standard_code_observation and recommendation_standard_code_observation_output.exists()
    else {}
)
policy_data_triage_observation = (
    read_key_values(policy_data_triage_observation_output)
    if run_policy_data_triage_observation and policy_data_triage_observation_output.exists()
    else {}
)
chat_observability = (
    read_key_values(chat_observability_output)
    if run_chat_observability_audit and chat_observability_output.exists()
    else {}
)
current_priority_summary = read_key_values(root_dir / "tmp" / "current-priority-suite" / "latest-current-priority-summary.txt")
previous_current_priority_summary_path = find_previous_current_priority_summary(root_dir / "tmp" / "current-priority-suite")
previous_current_priority_summary = (
    read_key_values(previous_current_priority_summary_path)
    if previous_current_priority_summary_path is not None
    else {}
)

failed_jobs = int(collect.get("failed_jobs_in_window", "0"))
partial_jobs = int(collect.get("partial_success_jobs_in_window", "0"))
open_circuits = int(collect.get("open_collect_circuits", "0"))
users_missing_all_standard_codes = int(standard_code_coverage.get("users_missing_all_standard_codes", "0") or 0)
total_users = int(standard_code_coverage.get("total_users", "0") or 0)
safe_reconcile_candidate_rows = int(standard_code_coverage.get("safe_reconcile_candidate_rows", "0") or 0)
non_example_users_missing_all_standard_codes = int(
    standard_code_coverage.get("non_example_users_missing_all_standard_codes", "0") or 0
)
example_smoke_users_missing_all_standard_codes = int(
    standard_code_coverage.get("example_smoke_users_missing_all_standard_codes", "0") or 0
)
standard_code_coverage_status = "skipped"
if run_standard_code_coverage_audit:
    standard_code_coverage_status = "ok" if standard_code_coverage else "missing"
recommendation_standard_code_observation_status = "skipped"
if run_recommendation_standard_code_observation:
    recommendation_standard_code_observation_status = (
        "ok" if recommendation_standard_code_observation else "missing"
    )
policy_data_triage_observation_status = "skipped"
if run_policy_data_triage_observation:
    policy_data_triage_observation_status = "ok" if policy_data_triage_observation else "missing"
chat_observability_status = "skipped"
if run_chat_observability_audit:
    chat_observability_status = "ok" if chat_observability else "missing"
current_priority_missing_all_standard_codes_value = current_priority_summary.get(
    "active_baseline_user_profile_standard_code_users_missing_all_standard_codes", ""
)
previous_current_priority_missing_all_standard_codes_value = previous_current_priority_summary.get(
    "active_baseline_user_profile_standard_code_users_missing_all_standard_codes", ""
)
missing_all_standard_codes_comparison_available = (
    has_value(current_priority_missing_all_standard_codes_value)
    and has_value(previous_current_priority_missing_all_standard_codes_value)
)
current_priority_missing_all_standard_codes = int(
    current_priority_missing_all_standard_codes_value or 0
)
previous_current_priority_missing_all_standard_codes = int(
    previous_current_priority_missing_all_standard_codes_value or 0
)
current_priority_missing_all_standard_codes_delta = (
    current_priority_missing_all_standard_codes - previous_current_priority_missing_all_standard_codes
    if missing_all_standard_codes_comparison_available
    else 0
)
current_priority_missing_all_standard_codes_delta_label = build_missing_delta_label(
    missing_all_standard_codes_comparison_available,
    current_priority_missing_all_standard_codes_delta,
)
current_priority_recommendation_observation_status = current_priority_summary.get(
    "recommendation_standard_code_observation_status", ""
)
previous_current_priority_recommendation_observation_status = previous_current_priority_summary.get(
    "recommendation_standard_code_observation_status", ""
)
recommendation_observation_comparison_available = (
    has_value(current_priority_recommendation_observation_status)
    and has_value(previous_current_priority_recommendation_observation_status)
)
current_priority_recommendation_observation_status_changed = recommendation_observation_comparison_available and (
    current_priority_recommendation_observation_status
    != previous_current_priority_recommendation_observation_status
)
current_priority_previous_available = (
    previous_current_priority_summary_path is not None
    and (missing_all_standard_codes_comparison_available or recommendation_observation_comparison_available)
)
current_priority_recommendation_observation_transition_label = build_observation_transition_label(
    recommendation_observation_comparison_available,
    current_priority_recommendation_observation_status_changed,
    previous_current_priority_recommendation_observation_status,
    current_priority_recommendation_observation_status,
)
wrapper_promoted_alert = build_promoted_alert(
    current_priority_previous_available,
    current_priority_missing_all_standard_codes_delta,
    current_priority_missing_all_standard_codes_delta_label,
    current_priority_recommendation_observation_status,
    current_priority_recommendation_observation_status_changed,
    current_priority_recommendation_observation_transition_label,
)
attention_feed_response_path = Path(attention_feed.get("attention_response", "") or "")
attention_feed_payload = {}
if attention_feed_response_path.exists():
    try:
        attention_feed_payload = json.loads(attention_feed_response_path.read_text(encoding="utf-8")).get("data", {})
    except Exception:
        attention_feed_payload = {}

if failed_jobs == 0 and partial_jobs == 0 and open_circuits == 0:
    decision_class = "BASELINE_HEALTHY"
    operator_reading = (
        "Ops baseline is healthy. Keep dashboard, collect failures, and recommendation breakdown surfaces on the current contract."
    )
else:
    decision_class = "INVESTIGATE_COLLECT_DRIFT"
    operator_reading = (
        "Ops baseline shows collect failure drift. Read collect failures first, then confirm recommendation/admin surfaces are still aligned."
    )

next_action = "docs/core/ops-baseline-runbook.md"

lines = [
    "ops_observation_suite=passed",
    f"artifact_dir={artifact_dir}",
    f"generated_at_utc={generated_at_utc}",
    f"suite_duration_ms={suite_duration_ms}",
    f"suite_duration_seconds={suite_duration_seconds:.3f}",
    f"app_base_url={app_base_url}",
    f"summary_window_days={summary_window_days}",
    f"trend_window_days_csv={trend_window_days_csv}",
    f"collect_limit={collect_limit}",
    f"breakdown_limit={breakdown_limit}",
    f"collect_failed_jobs_in_window={failed_jobs}",
    f"collect_partial_success_jobs_in_window={partial_jobs}",
    f"open_collect_circuits={open_circuits}",
    f"collect_lane_count={collect.get('lane_count', '')}",
    f"recommendation_real_user_traffic_gate_in_window={dashboard.get('recommendation_real_user_traffic_gate_in_window', '')}",
    f"recommendation_review_gate={dashboard.get('recommendation_review_gate', '')}",
    f"recommendation_top1_leader_signal_summary={dashboard.get('recommendation_top1_leader_signal_summary', '')}",
    f"recommendation_recent_window_review_reading={dashboard.get('recommendation_recent_window_review_reading', '')}",
    f"breakdown_recent_clicked_sample_user_cohort={breakdowns.get('recent_clicked_sample_user_cohort', '')}",
    f"breakdown_real_user_traffic_gate_in_window={breakdowns.get('real_user_traffic_gate_in_window', '')}",
    f"run_user_profile_standard_code_coverage_audit={str(run_standard_code_coverage_audit).lower()}",
    f"user_profile_standard_code_coverage_status={standard_code_coverage_status}",
    f"attention_feed_status={'ok' if attention_feed else 'missing'}",
    f"run_recommendation_standard_code_observation={str(run_recommendation_standard_code_observation).lower()}",
    f"recommendation_standard_code_observation_status={recommendation_standard_code_observation_status}",
    f"run_policy_data_triage_observation={str(run_policy_data_triage_observation).lower()}",
    f"policy_data_triage_observation_status={policy_data_triage_observation_status}",
    f"policy_data_triage_decision_class={policy_data_triage_observation.get('decision_class', '')}",
    f"policy_data_triage_next_action={policy_data_triage_observation.get('next_action', '')}",
    f"run_chat_observability_audit={str(run_chat_observability_audit).lower()}",
    f"chat_observability_status={chat_observability_status}",
    f"chat_observability_decision_class={chat_observability.get('decision_class', '')}",
    f"chat_observability_primary_window_days={chat_observability.get('primary_window_days', '')}",
    f"chat_observability_window_7d_assistant_messages={chat_observability.get('window_7d_assistant_messages', '')}",
    f"chat_observability_window_7d_reference_rate_pct={chat_observability.get('window_7d_reference_rate_pct', '')}",
    f"chat_observability_window_7d_clarification_rate_pct={chat_observability.get('window_7d_clarification_rate_pct', '')}",
    f"chat_observability_window_7d_zero_result_rate_pct={chat_observability.get('window_7d_zero_result_rate_pct', '')}",
    f"wrapper_current_priority_previous_available={str(current_priority_previous_available).lower()}",
    f"wrapper_current_priority_users_missing_all_standard_codes_delta={current_priority_missing_all_standard_codes_delta}",
    f"wrapper_current_priority_users_missing_all_standard_codes_delta_label={current_priority_missing_all_standard_codes_delta_label}",
    f"wrapper_current_priority_recommendation_observation_status={current_priority_recommendation_observation_status}",
    f"wrapper_current_priority_recommendation_observation_status_changed={str(current_priority_recommendation_observation_status_changed).lower()}",
    f"wrapper_current_priority_recommendation_observation_transition_label={current_priority_recommendation_observation_transition_label}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    f"next_action={next_action}",
]
if attention_feed:
    lines.extend([
        f"attention_feed_stdout={attention_feed_output}",
        f"attention_feed_item_count={attention_feed.get('attention_item_count', '')}",
        f"attention_feed_warning_item_count={attention_feed.get('attention_warning_item_count', '')}",
        f"attention_feed_item_keys={attention_feed.get('attention_item_keys', '')}",
        f"attention_feed_item_titles={attention_feed.get('attention_item_titles', '')}",
        f"attention_feed_response={attention_feed.get('attention_response', '')}",
    ])
if wrapper_promoted_alert:
    lines.extend([
        f"wrapper_promoted_alert_severity={wrapper_promoted_alert['severity']}",
        f"wrapper_promoted_alert_title={wrapper_promoted_alert['title']}",
        f"wrapper_promoted_alert_message={wrapper_promoted_alert['message']}",
    ])
if run_standard_code_coverage_audit:
    lines.extend([
        f"user_profile_standard_code_coverage_stdout={standard_code_coverage_output}",
        f"user_profile_standard_code_total_users={total_users}",
        f"user_profile_standard_code_users_with_any_standard_code={standard_code_coverage.get('users_with_any_standard_code', '')}",
        f"user_profile_standard_code_users_missing_all_standard_codes={users_missing_all_standard_codes}",
        f"user_profile_standard_code_non_example_total_users={standard_code_coverage.get('non_example_total_users', '')}",
        f"user_profile_standard_code_non_example_users_with_any_standard_code={standard_code_coverage.get('non_example_users_with_any_standard_code', '')}",
        f"user_profile_standard_code_non_example_users_missing_all_standard_codes={non_example_users_missing_all_standard_codes}",
        f"user_profile_standard_code_non_example_safe_reconcile_candidate_rows={standard_code_coverage.get('non_example_safe_reconcile_candidate_rows', '')}",
        f"user_profile_standard_code_non_example_conflicting_value_gap_rows={standard_code_coverage.get('non_example_conflicting_value_gap_rows', '')}",
        f"user_profile_standard_code_real_user_total_users={standard_code_coverage.get('real_user_total_users', '')}",
        f"user_profile_standard_code_real_user_users_missing_all_standard_codes={standard_code_coverage.get('real_user_users_missing_all_standard_codes', '')}",
        f"user_profile_standard_code_example_smoke_total_users={standard_code_coverage.get('example_smoke_total_users', '')}",
        f"user_profile_standard_code_example_smoke_users_missing_all_standard_codes={example_smoke_users_missing_all_standard_codes}",
        f"user_profile_standard_code_bounded_local_total_users={standard_code_coverage.get('bounded_local_total_users', '')}",
        f"user_profile_standard_code_bounded_local_users_missing_all_standard_codes={standard_code_coverage.get('bounded_local_users_missing_all_standard_codes', '')}",
        f"user_profile_standard_code_house_tenure_filled={standard_code_coverage.get('users_house_tenure_code_filled', '')}",
        f"user_profile_standard_code_housing_type_filled={standard_code_coverage.get('users_housing_type_code_filled', '')}",
        f"user_profile_standard_code_basic_living_filled={standard_code_coverage.get('users_basic_living_recipient_type_code_filled', '')}",
        f"user_profile_standard_code_disability_grade_filled={standard_code_coverage.get('users_disability_grade_code_filled', '')}",
        f"user_profile_standard_code_safe_reconcile_candidate_rows={safe_reconcile_candidate_rows}",
        f"user_profile_standard_code_conflicting_value_gap_rows={standard_code_coverage.get('conflicting_value_gap_rows', '')}",
    ])
if run_recommendation_standard_code_observation:
    lines.extend([
        f"recommendation_standard_code_observation_stdout={recommendation_standard_code_observation_output}",
        f"recommendation_standard_code_precheck_status={recommendation_standard_code_observation.get('precheck_status', '')}",
        f"recommendation_standard_code_decision_class={recommendation_standard_code_observation.get('decision_class', '')}",
        f"recommendation_standard_code_housing_effect_status={recommendation_standard_code_observation.get('housing_standard_code_effect_status', '')}",
        f"recommendation_standard_code_housing_positive_rule_delta_rows={recommendation_standard_code_observation.get('housing_standard_code_effect_positive_rule_delta_rows', '')}",
        f"recommendation_standard_code_housing_positive_final_delta_rows={recommendation_standard_code_observation.get('housing_standard_code_effect_positive_final_delta_rows', '')}",
        f"recommendation_standard_code_housing_max_rule_delta={recommendation_standard_code_observation.get('housing_standard_code_effect_max_rule_delta', '')}",
        f"recommendation_standard_code_housing_max_final_delta={recommendation_standard_code_observation.get('housing_standard_code_effect_max_final_delta', '')}",
        f"recommendation_standard_code_welfare_matrix_status={recommendation_standard_code_observation.get('welfare_standard_code_matrix_status', '')}",
        f"recommendation_standard_code_welfare_scenario_count={recommendation_standard_code_observation.get('welfare_standard_code_matrix_scenario_count', '')}",
        f"recommendation_standard_code_welfare_positive_rule_scenarios={recommendation_standard_code_observation.get('welfare_standard_code_matrix_positive_rule_scenarios', '')}",
        f"recommendation_standard_code_welfare_positive_final_scenarios={recommendation_standard_code_observation.get('welfare_standard_code_matrix_positive_final_scenarios', '')}",
        f"recommendation_standard_code_welfare_max_rule_delta_scenario={recommendation_standard_code_observation.get('welfare_standard_code_matrix_max_rule_delta_scenario', '')}",
        f"recommendation_standard_code_welfare_max_rule_delta={recommendation_standard_code_observation.get('welfare_standard_code_matrix_max_rule_delta', '')}",
        f"recommendation_standard_code_welfare_max_final_delta_scenario={recommendation_standard_code_observation.get('welfare_standard_code_matrix_max_final_delta_scenario', '')}",
        f"recommendation_standard_code_welfare_max_final_delta={recommendation_standard_code_observation.get('welfare_standard_code_matrix_max_final_delta', '')}",
        f"recommendation_standard_code_adoption_status={recommendation_standard_code_observation.get('recommendation_standard_code_adoption_status', '')}",
        f"recommendation_standard_code_adoption_latest_batch_user_count={recommendation_standard_code_observation.get('recommendation_standard_code_adoption_latest_batch_user_count', '')}",
        f"recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code={recommendation_standard_code_observation.get('recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code', '')}",
        f"recommendation_standard_code_adoption_latest_batch_users_missing_all_standard_codes={recommendation_standard_code_observation.get('recommendation_standard_code_adoption_latest_batch_users_missing_all_standard_codes', '')}",
        f"recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code_share_pct={recommendation_standard_code_observation.get('recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code_share_pct', '')}",
        f"recommendation_standard_code_adoption_latest_batch_users_missing_all_standard_codes_share_pct={recommendation_standard_code_observation.get('recommendation_standard_code_adoption_latest_batch_users_missing_all_standard_codes_share_pct', '')}",
    ])

for row in rows:
    seconds = int(row["duration_ms"]) / 1000
    lines.append(f"{row['label']}_duration_ms={row['duration_ms']}")
    lines.append(f"{row['label']}_duration_seconds={seconds:.3f}")

summary_out.write_text("\n".join(lines) + "\n", encoding="utf-8")

payload = {
    "artifact_dir": artifact_dir,
    "generated_at_utc": generated_at_utc,
    "suite_duration_ms": suite_duration_ms,
    "suite_duration_seconds": suite_duration_seconds,
    "app_base_url": app_base_url,
    "summary_window_days": int(summary_window_days),
    "trend_window_days_csv": trend_window_days_csv,
    "collect_limit": int(collect_limit),
    "breakdown_limit": int(breakdown_limit),
    "collect_failed_jobs_in_window": failed_jobs,
    "collect_partial_success_jobs_in_window": partial_jobs,
    "open_collect_circuits": open_circuits,
    "collect_lane_count": int(collect.get("lane_count", "0") or 0),
    "recommendation_real_user_traffic_gate_in_window": dashboard.get("recommendation_real_user_traffic_gate_in_window", ""),
    "recommendation_review_gate": dashboard.get("recommendation_review_gate", ""),
    "recommendation_top1_leader_signal_summary": dashboard.get("recommendation_top1_leader_signal_summary", ""),
    "recommendation_recent_window_review_reading": dashboard.get("recommendation_recent_window_review_reading", ""),
    "breakdown_recent_clicked_sample_user_cohort": breakdowns.get("recent_clicked_sample_user_cohort", ""),
    "breakdown_real_user_traffic_gate_in_window": breakdowns.get("real_user_traffic_gate_in_window", ""),
    "run_user_profile_standard_code_coverage_audit": run_standard_code_coverage_audit,
    "user_profile_standard_code_coverage_status": standard_code_coverage_status,
    "attention_feed_status": "ok" if attention_feed else "missing",
    "run_recommendation_standard_code_observation": run_recommendation_standard_code_observation,
    "recommendation_standard_code_observation_status": recommendation_standard_code_observation_status,
    "run_policy_data_triage_observation": run_policy_data_triage_observation,
    "policy_data_triage_observation_status": policy_data_triage_observation_status,
    "wrapper_current_priority": {
        "previous_available": current_priority_previous_available,
        "users_missing_all_standard_codes_delta": current_priority_missing_all_standard_codes_delta,
        "users_missing_all_standard_codes_delta_label": current_priority_missing_all_standard_codes_delta_label,
        "recommendation_observation_status": current_priority_recommendation_observation_status,
        "recommendation_observation_status_changed": current_priority_recommendation_observation_status_changed,
        "recommendation_observation_transition_label": current_priority_recommendation_observation_transition_label,
        "previous_summary_path": str(previous_current_priority_summary_path) if previous_current_priority_summary_path else "",
    },
    "wrapper_promoted_alert": wrapper_promoted_alert,
    "decision_class": decision_class,
    "operator_reading": operator_reading,
    "next_action": next_action,
}
if attention_feed:
    payload["attention_feed"] = {
        "stdout": str(attention_feed_output),
        "response": attention_feed.get("attention_response", ""),
        "item_count": int(attention_feed.get("attention_item_count", "0") or 0),
        "warning_item_count": int(attention_feed.get("attention_warning_item_count", "0") or 0),
        "item_keys": [key for key in attention_feed.get("attention_item_keys", "").split(",") if key],
        "item_titles": [title.strip() for title in attention_feed.get("attention_item_titles", "").split("||") if title.strip()],
        "items": attention_feed_payload.get("items", []),
    }
if run_standard_code_coverage_audit:
    payload["user_profile_standard_code_coverage"] = {
        "stdout": str(standard_code_coverage_output),
        "total_users": total_users,
        "users_with_any_standard_code": int(standard_code_coverage.get("users_with_any_standard_code", "0") or 0),
        "users_missing_all_standard_codes": users_missing_all_standard_codes,
        "non_example_total_users": int(standard_code_coverage.get("non_example_total_users", "0") or 0),
        "non_example_users_with_any_standard_code": int(standard_code_coverage.get("non_example_users_with_any_standard_code", "0") or 0),
        "non_example_users_missing_all_standard_codes": non_example_users_missing_all_standard_codes,
        "non_example_safe_reconcile_candidate_rows": int(standard_code_coverage.get("non_example_safe_reconcile_candidate_rows", "0") or 0),
        "non_example_conflicting_value_gap_rows": int(standard_code_coverage.get("non_example_conflicting_value_gap_rows", "0") or 0),
        "real_user_total_users": int(standard_code_coverage.get("real_user_total_users", "0") or 0),
        "real_user_users_missing_all_standard_codes": int(standard_code_coverage.get("real_user_users_missing_all_standard_codes", "0") or 0),
        "example_smoke_total_users": int(standard_code_coverage.get("example_smoke_total_users", "0") or 0),
        "example_smoke_users_missing_all_standard_codes": example_smoke_users_missing_all_standard_codes,
        "bounded_local_total_users": int(standard_code_coverage.get("bounded_local_total_users", "0") or 0),
        "bounded_local_users_missing_all_standard_codes": int(standard_code_coverage.get("bounded_local_users_missing_all_standard_codes", "0") or 0),
        "users_house_tenure_code_filled": int(standard_code_coverage.get("users_house_tenure_code_filled", "0") or 0),
        "users_housing_type_code_filled": int(standard_code_coverage.get("users_housing_type_code_filled", "0") or 0),
        "users_basic_living_recipient_type_code_filled": int(standard_code_coverage.get("users_basic_living_recipient_type_code_filled", "0") or 0),
        "users_disability_grade_code_filled": int(standard_code_coverage.get("users_disability_grade_code_filled", "0") or 0),
        "safe_reconcile_candidate_rows": safe_reconcile_candidate_rows,
        "conflicting_value_gap_rows": int(standard_code_coverage.get("conflicting_value_gap_rows", "0") or 0),
    }
if run_recommendation_standard_code_observation:
    payload["recommendation_standard_code_observation"] = {
        "stdout": str(recommendation_standard_code_observation_output),
        "precheck_status": recommendation_standard_code_observation.get("precheck_status", ""),
        "decision_class": recommendation_standard_code_observation.get("decision_class", ""),
        "housing_standard_code_effect_status": recommendation_standard_code_observation.get("housing_standard_code_effect_status", ""),
        "housing_standard_code_effect_positive_rule_delta_rows": int(recommendation_standard_code_observation.get("housing_standard_code_effect_positive_rule_delta_rows", "0") or 0),
        "housing_standard_code_effect_positive_final_delta_rows": int(recommendation_standard_code_observation.get("housing_standard_code_effect_positive_final_delta_rows", "0") or 0),
        "housing_standard_code_effect_max_rule_delta": float(recommendation_standard_code_observation.get("housing_standard_code_effect_max_rule_delta", "0") or 0),
        "housing_standard_code_effect_max_final_delta": float(recommendation_standard_code_observation.get("housing_standard_code_effect_max_final_delta", "0") or 0),
        "welfare_standard_code_matrix_status": recommendation_standard_code_observation.get("welfare_standard_code_matrix_status", ""),
        "welfare_standard_code_matrix_scenario_count": int(recommendation_standard_code_observation.get("welfare_standard_code_matrix_scenario_count", "0") or 0),
        "welfare_standard_code_matrix_positive_rule_scenarios": int(recommendation_standard_code_observation.get("welfare_standard_code_matrix_positive_rule_scenarios", "0") or 0),
        "welfare_standard_code_matrix_positive_final_scenarios": int(recommendation_standard_code_observation.get("welfare_standard_code_matrix_positive_final_scenarios", "0") or 0),
        "welfare_standard_code_matrix_max_rule_delta_scenario": recommendation_standard_code_observation.get("welfare_standard_code_matrix_max_rule_delta_scenario", ""),
        "welfare_standard_code_matrix_max_rule_delta": float(recommendation_standard_code_observation.get("welfare_standard_code_matrix_max_rule_delta", "0") or 0),
        "welfare_standard_code_matrix_max_final_delta_scenario": recommendation_standard_code_observation.get("welfare_standard_code_matrix_max_final_delta_scenario", ""),
        "welfare_standard_code_matrix_max_final_delta": float(recommendation_standard_code_observation.get("welfare_standard_code_matrix_max_final_delta", "0") or 0),
        "recommendation_standard_code_adoption_status": recommendation_standard_code_observation.get("recommendation_standard_code_adoption_status", ""),
        "recommendation_standard_code_adoption_latest_batch_user_count": int(recommendation_standard_code_observation.get("recommendation_standard_code_adoption_latest_batch_user_count", "0") or 0),
        "recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code": int(recommendation_standard_code_observation.get("recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code", "0") or 0),
        "recommendation_standard_code_adoption_latest_batch_users_missing_all_standard_codes": int(recommendation_standard_code_observation.get("recommendation_standard_code_adoption_latest_batch_users_missing_all_standard_codes", "0") or 0),
        "recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code_share_pct": float(recommendation_standard_code_observation.get("recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code_share_pct", "0") or 0),
        "recommendation_standard_code_adoption_latest_batch_users_missing_all_standard_codes_share_pct": float(recommendation_standard_code_observation.get("recommendation_standard_code_adoption_latest_batch_users_missing_all_standard_codes_share_pct", "0") or 0),
    }
if run_policy_data_triage_observation:
    payload["policy_data_triage_observation"] = {
        "stdout": str(policy_data_triage_observation_output),
        "decision_class": policy_data_triage_observation.get("decision_class", ""),
        "next_action": policy_data_triage_observation.get("next_action", ""),
        "duplicate_groups_youth": int(policy_data_triage_observation.get("duplicate_groups_youth", "0") or 0),
        "duplicate_groups_bokjiro_local": int(policy_data_triage_observation.get("duplicate_groups_bokjiro_local", "0") or 0),
        "exact_duplicate_groups": int(policy_data_triage_observation.get("exact_duplicate_groups", "0") or 0),
        "mirror_variant_groups": int(policy_data_triage_observation.get("mirror_variant_groups", "0") or 0),
        "active_visible_youth_total": int(policy_data_triage_observation.get("active_visible_youth_total", "0") or 0),
        "benefit_support_count": int(policy_data_triage_observation.get("benefit_support_count", "0") or 0),
        "announcement_recruitment_count": int(policy_data_triage_observation.get("announcement_recruitment_count", "0") or 0),
    }
if run_chat_observability_audit:
    payload["chat_observability"] = {
        "stdout": str(chat_observability_output),
        "status": chat_observability_status,
        "decision_class": chat_observability.get("decision_class", ""),
        "operator_reading": chat_observability.get("operator_reading", ""),
        "primary_window_days": chat_observability.get("primary_window_days", ""),
        "window_7d_assistant_messages": int(chat_observability.get("window_7d_assistant_messages", "0") or 0),
        "window_7d_reference_rate_pct": float(chat_observability.get("window_7d_reference_rate_pct", "0") or 0),
        "window_7d_action_link_rate_pct": float(chat_observability.get("window_7d_action_link_rate_pct", "0") or 0),
        "window_7d_retrieval_snapshots": int(chat_observability.get("window_7d_retrieval_snapshots", "0") or 0),
        "window_7d_branch_suggestion_snapshots": int(chat_observability.get("window_7d_branch_suggestion_snapshots", "0") or 0),
        "window_7d_clarification_rate_pct": float(chat_observability.get("window_7d_clarification_rate_pct", "0") or 0),
        "window_7d_zero_result_rate_pct": float(chat_observability.get("window_7d_zero_result_rate_pct", "0") or 0),
    }
for row in rows:
    payload[f"{row['label']}_duration_ms"] = int(row["duration_ms"])
    payload[f"{row['label']}_duration_seconds"] = int(row["duration_ms"]) / 1000

json_out.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Ops Observation",
    "",
    f"- `decision_class`: `{decision_class}`",
    f"- `collect_failed_jobs_in_window`: `{failed_jobs}`",
    f"- `collect_partial_success_jobs_in_window`: `{partial_jobs}`",
    f"- `open_collect_circuits`: `{open_circuits}`",
    f"- `attention_feed_status`: `{'ok' if attention_feed else 'missing'}`",
    f"- `attention_feed_item_count`: `{attention_feed.get('attention_item_count', '')}`",
    f"- `attention_feed_item_titles`: `{attention_feed.get('attention_item_titles', '')}`",
    f"- `recommendation_review_gate`: `{dashboard.get('recommendation_review_gate', '')}`",
    f"- `recommendation_real_user_traffic_gate_in_window`: `{dashboard.get('recommendation_real_user_traffic_gate_in_window', '')}`",
    f"- `user_profile_standard_code_coverage_status`: `{standard_code_coverage_status}`",
    f"- `user_profile_standard_code_users_missing_all_standard_codes`: `{users_missing_all_standard_codes}`",
    f"- `user_profile_standard_code_non_example_users_missing_all_standard_codes`: `{non_example_users_missing_all_standard_codes}`",
    f"- `user_profile_standard_code_example_smoke_users_missing_all_standard_codes`: `{example_smoke_users_missing_all_standard_codes}`",
    f"- `recommendation_standard_code_observation_status`: `{recommendation_standard_code_observation_status}`",
    f"- `policy_data_triage_observation_status`: `{policy_data_triage_observation_status}`",
    f"- `policy_data_triage_decision_class`: `{policy_data_triage_observation.get('decision_class', '')}`",
    f"- `policy_data_triage_next_action`: `{policy_data_triage_observation.get('next_action', '')}`",
    f"- `chat_observability_status`: `{chat_observability_status}`",
    f"- `chat_observability_decision_class`: `{chat_observability.get('decision_class', '')}`",
    f"- `chat_observability_window_7d_assistant_messages`: `{chat_observability.get('window_7d_assistant_messages', '')}`",
    f"- `chat_observability_window_7d_reference_rate_pct`: `{chat_observability.get('window_7d_reference_rate_pct', '')}`",
    f"- `chat_observability_window_7d_clarification_rate_pct`: `{chat_observability.get('window_7d_clarification_rate_pct', '')}`",
    f"- `chat_observability_window_7d_zero_result_rate_pct`: `{chat_observability.get('window_7d_zero_result_rate_pct', '')}`",
    f"- `wrapper_promoted_alert`: `{wrapper_promoted_alert['severity'] if wrapper_promoted_alert else 'none'}`",
    f"- `wrapper_promoted_alert_message`: `{wrapper_promoted_alert['message'] if wrapper_promoted_alert else ''}`",
    f"- `recommendation_standard_code_housing_positive_rule_delta_rows`: `{recommendation_standard_code_observation.get('housing_standard_code_effect_positive_rule_delta_rows', '')}`",
    f"- `recommendation_standard_code_welfare_positive_rule_scenarios`: `{recommendation_standard_code_observation.get('welfare_standard_code_matrix_positive_rule_scenarios', '')}`",
    f"- `recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code_share_pct`: `{recommendation_standard_code_observation.get('recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code_share_pct', '')}`",
    f"- `recommendation_standard_code_adoption_latest_batch_users_missing_all_standard_codes`: `{recommendation_standard_code_observation.get('recommendation_standard_code_adoption_latest_batch_users_missing_all_standard_codes', '')}`",
    f"- `suite_duration_ms`: `{suite_duration_ms}`",
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
