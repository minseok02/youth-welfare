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
gov24_null_ai as (
  select * from ranked
  where source_type = 'GOV24'
    and ai_score is null
),
classified as (
  select
    *,
    case
      when rec_rank > 15 then 'outside_ai_top_n'
      when rec_rank <= 15 and ai_reason = '' then 'inside_ai_top_n_blank_reason'
      when rec_rank <= 15 then 'inside_ai_top_n_with_reason'
      else 'unknown'
    end as null_ai_cause
  from gov24_null_ai
)
select 'gov24_null_ai_cause_distribution',
       coalesce(string_agg(null_ai_cause || ':' || cnt, ',' order by null_ai_cause), '')
from (
  select null_ai_cause, count(*) as cnt
  from classified
  group by null_ai_cause
) c
union all
select 'gov24_null_ai_top2_cause_distribution',
       coalesce(string_agg(null_ai_cause || ':' || cnt, ',' order by null_ai_cause), '')
from (
  select null_ai_cause, count(*) as cnt
  from classified
  where rec_rank <= 2
  group by null_ai_cause
) c2
union all
select 'gov24_null_ai_rank15_boundary',
       coalesce(string_agg(rec_rank || ':' || cnt, ',' order by rec_rank), '')
from (
  select rec_rank, count(*) as cnt
  from classified
  where rec_rank between 1 and 15
  group by rec_rank
) rb
union all
select 'gov24_null_ai_samples',
       coalesce(string_agg(
         user_key || ':' || rec_rank || ':' || service_id || ':' || title || ':' ||
         null_ai_cause || ':' ||
         coalesce(rule_weighted_score::text, 'null') || ':' ||
         coalesce(final_score::text, 'null') || ':' ||
         coalesce(nullif(left(ai_reason, 80), ''), 'no-reason'),
         ' | ' order by rec_rank, final_score desc, user_key
       ), '')
from (
  select *
  from classified
  order by rec_rank, final_score desc, user_key
  limit 12
) s;
SQL

rows="$(smoke_db_query "${SQL}")"

echo "${rows}" | while IFS=$'\t' read -r metric value; do
  [[ -n "${metric}" ]] || continue
  echo "METRIC ${metric}=${value}"
done
