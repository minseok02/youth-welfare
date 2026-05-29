#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

NGINX_ACCESS_LOG="${NGINX_ACCESS_LOG:-/var/log/nginx/access.log}"
NGINX_ERROR_LOG="${NGINX_ERROR_LOG:-/var/log/nginx/error.log}"
NGINX_LOG_TAIL_LINES="${NGINX_LOG_TAIL_LINES:-10000}"
NGINX_LOG_ROOT="${NGINX_LOG_ROOT:-${ROOT_DIR}/tmp/performance/nginx-log-observability}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${NGINX_LOG_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
ACCESS_SAMPLE="${ARTIFACT_DIR}/access-tail.log"
ERROR_SAMPLE="${ARTIFACT_DIR}/error-tail.log"
SUMMARY_TXT="${ARTIFACT_DIR}/nginx-log-observability-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/nginx-log-observability-summary.json"
LATEST_DIR="${NGINX_LOG_ROOT}/latest"
LATEST_SUMMARY_TXT="${NGINX_LOG_ROOT}/latest-nginx-log-observability-summary.txt"
LATEST_SUMMARY_JSON="${NGINX_LOG_ROOT}/latest-nginx-log-observability-summary.json"

perf_require_python
mkdir -p "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"

if [[ -r "${NGINX_ACCESS_LOG}" ]]; then
  tail -n "${NGINX_LOG_TAIL_LINES}" "${NGINX_ACCESS_LOG}" > "${ACCESS_SAMPLE}" || true
else
  : > "${ACCESS_SAMPLE}"
fi

if [[ -r "${NGINX_ERROR_LOG}" ]]; then
  tail -n 1000 "${NGINX_ERROR_LOG}" > "${ERROR_SAMPLE}" || true
else
  : > "${ERROR_SAMPLE}"
fi

python3 - "${ACCESS_SAMPLE}" "${ERROR_SAMPLE}" "${SUMMARY_TXT}" "${SUMMARY_JSON}" "${CONTEXT_TXT}" "${NGINX_ACCESS_LOG}" "${NGINX_ERROR_LOG}" <<'PY'
import collections
import json
import re
import statistics
import sys
from pathlib import Path

access_path, error_path, summary_txt, summary_json, context_path = map(Path, sys.argv[1:6])
access_log, error_log = sys.argv[6:8]
access_lines = [line for line in access_path.read_text(encoding="utf-8", errors="replace").splitlines() if line.strip()]
error_lines = [line for line in error_path.read_text(encoding="utf-8", errors="replace").splitlines() if line.strip()]

status_counts = collections.Counter()
status_class_counts = collections.Counter()
method_counts = collections.Counter()
path_counts = collections.Counter()
fourxx_paths = collections.Counter()
fivexx_paths = collections.Counter()
user_agent_counts = collections.Counter()
request_times = []
upstream_times = []

combined_re = re.compile(
    r'^(?P<ip>\S+) \S+ \S+ \[[^\]]+\] "(?P<method>[A-Z]+)? ?(?P<path>[^ ]*)? [^"]*" '
    r'(?P<status>[0-9]{3}) (?P<size>[0-9]+) "[^"]*" "(?P<ua>[^"]*)"(?: (?P<tail>.*))?$'
)

for line in access_lines:
    match = combined_re.match(line)
    if not match:
        continue
    status = match.group("status")
    method = match.group("method") or "(empty)"
    raw_path = match.group("path") or "(empty)"
    path = raw_path.split("?", 1)[0]
    ua = match.group("ua") or "(empty)"
    status_counts[status] += 1
    status_class_counts[f"{status[0]}xx"] += 1
    method_counts[method] += 1
    path_counts[f"{method} {path}"] += 1
    if status.startswith("4"):
        fourxx_paths[f"{status} {method} {path}"] += 1
    if status.startswith("5"):
        fivexx_paths[f"{status} {method} {path}"] += 1
    user_agent_counts[ua[:120]] += 1
    tail = match.group("tail") or ""
    floats = [float(item) for item in re.findall(r'(?<![0-9.])([0-9]+\.[0-9]+)(?![0-9.])', tail)]
    if floats:
        request_times.append(floats[0])
    if len(floats) > 1:
        upstream_times.append(floats[1])

def pct(values, p):
    if not values:
        return None
    values = sorted(values)
    k = (len(values) - 1) * p
    f = int(k)
    c = min(f + 1, len(values) - 1)
    if f == c:
        return values[f]
    return values[f] + (values[c] - values[f]) * (k - f)

context = {}
for line in context_path.read_text(encoding="utf-8").splitlines():
    if "=" in line:
        key, value = line.split("=", 1)
        context[key] = value

request_time_summary = None
if request_times:
    request_time_summary = {
        "count": len(request_times),
        "p50_seconds": pct(request_times, 0.50),
        "p95_seconds": pct(request_times, 0.95),
        "p99_seconds": pct(request_times, 0.99),
        "max_seconds": max(request_times),
        "avg_seconds": statistics.mean(request_times),
    }
upstream_time_summary = None
if upstream_times:
    upstream_time_summary = {
        "count": len(upstream_times),
        "p50_seconds": pct(upstream_times, 0.50),
        "p95_seconds": pct(upstream_times, 0.95),
        "p99_seconds": pct(upstream_times, 0.99),
        "max_seconds": max(upstream_times),
        "avg_seconds": statistics.mean(upstream_times),
    }

payload = {
    "context": context,
    "access_log": access_log,
    "error_log": error_log,
    "sample_line_count": len(access_lines),
    "status_counts": dict(status_counts),
    "status_class_counts": dict(status_class_counts),
    "method_counts": dict(method_counts),
    "top_paths": path_counts.most_common(30),
    "top_4xx_paths": fourxx_paths.most_common(20),
    "top_5xx_paths": fivexx_paths.most_common(20),
    "top_user_agents": user_agent_counts.most_common(20),
    "error_log_line_count": len(error_lines),
    "request_time_available": request_time_summary is not None,
    "request_time_summary": request_time_summary,
    "upstream_time_available": upstream_time_summary is not None,
    "upstream_time_summary": upstream_time_summary,
}
summary_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = ["nginx_log_observability_baseline=passed"]
lines.append(f"sample_line_count={len(access_lines)}")
lines.append(f"error_log_line_count={len(error_lines)}")
lines.append(f"request_time_available={str(request_time_summary is not None).lower()}")
lines.append(f"upstream_time_available={str(upstream_time_summary is not None).lower()}")
for status, count in sorted(status_counts.items()):
    lines.append(f"status_{status}={count}")
for klass, count in sorted(status_class_counts.items()):
    lines.append(f"status_class_{klass}={count}")
if request_time_summary:
    lines.append(
        "request_time "
        f"p50_seconds={request_time_summary['p50_seconds']:.6f} "
        f"p95_seconds={request_time_summary['p95_seconds']:.6f} "
        f"p99_seconds={request_time_summary['p99_seconds']:.6f} "
        f"max_seconds={request_time_summary['max_seconds']:.6f}"
    )
for path, count in path_counts.most_common(10):
    lines.append(f"top_path count={count} path={path}")
for path, count in fourxx_paths.most_common(8):
    lines.append(f"top_4xx count={count} path={path}")
summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")
PY

perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
