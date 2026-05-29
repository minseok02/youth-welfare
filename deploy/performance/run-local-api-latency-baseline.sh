#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
RUNS="${RUNS:-7}"
WARMUP_RUNS="${WARMUP_RUNS:-1}"
API_LATENCY_ROOT="${API_LATENCY_ROOT:-${ROOT_DIR}/tmp/performance/api-latency}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${API_LATENCY_ROOT}/${RUN_TS_UTC}}"
SAMPLES_TSV="${ARTIFACT_DIR}/api-latency-samples.tsv"
SUMMARY_TSV="${ARTIFACT_DIR}/api-latency-summary.tsv"
SUMMARY_JSON="${ARTIFACT_DIR}/api-latency-summary.json"
SUMMARY_TXT="${ARTIFACT_DIR}/api-latency-summary.txt"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
LATEST_DIR="${API_LATENCY_ROOT}/latest"
LATEST_SUMMARY_TXT="${API_LATENCY_ROOT}/latest-api-latency-summary.txt"
LATEST_SUMMARY_JSON="${API_LATENCY_ROOT}/latest-api-latency-summary.json"

smoke_require_command curl
perf_require_python
mkdir -p "${ARTIFACT_DIR}/responses"
perf_write_run_context "${CONTEXT_TXT}"

if (( RUNS < 1 )); then
  echo "RUNS must be >= 1" >&2
  exit 1
fi

admin_token=""
smoke_resolve_admin_credentials "${ROOT_DIR}"
if [[ -n "${ADMIN_EMAIL:-}" && -n "${ADMIN_PASSWORD:-}" ]]; then
  login_response="${ARTIFACT_DIR}/responses/admin-login.json"
  login_payload_file="$(mktemp)"
  python3 - "${ADMIN_EMAIL}" "${ADMIN_PASSWORD}" "${login_payload_file}" <<'PY'
import json, sys
email, password, path = sys.argv[1:4]
with open(path, "w", encoding="utf-8") as fp:
    json.dump({"email": email, "password": password}, fp)
PY
  set +e
  login_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${login_response}" \
      -H 'Content-Type: application/json' \
      --data-binary "@${login_payload_file}"
  )"
  login_exit=$?
  set -e
  rm -f "${login_payload_file}"
  if (( login_exit != 0 )); then
    login_status=""
  fi
  if [[ "${login_status}" == "200" ]]; then
    admin_token="$(
      python3 - "${login_response}" <<'PY'
import json, sys
payload = json.load(open(sys.argv[1], encoding="utf-8"))
print((payload.get("data") or {}).get("accessToken") or "")
PY
    )"
  fi
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

declare -a ENDPOINTS=(
  "health|GET|/actuator/health|none"
  "policy_list_default|GET|/api/policies?page=0&size=20|none"
  "policy_list_status_open|GET|/api/policies?page=0&size=20&statusFilter=open|none"
  "policy_search_keyword|GET|/api/policies/search?keyword=%EC%B2%AD%EB%85%84&page=0&size=20|none"
  "policy_search_filtered|GET|/api/policies/search?keyword=%EC%B2%AD%EB%85%84&page=0&size=20&statusFilter=open&sort=deadline|none"
  "policy_suggestions|GET|/api/policies/search/suggestions?keyword=%EC%B2%AD%EB%85%84&limit=10|none"
  "policy_trending|GET|/api/policies/search/trending?limit=10|none"
  "policy_ranking|GET|/api/policies/ranking?size=20|none"
)

if [[ -n "${first_policy_id}" ]]; then
  ENDPOINTS+=("policy_detail_first|GET|/api/policies/${first_policy_id}|none")
fi

if [[ -n "${admin_token}" ]]; then
  ENDPOINTS+=(
    "admin_dashboard_summary|GET|/api/admin/dashboard/summary?summaryWindowDays=14&trendWindowDays=1&trendWindowDays=7&trendWindowDays=30|admin"
    "admin_collect_failures|GET|/api/admin/dashboard/collect-failures?summaryWindowDays=14&limit=5|admin"
    "admin_recommendation_breakdowns|GET|/api/admin/dashboard/recommendation-breakdowns?summaryWindowDays=14&limit=5|admin"
  )
fi

printf 'scenario\tmethod\tpath\tauth\trun_index\tphase\thttp_code\ttime_total_ms\ttime_connect_ms\ttime_starttransfer_ms\tsize_download_bytes\tresponse_file\n' > "${SAMPLES_TSV}"

measure_endpoint() {
  local scenario="$1"
  local method="$2"
  local path="$3"
  local auth="$4"
  local run_index="$5"
  local phase="$6"
  local response_file="${ARTIFACT_DIR}/responses/${scenario}-${phase}-${run_index}.body"
  local curl_out
  local curl_exit
  local auth_args=()

  if [[ "${auth}" == "admin" ]]; then
    auth_args=(-H "Authorization: Bearer ${admin_token}")
  fi

  set +e
  curl_out="$(
    curl -sS -o "${response_file}" \
      -w "%{http_code}\t%{time_total}\t%{time_connect}\t%{time_starttransfer}\t%{size_download}" \
      -X "${method}" "${APP_BASE_URL}${path}" "${auth_args[@]}"
  )"
  curl_exit=$?
  set -e
  if (( curl_exit != 0 )); then
    curl_out=$'000\t0\t0\t0\t0'
  fi

  python3 - "${scenario}" "${method}" "${path}" "${auth}" "${run_index}" "${phase}" "${response_file}" "${curl_out}" >> "${SAMPLES_TSV}" <<'PY'
import sys
scenario, method, path, auth, run_index, phase, response_file, raw = sys.argv[1:9]
parts = raw.split("\t")
code = parts[0] if len(parts) > 0 else "000"
total = float(parts[1]) * 1000 if len(parts) > 1 else 0.0
connect = float(parts[2]) * 1000 if len(parts) > 2 else 0.0
starttransfer = float(parts[3]) * 1000 if len(parts) > 3 else 0.0
size = parts[4] if len(parts) > 4 else "0"
print("\t".join([
    scenario, method, path, auth, run_index, phase, code,
    f"{total:.3f}", f"{connect:.3f}", f"{starttransfer:.3f}", size, response_file
]))
PY
}

for endpoint in "${ENDPOINTS[@]}"; do
  IFS='|' read -r scenario method path auth <<< "${endpoint}"
  for ((i = 1; i <= WARMUP_RUNS; i++)); do
    measure_endpoint "${scenario}" "${method}" "${path}" "${auth}" "${i}" "warmup"
  done
  for ((i = 1; i <= RUNS; i++)); do
    measure_endpoint "${scenario}" "${method}" "${path}" "${auth}" "${i}" "measure"
  done
done

python3 - "${SAMPLES_TSV}" "${SUMMARY_TSV}" "${SUMMARY_JSON}" "${SUMMARY_TXT}" "${CONTEXT_TXT}" <<'PY'
import csv, json, statistics, sys
from pathlib import Path

samples_path, summary_tsv_path, summary_json_path, summary_txt_path, context_path = map(Path, sys.argv[1:6])
rows = list(csv.DictReader(samples_path.open(encoding="utf-8"), delimiter="\t"))
measured = [r for r in rows if r["phase"] == "measure"]

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

groups = {}
for row in measured:
    groups.setdefault(row["scenario"], []).append(row)

summary = []
for scenario, group in groups.items():
    latencies = [float(r["time_total_ms"]) for r in group]
    errors = [r for r in group if not (200 <= int(r["http_code"]) < 400)]
    summary.append({
        "scenario": scenario,
        "method": group[0]["method"],
        "path": group[0]["path"],
        "auth": group[0]["auth"],
        "runs": len(group),
        "success_count": len(group) - len(errors),
        "error_count": len(errors),
        "error_rate": len(errors) / len(group) if group else 0,
        "p50_ms": pct(latencies, 0.50),
        "p90_ms": pct(latencies, 0.90),
        "p95_ms": pct(latencies, 0.95),
        "p99_ms": pct(latencies, 0.99),
        "max_ms": max(latencies) if latencies else 0.0,
        "avg_ms": statistics.mean(latencies) if latencies else 0.0,
    })

summary.sort(key=lambda item: item["p95_ms"], reverse=True)

with summary_tsv_path.open("w", encoding="utf-8") as fp:
    headers = ["scenario", "runs", "success_count", "error_count", "error_rate", "p50_ms", "p90_ms", "p95_ms", "p99_ms", "max_ms", "avg_ms", "method", "path", "auth"]
    fp.write("\t".join(headers) + "\n")
    for item in summary:
        fp.write("\t".join(str(round(item[h], 3)) if isinstance(item[h], float) else str(item[h]) for h in headers) + "\n")

context = {}
for line in context_path.read_text(encoding="utf-8").splitlines():
    if "=" in line:
        k, v = line.split("=", 1)
        context[k] = v

summary_json_path.write_text(json.dumps({"context": context, "summary": summary}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = ["api_latency_baseline=passed", f"sample_count={len(measured)}"]
for item in summary[:20]:
    lines.append(
        f"{item['scenario']} p50_ms={item['p50_ms']:.1f} p95_ms={item['p95_ms']:.1f} "
        f"p99_ms={item['p99_ms']:.1f} max_ms={item['max_ms']:.1f} errors={item['error_count']}/{item['runs']}"
    )
summary_txt_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt_path.read_text(encoding="utf-8"), end="")
PY

perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
