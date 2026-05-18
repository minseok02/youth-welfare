#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
TARGET_USER_KEY="${TARGET_USER_KEY:-}"
TARGET_SERVICE_IDS_CSV="${TARGET_SERVICE_IDS_CSV:-2736,3257,3281,3575,3714}"
TOP_COMPETITOR_LIMIT="${TOP_COMPETITOR_LIMIT:-3}"
SUMMARY_EXCERPT_LIMIT="${SUMMARY_EXCERPT_LIMIT:-160}"
SKIP_DIAGNOSTICS="${SKIP_DIAGNOSTICS:-false}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/admin-login.json"
DIAGNOSTICS_RESPONSE="${ARTIFACT_DIR}/recommendation-diagnostics.json"
DB_ROWS_FILE="${ARTIFACT_DIR}/db-rows.tsv"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

normalize_bool() {
  local value="${1,,}"
  case "${value}" in
    true|false) printf '%s' "${value}" ;;
    *)
      echo "unsupported boolean value: ${1}" >&2
      exit 1
      ;;
  esac
}

SKIP_DIAGNOSTICS="$(normalize_bool "${SKIP_DIAGNOSTICS}")"
KEEP_ARTIFACTS="$(normalize_bool "${KEEP_ARTIFACTS}")"

extract_access_token() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

print(payload["data"]["accessToken"])
PY
}

resolve_target_user_key() {
  if [[ -n "${TARGET_USER_KEY}" ]]; then
    printf '%s' "${TARGET_USER_KEY}"
    return 0
  fi

  smoke_db_query "
    with latest_per_user as (
      select user_key, max(recommended_at) as recommended_at
      from user_recommendations
      group by user_key
    )
    select user_key
    from latest_per_user
    order by recommended_at desc, user_key desc
    limit 1;
  "
}

resolve_top_competitor_ids_csv() {
  local user_key="$1"

  smoke_db_query "
    with target_batch as (
      select max(recommended_at) as recommended_at
      from user_recommendations
      where user_key = '${user_key}'
    ),
    ranked as (
      select
        ws.id as service_id,
        row_number() over (
          order by ur.final_score desc, ur.id asc
        ) as final_rank
      from user_recommendations ur
      join target_batch tb
        on tb.recommended_at = ur.recommended_at
      join welfare_services ws
        on ws.id = ur.service_id
      where ur.user_key = '${user_key}'
    )
    select coalesce(string_agg(service_id::text, ',' order by final_rank), '')
    from ranked
    where final_rank <= ${TOP_COMPETITOR_LIMIT};
  "
}

merge_csv_ids() {
  python3 - "$1" "$2" <<'PY'
import sys

seen = set()
merged = []
for raw in sys.argv[1:]:
    for part in raw.split(","):
        value = part.strip()
        if not value or value in seen:
            continue
        seen.add(value)
        merged.append(value)

print(",".join(merged))
PY
}

build_diagnostics_query_args() {
  python3 - "$1" <<'PY'
import sys
from urllib.parse import quote

for part in sys.argv[1].split(","):
    value = part.strip()
    if value:
        print(f"serviceId={quote(value)}")
PY
}

FOCUS_USER_KEY="$(resolve_target_user_key)"
if [[ -z "${FOCUS_USER_KEY}" ]]; then
  echo "missing target user_key and no recommendation batch exists" >&2
  exit 1
fi

TOP_COMPETITOR_IDS_CSV="$(resolve_top_competitor_ids_csv "${FOCUS_USER_KEY}")"
FOCUS_SERVICE_IDS_CSV="$(merge_csv_ids "${TARGET_SERVICE_IDS_CSV}" "${TOP_COMPETITOR_IDS_CSV}")"
FOCUS_SERVICE_IDS_SQL="$(python3 - "${FOCUS_SERVICE_IDS_CSV}" <<'PY'
import sys

values = []
for part in sys.argv[1].split(","):
    value = part.strip()
    if value:
        values.append(value)

print(",".join(values))
PY
)"

if [[ -z "${FOCUS_SERVICE_IDS_SQL}" ]]; then
  echo "no focus service ids resolved" >&2
  exit 1
fi

read -r -d '' SQL <<SQL || true
with target_family_ids as (
  select unnest(string_to_array('${TARGET_SERVICE_IDS_CSV}', ','))::bigint as service_id
),
focus_ids as (
  select unnest(string_to_array('${FOCUS_SERVICE_IDS_SQL}', ','))::bigint as service_id
),
target_batch as (
  select max(recommended_at) as recommended_at
  from user_recommendations
  where user_key = '${FOCUS_USER_KEY}'
),
user_ctx as (
  select
    up.user_key,
    up.age,
    up.age_band,
    up.sido,
    up.sgg,
    up.region_code,
    up.income_level,
    up.household_type,
    up.employment_status,
    coalesce((
      select string_agg(attr_value, ',' order by attr_value)
      from (
        select distinct ua2.attr_value
        from user_attributes ua2
        where ua2.user_key = up.user_key
          and ua2.attr_type = 'INTEREST_FIELD'
      ) x
    ), '') as interest_fields,
    coalesce((
      select string_agg(attr_value, ',' order by attr_value)
      from (
        select distinct ua3.attr_value
        from user_attributes ua3
        where ua3.user_key = up.user_key
          and ua3.attr_type = 'TARGET_TYPE'
      ) x
    ), '') as target_types,
    coalesce((
      select string_agg(priority_item, ',' order by priority_rank)
      from (
        select distinct upr2.priority_rank,
               po2.code || ':' || upr2.priority_rank || ':' || upr2.weight as priority_item
        from user_priorities upr2
        join priority_options po2
          on po2.id = upr2.priority_option_id
        where upr2.user_key = up.user_key
      ) x
    ), '') as priorities
  from user_profiles up
  where up.user_key = '${FOCUS_USER_KEY}'
),
ranked as (
  select
    ur.user_key,
    ur.recommended_at,
    ws.id as service_id,
    ws.source_type,
    ws.source_id,
    ws.title,
    coalesce(ws.unified_category, '') as unified_category,
    coalesce(ur.ai_status, 'NULL_STATUS') as ai_status,
    ur.rule_weighted_score,
    ur.ai_score,
    ur.final_score,
    row_number() over (
      partition by ur.user_key
      order by ur.final_score desc, ur.id asc
    ) as final_rank,
    row_number() over (
      partition by ur.user_key
      order by ur.rule_weighted_score desc nulls last, ur.id asc
    ) as rule_rank
  from user_recommendations ur
  join target_batch tb
    on tb.recommended_at = ur.recommended_at
  join welfare_services ws
    on ws.id = ur.service_id
  where ur.user_key = '${FOCUS_USER_KEY}'
),
focus_request as (
  select tf.service_id, true as in_target_family
  from target_family_ids tf
  union all
  select r.service_id, false as in_target_family
  from ranked r
  where r.final_rank <= ${TOP_COMPETITOR_LIMIT}
    and not exists (
      select 1
      from target_family_ids tf
      where tf.service_id = r.service_id
    )
),
focus_rows as (
  select
    frq.in_target_family,
    r.user_key,
    r.recommended_at,
    ws.id as service_id,
    ws.source_type,
    ws.source_id,
    ws.title,
    coalesce(ws.unified_category, '') as unified_category,
    coalesce(r.ai_status, 'NOT_IN_LATEST_BATCH') as ai_status,
    r.rule_weighted_score,
    r.ai_score,
    r.final_score,
    r.final_rank,
    r.rule_rank
  from focus_request frq
  join welfare_services ws
    on ws.id = frq.service_id
  left join ranked r
    on r.service_id = frq.service_id
),
term_agg as (
  select
    stt.service_id,
    coalesce(string_agg(distinct case when stt.term_group = 'INTEREST_THEME' then stt.term_label end, ','), '') as interest_themes,
    coalesce(string_agg(distinct case when stt.term_group = 'YOUTH_KEYWORD' then stt.term_label end, ','), '') as keyword_tags,
    coalesce(string_agg(distinct case when stt.term_group = 'TARGET_GROUP' then stt.term_label end, ','), '') as target_groups,
    coalesce(string_agg(distinct case when stt.term_group = 'LIFE_STAGE' then stt.term_label end, ','), '') as life_stages
  from service_taxonomy_terms stt
  where stt.service_id in (select service_id from focus_ids)
  group by stt.service_id
),
fact_agg as (
  select
    sf.service_id,
    coalesce(string_agg(distinct sf.fact_code_set_key || ':' || coalesce(nullif(sf.text_value, ''), sf.fact_code), ' | ' order by sf.fact_code_set_key || ':' || coalesce(nullif(sf.text_value, ''), sf.fact_code)), '') as fact_summary
  from service_facts sf
  where sf.service_id in (select service_id from focus_ids)
  group by sf.service_id
),
tag_agg as (
  select
    st.service_id,
    coalesce(string_agg(st.tag_item, ' | ' order by st.tag_item), '') as raw_tags
  from (
    select distinct service_id, tag_type || ':' || tag_value as tag_item
    from service_tags
    where service_id in (select service_id from focus_ids)
  ) st
  group by st.service_id
),
projection_base as (
  select
    ws.id as service_id,
    ws.search_youth_relevant,
    ws.min_age,
    ws.max_age,
    ws.min_income,
    ws.max_income,
    ws.apply_end_date,
    coalesce(stss_youth_major.slot_label, st.youth_major_label) as youth_major_label,
    coalesce(stss_youth_mid.slot_label, st.youth_mid_label) as youth_mid_label,
    coalesce(stss_provision_method.slot_label, st.provision_method_label, ws.apply_method_name) as provision_method_label,
    coalesce(stss_gov24_service_field.slot_label, st.gov24_service_field_label) as gov24_service_field_label,
    coalesce(stss_gov24_user_type.slot_label, st.gov24_user_type_label) as gov24_user_type_label,
    coalesce(stss_gov24_benefit_type.slot_label, st.gov24_benefit_type_label) as gov24_benefit_type_label,
    left(regexp_replace(coalesce(wsd.support_detail, ws.support_content, ws.description, ''), E'[\\n\\r\\t]+', ' ', 'g'), ${SUMMARY_EXCERPT_LIMIT}) as summary_excerpt
  from welfare_services ws
  left join welfare_service_details wsd on wsd.service_id = ws.id
  left join service_taxonomies st on st.service_id = ws.id
  left join (
      select service_id, max(slot_label) as slot_label
      from service_taxonomy_summary_slots
      where slot_key = 'YOUTH_MAJOR'
      group by service_id
  ) stss_youth_major on stss_youth_major.service_id = ws.id
  left join (
      select service_id, max(slot_label) as slot_label
      from service_taxonomy_summary_slots
      where slot_key = 'YOUTH_MID'
      group by service_id
  ) stss_youth_mid on stss_youth_mid.service_id = ws.id
  left join (
      select service_id, max(slot_label) as slot_label
      from service_taxonomy_summary_slots
      where slot_key = 'PROVISION_METHOD'
      group by service_id
  ) stss_provision_method on stss_provision_method.service_id = ws.id
  left join (
      select service_id, max(slot_label) as slot_label
      from service_taxonomy_summary_slots
      where slot_key = 'GOV24_SERVICE_FIELD'
      group by service_id
  ) stss_gov24_service_field on stss_gov24_service_field.service_id = ws.id
  left join (
      select service_id, max(slot_label) as slot_label
      from service_taxonomy_summary_slots
      where slot_key = 'GOV24_USER_TYPE'
      group by service_id
  ) stss_gov24_user_type on stss_gov24_user_type.service_id = ws.id
  left join (
      select service_id, max(slot_label) as slot_label
      from service_taxonomy_summary_slots
      where slot_key = 'GOV24_BENEFIT_TYPE'
      group by service_id
  ) stss_gov24_benefit_type on stss_gov24_benefit_type.service_id = ws.id
  where ws.id in (select service_id from focus_ids)
)
select 'target_user_key', '${FOCUS_USER_KEY}'
union all
select 'target_service_ids_csv', '${TARGET_SERVICE_IDS_CSV}'
union all
select 'top_competitor_ids_csv', coalesce('${TOP_COMPETITOR_IDS_CSV}', '')
union all
select 'focus_service_ids_csv', '${FOCUS_SERVICE_IDS_CSV}'
union all
select 'user_context',
       user_key || ':' ||
       coalesce(age::text, 'null') || ':' ||
       coalesce(age_band, '') || ':' ||
       coalesce(sido, '') || ':' ||
       coalesce(sgg, '') || ':' ||
       coalesce(region_code, '') || ':' ||
       coalesce(income_level::text, 'null') || ':' ||
       coalesce(household_type, '') || ':' ||
       coalesce(employment_status, '') || ':' ||
       coalesce(interest_fields, '') || ':' ||
       coalesce(target_types, '') || ':' ||
       coalesce(priorities, '')
from user_ctx
union all
select 'latest_batch_focus_rows',
       coalesce(string_agg(
         case when fr.in_target_family then 'target' else 'competitor' end || ':' ||
         'f' || coalesce(fr.final_rank::text, 'NA') || ':r' || coalesce(fr.rule_rank::text, 'NA') || ':' ||
         fr.service_id || ':' || fr.title || ':' || fr.source_type || ':' ||
         coalesce(fr.unified_category, '') || ':' ||
         coalesce(fr.rule_weighted_score::text, 'null') || ':' ||
         coalesce(fr.ai_score::text, 'null') || ':' ||
         coalesce(fr.final_score::text, 'null') || ':' ||
         coalesce(fr.ai_status, '') || ':' ||
         coalesce(pb.search_youth_relevant::text, 'false') || ':' ||
         coalesce(pb.min_age::text, 'null') || ':' || coalesce(pb.max_age::text, 'null') || ':' ||
         coalesce(pb.min_income::text, 'null') || ':' || coalesce(pb.max_income::text, 'null') || ':' ||
         coalesce(pb.youth_major_label, '') || ':' ||
         coalesce(pb.youth_mid_label, '') || ':' ||
         coalesce(pb.provision_method_label, '') || ':' ||
         coalesce(pb.gov24_service_field_label, '') || ':' ||
         coalesce(pb.gov24_user_type_label, '') || ':' ||
         coalesce(pb.gov24_benefit_type_label, '') || ':' ||
         coalesce(ta.interest_themes, '') || ':' ||
         coalesce(ta.keyword_tags, '') || ':' ||
         coalesce(ta.target_groups, '') || ':' ||
         coalesce(ta.life_stages, '') || ':' ||
         coalesce(fa.fact_summary, '') || ':' ||
         coalesce(left(tg.raw_tags, 220), '') || ':' ||
         coalesce(pb.summary_excerpt, ''),
         ' || ' order by fr.in_target_family desc, coalesce(fr.final_rank, 9999), coalesce(fr.rule_rank, 9999), fr.service_id
       ), '')
from focus_rows fr
left join projection_base pb on pb.service_id = fr.service_id
left join term_agg ta on ta.service_id = fr.service_id
left join fact_agg fa on fa.service_id = fr.service_id
left join tag_agg tg on tg.service_id = fr.service_id;
SQL

rows="$(smoke_db_query "${SQL}")"
printf '%s\n' "${rows}" > "${DB_ROWS_FILE}"

echo "recommendation signal gap audit passed"
while IFS=$'\t' read -r metric value; do
  [[ -n "${metric}" ]] || continue
  echo "METRIC ${metric}=${value}"
done < "${DB_ROWS_FILE}"

if [[ "${SKIP_DIAGNOSTICS}" == "true" ]]; then
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    echo "artifact_dir=${ARTIFACT_DIR}"
  fi
  exit 0
fi

smoke_require_command curl
smoke_require_command python3

smoke_resolve_admin_credentials "${ROOT_DIR}"
: "${ADMIN_EMAIL:?ADMIN_EMAIL is empty; export ADMIN_EMAIL or set SECURITY_ADMIN_EMAILS/.env or /tmp/youth-welfare-admin-smoke-email}"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD is empty; export ADMIN_PASSWORD or set /tmp/youth-welfare-admin-smoke-password}"

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

smoke_print_step "admin login (${ADMIN_EMAIL})"
LOGIN_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${LOGIN_RESPONSE}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${ADMIN_EMAIL}\",
      \"password\": \"${ADMIN_PASSWORD}\"
    }"
)"
smoke_assert_status 200 "${LOGIN_STATUS}" "admin login" "${LOGIN_RESPONSE}"
ADMIN_TOKEN="$(extract_access_token "${LOGIN_RESPONSE}")"

query_args="$(build_diagnostics_query_args "${FOCUS_SERVICE_IDS_CSV}" | paste -sd'&' -)"
if [[ -z "${query_args}" ]]; then
  echo "failed to build diagnostics query args" >&2
  exit 1
fi

smoke_print_step "recommendation diagnostics"
DIAGNOSTICS_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/recommendation-diagnostics?userKey=${FOCUS_USER_KEY}&${query_args}" "${DIAGNOSTICS_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${DIAGNOSTICS_STATUS}" "recommendation diagnostics" "${DIAGNOSTICS_RESPONSE}"

python3 - "${DIAGNOSTICS_RESPONSE}" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)["data"]

print(f"METRIC diagnostics_rerank_trace_mode={payload.get('rerankTraceMode')}")
print(f"METRIC diagnostics_base_candidate_count={payload.get('baseCandidateCount')}")
print(f"METRIC diagnostics_latest_saved_candidate_count={payload.get('latestSavedCandidateCount')}")

for row in payload.get("services") or []:
    fields = [
        str(row.get("serviceId")),
        row.get("title") or "",
        row.get("sourceType") or "",
        row.get("category") or "",
        row.get("dropStage") or "",
        str(row.get("latestSavedRank")),
        str(row.get("latestSavedFinalScore")),
        str(row.get("latestSavedAiScore")),
        row.get("latestSavedAiStatus") or "",
        str(row.get("ruleWeightedScore")),
        str(row.get("rerankCurrentRank")),
        str(row.get("rerankCurrentFinalScore")),
        ",".join(row.get("gov24UserTypeTokens") or []),
        ",".join(row.get("gov24BenefitTypeTokens") or []),
        row.get("youthIncomeConditionTypeLabel") or "",
        ",".join(row.get("youthEmploymentRequirementLabels") or []),
        ",".join(row.get("youthEducationRequirementLabels") or []),
        ",".join(row.get("youthSpecialRequirementLabels") or []),
        row.get("youthMaritalStatusLabel") or "",
    ]
    print("DIAG " + "|".join(fields))
PY

if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
  echo "artifact_dir=${ARTIFACT_DIR}"
fi
