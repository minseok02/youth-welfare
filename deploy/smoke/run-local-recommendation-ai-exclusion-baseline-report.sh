#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

VOLATILITY_SUMMARY="${VOLATILITY_SUMMARY:-}"
VOLATILITY_ROOT="${VOLATILITY_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-volatility}"
BASELINE_SUMMARY="${BASELINE_SUMMARY:-}"
TARGET_SUMMARY="${TARGET_SUMMARY:-}"
SNAPSHOT_ROOT="${SNAPSHOT_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-snapshot}"

find_recent_files() {
  local root="$1"
  local pattern="$2"
  find "${root}" -type f -name "${pattern}" | sort
}

if [[ -z "${VOLATILITY_SUMMARY}" ]]; then
  mapfile -t VOL_FILES < <(find_recent_files "${VOLATILITY_ROOT}" 'volatility-summary.txt')
  if (( ${#VOL_FILES[@]} < 1 )); then
    echo "need VOLATILITY_SUMMARY or at least one volatility-summary.txt under ${VOLATILITY_ROOT}" >&2
    exit 1
  fi
  VOLATILITY_SUMMARY="${VOL_FILES[$((${#VOL_FILES[@]} - 1))]}"
fi

if [[ -z "${BASELINE_SUMMARY}" || -z "${TARGET_SUMMARY}" ]]; then
  mapfile -t SNAPSHOT_FILES < <(find_recent_files "${SNAPSHOT_ROOT}" 'ai-exclusion-snapshot-summary.txt')
  if (( ${#SNAPSHOT_FILES[@]} < 2 )); then
    echo "need BASELINE_SUMMARY and TARGET_SUMMARY or at least two snapshot summaries under ${SNAPSHOT_ROOT}" >&2
    exit 1
  fi
  BASELINE_SUMMARY="${SNAPSHOT_FILES[$((${#SNAPSHOT_FILES[@]} - 2))]}"
  TARGET_SUMMARY="${SNAPSHOT_FILES[$((${#SNAPSHOT_FILES[@]} - 1))]}"
fi

if [[ ! -f "${VOLATILITY_SUMMARY}" ]]; then
  echo "volatility summary not found: ${VOLATILITY_SUMMARY}" >&2
  exit 1
fi

if [[ ! -f "${BASELINE_SUMMARY}" ]]; then
  echo "baseline summary not found: ${BASELINE_SUMMARY}" >&2
  exit 1
fi

if [[ ! -f "${TARGET_SUMMARY}" ]]; then
  echo "target summary not found: ${TARGET_SUMMARY}" >&2
  exit 1
fi

smoke_require_command bash
smoke_require_command python3

STABILITY_OUTPUT="$(VOLATILITY_SUMMARY="${VOLATILITY_SUMMARY}" bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-exclusion-stability-report.sh")"
CLASSIFY_OUTPUT="$(VOLATILITY_SUMMARY="${VOLATILITY_SUMMARY}" BASELINE_SUMMARY="${BASELINE_SUMMARY}" TARGET_SUMMARY="${TARGET_SUMMARY}" bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-exclusion-drift-classify.sh")"

printf '%s\n' "${STABILITY_OUTPUT}"
printf '%s\n' "${CLASSIFY_OUTPUT}"

python3 - "${VOLATILITY_SUMMARY}" "${BASELINE_SUMMARY}" "${TARGET_SUMMARY}" "${STABILITY_OUTPUT}" "${CLASSIFY_OUTPUT}" <<'PY'
import sys
from pathlib import Path

volatility_summary = Path(sys.argv[1])
baseline_summary = Path(sys.argv[2])
target_summary = Path(sys.argv[3])
stability_output = sys.argv[4]
classify_output = sys.argv[5]


def parse_kv_text(text: str) -> dict[str, str]:
    data = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key] = value
    return data


def parse_kv_file(path: Path) -> dict[str, str]:
    return parse_kv_text(path.read_text(encoding="utf-8"))


volatility = parse_kv_file(volatility_summary)
baseline = parse_kv_file(baseline_summary)
target = parse_kv_file(target_summary)
stability = parse_kv_text(stability_output)
classify = parse_kv_text(classify_output)

drift_class = classify.get("drift_class", "")
if drift_class == "VOLATILE_ONLY_DRIFT":
    recommended = "READ_LATEST_AS_VOLATILE_OBSERVATION"
elif drift_class == "STABLE_BASELINE_DRIFT":
    recommended = "UPDATE_STABLE_BASELINE"
elif drift_class == "MIXED_DRIFT":
    recommended = "INVESTIGATE_BEFORE_BASELINE_UPDATE"
else:
    recommended = "BASELINE_UNCHANGED"

print(f"volatility_summary={volatility_summary}")
print(f"baseline_summary={baseline_summary}")
print(f"target_summary={target_summary}")
print(f"drift_class={drift_class}")
print(f"recommended_reading={recommended}")
print(f"stable_keys={stability.get('stable_keys', '')}")
print(f"volatile_keys={stability.get('volatile_keys', '')}")
print(f"stable_baseline_zero_ai_reason_buckets={baseline.get('baseline_zero_ai_reason_buckets', '')}")
print(f"stable_dashboard_real_user_gate={baseline.get('dashboard_real_user_gate', '')}")
print(f"stable_breakdown_real_user_cohort_gate={baseline.get('breakdown_real_user_cohort_gate', '')}")
print(f"latest_fresh_top_ai_zero_count={target.get('fresh_top_ai_zero_count', '')}")
print(f"latest_ai_zero_count={target.get('ai_zero_count', '')}")
print(f"latest_ai_zero_reason_buckets={target.get('ai_zero_reason_buckets', '')}")
print(f"volatile_changed_keys={classify.get('volatile_changed_keys', '')}")
print(f"stable_changed_keys={classify.get('stable_changed_keys', '')}")
print(f"volatility_reference_frequency={volatility.get('fresh_top_ai_zero_count_frequency', '')}")
PY
