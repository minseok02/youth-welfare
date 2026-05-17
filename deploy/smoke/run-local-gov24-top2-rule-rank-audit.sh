#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

read -r -d '' SQL <<'SQL' || true
with latest as (
  select user_key, max(recommended_at) recommended_at
  from user_recommendations
  group by user_key
),
latest_batch as (
  select
    ur.user_key,
    ws.source_type,
    ws.id as service_id,
    ws.title,
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
  join latest l
    on l.user_key = ur.user_key
   and l.recommended_at = ur.recommended_at
  join welfare_services ws
    on ws.id = ur.service_id
),
gov24_top2 as (
  select *,
         case when rule_rank <= 15 then 'inside_ai_top_n' else 'outside_ai_top_n' end as ai_request_bucket
  from latest_batch
  where source_type = 'GOV24'
    and final_rank <= 2
)
select 'gov24_top2_rule_rank_count', count(*)::text
from gov24_top2
union all
select 'gov24_top2_final_rank_distribution',
       coalesce(string_agg(final_rank || ':' || cnt, ',' order by final_rank), '')
from (
  select final_rank, count(*) as cnt
  from gov24_top2
  group by final_rank
) fr
union all
select 'gov24_top2_ai_request_bucket_distribution',
       coalesce(string_agg(ai_request_bucket || ':' || cnt, ',' order by ai_request_bucket), '')
from (
  select ai_request_bucket, count(*) as cnt
  from gov24_top2
  group by ai_request_bucket
) ab
union all
select 'gov24_top2_rule_rank_distribution',
       coalesce(string_agg(rule_rank || ':' || cnt, ',' order by rule_rank), '')
from (
  select rule_rank, count(*) as cnt
  from gov24_top2
  group by rule_rank
) rr
union all
select 'gov24_top2_rule_rank_summary',
       coalesce(string_agg(metric || ':' || value, ',' order by metric), '')
from (
  select 'avg_rule_rank' as metric, round(avg(rule_rank), 2)::text as value
  from gov24_top2
  union all
  select 'max_rule_rank', max(rule_rank)::text
  from gov24_top2
  union all
  select 'min_rule_rank', min(rule_rank)::text
  from gov24_top2
) s
union all
select 'gov24_top2_rule_rank_samples',
       coalesce(string_agg(
         user_key || ':' ||
         'f' || final_rank || ':' ||
         'r' || rule_rank || ':' ||
         ai_request_bucket || ':' ||
         ai_status || ':' ||
         service_id || ':' || title || ':' ||
         coalesce(rule_weighted_score::text, 'null') || ':' ||
         coalesce(ai_score::text, 'null') || ':' ||
         coalesce(final_score::text, 'null'),
         ' | ' order by final_rank, rule_rank, final_score desc, user_key
       ), '')
from (
  select *
  from gov24_top2
  order by final_rank, rule_rank, final_score desc, user_key
  limit 12
) gs;
SQL

rows="$(smoke_db_query "${SQL}")"

echo "${rows}" | while IFS=$'\t' read -r metric value; do
  [[ -n "${metric}" ]] || continue
  echo "METRIC ${metric}=${value}"
done
