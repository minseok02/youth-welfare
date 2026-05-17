#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

TARGET_USER_KEY="${TARGET_USER_KEY:-}"

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

USER_KEY="$(resolve_target_user_key)"
if [[ -z "${USER_KEY}" ]]; then
  echo "missing target user_key and no recommendation batch exists" >&2
  exit 1
fi

read -r -d '' SQL <<SQL || true
with target_batch as (
  select max(recommended_at) as recommended_at
  from user_recommendations
  where user_key = '${USER_KEY}'
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
  where up.user_key = '${USER_KEY}'
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
  where ur.user_key = '${USER_KEY}'
),
focus as (
  select * from ranked where final_rank <= 2
  union
  select * from ranked where source_type = 'GOV24' and final_rank <= 10
),
focus_ids as (
  select distinct service_id from focus
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
    coalesce(string_agg(distinct sf.fact_merge_key, ',' order by sf.fact_merge_key), '') as fact_keys
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
    left(regexp_replace(coalesce(wsd.support_detail, ws.support_content, ws.description, ''), E'[\\n\\r\\t]+', ' ', 'g'), 160) as summary_excerpt
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
select 'focus_service_upstream',
       coalesce(string_agg(
         'f' || f.final_rank || ':r' || f.rule_rank || ':' || f.source_type || ':' || f.service_id || ':' || f.title || ':' ||
         f.ai_status || ':' ||
         coalesce(f.rule_weighted_score::text, 'null') || ':' ||
         coalesce(f.ai_score::text, 'null') || ':' ||
         coalesce(f.final_score::text, 'null') || ':' ||
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
         coalesce(fa.fact_keys, '') || ':' ||
         coalesce(left(tg.raw_tags, 220), '') || ':' ||
         coalesce(pb.summary_excerpt, ''),
         ' || ' order by f.final_rank, f.rule_rank, f.service_id
       ), '')
from focus f
left join projection_base pb on pb.service_id = f.service_id
left join term_agg ta on ta.service_id = f.service_id
left join fact_agg fa on fa.service_id = f.service_id
left join tag_agg tg on tg.service_id = f.service_id;
SQL

rows="$(smoke_db_query "${SQL}")"

echo "${rows}" | while IFS=$'\t' read -r metric value; do
  [[ -n "${metric}" ]] || continue
  echo "METRIC ${metric}=${value}"
done
