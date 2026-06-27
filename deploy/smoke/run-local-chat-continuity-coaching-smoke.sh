#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-chat.continuity.coaching}"
SMOKE_NAME="${SMOKE_NAME:-점검사용자}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-서울특별시}"
SMOKE_SGG="${SMOKE_SGG:-마포구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
COACH_POLICY_ID="${COACH_POLICY_ID:-}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"
CHAT_CONTINUITY_COACHING_MIN_POLICY_ROWS="${CHAT_CONTINUITY_COACHING_MIN_POLICY_ROWS:-10}"
RUN_TS_UTC="${RUN_TS_UTC:-$(smoke_now_ts_utc)}"
ARTIFACT_ROOT="${ARTIFACT_ROOT:-${ROOT_DIR}/tmp/chat-continuity-coaching-smoke}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${RUN_TS_UTC}}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"

COOKIE_JAR="${ARTIFACT_DIR}/user.cookie"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SUMMARY_JSON="${ARTIFACT_DIR}/chat-continuity-coaching-summary.json"
SUMMARY_TXT="${ARTIFACT_DIR}/chat-continuity-coaching-summary.txt"
NOTE_OUT="${ARTIFACT_DIR}/chat-continuity-coaching-note.md"
LATEST_ARTIFACT_LINK="${ARTIFACT_ROOT}/latest"
LATEST_SUMMARY_LINK="${ARTIFACT_ROOT}/latest-chat-continuity-coaching-summary.txt"
LATEST_JSON_LINK="${ARTIFACT_ROOT}/latest-chat-continuity-coaching-summary.json"
LATEST_NOTE_LINK="${ARTIFACT_ROOT}/latest-chat-continuity-coaching-note.md"
ACCESS_TOKEN=""
CONTINUITY_SESSION_ID=""
COACHING_SESSION_ID=""

mkdir -p "${ARTIFACT_DIR}"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ -n "${ACCESS_TOKEN}" && -n "${CONTINUITY_SESSION_ID}" ]]; then
    curl -sS -o /dev/null -X DELETE "${APP_BASE_URL}/api/chat/sessions/${CONTINUITY_SESSION_ID}" \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" || true
  fi
  if [[ -n "${ACCESS_TOKEN}" && -n "${COACHING_SESSION_ID}" ]]; then
    curl -sS -o /dev/null -X DELETE "${APP_BASE_URL}/api/chat/sessions/${COACHING_SESSION_ID}" \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" || true
  fi
  if [[ "${KEEP_ARTIFACTS}" != "true" ]]; then
    rm -rf "${ARTIFACT_DIR}"
  fi
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

write_chat_body() {
  local output_file="$1"
  local content="$2"
  local coach_policy_id="${3:-}"

  python3 - "$output_file" "$content" "$coach_policy_id" <<'PY'
import json
import sys

output_file, content, coach_policy_id = sys.argv[1], sys.argv[2], sys.argv[3]
body = {"content": content}
if coach_policy_id:
    body["coachPolicyId"] = int(coach_policy_id)

with open(output_file, "w", encoding="utf-8") as fp:
    json.dump(body, fp, ensure_ascii=False)
PY
}

resolve_coach_policy_id() {
  if [[ -n "${COACH_POLICY_ID}" ]]; then
    printf '%s' "${COACH_POLICY_ID}"
    return 0
  fi

  smoke_db_query "
      select ws.id
      from welfare_services ws
      join welfare_service_details wsd on wsd.service_id = ws.id
      where wsd.reference_urls_json like '%\"type\":\"APPLY\"%'
         or wsd.reference_urls_json like '%\"type\": \"APPLY\"%'
      order by
        case when wsd.form_files is not null and length(trim(wsd.form_files)) > 0 then 0 else 1 end,
        ws.updated_at desc nulls last,
        ws.id desc
      limit 1;
    " | head -n 1
}

send_chat_message() {
  local session_id="$1"
  local content="$2"
  local response_file="$3"
  local body_file="$4"
  local access_token="$5"
  local coach_policy_id="${6:-}"

  write_chat_body "${body_file}" "${content}" "${coach_policy_id}"
  smoke_http_status POST "${APP_BASE_URL}/api/chat/sessions/${session_id}/messages" "${response_file}" \
    -H "Authorization: Bearer ${access_token}" \
    -H 'Content-Type: application/json' \
    --data-binary "@${body_file}"
}

smoke_require_command curl
smoke_require_command python3

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

smoke_print_step "policy corpus precheck"
POLICY_ROW_COUNT="$(smoke_db_query "select count(*) from welfare_services;" | head -n 1)"
POLICY_ROW_COUNT="${POLICY_ROW_COUNT//[^0-9]/}"
POLICY_ROW_COUNT="${POLICY_ROW_COUNT:-0}"
if (( POLICY_ROW_COUNT < CHAT_CONTINUITY_COACHING_MIN_POLICY_ROWS )); then
  cat > "${SUMMARY_TXT}" <<EOF
chat continuity/coaching smoke skipped
skip_reason=INSUFFICIENT_POLICY_CORPUS
policy_row_count=${POLICY_ROW_COUNT}
min_policy_rows=${CHAT_CONTINUITY_COACHING_MIN_POLICY_ROWS}
artifact_dir=${ARTIFACT_DIR}
EOF
  python3 - "${SUMMARY_JSON}" "${ARTIFACT_DIR}" "${POLICY_ROW_COUNT}" "${CHAT_CONTINUITY_COACHING_MIN_POLICY_ROWS}" <<'PY'
import json
import sys
from pathlib import Path

summary_json = Path(sys.argv[1])
summary_json.write_text(json.dumps({
    "chatContinuityCoachingSmoke": "skipped",
    "skipReason": "INSUFFICIENT_POLICY_CORPUS",
    "artifactDir": sys.argv[2],
    "policyRowCount": int(sys.argv[3]),
    "minPolicyRows": int(sys.argv[4]),
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY
  cat > "${NOTE_OUT}" <<EOF
# Chat Continuity Coaching Smoke

- status: \`skipped\`
- skip_reason: \`INSUFFICIENT_POLICY_CORPUS\`
- policy_row_count: \`${POLICY_ROW_COUNT}\`
- min_policy_rows: \`${CHAT_CONTINUITY_COACHING_MIN_POLICY_ROWS}\`

Fresh local databases do not contain the representative policy corpus required by the continuity/coaching smoke.
EOF
smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  smoke_publish_dir_snapshot "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
  smoke_publish_file "${SUMMARY_TXT}" "${LATEST_SUMMARY_LINK}"
  smoke_publish_file "${SUMMARY_JSON}" "${LATEST_JSON_LINK}"
  smoke_publish_file "${NOTE_OUT}" "${LATEST_NOTE_LINK}"
  cat "${SUMMARY_TXT}"
  echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
  echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
  echo "latest_json_link=${LATEST_JSON_LINK}"
  echo "latest_note_link=${LATEST_NOTE_LINK}"
  exit 0
fi

smoke_print_step "resolve coach policy"
RESOLVED_COACH_POLICY_ID="$(resolve_coach_policy_id)"
if [[ -z "${RESOLVED_COACH_POLICY_ID}" ]]; then
  echo "chat continuity/coaching smoke requires a policy with APPLY reference_urls_json" >&2
  exit 1
fi

SMOKE_EMAIL="$(smoke_build_email "${SMOKE_EMAIL_PREFIX}")"

smoke_print_step "signup ${SMOKE_EMAIL}"
smoke_seed_verified_email "${SMOKE_EMAIL}"
SIGNUP_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/signup" "${ARTIFACT_DIR}/signup.json" \
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
smoke_assert_status 200 "${SIGNUP_STATUS}" "signup" "${ARTIFACT_DIR}/signup.json"

smoke_print_step "login"
LOGIN_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${ARTIFACT_DIR}/login.json" \
    -c "${COOKIE_JAR}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\"
    }"
)"
smoke_assert_status 200 "${LOGIN_STATUS}" "login" "${ARTIFACT_DIR}/login.json"
ACCESS_TOKEN="$(extract_json "${ARTIFACT_DIR}/login.json" 'payload["data"]["accessToken"]')"

smoke_print_step "create continuity session"
CREATE_CONTINUITY_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/chat/sessions" "${ARTIFACT_DIR}/create-continuity-session.json" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d '{"title":"대화 연속성 smoke"}'
)"
smoke_assert_status 200 "${CREATE_CONTINUITY_STATUS}" "create continuity session" "${ARTIFACT_DIR}/create-continuity-session.json"
CONTINUITY_SESSION_ID="$(extract_json "${ARTIFACT_DIR}/create-continuity-session.json" 'payload["data"].get("sessionId") or payload["data"].get("id")')"
if [[ -z "${CONTINUITY_SESSION_ID}" ]]; then
  echo "chat create session returned no continuity session id" >&2
  cat "${ARTIFACT_DIR}/create-continuity-session.json" >&2
  exit 1
fi

CONTINUITY_TURNS=(
  "서울 월세 지원 알려줘"
  "그럼 전세는?"
  "다시 월세 쪽으로 돌아가면?"
)

turn=1
for content in "${CONTINUITY_TURNS[@]}"; do
  smoke_print_step "send continuity turn ${turn}"
  SEND_STATUS="$(
    send_chat_message \
      "${CONTINUITY_SESSION_ID}" \
      "${content}" \
      "${ARTIFACT_DIR}/continuity-turn-${turn}.json" \
      "${ARTIFACT_DIR}/continuity-turn-${turn}-body.json" \
      "${ACCESS_TOKEN}"
  )"
  smoke_assert_status 200 "${SEND_STATUS}" "send continuity turn ${turn}" "${ARTIFACT_DIR}/continuity-turn-${turn}.json"
  turn=$((turn + 1))
done

smoke_print_step "get continuity messages"
CONTINUITY_MESSAGES_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/chat/sessions/${CONTINUITY_SESSION_ID}/messages" "${ARTIFACT_DIR}/continuity-messages.json" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${CONTINUITY_MESSAGES_STATUS}" "get continuity messages" "${ARTIFACT_DIR}/continuity-messages.json"

smoke_print_step "inspect continuity db state"
smoke_db_query "select context_state_json from chat_sessions where id = ${CONTINUITY_SESSION_ID};" \
  > "${ARTIFACT_DIR}/continuity-context.tsv"
smoke_db_query "
    select question,
           coalesce(search_keyword, ''),
           coalesce(branch_key, ''),
           coalesce(preferred_terms_json, ''),
           result_count,
           coalesce(merged_service_ids_json, '')
    from chat_retrieval_snapshots
    where session_id = ${CONTINUITY_SESSION_ID}
    order by id;
  " > "${ARTIFACT_DIR}/continuity-snapshots.tsv"

smoke_print_step "create coaching session"
CREATE_COACHING_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/chat/sessions" "${ARTIFACT_DIR}/create-coaching-session.json" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d '{"title":"신청 도우미 smoke"}'
)"
smoke_assert_status 200 "${CREATE_COACHING_STATUS}" "create coaching session" "${ARTIFACT_DIR}/create-coaching-session.json"
COACHING_SESSION_ID="$(extract_json "${ARTIFACT_DIR}/create-coaching-session.json" 'payload["data"].get("sessionId") or payload["data"].get("id")')"
if [[ -z "${COACHING_SESSION_ID}" ]]; then
  echo "chat create session returned no coaching session id" >&2
  cat "${ARTIFACT_DIR}/create-coaching-session.json" >&2
  exit 1
fi

smoke_print_step "send coaching message"
COACHING_STATUS="$(
  send_chat_message \
    "${COACHING_SESSION_ID}" \
    "이 정책 신청 준비를 단계별로 도와줘. 자격, 서류, 신청 링크까지 알려줘." \
    "${ARTIFACT_DIR}/coaching-response.json" \
    "${ARTIFACT_DIR}/coaching-body.json" \
    "${ACCESS_TOKEN}" \
    "${RESOLVED_COACH_POLICY_ID}"
)"
smoke_assert_status 200 "${COACHING_STATUS}" "send coaching message" "${ARTIFACT_DIR}/coaching-response.json"

smoke_print_step "get coaching messages"
COACHING_MESSAGES_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/chat/sessions/${COACHING_SESSION_ID}/messages" "${ARTIFACT_DIR}/coaching-messages.json" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${COACHING_MESSAGES_STATUS}" "get coaching messages" "${ARTIFACT_DIR}/coaching-messages.json"

smoke_db_query "
    select role,
           coalesce(referenced_service_ids, ''),
           coalesce(references_json, '')
    from chat_messages
    where session_id = ${COACHING_SESSION_ID}
    order by id;
  " > "${ARTIFACT_DIR}/coaching-db-messages.tsv"

python3 - \
  "${ARTIFACT_DIR}" \
  "${SUMMARY_JSON}" \
  "${SUMMARY_TXT}" \
  "${NOTE_OUT}" \
  "${RESOLVED_COACH_POLICY_ID}" <<'PY'
import json
import pathlib
import sys

artifact_dir = pathlib.Path(sys.argv[1])
summary_json = pathlib.Path(sys.argv[2])
summary_txt = pathlib.Path(sys.argv[3])
note_out = pathlib.Path(sys.argv[4])
coach_policy_id = int(sys.argv[5])

continuity_turns = []
for index in range(1, 4):
    payload = json.loads((artifact_dir / f"continuity-turn-{index}.json").read_text(encoding="utf-8"))["data"]
    references = payload.get("references") or []
    continuity_turns.append({
        "turn": index,
        "answerMode": payload.get("answerMode"),
        "needsClarification": payload.get("needsClarification"),
        "referenceCount": len(references),
        "referenceTitles": [reference.get("title") for reference in references[:3]],
        "answerPreview": (payload.get("answer") or "").replace("\n", " ")[:180],
    })

continuity_messages = json.loads((artifact_dir / "continuity-messages.json").read_text(encoding="utf-8"))["data"]
context_text = (artifact_dir / "continuity-context.tsv").read_text(encoding="utf-8").strip()
context = json.loads(context_text) if context_text else {}
snapshot_rows = [
    line.split("\t")
    for line in (artifact_dir / "continuity-snapshots.tsv").read_text(encoding="utf-8").splitlines()
    if line.strip()
]

recent_questions = context.get("recentUserQuestions") or context.get("memory", {}).get("recentUserQuestions") or []
memory_summary = context.get("summary") or context.get("memory", {}).get("summary") or ""
latest_snapshot = snapshot_rows[-1] if snapshot_rows else []

coaching_payload = json.loads((artifact_dir / "coaching-response.json").read_text(encoding="utf-8"))["data"]
coaching_messages = json.loads((artifact_dir / "coaching-messages.json").read_text(encoding="utf-8"))["data"]
references = coaching_payload.get("references") or []
action_links = references[0].get("actionLinks") if references else []
action_link_types = [link.get("type") for link in (action_links or [])]
coaching_answer = coaching_payload.get("answer") or ""
coaching_db_text = (artifact_dir / "coaching-db-messages.tsv").read_text(encoding="utf-8")

checks = {
    "continuity_all_turns_policy_grounded": all(turn["answerMode"] == "POLICY_GROUNDED" for turn in continuity_turns),
    "continuity_message_count_is_6": len(continuity_messages) == 6,
    "continuity_context_mentions_followups": "전세" in context_text and "월세" in context_text,
    "continuity_context_has_three_recent_questions": len(recent_questions) >= 3,
    "continuity_snapshot_count_is_3": len(snapshot_rows) == 3,
    "continuity_latest_snapshot_has_context_search": bool(latest_snapshot and ("월세" in latest_snapshot[1] or "주거" in latest_snapshot[1])),
    "coaching_answer_mode_application_coaching": coaching_payload.get("answerMode") == "APPLICATION_COACHING",
    "coaching_needs_clarification_false": coaching_payload.get("needsClarification") is False,
    "coaching_pinned_policy_referenced": bool(references and references[0].get("serviceId") == coach_policy_id),
    "coaching_official_apply_link_present": "OFFICIAL_APPLY" in action_link_types,
    "coaching_answer_mentions_application_steps": (
        "신청" in coaching_answer
        and "자격" in coaching_answer
        and ("제출서류" in coaching_answer or "제출 서류" in coaching_answer or "서류" in coaching_answer)
    ),
    "coaching_message_count_is_2": len(coaching_messages) == 2,
    "coaching_db_persisted_references": "OFFICIAL_APPLY" in coaching_db_text and str(coach_policy_id) in coaching_db_text,
}

summary = {
    "coachPolicyId": coach_policy_id,
    "continuityTurns": continuity_turns,
    "continuityMessageCount": len(continuity_messages),
    "continuityRecentQuestions": recent_questions,
    "continuityMemorySummary": memory_summary,
    "continuitySnapshotRows": snapshot_rows,
    "coachingAnswerMode": coaching_payload.get("answerMode"),
    "coachingNeedsClarification": coaching_payload.get("needsClarification"),
    "coachingActionLinkTypes": action_link_types,
    "coachingReference": references[0] if references else None,
    "coachingAnswerPreview": coaching_answer[:500],
    "coachingMessageCount": len(coaching_messages),
    "checks": checks,
}
summary_json.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

failed = [name for name, passed in checks.items() if not passed]
summary_lines = [
    "chat continuity/coaching smoke passed" if not failed else "chat continuity/coaching smoke failed",
    f"coach_policy_id={coach_policy_id}",
    f"continuity_message_count={len(continuity_messages)}",
    f"continuity_snapshot_count={len(snapshot_rows)}",
    f"coaching_answer_mode={coaching_payload.get('answerMode')}",
    f"coaching_action_link_types={','.join(action_link_types)}",
    f"failed_checks={','.join(failed)}",
    f"summary_json={summary_json}",
]
summary_txt.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

note_lines = [
    "# Chat Continuity Coaching Smoke",
    "",
    f"- status: `{'passed' if not failed else 'failed'}`",
    f"- coach_policy_id: `{coach_policy_id}`",
    f"- continuity_message_count: `{len(continuity_messages)}`",
    f"- continuity_snapshot_count: `{len(snapshot_rows)}`",
    f"- coaching_answer_mode: `{coaching_payload.get('answerMode')}`",
    f"- coaching_action_link_types: `{', '.join(action_link_types)}`",
    f"- failed_checks: `{', '.join(failed)}`",
    "",
    "## Continuity Turns",
]
for turn in continuity_turns:
    note_lines.append(
        f"- turn `{turn['turn']}` mode=`{turn['answerMode']}` refs=`{turn['referenceCount']}` "
        f"titles=`{', '.join(title or '' for title in turn['referenceTitles'])}`"
    )
note_out.write_text("\n".join(note_lines) + "\n", encoding="utf-8")

if failed:
    raise SystemExit("\n".join(summary_lines))
PY

smoke_print_step "cleanup sessions"
DELETE_CONTINUITY_STATUS="$(
  smoke_http_status DELETE "${APP_BASE_URL}/api/chat/sessions/${CONTINUITY_SESSION_ID}" "${ARTIFACT_DIR}/delete-continuity-session.json" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${DELETE_CONTINUITY_STATUS}" "delete continuity session" "${ARTIFACT_DIR}/delete-continuity-session.json"
DELETE_COACHING_STATUS="$(
  smoke_http_status DELETE "${APP_BASE_URL}/api/chat/sessions/${COACHING_SESSION_ID}" "${ARTIFACT_DIR}/delete-coaching-session.json" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${DELETE_COACHING_STATUS}" "delete coaching session" "${ARTIFACT_DIR}/delete-coaching-session.json"

smoke_db_query "
    select count(*)
    from chat_retrieval_snapshots
    where session_id in (${CONTINUITY_SESSION_ID}, ${COACHING_SESSION_ID});
  " > "${ARTIFACT_DIR}/post-delete-snapshot-count.txt"
POST_DELETE_SNAPSHOT_COUNT="$(cat "${ARTIFACT_DIR}/post-delete-snapshot-count.txt")"
if [[ "${POST_DELETE_SNAPSHOT_COUNT}" != "0" ]]; then
  echo "expected deleted chat sessions to cascade retrieval snapshots, got ${POST_DELETE_SNAPSHOT_COUNT}" >&2
  exit 1
fi
PRINT_CONTINUITY_SESSION_ID="${CONTINUITY_SESSION_ID}"
PRINT_COACHING_SESSION_ID="${COACHING_SESSION_ID}"
CONTINUITY_SESSION_ID=""
COACHING_SESSION_ID=""
smoke_sanitize_artifacts "${ARTIFACT_DIR}"

smoke_sanitize_artifacts "${ARTIFACT_DIR}"
smoke_publish_dir_snapshot "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
smoke_publish_file "${SUMMARY_TXT}" "${LATEST_SUMMARY_LINK}"
smoke_publish_file "${SUMMARY_JSON}" "${LATEST_JSON_LINK}"
smoke_publish_file "${NOTE_OUT}" "${LATEST_NOTE_LINK}"

cat "${SUMMARY_TXT}"
echo "artifact_dir=${ARTIFACT_DIR}"
echo "smoke_email=${SMOKE_EMAIL}"
echo "continuity_session_id=${PRINT_CONTINUITY_SESSION_ID}"
echo "coaching_session_id=${PRINT_COACHING_SESSION_ID}"
echo "post_delete_snapshot_count=${POST_DELETE_SNAPSHOT_COUNT}"
echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
echo "latest_note_link=${LATEST_NOTE_LINK}"
