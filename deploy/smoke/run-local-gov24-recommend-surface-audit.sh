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
    coalesce(ws.unified_category, '') as unified_category,
    ws.id as service_id,
    ws.title
  from user_recommendations ur
  join latest l
    on l.user_key = ur.user_key
   and l.recommended_at = ur.recommended_at
  join welfare_services ws
    on ws.id = ur.service_id
),
topn as (
  select * from ranked where rec_rank <= 10
)
select 'top1_gov24_share_pct', coalesce(round(100.0 * avg(case when source_type = 'GOV24' and rec_rank = 1 then 1.0 else 0.0 end), 2), 0)::text from topn
union all
select 'top2_gov24_share_pct', coalesce(round(100.0 * avg(case when source_type = 'GOV24' and rec_rank = 2 then 1.0 else 0.0 end), 2), 0)::text from topn
union all
select 'top3_gov24_share_pct', coalesce(round(100.0 * avg(case when source_type = 'GOV24' and rec_rank = 3 then 1.0 else 0.0 end), 2), 0)::text from topn
union all
select 'top5_gov24_share_pct', coalesce(round(100.0 * avg(case when source_type = 'GOV24' and rec_rank <= 5 then 1.0 else 0.0 end), 2), 0)::text from topn
union all
select 'top10_gov24_share_pct', coalesce(round(100.0 * avg(case when source_type = 'GOV24' then 1.0 else 0.0 end), 2), 0)::text from topn
union all
select 'top1_gov24_count', count(*)::text from topn where rec_rank = 1 and source_type = 'GOV24'
union all
select 'top10_gov24_count', count(*)::text from topn where source_type = 'GOV24'
union all
select 'top10_distinct_sources',
       string_agg(source_type || ':' || cnt, ',' order by source_type)
from (
  select source_type, count(*) as cnt
  from topn
  group by source_type
) s
union all
select 'rank_source_distribution',
       string_agg(rec_rank || ':' || source_type || ':' || cnt, ',' order by rec_rank, source_type)
from (
  select rec_rank, source_type, count(*) as cnt
  from topn
  group by rec_rank, source_type
) rs
union all
select 'gov24_top_services',
       coalesce(string_agg(service_id || ':' || title || ':' || cnt, ' | ' order by cnt desc, service_id), '')
from (
  select service_id, title, count(*) as cnt
  from topn
  where source_type = 'GOV24'
  group by service_id, title
  order by cnt desc, service_id
  limit 5
) gs
union all
select 'gov24_rank_category_distribution',
       coalesce(string_agg(rec_rank || ':' || unified_category || ':' || cnt, ',' order by rec_rank, cnt desc, unified_category), '')
from (
  select rec_rank, unified_category, count(*) as cnt
  from topn
  where source_type = 'GOV24'
  group by rec_rank, unified_category
) gc;
SQL

rows="$(smoke_db_query "${SQL}")"

echo "${rows}" | while IFS=$'\t' read -r metric value; do
  [[ -n "${metric}" ]] || continue
  echo "METRIC ${metric}=${value}"
done
