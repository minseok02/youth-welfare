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
ranked as (
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
top2 as (
  select * from ranked where final_rank <= 2
),
gov24 as (
  select *,
         case when rule_rank <= 15 then 'inside_ai_top_n' else 'outside_ai_top_n' end as ai_request_bucket
  from ranked
  where source_type = 'GOV24'
),
gov24_top10 as (
  select * from gov24 where final_rank <= 10
)
select 'target_user_key', '${USER_KEY}'
union all
select 'target_recommended_at', coalesce(max(recommended_at)::text, '')
from ranked
union all
select 'fresh_batch_rows', count(*)::text
from ranked
union all
select 'top2_source_distribution',
       coalesce(string_agg(source_type || ':' || cnt, ',' order by source_type), '')
from (
  select source_type, count(*) as cnt
  from top2
  group by source_type
) t2s
union all
select 'fresh_batch_source_distribution',
       coalesce(string_agg(source_type || ':' || cnt, ',' order by source_type), '')
from (
  select source_type, count(*) as cnt
  from ranked
  group by source_type
) s
union all
select 'gov24_rows', count(*)::text
from gov24
union all
select 'gov24_top10_rows', count(*)::text
from gov24_top10
union all
select 'gov24_top2_rows', count(*)::text
from gov24
where final_rank <= 2
union all
select 'gov24_ai_status_distribution',
       coalesce(string_agg(ai_status || ':' || cnt, ',' order by ai_status), '')
from (
  select ai_status, count(*) as cnt
  from gov24
  group by ai_status
) gas
union all
select 'gov24_top10_rows_detail',
       coalesce(string_agg(
         'f' || final_rank || ':r' || rule_rank || ':' || ai_request_bucket || ':' || ai_status || ':' ||
         service_id || ':' || title || ':' ||
         coalesce(rule_weighted_score::text, 'null') || ':' ||
         coalesce(ai_score::text, 'null') || ':' ||
         coalesce(final_score::text, 'null'),
         ' | ' order by final_rank, rule_rank
       ), '')
from gov24_top10
union all
select 'top2_rows_detail',
       coalesce(string_agg(
         'f' || final_rank || ':' || source_type || ':' || service_id || ':' || title || ':' ||
         coalesce(rule_weighted_score::text, 'null') || ':' ||
         coalesce(ai_score::text, 'null') || ':' ||
         coalesce(final_score::text, 'null'),
         ' | ' order by final_rank
       ), '')
from top2
union all
select 'gov24_vs_top2_samples',
       coalesce(string_agg(sample, ' || ' order by gov24_final_rank, rival_final_rank), '')
from (
  select
    g.final_rank as gov24_final_rank,
    r.final_rank as rival_final_rank,
    'g' || g.final_rank || ':r' || g.rule_rank || ':' || g.ai_request_bucket || ':' || g.ai_status || ':' ||
    g.service_id || ':' || g.title || ':' ||
    coalesce(g.rule_weighted_score::text, 'null') || ':' ||
    coalesce(g.ai_score::text, 'null') || ':' ||
    coalesce(g.final_score::text, 'null') || ' => ' ||
    'top' || r.final_rank || ':' || r.source_type || ':' || r.service_id || ':' || r.title || ':' ||
    coalesce(r.rule_weighted_score::text, 'null') || ':' ||
    coalesce(r.ai_score::text, 'null') || ':' ||
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
