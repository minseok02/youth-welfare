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

print(f"status_json={status_json}")
print(f"gate_status={status}")
print(f"gate_reason={reason}")
print(f"generated_at_utc={data.get('generated_at_utc', '')}")
print(f"generated_at_kst={data.get('generated_at_kst', '')}")
print(f"operator_next_step={data.get('operator_next_step', '')}")
print(f"effective_operator_next_step={data.get('effective_operator_next_step', data.get('operator_next_step', ''))}")
print(f"status_json_stale_relative_to_summaries={status_json_stale_relative_to_summaries}")
print(f"status_json_recommended_action={status_json_recommended_action}")
print(f"latest_drift_class={data.get('latest_drift_class', '')}")
print(f"latest_recommended_reading={data.get('latest_recommended_reading', '')}")
print(f"interpretation_changed={interpretation_changed}")
print(f"stable_baseline_changed={stable_baseline_changed}")
print(f"latest_observation_changed={latest_observation_changed}")
print(f"changed_keys={drift.get('changed_keys', '')}")
review_gate_context = data.get("review_gate_context", {})
print(f"primary_review_gate_blocker_class={review_gate_context.get('primary_review_gate_blocker_class', '')}")
print(f"recent_window_recommendation_review_reading={review_gate_context.get('recent_window_recommendation_review_reading', '')}")
print(f"historical_example_dominance_detected={str(review_gate_context.get('historical_example_dominance_detected', '')).lower() if review_gate_context else ''}")

sys.exit(exit_code)
PY
