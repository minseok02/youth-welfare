#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-chat.followup.smoke}"
SMOKE_NAME="${SMOKE_NAME:-점검사용자}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-서울특별시}"
SMOKE_SGG="${SMOKE_SGG:-마포구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
FIRST_CHAT_MESSAGE_CONTENT="${FIRST_CHAT_MESSAGE_CONTENT:-서울 월세 지원 알려줘}"
SECOND_CHAT_MESSAGE_CONTENT="${SECOND_CHAT_MESSAGE_CONTENT:-그럼 전세는?}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"
CHAT_FOLLOWUP_SMOKE_MIN_POLICY_ROWS="${CHAT_FOLLOWUP_SMOKE_MIN_POLICY_ROWS:-10}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
COOKIE_JAR="${ARTIFACT_DIR}/user.cookie"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SIGNUP_RESPONSE="${ARTIFACT_DIR}/signup.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/login.json"
CREATE_CHAT_RESPONSE="${ARTIFACT_DIR}/create-chat.json"
FIRST_SEND_CHAT_RESPONSE="${ARTIFACT_DIR}/send-chat-first.json"
SECOND_SEND_CHAT_RESPONSE="${ARTIFACT_DIR}/send-chat-second.json"
CHAT_MESSAGES_RESPONSE="${ARTIFACT_DIR}/chat-messages.json"
DELETE_CHAT_RESPONSE="${ARTIFACT_DIR}/delete-chat.json"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

extract_json() {
  local response_file="$1"
  local expression="$2"
  python3 - "$response_file" "$expression" <<'PY'
import json
import sys

response_file, expression = sys.argv[1], sys.argv[2]
with open(response_file, "r", encoding="utf-8") as fp:
    payload = json.load(fp)

value = eval(expression, {"__builtins__": {}, "len": len}, {"payload": payload})
if value is None:
    print("")
elif isinstance(value, (dict, list)):
    print(json.dumps(value, ensure_ascii=False))
else:
    print(value)
PY
}

smoke_require_command curl
smoke_require_command python3

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
mkdir -p "${ARTIFACT_DIR}"

SMOKE_EMAIL="$(smoke_build_email "${SMOKE_EMAIL_PREFIX}")"

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

smoke_print_step "policy corpus precheck"
POLICY_ROW_COUNT="$(smoke_db_query "select count(*) from welfare_services;" | head -n 1)"
POLICY_ROW_COUNT="${POLICY_ROW_COUNT//[^0-9]/}"
POLICY_ROW_COUNT="${POLICY_ROW_COUNT:-0}"
if (( POLICY_ROW_COUNT < CHAT_FOLLOWUP_SMOKE_MIN_POLICY_ROWS )); then
  echo
  echo "chat follow-up smoke skipped"
  echo "skip_reason=INSUFFICIENT_POLICY_CORPUS"
  echo "policy_row_count=${POLICY_ROW_COUNT}"
  echo "min_policy_rows=${CHAT_FOLLOWUP_SMOKE_MIN_POLICY_ROWS}"
  echo "app_base_url=${APP_BASE_URL}"
  exit 0
fi

smoke_print_step "signup ${SMOKE_EMAIL}"
smoke_seed_verified_email "${SMOKE_EMAIL}"
SIGNUP_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/signup" "${SIGNUP_RESPONSE}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\",
      \"name\": \"${SMOKE_NAME}\",
      \"birthDate\": \"${SMOKE_BIRTH_DATE}\",
      \"privacyNoticeConfirmed\": true,
      \"optionalProfileConsentAgreed\": true,
      \"sido\": \"${SMOKE_SIDO}\",
      \"sgg\": \"${SMOKE_SGG}\",
      \"incomeLevel\": ${SMOKE_INCOME_LEVEL},
      \"employmentStatus\": \"${SMOKE_EMPLOYMENT_STATUS}\",
      \"householdType\": \"${SMOKE_HOUSEHOLD_TYPE}\"
    }"
)"
smoke_assert_status 200 "${SIGNUP_STATUS}" "signup" "${SIGNUP_RESPONSE}"

smoke_print_step "login"
LOGIN_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${LOGIN_RESPONSE}" \
    -c "${COOKIE_JAR}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\"
    }"
)"
smoke_assert_status 200 "${LOGIN_STATUS}" "login" "${LOGIN_RESPONSE}"
ACCESS_TOKEN="$(extract_json "${LOGIN_RESPONSE}" 'payload["data"]["accessToken"]')"

smoke_print_step "chat create session"
CREATE_CHAT_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/chat/sessions" "${CREATE_CHAT_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d '{"title":"Follow-up QA 세션"}'
)"
smoke_assert_status 200 "${CREATE_CHAT_STATUS}" "chat create session" "${CREATE_CHAT_RESPONSE}"
SESSION_ID="$(extract_json "${CREATE_CHAT_RESPONSE}" 'payload["data"].get("sessionId") or payload["data"].get("id")')"
if [[ -z "${SESSION_ID}" ]]; then
  echo "chat create session returned no session id" >&2
  cat "${CREATE_CHAT_RESPONSE}" >&2
  exit 1
fi

smoke_print_step "chat send first message"
FIRST_SEND_CHAT_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/chat/sessions/${SESSION_ID}/messages" "${FIRST_SEND_CHAT_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"content\": \"${FIRST_CHAT_MESSAGE_CONTENT}\"
    }"
)"
smoke_assert_status 200 "${FIRST_SEND_CHAT_STATUS}" "chat send first message" "${FIRST_SEND_CHAT_RESPONSE}"

smoke_print_step "chat send follow-up message"
SECOND_SEND_CHAT_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/chat/sessions/${SESSION_ID}/messages" "${SECOND_SEND_CHAT_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"content\": \"${SECOND_CHAT_MESSAGE_CONTENT}\"
    }"
)"
smoke_assert_status 200 "${SECOND_SEND_CHAT_STATUS}" "chat send follow-up message" "${SECOND_SEND_CHAT_RESPONSE}"

FIRST_ANSWER="$(extract_json "${FIRST_SEND_CHAT_RESPONSE}" 'payload["data"].get("answer", "")')"
SECOND_ANSWER="$(extract_json "${SECOND_SEND_CHAT_RESPONSE}" 'payload["data"].get("answer", "")')"
FIRST_REFERENCE_COUNT="$(extract_json "${FIRST_SEND_CHAT_RESPONSE}" 'len(payload["data"].get("references") or [])')"
SECOND_REFERENCE_COUNT="$(extract_json "${SECOND_SEND_CHAT_RESPONSE}" 'len(payload["data"].get("references") or [])')"
SECOND_BRANCH_SUGGESTION_COUNT="$(extract_json "${SECOND_SEND_CHAT_RESPONSE}" 'len(payload["data"].get("branchSuggestions") or [])')"
SECOND_ANSWER_MODE="$(extract_json "${SECOND_SEND_CHAT_RESPONSE}" 'payload["data"].get("answerMode")')"

if [[ -z "${FIRST_ANSWER}" || -z "${SECOND_ANSWER}" ]]; then
  echo "chat follow-up smoke expected non-empty answers for both turns" >&2
  cat "${FIRST_SEND_CHAT_RESPONSE}" >&2
  cat "${SECOND_SEND_CHAT_RESPONSE}" >&2
  exit 1
fi

if (( SECOND_REFERENCE_COUNT == 0 && SECOND_BRANCH_SUGGESTION_COUNT == 0 )); then
  echo "chat follow-up smoke expected second turn to keep either references or branch suggestions" >&2
  cat "${SECOND_SEND_CHAT_RESPONSE}" >&2
  exit 1
fi

smoke_print_step "chat get messages"
CHAT_MESSAGES_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/chat/sessions/${SESSION_ID}/messages" "${CHAT_MESSAGES_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${CHAT_MESSAGES_STATUS}" "chat get messages" "${CHAT_MESSAGES_RESPONSE}"
MESSAGE_COUNT="$(extract_json "${CHAT_MESSAGES_RESPONSE}" 'len(payload["data"] or [])')"
if (( MESSAGE_COUNT < 4 )); then
  echo "chat follow-up smoke expected at least 4 messages in session" >&2
  cat "${CHAT_MESSAGES_RESPONSE}" >&2
  exit 1
fi

smoke_print_step "chat delete session"
DELETE_CHAT_STATUS="$(
  smoke_http_status DELETE "${APP_BASE_URL}/api/chat/sessions/${SESSION_ID}" "${DELETE_CHAT_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${DELETE_CHAT_STATUS}" "chat delete session" "${DELETE_CHAT_RESPONSE}"

echo
echo "chat follow-up smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "smoke_email=${SMOKE_EMAIL}"
echo "session_id=${SESSION_ID}"
echo "first_answer_mode=$(extract_json "${FIRST_SEND_CHAT_RESPONSE}" 'payload["data"].get("answerMode")')"
echo "second_answer_mode=${SECOND_ANSWER_MODE}"
echo "first_reference_count=${FIRST_REFERENCE_COUNT}"
echo "second_reference_count=${SECOND_REFERENCE_COUNT}"
echo "second_branch_suggestion_count=${SECOND_BRANCH_SUGGESTION_COUNT}"
echo "message_count=${MESSAGE_COUNT}"
