#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_HEALTH_URL="${APP_HEALTH_URL:-http://127.0.0.1:8082/actuator/health}"
MIN_REGION_SERVICE_PCT="${MIN_REGION_SERVICE_PCT:-85}"
MAX_REGIONLESS_TRUE_LOCAL_AGENCY_SERVICES="${MAX_REGIONLESS_TRUE_LOCAL_AGENCY_SERVICES:-10}"

metric() {
  local key="$1"
  local value="$2"
  printf 'METRIC %s=%s\n' "${key}" "${value}"
}

section() {
  printf '\n[%s]\n' "$1"
}

smoke_require_command curl
smoke_require_command python3

section "health"
curl -fsS "${APP_HEALTH_URL}" >/dev/null
echo "OK app_health=${APP_HEALTH_URL}"

section "coverage"
coverage_row="$(smoke_db_query "
with gov24 as (
  select ws.id, rap.payload_json::jsonb as payload
  from welfare_services ws
  join raw_api_payloads rap
    on rap.source_type = ws.source_type
   and rap.source_id = ws.source_id
   and rap.api_category = 'LIST'
  where ws.source_type = 'GOV24'
),
region_counts as (
  select service_id, count(*) as region_rows
  from service_regions
  group by service_id
),
enriched as (
  select
    g.id,
    g.payload,
    coalesce(rc.region_rows, 0) as region_rows,
    concat_ws(' ',
      g.payload->>'소관기관명',
      g.payload->>'접수기관',
      g.payload->>'부서명',
      g.payload->>'서비스명',
      g.payload->>'서비스목적요약',
      g.payload->>'지원대상',
      g.payload->>'선정기준',
      g.payload->>'지원내용',
      g.payload->>'신청방법'
    ) as haystack
  from gov24 g
  left join region_counts rc on rc.service_id = g.id
)
select
  count(*) as total_services,
  count(*) filter (where region_rows > 0) as region_services,
  count(*) filter (where region_rows = 0) as regionless_services,
  round(100.0 * count(*) filter (where region_rows > 0) / nullif(count(*), 0), 2) as region_service_pct,
  coalesce(sum(region_rows), 0) as region_rows,
  count(*) filter (where region_rows = 1) as single_region_services,
  count(*) filter (where region_rows between 2 and 10) as multi_region_2_10_services,
  count(*) filter (where region_rows >= 11) as multi_region_11_plus_services,
  count(*) filter (where region_rows = 0 and haystack like '%전국%') as regionless_with_nationwide_text,
  count(*) filter (where region_rows = 0 and payload->>'소관기관유형' like '%중앙%') as regionless_central_agency_services,
  count(*) filter (where region_rows = 0 and coalesce(payload->>'소관기관유형','') not like '%중앙%') as regionless_non_central_agency_services,
  count(*) filter (
    where region_rows = 0
      and coalesce(payload->>'소관기관유형','') in ('광역시도', '시군구', '교육청', '지방공기업', '지방출자_출연기관')
  ) as regionless_true_local_agency_services
from enriched;
")"

IFS=$'\t' read -r total_services region_services regionless_services region_service_pct region_rows single_region_services multi_region_2_10_services multi_region_11_plus_services regionless_with_nationwide_text regionless_central_agency_services regionless_non_central_agency_services regionless_true_local_agency_services <<<"${coverage_row}"

metric "gov24_total_services" "${total_services}"
metric "gov24_region_services" "${region_services}"
metric "gov24_regionless_services" "${regionless_services}"
metric "gov24_region_service_pct" "${region_service_pct}"
metric "gov24_region_rows" "${region_rows}"
metric "gov24_single_region_services" "${single_region_services}"
metric "gov24_multi_region_2_10_services" "${multi_region_2_10_services}"
metric "gov24_multi_region_11_plus_services" "${multi_region_11_plus_services}"
metric "gov24_regionless_with_nationwide_text" "${regionless_with_nationwide_text}"
metric "gov24_regionless_central_agency_services" "${regionless_central_agency_services}"
metric "gov24_regionless_non_central_agency_services" "${regionless_non_central_agency_services}"
metric "gov24_regionless_local_agency_services" "${regionless_true_local_agency_services}"
metric "gov24_regionless_true_local_agency_services" "${regionless_true_local_agency_services}"

section "sido_distribution"
smoke_db_query "
select sr.sido_name || E'\t' || count(distinct sr.service_id) || E'\t' || count(*)
from service_regions sr
join welfare_services ws on ws.id = sr.service_id
where ws.source_type = 'GOV24'
group by sr.sido_name
order by count(distinct sr.service_id) desc, sr.sido_name
limit 20;
" | awk -F $'\t' '{ printf "SIDO sido=%s services=%s rows=%s\n", $1, $2, $3 }'

section "regionless_agency_type_distribution"
smoke_db_query "
with gov24 as (
  select ws.id, rap.payload_json::jsonb as payload
  from welfare_services ws
  join raw_api_payloads rap
    on rap.source_type = ws.source_type
   and rap.source_id = ws.source_id
   and rap.api_category = 'LIST'
  where ws.source_type = 'GOV24'
),
region_counts as (
  select service_id, count(*) as region_rows
  from service_regions
  group by service_id
),
enriched as (
  select
    g.payload,
    coalesce(rc.region_rows, 0) as region_rows,
    concat_ws(' ',
      g.payload->>'소관기관명',
      g.payload->>'접수기관',
      g.payload->>'부서명',
      g.payload->>'서비스명',
      g.payload->>'서비스목적요약',
      g.payload->>'지원대상',
      g.payload->>'선정기준',
      g.payload->>'지원내용',
      g.payload->>'신청방법'
    ) as haystack
  from gov24 g
  left join region_counts rc on rc.service_id = g.id
)
select
  coalesce(payload->>'소관기관유형', '') || E'\t' ||
  count(*)
from enriched
where region_rows = 0
group by coalesce(payload->>'소관기관유형', '')
order by count(*) desc, coalesce(payload->>'소관기관유형', '')
limit 20;
" | awk -F $'\t' '{ printf "REGIONLESS_TYPE agency_type=%s count=%s\n", $1, $2 }'

section "regionless_non_central_top_agencies"
smoke_db_query "
with gov24 as (
  select ws.id, rap.payload_json::jsonb as payload
  from welfare_services ws
  join raw_api_payloads rap
    on rap.source_type = ws.source_type
   and rap.source_id = ws.source_id
   and rap.api_category = 'LIST'
  where ws.source_type = 'GOV24'
),
region_counts as (
  select service_id, count(*) as region_rows
  from service_regions
  group by service_id
),
enriched as (
  select
    g.payload,
    coalesce(rc.region_rows, 0) as region_rows,
    concat_ws(' ',
      g.payload->>'소관기관명',
      g.payload->>'접수기관',
      g.payload->>'부서명',
      g.payload->>'서비스명',
      g.payload->>'서비스목적요약',
      g.payload->>'지원대상',
      g.payload->>'선정기준',
      g.payload->>'지원내용',
      g.payload->>'신청방법'
    ) as haystack
  from gov24 g
  left join region_counts rc on rc.service_id = g.id
)
select
  coalesce(payload->>'소관기관유형', '') || E'\t' ||
  coalesce(payload->>'소관기관명', '') || E'\t' ||
  count(*)
from enriched
where region_rows = 0
  and coalesce(payload->>'소관기관유형','') not like '%중앙%'
  and haystack not like '%전국%'
group by coalesce(payload->>'소관기관유형', ''), coalesce(payload->>'소관기관명', '')
order by count(*) desc, coalesce(payload->>'소관기관명', '')
limit 20;
" | awk -F $'\t' '{ printf "REGIONLESS agency_type=%s agency=%s count=%s\n", $1, $2, $3 }'

section "regionless_true_local_samples"
smoke_db_query "
with gov24 as (
  select ws.id, ws.source_id, rap.payload_json::jsonb as payload
  from welfare_services ws
  join raw_api_payloads rap
    on rap.source_type = ws.source_type
   and rap.source_id = ws.source_id
   and rap.api_category = 'LIST'
  where ws.source_type = 'GOV24'
),
region_counts as (
  select service_id, count(*) as region_rows
  from service_regions
  group by service_id
)
select
  g.source_id || E'\t' ||
  coalesce(g.payload->>'소관기관유형', '') || E'\t' ||
  coalesce(g.payload->>'소관기관명', '') || E'\t' ||
  coalesce(g.payload->>'서비스명', '') || E'\t' ||
  case
    when coalesce(g.payload->>'소관기관명', '') like '%고성%' then 'AMBIGUOUS_GEO_NAME'
    when coalesce(g.payload->>'서비스명', '') like '%시연 테스트%' then 'TEST_OR_ODD_ROW'
    else 'NO_SAFE_REGION_SIGNAL'
  end
from gov24 g
left join region_counts rc on rc.service_id = g.id
where coalesce(rc.region_rows, 0) = 0
  and coalesce(g.payload->>'소관기관유형','') in ('광역시도', '시군구', '교육청', '지방공기업', '지방출자_출연기관')
order by coalesce(g.payload->>'소관기관유형', ''), coalesce(g.payload->>'소관기관명', ''), g.source_id
limit 20;
" | awk -F $'\t' '{ printf "REGIONLESS_TRUE_LOCAL source_id=%s agency_type=%s agency=%s service=%s reason=%s\n", $1, $2, $3, $4, $5 }'

python3 - "${region_service_pct}" "${MIN_REGION_SERVICE_PCT}" "${regionless_true_local_agency_services}" "${MAX_REGIONLESS_TRUE_LOCAL_AGENCY_SERVICES}" <<'PY'
import sys

region_pct = float(sys.argv[1])
min_region_pct = float(sys.argv[2])
regionless_true_local = int(sys.argv[3])
max_regionless_true_local = int(sys.argv[4])

if region_pct < min_region_pct:
    raise SystemExit(
        f"gov24 region coverage below threshold: pct={region_pct} min={min_region_pct}"
    )
if regionless_true_local > max_regionless_true_local:
    raise SystemExit(
        "gov24 regionless true-local-agency count above threshold: "
        f"count={regionless_true_local} max={max_regionless_true_local}"
    )
PY

echo "OK gov24_region_coverage_audit"
