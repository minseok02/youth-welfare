#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
OVERVIEW_OUTPUT="${ARTIFACT_DIR}/latest-overview.out"
SUMMARY_OUT="${ARTIFACT_DIR}/recommendation-reopen-precheck-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/recommendation-reopen-precheck.json"

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
smoke_resolve_admin_credentials "${ROOT_DIR}"
mkdir -p "${ARTIFACT_DIR}"

smoke_print_step "recommendation latest overview with readiness"
APP_BASE_URL="${APP_BASE_URL}" \
INCLUDE_REAL_USER_READINESS=true \
KEEP_ARTIFACTS=true \
ARTIFACT_DIR="${ARTIFACT_DIR}/latest-overview-artifact" \
bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh" | tee "${OVERVIEW_OUTPUT}"

OVERVIEW_JSON_PATH="$(python3 - "${OVERVIEW_OUTPUT}" <<'PY'
import sys

output_path = sys.argv[1]
prefix = "overview_json="

with open(output_path, "r", encoding="utf-8") as fp:
    for raw_line in fp:
        line = raw_line.strip()
        if line.startswith(prefix):
            print(line[len(prefix):])
            break
PY
)"

if [[ -z "${OVERVIEW_JSON_PATH}" || ! -f "${OVERVIEW_JSON_PATH}" ]]; then
  echo "failed to locate overview_json artifact from latest overview output" >&2
  exit 1
fi

python3 - "${OVERVIEW_JSON_PATH}" "${SUMMARY_OUT}" "${JSON_OUT}" "${ARTIFACT_DIR}" <<'PY'
import json
import sys
from pathlib import Path

overview_json_path = Path(sys.argv[1])
summary_out = Path(sys.argv[2])
json_out = Path(sys.argv[3])
artifact_dir = sys.argv[4]

payload = json.loads(overview_json_path.read_text(encoding="utf-8"))

latest_drift_class = payload.get("baseline", {}).get("latest_drift_class", "")
effective_operator_next_step = payload.get("effective_operator_next_step", "")
gate_action_class = payload.get("gate_action_class", "")
gate_policy_status = payload.get("gate_policy_status", "")
dashboard_gate = payload.get("real_user_readiness", {}).get("dashboard_real_user_gate", "")
cohort_gate = payload.get("real_user_readiness", {}).get("breakdown_real_user_cohort_gate", "")
review_gate = payload.get("real_user_readiness", {}).get("recommendation_review_gate", "")
leader_signal = payload.get("real_user_readiness", {}).get("recommendation_top1_leader_signal_summary", "")
promotion_status = payload.get("review_gate_context", {}).get("review_gate_policy_promotion_status", "")
promotion_readiness_status = payload.get("review_gate_context", {}).get("review_gate_policy_promotion_readiness_status", "")
promotion_execution_status = payload.get("review_gate_context", {}).get("review_gate_policy_promotion_execution_status", "")

if gate_action_class == "INVESTIGATE_BASELINE_DRIFT":
    reopen_precheck_status = "INVESTIGATE_BASELINE_DRIFT"
    reopen_precheck_reason = latest_drift_class or gate_action_class
    next_action = "bash deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh-drift-check.sh"
elif effective_operator_next_step == "RUN_REAL_USER_RECHECK_DECISION":
    reopen_precheck_status = "READY_FOR_REOPEN_DECISION"
    reopen_precheck_reason = "REAL_USER gates are ready and latest overview says to run the reopen decision"
    next_action = "docs/recommendation/recommendation-reopen-decision-runbook.md"
elif effective_operator_next_step == "WAIT_FOR_REAL_USER_LEADER_SIGNAL":
    reopen_precheck_status = "WAIT_FOR_REAL_USER_LEADER_SIGNAL"
    reopen_precheck_reason = leader_signal or review_gate or effective_operator_next_step
    next_action = "bash deploy/smoke/run-local-recommendation-review-gate-blocker-audit.sh"
elif effective_operator_next_step == "USE_RECENT_WINDOW_AS_SUPPLEMENTAL_REVIEW_CONTEXT":
    reopen_precheck_status = "SUPPLEMENTAL_REVIEW_ONLY"
    reopen_precheck_reason = promotion_status or effective_operator_next_step
    next_action = "docs/recommendation/recommendation-reopen-decision-runbook.md"
else:
    reopen_precheck_status = "KEEP_OBSERVING"
    reopen_precheck_reason = effective_operator_next_step or gate_policy_status or "baseline monitoring"
    next_action = "bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh"

summary_lines = [
    "recommendation_reopen_precheck=passed",
    f"artifact_dir={artifact_dir}",
    f"overview_json={overview_json_path}",
    f"generated_at_utc={payload.get('generated_at_utc', '')}",
    f"generated_at_kst={payload.get('generated_at_kst', '')}",
    f"latest_drift_class={latest_drift_class}",
    f"effective_operator_next_step={effective_operator_next_step}",
    f"gate_action_class={gate_action_class}",
    f"gate_policy_status={gate_policy_status}",
    f"real_user_dashboard_gate={dashboard_gate}",
    f"real_user_breakdown_cohort_gate={cohort_gate}",
    f"real_user_review_gate={review_gate}",
    f"real_user_top1_leader_signal_summary={leader_signal}",
    f"review_gate_policy_promotion_status={promotion_status}",
    f"review_gate_policy_promotion_readiness_status={promotion_readiness_status}",
    f"review_gate_policy_promotion_execution_status={promotion_execution_status}",
    f"reopen_precheck_status={reopen_precheck_status}",
    f"reopen_precheck_reason={reopen_precheck_reason}",
    f"next_action={next_action}",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": artifact_dir,
    "overview_json": str(overview_json_path),
    "generated_at_utc": payload.get("generated_at_utc", ""),
    "generated_at_kst": payload.get("generated_at_kst", ""),
    "latest_drift_class": latest_drift_class,
    "effective_operator_next_step": effective_operator_next_step,
    "gate_action_class": gate_action_class,
    "gate_policy_status": gate_policy_status,
    "real_user_dashboard_gate": dashboard_gate,
    "real_user_breakdown_cohort_gate": cohort_gate,
    "real_user_review_gate": review_gate,
    "real_user_top1_leader_signal_summary": leader_signal,
    "review_gate_policy_promotion_status": promotion_status,
    "review_gate_policy_promotion_readiness_status": promotion_readiness_status,
    "review_gate_policy_promotion_execution_status": promotion_execution_status,
    "reopen_precheck_status": reopen_precheck_status,
    "reopen_precheck_reason": reopen_precheck_reason,
    "next_action": next_action,
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

cat "${SUMMARY_OUT}"
