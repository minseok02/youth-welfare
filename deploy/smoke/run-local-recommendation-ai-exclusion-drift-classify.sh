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

COMPARE_OUTPUT="$(BASELINE_SUMMARY="${BASELINE_SUMMARY}" TARGET_SUMMARY="${TARGET_SUMMARY}" bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-exclusion-snapshot-compare.sh")"
printf '%s\n' "${COMPARE_OUTPUT}"

python3 - "${VOLATILITY_SUMMARY}" "${COMPARE_OUTPUT}" <<'PY'
import sys
from pathlib import Path

volatility_path = Path(sys.argv[1])
compare_output = sys.argv[2]


def parse_kv_text(text: str) -> dict[str, str]:
    data: dict[str, str] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key] = value
    return data


volatility = parse_kv_text(volatility_path.read_text(encoding="utf-8"))
compare = parse_kv_text(compare_output)

volatile_keys = set(filter(None, [
    key.strip() for key in volatility.get("changed_keys_frequency", "").replace(":", ",").split(",")[::2]
]))
volatile_keys.discard("snapshot_ts_utc")

changed_keys = set(filter(None, compare.get("changed_keys", "").split(",")))
changed_keys.discard("snapshot_ts_utc")

stable_changed = sorted(key for key in changed_keys if key not in volatile_keys)
volatile_changed = sorted(key for key in changed_keys if key in volatile_keys)

if compare.get("drift_detected") != "true":
    drift_class = "NO_DRIFT"
elif stable_changed and volatile_changed:
    drift_class = "MIXED_DRIFT"
elif stable_changed:
    drift_class = "STABLE_BASELINE_DRIFT"
else:
    drift_class = "VOLATILE_ONLY_DRIFT"

print(f"volatility_summary={volatility_path}")
print(f"volatile_reference_keys={','.join(sorted(volatile_keys))}")
print(f"drift_class={drift_class}")
print(f"stable_changed_keys={','.join(stable_changed)}")
print(f"volatile_changed_keys={','.join(volatile_changed)}")
PY
