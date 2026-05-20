#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

REFRESH_ROOT="${REFRESH_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-baseline-refresh}"
BASELINE_REFRESH_SUMMARY="${BASELINE_REFRESH_SUMMARY:-}"
TARGET_REFRESH_SUMMARY="${TARGET_REFRESH_SUMMARY:-}"

find_recent_files() {
  local root="$1"
  local pattern="$2"
  find "${root}" -type f -name "${pattern}" | sort
}

if [[ -z "${BASELINE_REFRESH_SUMMARY}" || -z "${TARGET_REFRESH_SUMMARY}" ]]; then
  mapfile -t SUMMARY_FILES < <(find_recent_files "${REFRESH_ROOT}" 'baseline-refresh-summary.txt')
  if (( ${#SUMMARY_FILES[@]} < 2 )); then
    echo "need BASELINE_REFRESH_SUMMARY and TARGET_REFRESH_SUMMARY or at least two baseline-refresh-summary.txt files under ${REFRESH_ROOT}" >&2
    exit 1
  fi
  BASELINE_REFRESH_SUMMARY="${SUMMARY_FILES[$((${#SUMMARY_FILES[@]} - 2))]}"
  TARGET_REFRESH_SUMMARY="${SUMMARY_FILES[$((${#SUMMARY_FILES[@]} - 1))]}"
fi

if [[ ! -f "${BASELINE_REFRESH_SUMMARY}" ]]; then
  echo "baseline refresh summary not found: ${BASELINE_REFRESH_SUMMARY}" >&2
  exit 1
fi

if [[ ! -f "${TARGET_REFRESH_SUMMARY}" ]]; then
  echo "target refresh summary not found: ${TARGET_REFRESH_SUMMARY}" >&2
  exit 1
fi

smoke_require_command python3

python3 - "${BASELINE_REFRESH_SUMMARY}" "${TARGET_REFRESH_SUMMARY}" <<'PY'
import sys
from pathlib import Path

baseline_path = Path(sys.argv[1])
target_path = Path(sys.argv[2])

tracked_keys = [
    "drift_class",
    "recommended_reading",
    "stable_baseline_zero_ai_reason_buckets",
    "stable_dashboard_real_user_gate",
    "stable_breakdown_real_user_cohort_gate",
    "latest_fresh_top_ai_zero_count",
    "latest_ai_zero_count",
    "latest_ai_zero_reason_buckets",
    "volatility_reference_frequency",
]
stable_keys = {
    "stable_baseline_zero_ai_reason_buckets",
    "stable_dashboard_real_user_gate",
    "stable_breakdown_real_user_cohort_gate",
}
latest_keys = {
    "latest_fresh_top_ai_zero_count",
    "latest_ai_zero_count",
    "latest_ai_zero_reason_buckets",
    "volatility_reference_frequency",
}
interpretation_keys = {"drift_class", "recommended_reading"}


def parse_kv(path: Path) -> dict[str, str]:
    data = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key] = value
    return data


baseline = parse_kv(baseline_path)
target = parse_kv(target_path)

changed = [key for key in tracked_keys if baseline.get(key, "") != target.get(key, "")]
stable_changed = [key for key in changed if key in stable_keys]
latest_changed = [key for key in changed if key in latest_keys]
interpretation_changed = [key for key in changed if key in interpretation_keys]

print(f"baseline_refresh_summary={baseline_path}")
print(f"target_refresh_summary={target_path}")
print(f"drift_detected={'true' if changed else 'false'}")
print(f"interpretation_changed={'true' if interpretation_changed else 'false'}")
print(f"stable_baseline_changed={'true' if stable_changed else 'false'}")
print(f"latest_observation_changed={'true' if latest_changed else 'false'}")
print(f"changed_keys={','.join(changed)}")
print(f"interpretation_changed_keys={','.join(interpretation_changed)}")
print(f"stable_changed_keys={','.join(stable_changed)}")
print(f"latest_changed_keys={','.join(latest_changed)}")

for key in tracked_keys:
    print(f"baseline_{key}={baseline.get(key, '')}")
    print(f"target_{key}={target.get(key, '')}")
PY
