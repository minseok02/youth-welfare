#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-public.profile.chat.smoke}"
SMOKE_NAME="${SMOKE_NAME:-점검사용자}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
PUBLIC_SEARCH_KEYWORD="${PUBLIC_SEARCH_KEYWORD:-청년}"
CHAT_MESSAGE_CONTENT="${CHAT_MESSAGE_CONTENT:-청년 취업 지원 정책을 간단히 알려줘}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
COOKIE_JAR="${ARTIFACT_DIR}/user.cookie"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
POLICY_LIST_RESPONSE="${ARTIFACT_DIR}/policies.json"
POLICY_SEARCH_RESPONSE="${ARTIFACT_DIR}/policy-search.json"
POLICY_SEARCH_REQUEST="${ARTIFACT_DIR}/policy-search-request.json"
POLICY_DETAIL_RESPONSE="${ARTIFACT_DIR}/policy-detail.json"
SIGNUP_RESPONSE="${ARTIFACT_DIR}/signup.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/login.json"
PROFILE_RESPONSE="${ARTIFACT_DIR}/profile.json"
PRIORITIES_RESPONSE="${ARTIFACT_DIR}/priorities.json"
CREATE_CHAT_RESPONSE="${ARTIFACT_DIR}/create-chat.json"
CHAT_SESSIONS_RESPONSE="${ARTIFACT_DIR}/chat-sessions.json"
SEND_CHAT_RESPONSE="${ARTIFACT_DIR}/send-chat.json"
CHAT_MESSAGES_RESPONSE="${ARTIFACT_DIR}/chat-messages.json"
DELETE_CHAT_RESPONSE="${ARTIFACT_DIR}/delete-chat.json"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
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
smoke_require_command docker

SMOKE_EMAIL="$(smoke_build_email "${SMOKE_EMAIL_PREFIX}")"

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

smoke_print_step "ensure policy fixture"
smoke_ensure_policy_fixture

smoke_print_step "public policies list"
POLICY_LIST_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/policies?size=3&statusFilter=ACTIVE_ONLY" "${POLICY_LIST_RESPONSE}"
)"
smoke_assert_status 200 "${POLICY_LIST_STATUS}" "public policies list" "${POLICY_LIST_RESPONSE}"
POLICY_ID="$(extract_json "${POLICY_LIST_RESPONSE}" '(payload["data"]["content"] or [])[0].get("id", "") if (payload["data"]["content"] or []) else ""')"
if [[ -z "${POLICY_ID}" ]]; then
  echo "public policies list returned no first policy id" >&2
  cat "${POLICY_LIST_RESPONSE}" >&2
  exit 1
fi

smoke_print_step "public policy search (${PUBLIC_SEARCH_KEYWORD})"
python3 - "${PUBLIC_SEARCH_KEYWORD}" "${POLICY_SEARCH_REQUEST}" <<'PY'
import json
import sys

keyword, out_path = sys.argv[1], sys.argv[2]
with open(out_path, "w", encoding="utf-8") as fp:
    json.dump({"keyword": keyword, "size": 3}, fp, ensure_ascii=False)
PY
POLICY_SEARCH_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/policies/search" "${POLICY_SEARCH_RESPONSE}" \
    -H 'Content-Type: application/json' \
    -d @"${POLICY_SEARCH_REQUEST}"
)"
smoke_assert_status 200 "${POLICY_SEARCH_STATUS}" "public policy search" "${POLICY_SEARCH_RESPONSE}"

smoke_print_step "public policy detail (${POLICY_ID})"
POLICY_DETAIL_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/policies/${POLICY_ID}" "${POLICY_DETAIL_RESPONSE}"
)"
smoke_assert_status 200 "${POLICY_DETAIL_STATUS}" "public policy detail" "${POLICY_DETAIL_RESPONSE}"

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

smoke_print_step "profile get"
PROFILE_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/users/me" "${PROFILE_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${PROFILE_STATUS}" "profile get" "${PROFILE_RESPONSE}"

smoke_print_step "priorities update"
PRIORITIES_STATUS="$(
  smoke_http_status PUT "${APP_BASE_URL}/api/users/me/priorities" "${PRIORITIES_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d '{"priorityCodes":["EDUCATION","JOB"]}'
)"
smoke_assert_status 200 "${PRIORITIES_STATUS}" "priorities update" "${PRIORITIES_RESPONSE}"

smoke_print_step "chat create session"
CREATE_CHAT_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/chat/sessions" "${CREATE_CHAT_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d '{"title":"QA 세션"}'
)"
smoke_assert_status 200 "${CREATE_CHAT_STATUS}" "chat create session" "${CREATE_CHAT_RESPONSE}"
SESSION_ID="$(extract_json "${CREATE_CHAT_RESPONSE}" 'payload["data"].get("sessionId") or payload["data"].get("id")')"
if [[ -z "${SESSION_ID}" ]]; then
  echo "chat create session returned no session id" >&2
  cat "${CREATE_CHAT_RESPONSE}" >&2
  exit 1
fi

smoke_print_step "chat list sessions"
CHAT_SESSIONS_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/chat/sessions" "${CHAT_SESSIONS_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${CHAT_SESSIONS_STATUS}" "chat list sessions" "${CHAT_SESSIONS_RESPONSE}"

smoke_print_step "chat send message"
SEND_CHAT_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/chat/sessions/${SESSION_ID}/messages" "${SEND_CHAT_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"content\": \"${CHAT_MESSAGE_CONTENT}\"
    }"
)"
smoke_assert_status 200 "${SEND_CHAT_STATUS}" "chat send message" "${SEND_CHAT_RESPONSE}"

smoke_print_step "chat get messages"
CHAT_MESSAGES_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/chat/sessions/${SESSION_ID}/messages" "${CHAT_MESSAGES_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${CHAT_MESSAGES_STATUS}" "chat get messages" "${CHAT_MESSAGES_RESPONSE}"

smoke_print_step "chat delete session"
DELETE_CHAT_STATUS="$(
  smoke_http_status DELETE "${APP_BASE_URL}/api/chat/sessions/${SESSION_ID}" "${DELETE_CHAT_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${DELETE_CHAT_STATUS}" "chat delete session" "${DELETE_CHAT_RESPONSE}"

echo
echo "public/profile/chat smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "smoke_email=${SMOKE_EMAIL}"
echo "policy_id=${POLICY_ID}"
echo "search_total=$(extract_json "${POLICY_SEARCH_RESPONSE}" 'payload["data"]["totalElements"]')"
echo "reference_count=$(extract_json "${POLICY_DETAIL_RESPONSE}" 'len(payload["data"].get("referenceUrls") or [])')"
echo "session_id=${SESSION_ID}"
echo "answer_mode=$(extract_json "${SEND_CHAT_RESPONSE}" 'payload["data"].get("answerMode")')"
echo "needs_clarification=$(extract_json "${SEND_CHAT_RESPONSE}" 'payload["data"].get("needsClarification")')"
echo "message_count=$(extract_json "${CHAT_MESSAGES_RESPONSE}" 'len(payload["data"] or [])')"
