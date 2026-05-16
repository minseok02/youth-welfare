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
ranked as (
  select
    ur.user_key,
    row_number() over (
      partition by ur.user_key
      order by ur.final_score desc, ur.id asc
    ) as rec_rank,
    ws.source_type,
    ws.id as service_id,
    ws.title,
    ur.rule_weighted_score,
    ur.ai_score,
    ur.final_score
  from user_recommendations ur
  join latest l
    on l.user_key = ur.user_key
   and l.recommended_at = ur.recommended_at
  join welfare_services ws
    on ws.id = ur.service_id
),
top10 as (
  select * from ranked where rec_rank <= 10
),
top2 as (
  select * from ranked where rec_rank <= 2
)
select 'top10_source_score_summary',
       coalesce(string_agg(
         source_type || ':' || cnt || ':' || avg_rule || ':' || avg_ai || ':' || avg_final,
         ',' order by source_type
       ), '')
from (
  select
    source_type,
    count(*) as cnt,
    coalesce(round(avg(rule_weighted_score), 2), 0)::text as avg_rule,
    coalesce(round(avg(ai_score), 2), 0)::text as avg_ai,
    coalesce(round(avg(final_score), 5), 0)::text as avg_final
  from top10
  group by source_type
) s
union all
select 'top2_source_score_summary',
       coalesce(string_agg(
         source_type || ':' || cnt || ':' || avg_rule || ':' || avg_ai || ':' || avg_final,
         ',' order by source_type
       ), '')
from (
  select
    source_type,
    count(*) as cnt,
    coalesce(round(avg(rule_weighted_score), 2), 0)::text as avg_rule,
    coalesce(round(avg(ai_score), 2), 0)::text as avg_ai,
    coalesce(round(avg(final_score), 5), 0)::text as avg_final
  from top2
  group by source_type
) s
union all
select 'rank_source_score_summary',
       coalesce(string_agg(
         rec_rank || ':' || source_type || ':' || cnt || ':' || avg_rule || ':' || avg_ai || ':' || avg_final,
         ',' order by rec_rank, source_type
       ), '')
from (
  select
    rec_rank,
    source_type,
    count(*) as cnt,
    coalesce(round(avg(rule_weighted_score), 2), 0)::text as avg_rule,
    coalesce(round(avg(ai_score), 2), 0)::text as avg_ai,
    coalesce(round(avg(final_score), 5), 0)::text as avg_final
  from top10
  group by rec_rank, source_type
) rs
union all
select 'gov24_top2_services',
       coalesce(string_agg(service_id || ':' || title || ':' || cnt, ' | ' order by cnt desc, service_id), '')
from (
  select service_id, title, count(*) as cnt
  from top2
  where source_type = 'GOV24'
  group by service_id, title
  order by cnt desc, service_id
  limit 5
) g2
union all
select 'gov24_top10_services',
       coalesce(string_agg(service_id || ':' || title || ':' || cnt, ' | ' order by cnt desc, service_id), '')
from (
  select service_id, title, count(*) as cnt
  from top10
  where source_type = 'GOV24'
  group by service_id, title
  order by cnt desc, service_id
  limit 5
) g10;
SQL

rows="$(smoke_db_query "${SQL}")"

echo "${rows}" | while IFS=$'\t' read -r metric value; do
  [[ -n "${metric}" ]] || continue
  echo "METRIC ${metric}=${value}"
done
