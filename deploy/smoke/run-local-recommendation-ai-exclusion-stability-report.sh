#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

VOLATILITY_SUMMARY="${VOLATILITY_SUMMARY:-}"
VOLATILITY_ROOT="${VOLATILITY_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-volatility}"

find_recent_volatility_summaries() {
  find "${VOLATILITY_ROOT}" -type f -name 'volatility-summary.txt' | sort
}

if [[ -z "${VOLATILITY_SUMMARY}" ]]; then
  mapfile -t VOL_FILES < <(find_recent_volatility_summaries)
  if (( ${#VOL_FILES[@]} < 1 )); then
    echo "need VOLATILITY_SUMMARY or at least one volatility-summary.txt under ${VOLATILITY_ROOT}" >&2
    exit 1
  fi
  VOLATILITY_SUMMARY="${VOL_FILES[$((${#VOL_FILES[@]} - 1))]}"
fi

if [[ ! -f "${VOLATILITY_SUMMARY}" ]]; then
  echo "volatility summary not found: ${VOLATILITY_SUMMARY}" >&2
  exit 1
fi

smoke_require_command python3

python3 - "${VOLATILITY_SUMMARY}" <<'PY'
import sys
from pathlib import Path

volatility_path = Path(sys.argv[1])

KEYS = [
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


def parse_kv(path: Path) -> dict[str, str]:
    data: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key] = value
    return data


summary = parse_kv(volatility_path)
baseline_path = Path(summary["baseline_summary"])
run_count = int(summary["run_count"])

run_summaries: list[Path] = []
for idx in range(1, run_count + 1):
    run_key = f"run_{idx:02d}_target_summary"
    run_summaries.append(Path(summary[run_key]))

all_paths = [baseline_path, *run_summaries]
all_data = [parse_kv(path) for path in all_paths]

stable_keys: list[str] = []
volatile_keys: list[str] = []
for key in KEYS:
    values = [data.get(key, "") for data in all_data]
    if len(set(values)) == 1:
        stable_keys.append(key)
    else:
        volatile_keys.append(key)

print(f"volatility_summary={volatility_path}")
print(f"baseline_summary={baseline_path}")
print(f"run_count={run_count}")
print(f"stable_keys={','.join(stable_keys)}")
print(f"volatile_keys={','.join(volatile_keys)}")

for key in stable_keys:
    print(f"stable_{key}={all_data[0].get(key, '')}")

for key in volatile_keys:
    values = [data.get(key, "") for data in all_data]
    encoded = " | ".join(values)
    print(f"volatile_{key}={encoded}")
PY
