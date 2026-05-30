#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/policy-quality-observation}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

SMOKE_OUTPUT="${ARTIFACT_DIR}/policy-quality-summary.out"
SMOKE_ARTIFACT_DIR="${ARTIFACT_DIR}/policy-quality-summary-artifact"
SUMMARY_OUT="${ARTIFACT_DIR}/policy-quality-observation-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/policy-quality-observation.json"
NOTE_OUT="${ARTIFACT_DIR}/policy-quality-observation-note.md"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-policy-quality-observation-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-policy-quality-observation.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-policy-quality-observation-note.md"

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

smoke_print_step "policy quality observation"
APP_BASE_URL="${APP_BASE_URL}" \
KEEP_ARTIFACTS=true \
ARTIFACT_DIR="${SMOKE_ARTIFACT_DIR}" \
bash "${ROOT_DIR}/deploy/smoke/run-local-policy-quality-summary.sh" | tee "${SMOKE_OUTPUT}"

python3 - "${SMOKE_OUTPUT}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" <<'PY'
import json
import sys
from pathlib import Path

output_path = Path(sys.argv[1])
summary_out = Path(sys.argv[2])
json_out = Path(sys.argv[3])
note_out = Path(sys.argv[4])
artifact_dir = sys.argv[5]

values = {}
for raw_line in output_path.read_text(encoding="utf-8").splitlines():
    if "=" not in raw_line:
        continue
    key, value = raw_line.split("=", 1)
    values[key.strip()] = value.strip()

def as_float(name: str) -> float:
    try:
        return float(values.get(name, "0") or "0")
    except ValueError:
        return 0.0

def as_int(name: str) -> int:
    try:
        return int(values.get(name, "0") or "0")
    except ValueError:
        return 0

quality_gate_passed = values.get("quality_gate_passed", "").lower() == "true"
top1 = as_float("top1_hit_rate")
top3 = as_float("top3_hit_rate")
branch = as_float("branch_suggestion_hit_rate")
fallback_count = as_int("fallback_count")
empty_result_count = as_int("empty_result_count")
searchable_ratio = as_float("category_searchable_ratio")
failure_reasons = values.get("quality_gate_failure_reasons", "")

if not quality_gate_passed or empty_result_count > 0:
    decision_class = "ATTENTION_REQUIRED"
    operator_reading = "Policy retrieval or category baseline has drifted. Review retrieval gate failures and rerun bounded admin runtime checks before treating policy quality as healthy."
    next_action = "docs/policy/policy-admin-runtime-runbook.md"
elif top1 < 1.0 or top3 < 1.0 or branch < 1.0:
    decision_class = "BASELINE_REVIEW"
    operator_reading = "The quality gate still passes, but one or more retrieval hit-rate metrics moved below the ideal baseline. Compare retrieval evaluation output before reopening policy tuning."
    next_action = "bash deploy/smoke/run-local-policy-quality-summary.sh"
else:
    decision_class = "BASELINE_HEALTHY"
    operator_reading = "Policy retrieval and category baseline are healthy. Keep the current normalization/runtime boundary and only reopen policy tuning if a new regression appears."
    next_action = "docs/policy/policy-normalization-current-state.md"

summary_lines = [
    "policy_quality_observation_suite=passed",
    f"artifact_dir={artifact_dir}",
    f"dataset_key={values.get('dataset_key', '')}",
    f"scenario_count={values.get('scenario_count', '')}",
    f"top1_hit_rate={values.get('top1_hit_rate', '')}",
    f"top3_hit_rate={values.get('top3_hit_rate', '')}",
    f"branch_suggestion_hit_rate={values.get('branch_suggestion_hit_rate', '')}",
    f"fallback_count={fallback_count}",
    f"empty_result_count={empty_result_count}",
    f"quality_gate_passed={str(quality_gate_passed).lower()}",
    f"quality_gate_failure_reasons={failure_reasons}",
    f"category_searchable_ratio={values.get('category_searchable_ratio', '')}",
    f"category_top_unified={values.get('category_top_unified', '')}",
    f"category_top_unified_total_share={values.get('category_top_unified_total_share', '')}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    f"next_action={next_action}",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": artifact_dir,
    "dataset_key": values.get("dataset_key", ""),
    "scenario_count": values.get("scenario_count", ""),
    "top1_hit_rate": top1,
    "top3_hit_rate": top3,
    "branch_suggestion_hit_rate": branch,
    "fallback_count": fallback_count,
    "empty_result_count": empty_result_count,
    "quality_gate_passed": quality_gate_passed,
    "quality_gate_failure_reasons": failure_reasons,
    "category_searchable_ratio": searchable_ratio,
    "category_top_unified": values.get("category_top_unified", ""),
    "category_top_unified_total_share": values.get("category_top_unified_total_share", ""),
    "decision_class": decision_class,
    "operator_reading": operator_reading,
    "next_action": next_action,
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Policy Quality Observation",
    "",
    f"- `decision_class`: `{decision_class}`",
    f"- `dataset_key`: `{values.get('dataset_key', '')}`",
    f"- `scenario_count`: `{values.get('scenario_count', '')}`",
    f"- `top1_hit_rate`: `{values.get('top1_hit_rate', '')}`",
    f"- `top3_hit_rate`: `{values.get('top3_hit_rate', '')}`",
    f"- `branch_suggestion_hit_rate`: `{values.get('branch_suggestion_hit_rate', '')}`",
    f"- `fallback_count`: `{fallback_count}`",
    f"- `empty_result_count`: `{empty_result_count}`",
    f"- `quality_gate_passed`: `{str(quality_gate_passed).lower()}`",
    f"- `quality_gate_failure_reasons`: `{failure_reasons}`",
    f"- `category_searchable_ratio`: `{values.get('category_searchable_ratio', '')}`",
    f"- `category_top_unified`: `{values.get('category_top_unified', '')}`",
    f"- `category_top_unified_total_share`: `{values.get('category_top_unified_total_share', '')}`",
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
