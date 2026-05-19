#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

SNAPSHOT_ROOT="${SNAPSHOT_ROOT:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-snapshot}"
RUN_COUNT="${RUN_COUNT:-3}"
BASELINE_SUMMARY="${BASELINE_SUMMARY:-}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"

RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/tmp/recommendation-ai-exclusion-volatility/${RUN_TS_UTC}}"
SUMMARY_OUTPUT="${ARTIFACT_DIR}/volatility-summary.txt"

find_recent_summaries() {
  find "${SNAPSHOT_ROOT}" -type f -name 'ai-exclusion-snapshot-summary.txt' | sort
}

extract_key_value() {
  local output_file="$1"
  local key="$2"
  python3 - "${output_file}" "${key}" <<'PY'
import sys

output_file, key = sys.argv[1], sys.argv[2]
prefix = f"{key}="

with open(output_file, "r", encoding="utf-8") as fp:
    for raw_line in fp:
        line = raw_line.strip()
        if line.startswith(prefix):
            print(line[len(prefix):])
            break
PY
}

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"

if [[ ! "${RUN_COUNT}" =~ ^[0-9]+$ ]] || (( RUN_COUNT < 2 )); then
  echo "RUN_COUNT must be an integer >= 2" >&2
  exit 1
fi

if [[ -z "${BASELINE_SUMMARY}" ]]; then
  mapfile -t SNAPSHOT_FILES < <(find_recent_summaries)
  if (( ${#SNAPSHOT_FILES[@]} < 1 )); then
    echo "need BASELINE_SUMMARY or at least one snapshot summary under ${SNAPSHOT_ROOT}" >&2
    exit 1
  fi
  BASELINE_SUMMARY="${SNAPSHOT_FILES[$((${#SNAPSHOT_FILES[@]} - 1))]}"
fi

if [[ ! -f "${BASELINE_SUMMARY}" ]]; then
  echo "baseline summary not found: ${BASELINE_SUMMARY}" >&2
  exit 1
fi

mkdir -p "${ARTIFACT_DIR}"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" != "true" ]]; then
    rm -rf "${ARTIFACT_DIR}"
  fi
}
trap cleanup EXIT

smoke_require_command bash
smoke_require_command python3

for ((i = 1; i <= RUN_COUNT; i++)); do
  RUN_LABEL="$(printf 'run-%02d' "${i}")"
  RUN_DIR="${ARTIFACT_DIR}/${RUN_LABEL}"
  mkdir -p "${RUN_DIR}"
  smoke_print_step "ai exclusion drift check ${RUN_LABEL}"
  BASELINE_SUMMARY="${BASELINE_SUMMARY}" \
  APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}" \
  TARGET_USER_KEY="${TARGET_USER_KEY:-}" \
  USER_EMAIL="${USER_EMAIL:-}" \
  USER_PASSWORD="${USER_PASSWORD:-}" \
  USER_ACCESS_TOKEN="${USER_ACCESS_TOKEN:-}" \
  ADMIN_EMAIL="${ADMIN_EMAIL:-}" \
  ADMIN_PASSWORD="${ADMIN_PASSWORD:-}" \
  TARGET_SERVICE_IDS_CSV="${TARGET_SERVICE_IDS_CSV:-3288,3289,3290,5837}" \
  TOP_REFRESH_LIMIT="${TOP_REFRESH_LIMIT:-20}" \
  TOP_N="${TOP_N:-20}" \
  SAMPLE_LIMIT="${SAMPLE_LIMIT:-10}" \
  BASELINE_COHORT="${BASELINE_COHORT:-non_example}" \
  TARGET_COHORT="${TARGET_COHORT:-real_user}" \
  KEEP_ARTIFACTS=true \
  ARTIFACT_DIR="${RUN_DIR}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-exclusion-drift-check.sh" \
    | tee "${RUN_DIR}/drift-check.out"
done

python3 - "${ARTIFACT_DIR}" "${BASELINE_SUMMARY}" "${RUN_COUNT}" <<'PY' | tee "${SUMMARY_OUTPUT}"
import sys
from collections import Counter
from pathlib import Path

artifact_dir = Path(sys.argv[1])
baseline_summary = Path(sys.argv[2])
run_count = int(sys.argv[3])


def parse_kv(path: Path) -> dict[str, str]:
    data = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key] = value
    return data


drift_runs = 0
changed_counter = Counter()
target_bucket_counter = Counter()
fresh_zero_counter = Counter()
target_summary_paths = []

for idx in range(1, run_count + 1):
    run_dir = artifact_dir / f"run-{idx:02d}"
    drift_out = run_dir / "drift-check.out"
    drift = parse_kv(drift_out)
    if drift.get("drift_detected") == "true":
        drift_runs += 1
    changed_keys = drift.get("changed_keys", "")
    for key in filter(None, changed_keys.split(",")):
        changed_counter[key] += 1
    target_summary = Path(drift.get("target_summary", ""))
    target_summary_paths.append(target_summary)
    summary = parse_kv(target_summary)
    target_bucket_counter[summary.get("ai_zero_reason_buckets", "")] += 1
    fresh_zero_counter[summary.get("fresh_top_ai_zero_count", "")] += 1
    print(
        f"run_{idx:02d}_target_summary={target_summary}"
    )
    print(
        f"run_{idx:02d}_fresh_top_ai_zero_count={summary.get('fresh_top_ai_zero_count', '')}"
    )
    print(
        f"run_{idx:02d}_ai_zero_reason_buckets={summary.get('ai_zero_reason_buckets', '')}"
    )
    print(
        f"run_{idx:02d}_changed_keys={changed_keys}"
    )


def encode(counter: Counter) -> str:
    if not counter:
        return ""
    return ",".join(f"{key}:{counter[key]}" for key in sorted(counter))


print(f"baseline_summary={baseline_summary}")
print(f"run_count={run_count}")
print(f"drift_detected_runs={drift_runs}")
print(f"changed_keys_frequency={encode(changed_counter)}")
print(f"fresh_top_ai_zero_count_frequency={encode(fresh_zero_counter)}")
print(f"ai_zero_reason_buckets_frequency={encode(target_bucket_counter)}")
print(f"artifact_dir={artifact_dir}")
PY

echo
echo "recommendation ai exclusion volatility audit passed"
echo "baseline_summary=${BASELINE_SUMMARY}"
echo "summary_output=${SUMMARY_OUTPUT}"
