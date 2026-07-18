#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-chat.explicit.region.matrix}"
SMOKE_NAME="${SMOKE_NAME:-지역매트릭스점검}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_PROFILE_SIDO="${SMOKE_PROFILE_SIDO:-서울특별시}"
SMOKE_PROFILE_SGG="${SMOKE_PROFILE_SGG:-마포구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
SMOKE_USER_AGENT_PREFIX="${SMOKE_USER_AGENT_PREFIX:-youth-welfare-chat-explicit-region-matrix}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-20}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"
CHAT_EXPLICIT_REGION_MATRIX_MIN_POLICY_ROWS="${CHAT_EXPLICIT_REGION_MATRIX_MIN_POLICY_ROWS:-100}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"
RUN_TS_UTC="${RUN_TS_UTC:-$(smoke_now_ts_utc)}"
ARTIFACT_ROOT="${ARTIFACT_ROOT:-${ROOT_DIR}/tmp/chat-explicit-region-matrix-audit}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${RUN_TS_UTC}}"

HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SCENARIOS_TSV="${ARTIFACT_DIR}/chat-explicit-region-matrix-scenarios.tsv"
PREFLIGHT_TSV="${ARTIFACT_DIR}/chat-explicit-region-matrix-preflight.tsv"
API_TSV="${ARTIFACT_DIR}/chat-explicit-region-matrix-api.tsv"
CANDIDATES_TSV="${ARTIFACT_DIR}/chat-explicit-region-matrix-candidates.tsv"
SUMMARY_OUT="${ARTIFACT_DIR}/chat-explicit-region-matrix-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/chat-explicit-region-matrix-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/chat-explicit-region-matrix-note.md"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ "${KEEP_ARTIFACTS}" != "true" ]]; then
    rm -rf "${ARTIFACT_DIR}"
  fi
}
trap cleanup EXIT

mkdir -p "${ARTIFACT_DIR}"

write_json_payload() {
  local output_file="$1"
  shift
  python3 - "$output_file" "$@" <<'PY'
import json
import sys
from pathlib import Path

out = Path(sys.argv[1])
payload = {}
for raw in sys.argv[2:]:
    key, value = raw.split("=", 1)
    if value == "__true__":
        payload[key] = True
    elif value == "__false__":
        payload[key] = False
    elif value.isdigit():
        payload[key] = int(value)
    else:
        payload[key] = value
out.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
PY
}

json_value() {
  local response_file="$1"
  local expression="$2"
  python3 - "$response_file" "$expression" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

value = eval(sys.argv[2], {"__builtins__": {}, "len": len, "str": str}, {"payload": payload})
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

cat > "${SCENARIOS_TSV}" <<'EOF'
scenario_key	question	expected_region_code	expected_sido	expected_sgg	expected_category	require_region_specific	note
incheon-junggu-housing	인천 중구 청년이 받을 수 있는 주거 지원을 알려줘	28110	인천광역시	중구	주거	true	accepted fix regression
seoul-mapo-housing	서울 마포구 청년 월세 지원 알려줘	11440	서울특별시	마포구	주거	true	profile-overridable explicit region
busan-haeundae-finance	부산 해운대구 청년 금융 지원 알려줘	26350	부산광역시	해운대구	금융·생활지원	true	non-housing explicit category
seongnam-housing-parent	경기 성남시 청년 주거 지원 알려줘	41130	경기도	성남시	주거	true	parent and child district region case
gwangju-bukgu-housing-empty	광주 북구 청년 주거 지원 알려줘	29170	광주광역시	북구	주거	false	no local housing candidate expected by preflight
ambiguous-junggu-housing	중구 청년 주거 지원 알려줘	11440	서울특별시	마포구	주거	false	ambiguous sgg-only should not become explicit hard filter
EOF

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

smoke_print_step "policy corpus precheck"
POLICY_ROW_COUNT="$(smoke_db_query "select count(*) from welfare_services where search_youth_relevant is true and status in ('ACTIVE','UPCOMING');" | head -n 1)"
POLICY_ROW_COUNT="${POLICY_ROW_COUNT//[^0-9]/}"
POLICY_ROW_COUNT="${POLICY_ROW_COUNT:-0}"
if (( POLICY_ROW_COUNT < CHAT_EXPLICIT_REGION_MATRIX_MIN_POLICY_ROWS )); then
  {
    echo "chat_explicit_region_matrix_audit=skipped"
    echo "skip_reason=INSUFFICIENT_POLICY_CORPUS"
    echo "policy_row_count=${POLICY_ROW_COUNT}"
    echo "min_policy_rows=${CHAT_EXPLICIT_REGION_MATRIX_MIN_POLICY_ROWS}"
    echo "artifact_dir=${ARTIFACT_DIR}"
  } | tee "${SUMMARY_OUT}"
  exit 0
fi

smoke_print_step "region/category preflight"
smoke_db_query "
with scenarios(scenario_key, question, expected_region_code, expected_sido, expected_sgg, expected_category, require_region_specific, note) as (
  values
    ('incheon-junggu-housing','인천 중구 청년이 받을 수 있는 주거 지원을 알려줘','28110','인천광역시','중구','주거',true,'accepted fix regression'),
    ('seoul-mapo-housing','서울 마포구 청년 월세 지원 알려줘','11440','서울특별시','마포구','주거',true,'profile-overridable explicit region'),
    ('busan-haeundae-finance','부산 해운대구 청년 금융 지원 알려줘','26350','부산광역시','해운대구','금융·생활지원',true,'non-housing explicit category'),
    ('seongnam-housing-parent','경기 성남시 청년 주거 지원 알려줘','41130','경기도','성남시','주거',true,'parent and child district region case'),
    ('gwangju-bukgu-housing-empty','광주 북구 청년 주거 지원 알려줘','29170','광주광역시','북구','주거',false,'no local housing candidate expected by preflight'),
    ('ambiguous-junggu-housing','중구 청년 주거 지원 알려줘','11440','서울특별시','마포구','주거',false,'ambiguous sgg-only should not become explicit hard filter')
),
matches as (
  select s.scenario_key,
         count(distinct ws.id) filter (
           where sr.region_code = s.expected_region_code
              or (sr.sido_name = s.expected_sido and sr.sgg_name = s.expected_sgg)
         ) as exact_match_count,
         count(distinct ws.id) filter (
           where s.scenario_key = 'seongnam-housing-parent'
             and (
               sr.region_code in ('41131','41133','41135')
               or (sr.sido_name = '경기도' and sr.sgg_name like '성남시 %')
             )
         ) as child_match_count,
         count(distinct ws.id) as same_category_region_rows
  from scenarios s
  left join service_regions sr
    on (
      sr.region_code = s.expected_region_code
      or (sr.sido_name = s.expected_sido and sr.sgg_name = s.expected_sgg)
      or (
        s.scenario_key = 'seongnam-housing-parent'
        and (
          sr.region_code in ('41131','41133','41135')
          or (sr.sido_name = '경기도' and sr.sgg_name like '성남시 %')
        )
      )
    )
  left join welfare_services ws
    on ws.id = sr.service_id
   and ws.search_youth_relevant is true
   and ws.status in ('ACTIVE','UPCOMING')
   and ws.unified_category = s.expected_category
  group by s.scenario_key
)
select s.scenario_key,
       s.expected_region_code,
       s.expected_sido,
       s.expected_sgg,
       s.expected_category,
       s.require_region_specific,
       coalesce(m.exact_match_count, 0),
       coalesce(m.child_match_count, 0),
       coalesce(m.same_category_region_rows, 0),
       s.note
from scenarios s
left join matches m on m.scenario_key = s.scenario_key
order by s.scenario_key;
" > "${PREFLIGHT_TSV}"

printf "scenario_key\tstatus\tsession_id\tanswer_mode\tneeds_clarification\treference_ids\treference_titles\tanswer_length\n" > "${API_TSV}"

while IFS=$'\t' read -r scenario_key question expected_region_code expected_sido expected_sgg expected_category require_region_specific note; do
  if [[ "${scenario_key}" == "scenario_key" ]]; then
    continue
  fi

  scenario_dir="${ARTIFACT_DIR}/${scenario_key}"
  mkdir -p "${scenario_dir}"
  email="$(smoke_build_email "${SMOKE_EMAIL_PREFIX}.${scenario_key}")"
  user_agent="${SMOKE_USER_AGENT_PREFIX}/${scenario_key}/${RUN_TS_UTC}"

  smoke_print_step "signup ${scenario_key}"
  smoke_seed_verified_email "${email}"
  write_json_payload "${scenario_dir}/signup-payload.json" \
    "email=${email}" \
    "password=${SMOKE_PASSWORD}" \
    "name=${SMOKE_NAME}" \
    "birthDate=${SMOKE_BIRTH_DATE}" \
    "privacyNoticeConfirmed=__true__" \
    "optionalProfileConsentAgreed=__true__" \
    "sido=${SMOKE_PROFILE_SIDO}" \
    "sgg=${SMOKE_PROFILE_SGG}" \
    "incomeLevel=${SMOKE_INCOME_LEVEL}" \
    "employmentStatus=${SMOKE_EMPLOYMENT_STATUS}" \
    "householdType=${SMOKE_HOUSEHOLD_TYPE}"
  signup_status="$(smoke_http_status POST "${APP_BASE_URL}/api/auth/signup" "${scenario_dir}/signup.json" \
    -H "User-Agent: ${user_agent}" \
    -H 'Content-Type: application/json' \
    --data-binary @"${scenario_dir}/signup-payload.json")"
  smoke_assert_status 200 "${signup_status}" "signup ${scenario_key}" "${scenario_dir}/signup.json"

  smoke_print_step "login ${scenario_key}"
  write_json_payload "${scenario_dir}/login-payload.json" \
    "email=${email}" \
    "password=${SMOKE_PASSWORD}"
  login_status="$(smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${scenario_dir}/login.json" \
    -H "User-Agent: ${user_agent}" \
    -H 'Content-Type: application/json' \
    --data-binary @"${scenario_dir}/login-payload.json")"
  smoke_assert_status 200 "${login_status}" "login ${scenario_key}" "${scenario_dir}/login.json"
  access_token="$(json_value "${scenario_dir}/login.json" 'payload["data"]["accessToken"]')"

  smoke_print_step "chat ${scenario_key}"
  write_json_payload "${scenario_dir}/create-session-payload.json" "title=${scenario_key}"
  create_status="$(smoke_http_status POST "${APP_BASE_URL}/api/chat/sessions" "${scenario_dir}/create-session.json" \
    -H "User-Agent: ${user_agent}" \
    -H "Authorization: Bearer ${access_token}" \
    -H 'Content-Type: application/json' \
    --data-binary @"${scenario_dir}/create-session-payload.json")"
  smoke_assert_status 200 "${create_status}" "create chat session ${scenario_key}" "${scenario_dir}/create-session.json"
  session_id="$(json_value "${scenario_dir}/create-session.json" 'payload["data"].get("sessionId") or payload["data"].get("id")')"

  write_json_payload "${scenario_dir}/message-payload.json" "content=${question}"
  send_status="$(smoke_http_status POST "${APP_BASE_URL}/api/chat/sessions/${session_id}/messages" "${scenario_dir}/message.json" \
    -H "User-Agent: ${user_agent}" \
    -H "Authorization: Bearer ${access_token}" \
    -H 'Content-Type: application/json' \
    --data-binary @"${scenario_dir}/message-payload.json")"

  answer_mode=""
  needs_clarification=""
  reference_ids=""
  reference_titles=""
  answer_length="0"
  if [[ "${send_status}" == "200" ]]; then
    answer_mode="$(json_value "${scenario_dir}/message.json" 'payload["data"].get("answerMode")')"
    needs_clarification="$(json_value "${scenario_dir}/message.json" 'payload["data"].get("needsClarification")')"
    reference_ids="$(json_value "${scenario_dir}/message.json" '",".join(str(ref.get("serviceId")) for ref in (payload["data"].get("references") or []))')"
    reference_titles="$(json_value "${scenario_dir}/message.json" '",".join(str(ref.get("title")) for ref in (payload["data"].get("references") or []))')"
    answer_length="$(json_value "${scenario_dir}/message.json" 'len(payload["data"].get("answer") or "")')"
  fi

  printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n" \
    "${scenario_key}" \
    "${send_status}" \
    "${session_id}" \
    "${answer_mode}" \
    "${needs_clarification}" \
    "${reference_ids}" \
    "${reference_titles}" \
    "${answer_length}" >> "${API_TSV}"
done < "${SCENARIOS_TSV}"

scenario_values_sql="$(python3 - "${API_TSV}" "${SCENARIOS_TSV}" <<'PY'
import csv
import sys

api_path, scenarios_path = sys.argv[1], sys.argv[2]
api_by_key = {}
with open(api_path, encoding="utf-8") as fp:
    for row in csv.DictReader(fp, delimiter="\t"):
        api_by_key[row["scenario_key"]] = row

def quote(value):
    return "'" + (value or "").replace("'", "''") + "'"

values = []
with open(scenarios_path, encoding="utf-8") as fp:
    for row in csv.DictReader(fp, delimiter="\t"):
        api = api_by_key.get(row["scenario_key"], {})
        session_id = api.get("session_id") or "0"
        if not session_id.isdigit():
            session_id = "0"
        values.append("(" + ",".join([
            quote(row["scenario_key"]),
            str(session_id),
            quote(row["expected_region_code"]),
            quote(row["expected_sido"]),
            quote(row["expected_sgg"]),
            quote(row["expected_category"]),
            "true" if row["require_region_specific"].lower() == "true" else "false",
            quote(row["note"]),
        ]) + ")")
print(",\n    ".join(values))
PY
)"

smoke_print_step "snapshot candidate audit"
smoke_db_query "
with scenarios(scenario_key, session_id, expected_region_code, expected_sido, expected_sgg, expected_category, require_region_specific, note) as (
  values
    ${scenario_values_sql}
),
selected_snapshots as (
  select distinct on (s.scenario_key)
         s.scenario_key,
         s.expected_region_code,
         s.expected_sido,
         s.expected_sgg,
         s.expected_category,
         s.require_region_specific,
         crs.id as snapshot_id,
         crs.session_id,
         crs.created_at,
         crs.question,
         crs.search_keyword,
         crs.fts_service_ids_json,
         crs.semantic_service_ids_json,
         crs.merged_service_ids_json
  from scenarios s
  left join chat_retrieval_snapshots crs
    on crs.session_id = s.session_id
   and crs.snapshot_type = 'INTERACTIVE'
  order by s.scenario_key, crs.created_at desc nulls last, crs.id desc nulls last
),
candidate_ids as (
  select ss.scenario_key,
         ss.expected_region_code,
         ss.expected_sido,
         ss.expected_sgg,
         ss.expected_category,
         ss.require_region_specific,
         ss.snapshot_id,
         ss.session_id,
         ss.created_at,
         ss.question,
         ss.search_keyword,
         source.source_name,
         candidate.ordinality::int as candidate_rank,
         candidate.service_id::bigint as service_id
  from selected_snapshots ss
  cross join lateral (
    values
      ('fts', coalesce(nullif(ss.fts_service_ids_json, ''), '[]')::jsonb),
      ('semantic', coalesce(nullif(ss.semantic_service_ids_json, ''), '[]')::jsonb),
      ('merged', coalesce(nullif(ss.merged_service_ids_json, ''), '[]')::jsonb)
  ) as source(source_name, ids_json)
  cross join lateral jsonb_array_elements_text(source.ids_json) with ordinality as candidate(service_id, ordinality)
),
regions as (
  select ci.scenario_key,
         sr.service_id,
         count(*) as region_rows,
         bool_or(
           (ci.expected_region_code <> '' and sr.region_code = ci.expected_region_code)
           or (
             ci.expected_sido <> ''
             and sr.sido_name = ci.expected_sido
             and (ci.expected_sgg = '' or sr.sgg_name = ci.expected_sgg)
           )
         ) as exact_region_match,
         bool_or(
           ci.scenario_key = 'seongnam-housing-parent'
           and (
             sr.region_code in ('41131','41133','41135')
             or (sr.sido_name = '경기도' and sr.sgg_name like '성남시 %')
           )
         ) as child_region_match,
         bool_or(
           ci.expected_sido <> ''
           and sr.sido_name = ci.expected_sido
           and coalesce(sr.sgg_name, '') = ''
         ) as sido_wide_region_match,
         string_agg(
           distinct trim(coalesce(sr.region_code, '') || ' ' || coalesce(sr.sido_name, '') || ' ' || coalesce(sr.sgg_name, '')),
           ', '
           order by trim(coalesce(sr.region_code, '') || ' ' || coalesce(sr.sido_name, '') || ' ' || coalesce(sr.sgg_name, ''))
         ) as region_labels
  from candidate_ids ci
  join service_regions sr on sr.service_id = ci.service_id
  group by ci.scenario_key, sr.service_id
)
select ci.scenario_key,
       ci.snapshot_id,
       ci.session_id,
       to_char(ci.created_at at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') as created_at_utc,
       ci.source_name,
       ci.candidate_rank,
       ci.service_id,
       replace(ws.title, chr(9), ' ') as title,
       ws.source_type,
       coalesce(ws.unified_category, '') as unified_category,
       case
         when coalesce(r.region_rows, 0) = 0 then 'NO_REGION_ROW'
         when r.exact_region_match then 'REGION_MATCH'
         when r.child_region_match then 'CHILD_REGION_MATCH'
         when r.sido_wide_region_match then 'SIDO_WIDE_MATCH'
         else 'REGION_MISMATCH'
       end as region_match_class,
       coalesce(r.region_labels, '') as regions,
       ci.expected_region_code,
       ci.expected_sido,
       ci.expected_sgg,
       ci.expected_category,
       ci.require_region_specific,
       replace(coalesce(ci.question, ''), chr(9), ' ') as question,
       replace(coalesce(ci.search_keyword, ''), chr(9), ' ') as search_keyword
from candidate_ids ci
join welfare_services ws on ws.id = ci.service_id
left join regions r
  on r.scenario_key = ci.scenario_key
 and r.service_id = ci.service_id
order by ci.scenario_key, ci.source_name, ci.candidate_rank;
" > "${CANDIDATES_TSV}"

python3 - "${SCENARIOS_TSV}" "${PREFLIGHT_TSV}" "${API_TSV}" "${CANDIDATES_TSV}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" <<'PY'
import csv
import json
import sys
from collections import defaultdict
from pathlib import Path

scenarios_path = Path(sys.argv[1])
preflight_path = Path(sys.argv[2])
api_path = Path(sys.argv[3])
candidates_path = Path(sys.argv[4])
summary_path = Path(sys.argv[5])
json_path = Path(sys.argv[6])
note_path = Path(sys.argv[7])
artifact_dir = sys.argv[8]

scenarios = []
with scenarios_path.open(encoding="utf-8") as fp:
    scenarios = list(csv.DictReader(fp, delimiter="\t"))

preflight = {}
with preflight_path.open(encoding="utf-8") as fp:
    for row in csv.reader(fp, delimiter="\t"):
        if not row:
            continue
        preflight[row[0]] = {
            "scenarioKey": row[0],
            "expectedRegionCode": row[1],
            "expectedSido": row[2],
            "expectedSgg": row[3],
            "expectedCategory": row[4],
            "requireRegionSpecific": row[5] == "t",
            "exactMatchCount": int(row[6] or 0),
            "childMatchCount": int(row[7] or 0),
            "sameCategoryRegionRows": int(row[8] or 0),
            "note": row[9] if len(row) > 9 else "",
        }

api = {}
with api_path.open(encoding="utf-8") as fp:
    for row in csv.DictReader(fp, delimiter="\t"):
        api[row["scenario_key"]] = row

candidates_by_scenario = defaultdict(list)
with candidates_path.open(encoding="utf-8") as fp:
    for row in csv.reader(fp, delimiter="\t"):
        if not row:
            continue
        candidates_by_scenario[row[0]].append({
            "snapshotId": row[1],
            "sessionId": row[2],
            "sourceName": row[4],
            "rank": int(row[5]),
            "serviceId": int(row[6]),
            "title": row[7],
            "sourceType": row[8],
            "category": row[9],
            "regionMatchClass": row[10],
            "regions": row[11],
        })

scenario_results = []
failures = []
attention = []
for scenario in scenarios:
    key = scenario["scenario_key"]
    pre = preflight.get(key, {})
    api_row = api.get(key, {})
    merged = [row for row in candidates_by_scenario.get(key, []) if row["sourceName"] == "merged"]
    top = merged[0] if merged else None
    mismatch_count = sum(1 for row in merged if row["regionMatchClass"] == "REGION_MISMATCH")
    acceptable_region_classes = {"REGION_MATCH", "CHILD_REGION_MATCH", "SIDO_WIDE_MATCH"}
    match_count = sum(1 for row in merged if row["regionMatchClass"] in acceptable_region_classes)
    no_region_count = sum(1 for row in merged if row["regionMatchClass"] == "NO_REGION_ROW")
    category_mismatch_count = sum(1 for row in merged if row["category"] != scenario["expected_category"])
    require_region = scenario["require_region_specific"].lower() == "true"
    local_candidate_count = int(pre.get("sameCategoryRegionRows", 0))
    status = "PASSED"
    reasons = []

    if api_row.get("status") != "200":
        status = "FAILED"
        reasons.append("api_non_200")
    if not merged:
        status = "FAILED"
        reasons.append("no_snapshot_merged_candidates")
    if require_region and local_candidate_count > 0:
        if top and top["regionMatchClass"] not in acceptable_region_classes:
            status = "FAILED"
            reasons.append("top_candidate_not_region_specific")
        if match_count == 0:
            status = "FAILED"
            reasons.append("no_region_specific_candidate")
    if mismatch_count > 0:
        status = "FAILED"
        reasons.append("region_mismatch_candidate_present")
    if category_mismatch_count > 0:
        if status != "FAILED":
            status = "ATTENTION"
        reasons.append("category_mismatch_candidate_present")
    if not require_region and local_candidate_count == 0 and no_region_count == 0 and match_count == 0:
        if status != "FAILED":
            status = "ATTENTION"
        reasons.append("no_local_or_national_candidate_for_empty_region_case")

    if key == "ambiguous-junggu-housing":
        hard_junggu_rows = [
            row for row in merged
            if " 중구" in row["regions"] and "서울특별시 마포구" not in row["regions"]
        ]
        if hard_junggu_rows:
            status = "FAILED"
            reasons.append("ambiguous_sgg_appears_hard_filtered_to_junggu")

    result = {
        "scenarioKey": key,
        "status": status,
        "reasons": reasons,
        "api": api_row,
        "preflight": pre,
        "mergedCandidateCount": len(merged),
        "topCandidate": top,
        "regionMismatchCount": mismatch_count,
        "regionSpecificCount": match_count,
        "noRegionCount": no_region_count,
        "categoryMismatchCount": category_mismatch_count,
        "mergedCandidates": merged[:5],
    }
    scenario_results.append(result)
    if status == "FAILED":
        failures.append(key)
    elif status == "ATTENTION":
        attention.append(key)

decision = "PASSED"
if failures:
    decision = "FAILED"
elif attention:
    decision = "ATTENTION"

summary_lines = [
    "chat_explicit_region_matrix_audit=completed",
    f"artifact_dir={artifact_dir}",
    f"scenario_count={len(scenario_results)}",
    f"failed_count={len(failures)}",
    f"attention_count={len(attention)}",
    f"decision={decision}",
    f"failed_scenarios={','.join(failures)}",
    f"attention_scenarios={','.join(attention)}",
]
for result in scenario_results:
    top = result["topCandidate"] or {}
    summary_lines.append(
        "scenario="
        f"{result['scenarioKey']}|status={result['status']}|reasons={','.join(result['reasons'])}|"
        f"answer_mode={result['api'].get('answer_mode','')}|refs={result['api'].get('reference_ids','')}|"
        f"merged={result['mergedCandidateCount']}|region_specific={result['regionSpecificCount']}|"
        f"mismatch={result['regionMismatchCount']}|no_region={result['noRegionCount']}|"
        f"top={top.get('serviceId','')}:{top.get('title','')}:{top.get('regionMatchClass','')}"
    )
summary_path.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

payload = {
    "artifactDir": artifact_dir,
    "decision": decision,
    "failedScenarios": failures,
    "attentionScenarios": attention,
    "scenarios": scenario_results,
}
json_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Chat Explicit Region Matrix Audit",
    "",
    f"- decision: `{decision}`",
    f"- failed_count: `{len(failures)}`",
    f"- attention_count: `{len(attention)}`",
    "",
    "## Scenarios",
]
for result in scenario_results:
    top = result["topCandidate"] or {}
    note_lines.append(
        f"- `{result['scenarioKey']}` `{result['status']}` reasons=`{', '.join(result['reasons']) or '-'}` "
        f"answerMode=`{result['api'].get('answer_mode','')}` refs=`{result['api'].get('reference_ids','')}` "
        f"top=`{top.get('serviceId','')} {top.get('title','')} {top.get('regionMatchClass','')}`"
    )
    for candidate in result["mergedCandidates"]:
        note_lines.append(
            f"  - rank `{candidate['rank']}` service `{candidate['serviceId']}` "
            f"`{candidate['title']}` category=`{candidate['category']}` region=`{candidate['regionMatchClass']}`"
        )
note_path.write_text("\n".join(note_lines) + "\n", encoding="utf-8")

print(summary_path.read_text(encoding="utf-8"), end="")

if failures:
    raise SystemExit("chat explicit region matrix audit failed: " + ", ".join(failures))
PY

smoke_sanitize_artifacts "${ARTIFACT_DIR}"
smoke_update_links \
  "${ARTIFACT_DIR}" "${ARTIFACT_ROOT}/latest" \
  "${SUMMARY_OUT}" "${ARTIFACT_ROOT}/latest-chat-explicit-region-matrix-summary.txt" \
  "${JSON_OUT}" "${ARTIFACT_ROOT}/latest-chat-explicit-region-matrix-summary.json" \
  "${NOTE_OUT}" "${ARTIFACT_ROOT}/latest-chat-explicit-region-matrix-note.md"
