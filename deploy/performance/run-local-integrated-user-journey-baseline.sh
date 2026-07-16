#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
INTEGRATED_JOURNEY_ROOT="${INTEGRATED_JOURNEY_ROOT:-${ROOT_DIR}/tmp/performance/integrated-user-journey}"
REQUEST_TIMEOUT_SECONDS="${REQUEST_TIMEOUT_SECONDS:-120}"
JOURNEY_REQUEST_DELAY_SECONDS="${JOURNEY_REQUEST_DELAY_SECONDS:-0.5}"
JOURNEY_CHAT_QUESTION="${JOURNEY_CHAT_QUESTION:-청년 취업 지원 정책을 간단히 알려줘}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-integrated.journey.perf}"
SMOKE_NAME="${SMOKE_NAME:-통합측정}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
SMOKE_TRUSTED_ORIGIN="${SMOKE_TRUSTED_ORIGIN:-https://youthmoa.kr}"
SMOKE_TRUSTED_REFERER="${SMOKE_TRUSTED_REFERER:-${SMOKE_TRUSTED_ORIGIN}/}"

RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${INTEGRATED_JOURNEY_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
DB_COUNTS_TSV="${ARTIFACT_DIR}/integrated-user-journey-db-counts.tsv"
SAMPLES_TSV="${ARTIFACT_DIR}/integrated-user-journey-samples.tsv"
SUMMARY_TXT="${ARTIFACT_DIR}/integrated-user-journey-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/integrated-user-journey-summary.json"
LATEST_DIR="${INTEGRATED_JOURNEY_ROOT}/latest"
LATEST_SUMMARY_TXT="${INTEGRATED_JOURNEY_ROOT}/latest-integrated-user-journey-summary.txt"
LATEST_SUMMARY_JSON="${INTEGRATED_JOURNEY_ROOT}/latest-integrated-user-journey-summary.json"

perf_require_python
smoke_require_command curl
mkdir -p "${ARTIFACT_DIR}/responses"
perf_sanitize_artifacts_on_exit "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"
{
  echo "request_timeout_seconds=${REQUEST_TIMEOUT_SECONDS}"
  echo "journey_request_delay_seconds=${JOURNEY_REQUEST_DELAY_SECONDS}"
  echo "smoke_trusted_origin=${SMOKE_TRUSTED_ORIGIN}"
  echo "measured_flow=signup,login,refresh,profile,priorities,policy_list,policy_search,policy_detail,recommend_refresh,recommend_read,bookmark_on,bookmarks_after_on,bookmark_off,bookmarks_after_off,chat_create,chat_message,chat_messages,logout"
  echo "ai_backed_steps=recommend_refresh,chat_message"
  echo "excluded_steps=password_reset,email_send,high_rps_stress"
} >> "${CONTEXT_TXT}"

HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
HEALTH_STATUS="$(smoke_wait_for_health 15 1 "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

db_count_snapshot() {
  local label="$1"
  local env_file="${ENV_FILE:-${ROOT_DIR}/.env}"
  local db_query_username="${DB_QUERY_USERNAME:-}"
  local db_query_password="${DB_QUERY_PASSWORD:-}"
  if [[ -z "${db_query_username}" ]]; then
    db_query_username="$(smoke_load_env_value "${env_file}" DB_USERNAME "")"
  fi
  if [[ -z "${db_query_password}" ]]; then
    db_query_password="$(smoke_load_env_value "${env_file}" DB_PASSWORD "")"
  fi
  set +e
  local output
  output="$(
    DB_QUERY_USERNAME="${db_query_username}" DB_QUERY_PASSWORD="${db_query_password}" \
      SMOKE_DB_MODE="${SMOKE_DB_MODE:-postgres}" smoke_db_query "
      select 'users', count(*)::text from users
      union all
      select 'user_recommendations', count(*)::text from user_recommendations
      union all
      select 'recommendation_logs', count(*)::text from recommendation_logs
      union all
      select 'recommendation_run_logs', count(*)::text from recommendation_run_logs
      union all
      select 'chat_sessions', count(*)::text from chat_sessions
      union all
      select 'chat_messages', count(*)::text from chat_messages
      union all
      select 'chat_retrieval_snapshots', count(*)::text from chat_retrieval_snapshots;
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
smoke_seed_verified_email "${email}"

INTEGRATED_JOURNEY_PASSWORD="${SMOKE_PASSWORD}" python3 - \
  "${APP_BASE_URL}" \
  "${email}" \
  "${SMOKE_NAME}" \
  "${SMOKE_BIRTH_DATE}" \
  "${SMOKE_SIDO}" \
  "${SMOKE_SGG}" \
  "${SMOKE_INCOME_LEVEL}" \
  "${SMOKE_EMPLOYMENT_STATUS}" \
  "${SMOKE_HOUSEHOLD_TYPE}" \
  "${JOURNEY_CHAT_QUESTION}" \
  "${JOURNEY_REQUEST_DELAY_SECONDS}" \
  "${REQUEST_TIMEOUT_SECONDS}" \
  "${SMOKE_TRUSTED_ORIGIN}" \
  "${SMOKE_TRUSTED_REFERER}" \
  "${ARTIFACT_DIR}/responses" \
  "${SAMPLES_TSV}" \
  "${SUMMARY_TXT}" \
  "${SUMMARY_JSON}" <<'PY'
import csv
import http.cookiejar
import json
import os
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from pathlib import Path

(
    base_url,
    email,
    name,
    birth_date,
    sido,
    sgg,
    income_level,
    employment_status,
    household_type,
    chat_question,
    delay_raw,
    timeout_raw,
    trusted_origin,
    trusted_referer,
    responses_dir_raw,
    samples_path_raw,
    summary_txt_raw,
    summary_json_raw,
) = sys.argv[1:19]

password = os.environ["INTEGRATED_JOURNEY_PASSWORD"]
base = base_url.rstrip("/")
delay_s = float(delay_raw)
timeout_s = int(timeout_raw)
responses_dir = Path(responses_dir_raw)
samples_path = Path(samples_path_raw)
summary_txt = Path(summary_txt_raw)
summary_json = Path(summary_json_raw)

cookie_jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookie_jar))
access_token = ""
service_id = None
recommendation_id = None
chat_session_id = None

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

def parse_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {}

def data_obj(payload):
    data = payload.get("data")
    return data if isinstance(data, dict) else {}

def data_list(payload):
    data = payload.get("data")
    return data if isinstance(data, list) else []

def page_content(payload):
    data = payload.get("data") or {}
    content = data.get("content") if isinstance(data, dict) else None
    return content if isinstance(content, list) else []

def error_code(payload):
    return str(payload.get("errorCode") or "")

def record(step, phase, method, path, expected_status, status, duration_ms, response_file, error, ok, detail=None, ai_backed=False):
    payload = parse_json(response_file) if response_file else {}
    samples.append({
        "step": step,
        "phase": phase,
        "method": method,
        "path": path,
        "expected_status": str(expected_status),
        "http_code": str(status),
        "duration_ms": duration_ms,
        "error_code": error_code(payload),
        "error": error,
        "ok": bool(ok),
        "ai_backed": bool(ai_backed),
        "detail": detail or {},
    })

def request(step, phase, method, path, body=None, token=None, expected_status="200", response_name=None, ai_backed=False):
    response_file = responses_dir / (response_name or f"{len(samples) + 1:02d}-{step}.json")
    headers = {
        "Accept": "application/json",
        "User-Agent": "youth-welfare-integrated-user-journey-baseline/1.0",
    }
    if body is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if method in {"POST", "PUT", "DELETE"}:
        headers["Origin"] = trusted_origin
        headers["Referer"] = trusted_referer
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    status = "000"
    error = ""
    payload = b""
    started = time.perf_counter()
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
    payload_json = parse_json(response_file)
    ok = status == str(expected_status)
    record(step, phase, method, path, expected_status, status, duration_ms, response_file, error, ok, ai_backed=ai_backed)
    if delay_s > 0:
        time.sleep(delay_s)
    return status, payload_json, response_file

def update_last_detail(detail, ok=None):
    samples[-1]["detail"] = detail
    if ok is not None:
        samples[-1]["ok"] = bool(ok) and samples[-1]["ok"]

signup_body = {
    "email": email,
    "password": password,
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
status, payload, _ = request("signup", "auth", "POST", "/api/auth/signup", signup_body)

status, payload, _ = request("login", "auth", "POST", "/api/auth/login", {"email": email, "password": password})
access_token = data_obj(payload).get("accessToken") or ""
update_last_detail({"access_token_present": bool(access_token)}, bool(access_token))

status, payload, _ = request("refresh", "auth", "POST", "/api/auth/refresh")
refreshed_token = data_obj(payload).get("accessToken") or ""
if refreshed_token:
    access_token = refreshed_token
update_last_detail({"access_token_present": bool(refreshed_token)}, bool(refreshed_token))

status, payload, _ = request("profile_me", "profile", "GET", "/api/users/me", token=access_token)
update_last_detail({"profile_present": bool(data_obj(payload))}, bool(data_obj(payload)))

status, payload, _ = request(
    "priorities_update",
    "profile",
    "PUT",
    "/api/users/me/priorities",
    {"priorityCodes": ["EDUCATION", "JOB", "HOUSING"]},
    token=access_token,
)

status, payload, _ = request("policy_list", "read_api", "GET", "/api/policies?page=0&size=20&statusFilter=ACTIVE_ONLY", token=access_token)
policy_items = page_content(payload)
first_policy = policy_items[0] if policy_items else {}
service_id = first_policy.get("id")
update_last_detail({"result_count": len(policy_items), "first_service_id": service_id}, len(policy_items) > 0 and service_id is not None)

status, payload, _ = request(
    "policy_search",
    "read_api",
    "POST",
    "/api/policies/search",
    {"keyword": "청년", "page": 0, "size": 20, "statusFilter": "ACTIVE_ONLY"},
    token=access_token,
)
search_items = page_content(payload)
update_last_detail({"result_count": len(search_items)}, len(search_items) > 0)

if service_id is None:
    raise SystemExit("policy_list returned no service_id")

status, payload, _ = request("policy_detail", "read_api", "GET", f"/api/policies/{service_id}", token=access_token)
detail = data_obj(payload)
update_last_detail({"service_id": detail.get("id"), "title_present": bool(detail.get("title"))}, str(detail.get("id")) == str(service_id))

status, payload, _ = request(
    "recommend_refresh",
    "recommendation",
    "POST",
    "/api/recommendations/refresh?personal=true",
    token=access_token,
    ai_backed=True,
)
recommend_items = data_list(payload)
first_recommendation = recommend_items[0] if recommend_items else {}
recommendation_id = first_recommendation.get("id")
recommended_service_id = first_recommendation.get("serviceId") or first_recommendation.get("service", {}).get("id")
if recommended_service_id is not None:
    service_id = recommended_service_id
ai_status_counts = Counter(str(item.get("aiStatus") or "null") for item in recommend_items if isinstance(item, dict))
update_last_detail(
    {
        "result_count": len(recommend_items),
        "first_recommendation_id": recommendation_id,
        "first_service_id": service_id,
        "ai_status_counts": dict(sorted(ai_status_counts.items())),
    },
    len(recommend_items) > 0 and service_id is not None,
)

status, payload, _ = request("recommend_read", "recommendation", "GET", "/api/recommendations?size=20", token=access_token)
stored_recommend_items = data_list(payload)
update_last_detail({"result_count": len(stored_recommend_items)}, len(stored_recommend_items) > 0)

status, payload, _ = request("bookmark_on", "bookmark", "POST", f"/api/policies/{service_id}/bookmark", token=access_token)
update_last_detail({"service_id": service_id, "target_state": "on"})

status, payload, _ = request("bookmarks_after_on", "bookmark", "GET", "/api/users/me/bookmarks", token=access_token)
bookmarks = data_list(payload)
bookmark_contains = any(str(item.get("id")) == str(service_id) for item in bookmarks if isinstance(item, dict))
update_last_detail({"result_count": len(bookmarks), "contains_service": bookmark_contains, "service_id": service_id}, bookmark_contains)

status, payload, _ = request("bookmark_off", "bookmark", "POST", f"/api/policies/{service_id}/bookmark", token=access_token)
update_last_detail({"service_id": service_id, "target_state": "off"})

status, payload, _ = request("bookmarks_after_off", "bookmark", "GET", "/api/users/me/bookmarks", token=access_token)
bookmarks = data_list(payload)
bookmark_contains = any(str(item.get("id")) == str(service_id) for item in bookmarks if isinstance(item, dict))
update_last_detail({"result_count": len(bookmarks), "contains_service": bookmark_contains, "service_id": service_id}, not bookmark_contains)

status, payload, _ = request("chat_create", "chat", "POST", "/api/chat/sessions", {"title": "통합 측정 세션"}, token=access_token)
chat_session_id = data_obj(payload).get("sessionId") or data_obj(payload).get("id")
update_last_detail({"session_id": chat_session_id}, chat_session_id is not None)
if chat_session_id is None:
    raise SystemExit("chat_create returned no session id")

status, payload, _ = request(
    "chat_message",
    "chat",
    "POST",
    f"/api/chat/sessions/{chat_session_id}/messages",
    {"content": chat_question},
    token=access_token,
    ai_backed=True,
)
chat_data = data_obj(payload)
references = chat_data.get("references") if isinstance(chat_data.get("references"), list) else []
branches = chat_data.get("branchSuggestions") if isinstance(chat_data.get("branchSuggestions"), list) else []
update_last_detail(
    {
        "answer_mode": chat_data.get("answerMode"),
        "needs_clarification": chat_data.get("needsClarification"),
        "answer_length": len(str(chat_data.get("answer") or "")),
        "reference_count": len(references),
        "branch_suggestion_count": len(branches),
    },
    bool(chat_data.get("answerMode")) and len(str(chat_data.get("answer") or "")) > 0,
)

status, payload, _ = request("chat_messages", "chat", "GET", f"/api/chat/sessions/{chat_session_id}/messages", token=access_token)
chat_messages = data_list(payload)
update_last_detail({"message_count": len(chat_messages)}, len(chat_messages) >= 2)

status, payload, _ = request("logout", "auth", "POST", "/api/auth/logout", token=access_token)

fieldnames = [
    "step",
    "phase",
    "method",
    "path",
    "expected_status",
    "http_code",
    "duration_ms",
    "error_code",
    "error",
    "ok",
    "ai_backed",
    "detail",
]
with samples_path.open("w", encoding="utf-8", newline="") as fp:
    writer = csv.DictWriter(fp, fieldnames=fieldnames, delimiter="\t")
    writer.writeheader()
    for row in samples:
        out = dict(row)
        out["duration_ms"] = f"{row['duration_ms']:.3f}"
        out["ok"] = str(row["ok"]).lower()
        out["ai_backed"] = str(row["ai_backed"]).lower()
        out["detail"] = json.dumps(row["detail"], ensure_ascii=False, sort_keys=True)
        writer.writerow(out)

durations = [row["duration_ms"] for row in samples]
non_ai_durations = [row["duration_ms"] for row in samples if not row["ai_backed"]]
ai_durations = [row["duration_ms"] for row in samples if row["ai_backed"]]
failed_rows = [row for row in samples if not row["ok"]]
rate_limited_count = sum(1 for row in samples if row["http_code"] == "429" or row["error_code"] in {"R004", "C006", "P003", "A010"})
status_counts = Counter(str(row["http_code"]) for row in samples)
phase_durations = {}
for phase in sorted({row["phase"] for row in samples}):
    values = [row["duration_ms"] for row in samples if row["phase"] == phase]
    phase_durations[phase] = {
        "count": len(values),
        "total_ms": sum(values),
        "max_ms": max(values) if values else None,
        "avg_ms": statistics.mean(values) if values else None,
    }

result = {
    "total_steps": len(samples),
    "success_count": len(samples) - len(failed_rows),
    "error_count": len(failed_rows),
    "rate_limited_count": rate_limited_count,
    "status_counts": dict(sorted(status_counts.items())),
    "total_duration_ms": sum(durations),
    "p50_ms": percentile(durations, 0.50),
    "p95_ms": percentile(durations, 0.95),
    "p99_ms": percentile(durations, 0.99),
    "max_ms": max(durations) if durations else None,
    "non_ai_total_duration_ms": sum(non_ai_durations),
    "ai_total_duration_ms": sum(ai_durations),
    "ai_step_count": len(ai_durations),
    "phase_durations": phase_durations,
    "service_id": service_id,
    "recommendation_id": recommendation_id,
    "chat_session_id": chat_session_id,
    "summary": samples,
}

with summary_json.open("w", encoding="utf-8") as fp:
    json.dump(result, fp, ensure_ascii=False, indent=2)

lines = [
    "integrated_user_journey_baseline=passed" if not failed_rows else "integrated_user_journey_baseline=failed",
    f"total_steps={result['total_steps']}",
    f"success_count={result['success_count']}",
    f"error_count={result['error_count']}",
    f"rate_limited_count={result['rate_limited_count']}",
    f"total_duration_ms={result['total_duration_ms']:.1f}",
    f"non_ai_total_duration_ms={result['non_ai_total_duration_ms']:.1f}",
    f"ai_total_duration_ms={result['ai_total_duration_ms']:.1f}",
    f"p95_ms={result['p95_ms']:.1f}",
    f"max_ms={result['max_ms']:.1f}",
]
for row in samples:
    lines.append(
        f"{row['step']} phase={row['phase']} status={row['http_code']} "
        f"duration_ms={row['duration_ms']:.1f} ok={str(row['ok']).lower()} "
        f"ai_backed={str(row['ai_backed']).lower()} error_code={row['error_code'] or '-'} "
        f"detail={json.dumps(row['detail'], ensure_ascii=False, sort_keys=True)}"
    )
summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")

if failed_rows:
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
