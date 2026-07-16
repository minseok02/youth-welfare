#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
RECOMMEND_FLOW_ROOT="${RECOMMEND_FLOW_ROOT:-${ROOT_DIR}/tmp/performance/recommendation-flow}"
RECOMMEND_RUN_PERSONAL="${RECOMMEND_RUN_PERSONAL:-true}"
RECOMMEND_RUN_SIMILAR_USERS_VIEWED="${RECOMMEND_RUN_SIMILAR_USERS_VIEWED:-false}"
RECOMMEND_REQUEST_DELAY_SECONDS="${RECOMMEND_REQUEST_DELAY_SECONDS:-1.0}"
REQUEST_TIMEOUT_SECONDS="${REQUEST_TIMEOUT_SECONDS:-90}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-recommend.flow.perf}"
SMOKE_NAME="${SMOKE_NAME:-추천측정}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
SMOKE_TRUSTED_ORIGIN="${SMOKE_TRUSTED_ORIGIN:-https://youthmoa.kr}"
SMOKE_TRUSTED_REFERER="${SMOKE_TRUSTED_REFERER:-${SMOKE_TRUSTED_ORIGIN}/}"

RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${RECOMMEND_FLOW_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
SETUP_TSV="${ARTIFACT_DIR}/recommendation-flow-setup.tsv"
SAMPLES_TSV="${ARTIFACT_DIR}/recommendation-flow-samples.tsv"
DB_COUNTS_TSV="${ARTIFACT_DIR}/recommendation-flow-db-counts.tsv"
SUMMARY_TXT="${ARTIFACT_DIR}/recommendation-flow-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/recommendation-flow-summary.json"
LATEST_DIR="${RECOMMEND_FLOW_ROOT}/latest"
LATEST_SUMMARY_TXT="${RECOMMEND_FLOW_ROOT}/latest-recommendation-flow-summary.txt"
LATEST_SUMMARY_JSON="${RECOMMEND_FLOW_ROOT}/latest-recommendation-flow-summary.json"

perf_require_python
smoke_require_command curl
RECOMMEND_RUN_PERSONAL="$(smoke_normalize_bool "${RECOMMEND_RUN_PERSONAL}")"
RECOMMEND_RUN_SIMILAR_USERS_VIEWED="$(smoke_normalize_bool "${RECOMMEND_RUN_SIMILAR_USERS_VIEWED}")"
mkdir -p "${ARTIFACT_DIR}/setup" "${ARTIFACT_DIR}/responses"
perf_sanitize_artifacts_on_exit "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"
{
  echo "recommend_run_personal=${RECOMMEND_RUN_PERSONAL}"
  echo "recommend_run_similar_users_viewed=${RECOMMEND_RUN_SIMILAR_USERS_VIEWED}"
  echo "recommend_request_delay_seconds=${RECOMMEND_REQUEST_DELAY_SECONDS}"
  echo "request_timeout_seconds=${REQUEST_TIMEOUT_SECONDS}"
  echo "smoke_trusted_origin=${SMOKE_TRUSTED_ORIGIN}"
  echo "measured_steps=stored_read_before,shared_refresh,stored_read_after,shared_refresh_cached,personal_refresh,stored_read_final"
  echo "excluded_steps=bookmark,chat,email-send,password-reset"
} >> "${CONTEXT_TXT}"

HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
HEALTH_STATUS="$(smoke_wait_for_health 15 1 "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

db_count_snapshot() {
  local label="$1"
  set +e
  local output
  output="$(
    SMOKE_DB_MODE="${SMOKE_DB_MODE:-postgres}" smoke_db_query "
      select 'user_recommendations', count(*)::text from user_recommendations
      union all
      select 'recommendation_logs', count(*)::text from recommendation_logs
      union all
      select 'recommendation_run_logs', count(*)::text from recommendation_run_logs;
    " 2>/dev/null
  )"
  local status=$?
  set -e
  if (( status != 0 )); then
    printf '%s\t%s\t%s\n' "${label}" "unavailable" "unavailable" >> "${DB_COUNTS_TSV}"
    return 0
  fi
  while IFS=$'\t' read -r table_name row_count; do
    [[ -n "${table_name}" ]] || continue
    printf '%s\t%s\t%s\n' "${label}" "${table_name}" "${row_count}" >> "${DB_COUNTS_TSV}"
  done <<< "${output}"
}

printf 'snapshot\ttable_name\trow_count\n' > "${DB_COUNTS_TSV}"
db_count_snapshot "before"

email_prefix="${SMOKE_EMAIL_PREFIX}.${RUN_TS_UTC,,}"
email_domain="${SMOKE_EMAIL_DOMAIN:-example.com}"
email="${email_prefix}.1@${email_domain}"
signup_response="${ARTIFACT_DIR}/setup/signup.json"
signup_body="${ARTIFACT_DIR}/setup/signup.body.json"
login_response="${ARTIFACT_DIR}/setup/login.json"
cookie_jar="${ARTIFACT_DIR}/setup/recommendation-flow.cookie"

printf 'step\tstatus\tduration_ms\n' > "${SETUP_TSV}"
smoke_seed_verified_email "${email}"
AUTH_FLOW_SIGNUP_PASSWORD="${SMOKE_PASSWORD}" python3 - "${email}" "${SMOKE_NAME}" "${SMOKE_BIRTH_DATE}" "${SMOKE_SIDO}" "${SMOKE_SGG}" "${SMOKE_INCOME_LEVEL}" "${SMOKE_EMPLOYMENT_STATUS}" "${SMOKE_HOUSEHOLD_TYPE}" "${signup_body}" <<'PY'
import json
import os
import sys

email, name, birth_date, sido, sgg, income_level, employment_status, household_type, output_path = sys.argv[1:10]
payload = {
    "email": email,
    "password": os.environ["AUTH_FLOW_SIGNUP_PASSWORD"],
    "name": name,
    "birthDate": birth_date,
    "privacyNoticeConfirmed": True,
    "optionalProfileConsentAgreed": True,
    "sido": sido,
    "sgg": sgg,
    "incomeLevel": int(income_level),
    "employmentStatus": employment_status,
    "householdType": household_type,
}
with open(output_path, "w", encoding="utf-8") as fp:
    json.dump(payload, fp, ensure_ascii=False)
PY

start_ms="$(perf_now_ms)"
signup_status="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/signup" "${signup_response}" \
    -H "Origin: ${SMOKE_TRUSTED_ORIGIN}" \
    -H "Referer: ${SMOKE_TRUSTED_REFERER}" \
    -H 'Content-Type: application/json' \
    --data-binary "@${signup_body}"
)"
end_ms="$(perf_now_ms)"
printf 'signup\t%s\t%s\n' "${signup_status}" "$((end_ms - start_ms))" >> "${SETUP_TSV}"
if [[ "${signup_status}" != "200" ]]; then
  echo "signup failed: status=${signup_status}" >&2
  smoke_print_redacted_file_for_log "${signup_response}"
  exit 1
fi

login_body="${ARTIFACT_DIR}/setup/login.body.json"
AUTH_FLOW_LOGIN_PASSWORD="${SMOKE_PASSWORD}" python3 - "${email}" "${login_body}" <<'PY'
import json
import os
import sys

email, output_path = sys.argv[1:3]
with open(output_path, "w", encoding="utf-8") as fp:
    json.dump({"email": email, "password": os.environ["AUTH_FLOW_LOGIN_PASSWORD"]}, fp, ensure_ascii=False)
PY

start_ms="$(perf_now_ms)"
login_status="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${login_response}" \
    -H 'User-Agent: youth-welfare-recommendation-flow-baseline/1.0' \
    -H 'Content-Type: application/json' \
    -c "${cookie_jar}" \
    --data-binary "@${login_body}"
)"
end_ms="$(perf_now_ms)"
printf 'login\t%s\t%s\n' "${login_status}" "$((end_ms - start_ms))" >> "${SETUP_TSV}"
if [[ "${login_status}" != "200" ]]; then
  echo "login failed: status=${login_status}" >&2
  smoke_print_redacted_file_for_log "${login_response}"
  exit 1
fi

access_token="$(
  python3 - "${login_response}" <<'PY'
import json
import sys

payload = json.load(open(sys.argv[1], encoding="utf-8"))
print((payload.get("data") or {}).get("accessToken") or "")
PY
)"

AUTH_TOKEN="${access_token}" python3 - \
  "${APP_BASE_URL}" \
  "${RECOMMEND_RUN_PERSONAL}" \
  "${RECOMMEND_RUN_SIMILAR_USERS_VIEWED}" \
  "${RECOMMEND_REQUEST_DELAY_SECONDS}" \
  "${REQUEST_TIMEOUT_SECONDS}" \
  "${SMOKE_TRUSTED_ORIGIN}" \
  "${SMOKE_TRUSTED_REFERER}" \
  "${ARTIFACT_DIR}/responses" \
  "${SAMPLES_TSV}" \
  "${SUMMARY_TXT}" \
  "${SUMMARY_JSON}" <<'PY'
import csv
import json
import os
import statistics
import sys
import time
import urllib.error
import urllib.request
from collections import Counter
from pathlib import Path

(
    base_url,
    run_personal_raw,
    run_similar_raw,
    delay_raw,
    timeout_raw,
    trusted_origin,
    trusted_referer,
    responses_dir_raw,
    samples_path_raw,
    summary_txt_raw,
    summary_json_raw,
) = sys.argv[1:12]

base = base_url.rstrip("/")
run_personal = run_personal_raw.lower() == "true"
run_similar = run_similar_raw.lower() == "true"
delay_s = float(delay_raw)
timeout_s = int(timeout_raw)
responses_dir = Path(responses_dir_raw)
samples_path = Path(samples_path_raw)
summary_txt = Path(summary_txt_raw)
summary_json = Path(summary_json_raw)
auth_token = os.environ["AUTH_TOKEN"]

steps = [
    ("stored_read_before", "GET", "/api/recommendations?size=20", False),
    ("shared_refresh", "POST", "/api/recommendations/refresh?personal=false", True),
    ("stored_read_after", "GET", "/api/recommendations?size=20", False),
    ("shared_refresh_cached", "POST", "/api/recommendations/refresh?personal=false", True),
]
if run_personal:
    steps.append(("personal_refresh", "POST", "/api/recommendations/refresh?personal=true", True))
steps.append(("stored_read_final", "GET", "/api/recommendations?size=20", False))
if run_similar:
    steps.append(("similar_users_viewed", "GET", "/api/recommendations/similar-users-viewed?size=6", False))

def percentile(values, p):
    if not values:
        return None
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    rank = (len(ordered) - 1) * p
    low = int(rank)
    high = min(low + 1, len(ordered) - 1)
    fraction = rank - low
    return ordered[low] * (1 - fraction) + ordered[high] * fraction

def parse_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {}

def payload_count(payload):
    data = payload.get("data")
    return len(data) if isinstance(data, list) else 0

def ai_status_counts(payload):
    data = payload.get("data")
    if not isinstance(data, list):
        return {}
    counts = Counter()
    for item in data:
        if isinstance(item, dict):
            counts[str(item.get("aiStatus") or "null")] += 1
    return dict(sorted(counts.items()))

def error_code(payload):
    return str(payload.get("errorCode") or "")

samples = []
for index, (step, method, path, mutates) in enumerate(steps, start=1):
    response_file = responses_dir / f"{index:02d}-{step}.json"
    headers = {
        "Accept": "application/json",
        "Authorization": f"Bearer {auth_token}",
        "User-Agent": "youth-welfare-recommendation-flow-baseline/1.0",
    }
    if mutates:
        headers["Origin"] = trusted_origin
        headers["Referer"] = trusted_referer
    request = urllib.request.Request(base + path, headers=headers, method=method)
    status = "000"
    error = ""
    body = b""
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout_s) as response:
            body = response.read()
            status = str(response.status)
    except urllib.error.HTTPError as exc:
        status = str(exc.code)
        error = exc.__class__.__name__
        try:
            body = exc.read()
        except Exception:
            body = b""
    except Exception as exc:
        error = exc.__class__.__name__
    duration_ms = (time.perf_counter() - started) * 1000
    response_file.write_bytes(body)
    payload = parse_json(response_file)
    samples.append({
        "step": step,
        "method": method,
        "path": path,
        "http_code": status,
        "duration_ms": duration_ms,
        "error_code": error_code(payload),
        "error": error,
        "result_count": payload_count(payload),
        "ai_status_counts": ai_status_counts(payload),
    })
    if delay_s > 0 and index < len(steps):
        time.sleep(delay_s)

with samples_path.open("w", encoding="utf-8", newline="") as fp:
    writer = csv.DictWriter(
        fp,
        fieldnames=["step", "method", "path", "http_code", "duration_ms", "error_code", "error", "result_count", "ai_status_counts"],
        delimiter="\t",
    )
    writer.writeheader()
    for row in samples:
        writer.writerow({
            **row,
            "duration_ms": f"{row['duration_ms']:.3f}",
            "ai_status_counts": json.dumps(row["ai_status_counts"], ensure_ascii=False, sort_keys=True),
        })

summary_rows = []
for row in samples:
    ok = row["http_code"] == "200"
    summary_rows.append({
        "step": row["step"],
        "method": row["method"],
        "path": row["path"],
        "success": ok,
        "http_code": row["http_code"],
        "error_code": row["error_code"],
        "rate_limited": row["http_code"] == "429" or row["error_code"] == "R004",
        "already_running": row["http_code"] == "409" or row["error_code"] == "R003",
        "duration_ms": row["duration_ms"],
        "result_count": row["result_count"],
        "ai_status_counts": row["ai_status_counts"],
    })

durations = [row["duration_ms"] for row in samples]
result = {
    "run_personal": run_personal,
    "run_similar_users_viewed": run_similar,
    "total_steps": len(samples),
    "success_count": sum(1 for row in summary_rows if row["success"]),
    "error_count": sum(1 for row in summary_rows if not row["success"]),
    "rate_limited_count": sum(1 for row in summary_rows if row["rate_limited"]),
    "already_running_count": sum(1 for row in summary_rows if row["already_running"]),
    "p50_ms": percentile(durations, 0.50),
    "p95_ms": percentile(durations, 0.95),
    "p99_ms": percentile(durations, 0.99),
    "max_ms": max(durations) if durations else None,
    "avg_ms": statistics.mean(durations) if durations else None,
    "summary": summary_rows,
}

with summary_json.open("w", encoding="utf-8") as fp:
    json.dump(result, fp, ensure_ascii=False, indent=2)

lines = [
    "recommendation_flow_baseline=passed" if result["error_count"] == 0 else "recommendation_flow_baseline=failed",
    f"run_personal={run_personal}",
    f"run_similar_users_viewed={run_similar}",
    f"total_steps={result['total_steps']}",
    f"success_count={result['success_count']}",
    f"error_count={result['error_count']}",
    f"rate_limited_count={result['rate_limited_count']}",
    f"already_running_count={result['already_running_count']}",
]
for row in summary_rows:
    lines.append(
        f"{row['step']} status={row['http_code']} duration_ms={row['duration_ms']:.1f} "
        f"result_count={row['result_count']} error_code={row['error_code'] or '-'} "
        f"ai_status_counts={json.dumps(row['ai_status_counts'], ensure_ascii=False, sort_keys=True)}"
    )
summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")

if result["error_count"] != 0:
    raise SystemExit(1)
PY

db_count_snapshot "after"

perf_publish_latest \
  "${ARTIFACT_DIR}" \
  "${LATEST_DIR}" \
  "${SUMMARY_TXT}" \
  "${LATEST_SUMMARY_TXT}" \
  "${SUMMARY_JSON}" \
  "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
