#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_BASE_URL="${APP_BASE_URL%/}"
COLD_RUNS="${COLD_RUNS:-3}"
WARM_RUNS="${WARM_RUNS:-7}"
CACHE_TTL_WAIT_SECONDS="${CACHE_TTL_WAIT_SECONDS:-31}"
WARM_DELAY_SECONDS="${WARM_DELAY_SECONDS:-0.2}"
SEARCH_KEYWORD="${SEARCH_KEYWORD:-청년}"
SEARCH_SIZE="${SEARCH_SIZE:-20}"
RANKING_SIZE="${RANKING_SIZE:-20}"
POLICY_CACHE_TAIL_ROOT="${POLICY_CACHE_TAIL_ROOT:-${ROOT_DIR}/tmp/performance/policy-cache-tail}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${POLICY_CACHE_TAIL_ROOT}/${RUN_TS_UTC}}"
RESPONSES_DIR="${ARTIFACT_DIR}/responses"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
SAMPLES_TSV="${ARTIFACT_DIR}/policy-cache-tail-samples.tsv"
SUMMARY_TXT="${ARTIFACT_DIR}/policy-cache-tail-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/policy-cache-tail-summary.json"
LATEST_DIR="${POLICY_CACHE_TAIL_ROOT}/latest"
LATEST_SUMMARY_TXT="${POLICY_CACHE_TAIL_ROOT}/latest-policy-cache-tail-summary.txt"
LATEST_SUMMARY_JSON="${POLICY_CACHE_TAIL_ROOT}/latest-policy-cache-tail-summary.json"

smoke_require_command curl
perf_require_python

if (( COLD_RUNS < 1 )); then
  echo "COLD_RUNS must be >= 1" >&2
  exit 1
fi
if (( WARM_RUNS < 1 )); then
  echo "WARM_RUNS must be >= 1" >&2
  exit 1
fi

mkdir -p "${RESPONSES_DIR}"
perf_sanitize_artifacts_on_exit "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"
{
  echo "cold_runs=${COLD_RUNS}"
  echo "warm_runs=${WARM_RUNS}"
  echo "cache_ttl_wait_seconds=${CACHE_TTL_WAIT_SECONDS}"
  echo "warm_delay_seconds=${WARM_DELAY_SECONDS}"
  echo "search_keyword=${SEARCH_KEYWORD}"
  echo "search_size=${SEARCH_SIZE}"
  echo "ranking_size=${RANKING_SIZE}"
} >> "${CONTEXT_TXT}"

printf 'phase\tscenario\tsample\tstatus\ttotal_ms\tttfb_ms\tsize_bytes\titem_count\tfirst_id\tfirst_title\tresponse_path\n' > "${SAMPLES_TSV}"

parse_response() {
  local scenario="$1"
  local response_file="$2"
  python3 - "${scenario}" "${response_file}" <<'PY'
import json
import sys
from pathlib import Path

scenario, path = sys.argv[1:3]
try:
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
except Exception:
    print("0\t\t")
    raise SystemExit(0)

data = payload.get("data", payload)
if scenario == "search_keyword":
    items = data.get("content") if isinstance(data, dict) else []
elif scenario == "ranking":
    items = data if isinstance(data, list) else []
else:
    items = []

items = items or []
first = items[0] if items else {}
first_id = first.get("id") or first.get("serviceId") or ""
first_title = str(first.get("title") or "").replace("\t", " ").replace("\n", " ")
print(f"{len(items)}\t{first_id}\t{first_title}")
PY
}

measure_request() {
  local phase="$1"
  local scenario="$2"
  local sample="$3"
  local method="$4"
  local path="$5"
  local payload="${6:-}"
  local response_file="${RESPONSES_DIR}/${phase}-${scenario}-${sample}.json"
  local curl_meta status total_seconds ttfb_seconds size_bytes parsed item_count first_id first_title

  if [[ "${method}" == "POST" ]]; then
    curl_meta="$(
      curl -k -sS -o "${response_file}" \
        -w '%{http_code}\t%{time_total}\t%{time_starttransfer}\t%{size_download}' \
        -H 'Content-Type: application/json' \
        -X POST \
        --data-binary "${payload}" \
        "${APP_BASE_URL}${path}"
    )"
  else
    curl_meta="$(
      curl -k -sS -o "${response_file}" \
        -w '%{http_code}\t%{time_total}\t%{time_starttransfer}\t%{size_download}' \
        "${APP_BASE_URL}${path}"
    )"
  fi

  IFS=$'\t' read -r status total_seconds ttfb_seconds size_bytes <<< "${curl_meta}"
  parsed="$(parse_response "${scenario}" "${response_file}")"
  IFS=$'\t' read -r item_count first_id first_title <<< "${parsed}"
  python3 - "${phase}" "${scenario}" "${sample}" "${status}" "${total_seconds}" "${ttfb_seconds}" "${size_bytes}" "${item_count}" "${first_id}" "${first_title}" "${response_file}" >> "${SAMPLES_TSV}" <<'PY'
import sys

phase, scenario, sample, status, total_seconds, ttfb_seconds, size_bytes, item_count, first_id, first_title, response_file = sys.argv[1:12]
total_ms = float(total_seconds) * 1000.0
ttfb_ms = float(ttfb_seconds) * 1000.0
safe_title = first_title.replace("\t", " ").replace("\n", " ")
print("\t".join([
    phase,
    scenario,
    sample,
    status,
    f"{total_ms:.3f}",
    f"{ttfb_ms:.3f}",
    size_bytes,
    item_count,
    first_id,
    safe_title,
    response_file,
]))
PY
}

search_payload() {
  python3 - "${SEARCH_KEYWORD}" "${SEARCH_SIZE}" <<'PY'
import json
import sys

keyword, size = sys.argv[1:3]
print(json.dumps({"keyword": keyword, "page": 0, "size": int(size)}, ensure_ascii=False))
PY
}

SEARCH_PAYLOAD="$(search_payload)"

for sample in $(seq 1 "${COLD_RUNS}"); do
  sleep "${CACHE_TTL_WAIT_SECONDS}"
  measure_request "cold" "search_keyword" "${sample}" "POST" "/api/policies/search" "${SEARCH_PAYLOAD}"
  measure_request "cold" "ranking" "${sample}" "GET" "/api/policies/ranking?size=${RANKING_SIZE}"
done

measure_request "warmup" "search_keyword" "1" "POST" "/api/policies/search" "${SEARCH_PAYLOAD}"
measure_request "warmup" "ranking" "1" "GET" "/api/policies/ranking?size=${RANKING_SIZE}"

for sample in $(seq 1 "${WARM_RUNS}"); do
  measure_request "warm" "search_keyword" "${sample}" "POST" "/api/policies/search" "${SEARCH_PAYLOAD}"
  measure_request "warm" "ranking" "${sample}" "GET" "/api/policies/ranking?size=${RANKING_SIZE}"
  sleep "${WARM_DELAY_SECONDS}"
done

python3 - "${SAMPLES_TSV}" "${SUMMARY_TXT}" "${SUMMARY_JSON}" "${CONTEXT_TXT}" <<'PY'
import csv
import json
import statistics
import sys
from collections import defaultdict
from pathlib import Path

samples_path, summary_txt, summary_json, context_path = map(Path, sys.argv[1:5])

rows = list(csv.DictReader(samples_path.open(encoding="utf-8"), delimiter="\t"))
context = {}
for line in context_path.read_text(encoding="utf-8").splitlines():
    if "=" in line:
        k, v = line.split("=", 1)
        context[k] = v

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

groups = defaultdict(list)
for row in rows:
    groups[(row["phase"], row["scenario"])].append(row)

summary_rows = []
for (phase, scenario), group_rows in sorted(groups.items()):
    measured = [float(row["total_ms"]) for row in group_rows if row["status"] == "200"]
    ttfb = [float(row["ttfb_ms"]) for row in group_rows if row["status"] == "200"]
    errors = [row for row in group_rows if row["status"] != "200"]
    item_counts = [int(row["item_count"]) for row in group_rows if row["item_count"].isdigit()]
    summary_rows.append({
        "phase": phase,
        "scenario": scenario,
        "runs": len(group_rows),
        "success_count": len(measured),
        "error_count": len(errors),
        "p50_ms": pct(measured, 0.50),
        "p95_ms": pct(measured, 0.95),
        "p99_ms": pct(measured, 0.99),
        "max_ms": max(measured) if measured else None,
        "avg_ms": statistics.mean(measured) if measured else None,
        "ttfb_p95_ms": pct(ttfb, 0.95),
        "min_item_count": min(item_counts) if item_counts else None,
        "max_item_count": max(item_counts) if item_counts else None,
    })

payload = {
    "context": context,
    "samples": rows,
    "summary": summary_rows,
}
summary_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

decision = "passed" if all(item["error_count"] == 0 for item in summary_rows) else "failed"
lines = [f"policy_cache_tail_baseline={decision}"]
for item in summary_rows:
    def fmt(value):
        return "null" if value is None else f"{value:.1f}"
    lines.append(
        f"{item['phase']} {item['scenario']} "
        f"p50_ms={fmt(item['p50_ms'])} "
        f"p95_ms={fmt(item['p95_ms'])} "
        f"p99_ms={fmt(item['p99_ms'])} "
        f"max_ms={fmt(item['max_ms'])} "
        f"success={item['success_count']}/{item['runs']} "
        f"items={item['min_item_count']}..{item['max_item_count']}"
    )

summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")
if decision != "passed":
    raise SystemExit(1)
PY

perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
