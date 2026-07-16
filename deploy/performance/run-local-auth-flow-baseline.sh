#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
AUTH_FLOW_ROOT="${AUTH_FLOW_ROOT:-${ROOT_DIR}/tmp/performance/auth-flow}"
AUTH_FLOW_ACCOUNT_COUNT="${AUTH_FLOW_ACCOUNT_COUNT:-12}"
AUTH_FLOW_REQUEST_DELAY_SECONDS="${AUTH_FLOW_REQUEST_DELAY_SECONDS:-1.0}"
REQUEST_TIMEOUT_SECONDS="${REQUEST_TIMEOUT_SECONDS:-10}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-auth.flow.perf}"
SMOKE_NAME="${SMOKE_NAME:-인증측정}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
SMOKE_TRUSTED_ORIGIN="${SMOKE_TRUSTED_ORIGIN:-https://youthmoa.kr}"
SMOKE_TRUSTED_REFERER="${SMOKE_TRUSTED_REFERER:-${SMOKE_TRUSTED_ORIGIN}/}"

RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${AUTH_FLOW_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
SETUP_TSV="${ARTIFACT_DIR}/auth-flow-setup.tsv"
SAMPLES_TSV="${ARTIFACT_DIR}/auth-flow-samples.tsv"
SUMMARY_TXT="${ARTIFACT_DIR}/auth-flow-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/auth-flow-summary.json"
LATEST_DIR="${AUTH_FLOW_ROOT}/latest"
LATEST_SUMMARY_TXT="${AUTH_FLOW_ROOT}/latest-auth-flow-summary.txt"
LATEST_SUMMARY_JSON="${AUTH_FLOW_ROOT}/latest-auth-flow-summary.json"

perf_require_python
smoke_require_command curl
mkdir -p "${ARTIFACT_DIR}/setup" "${ARTIFACT_DIR}/responses"
perf_sanitize_artifacts_on_exit "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"
{
  echo "auth_flow_account_count=${AUTH_FLOW_ACCOUNT_COUNT}"
  echo "auth_flow_request_delay_seconds=${AUTH_FLOW_REQUEST_DELAY_SECONDS}"
  echo "request_timeout_seconds=${REQUEST_TIMEOUT_SECONDS}"
  echo "smoke_trusted_origin=${SMOKE_TRUSTED_ORIGIN}"
  echo "measured_steps=login,refresh,me,logout"
  echo "excluded_steps=signup,email-send,password-reset"
} >> "${CONTEXT_TXT}"

if (( AUTH_FLOW_ACCOUNT_COUNT < 1 )); then
  echo "AUTH_FLOW_ACCOUNT_COUNT must be >= 1" >&2
  exit 1
fi

HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
HEALTH_STATUS="$(smoke_wait_for_health 15 1 "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

email_prefix="${SMOKE_EMAIL_PREFIX}.${RUN_TS_UTC,,}"
email_domain="${SMOKE_EMAIL_DOMAIN:-example.com}"
printf 'account_index\tsignup_status\tsignup_ms\n' > "${SETUP_TSV}"

for account_index in $(seq 1 "${AUTH_FLOW_ACCOUNT_COUNT}"); do
  email="${email_prefix}.${account_index}@${email_domain}"
  smoke_seed_verified_email "${email}"
  signup_response="${ARTIFACT_DIR}/setup/signup-${account_index}.json"
  signup_body="${ARTIFACT_DIR}/setup/signup-${account_index}.body.json"
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
  signup_ms=$((end_ms - start_ms))
  printf '%s\t%s\t%s\n' "${account_index}" "${signup_status}" "${signup_ms}" >> "${SETUP_TSV}"
  if [[ "${signup_status}" != "200" ]]; then
    echo "signup failed for account_index=${account_index}: status=${signup_status}" >&2
    smoke_print_redacted_file_for_log "${signup_response}"
    exit 1
  fi
done

AUTH_FLOW_PASSWORD="${SMOKE_PASSWORD}" python3 - \
  "${APP_BASE_URL}" \
  "${email_prefix}" \
  "${email_domain}" \
  "${AUTH_FLOW_ACCOUNT_COUNT}" \
  "${AUTH_FLOW_REQUEST_DELAY_SECONDS}" \
  "${REQUEST_TIMEOUT_SECONDS}" \
  "${SMOKE_TRUSTED_ORIGIN}" \
  "${SMOKE_TRUSTED_REFERER}" \
  "${ARTIFACT_DIR}/responses" \
  "${SAMPLES_TSV}" \
  "${SUMMARY_TXT}" \
  "${SUMMARY_JSON}" \
  "${SETUP_TSV}" <<'PY'
import csv
import http.cookiejar
import json
import os
import statistics
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

(
    base_url,
    email_prefix,
    email_domain,
    account_count_raw,
    delay_raw,
    timeout_raw,
    trusted_origin,
    trusted_referer,
    responses_dir_raw,
    samples_path_raw,
    summary_txt_raw,
    summary_json_raw,
    setup_tsv_raw,
) = sys.argv[1:14]
password = os.environ["AUTH_FLOW_PASSWORD"]

account_count = int(account_count_raw)
delay_s = float(delay_raw)
timeout_s = int(timeout_raw)
responses_dir = Path(responses_dir_raw)
samples_path = Path(samples_path_raw)
summary_txt = Path(summary_txt_raw)
summary_json = Path(summary_json_raw)
setup_tsv = Path(setup_tsv_raw)
base = base_url.rstrip("/")

samples = []

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

def parse_access_token(path):
    payload = json.loads(path.read_text(encoding="utf-8"))
    return ((payload.get("data") or {}).get("accessToken")) or ""

def parse_error_code(path):
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return ""
    return str(payload.get("errorCode") or "")

def record(account_index, step, status, duration_ms, response_file, error):
    error_code = parse_error_code(response_file) if response_file and response_file.exists() else ""
    samples.append({
        "account_index": account_index,
        "step": step,
        "http_code": status,
        "duration_ms": duration_ms,
        "error_code": error_code,
        "error": error,
    })

def request(account_index, opener, step, method, path, body=None, token=None):
    response_file = responses_dir / f"{account_index:03d}-{step}.json"
    headers = {
        "Accept": "application/json",
        "User-Agent": "youth-welfare-auth-flow-baseline/1.0",
    }
    if body is not None:
        headers["Content-Type"] = "application/json"
    if step in {"refresh", "logout"}:
        headers["Origin"] = trusted_origin
        headers["Referer"] = trusted_referer
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    started = time.perf_counter()
    status = "000"
    error = ""
    payload = b""
    try:
        with opener.open(req, timeout=timeout_s) as response:
            payload = response.read()
            status = str(response.status)
    except urllib.error.HTTPError as exc:
        status = str(exc.code)
        error = exc.__class__.__name__
        try:
            payload = exc.read()
        except Exception:
            payload = b""
    except Exception as exc:
        error = exc.__class__.__name__
    duration_ms = (time.perf_counter() - started) * 1000
    response_file.write_bytes(payload)
    record(account_index, step, status, duration_ms, response_file, error)
    return status, response_file

flow_success = 0
for account_index in range(1, account_count + 1):
    email = f"{email_prefix}.{account_index}@{email_domain}"
    cookie_jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookie_jar))

    login_status, login_file = request(
        account_index,
        opener,
        "login",
        "POST",
        "/api/auth/login",
        {"email": email, "password": password},
    )
    access_token = parse_access_token(login_file) if login_status == "200" else ""

    refresh_status, refresh_file = request(account_index, opener, "refresh", "POST", "/api/auth/refresh")
    refreshed_token = parse_access_token(refresh_file) if refresh_status == "200" else ""
    token_for_me = refreshed_token or access_token

    me_status, _ = request(account_index, opener, "me", "GET", "/api/users/me", token=token_for_me)
    logout_status, _ = request(account_index, opener, "logout", "POST", "/api/auth/logout", token=token_for_me)

    if login_status == refresh_status == me_status == logout_status == "200":
        flow_success += 1

    if delay_s > 0 and account_index < account_count:
        time.sleep(delay_s)

with samples_path.open("w", encoding="utf-8", newline="") as fp:
    writer = csv.DictWriter(fp, fieldnames=["account_index", "step", "http_code", "duration_ms", "error_code", "error"], delimiter="\t")
    writer.writeheader()
    writer.writerows({
        **row,
        "duration_ms": f"{row['duration_ms']:.3f}",
    } for row in samples)

setup_rows = list(csv.DictReader(setup_tsv.open(encoding="utf-8"), delimiter="\t"))
summary_rows = []
for step in ["login", "refresh", "me", "logout"]:
    rows = [row for row in samples if row["step"] == step]
    durations = [row["duration_ms"] for row in rows]
    status_counts = {}
    error_codes = {}
    for row in rows:
        status_counts[row["http_code"]] = status_counts.get(row["http_code"], 0) + 1
        if row["error_code"]:
            error_codes[row["error_code"]] = error_codes.get(row["error_code"], 0) + 1
    success_count = status_counts.get("200", 0)
    rate_limited = status_counts.get("429", 0) + error_codes.get("A010", 0)
    summary_rows.append({
        "step": step,
        "requests": len(rows),
        "success_count": success_count,
        "error_count": len(rows) - success_count,
        "rate_limited_count": rate_limited,
        "status_counts": status_counts,
        "error_codes": error_codes,
        "p50_ms": percentile(durations, 0.50),
        "p95_ms": percentile(durations, 0.95),
        "p99_ms": percentile(durations, 0.99),
        "max_ms": max(durations) if durations else None,
        "avg_ms": statistics.mean(durations) if durations else None,
    })

total_requests = len(samples)
total_errors = sum(row["error_count"] for row in summary_rows)
total_rate_limited = sum(row["rate_limited_count"] for row in summary_rows)
setup_success = sum(1 for row in setup_rows if row.get("signup_status") == "200")
setup_durations = [float(row["signup_ms"]) for row in setup_rows if row.get("signup_ms")]

result = {
    "account_count": account_count,
    "setup_success_count": setup_success,
    "setup_error_count": len(setup_rows) - setup_success,
    "setup_avg_ms": statistics.mean(setup_durations) if setup_durations else None,
    "setup_max_ms": max(setup_durations) if setup_durations else None,
    "flow_success_count": flow_success,
    "flow_error_count": account_count - flow_success,
    "total_requests": total_requests,
    "total_errors": total_errors,
    "total_rate_limited": total_rate_limited,
    "summary": summary_rows,
}

with summary_json.open("w", encoding="utf-8") as fp:
    json.dump(result, fp, ensure_ascii=False, indent=2)

lines = [
    "auth_flow_baseline=passed" if total_errors == 0 else "auth_flow_baseline=failed",
    f"account_count={account_count}",
    f"setup_success_count={setup_success}",
    f"flow_success_count={flow_success}",
    f"total_requests={total_requests}",
    f"total_errors={total_errors}",
    f"total_rate_limited={total_rate_limited}",
]
for row in summary_rows:
    lines.append(
        f"{row['step']} requests={row['requests']} success={row['success_count']} "
        f"p50_ms={row['p50_ms']:.1f} p95_ms={row['p95_ms']:.1f} "
        f"p99_ms={row['p99_ms']:.1f} max_ms={row['max_ms']:.1f} "
        f"rate_limited={row['rate_limited_count']} status_counts={row['status_counts']}"
    )
summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")

if total_errors != 0:
    raise SystemExit(1)
PY

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
