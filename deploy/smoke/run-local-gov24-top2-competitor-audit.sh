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
gov24_top2 as (
  select * from ranked where source_type = 'GOV24' and rec_rank <= 2
),
competitor_pairs as (
  select
    g.user_key,
    g.rec_rank as gov24_rank,
    g.service_id as gov24_service_id,
    g.title as gov24_title,
    g.rule_weighted_score as gov24_rule,
    g.ai_score as gov24_ai,
    g.final_score as gov24_final,
    left(g.ai_reason, 120) as gov24_reason,
    r.rec_rank as rival_rank,
    r.source_type as rival_source_type,
    r.service_id as rival_service_id,
    r.title as rival_title,
    r.rule_weighted_score as rival_rule,
    r.ai_score as rival_ai,
    r.final_score as rival_final
  from gov24_top2 g
  join ranked r
    on r.user_key = g.user_key
   and r.rec_rank = 1
)
select 'gov24_top2_competitor_count', count(*)::text
from competitor_pairs
union all
select 'gov24_top2_rank_distribution',
       coalesce(string_agg(gov24_rank || ':' || cnt, ',' order by gov24_rank), '')
from (
  select gov24_rank, count(*) as cnt
  from competitor_pairs
  group by gov24_rank
) rd
union all
select 'gov24_top2_rival_sources',
       coalesce(string_agg(rival_source_type || ':' || cnt, ',' order by rival_source_type), '')
from (
  select rival_source_type, count(*) as cnt
  from competitor_pairs
  group by rival_source_type
) rs
union all
select 'gov24_top2_pair_samples',
       coalesce(string_agg(
         user_key || ':' ||
         'g' || gov24_rank || ':' || gov24_service_id || ':' || gov24_title || ':' ||
         coalesce(gov24_rule::text, 'null') || ':' ||
         coalesce(gov24_ai::text, 'null') || ':' ||
         coalesce(gov24_final::text, 'null') || ':' ||
         coalesce(nullif(gov24_reason, ''), 'no-reason') || ' => ' ||
         'r' || rival_rank || ':' || rival_source_type || ':' || rival_service_id || ':' || rival_title || ':' ||
         coalesce(rival_rule::text, 'null') || ':' ||
         coalesce(rival_ai::text, 'null') || ':' ||
         coalesce(rival_final::text, 'null'),
         ' || ' order by gov24_final desc, user_key
       ), '')
from (
  select *
  from competitor_pairs
  order by gov24_final desc, user_key
  limit 12
) ps
union all
select 'gov24_top2_score_delta_summary',
       coalesce(string_agg(
         metric || ':' || value,
         ',' order by metric
       ), '')
from (
  select 'avg_rule_delta' as metric,
         coalesce(round(avg(gov24_rule - rival_rule), 2), 0)::text as value
  from competitor_pairs
  union all
  select 'avg_ai_delta',
         coalesce(round(avg(coalesce(gov24_ai, 0) - coalesce(rival_ai, 0)), 2), 0)::text
  from competitor_pairs
  union all
  select 'avg_final_delta',
         coalesce(round(avg(gov24_final - rival_final), 5), 0)::text
  from competitor_pairs
) ds;
SQL

rows="$(smoke_db_query "${SQL}")"

echo "${rows}" | while IFS=$'\t' read -r metric value; do
  [[ -n "${metric}" ]] || continue
  echo "METRIC ${metric}=${value}"
done
