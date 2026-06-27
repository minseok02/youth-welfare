#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/recommendation-observation}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_HOUSING_STANDARD_CODE_EFFECT_AUDIT="${RUN_HOUSING_STANDARD_CODE_EFFECT_AUDIT:-auto}"
RUN_WELFARE_STANDARD_CODE_MATRIX_AUDIT="${RUN_WELFARE_STANDARD_CODE_MATRIX_AUDIT:-auto}"
RUN_RECOMMENDATION_STANDARD_CODE_ADOPTION_AUDIT="${RUN_RECOMMENDATION_STANDARD_CODE_ADOPTION_AUDIT:-true}"
STANDARD_CODE_EFFECT_MIN_POLICY_ROWS="${STANDARD_CODE_EFFECT_MIN_POLICY_ROWS:-10}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"
PRECHECK_OUTPUT="${ARTIFACT_DIR}/recommendation-reopen-precheck.out"
HOUSING_EFFECT_OUTPUT="${ARTIFACT_DIR}/housing-standard-code-effect.out"
WELFARE_MATRIX_OUTPUT="${ARTIFACT_DIR}/welfare-standard-code-matrix.out"
STANDARD_CODE_ADOPTION_OUTPUT="${ARTIFACT_DIR}/recommendation-standard-code-adoption.out"
HOUSING_EFFECT_ARTIFACT_DIR="${ARTIFACT_DIR}/housing-effect-artifact"
WELFARE_MATRIX_ARTIFACT_DIR="${ARTIFACT_DIR}/welfare-matrix-artifact"
SUMMARY_OUT="${ARTIFACT_DIR}/recommendation-observation-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/recommendation-observation.json"
NOTE_OUT="${ARTIFACT_DIR}/recommendation-observation-note.md"
LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-recommendation-observation-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-recommendation-observation.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-recommendation-observation-note.md"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

normalize_bool_or_auto() {
  local value="${1,,}"
  case "${value}" in
    true|false|auto) printf '%s' "${value}" ;;
    *)
      echo "unsupported boolean/auto value: ${1}" >&2
      exit 1
      ;;
  esac
}

standard_code_effect_policy_rows() {
  smoke_db_query "select count(*) from welfare_services;" 2>/dev/null | head -n 1
}

resolve_auto_audit_flag() {
  local label="$1"
  local value="$2"
  local policy_rows="$3"

  if [[ "${value}" != "auto" ]]; then
    printf '%s' "${value}"
    return 0
  fi

  if [[ "${policy_rows}" =~ ^[0-9]+$ && "${policy_rows}" -ge "${STANDARD_CODE_EFFECT_MIN_POLICY_ROWS}" ]]; then
    printf 'true'
    return 0
  fi

  echo "SKIP ${label}: welfare_services rows ${policy_rows:-unknown} < ${STANDARD_CODE_EFFECT_MIN_POLICY_ROWS}" >&2
  printf 'false'
}

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
RUN_HOUSING_STANDARD_CODE_EFFECT_AUDIT="$(normalize_bool_or_auto "${RUN_HOUSING_STANDARD_CODE_EFFECT_AUDIT}")"
RUN_WELFARE_STANDARD_CODE_MATRIX_AUDIT="$(normalize_bool_or_auto "${RUN_WELFARE_STANDARD_CODE_MATRIX_AUDIT}")"
RUN_RECOMMENDATION_STANDARD_CODE_ADOPTION_AUDIT="$(smoke_normalize_bool "${RUN_RECOMMENDATION_STANDARD_CODE_ADOPTION_AUDIT}")"

smoke_require_command bash
smoke_require_command python3
smoke_require_command tee
mkdir -p "${ARTIFACT_DIR}"

POLICY_ROW_COUNT="$(standard_code_effect_policy_rows || true)"
RUN_HOUSING_STANDARD_CODE_EFFECT_AUDIT="$(resolve_auto_audit_flag "housing standard code effect audit" "${RUN_HOUSING_STANDARD_CODE_EFFECT_AUDIT}" "${POLICY_ROW_COUNT}")"
RUN_WELFARE_STANDARD_CODE_MATRIX_AUDIT="$(resolve_auto_audit_flag "welfare standard code matrix audit" "${RUN_WELFARE_STANDARD_CODE_MATRIX_AUDIT}" "${POLICY_ROW_COUNT}")"

smoke_print_step "recommendation observation precheck"
APP_BASE_URL="${APP_BASE_URL}" \
KEEP_ARTIFACTS=true \
ARTIFACT_DIR="${ARTIFACT_DIR}/precheck-artifact" \
bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-reopen-precheck.sh" | tee "${PRECHECK_OUTPUT}"

if [[ "${RUN_HOUSING_STANDARD_CODE_EFFECT_AUDIT}" == "true" ]]; then
  smoke_print_step "housing standard code effect audit"
  APP_BASE_URL="${APP_BASE_URL}" \
  KEEP_ARTIFACTS=true \
  ARTIFACT_DIR="${HOUSING_EFFECT_ARTIFACT_DIR}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-housing-standard-code-effect-audit.sh" | tee "${HOUSING_EFFECT_OUTPUT}"
fi

if [[ "${RUN_WELFARE_STANDARD_CODE_MATRIX_AUDIT}" == "true" ]]; then
  smoke_print_step "welfare standard code matrix audit"
  APP_BASE_URL="${APP_BASE_URL}" \
  KEEP_ARTIFACTS=true \
  ARTIFACT_DIR="${WELFARE_MATRIX_ARTIFACT_DIR}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-welfare-standard-code-matrix-audit.sh" | tee "${WELFARE_MATRIX_OUTPUT}"
fi

if [[ "${RUN_RECOMMENDATION_STANDARD_CODE_ADOPTION_AUDIT}" == "true" ]]; then
  smoke_print_step "recommendation standard code adoption audit"
  bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-standard-code-adoption-audit.sh" | tee "${STANDARD_CODE_ADOPTION_OUTPUT}"
fi

python3 - "${PRECHECK_OUTPUT}" "${HOUSING_EFFECT_OUTPUT}" "${WELFARE_MATRIX_OUTPUT}" "${STANDARD_CODE_ADOPTION_OUTPUT}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" "${RUN_HOUSING_STANDARD_CODE_EFFECT_AUDIT}" "${RUN_WELFARE_STANDARD_CODE_MATRIX_AUDIT}" "${RUN_RECOMMENDATION_STANDARD_CODE_ADOPTION_AUDIT}" "${HOUSING_EFFECT_ARTIFACT_DIR}" "${WELFARE_MATRIX_ARTIFACT_DIR}" <<'PY'
import json
import sys
from pathlib import Path

output_path = Path(sys.argv[1])
housing_effect_path = Path(sys.argv[2])
welfare_matrix_path = Path(sys.argv[3])
standard_code_adoption_path = Path(sys.argv[4])
summary_out = Path(sys.argv[5])
json_out = Path(sys.argv[6])
note_out = Path(sys.argv[7])
artifact_dir = sys.argv[8]
run_housing_effect = sys.argv[9] == "true"
run_welfare_matrix = sys.argv[10] == "true"
run_standard_code_adoption = sys.argv[11] == "true"
housing_effect_artifact_dir = sys.argv[12]
welfare_matrix_artifact_dir = sys.argv[13]

values = {}
for raw_line in output_path.read_text(encoding="utf-8").splitlines():
    if "=" not in raw_line:
        continue
    key, value = raw_line.split("=", 1)
    values[key.strip()] = value.strip()

precheck_status = values.get("reopen_precheck_status", "")
precheck_reason = values.get("reopen_precheck_reason", "")
next_action = values.get("next_action", "")
effective_step = values.get("effective_operator_next_step", "")
dashboard_gate = values.get("real_user_dashboard_gate", "")
cohort_gate = values.get("real_user_breakdown_cohort_gate", "")
review_gate = values.get("real_user_review_gate", "")

housing_values = {}
if run_housing_effect and housing_effect_path.exists():
    for raw_line in housing_effect_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line.startswith("METRIC "):
            continue
        metric_payload = line[len("METRIC "):]
        if "=" not in metric_payload:
            continue
        key, value = metric_payload.split("=", 1)
        housing_values[key.strip()] = value.strip()

housing_effect_status = "skipped"
if run_housing_effect:
    housing_effect_status = "ok" if housing_values else "missing"

positive_rule_delta_rows = housing_values.get("positive_rule_delta_rows", "")
positive_final_delta_rows = housing_values.get("positive_final_delta_rows", "")
max_rule_delta = housing_values.get("max_rule_delta", "")
max_final_delta = housing_values.get("max_final_delta", "")
top_positive_rule_delta_rows = housing_values.get("top_positive_rule_delta_rows", "")

welfare_matrix_values = {}
if run_welfare_matrix and welfare_matrix_path.exists():
    for raw_line in welfare_matrix_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        welfare_matrix_values[key.strip()] = value.strip()

welfare_matrix_status = "skipped"
if run_welfare_matrix:
    welfare_matrix_status = "ok" if welfare_matrix_values else "missing"

standard_code_adoption_values = {}
if run_standard_code_adoption and standard_code_adoption_path.exists():
    for raw_line in standard_code_adoption_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line.startswith("METRIC "):
            continue
        payload = line[len("METRIC "):]
        if "=" not in payload:
            continue
        key, value = payload.split("=", 1)
        standard_code_adoption_values[key.strip()] = value.strip()

standard_code_adoption_status = "skipped"
if run_standard_code_adoption:
    standard_code_adoption_status = "ok" if standard_code_adoption_values else "missing"

if precheck_status == "KEEP_OBSERVING":
    observation_blocker = "REAL_USER_TRAFFIC"
    recommended_cadence = "daily"
    decision_class = "OBSERVE_REAL_USER_TRAFFIC"
    reopen_allowed = "false"
    operator_reading = "Recommendation code stays closed. Keep daily observation until real-user traffic and leader signal grow."
elif precheck_status == "WAIT_FOR_REAL_USER_LEADER_SIGNAL":
    observation_blocker = "REAL_USER_LEADER_SIGNAL"
    recommended_cadence = "daily"
    decision_class = "OBSERVE_LEADER_SIGNAL"
    reopen_allowed = "false"
    operator_reading = "Read latest overview and blocker audit, but do not reopen tuning yet."
elif precheck_status == "SUPPLEMENTAL_REVIEW_ONLY":
    observation_blocker = "EXPLICIT_POLICY_REVIEW"
    recommended_cadence = "event-driven"
    decision_class = "SUPPLEMENTAL_POLICY_REVIEW"
    reopen_allowed = "false"
    operator_reading = "Use recent-window review as supplemental context. Keep the primary baseline unless an explicit policy review approves a change."
elif precheck_status == "READY_FOR_REOPEN_DECISION":
    observation_blocker = "NONE"
    recommended_cadence = "immediate"
    decision_class = "REOPEN_DECISION_READY"
    reopen_allowed = "true"
    operator_reading = "Recommendation reopen decision can be reviewed now."
elif precheck_status == "INVESTIGATE_BASELINE_DRIFT":
    observation_blocker = "BASELINE_DRIFT"
    recommended_cadence = "immediate"
    decision_class = "INVESTIGATE_BASELINE_DRIFT"
    reopen_allowed = "false"
    operator_reading = "Investigate baseline drift before any recommendation reopen or tuning decision."
else:
    observation_blocker = "UNKNOWN"
    recommended_cadence = "manual"
    decision_class = "MANUAL_INTERPRETATION"
    reopen_allowed = "false"
    operator_reading = "Manual interpretation is required because the observation state is outside the known ladder."

summary_lines = [
    "recommendation_observation_suite=passed",
    f"artifact_dir={artifact_dir}",
    f"precheck_status={precheck_status}",
    f"precheck_reason={precheck_reason}",
    f"effective_operator_next_step={effective_step}",
    f"dashboard_real_user_gate={dashboard_gate}",
    f"breakdown_real_user_cohort_gate={cohort_gate}",
    f"recommendation_review_gate={review_gate}",
    f"observation_blocker={observation_blocker}",
    f"decision_class={decision_class}",
    f"reopen_allowed={reopen_allowed}",
    f"recommended_cadence={recommended_cadence}",
    f"operator_reading={operator_reading}",
    f"next_action={next_action}",
    f"run_housing_standard_code_effect_audit={str(run_housing_effect).lower()}",
    f"housing_standard_code_effect_status={housing_effect_status}",
    f"run_welfare_standard_code_matrix_audit={str(run_welfare_matrix).lower()}",
    f"welfare_standard_code_matrix_status={welfare_matrix_status}",
    f"run_recommendation_standard_code_adoption_audit={str(run_standard_code_adoption).lower()}",
    f"recommendation_standard_code_adoption_status={standard_code_adoption_status}",
]
if run_housing_effect:
    summary_lines.extend([
        f"housing_standard_code_effect_stdout={artifact_dir}/housing-standard-code-effect.out",
        f"housing_standard_code_effect_artifact_dir={housing_effect_artifact_dir}",
        f"housing_standard_code_effect_positive_rule_delta_rows={positive_rule_delta_rows}",
        f"housing_standard_code_effect_positive_final_delta_rows={positive_final_delta_rows}",
        f"housing_standard_code_effect_max_rule_delta={max_rule_delta}",
        f"housing_standard_code_effect_max_final_delta={max_final_delta}",
        f"housing_standard_code_effect_top_positive_rule_delta_rows={top_positive_rule_delta_rows}",
    ])
if run_welfare_matrix:
    summary_lines.extend([
        f"welfare_standard_code_matrix_stdout={artifact_dir}/welfare-standard-code-matrix.out",
        f"welfare_standard_code_matrix_artifact_dir={welfare_matrix_artifact_dir}",
        f"welfare_standard_code_matrix_scenario_count={welfare_matrix_values.get('scenario_count', '')}",
        f"welfare_standard_code_matrix_positive_rule_scenarios={welfare_matrix_values.get('positive_rule_scenarios', '')}",
        f"welfare_standard_code_matrix_positive_final_scenarios={welfare_matrix_values.get('positive_final_scenarios', '')}",
        f"welfare_standard_code_matrix_max_rule_delta_scenario={welfare_matrix_values.get('max_rule_delta_scenario', '')}",
        f"welfare_standard_code_matrix_max_rule_delta={welfare_matrix_values.get('max_rule_delta', '')}",
        f"welfare_standard_code_matrix_max_final_delta_scenario={welfare_matrix_values.get('max_final_delta_scenario', '')}",
        f"welfare_standard_code_matrix_max_final_delta={welfare_matrix_values.get('max_final_delta', '')}",
        f"welfare_standard_code_matrix_scenario_rule_delta_snapshot={welfare_matrix_values.get('scenario_rule_delta_snapshot', '')}",
    ])
if run_standard_code_adoption:
    summary_lines.extend([
        f"recommendation_standard_code_adoption_stdout={artifact_dir}/recommendation-standard-code-adoption.out",
        f"recommendation_standard_code_adoption_latest_batch_user_count={standard_code_adoption_values.get('latest_batch_user_count', '')}",
        f"recommendation_standard_code_adoption_latest_batch_row_count={standard_code_adoption_values.get('latest_batch_row_count', '')}",
        f"recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code={standard_code_adoption_values.get('latest_batch_users_with_any_standard_code', '')}",
        f"recommendation_standard_code_adoption_latest_batch_users_with_all_standard_codes={standard_code_adoption_values.get('latest_batch_users_with_all_standard_codes', '')}",
        f"recommendation_standard_code_adoption_latest_batch_users_missing_all_standard_codes={standard_code_adoption_values.get('latest_batch_users_missing_all_standard_codes', '')}",
        f"recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code_share_pct={standard_code_adoption_values.get('latest_batch_users_with_any_standard_code_share_pct', '')}",
        f"recommendation_standard_code_adoption_latest_batch_users_missing_all_standard_codes_share_pct={standard_code_adoption_values.get('latest_batch_users_missing_all_standard_codes_share_pct', '')}",
        f"recommendation_standard_code_adoption_latest_batch_avg_final_score_with_any_standard_code={standard_code_adoption_values.get('latest_batch_avg_final_score_with_any_standard_code', '')}",
        f"recommendation_standard_code_adoption_latest_batch_avg_final_score_missing_all_standard_codes={standard_code_adoption_values.get('latest_batch_avg_final_score_missing_all_standard_codes', '')}",
    ])
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": artifact_dir,
    "precheck_status": precheck_status,
    "precheck_reason": precheck_reason,
    "effective_operator_next_step": effective_step,
    "dashboard_real_user_gate": dashboard_gate,
    "breakdown_real_user_cohort_gate": cohort_gate,
    "recommendation_review_gate": review_gate,
    "observation_blocker": observation_blocker,
    "decision_class": decision_class,
    "reopen_allowed": reopen_allowed == "true",
    "recommended_cadence": recommended_cadence,
    "operator_reading": operator_reading,
    "next_action": next_action,
    "run_housing_standard_code_effect_audit": run_housing_effect,
    "housing_standard_code_effect_status": housing_effect_status,
    "run_welfare_standard_code_matrix_audit": run_welfare_matrix,
    "welfare_standard_code_matrix_status": welfare_matrix_status,
    "run_recommendation_standard_code_adoption_audit": run_standard_code_adoption,
    "recommendation_standard_code_adoption_status": standard_code_adoption_status,
}
if run_housing_effect:
    json_payload["housing_standard_code_effect"] = {
        "stdout": f"{artifact_dir}/housing-standard-code-effect.out",
        "artifact_dir": housing_effect_artifact_dir,
        "positive_rule_delta_rows": int(positive_rule_delta_rows or "0"),
        "positive_final_delta_rows": int(positive_final_delta_rows or "0"),
        "max_rule_delta": float(max_rule_delta or "0"),
        "max_final_delta": float(max_final_delta or "0"),
        "top_positive_rule_delta_rows": top_positive_rule_delta_rows,
    }
if run_welfare_matrix:
    json_payload["welfare_standard_code_matrix"] = {
        "stdout": f"{artifact_dir}/welfare-standard-code-matrix.out",
        "artifact_dir": welfare_matrix_artifact_dir,
        "scenario_count": int(welfare_matrix_values.get("scenario_count", "0") or "0"),
        "positive_rule_scenarios": int(welfare_matrix_values.get("positive_rule_scenarios", "0") or "0"),
        "positive_final_scenarios": int(welfare_matrix_values.get("positive_final_scenarios", "0") or "0"),
        "max_rule_delta_scenario": welfare_matrix_values.get("max_rule_delta_scenario", ""),
        "max_rule_delta": float(welfare_matrix_values.get("max_rule_delta", "0") or "0"),
        "max_final_delta_scenario": welfare_matrix_values.get("max_final_delta_scenario", ""),
        "max_final_delta": float(welfare_matrix_values.get("max_final_delta", "0") or "0"),
        "scenario_rule_delta_snapshot": welfare_matrix_values.get("scenario_rule_delta_snapshot", ""),
    }
if run_standard_code_adoption:
    json_payload["recommendation_standard_code_adoption"] = {
        "stdout": f"{artifact_dir}/recommendation-standard-code-adoption.out",
        "latest_batch_user_count": int(standard_code_adoption_values.get("latest_batch_user_count", "0") or "0"),
        "latest_batch_row_count": int(standard_code_adoption_values.get("latest_batch_row_count", "0") or "0"),
        "latest_batch_users_with_any_standard_code": int(standard_code_adoption_values.get("latest_batch_users_with_any_standard_code", "0") or "0"),
        "latest_batch_users_with_all_standard_codes": int(standard_code_adoption_values.get("latest_batch_users_with_all_standard_codes", "0") or "0"),
        "latest_batch_users_missing_all_standard_codes": int(standard_code_adoption_values.get("latest_batch_users_missing_all_standard_codes", "0") or "0"),
        "latest_batch_users_with_any_standard_code_share_pct": float(standard_code_adoption_values.get("latest_batch_users_with_any_standard_code_share_pct", "0") or "0"),
        "latest_batch_users_missing_all_standard_codes_share_pct": float(standard_code_adoption_values.get("latest_batch_users_missing_all_standard_codes_share_pct", "0") or "0"),
        "latest_batch_avg_final_score_with_any_standard_code": float(standard_code_adoption_values.get("latest_batch_avg_final_score_with_any_standard_code", "0") or "0"),
        "latest_batch_avg_final_score_missing_all_standard_codes": float(standard_code_adoption_values.get("latest_batch_avg_final_score_missing_all_standard_codes", "0") or "0"),
    }
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Recommendation Observation",
    "",
    f"- `precheck_status`: `{precheck_status}`",
    f"- `precheck_reason`: `{precheck_reason}`",
    f"- `decision_class`: `{decision_class}`",
    f"- `reopen_allowed`: `{reopen_allowed}`",
    f"- `observation_blocker`: `{observation_blocker}`",
    f"- `recommended_cadence`: `{recommended_cadence}`",
    f"- `next_action`: `{next_action}`",
    "",
    "## Operator Reading",
    "",
    operator_reading,
    "",
    "## Gate Snapshot",
    "",
    f"- `dashboard_real_user_gate`: `{dashboard_gate}`",
    f"- `breakdown_real_user_cohort_gate`: `{cohort_gate}`",
    f"- `recommendation_review_gate`: `{review_gate}`",
]
if run_housing_effect:
    note_lines.extend([
        "",
        "## Housing Standard Code Effect",
        "",
        f"- `status`: `{housing_effect_status}`",
        f"- `artifact_dir`: `{housing_effect_artifact_dir}`",
        f"- `positive_rule_delta_rows`: `{positive_rule_delta_rows}`",
        f"- `positive_final_delta_rows`: `{positive_final_delta_rows}`",
        f"- `max_rule_delta`: `{max_rule_delta}`",
        f"- `max_final_delta`: `{max_final_delta}`",
        f"- `top_positive_rule_delta_rows`: `{top_positive_rule_delta_rows}`",
    ])
if run_welfare_matrix:
    note_lines.extend([
        "",
        "## Welfare Standard Code Matrix",
        "",
        f"- `status`: `{welfare_matrix_status}`",
        f"- `artifact_dir`: `{welfare_matrix_artifact_dir}`",
        f"- `scenario_count`: `{welfare_matrix_values.get('scenario_count', '')}`",
        f"- `positive_rule_scenarios`: `{welfare_matrix_values.get('positive_rule_scenarios', '')}`",
        f"- `positive_final_scenarios`: `{welfare_matrix_values.get('positive_final_scenarios', '')}`",
        f"- `max_rule_delta_scenario`: `{welfare_matrix_values.get('max_rule_delta_scenario', '')}`",
        f"- `max_rule_delta`: `{welfare_matrix_values.get('max_rule_delta', '')}`",
        f"- `max_final_delta_scenario`: `{welfare_matrix_values.get('max_final_delta_scenario', '')}`",
        f"- `max_final_delta`: `{welfare_matrix_values.get('max_final_delta', '')}`",
        f"- `scenario_rule_delta_snapshot`: `{welfare_matrix_values.get('scenario_rule_delta_snapshot', '')}`",
    ])
if run_standard_code_adoption:
    note_lines.extend([
        "",
        "## Recommendation Standard Code Adoption",
        "",
        f"- `status`: `{standard_code_adoption_status}`",
        f"- `latest_batch_user_count`: `{standard_code_adoption_values.get('latest_batch_user_count', '')}`",
        f"- `latest_batch_users_with_any_standard_code`: `{standard_code_adoption_values.get('latest_batch_users_with_any_standard_code', '')}`",
        f"- `latest_batch_users_missing_all_standard_codes`: `{standard_code_adoption_values.get('latest_batch_users_missing_all_standard_codes', '')}`",
        f"- `latest_batch_users_with_any_standard_code_share_pct`: `{standard_code_adoption_values.get('latest_batch_users_with_any_standard_code_share_pct', '')}`",
        f"- `latest_batch_users_missing_all_standard_codes_share_pct`: `{standard_code_adoption_values.get('latest_batch_users_missing_all_standard_codes_share_pct', '')}`",
        f"- `latest_batch_avg_final_score_with_any_standard_code`: `{standard_code_adoption_values.get('latest_batch_avg_final_score_with_any_standard_code', '')}`",
        f"- `latest_batch_avg_final_score_missing_all_standard_codes`: `{standard_code_adoption_values.get('latest_batch_avg_final_score_missing_all_standard_codes', '')}`",
    ])
note_out.write_text("\n".join(note_lines) + "\n", encoding="utf-8")
PY
smoke_sanitize_artifacts "${ARTIFACT_DIR}"

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
