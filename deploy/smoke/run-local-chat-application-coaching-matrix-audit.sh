#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-chat.coaching.matrix}"
SMOKE_NAME="${SMOKE_NAME:-점검사용자}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-서울특별시}"
SMOKE_SGG="${SMOKE_SGG:-마포구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
SMOKE_USER_AGENT="${SMOKE_USER_AGENT:-youth-welfare-chat-coaching-matrix/${RUN_TS_UTC:-$(smoke_now_ts_utc)}}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"
CHAT_APPLICATION_COACHING_MATRIX_MIN_POLICY_ROWS="${CHAT_APPLICATION_COACHING_MATRIX_MIN_POLICY_ROWS:-10}"
RUN_TS_UTC="${RUN_TS_UTC:-$(smoke_now_ts_utc)}"
ARTIFACT_ROOT="${ARTIFACT_ROOT:-${ROOT_DIR}/tmp/chat-application-coaching-matrix-audit}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${RUN_TS_UTC}}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"
COACHING_SCENARIOS="${COACHING_SCENARIOS:-many_links:12291:5:OFFICIAL_APPLY,RELATED_SITE;no_links:15221:0:;closed_reference:15232:1:NOTICE;closed_apply:705:1:OFFICIAL_APPLY}"

COOKIE_JAR="${ARTIFACT_DIR}/user.cookie"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SUMMARY_TXT="${ARTIFACT_DIR}/chat-application-coaching-matrix-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/chat-application-coaching-matrix-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/chat-application-coaching-matrix-note.md"
ACCESS_TOKEN=""
CREATED_SESSIONS=()

mkdir -p "${ARTIFACT_DIR}"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ -n "${ACCESS_TOKEN}" ]]; then
    for session_id in "${CREATED_SESSIONS[@]}"; do
      if [[ -n "${session_id}" ]]; then
        curl -sS -o /dev/null -X DELETE "${APP_BASE_URL}/api/chat/sessions/${session_id}" \
          -H "Authorization: Bearer ${ACCESS_TOKEN}" || true
      fi
    done
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
  local coach_policy_id="$3"

  python3 - "$output_file" "$content" "$coach_policy_id" <<'PY'
import json
import sys

output_file, content, coach_policy_id = sys.argv[1], sys.argv[2], sys.argv[3]
with open(output_file, "w", encoding="utf-8") as fp:
    json.dump({"content": content, "coachPolicyId": int(coach_policy_id)}, fp, ensure_ascii=False)
PY
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
if (( POLICY_ROW_COUNT < CHAT_APPLICATION_COACHING_MATRIX_MIN_POLICY_ROWS )); then
  cat > "${SUMMARY_TXT}" <<EOF
chat_application_coaching_matrix_audit=skipped
skip_reason=INSUFFICIENT_POLICY_CORPUS
policy_row_count=${POLICY_ROW_COUNT}
min_policy_rows=${CHAT_APPLICATION_COACHING_MATRIX_MIN_POLICY_ROWS}
scenario_count=0
failed_checks=
artifact_dir=${ARTIFACT_DIR}
EOF
  python3 - "${SUMMARY_JSON}" "${NOTE_OUT}" "${ARTIFACT_DIR}" "${POLICY_ROW_COUNT}" "${CHAT_APPLICATION_COACHING_MATRIX_MIN_POLICY_ROWS}" <<'PY'
import json
import sys
from pathlib import Path

summary_json = Path(sys.argv[1])
note_out = Path(sys.argv[2])
artifact_dir = sys.argv[3]
policy_row_count = int(sys.argv[4])
min_policy_rows = int(sys.argv[5])

summary_json.write_text(json.dumps({
    "chatApplicationCoachingMatrixAudit": "skipped",
    "skipReason": "INSUFFICIENT_POLICY_CORPUS",
    "artifactDir": artifact_dir,
    "policyRowCount": policy_row_count,
    "minPolicyRows": min_policy_rows,
    "failedChecks": [],
    "scenarios": [],
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_out.write_text(
    "\n".join([
        "# Chat Application Coaching Matrix Audit",
        "",
        "- status: `skipped`",
        "- skip_reason: `INSUFFICIENT_POLICY_CORPUS`",
        f"- policy_row_count: `{policy_row_count}`",
        f"- min_policy_rows: `{min_policy_rows}`",
        "",
        "The matrix requires representative policy rows with known action-link shapes.",
    ]) + "\n",
    encoding="utf-8",
)
PY
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  smoke_update_links \
    "${ARTIFACT_DIR}" "${ARTIFACT_ROOT}/latest" \
    "${SUMMARY_TXT}" "${ARTIFACT_ROOT}/latest-chat-application-coaching-matrix-summary.txt" \
    "${SUMMARY_JSON}" "${ARTIFACT_ROOT}/latest-chat-application-coaching-matrix-summary.json" \
    "${NOTE_OUT}" "${ARTIFACT_ROOT}/latest-chat-application-coaching-matrix-note.md"
  cat "${SUMMARY_TXT}"
  exit 0
fi

SMOKE_EMAIL="$(smoke_build_email "${SMOKE_EMAIL_PREFIX}")"

smoke_print_step "signup ${SMOKE_EMAIL}"
smoke_seed_verified_email "${SMOKE_EMAIL}"
SIGNUP_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/signup" "${ARTIFACT_DIR}/signup.json" \
    -H "User-Agent: ${SMOKE_USER_AGENT}" \
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
    -H "User-Agent: ${SMOKE_USER_AGENT}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\"
    }"
)"
smoke_assert_status 200 "${LOGIN_STATUS}" "login" "${ARTIFACT_DIR}/login.json"
ACCESS_TOKEN="$(extract_json "${ARTIFACT_DIR}/login.json" 'payload["data"]["accessToken"]')"

IFS=';' read -r -a SCENARIOS <<< "${COACHING_SCENARIOS}"
for raw_scenario in "${SCENARIOS[@]}"; do
  [[ -n "${raw_scenario}" ]] || continue
  IFS=':' read -r scenario_key policy_id min_links required_types <<< "${raw_scenario}"
  scenario_dir="${ARTIFACT_DIR}/${scenario_key}"
  mkdir -p "${scenario_dir}"

  smoke_print_step "create coaching session ${scenario_key}"
  create_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/chat/sessions" "${scenario_dir}/create-session.json" \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      -H 'Content-Type: application/json' \
      -d "{\"title\":\"coaching-${scenario_key}\"}"
  )"
  smoke_assert_status 200 "${create_status}" "create coaching session (${scenario_key})" "${scenario_dir}/create-session.json"
  session_id="$(extract_json "${scenario_dir}/create-session.json" 'payload["data"].get("sessionId") or payload["data"].get("id")')"
  if [[ -z "${session_id}" ]]; then
    echo "chat create session returned no session id for ${scenario_key}" >&2
    cat "${scenario_dir}/create-session.json" >&2
    exit 1
  fi
  CREATED_SESSIONS+=("${session_id}")

  smoke_print_step "send coaching ${scenario_key} policy ${policy_id}"
  write_chat_body "${scenario_dir}/body.json" "이 정책 신청 준비를 단계별로 도와줘. 자격, 서류, 신청 링크까지 알려줘." "${policy_id}"
  send_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/chat/sessions/${session_id}/messages" "${scenario_dir}/response.json" \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      -H 'Content-Type: application/json' \
      --data-binary "@${scenario_dir}/body.json"
  )"
  smoke_assert_status 200 "${send_status}" "send coaching (${scenario_key})" "${scenario_dir}/response.json"

  printf '%s\t%s\t%s\t%s\n' "${scenario_key}" "${policy_id}" "${min_links}" "${required_types}" >> "${ARTIFACT_DIR}/scenario-expectations.tsv"
done

python3 - "${ARTIFACT_DIR}" "${SUMMARY_TXT}" "${SUMMARY_JSON}" "${NOTE_OUT}" <<'PY'
import json
import sys
from pathlib import Path

artifact_dir = Path(sys.argv[1])
summary_txt = Path(sys.argv[2])
summary_json = Path(sys.argv[3])
note_out = Path(sys.argv[4])

expectations = []
for line in (artifact_dir / "scenario-expectations.tsv").read_text(encoding="utf-8").splitlines():
    if not line.strip():
        continue
    scenario_key, policy_id, min_links, required_types = line.split("\t")
    expectations.append({
        "scenarioKey": scenario_key,
        "policyId": int(policy_id),
        "minLinks": int(min_links),
        "requiredTypes": [item for item in required_types.split(",") if item],
    })

scenarios = []
failed = []
for expectation in expectations:
    scenario_dir = artifact_dir / expectation["scenarioKey"]
    payload = json.loads((scenario_dir / "response.json").read_text(encoding="utf-8"))["data"]
    references = payload.get("references") or []
    action_links = references[0].get("actionLinks") if references else []
    action_link_types = [link.get("type") for link in (action_links or [])]
    answer = payload.get("answer") or ""
    checks = {
        "answer_mode_application_coaching": payload.get("answerMode") == "APPLICATION_COACHING",
        "needs_clarification_false": payload.get("needsClarification") is False,
        "pinned_policy_referenced": bool(references and references[0].get("serviceId") == expectation["policyId"]),
        "min_action_links": len(action_links or []) >= expectation["minLinks"],
        "required_action_link_types": all(required in action_link_types for required in expectation["requiredTypes"]),
        "answer_mentions_application_readiness": (
            "신청" in answer
            and "자격" in answer
            and ("서류" in answer or "제출서류" in answer or "제출 서류" in answer)
        ),
    }
    scenario_failed = [name for name, passed in checks.items() if not passed]
    if scenario_failed:
        failed.append(f"{expectation['scenarioKey']}:{','.join(scenario_failed)}")
    scenarios.append({
        **expectation,
        "answerMode": payload.get("answerMode"),
        "needsClarification": payload.get("needsClarification"),
        "referenceCount": len(references),
        "referenceTitle": references[0].get("title") if references else None,
        "actionLinkCount": len(action_links or []),
        "actionLinkTypes": action_link_types,
        "answerPreview": answer.replace("\n", " ")[:220],
        "checks": checks,
    })

summary_lines = [
    "chat_application_coaching_matrix_audit=passed" if not failed else "chat_application_coaching_matrix_audit=failed",
    f"artifact_dir={artifact_dir}",
    f"scenario_count={len(scenarios)}",
    f"failed_checks={';'.join(failed)}",
]
for scenario in scenarios:
    summary_lines.append(
        "scenario="
        f"{scenario['scenarioKey']}|policy_id={scenario['policyId']}|mode={scenario['answerMode']}|"
        f"refs={scenario['referenceCount']}|links={scenario['actionLinkCount']}|"
        f"types={','.join(scenario['actionLinkTypes'])}|title={scenario['referenceTitle']}"
    )
summary_txt.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

summary_json.write_text(json.dumps({
    "artifactDir": str(artifact_dir),
    "failedChecks": failed,
    "scenarios": scenarios,
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Chat Application Coaching Matrix Audit",
    "",
    f"- scenario_count: `{len(scenarios)}`",
    f"- failed_checks: `{';'.join(failed)}`",
    "",
    "## Scenarios",
]
for scenario in scenarios:
    note_lines.append(
        f"- `{scenario['scenarioKey']}` policy=`{scenario['policyId']}` mode=`{scenario['answerMode']}` "
        f"links=`{scenario['actionLinkCount']}` types=`{', '.join(scenario['actionLinkTypes'])}` title=`{scenario['referenceTitle']}`"
    )
note_out.write_text("\n".join(note_lines) + "\n", encoding="utf-8")

if failed:
    raise SystemExit("\n".join(summary_lines))
PY

smoke_sanitize_artifacts "${ARTIFACT_DIR}"
smoke_update_links \
  "${ARTIFACT_DIR}" "${ARTIFACT_ROOT}/latest" \
  "${SUMMARY_TXT}" "${ARTIFACT_ROOT}/latest-chat-application-coaching-matrix-summary.txt" \
  "${SUMMARY_JSON}" "${ARTIFACT_ROOT}/latest-chat-application-coaching-matrix-summary.json" \
  "${NOTE_OUT}" "${ARTIFACT_ROOT}/latest-chat-application-coaching-matrix-note.md"

cat "${SUMMARY_TXT}"
echo "smoke_email=${SMOKE_EMAIL}"
