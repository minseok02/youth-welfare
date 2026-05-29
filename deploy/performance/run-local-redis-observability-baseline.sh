#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

REDIS_CONTAINER_NAME="${REDIS_CONTAINER_NAME:-youth-welfare-redis}"
REDIS_OBSERVABILITY_ROOT="${REDIS_OBSERVABILITY_ROOT:-${ROOT_DIR}/tmp/performance/redis-observability}"
REDIS_SCAN_COUNT="${REDIS_SCAN_COUNT:-1000}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${REDIS_OBSERVABILITY_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
INFO_ALL="${ARTIFACT_DIR}/redis-info-all.txt"
INFO_COMMANDSTATS="${ARTIFACT_DIR}/redis-info-commandstats.txt"
MEMORY_STATS="${ARTIFACT_DIR}/redis-memory-stats.txt"
LATENCY_LATEST="${ARTIFACT_DIR}/redis-latency-latest.txt"
LATENCY_DOCTOR="${ARTIFACT_DIR}/redis-latency-doctor.txt"
SLOWLOG_LEN="${ARTIFACT_DIR}/redis-slowlog-len.txt"
SLOWLOG_SAMPLE="${ARTIFACT_DIR}/redis-slowlog-sample.txt"
DBSIZE_TXT="${ARTIFACT_DIR}/redis-dbsize.txt"
KEY_SAMPLE="${ARTIFACT_DIR}/redis-key-sample.txt"
SUMMARY_TXT="${ARTIFACT_DIR}/redis-observability-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/redis-observability-summary.json"
LATEST_DIR="${REDIS_OBSERVABILITY_ROOT}/latest"
LATEST_SUMMARY_TXT="${REDIS_OBSERVABILITY_ROOT}/latest-redis-observability-summary.txt"
LATEST_SUMMARY_JSON="${REDIS_OBSERVABILITY_ROOT}/latest-redis-observability-summary.json"

smoke_require_command docker
perf_require_python
mkdir -p "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"

if ! docker ps --format '{{.Names}}' | grep -Fxq "${REDIS_CONTAINER_NAME}"; then
  {
    echo "redis_observability_baseline=skipped"
    echo "reason=container_not_running"
  } | tee "${SUMMARY_TXT}"
  printf '{"status":"skipped","reason":"container_not_running"}\n' > "${SUMMARY_JSON}"
  perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"
  exit 0
fi

docker exec "${REDIS_CONTAINER_NAME}" redis-cli INFO > "${INFO_ALL}"
docker exec "${REDIS_CONTAINER_NAME}" redis-cli INFO commandstats > "${INFO_COMMANDSTATS}" || true
docker exec "${REDIS_CONTAINER_NAME}" redis-cli MEMORY STATS > "${MEMORY_STATS}" || true
docker exec "${REDIS_CONTAINER_NAME}" redis-cli LATENCY LATEST > "${LATENCY_LATEST}" || true
docker exec "${REDIS_CONTAINER_NAME}" redis-cli LATENCY DOCTOR > "${LATENCY_DOCTOR}" || true
docker exec "${REDIS_CONTAINER_NAME}" redis-cli SLOWLOG LEN > "${SLOWLOG_LEN}" || true
docker exec "${REDIS_CONTAINER_NAME}" redis-cli SLOWLOG GET 50 > "${SLOWLOG_SAMPLE}" || true
docker exec "${REDIS_CONTAINER_NAME}" redis-cli DBSIZE > "${DBSIZE_TXT}" || true
docker exec "${REDIS_CONTAINER_NAME}" redis-cli --scan | head -n "${REDIS_SCAN_COUNT}" > "${KEY_SAMPLE}" || true

python3 - \
  "${INFO_ALL}" \
  "${INFO_COMMANDSTATS}" \
  "${MEMORY_STATS}" \
  "${LATENCY_LATEST}" \
  "${LATENCY_DOCTOR}" \
  "${SLOWLOG_LEN}" \
  "${SLOWLOG_SAMPLE}" \
  "${DBSIZE_TXT}" \
  "${KEY_SAMPLE}" \
  "${SUMMARY_TXT}" \
  "${SUMMARY_JSON}" \
  "${CONTEXT_TXT}" <<'PY'
import collections
import json
import re
import sys
from pathlib import Path

info_path, commandstats_path, memory_path, latency_latest_path, latency_doctor_path, slowlog_len_path, slowlog_sample_path, dbsize_path, key_sample_path, summary_txt, summary_json, context_path = map(Path, sys.argv[1:13])

def parse_info(path):
    data = {}
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        raw = raw.strip()
        if not raw or raw.startswith("#") or ":" not in raw:
            continue
        key, value = raw.split(":", 1)
        data[key] = value
    return data

info = parse_info(info_path)
commandstats_raw = parse_info(commandstats_path)
commandstats = {}
for key, value in commandstats_raw.items():
    if not key.startswith("cmdstat_"):
        continue
    parts = {}
    for item in value.split(","):
        if "=" in item:
            k, v = item.split("=", 1)
            parts[k] = v
    commandstats[key.removeprefix("cmdstat_")] = parts

keys = [line.strip() for line in key_sample_path.read_text(encoding="utf-8", errors="replace").splitlines() if line.strip()]
prefix_counts = collections.Counter()
for key in keys:
    if ":" in key:
        prefix = key.split(":", 1)[0]
    elif "_" in key:
        prefix = key.split("_", 1)[0]
    else:
        prefix = "(no-prefix)"
    prefix_counts[prefix] += 1

slowlog_len_text = slowlog_len_path.read_text(encoding="utf-8", errors="replace").strip()
dbsize_text = dbsize_path.read_text(encoding="utf-8", errors="replace").strip()
latency_latest_text = latency_latest_path.read_text(encoding="utf-8", errors="replace").strip()
latency_doctor_text = latency_doctor_path.read_text(encoding="utf-8", errors="replace").strip()

hits = int(info.get("keyspace_hits", "0") or 0)
misses = int(info.get("keyspace_misses", "0") or 0)
hit_rate = hits / (hits + misses) if hits + misses else None
top_commands = sorted(
    commandstats.items(),
    key=lambda item: int(item[1].get("calls", "0") or 0),
    reverse=True,
)[:15]

context = {}
for line in context_path.read_text(encoding="utf-8").splitlines():
    if "=" in line:
        key, value = line.split("=", 1)
        context[key] = value

payload = {
    "context": context,
    "dbsize": dbsize_text,
    "key_sample_count": len(keys),
    "key_prefix_counts": prefix_counts.most_common(30),
    "keyspace_hit_rate": hit_rate,
    "slowlog_len": slowlog_len_text,
    "latency_latest_raw": latency_latest_text,
    "latency_doctor_raw": latency_doctor_text,
    "top_commands": top_commands,
    "selected_info": {
        key: info.get(key)
        for key in [
            "connected_clients", "blocked_clients", "used_memory", "used_memory_human",
            "used_memory_peak", "used_memory_peak_human", "mem_fragmentation_ratio",
            "evicted_keys", "expired_keys", "rejected_connections", "instantaneous_ops_per_sec",
            "total_commands_processed", "keyspace_hits", "keyspace_misses",
        ]
    },
    "raw_artifacts": {
        "info": str(info_path),
        "commandstats": str(commandstats_path),
        "memory_stats": str(memory_path),
        "latency_latest": str(latency_latest_path),
        "latency_doctor": str(latency_doctor_path),
        "slowlog_sample": str(slowlog_sample_path),
        "key_sample": str(key_sample_path),
    },
}
summary_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

latency_events = len([line for line in latency_latest_text.splitlines() if line.strip()])
lines = ["redis_observability_baseline=passed"]
lines.append(f"dbsize={dbsize_text}")
lines.append(f"key_sample_count={len(keys)}")
if hit_rate is not None:
    lines.append(f"keyspace_hit_rate={hit_rate:.6f}")
lines.append(f"slowlog_len={slowlog_len_text}")
lines.append(f"latency_event_lines={latency_events}")
for key in ["connected_clients", "blocked_clients", "used_memory_human", "used_memory_peak_human", "mem_fragmentation_ratio", "evicted_keys", "expired_keys", "rejected_connections"]:
    if info.get(key) is not None:
        lines.append(f"{key}={info[key]}")
for command, stats in top_commands[:10]:
    lines.append(f"cmdstat command={command} calls={stats.get('calls')} usec_per_call={stats.get('usec_per_call')}")
for prefix, count in prefix_counts.most_common(10):
    lines.append(f"key_prefix prefix={prefix} sample_count={count}")
summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")
PY

perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
