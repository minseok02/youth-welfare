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
    coalesce(ur.ai_reason, '') as ai_reason,
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
gov24_null_ai as (
  select * from top10 where source_type = 'GOV24' and ai_score is null
)
select 'gov24_null_ai_top10_count', count(*)::text
from gov24_null_ai
union all
select 'gov24_null_ai_top2_count', count(*)::text
from gov24_null_ai
where rec_rank <= 2
union all
select 'gov24_null_ai_rank_distribution',
       coalesce(string_agg(rec_rank || ':' || cnt, ',' order by rec_rank), '')
from (
  select rec_rank, count(*) as cnt
  from gov24_null_ai
  group by rec_rank
) zr
union all
select 'gov24_null_ai_services',
       coalesce(string_agg(service_id || ':' || title || ':' || cnt, ' | ' order by cnt desc, service_id), '')
from (
  select service_id, title, count(*) as cnt
  from gov24_null_ai
  group by service_id, title
  order by cnt desc, service_id
  limit 10
) zs
union all
select 'gov24_null_ai_reason_samples',
       coalesce(string_agg(sample, ' | ' order by sample), '')
from (
  select distinct left(ai_reason, 120) as sample
  from gov24_null_ai
  where ai_reason <> ''
  order by sample
  limit 10
) rs
union all
select 'top2_gov24_null_ai_score_summary',
       coalesce(string_agg(
         service_id || ':' || title || ':' ||
         coalesce(rule_weighted_score::text, 'null') || ':' ||
         coalesce(ai_score::text, 'null') || ':' ||
         coalesce(final_score::text, 'null'),
         ' | ' order by final_score desc, service_id
       ), '')
from (
  select service_id, title, rule_weighted_score, ai_score, final_score
  from gov24_null_ai
  where rec_rank <= 2
  order by final_score desc, service_id
  limit 10
) t2;
SQL

rows="$(smoke_db_query "${SQL}")"

echo "${rows}" | while IFS=$'\t' read -r metric value; do
  [[ -n "${metric}" ]] || continue
  echo "METRIC ${metric}=${value}"
done
