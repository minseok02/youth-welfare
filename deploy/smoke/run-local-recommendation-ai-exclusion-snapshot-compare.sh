#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

SNAPSHOT_ROOT="${SNAPSHOT_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-snapshot}"
BASELINE_SUMMARY="${BASELINE_SUMMARY:-}"
TARGET_SUMMARY="${TARGET_SUMMARY:-}"

find_recent_summaries() {
  find "${SNAPSHOT_ROOT}" -type f -name 'ai-exclusion-snapshot-summary.txt' | sort
}

if [[ -z "${BASELINE_SUMMARY}" || -z "${TARGET_SUMMARY}" ]]; then
  mapfile -t SNAPSHOT_FILES < <(find_recent_summaries)
  if (( ${#SNAPSHOT_FILES[@]} < 2 )); then
    echo "need BASELINE_SUMMARY and TARGET_SUMMARY or at least two snapshot summaries under ${SNAPSHOT_ROOT}" >&2
    exit 1
  fi
  BASELINE_SUMMARY="${SNAPSHOT_FILES[$((${#SNAPSHOT_FILES[@]} - 2))]}"
  TARGET_SUMMARY="${SNAPSHOT_FILES[$((${#SNAPSHOT_FILES[@]} - 1))]}"
fi

if [[ ! -f "${BASELINE_SUMMARY}" ]]; then
  echo "baseline summary not found: ${BASELINE_SUMMARY}" >&2
  exit 1
fi

if [[ ! -f "${TARGET_SUMMARY}" ]]; then
  echo "target summary not found: ${TARGET_SUMMARY}" >&2
  exit 1
fi

smoke_require_command python3

python3 - "${BASELINE_SUMMARY}" "${TARGET_SUMMARY}" <<'PY'
import sys
from pathlib import Path

baseline_path = Path(sys.argv[1])
target_path = Path(sys.argv[2])

COMPARE_KEYS = [
    "snapshot_ts_utc",
    "target_service_ids_csv",
    "fresh_top_ai_zero_count",
    "ai_zero_count",
    "ai_zero_reason_buckets",
    "baseline_scope_users",
    "baseline_zero_ai_reason_buckets",
    "target_scope_users",
    "target_zero_ai_reason_buckets",
    "real_user_distribution_executed",
    "dashboard_real_user_gate",
    "breakdown_real_user_cohort_gate",
]


def load_summary(path: Path) -> dict[str, str]:
    data: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key] = value
    return data


baseline = load_summary(baseline_path)
target = load_summary(target_path)

changed_keys = [
    key for key in COMPARE_KEYS
    if baseline.get(key, "") != target.get(key, "")
]

print(f"baseline_summary={baseline_path}")
print(f"target_summary={target_path}")
print(f"baseline_snapshot_ts_utc={baseline.get('snapshot_ts_utc', '')}")
print(f"target_snapshot_ts_utc={target.get('snapshot_ts_utc', '')}")
print(f"drift_detected={'true' if changed_keys else 'false'}")
print(f"changed_keys={','.join(changed_keys)}")

for key in COMPARE_KEYS:
    print(f"baseline_{key}={baseline.get(key, '')}")
    print(f"target_{key}={target.get(key, '')}")
PY
