#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

EXTERNAL_BASE_URL="${EXTERNAL_BASE_URL:-https://youthmoa.kr}"
NGINX_ACCESS_LOG="${NGINX_ACCESS_LOG:-/var/log/nginx/access.log}"
NGINX_ERROR_LOG="${NGINX_ERROR_LOG:-/var/log/nginx/error.log}"
EDGE_ROOT="${EDGE_ROOT:-${ROOT_DIR}/tmp/performance/edge}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${EDGE_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
CURL_SAMPLES_TSV="${ARTIFACT_DIR}/edge-curl-samples.tsv"
HEADERS_TXT="${ARTIFACT_DIR}/edge-headers.txt"
ACCESS_LOG_SAMPLE="${ARTIFACT_DIR}/nginx-access-tail.log"
ERROR_LOG_SAMPLE="${ARTIFACT_DIR}/nginx-error-tail.log"
ACCESS_SUMMARY_TXT="${ARTIFACT_DIR}/nginx-access-summary.txt"
SUMMARY_TXT="${ARTIFACT_DIR}/edge-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/edge-summary.json"
LATEST_DIR="${EDGE_ROOT}/latest"
LATEST_SUMMARY_TXT="${EDGE_ROOT}/latest-edge-summary.txt"
LATEST_SUMMARY_JSON="${EDGE_ROOT}/latest-edge-summary.json"

smoke_require_command curl
perf_require_python
mkdir -p "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"

paths=(
  "/"
  "/policies"
  "/api/policies?page=0&size=20"
  "/api/policies/search?keyword=%EC%B2%AD%EB%85%84&page=0&size=20"
)

printf 'path\thttp_code\ttime_namelookup_ms\ttime_connect_ms\ttime_appconnect_ms\ttime_starttransfer_ms\ttime_total_ms\tsize_download_bytes\n' > "${CURL_SAMPLES_TSV}"
for path in "${paths[@]}"; do
  curl_out="$(
    curl -sS -o /dev/null \
      -w "%{http_code}\t%{time_namelookup}\t%{time_connect}\t%{time_appconnect}\t%{time_starttransfer}\t%{time_total}\t%{size_download}" \
      "${EXTERNAL_BASE_URL}${path}" || printf '000\t0\t0\t0\t0\t0\t0'
  )"
  python3 - "${path}" "${curl_out}" >> "${CURL_SAMPLES_TSV}" <<'PY'
import sys
path, raw = sys.argv[1:3]
parts = raw.split("\t")
while len(parts) < 7:
    parts.append("0")
code, namelookup, connect, appconnect, starttransfer, total, size = parts[:7]
print("\t".join([
    path,
    code,
    f"{float(namelookup) * 1000:.3f}",
    f"{float(connect) * 1000:.3f}",
    f"{float(appconnect) * 1000:.3f}",
    f"{float(starttransfer) * 1000:.3f}",
    f"{float(total) * 1000:.3f}",
    size,
]))
PY
done

curl -fsSI "${EXTERNAL_BASE_URL}/" > "${HEADERS_TXT}" || true

if [[ -r "${NGINX_ACCESS_LOG}" ]]; then
  tail -n "${NGINX_LOG_TAIL_LINES:-2000}" "${NGINX_ACCESS_LOG}" > "${ACCESS_LOG_SAMPLE}" || true
else
  : > "${ACCESS_LOG_SAMPLE}"
fi

if [[ -r "${NGINX_ERROR_LOG}" ]]; then
  tail -n "${NGINX_ERROR_LOG_TAIL_LINES:-200}" "${NGINX_ERROR_LOG}" > "${ERROR_LOG_SAMPLE}" || true
else
  : > "${ERROR_LOG_SAMPLE}"
fi

python3 - \
  "${CURL_SAMPLES_TSV}" \
  "${HEADERS_TXT}" \
  "${ACCESS_LOG_SAMPLE}" \
  "${ERROR_LOG_SAMPLE}" \
  "${ACCESS_SUMMARY_TXT}" \
  "${SUMMARY_TXT}" \
  "${SUMMARY_JSON}" \
  "${CONTEXT_TXT}" \
  "${EXTERNAL_BASE_URL}" <<'PY'
import collections
import csv
import json
import re
import sys
from pathlib import Path

curl_path, headers_path, access_path, error_path, access_summary_path, summary_txt, summary_json, context_path, base_url = sys.argv[1:10]
curl_rows = list(csv.DictReader(open(curl_path, encoding="utf-8"), delimiter="\t"))
headers_text = Path(headers_path).read_text(encoding="utf-8", errors="replace")
access_text = Path(access_path).read_text(encoding="utf-8", errors="replace")
error_text = Path(error_path).read_text(encoding="utf-8", errors="replace")

required_headers = {
    "strict-transport-security": "hsts",
    "x-frame-options": "x_frame_options",
    "x-content-type-options": "x_content_type_options",
    "content-security-policy": "csp",
}
header_map = {}
for line in headers_text.splitlines():
    if ":" in line:
        key, value = line.split(":", 1)
        header_map[key.strip().lower()] = value.strip()

status_counts = collections.Counter()
method_path_counts = collections.Counter()
for line in access_text.splitlines():
    m = re.search(r'"([A-Z]+) ([^ ]+) HTTP/[^"]+" ([0-9]{3}) ', line)
    if not m:
        continue
    method, path, status = m.groups()
    status_counts[status] += 1
    method_path_counts[f"{method} {path.split('?', 1)[0]}"] += 1

access_summary_lines = ["status_counts"]
for status, count in sorted(status_counts.items()):
    access_summary_lines.append(f"{status}\t{count}")
access_summary_lines.append("top_paths")
for path, count in method_path_counts.most_common(20):
    access_summary_lines.append(f"{path}\t{count}")
Path(access_summary_path).write_text("\n".join(access_summary_lines) + "\n", encoding="utf-8")

context = {}
for line in Path(context_path).read_text(encoding="utf-8").splitlines():
    if "=" in line:
        key, value = line.split("=", 1)
        context[key] = value

header_presence = {label: key in header_map for key, label in required_headers.items()}
server_header = header_map.get("server")
server_tokens_hidden = server_header is None or "nginx/" not in server_header.lower()
curl_failed = [row for row in curl_rows if not (row["http_code"].isdigit() and 200 <= int(row["http_code"]) < 400)]
error_log_recent_lines = len([line for line in error_text.splitlines() if line.strip()])

payload = {
    "context": context,
    "external_base_url": base_url,
    "curl_samples": curl_rows,
    "headers": header_map,
    "header_presence": header_presence,
    "server_tokens_hidden": server_tokens_hidden,
    "nginx_access_status_counts": dict(status_counts),
    "nginx_access_top_paths": method_path_counts.most_common(20),
    "nginx_error_log_recent_lines": error_log_recent_lines,
    "nginx_access_summary_path": str(access_summary_path),
}
Path(summary_json).write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

failed = bool(curl_failed)
lines = [f"edge_baseline={'failed' if failed else 'passed'}"]
for row in curl_rows:
    lines.append(
        f"{row['path']} status={row['http_code']} total_ms={row['time_total_ms']} "
        f"ttfb_ms={row['time_starttransfer_ms']} tls_ms={row['time_appconnect_ms']} size={row['size_download_bytes']}"
    )
for label, present in header_presence.items():
    lines.append(f"{label}={str(present).lower()}")
lines.append(f"server_tokens_hidden={str(server_tokens_hidden).lower()}")
lines.append(f"nginx_access_sample_lines={len(access_text.splitlines())}")
lines.append(f"nginx_error_log_recent_lines={error_log_recent_lines}")
for status, count in sorted(status_counts.items()):
    lines.append(f"nginx_status_{status}={count}")
Path(summary_txt).write_text("\n".join(lines) + "\n", encoding="utf-8")
print(Path(summary_txt).read_text(encoding="utf-8"), end="")
if failed:
    raise SystemExit(1)
PY

perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
