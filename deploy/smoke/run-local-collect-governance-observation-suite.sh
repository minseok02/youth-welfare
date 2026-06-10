#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/collect-governance-observation}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS:-14}"
COLLECT_LIMIT="${COLLECT_LIMIT:-5}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

SMOKE_OUTPUT="${ARTIFACT_DIR}/admin-collect-failures.out"
SMOKE_ARTIFACT_DIR="${ARTIFACT_DIR}/collect-failures-artifact"
COLLECT_RESPONSE="${SMOKE_ARTIFACT_DIR}/collect-failures.json"
SUMMARY_OUT="${ARTIFACT_DIR}/collect-governance-observation-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/collect-governance-observation.json"
NOTE_OUT="${ARTIFACT_DIR}/collect-governance-observation-note.md"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-collect-governance-observation-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-collect-governance-observation.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-collect-governance-observation-note.md"

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
mkdir -p "${SMOKE_ARTIFACT_DIR}"

smoke_print_step "collect governance observation"
APP_BASE_URL="${APP_BASE_URL}" \
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS}" \
COLLECT_LIMIT="${COLLECT_LIMIT}" \
KEEP_ARTIFACTS=true \
ARTIFACT_DIR="${SMOKE_ARTIFACT_DIR}" \
bash "${ROOT_DIR}/deploy/smoke/run-local-admin-collect-failures-smoke.sh" | tee "${SMOKE_OUTPUT}"

if [[ ! -f "${COLLECT_RESPONSE}" ]]; then
  echo "expected collect-failures artifact missing: ${COLLECT_RESPONSE}" >&2
  exit 1
fi

python3 - "${COLLECT_RESPONSE}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" <<'PY'
import json
import sys
from pathlib import Path

response_path = Path(sys.argv[1])
summary_out = Path(sys.argv[2])
json_out = Path(sys.argv[3])
note_out = Path(sys.argv[4])
artifact_dir = sys.argv[5]

payload = json.loads(response_path.read_text(encoding="utf-8"))
data = payload["data"]
lanes = data["collectSourceLanes"]

scheduled = [lane for lane in lanes if lane["executionMode"] == "SCHEDULED"]
rotation = [lane for lane in lanes if lane["executionMode"] == "ROTATION"]
manual = [lane for lane in lanes if lane["executionMode"] == "MANUAL"]
open_circuits = [c for c in data["circuitStatuses"] if c.get("open") is True]
latest_failed = [lane["laneKey"] for lane in lanes if (lane.get("latestRun") or {}).get("status") == "FAILED"]
latest_partial = [lane["laneKey"] for lane in lanes if (lane.get("latestRun") or {}).get("status") == "PARTIAL_SUCCESS"]
missing_latest = [lane["laneKey"] for lane in lanes if lane.get("latestRun") is None]

if data["failedJobsInWindow"] > 0 or open_circuits:
    decision_class = "ATTENTION_REQUIRED"
    operator_reading = "Recent failed jobs or open collect circuits exist. Read collect failures triage before treating the lane inventory as healthy."
    next_action = "bash deploy/smoke/run-local-admin-collect-failures-smoke.sh"
elif data["partialSuccessJobsInWindow"] > 0:
    decision_class = "PARTIAL_SUCCESS_REVIEW"
    operator_reading = "No hard collect failure is open, but one or more lanes recently ended in partial success. Review the latest lane summaries before changing schedule or budget assumptions."
    next_action = "docs/collect/collect-ops.md"
else:
    decision_class = "BASELINE_HEALTHY"
    operator_reading = "Collect governance baseline is healthy. Keep scheduled, rotation, and manual lane inventory as-is and only reopen collect tuning if drift or repeated partials appear."
    next_action = "docs/collect/collect-current-state.md"

summary_lines = [
    "collect_governance_observation_suite=passed",
    f"artifact_dir={artifact_dir}",
    f"generated_at={data.get('generatedAt', '')}",
    f"summary_window_days={data.get('windowDays', '')}",
    f"failed_jobs_in_window={data.get('failedJobsInWindow', 0)}",
    f"partial_success_jobs_in_window={data.get('partialSuccessJobsInWindow', 0)}",
    f"open_collect_circuits={len(open_circuits)}",
    f"lane_count={len(lanes)}",
    f"scheduled_lane_count={len(scheduled)}",
    f"rotation_lane_count={len(rotation)}",
    f"manual_lane_count={len(manual)}",
    f"latest_failed_lane_keys={','.join(latest_failed) if latest_failed else '(none)'}",
    f"latest_partial_lane_keys={','.join(latest_partial) if latest_partial else '(none)'}",
    f"missing_latest_lane_keys={','.join(missing_latest) if missing_latest else '(none)'}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    f"next_action={next_action}",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": artifact_dir,
    "generated_at": data.get("generatedAt", ""),
    "summary_window_days": data.get("windowDays"),
    "failed_jobs_in_window": data.get("failedJobsInWindow", 0),
    "partial_success_jobs_in_window": data.get("partialSuccessJobsInWindow", 0),
    "open_collect_circuits": len(open_circuits),
    "lane_count": len(lanes),
    "scheduled_lane_count": len(scheduled),
    "rotation_lane_count": len(rotation),
    "manual_lane_count": len(manual),
    "latest_failed_lane_keys": latest_failed,
    "latest_partial_lane_keys": latest_partial,
    "missing_latest_lane_keys": missing_latest,
    "decision_class": decision_class,
    "operator_reading": operator_reading,
    "next_action": next_action,
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Collect Governance Observation",
    "",
    f"- `decision_class`: `{decision_class}`",
    f"- `failed_jobs_in_window`: `{data.get('failedJobsInWindow', 0)}`",
    f"- `partial_success_jobs_in_window`: `{data.get('partialSuccessJobsInWindow', 0)}`",
    f"- `open_collect_circuits`: `{len(open_circuits)}`",
    f"- `scheduled_lane_count`: `{len(scheduled)}`",
    f"- `rotation_lane_count`: `{len(rotation)}`",
    f"- `manual_lane_count`: `{len(manual)}`",
    f"- `latest_failed_lane_keys`: `{','.join(latest_failed) if latest_failed else '(none)'}`",
    f"- `latest_partial_lane_keys`: `{','.join(latest_partial) if latest_partial else '(none)'}`",
    f"- `missing_latest_lane_keys`: `{','.join(missing_latest) if missing_latest else '(none)'}`",
    f"- `next_action`: `{next_action}`",
    "",
    "## Operator Reading",
    "",
    operator_reading,
    "",
    "## Lane Snapshot",
    "",
    f"- scheduled snapshot lanes: `{len(scheduled)}`",
    f"- rotation/forced detail lanes: `{len(rotation)}`",
    f"- manual/on-demand lanes: `{len(manual)}`",
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
