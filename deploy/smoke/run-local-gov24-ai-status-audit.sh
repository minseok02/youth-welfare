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
    coalesce(ur.ai_status, 'NULL_STATUS') as ai_status,
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
gov24_ranked as (
  select *
  from ranked
  where source_type = 'GOV24'
),
gov24_top10 as (
  select * from gov24_ranked where rec_rank <= 10
),
gov24_top2 as (
  select * from gov24_ranked where rec_rank <= 2
)
select 'latest_users', coalesce(count(distinct user_key), 0)::text
from ranked
union all
select 'gov24_latest_batch_rows', count(*)::text
from gov24_ranked
union all
select 'gov24_top10_rows', count(*)::text
from gov24_top10
union all
select 'gov24_top2_rows', count(*)::text
from gov24_top2
union all
select 'gov24_ai_status_distribution',
       coalesce(string_agg(ai_status || ':' || cnt, ',' order by ai_status), '')
from (
  select ai_status, count(*) as cnt
  from gov24_ranked
  group by ai_status
) s
union all
select 'gov24_top10_ai_status_distribution',
       coalesce(string_agg(ai_status || ':' || cnt, ',' order by ai_status), '')
from (
  select ai_status, count(*) as cnt
  from gov24_top10
  group by ai_status
) s10
union all
select 'gov24_top2_ai_status_distribution',
       coalesce(string_agg(ai_status || ':' || cnt, ',' order by ai_status), '')
from (
  select ai_status, count(*) as cnt
  from gov24_top2
  group by ai_status
) s2
union all
select 'gov24_rank_ai_status_distribution',
       coalesce(string_agg(rec_rank || ':' || ai_status || ':' || cnt, ',' order by rec_rank, ai_status), '')
from (
  select rec_rank, ai_status, count(*) as cnt
  from gov24_top10
  group by rec_rank, ai_status
) rs
union all
select 'gov24_partial_missing_services',
       coalesce(string_agg(service_id || ':' || title || ':' || cnt, ' | ' order by cnt desc, service_id), '')
from (
  select service_id, title, count(*) as cnt
  from gov24_ranked
  where ai_status = 'PARTIAL_MISSING'
  group by service_id, title
  order by cnt desc, service_id
  limit 10
) pm
union all
select 'gov24_top2_status_samples',
       coalesce(string_agg(
         rec_rank || ':' || service_id || ':' || title || ':' || ai_status || ':' ||
         coalesce(rule_weighted_score::text, 'null') || ':' ||
         coalesce(ai_score::text, 'null') || ':' ||
         coalesce(final_score::text, 'null'),
         ' | ' order by rec_rank, final_score desc, service_id
       ), '')
from (
  select rec_rank, service_id, title, ai_status, rule_weighted_score, ai_score, final_score
  from gov24_top2
  order by rec_rank, final_score desc, service_id
  limit 10
) t2;
SQL

rows="$(smoke_db_query "${SQL}")"

echo "${rows}" | while IFS=$'\t' read -r metric value; do
  [[ -n "${metric}" ]] || continue
  echo "METRIC ${metric}=${value}"
done
