#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

TOP_WINDOW_LIMIT="${TOP_WINDOW_LIMIT:-20}"
SAMPLE_LIMIT="${SAMPLE_LIMIT:-20}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"

RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_ROOT="${ARTIFACT_ROOT:-${ROOT_DIR}/tmp/recommendation-region-mismatch-audit}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${RUN_TS_UTC}}"
SUMMARY_OUT="${ARTIFACT_DIR}/recommendation-region-mismatch-summary.txt"
DETAIL_OUT="${ARTIFACT_DIR}/recommendation-region-mismatch-samples.tsv"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"

mkdir -p "${ARTIFACT_DIR}"

read -r -d '' SUMMARY_SQL <<SQL || true
with latest_batch as (
  select ur.user_key, max(ur.recommended_at) as recommended_at
  from user_recommendations ur
  group by ur.user_key
),
ranked as (
  select ur.user_key, ur.service_id, ur.recommended_at,
         row_number() over (partition by ur.user_key order by ur.final_score desc, ur.id asc) as rank_no
  from user_recommendations ur
  join latest_batch lb
    on lb.user_key = ur.user_key
   and lb.recommended_at = ur.recommended_at
),
user_ctx as (
  select up.user_key,
         nullif(trim(up.region_code), '') as region_code,
         nullif(trim(up.sido), '') as sido
  from user_profiles up
),
projection as (
  select
    r.user_key,
    r.recommended_at,
    ws.source_type,
    case
      when uc.region_code is not null then 'REGION_CODE'
      when uc.sido is not null then 'SIDO'
      else 'NONE'
    end as branch_mode,
    case
      when not exists (
        select 1 from service_regions sr
        where sr.service_id = r.service_id
      ) then 'NATIONWIDE'
      when exists (
        select 1 from service_regions sr
        where sr.service_id = r.service_id
          and uc.region_code is not null
          and sr.region_code = uc.region_code
      ) then 'EXACT_REGION'
      when exists (
        select 1 from service_regions sr
        where sr.service_id = r.service_id
          and uc.sido is not null
          and sr.sido_name = uc.sido
      ) then 'EXACT_SIDO'
      else 'REGION_MISMATCH'
    end as region_projection
  from ranked r
  join user_ctx uc on uc.user_key = r.user_key
  join welfare_services ws on ws.id = r.service_id
  where r.rank_no <= ${TOP_WINDOW_LIMIT}
),
mismatched as (
  select
    user_key,
    max(recommended_at) as recommended_at,
    count(*) as mismatch_rows,
    count(*) filter (where source_type = 'GOV24') as gov24_mismatch_rows,
    count(*) filter (where source_type = 'BOKJIRO_LOCAL') as bokjiro_local_mismatch_rows
  from projection
  where branch_mode = 'SIDO'
    and region_projection = 'REGION_MISMATCH'
  group by user_key
)
select
  count(*) as affected_users,
  coalesce(sum(mismatch_rows), 0) as mismatch_rows,
  coalesce(sum(gov24_mismatch_rows), 0) as gov24_mismatch_rows,
  coalesce(sum(bokjiro_local_mismatch_rows), 0) as bokjiro_local_mismatch_rows,
  to_char(min(recommended_at) at time zone 'Asia/Seoul', 'YYYY-MM-DD HH24:MI:SS') as oldest_batch_kst,
  to_char(max(recommended_at) at time zone 'Asia/Seoul', 'YYYY-MM-DD HH24:MI:SS') as newest_batch_kst
from mismatched;
SQL

read -r -d '' DETAIL_SQL <<SQL || true
with latest_batch as (
  select ur.user_key, max(ur.recommended_at) as recommended_at
  from user_recommendations ur
  group by ur.user_key
),
ranked as (
  select ur.user_key, ur.service_id, ur.recommended_at,
         row_number() over (partition by ur.user_key order by ur.final_score desc, ur.id asc) as rank_no
  from user_recommendations ur
  join latest_batch lb
    on lb.user_key = ur.user_key
   and lb.recommended_at = ur.recommended_at
),
user_ctx as (
  select up.user_key,
         nullif(trim(up.region_code), '') as region_code,
         nullif(trim(up.sido), '') as sido,
         nullif(trim(up.sgg), '') as sgg
  from user_profiles up
),
projection as (
  select
    r.user_key,
    r.recommended_at,
    r.rank_no,
    uc.sido,
    uc.sgg,
    ws.id as service_id,
    ws.source_type,
    ws.title,
    case
      when uc.region_code is not null then 'REGION_CODE'
      when uc.sido is not null then 'SIDO'
      else 'NONE'
    end as branch_mode,
    case
      when not exists (
        select 1 from service_regions sr
        where sr.service_id = r.service_id
      ) then 'NATIONWIDE'
      when exists (
        select 1 from service_regions sr
        where sr.service_id = r.service_id
          and uc.region_code is not null
          and sr.region_code = uc.region_code
      ) then 'EXACT_REGION'
      when exists (
        select 1 from service_regions sr
        where sr.service_id = r.service_id
          and uc.sido is not null
          and sr.sido_name = uc.sido
      ) then 'EXACT_SIDO'
      else 'REGION_MISMATCH'
    end as region_projection,
    (
      select string_agg(
        distinct coalesce(sr.sido_name, '?') || '/' || coalesce(sr.sgg_name, '?'),
        ', '
        order by coalesce(sr.sido_name, '?') || '/' || coalesce(sr.sgg_name, '?')
      )
      from service_regions sr
      where sr.service_id = r.service_id
    ) as service_regions_label
  from ranked r
  join user_ctx uc on uc.user_key = r.user_key
  join welfare_services ws on ws.id = r.service_id
  where r.rank_no <= ${TOP_WINDOW_LIMIT}
)
select
  user_key,
  to_char(recommended_at at time zone 'Asia/Seoul', 'YYYY-MM-DD HH24:MI:SS') as recommended_at_kst,
  sido,
  sgg,
  rank_no,
  service_id,
  source_type,
  title,
  service_regions_label
from projection
where branch_mode = 'SIDO'
  and region_projection = 'REGION_MISMATCH'
order by recommended_at desc, user_key desc, rank_no asc
limit ${SAMPLE_LIMIT};
SQL

IFS=$'\t' read -r affected_users mismatch_rows gov24_mismatch_rows bokjiro_local_mismatch_rows oldest_batch_kst newest_batch_kst <<<"$(smoke_db_query "${SUMMARY_SQL}")"
smoke_db_query "${DETAIL_SQL}" > "${DETAIL_OUT}"

cat > "${SUMMARY_OUT}" <<EOF
recommendation_region_mismatch_status=ok
top_window_limit=${TOP_WINDOW_LIMIT}
sample_limit=${SAMPLE_LIMIT}
affected_users=${affected_users}
mismatch_rows=${mismatch_rows}
gov24_mismatch_rows=${gov24_mismatch_rows}
bokjiro_local_mismatch_rows=${bokjiro_local_mismatch_rows}
oldest_batch_kst=${oldest_batch_kst}
newest_batch_kst=${newest_batch_kst}
detail_out=${DETAIL_OUT}
EOF

cat "${SUMMARY_OUT}"
echo
echo "[SAMPLES]"
cat "${DETAIL_OUT}"
