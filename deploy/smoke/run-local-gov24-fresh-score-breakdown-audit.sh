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
base as (
  select
    ur.user_key,
    ur.recommended_at,
    ws.source_type,
    ws.id as service_id,
    ws.title,
    coalesce(ws.unified_category, '') as unified_category,
    coalesce(ur.ai_status, 'NULL_STATUS') as ai_status,
    ur.rule_weighted_score,
    ur.ai_score,
    ur.final_score,
    ur.rule_weight_used,
    ur.ai_weight_used,
    row_number() over (
      partition by ur.user_key
      order by ur.final_score desc, ur.id asc
    ) as final_rank
  from user_recommendations ur
  join target_batch tb
    on tb.recommended_at = ur.recommended_at
  join welfare_services ws
    on ws.id = ur.service_id
  where ur.user_key = '${USER_KEY}'
),
norm_context as (
  select coalesce(max(rule_weighted_score), 1) as rule_max
  from base
),
scored as (
  select
    b.*,
    case
      when nc.rule_max = 0 then 0.5
      else coalesce(b.rule_weighted_score, 0) / nc.rule_max
    end as norm_rule,
    case
      when b.ai_score is null then null
      else b.ai_score / 100.0
    end as norm_ai,
    case
      when b.ai_score is null then
        case
          when nc.rule_max = 0 then 0.5
          else coalesce(b.rule_weighted_score, 0) / nc.rule_max
        end
      else
        (case
          when nc.rule_max = 0 then 0.5
          else coalesce(b.rule_weighted_score, 0) / nc.rule_max
        end) * coalesce(b.rule_weight_used, 0)
        + (b.ai_score / 100.0) * coalesce(b.ai_weight_used, 0)
    end as base_blend_score
  from base b
  cross join norm_context nc
),
gov24_top10 as (
  select * from scored
  where source_type = 'GOV24'
    and final_rank <= 10
),
top2 as (
  select * from scored
  where final_rank <= 2
)
select 'target_user_key', '${USER_KEY}'
union all
select 'target_recommended_at', coalesce(max(recommended_at)::text, '')
from scored
union all
select 'rule_max', coalesce(max(rule_weighted_score)::text, 'null')
from scored
union all
select 'gov24_top10_score_breakdown',
       coalesce(string_agg(
         'f' || final_rank || ':' || service_id || ':' || title || ':' || ai_status || ':' ||
         coalesce(rule_weighted_score::text, 'null') || ':' ||
         coalesce(ai_score::text, 'null') || ':' ||
         round(norm_rule::numeric, 5)::text || ':' ||
         coalesce(round(norm_ai::numeric, 5)::text, 'null') || ':' ||
         coalesce(rule_weight_used::text, 'null') || ':' ||
         coalesce(ai_weight_used::text, 'null') || ':' ||
         round(base_blend_score::numeric, 5)::text || ':' ||
         coalesce(final_score::text, 'null') || ':' ||
         round((coalesce(final_score, 0) - base_blend_score)::numeric, 5)::text,
         ' | ' order by final_rank
       ), '')
from gov24_top10
union all
select 'top2_score_breakdown',
       coalesce(string_agg(
         'f' || final_rank || ':' || source_type || ':' || service_id || ':' || title || ':' || ai_status || ':' ||
         coalesce(rule_weighted_score::text, 'null') || ':' ||
         coalesce(ai_score::text, 'null') || ':' ||
         round(norm_rule::numeric, 5)::text || ':' ||
         coalesce(round(norm_ai::numeric, 5)::text, 'null') || ':' ||
         coalesce(rule_weight_used::text, 'null') || ':' ||
         coalesce(ai_weight_used::text, 'null') || ':' ||
         round(base_blend_score::numeric, 5)::text || ':' ||
         coalesce(final_score::text, 'null') || ':' ||
         round((coalesce(final_score, 0) - base_blend_score)::numeric, 5)::text,
         ' | ' order by final_rank
       ), '')
from top2
union all
select 'gov24_vs_top2_breakdown',
       coalesce(string_agg(sample, ' || ' order by gov24_final_rank, rival_final_rank), '')
from (
  select
    g.final_rank as gov24_final_rank,
    r.final_rank as rival_final_rank,
    'g' || g.final_rank || ':' || g.service_id || ':' || g.title || ':' || g.ai_status || ':' ||
    coalesce(g.rule_weighted_score::text, 'null') || ':' ||
    coalesce(g.ai_score::text, 'null') || ':' ||
    round(g.norm_rule::numeric, 5)::text || ':' ||
    coalesce(round(g.norm_ai::numeric, 5)::text, 'null') || ':' ||
    round(g.base_blend_score::numeric, 5)::text || ':' ||
    coalesce(g.final_score::text, 'null') || ' => ' ||
    'top' || r.final_rank || ':' || r.source_type || ':' || r.service_id || ':' || r.title || ':' || r.ai_status || ':' ||
    coalesce(r.rule_weighted_score::text, 'null') || ':' ||
    coalesce(r.ai_score::text, 'null') || ':' ||
    round(r.norm_rule::numeric, 5)::text || ':' ||
    coalesce(round(r.norm_ai::numeric, 5)::text, 'null') || ':' ||
    round(r.base_blend_score::numeric, 5)::text || ':' ||
    coalesce(r.final_score::text, 'null') as sample
  from gov24_top10 g
  join top2 r
    on true
  order by g.final_rank, r.final_rank
  limit 20
) pairs;
SQL

rows="$(smoke_db_query "${SQL}")"

echo "${rows}" | while IFS=$'\t' read -r metric value; do
  [[ -n "${metric}" ]] || continue
  echo "METRIC ${metric}=${value}"
done
