#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

REDIS_CONTAINER_NAME="${REDIS_CONTAINER_NAME:-youth-welfare-redis}"
REDIS_OBSERVABILITY_ROOT="${REDIS_OBSERVABILITY_ROOT:-${ROOT_DIR}/tmp/performance/redis-observability}"
REDIS_SCAN_COUNT="${REDIS_SCAN_COUNT:-1000}"
REDIS_CACHE_SCAN_COUNT="${REDIS_CACHE_SCAN_COUNT:-1000}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${REDIS_OBSERVABILITY_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
PING_TXT="${ARTIFACT_DIR}/redis-ping.txt"
INFO_ALL="${ARTIFACT_DIR}/redis-info-all.txt"
INFO_COMMANDSTATS="${ARTIFACT_DIR}/redis-info-commandstats.txt"
MEMORY_STATS="${ARTIFACT_DIR}/redis-memory-stats.txt"
LATENCY_LATEST="${ARTIFACT_DIR}/redis-latency-latest.txt"
LATENCY_DOCTOR="${ARTIFACT_DIR}/redis-latency-doctor.txt"
SLOWLOG_LEN="${ARTIFACT_DIR}/redis-slowlog-len.txt"
SLOWLOG_SAMPLE="${ARTIFACT_DIR}/redis-slowlog-sample.txt"
DBSIZE_TXT="${ARTIFACT_DIR}/redis-dbsize.txt"
KEY_SAMPLE="${ARTIFACT_DIR}/redis-key-sample.txt"
CACHE_NAMESPACE_TSV="${ARTIFACT_DIR}/redis-cache-namespace-summary.tsv"
SUMMARY_TXT="${ARTIFACT_DIR}/redis-observability-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/redis-observability-summary.json"
LATEST_DIR="${REDIS_OBSERVABILITY_ROOT}/latest"
LATEST_SUMMARY_TXT="${REDIS_OBSERVABILITY_ROOT}/latest-redis-observability-summary.txt"
LATEST_SUMMARY_JSON="${REDIS_OBSERVABILITY_ROOT}/latest-redis-observability-summary.json"

perf_require_python
mkdir -p "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"

redis_cli() {
  smoke_redis_cli "$@"
}

collect_cache_namespace() {
  local name="$1"
  local pattern="$2"
  local sampled_count=0
  local ttl_sample_count=0
  local ttl_expiring_count=0
  local ttl_no_expire_count=0
  local ttl_missing_count=0
  local ttl_error_count=0
  local ttl_min_seconds=""
  local ttl_max_seconds=""
  local ttl_avg_seconds=""
  local lua
  local -a stats=()

  lua='
local pattern = ARGV[1]
local limit = tonumber(ARGV[2])
local cursor = "0"
local sampled = 0
local ttl_sample = 0
local ttl_expiring = 0
local ttl_no_expire = 0
local ttl_missing = 0
local ttl_min = nil
local ttl_max = nil
local ttl_sum = 0
repeat
  local scan_result = redis.call("SCAN", cursor, "MATCH", pattern, "COUNT", 1000)
  cursor = scan_result[1]
  for _, key in ipairs(scan_result[2]) do
    sampled = sampled + 1
    local ttl = redis.call("TTL", key)
    ttl_sample = ttl_sample + 1
    if ttl >= 0 then
      ttl_expiring = ttl_expiring + 1
      ttl_sum = ttl_sum + ttl
      if ttl_min == nil or ttl < ttl_min then
        ttl_min = ttl
      end
      if ttl_max == nil or ttl > ttl_max then
        ttl_max = ttl
      end
    elseif ttl == -1 then
      ttl_no_expire = ttl_no_expire + 1
    elseif ttl == -2 then
      ttl_missing = ttl_missing + 1
    end
    if sampled >= limit then
      cursor = "0"
      break
    end
  end
until cursor == "0"
local ttl_avg = ""
if ttl_expiring > 0 then
  ttl_avg = string.format("%.1f", ttl_sum / ttl_expiring)
end
return {
  tostring(sampled),
  tostring(ttl_sample),
  tostring(ttl_expiring),
  tostring(ttl_no_expire),
  tostring(ttl_missing),
  "0",
  ttl_min and tostring(ttl_min) or "",
  ttl_max and tostring(ttl_max) or "",
  ttl_avg
}'

  mapfile -t stats < <(redis_cli --raw EVAL "${lua}" 0 "${pattern}" "${REDIS_CACHE_SCAN_COUNT}" 2>/dev/null || true)
  if (( ${#stats[@]} >= 9 )); then
    sampled_count="${stats[0]}"
    ttl_sample_count="${stats[1]}"
    ttl_expiring_count="${stats[2]}"
    ttl_no_expire_count="${stats[3]}"
    ttl_missing_count="${stats[4]}"
    ttl_error_count="${stats[5]}"
    ttl_min_seconds="${stats[6]}"
    ttl_max_seconds="${stats[7]}"
    ttl_avg_seconds="${stats[8]}"
  else
    ttl_error_count=1
  fi

  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "${name}" \
    "${pattern}" \
    "${REDIS_CACHE_SCAN_COUNT}" \
    "${sampled_count}" \
    "${ttl_sample_count}" \
    "${ttl_expiring_count}" \
    "${ttl_no_expire_count}" \
    "${ttl_missing_count}" \
    "${ttl_error_count}" \
    "${ttl_min_seconds}" \
    "${ttl_max_seconds}" \
    "${ttl_avg_seconds}" >> "${CACHE_NAMESPACE_TSV}"
}

if ! redis_cli PING > "${PING_TXT}" 2>&1; then
  {
    echo "redis_observability_baseline=skipped"
    echo "reason=redis_unreachable"
  } | tee "${SUMMARY_TXT}"
  printf '{"status":"skipped","reason":"redis_unreachable"}\n' > "${SUMMARY_JSON}"
  perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"
  exit 0
fi

redis_cli INFO > "${INFO_ALL}"
redis_cli INFO commandstats > "${INFO_COMMANDSTATS}" || true
redis_cli MEMORY STATS > "${MEMORY_STATS}" || true
redis_cli LATENCY LATEST > "${LATENCY_LATEST}" || true
redis_cli LATENCY DOCTOR > "${LATENCY_DOCTOR}" || true
redis_cli SLOWLOG LEN > "${SLOWLOG_LEN}" || true
redis_cli SLOWLOG GET 50 > "${SLOWLOG_SAMPLE}" || true
redis_cli DBSIZE > "${DBSIZE_TXT}" || true
redis_cli --scan | head -n "${REDIS_SCAN_COUNT}" > "${KEY_SAMPLE}" || true

printf 'name\tpattern\tscan_limit\tsampled_count\tttl_sample_count\tttl_expiring_count\tttl_no_expire_count\tttl_missing_count\tttl_error_count\tttl_min_seconds\tttl_max_seconds\tttl_avg_seconds\n' > "${CACHE_NAMESPACE_TSV}"
collect_cache_namespace "public_search" "policy:search:public:v1:*"
collect_cache_namespace "ranking" "policy:ranking:v1:*"
collect_cache_namespace "active_list_count" "policy:list:active-only:count:v1"

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
  "${CACHE_NAMESPACE_TSV}" \
  "${SUMMARY_TXT}" \
  "${SUMMARY_JSON}" \
  "${CONTEXT_TXT}" <<'PY'
import collections
import csv
import json
import re
import sys
from pathlib import Path

info_path, commandstats_path, memory_path, latency_latest_path, latency_doctor_path, slowlog_len_path, slowlog_sample_path, dbsize_path, key_sample_path, cache_namespace_path, summary_txt, summary_json, context_path = map(Path, sys.argv[1:14])

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
    "cache_namespaces": list(csv.DictReader(cache_namespace_path.open(encoding="utf-8"), delimiter="\t")),
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
        "cache_namespace_summary": str(cache_namespace_path),
    },
}
summary_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

cache_namespaces = payload["cache_namespaces"]
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
for row in cache_namespaces:
    lines.append(
        "cache_namespace "
        f"name={row.get('name')} "
        f"sampled_count={row.get('sampled_count')} "
        f"ttl_expiring_count={row.get('ttl_expiring_count')} "
        f"ttl_no_expire_count={row.get('ttl_no_expire_count')} "
        f"ttl_missing_count={row.get('ttl_missing_count')} "
        f"ttl_min_seconds={row.get('ttl_min_seconds')} "
        f"ttl_max_seconds={row.get('ttl_max_seconds')} "
        f"ttl_avg_seconds={row.get('ttl_avg_seconds')}"
    )
summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")
PY

perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
