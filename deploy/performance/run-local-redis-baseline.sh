#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

REDIS_CONTAINER_NAME="${REDIS_CONTAINER_NAME:-youth-welfare-redis}"
REDIS_BASELINE_ROOT="${REDIS_BASELINE_ROOT:-${ROOT_DIR}/tmp/performance/redis}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${REDIS_BASELINE_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
INFO_ALL="${ARTIFACT_DIR}/redis-info.txt"
SLOWLOG="${ARTIFACT_DIR}/redis-slowlog.txt"
SUMMARY_TXT="${ARTIFACT_DIR}/redis-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/redis-summary.json"
LATEST_DIR="${REDIS_BASELINE_ROOT}/latest"
LATEST_SUMMARY_TXT="${REDIS_BASELINE_ROOT}/latest-redis-summary.txt"
LATEST_SUMMARY_JSON="${REDIS_BASELINE_ROOT}/latest-redis-summary.json"

smoke_require_command docker
perf_require_python
mkdir -p "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"

if ! docker ps --format '{{.Names}}' | grep -Fxq "${REDIS_CONTAINER_NAME}"; then
  echo "redis_baseline=skipped"
  echo "reason=container_not_running"
  exit 0
fi

docker exec "${REDIS_CONTAINER_NAME}" redis-cli INFO > "${INFO_ALL}"
docker exec "${REDIS_CONTAINER_NAME}" redis-cli SLOWLOG GET 20 > "${SLOWLOG}" || true

python3 - "${INFO_ALL}" "${SLOWLOG}" "${SUMMARY_TXT}" "${SUMMARY_JSON}" "${CONTEXT_TXT}" <<'PY'
import json, sys
from pathlib import Path

info_path, slowlog_path, summary_txt, summary_json, context_path = map(Path, sys.argv[1:6])
info = {}
for raw in info_path.read_text(encoding="utf-8", errors="replace").splitlines():
    raw = raw.strip()
    if not raw or raw.startswith("#") or ":" not in raw:
        continue
    key, value = raw.split(":", 1)
    info[key] = value

selected_keys = [
    "redis_version", "uptime_in_seconds", "connected_clients", "blocked_clients",
    "used_memory", "used_memory_human", "used_memory_peak", "used_memory_peak_human",
    "mem_fragmentation_ratio", "total_commands_processed", "instantaneous_ops_per_sec",
    "total_net_input_bytes", "total_net_output_bytes", "rejected_connections",
    "expired_keys", "evicted_keys", "keyspace_hits", "keyspace_misses",
    "used_cpu_sys", "used_cpu_user", "used_cpu_sys_children", "used_cpu_user_children",
]

hits = int(info.get("keyspace_hits", "0") or 0)
misses = int(info.get("keyspace_misses", "0") or 0)
hit_rate = hits / (hits + misses) if hits + misses else None

context = {}
for line in context_path.read_text(encoding="utf-8").splitlines():
    if "=" in line:
        k, v = line.split("=", 1)
        context[k] = v

summary = {
    "context": context,
    "metrics": {key: info.get(key) for key in selected_keys},
    "keyspace_hit_rate": hit_rate,
    "slowlog_path": str(slowlog_path),
}
summary_json.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = ["redis_baseline=passed"]
for key in selected_keys:
    if key in info:
        lines.append(f"{key}={info[key]}")
if hit_rate is not None:
    lines.append(f"keyspace_hit_rate={hit_rate:.6f}")
lines.append(f"slowlog_path={slowlog_path}")
summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")
PY

perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
