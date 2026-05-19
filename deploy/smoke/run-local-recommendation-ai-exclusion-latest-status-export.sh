#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

REFRESH_ROOT="${REFRESH_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-baseline-refresh}"
DRIFT_CHECK_ROOT="${DRIFT_CHECK_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-baseline-refresh-drift-check}"
STATUS_ROOT="${STATUS_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-latest-status}"

REFRESH_SUMMARY="${REFRESH_SUMMARY:-${REFRESH_ROOT}/latest-baseline-refresh-summary.txt}"
DRIFT_SUMMARY="${DRIFT_SUMMARY:-${DRIFT_CHECK_ROOT}/latest-baseline-refresh-drift-summary.txt}"

RUN_TS_UTC="$(date -u +%Y%m%dT%H%M%SZ)"
GENERATED_AT_UTC="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
GENERATED_AT_KST="$(TZ=Asia/Seoul date +%Y-%m-%dT%H:%M:%S%z)"
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

python3 - "${REFRESH_SUMMARY}" "${DRIFT_SUMMARY}" "${OUTPUT_MD}" "${OUTPUT_JSON}" "${GENERATED_AT_UTC}" "${GENERATED_AT_KST}" <<'PY'
import sys
import json
from pathlib import Path

refresh_path = Path(sys.argv[1])
drift_path = Path(sys.argv[2])
output_md = Path(sys.argv[3])
output_json = Path(sys.argv[4])
generated_at_utc = sys.argv[5]
generated_at_kst = sys.argv[6]


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

lines = [
    "# AI Exclusion Latest Status",
    "",
    f"- generated_at_utc: `{generated_at_utc}`",
    f"- generated_at_kst: `{generated_at_kst}`",
    f"- operator_next_step: `{operator_next_step}`",
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
]

output_md.write_text("\n".join(lines) + "\n", encoding="utf-8")
json_payload = {
    "generated_at_utc": generated_at_utc,
    "generated_at_kst": generated_at_kst,
    "operator_next_step": operator_next_step,
    "status_json_stale_relative_to_summaries": "false",
    "status_json_recommended_action": "",
    "refresh_summary": str(refresh_path),
    "drift_summary": str(drift_path),
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
}
output_json.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(output_md)
print()
print(output_md.read_text(encoding="utf-8"), end="")
print()
print(output_json)
PY

mkdir -p "${STATUS_ROOT}"
ln -sfn "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
ln -sfn "${OUTPUT_MD}" "${LATEST_NOTE_LINK}"
ln -sfn "${OUTPUT_JSON}" "${LATEST_JSON_LINK}"

echo
echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_note_link=${LATEST_NOTE_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
