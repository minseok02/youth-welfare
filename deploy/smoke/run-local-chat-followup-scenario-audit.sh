#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-chat.followup.audit}"
SMOKE_NAME="${SMOKE_NAME:-점검사용자}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-서울특별시}"
SMOKE_SGG="${SMOKE_SGG:-마포구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"
REQUIRE_ALL_LAST_TURNS_POLICY_GROUNDED="${REQUIRE_ALL_LAST_TURNS_POLICY_GROUNDED:-true}"
RUN_TS_UTC="${RUN_TS_UTC:-$(smoke_now_ts_utc)}"
ARTIFACT_ROOT="${ARTIFACT_ROOT:-${ROOT_DIR}/tmp/chat-followup-scenario-audit}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${RUN_TS_UTC}}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"

COOKIE_JAR="${ARTIFACT_DIR}/user.cookie"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SUMMARY_OUT="${ARTIFACT_DIR}/chat-followup-scenario-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/chat-followup-scenario-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/chat-followup-scenario-note.md"

mkdir -p "${ARTIFACT_DIR}"

cleanup() {
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

send_chat_message() {
  local session_id="$1"
  local content="$2"
  local response_file="$3"
  local access_token="$4"
  smoke_http_status POST "${APP_BASE_URL}/api/chat/sessions/${session_id}/messages" "${response_file}" \
    -H "Authorization: Bearer ${access_token}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"content\": \"${content}\"
    }"
}

signup_and_login() {
  local scenario_key="$1"
  local scenario_dir="$2"
  local signup_response="${scenario_dir}/signup.json"
  local login_response="${scenario_dir}/login.json"
  local smoke_email
  local cookie_jar="${scenario_dir}/user.cookie"

  smoke_email="$(smoke_build_email "${SMOKE_EMAIL_PREFIX}.${scenario_key}")"
  smoke_print_step "signup ${smoke_email}"
  smoke_seed_verified_email "${smoke_email}"
  local signup_status
  signup_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/auth/signup" "${signup_response}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"email\": \"${smoke_email}\",
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
  smoke_assert_status 200 "${signup_status}" "signup (${scenario_key})" "${signup_response}"

  smoke_print_step "login ${scenario_key}"
  local login_status
  login_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${login_response}" \
      -c "${cookie_jar}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"email\": \"${smoke_email}\",
        \"password\": \"${SMOKE_PASSWORD}\"
      }"
  )"
  smoke_assert_status 200 "${login_status}" "login (${scenario_key})" "${login_response}"
  ACCESS_TOKEN="$(extract_json "${login_response}" 'payload["data"]["accessToken"]')"
}

run_scenario() {
  local scenario_key="$1"
  shift
  local scenario_dir="${ARTIFACT_DIR}/${scenario_key}"
  mkdir -p "${scenario_dir}"
  local create_response="${scenario_dir}/create-session.json"
  local messages_response="${scenario_dir}/messages.json"
  local delete_response="${scenario_dir}/delete-session.json"

  signup_and_login "${scenario_key}" "${scenario_dir}"

  smoke_print_step "chat scenario ${scenario_key}"
  local create_status
  create_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/chat/sessions" "${create_response}" \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      -H 'Content-Type: application/json' \
      -d "{\"title\":\"${scenario_key}\"}"
  )"
  smoke_assert_status 200 "${create_status}" "chat create session (${scenario_key})" "${create_response}"

  local session_id
  session_id="$(extract_json "${create_response}" 'payload["data"].get("sessionId") or payload["data"].get("id")')"
  if [[ -z "${session_id}" ]]; then
    echo "chat scenario ${scenario_key} returned no session id" >&2
    cat "${create_response}" >&2
    exit 1
  fi

  local turn=1
  for content in "$@"; do
    local turn_response="${scenario_dir}/turn-${turn}.json"
    local send_status
    send_status="$(send_chat_message "${session_id}" "${content}" "${turn_response}" "${ACCESS_TOKEN}")"
    smoke_assert_status 200 "${send_status}" "chat send turn ${turn} (${scenario_key})" "${turn_response}"
    turn=$((turn + 1))
  done

  local messages_status
  messages_status="$(
    smoke_http_status GET "${APP_BASE_URL}/api/chat/sessions/${session_id}/messages" "${messages_response}" \
      -H "Authorization: Bearer ${ACCESS_TOKEN}"
  )"
  smoke_assert_status 200 "${messages_status}" "chat get messages (${scenario_key})" "${messages_response}"

  local delete_status
  delete_status="$(
    smoke_http_status DELETE "${APP_BASE_URL}/api/chat/sessions/${session_id}" "${delete_response}" \
      -H "Authorization: Bearer ${ACCESS_TOKEN}"
  )"
  smoke_assert_status 200 "${delete_status}" "chat delete session (${scenario_key})" "${delete_response}"
}

smoke_require_command curl
smoke_require_command python3

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

run_scenario "housing-followup" \
  "서울 월세 지원 알려줘" \
  "그럼 전세는?"

run_scenario "housing-branch-freeform" \
  "주거 지원" \
  "월세 쪽으로 보여줘"

run_scenario "mixed-topic-memory" \
  "취업 지원도 있나" \
  "주거 지원도 있나" \
  "월세 쪽으로 보여줘"

run_scenario "job-branch-freeform" \
  "일자리 지원" \
  "인턴 쪽으로 보여줘"

run_scenario "interview-expense" \
  "청년 면접비 지원 알려줘"

run_scenario "certificate-followup" \
  "취업 준비 지원 알려줘" \
  "자격증 응시료 쪽으로 보여줘"

run_scenario "transport-expense" \
  "청년근로자 교통비 지원사업 알려줘"

run_scenario "startup-support" \
  "창업 지원 정책 알려줘" \
  "청년창업센터 사업화자금 쪽으로 보여줘"

run_scenario "youth-allowance" \
  "서울 청년수당 알려줘"

run_scenario "culture-voucher" \
  "청년 동아리 활동비 지원 알려줘"

python3 - "${ARTIFACT_DIR}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${REQUIRE_ALL_LAST_TURNS_POLICY_GROUNDED}" <<'PY'
import json
import sys
from pathlib import Path

artifact_dir = Path(sys.argv[1])
summary_path = Path(sys.argv[2])
json_path = Path(sys.argv[3])
note_path = Path(sys.argv[4])
require_all_policy_grounded = sys.argv[5].lower() == "true"

scenario_dirs = sorted([path for path in artifact_dir.iterdir() if path.is_dir()])
scenario_payloads = []

for scenario_dir in scenario_dirs:
    turn_files = sorted(scenario_dir.glob("turn-*.json"))
    if not turn_files:
        continue
    turns = []
    for turn_file in turn_files:
        payload = json.loads(turn_file.read_text(encoding="utf-8"))
        data = payload["data"]
        references = data.get("references") or []
        turns.append({
            "file": turn_file.name,
            "answerMode": data.get("answerMode"),
            "needsClarification": data.get("needsClarification"),
            "referenceCount": len(references),
            "referenceTitles": [reference.get("title") for reference in references[:3]],
            "branchSuggestionCount": len(data.get("branchSuggestions") or []),
            "answerPreview": (data.get("answer") or "").replace("\n", " ")[:120],
        })

    messages_payload = json.loads((scenario_dir / "messages.json").read_text(encoding="utf-8"))
    scenario_payloads.append({
        "scenarioKey": scenario_dir.name,
        "turnCount": len(turns),
        "messageCount": len(messages_payload.get("data") or []),
        "lastAnswerMode": turns[-1]["answerMode"],
        "lastNeedsClarification": turns[-1]["needsClarification"],
        "lastReferenceCount": turns[-1]["referenceCount"],
        "lastBranchSuggestionCount": turns[-1]["branchSuggestionCount"],
        "turns": turns,
    })

all_last_modes = {}
clarification_count = 0
policy_grounded_count = 0
branch_suggestion_count = 0
for scenario in scenario_payloads:
    mode = scenario["lastAnswerMode"] or "UNKNOWN"
    all_last_modes[mode] = all_last_modes.get(mode, 0) + 1
    if mode == "CLARIFICATION":
        clarification_count += 1
    elif mode == "POLICY_GROUNDED":
        policy_grounded_count += 1
    elif mode == "BRANCH_SUGGESTION":
        branch_suggestion_count += 1

decision = "HOLD_LONG_TERM_MEMORY"
if clarification_count >= 2 and policy_grounded_count <= 1:
    decision = "CONSIDER_LONG_TERM_MEMORY"

summary_lines = [
    f"scenario_count={len(scenario_payloads)}",
    f"policy_grounded_last_turn_scenarios={policy_grounded_count}",
    f"clarification_last_turn_scenarios={clarification_count}",
    f"branch_suggestion_last_turn_scenarios={branch_suggestion_count}",
    f"decision={decision}",
    f"require_all_last_turns_policy_grounded={str(require_all_policy_grounded).lower()}",
    f"artifact_dir={artifact_dir}",
]
failed_scenarios = [
    scenario["scenarioKey"]
    for scenario in scenario_payloads
    if scenario["lastAnswerMode"] != "POLICY_GROUNDED"
]
for scenario in scenario_payloads:
    summary_lines.append(
        "scenario="
        f"{scenario['scenarioKey']}|turns={scenario['turnCount']}|messages={scenario['messageCount']}|"
        f"last_mode={scenario['lastAnswerMode']}|last_refs={scenario['lastReferenceCount']}|"
        f"last_branch_suggestions={scenario['lastBranchSuggestionCount']}|"
        f"last_titles={','.join(scenario['turns'][-1]['referenceTitles'])}"
    )
if failed_scenarios:
    summary_lines.append(f"failed_policy_grounded_scenarios={','.join(failed_scenarios)}")
summary_path.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "scenarioCount": len(scenario_payloads),
    "decision": decision,
    "lastAnswerModes": all_last_modes,
    "requireAllLastTurnsPolicyGrounded": require_all_policy_grounded,
    "failedPolicyGroundedScenarios": failed_scenarios,
    "scenarios": scenario_payloads,
}
json_path.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Chat Follow-up Scenario Audit",
    "",
    f"- scenario_count: `{len(scenario_payloads)}`",
    f"- policy_grounded_last_turn_scenarios: `{policy_grounded_count}`",
    f"- clarification_last_turn_scenarios: `{clarification_count}`",
    f"- branch_suggestion_last_turn_scenarios: `{branch_suggestion_count}`",
    f"- decision: `{decision}`",
    "",
    "## Scenario results",
]
for scenario in scenario_payloads:
    note_lines.append(
        f"- `{scenario['scenarioKey']}`: last=`{scenario['lastAnswerMode']}` refs=`{scenario['lastReferenceCount']}` messages=`{scenario['messageCount']}`"
    )
    for turn in scenario["turns"]:
        note_lines.append(
            f"  - `{turn['file']}` `{turn['answerMode']}` refs=`{turn['referenceCount']}` titles=`{', '.join(turn['referenceTitles'])}` branchSuggestions=`{turn['branchSuggestionCount']}` answer=`{turn['answerPreview']}`"
        )
note_path.write_text("\n".join(note_lines) + "\n", encoding="utf-8")

if require_all_policy_grounded and failed_scenarios:
    raise SystemExit("last turn was not POLICY_GROUNDED for: " + ", ".join(failed_scenarios))
PY

smoke_update_links \
  "${ARTIFACT_DIR}" "${ARTIFACT_ROOT}/latest" \
  "${SUMMARY_OUT}" "${ARTIFACT_ROOT}/latest-chat-followup-scenario-summary.txt" \
  "${JSON_OUT}" "${ARTIFACT_ROOT}/latest-chat-followup-scenario-summary.json" \
  "${NOTE_OUT}" "${ARTIFACT_ROOT}/latest-chat-followup-scenario-note.md"

cat "${SUMMARY_OUT}"
