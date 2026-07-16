#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
DURATION_SECONDS="${DURATION_SECONDS:-20}"
CONCURRENCY="${CONCURRENCY:-4}"
API_LOAD_REQUEST_DELAY_SECONDS="${API_LOAD_REQUEST_DELAY_SECONDS:-0}"
REQUEST_TIMEOUT_SECONDS="${REQUEST_TIMEOUT_SECONDS:-10}"
INCLUDE_HEALTH="${INCLUDE_HEALTH:-true}"
API_LOAD_SCENARIOS="${API_LOAD_SCENARIOS:-mixed}"
API_LOAD_ROOT="${API_LOAD_ROOT:-${ROOT_DIR}/tmp/performance/api-load}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${API_LOAD_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
SCENARIOS_TSV="${ARTIFACT_DIR}/api-load-scenarios.tsv"
SAMPLES_TSV="${ARTIFACT_DIR}/api-load-samples.tsv"
SUMMARY_TXT="${ARTIFACT_DIR}/api-load-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/api-load-summary.json"
LATEST_DIR="${API_LOAD_ROOT}/latest"
LATEST_SUMMARY_TXT="${API_LOAD_ROOT}/latest-api-load-summary.txt"
LATEST_SUMMARY_JSON="${API_LOAD_ROOT}/latest-api-load-summary.json"

perf_require_python
mkdir -p "${ARTIFACT_DIR}/responses"
perf_write_run_context "${CONTEXT_TXT}"
echo "api_load_request_delay_seconds=${API_LOAD_REQUEST_DELAY_SECONDS}" >> "${CONTEXT_TXT}"
echo "include_health=${INCLUDE_HEALTH}" >> "${CONTEXT_TXT}"
echo "api_load_scenarios=${API_LOAD_SCENARIOS}" >> "${CONTEXT_TXT}"

if (( DURATION_SECONDS < 1 )); then
  echo "DURATION_SECONDS must be >= 1" >&2
  exit 1
fi
if (( CONCURRENCY < 1 )); then
  echo "CONCURRENCY must be >= 1" >&2
  exit 1
fi

first_policy_response="${ARTIFACT_DIR}/responses/first-policy.json"
first_policy_id=""
if curl -fsS "${APP_BASE_URL}/api/policies?page=0&size=1" -o "${first_policy_response}"; then
  first_policy_id="$(
    python3 - "${first_policy_response}" <<'PY'
import json, sys
try:
    payload = json.load(open(sys.argv[1], encoding="utf-8"))
    content = (((payload.get("data") or {}).get("content")) or [])
    print(content[0]["id"] if content else "")
except Exception:
    print("")
PY
  )"
fi

{
  printf 'scenario\tmethod\tpath\tbody_json\n'
  if [[ "${INCLUDE_HEALTH,,}" == "true" ]]; then
    printf 'health\tGET\t/actuator/health\t\n'
  fi
  printf 'policy_list_default\tGET\t/api/policies?page=0&size=20\t\n'
  printf 'policy_search_keyword\tPOST\t/api/policies/search\t{"keyword":"청년","page":0,"size":20}\n'
  printf 'policy_search_filtered\tPOST\t/api/policies/search\t{"keyword":"청년","page":0,"size":20,"statusFilter":"ACTIVE_ONLY","sort":"DEADLINE"}\n'
  printf 'policy_suggestions\tPOST\t/api/policies/search/suggestions\t{"keyword":"청년","limit":10}\n'
  printf 'policy_ranking\tGET\t/api/policies/ranking?size=20\t\n'
  if [[ -n "${first_policy_id}" ]]; then
    printf 'policy_detail_first\tGET\t/api/policies/%s\t\n' "${first_policy_id}"
  fi
} > "${SCENARIOS_TSV}"

if [[ "${API_LOAD_SCENARIOS}" != "mixed" && "${API_LOAD_SCENARIOS}" != "all" ]]; then
  python3 - "${SCENARIOS_TSV}" "${API_LOAD_SCENARIOS}" <<'PY'
import csv
import sys
from pathlib import Path

path = Path(sys.argv[1])
selected = {item.strip() for item in sys.argv[2].split(",") if item.strip()}
rows = list(csv.DictReader(path.open(encoding="utf-8"), delimiter="\t"))
kept = [row for row in rows if row["scenario"] in selected]
missing = sorted(selected - {row["scenario"] for row in rows})
if not kept:
    raise SystemExit(f"no selected scenarios found; selected={sorted(selected)} missing={missing}")
with path.open("w", encoding="utf-8", newline="") as fp:
    writer = csv.DictWriter(fp, fieldnames=["scenario", "method", "path", "body_json"], delimiter="\t")
    writer.writeheader()
    writer.writerows(kept)
if missing:
    print(f"api_load_missing_scenarios={','.join(missing)}")
PY
fi

python3 - \
  "${APP_BASE_URL}" \
  "${DURATION_SECONDS}" \
  "${CONCURRENCY}" \
  "${API_LOAD_REQUEST_DELAY_SECONDS}" \
  "${REQUEST_TIMEOUT_SECONDS}" \
  "${SCENARIOS_TSV}" \
  "${SAMPLES_TSV}" \
  "${SUMMARY_TXT}" \
  "${SUMMARY_JSON}" \
  "${CONTEXT_TXT}" <<'PY'
import csv
import json
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

base_url, duration_s, concurrency, request_delay_s, timeout_s, scenarios_path, samples_path, summary_txt, summary_json, context_path = sys.argv[1:11]
duration_s = int(duration_s)
concurrency = int(concurrency)
request_delay_s = float(request_delay_s)
timeout_s = int(timeout_s)
scenarios = list(csv.DictReader(open(scenarios_path, encoding="utf-8"), delimiter="\t"))
if not scenarios:
    raise SystemExit("no scenarios")

samples_file = open(samples_path, "w", encoding="utf-8", newline="")
samples_writer = csv.writer(samples_file, delimiter="\t")
samples_writer.writerow(["scenario", "method", "path", "worker", "run_index", "http_code", "duration_ms", "bytes", "error"])
write_lock = threading.Lock()
stop_at = time.monotonic() + duration_s

def worker(worker_id):
    run_index = 0
    while time.monotonic() < stop_at:
        scenario = scenarios[(worker_id + run_index) % len(scenarios)]
        run_index += 1
        url = base_url.rstrip("/") + scenario["path"]
        started = time.perf_counter()
        code = "000"
        size = 0
        error = ""
        try:
            body_json = scenario.get("body_json") or ""
            data = body_json.encode("utf-8") if body_json else None
            headers = {"Content-Type": "application/json"} if body_json else {}
            request = urllib.request.Request(url, data=data, headers=headers, method=scenario["method"])
            with urllib.request.urlopen(request, timeout=timeout_s) as response:
                body = response.read()
                code = str(response.status)
                size = len(body)
        except urllib.error.HTTPError as exc:
            code = str(exc.code)
            try:
                size = len(exc.read())
            except Exception:
                size = 0
            error = exc.__class__.__name__
        except Exception as exc:
            error = exc.__class__.__name__
        duration_ms = (time.perf_counter() - started) * 1000
        with write_lock:
            samples_writer.writerow([
                scenario["scenario"], scenario["method"], scenario["path"], worker_id, run_index,
                code, f"{duration_ms:.3f}", size, error,
            ])
        if request_delay_s > 0:
            time.sleep(request_delay_s)

threads = [threading.Thread(target=worker, args=(i,), daemon=True) for i in range(concurrency)]
started_wall = time.perf_counter()
for thread in threads:
    thread.start()
for thread in threads:
    thread.join()
elapsed = time.perf_counter() - started_wall
samples_file.close()

rows = list(csv.DictReader(open(samples_path, encoding="utf-8"), delimiter="\t"))

def pct(values, p):
    if not values:
        return 0.0
    values = sorted(values)
    k = (len(values) - 1) * p
    f = int(k)
    c = min(f + 1, len(values) - 1)
    if f == c:
        return values[f]
    return values[f] + (values[c] - values[f]) * (k - f)

summary_rows = []
for scenario in sorted({row["scenario"] for row in rows}):
    group = [row for row in rows if row["scenario"] == scenario]
    latencies = [float(row["duration_ms"]) for row in group]
    errors = [row for row in group if not (row["http_code"].isdigit() and 200 <= int(row["http_code"]) < 400)]
    rate_limited = [row for row in group if row["http_code"] == "429"]
    summary_rows.append({
        "scenario": scenario,
        "requests": len(group),
        "success_count": len(group) - len(errors),
        "error_count": len(errors),
        "rate_limited_count": len(rate_limited),
        "error_rate": len(errors) / len(group) if group else 0,
        "throughput_rps": len(group) / elapsed if elapsed > 0 else 0,
        "p50_ms": pct(latencies, 0.50),
        "p90_ms": pct(latencies, 0.90),
        "p95_ms": pct(latencies, 0.95),
        "p99_ms": pct(latencies, 0.99),
        "max_ms": max(latencies) if latencies else 0,
        "avg_ms": statistics.mean(latencies) if latencies else 0,
    })
summary_rows.sort(key=lambda item: item["p95_ms"], reverse=True)

context = {}
for line in Path(context_path).read_text(encoding="utf-8").splitlines():
    if "=" in line:
        key, value = line.split("=", 1)
        context[key] = value
context.update({
    "duration_seconds": duration_s,
    "concurrency": concurrency,
    "request_delay_seconds": request_delay_s,
    "actual_elapsed_seconds": round(elapsed, 3),
})

payload = {
    "context": context,
    "total_requests": len(rows),
    "total_throughput_rps": len(rows) / elapsed if elapsed > 0 else 0,
    "summary": summary_rows,
}
Path(summary_json).write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

failed = any(item["error_count"] for item in summary_rows)
lines = [
    f"api_load_baseline={'failed' if failed else 'passed'}",
    f"duration_seconds={duration_s}",
    f"concurrency={concurrency}",
    f"request_delay_seconds={request_delay_s}",
    f"total_requests={len(rows)}",
    f"total_throughput_rps={payload['total_throughput_rps']:.3f}",
]
for item in summary_rows:
    lines.append(
        f"{item['scenario']} requests={item['requests']} rps={item['throughput_rps']:.3f} "
        f"p50_ms={item['p50_ms']:.1f} p95_ms={item['p95_ms']:.1f} "
        f"p99_ms={item['p99_ms']:.1f} max_ms={item['max_ms']:.1f} "
        f"errors={item['error_count']}/{item['requests']} rate_limited={item['rate_limited_count']}"
    )
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
