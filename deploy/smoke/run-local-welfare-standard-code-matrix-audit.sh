#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
MATRIX_ROOT="${MATRIX_ROOT:-${ROOT_DIR}/tmp/welfare-standard-code-matrix-audit}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${MATRIX_ROOT}/${RUN_TS_UTC}}"
SUMMARY_OUT="${ARTIFACT_DIR}/welfare-standard-code-matrix-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/welfare-standard-code-matrix-summary.json"
LATEST_ARTIFACT_LINK="${MATRIX_ROOT}/latest"
LATEST_SUMMARY_LINK="${MATRIX_ROOT}/latest-welfare-standard-code-matrix-summary.txt"
LATEST_JSON_LINK="${MATRIX_ROOT}/latest-welfare-standard-code-matrix-summary.json"

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
mkdir -p "${ARTIFACT_DIR}"

run_scenario() {
  local label="$1"
  shift
  local output_file="${ARTIFACT_DIR}/${label}.out"
  local scenario_artifact_dir="${ARTIFACT_DIR}/${label}-artifact"

  smoke_print_step "welfare standard code matrix ${label}"
  APP_BASE_URL="${APP_BASE_URL}" \
  KEEP_ARTIFACTS=true \
  ARTIFACT_DIR="${scenario_artifact_dir}" \
  MIN_POSITIVE_RULE_DELTA_ROWS=0 \
  MIN_MAX_RULE_DELTA=0 \
  "$@" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-housing-standard-code-effect-audit.sh" | tee "${output_file}"
}

run_scenario "basic_living_only" env \
  SMOKE_INTEREST_FIELD="금융·생활지원" \
  SMOKE_PRIORITY_CODE="FINANCE" \
  SMOKE_INCOME_LEVEL="5" \
  SMOKE_HOUSE_TENURE_CODE="" \
  SMOKE_HOUSING_TYPE_CODE="" \
  SMOKE_BASIC_LIVING_RECIPIENT_TYPE_CODE="1" \
  SMOKE_DISABILITY_GRADE_CODE=""

run_scenario "disability_only" env \
  SMOKE_INTEREST_FIELD="건강·의료" \
  SMOKE_PRIORITY_CODE="FAMILY" \
  SMOKE_INCOME_LEVEL="5" \
  SMOKE_HOUSE_TENURE_CODE="" \
  SMOKE_HOUSING_TYPE_CODE="" \
  SMOKE_BASIC_LIVING_RECIPIENT_TYPE_CODE="" \
  SMOKE_DISABILITY_GRADE_CODE="041"

run_scenario "basic_living_and_housing_combo" env \
  SMOKE_INTEREST_FIELD="주거" \
  SMOKE_PRIORITY_CODE="HOUSING" \
  SMOKE_INCOME_LEVEL="5" \
  SMOKE_HOUSE_TENURE_CODE="3" \
  SMOKE_HOUSING_TYPE_CODE="4" \
  SMOKE_BASIC_LIVING_RECIPIENT_TYPE_CODE="1" \
  SMOKE_DISABILITY_GRADE_CODE=""

run_scenario "disability_and_housing_combo" env \
  SMOKE_INTEREST_FIELD="주거" \
  SMOKE_PRIORITY_CODE="HOUSING" \
  SMOKE_INCOME_LEVEL="5" \
  SMOKE_HOUSE_TENURE_CODE="3" \
  SMOKE_HOUSING_TYPE_CODE="4" \
  SMOKE_BASIC_LIVING_RECIPIENT_TYPE_CODE="" \
  SMOKE_DISABILITY_GRADE_CODE="041"

python3 - "${ARTIFACT_DIR}" "${SUMMARY_OUT}" "${JSON_OUT}" <<'PY'
import json
import sys
from pathlib import Path

artifact_dir = Path(sys.argv[1])
summary_out = Path(sys.argv[2])
json_out = Path(sys.argv[3])

scenario_files = sorted(artifact_dir.glob("*.out"))
scenarios = []
for path in scenario_files:
    metrics = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line.startswith("METRIC "):
            continue
        payload = line[len("METRIC "):]
        if "=" not in payload:
            continue
        key, value = payload.split("=", 1)
        metrics[key.strip()] = value.strip()
    if not metrics:
        continue
    scenarios.append({
        "scenario": path.stem,
        "output_file": str(path),
        "positive_rule_delta_rows": int(metrics.get("positive_rule_delta_rows", "0") or 0),
        "positive_final_delta_rows": int(metrics.get("positive_final_delta_rows", "0") or 0),
        "max_rule_delta": float(metrics.get("max_rule_delta", "0") or 0),
        "max_final_delta": float(metrics.get("max_final_delta", "0") or 0),
        "top_positive_rule_delta_rows": metrics.get("top_positive_rule_delta_rows", ""),
    })

positive_rule_scenarios = sum(1 for item in scenarios if item["positive_rule_delta_rows"] > 0)
positive_final_scenarios = sum(1 for item in scenarios if item["positive_final_delta_rows"] > 0)
max_rule_scenario = max(scenarios, key=lambda item: item["max_rule_delta"], default=None)
max_final_scenario = max(scenarios, key=lambda item: item["max_final_delta"], default=None)

summary_lines = [
    "welfare_standard_code_matrix_audit=passed",
    f"artifact_dir={artifact_dir}",
    f"scenario_count={len(scenarios)}",
    f"positive_rule_scenarios={positive_rule_scenarios}",
    f"positive_final_scenarios={positive_final_scenarios}",
    f"max_rule_delta_scenario={(max_rule_scenario or {}).get('scenario', '')}",
    f"max_rule_delta={(max_rule_scenario or {}).get('max_rule_delta', 0):.5f}",
    f"max_final_delta_scenario={(max_final_scenario or {}).get('scenario', '')}",
    f"max_final_delta={(max_final_scenario or {}).get('max_final_delta', 0):.5f}",
    "scenario_rule_delta_snapshot=" + " || ".join(
        f"{item['scenario']}:{item['positive_rule_delta_rows']}:{item['max_rule_delta']:.5f}"
        for item in scenarios
    ),
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": str(artifact_dir),
    "scenario_count": len(scenarios),
    "positive_rule_scenarios": positive_rule_scenarios,
    "positive_final_scenarios": positive_final_scenarios,
    "max_rule_delta_scenario": (max_rule_scenario or {}).get("scenario", ""),
    "max_rule_delta": (max_rule_scenario or {}).get("max_rule_delta", 0),
    "max_final_delta_scenario": (max_final_scenario or {}).get("scenario", ""),
    "max_final_delta": (max_final_scenario or {}).get("max_final_delta", 0),
    "scenarios": scenarios,
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

smoke_publish_dir_snapshot "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
smoke_publish_file "${SUMMARY_OUT}" "${LATEST_SUMMARY_LINK}"
smoke_publish_file "${JSON_OUT}" "${LATEST_JSON_LINK}"

cat "${SUMMARY_OUT}"
echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
