#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
CHAT_FLOW_ROOT="${CHAT_FLOW_ROOT:-${ROOT_DIR}/tmp/performance/chat-flow}"
CHAT_FLOW_QUESTIONS_FILE="${CHAT_FLOW_QUESTIONS_FILE:-}"
CHAT_FLOW_QUESTION_COUNT="${CHAT_FLOW_QUESTION_COUNT:-3}"
CHAT_FLOW_REQUEST_DELAY_SECONDS="${CHAT_FLOW_REQUEST_DELAY_SECONDS:-2.0}"
CHAT_FLOW_DELETE_SESSION="${CHAT_FLOW_DELETE_SESSION:-false}"
REQUEST_TIMEOUT_SECONDS="${REQUEST_TIMEOUT_SECONDS:-90}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-chat.flow.perf}"
SMOKE_NAME="${SMOKE_NAME:-챗봇측정}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
SMOKE_TRUSTED_ORIGIN="${SMOKE_TRUSTED_ORIGIN:-https://youthmoa.kr}"
SMOKE_TRUSTED_REFERER="${SMOKE_TRUSTED_REFERER:-${SMOKE_TRUSTED_ORIGIN}/}"

RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${CHAT_FLOW_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
SETUP_TSV="${ARTIFACT_DIR}/chat-flow-setup.tsv"
SAMPLES_TSV="${ARTIFACT_DIR}/chat-flow-samples.tsv"
DB_COUNTS_TSV="${ARTIFACT_DIR}/chat-flow-db-counts.tsv"
SUMMARY_TXT="${ARTIFACT_DIR}/chat-flow-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/chat-flow-summary.json"
LATEST_DIR="${CHAT_FLOW_ROOT}/latest"
LATEST_SUMMARY_TXT="${CHAT_FLOW_ROOT}/latest-chat-flow-summary.txt"
LATEST_SUMMARY_JSON="${CHAT_FLOW_ROOT}/latest-chat-flow-summary.json"

perf_require_python
smoke_require_command curl
CHAT_FLOW_DELETE_SESSION="$(smoke_normalize_bool "${CHAT_FLOW_DELETE_SESSION}")"
mkdir -p "${ARTIFACT_DIR}/setup" "${ARTIFACT_DIR}/responses"
perf_sanitize_artifacts_on_exit "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"
{
  echo "chat_flow_question_count=${CHAT_FLOW_QUESTION_COUNT}"
  echo "chat_flow_request_delay_seconds=${CHAT_FLOW_REQUEST_DELAY_SECONDS}"
  echo "chat_flow_delete_session=${CHAT_FLOW_DELETE_SESSION}"
  echo "request_timeout_seconds=${REQUEST_TIMEOUT_SECONDS}"
  echo "smoke_trusted_origin=${SMOKE_TRUSTED_ORIGIN}"
  echo "rate_limit_contract=5 messages per user per 60 seconds"
  echo "measured_steps=create_session,send_message_x${CHAT_FLOW_QUESTION_COUNT},get_messages"
  echo "excluded_steps=admin-chat-review,coach-policy-id,session-delete-unless-opted-in"
} >> "${CONTEXT_TXT}"

if (( CHAT_FLOW_QUESTION_COUNT < 1 || CHAT_FLOW_QUESTION_COUNT > 5 )); then
  echo "CHAT_FLOW_QUESTION_COUNT must be between 1 and 5" >&2
  exit 1
fi

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
signup_response="${ARTIFACT_DIR}/setup/signup.json"
signup_body="${ARTIFACT_DIR}/setup/signup.body.json"
login_response="${ARTIFACT_DIR}/setup/login.json"
priorities_response="${ARTIFACT_DIR}/setup/priorities.json"
cookie_jar="${ARTIFACT_DIR}/setup/chat-flow.cookie"

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
    -H 'User-Agent: youth-welfare-chat-flow-baseline/1.0' \
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

start_ms="$(perf_now_ms)"
priorities_status="$(
  smoke_http_status PUT "${APP_BASE_URL}/api/users/me/priorities" "${priorities_response}" \
    -H "Authorization: Bearer ${access_token}" \
    -H "Origin: ${SMOKE_TRUSTED_ORIGIN}" \
    -H "Referer: ${SMOKE_TRUSTED_REFERER}" \
    -H 'Content-Type: application/json' \
    --data-binary '{"priorityCodes":["EDUCATION","JOB","HOUSING"]}'
)"
end_ms="$(perf_now_ms)"
printf 'priorities\t%s\t%s\n' "${priorities_status}" "$((end_ms - start_ms))" >> "${SETUP_TSV}"
if [[ "${priorities_status}" != "200" ]]; then
  echo "priorities update failed: status=${priorities_status}" >&2
  smoke_print_redacted_file_for_log "${priorities_response}"
  exit 1
fi

AUTH_TOKEN="${access_token}" python3 - \
  "${APP_BASE_URL}" \
  "${CHAT_FLOW_QUESTIONS_FILE}" \
  "${CHAT_FLOW_QUESTION_COUNT}" \
  "${CHAT_FLOW_REQUEST_DELAY_SECONDS}" \
  "${CHAT_FLOW_DELETE_SESSION}" \
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
    questions_file_raw,
    question_count_raw,
    delay_raw,
    delete_session_raw,
    timeout_raw,
    trusted_origin,
    trusted_referer,
    responses_dir_raw,
    samples_path_raw,
    summary_txt_raw,
    summary_json_raw,
) = sys.argv[1:13]

base = base_url.rstrip("/")
question_count = int(question_count_raw)
delay_s = float(delay_raw)
delete_session = delete_session_raw.lower() == "true"
timeout_s = int(timeout_raw)
responses_dir = Path(responses_dir_raw)
samples_path = Path(samples_path_raw)
summary_txt = Path(summary_txt_raw)
summary_json = Path(summary_json_raw)
auth_token = os.environ["AUTH_TOKEN"]

default_questions = [
    "청년 취업 지원 정책을 간단히 알려줘",
    "인천 중구 청년이 받을 수 있는 주거 지원을 알려줘",
    "신청하려면 어떤 서류와 절차를 준비해야 해?",
]

def load_questions():
    if not questions_file_raw:
        return default_questions[:question_count]
    path = Path(questions_file_raw)
    text = path.read_text(encoding="utf-8")
    if path.suffix.lower() == ".json":
        payload = json.loads(text)
        if not isinstance(payload, list):
            raise SystemExit("CHAT_FLOW_QUESTIONS_FILE json must be a list")
        return [str(item) for item in payload if str(item).strip()][:question_count]
    return [line.strip() for line in text.splitlines() if line.strip() and not line.lstrip().startswith("#")][:question_count]

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

def error_code(payload):
    return str(payload.get("errorCode") or "")

def response_data(payload):
    data = payload.get("data")
    return data if isinstance(data, dict) else {}

def answer_metrics(payload):
    data = response_data(payload)
    refs = data.get("references") or []
    branches = data.get("branchSuggestions") or []
    action_links = 0
    if isinstance(refs, list):
        for ref in refs:
            if isinstance(ref, dict):
                action_links += len(ref.get("actionLinks") or [])
    answer = str(data.get("answer") or "")
    return {
        "answer_mode": str(data.get("answerMode") or ""),
        "needs_clarification": str(data.get("needsClarification")).lower() if "needsClarification" in data else "",
        "answer_length": len(answer),
        "reference_count": len(refs) if isinstance(refs, list) else 0,
        "action_link_count": action_links,
        "branch_suggestion_count": len(branches) if isinstance(branches, list) else 0,
    }

def message_count(payload):
    data = payload.get("data")
    return len(data) if isinstance(data, list) else 0

def request(step, method, path, body=None, response_name=None):
    response_file = responses_dir / (response_name or f"{step}.json")
    headers = {
        "Accept": "application/json",
        "Authorization": f"Bearer {auth_token}",
        "User-Agent": "youth-welfare-chat-flow-baseline/1.0",
    }
    if body is not None:
        headers["Content-Type"] = "application/json"
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
        with urllib.request.urlopen(req, timeout=timeout_s) as response:
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
    return status, duration_ms, response_file, error

questions = load_questions()
if not questions:
    raise SystemExit("no chat questions to measure")
if len(questions) > 5:
    raise SystemExit("chat questions must be <= 5 to respect the default rate limit")

samples = []

create_status, create_ms, create_file, create_error = request(
    "create_session",
    "POST",
    "/api/chat/sessions",
    {"title": "성능 측정 세션"},
    "00-create-session.json",
)
create_payload = parse_json(create_file)
session_id = response_data(create_payload).get("sessionId") or response_data(create_payload).get("id")
samples.append({
    "step": "create_session",
    "question_index": "",
    "http_code": create_status,
    "duration_ms": create_ms,
    "error_code": error_code(create_payload),
    "error": create_error,
    "answer_mode": "",
    "needs_clarification": "",
    "answer_length": "",
    "reference_count": "",
    "action_link_count": "",
    "branch_suggestion_count": "",
    "message_count": "",
})

if create_status != "200" or not session_id:
    raise SystemExit("chat session creation failed")

for index, question in enumerate(questions, start=1):
    status, duration_ms, response_file, error = request(
        f"send_message_{index}",
        "POST",
        f"/api/chat/sessions/{session_id}/messages",
        {"content": question},
        f"{index:02d}-send-message.json",
    )
    payload = parse_json(response_file)
    metrics = answer_metrics(payload)
    samples.append({
        "step": "send_message",
        "question_index": index,
        "http_code": status,
        "duration_ms": duration_ms,
        "error_code": error_code(payload),
        "error": error,
        **metrics,
        "message_count": "",
    })
    if delay_s > 0 and index < len(questions):
        time.sleep(delay_s)

messages_status, messages_ms, messages_file, messages_error = request(
    "get_messages",
    "GET",
    f"/api/chat/sessions/{session_id}/messages",
    response_name="99-get-messages.json",
)
messages_payload = parse_json(messages_file)
samples.append({
    "step": "get_messages",
    "question_index": "",
    "http_code": messages_status,
    "duration_ms": messages_ms,
    "error_code": error_code(messages_payload),
    "error": messages_error,
    "answer_mode": "",
    "needs_clarification": "",
    "answer_length": "",
    "reference_count": "",
    "action_link_count": "",
    "branch_suggestion_count": "",
    "message_count": message_count(messages_payload),
})

if delete_session:
    delete_status, delete_ms, delete_file, delete_error = request(
        "delete_session",
        "DELETE",
        f"/api/chat/sessions/{session_id}",
        response_name="100-delete-session.json",
    )
    delete_payload = parse_json(delete_file)
    samples.append({
        "step": "delete_session",
        "question_index": "",
        "http_code": delete_status,
        "duration_ms": delete_ms,
        "error_code": error_code(delete_payload),
        "error": delete_error,
        "answer_mode": "",
        "needs_clarification": "",
        "answer_length": "",
        "reference_count": "",
        "action_link_count": "",
        "branch_suggestion_count": "",
        "message_count": "",
    })

fieldnames = [
    "step",
    "question_index",
    "http_code",
    "duration_ms",
    "error_code",
    "error",
    "answer_mode",
    "needs_clarification",
    "answer_length",
    "reference_count",
    "action_link_count",
    "branch_suggestion_count",
    "message_count",
]
with samples_path.open("w", encoding="utf-8", newline="") as fp:
    writer = csv.DictWriter(fp, fieldnames=fieldnames, delimiter="\t")
    writer.writeheader()
    for row in samples:
        out = dict(row)
        out["duration_ms"] = f"{row['duration_ms']:.3f}"
        writer.writerow(out)

send_rows = [row for row in samples if row["step"] == "send_message"]
all_durations = [row["duration_ms"] for row in samples]
send_durations = [row["duration_ms"] for row in send_rows]
mode_counts = Counter(str(row.get("answer_mode") or "") for row in send_rows)
needs_clarification_count = sum(1 for row in send_rows if row.get("needs_clarification") == "true")
rate_limited_count = sum(1 for row in samples if row["http_code"] == "429" or row["error_code"] == "C006")
error_count = sum(1 for row in samples if row["http_code"] != "200")
send_success_count = sum(1 for row in send_rows if row["http_code"] == "200")

result = {
    "session_id": session_id,
    "question_count": len(questions),
    "questions": questions,
    "total_steps": len(samples),
    "success_count": sum(1 for row in samples if row["http_code"] == "200"),
    "send_success_count": send_success_count,
    "error_count": error_count,
    "rate_limited_count": rate_limited_count,
    "needs_clarification_count": needs_clarification_count,
    "answer_mode_counts": dict(sorted(mode_counts.items())),
    "all_p50_ms": percentile(all_durations, 0.50),
    "all_p95_ms": percentile(all_durations, 0.95),
    "all_p99_ms": percentile(all_durations, 0.99),
    "all_max_ms": max(all_durations) if all_durations else None,
    "send_p50_ms": percentile(send_durations, 0.50),
    "send_p95_ms": percentile(send_durations, 0.95),
    "send_p99_ms": percentile(send_durations, 0.99),
    "send_max_ms": max(send_durations) if send_durations else None,
    "send_avg_ms": statistics.mean(send_durations) if send_durations else None,
    "summary": samples,
}

with summary_json.open("w", encoding="utf-8") as fp:
    json.dump(result, fp, ensure_ascii=False, indent=2)

lines = [
    "chat_flow_baseline=passed" if error_count == 0 else "chat_flow_baseline=failed",
    f"session_id={session_id}",
    f"question_count={len(questions)}",
    f"total_steps={result['total_steps']}",
    f"success_count={result['success_count']}",
    f"send_success_count={send_success_count}",
    f"error_count={error_count}",
    f"rate_limited_count={rate_limited_count}",
    f"needs_clarification_count={needs_clarification_count}",
    f"answer_mode_counts={json.dumps(result['answer_mode_counts'], ensure_ascii=False, sort_keys=True)}",
]
for row in samples:
    detail = (
        f"{row['step']} status={row['http_code']} duration_ms={row['duration_ms']:.1f} "
        f"error_code={row['error_code'] or '-'}"
    )
    if row["step"] == "send_message":
        detail += (
            f" question_index={row['question_index']}"
            f" answer_mode={row['answer_mode'] or '-'}"
            f" needs_clarification={row['needs_clarification'] or '-'}"
            f" answer_length={row['answer_length']}"
            f" references={row['reference_count']}"
            f" action_links={row['action_link_count']}"
            f" branch_suggestions={row['branch_suggestion_count']}"
        )
    if row["step"] == "get_messages":
        detail += f" message_count={row['message_count']}"
    lines.append(detail)
summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")

if error_count != 0:
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
