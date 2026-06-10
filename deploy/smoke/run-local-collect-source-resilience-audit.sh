#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/collect-source-resilience-audit}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS:-14}"
COLLECT_LIMIT="${COLLECT_LIMIT:-10}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

SMOKE_OUTPUT="${ARTIFACT_DIR}/admin-collect-failures.out"
SMOKE_ARTIFACT_DIR="${ARTIFACT_DIR}/collect-failures-artifact"
COLLECT_RESPONSE="${SMOKE_ARTIFACT_DIR}/collect-failures.json"
SUMMARY_OUT="${ARTIFACT_DIR}/collect-source-resilience-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/collect-source-resilience.json"
NOTE_OUT="${ARTIFACT_DIR}/collect-source-resilience-note.md"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-collect-source-resilience-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-collect-source-resilience.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-collect-source-resilience-note.md"

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

smoke_print_step "collect source resilience audit"
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

EXPECTED_RETRY_LANES = {
    "YOUTH",
    "BOKJIRO_CENTRAL",
    "BOKJIRO_LOCAL",
    "BOKJIRO_DETAIL",
    "GOV24",
    "GOV24_DETAIL",
    "GOV24_SUPPORT_CONDITIONS",
    "BOKJIRO_DETAIL_GAP_FILL",
    "BOKJIRO_DETAIL_REFRESH",
}
EXPECTED_RATE_LIMIT_LANES = {
    "BOKJIRO_CENTRAL",
    "BOKJIRO_LOCAL",
    "BOKJIRO_DETAIL",
    "BOKJIRO_DETAIL_GAP_FILL",
    "BOKJIRO_DETAIL_REFRESH",
}
FAIL_ON_EMPTY_SNAPSHOT_LANES = {
    "YOUTH",
    "BOKJIRO_CENTRAL",
    "BOKJIRO_LOCAL",
    "GOV24",
}

def entry_map(lane):
    return {entry["label"]: entry["value"] for entry in lane.get("configEntries") or []}

lane_keys = [lane["laneKey"] for lane in lanes]
retry_lanes = []
rate_limit_lanes = []
lock_guard_lanes = []
missing_retry_lanes = []
missing_rate_limit_lanes = []
missing_lock_guard_lanes = []
manual_lanes = []
scheduled_lanes = []
rotation_lanes = []

for lane in lanes:
    key = lane["laneKey"]
    entries = entry_map(lane)
    labels = set(entries.keys())
    if lane["executionMode"] == "MANUAL":
        manual_lanes.append(key)
    elif lane["executionMode"] == "ROTATION":
        rotation_lanes.append(key)
    else:
        scheduled_lanes.append(key)
    if "재시도" in labels:
        retry_lanes.append(key)
    if "요청 제한 보호" in labels or "요청 제한 중단 기준" in labels:
        rate_limit_lanes.append(key)
    if "중복 실행 방지" in labels:
        lock_guard_lanes.append(key)
    if key in EXPECTED_RETRY_LANES and "재시도" not in labels:
        missing_retry_lanes.append(key)
    if key in EXPECTED_RATE_LIMIT_LANES and not ({"요청 제한 보호", "요청 제한 중단 기준"} & labels):
        missing_rate_limit_lanes.append(key)
    if "중복 실행 방지" not in labels:
        missing_lock_guard_lanes.append(key)

failed_jobs = int(data.get("failedJobsInWindow", 0))
partial_jobs = int(data.get("partialSuccessJobsInWindow", 0))
open_circuits = [c["circuitKey"] for c in data.get("circuitStatuses", []) if c.get("open") is True]

if missing_retry_lanes or missing_rate_limit_lanes or missing_lock_guard_lanes:
    decision_class = "RESILIENCE_DRIFT"
    operator_reading = "One or more collect lanes lost expected retry, rate-limit, or duplicate-run guard metadata. Fix the lane configuration contract before treating source resilience as stable."
    next_action = "docs/collect/collect-current-state.md"
elif failed_jobs > 0 or open_circuits:
    decision_class = "ATTENTION_REQUIRED"
    operator_reading = "Recent failed collect jobs or open circuits exist. Investigate current lane health before trusting the resilience baseline."
    next_action = "bash deploy/smoke/run-local-admin-collect-failures-smoke.sh"
else:
    decision_class = "BASELINE_HEALTHY"
    operator_reading = "Each collect source exposes the expected retry, duplicate-run guard, and rate-limit protections. Current collect source resilience baseline is healthy."
    next_action = "docs/collect/collect-ops.md"

summary_lines = [
    "collect_source_resilience_audit=passed",
    f"artifact_dir={artifact_dir}",
    f"generated_at={data.get('generatedAt', '')}",
    f"summary_window_days={data.get('windowDays', '')}",
    f"lane_count={len(lanes)}",
    f"scheduled_lane_count={len(scheduled_lanes)}",
    f"rotation_lane_count={len(rotation_lanes)}",
    f"manual_lane_count={len(manual_lanes)}",
    f"retry_lane_keys={','.join(retry_lanes) if retry_lanes else '(none)'}",
    f"rate_limit_lane_keys={','.join(rate_limit_lanes) if rate_limit_lanes else '(none)'}",
    f"lock_guard_lane_keys={','.join(lock_guard_lanes) if lock_guard_lanes else '(none)'}",
    f"missing_retry_lane_keys={','.join(missing_retry_lanes) if missing_retry_lanes else '(none)'}",
    f"missing_rate_limit_lane_keys={','.join(missing_rate_limit_lanes) if missing_rate_limit_lanes else '(none)'}",
    f"missing_lock_guard_lane_keys={','.join(missing_lock_guard_lanes) if missing_lock_guard_lanes else '(none)'}",
    f"fail_on_empty_snapshot_lane_keys={','.join(sorted(FAIL_ON_EMPTY_SNAPSHOT_LANES))}",
    f"failed_jobs_in_window={failed_jobs}",
    f"partial_success_jobs_in_window={partial_jobs}",
    f"open_collect_circuit_keys={','.join(open_circuits) if open_circuits else '(none)'}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    f"next_action={next_action}",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": artifact_dir,
    "generated_at": data.get("generatedAt", ""),
    "summary_window_days": data.get("windowDays"),
    "lane_count": len(lanes),
    "scheduled_lane_count": len(scheduled_lanes),
    "rotation_lane_count": len(rotation_lanes),
    "manual_lane_count": len(manual_lanes),
    "retry_lane_keys": retry_lanes,
    "rate_limit_lane_keys": rate_limit_lanes,
    "lock_guard_lane_keys": lock_guard_lanes,
    "missing_retry_lane_keys": missing_retry_lanes,
    "missing_rate_limit_lane_keys": missing_rate_limit_lanes,
    "missing_lock_guard_lane_keys": missing_lock_guard_lanes,
    "fail_on_empty_snapshot_lane_keys": sorted(FAIL_ON_EMPTY_SNAPSHOT_LANES),
    "failed_jobs_in_window": failed_jobs,
    "partial_success_jobs_in_window": partial_jobs,
    "open_collect_circuit_keys": open_circuits,
    "decision_class": decision_class,
    "operator_reading": operator_reading,
    "next_action": next_action,
    "lane_keys": lane_keys,
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Collect Source Resilience Audit",
    "",
    f"- `decision_class`: `{decision_class}`",
    f"- `lane_count`: `{len(lanes)}`",
    f"- `scheduled_lane_count`: `{len(scheduled_lanes)}`",
    f"- `rotation_lane_count`: `{len(rotation_lanes)}`",
    f"- `manual_lane_count`: `{len(manual_lanes)}`",
    f"- `retry_lane_keys`: `{','.join(retry_lanes) if retry_lanes else '(none)'}`",
    f"- `rate_limit_lane_keys`: `{','.join(rate_limit_lanes) if rate_limit_lanes else '(none)'}`",
    f"- `lock_guard_lane_keys`: `{','.join(lock_guard_lanes) if lock_guard_lanes else '(none)'}`",
    f"- `missing_retry_lane_keys`: `{','.join(missing_retry_lanes) if missing_retry_lanes else '(none)'}`",
    f"- `missing_rate_limit_lane_keys`: `{','.join(missing_rate_limit_lanes) if missing_rate_limit_lanes else '(none)'}`",
    f"- `missing_lock_guard_lane_keys`: `{','.join(missing_lock_guard_lanes) if missing_lock_guard_lanes else '(none)'}`",
    f"- `fail_on_empty_snapshot_lane_keys`: `{','.join(sorted(FAIL_ON_EMPTY_SNAPSHOT_LANES))}`",
    f"- `failed_jobs_in_window`: `{failed_jobs}`",
    f"- `partial_success_jobs_in_window`: `{partial_jobs}`",
    f"- `open_collect_circuit_keys`: `{','.join(open_circuits) if open_circuits else '(none)'}`",
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
