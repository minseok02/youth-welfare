#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/recommendation-observation}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"
PRECHECK_OUTPUT="${ARTIFACT_DIR}/recommendation-reopen-precheck.out"
SUMMARY_OUT="${ARTIFACT_DIR}/recommendation-observation-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/recommendation-observation.json"
LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-recommendation-observation-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-recommendation-observation.json"

cleanup() {
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
mkdir -p "${ARTIFACT_DIR}"

smoke_print_step "recommendation observation precheck"
APP_BASE_URL="${APP_BASE_URL}" \
KEEP_ARTIFACTS=true \
ARTIFACT_DIR="${ARTIFACT_DIR}/precheck-artifact" \
bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-reopen-precheck.sh" | tee "${PRECHECK_OUTPUT}"

python3 - "${PRECHECK_OUTPUT}" "${SUMMARY_OUT}" "${JSON_OUT}" "${ARTIFACT_DIR}" <<'PY'
import json
import sys
from pathlib import Path

output_path = Path(sys.argv[1])
summary_out = Path(sys.argv[2])
json_out = Path(sys.argv[3])
artifact_dir = sys.argv[4]

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

if precheck_status == "KEEP_OBSERVING":
    observation_blocker = "REAL_USER_TRAFFIC"
    recommended_cadence = "daily"
elif precheck_status == "WAIT_FOR_REAL_USER_LEADER_SIGNAL":
    observation_blocker = "REAL_USER_LEADER_SIGNAL"
    recommended_cadence = "daily"
elif precheck_status == "SUPPLEMENTAL_REVIEW_ONLY":
    observation_blocker = "EXPLICIT_POLICY_REVIEW"
    recommended_cadence = "event-driven"
elif precheck_status == "READY_FOR_REOPEN_DECISION":
    observation_blocker = "NONE"
    recommended_cadence = "immediate"
elif precheck_status == "INVESTIGATE_BASELINE_DRIFT":
    observation_blocker = "BASELINE_DRIFT"
    recommended_cadence = "immediate"
else:
    observation_blocker = "UNKNOWN"
    recommended_cadence = "manual"

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
    f"recommended_cadence={recommended_cadence}",
    f"next_action={next_action}",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_out.write_text(json.dumps({
    "artifact_dir": artifact_dir,
    "precheck_status": precheck_status,
    "precheck_reason": precheck_reason,
    "effective_operator_next_step": effective_step,
    "dashboard_real_user_gate": dashboard_gate,
    "breakdown_real_user_cohort_gate": cohort_gate,
    "recommendation_review_gate": review_gate,
    "observation_blocker": observation_blocker,
    "recommended_cadence": recommended_cadence,
    "next_action": next_action,
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

smoke_publish_dir_snapshot "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
smoke_publish_file "${SUMMARY_OUT}" "${LATEST_SUMMARY_LINK}"
smoke_publish_file "${JSON_OUT}" "${LATEST_JSON_LINK}"

cat "${SUMMARY_OUT}"
echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
