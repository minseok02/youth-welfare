#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
TOP_WINDOW_LIMIT="${TOP_WINDOW_LIMIT:-20}"
USER_LIMIT="${USER_LIMIT:-25}"
DRY_RUN="${DRY_RUN:-true}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"

RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_ROOT="${ARTIFACT_ROOT:-${ROOT_DIR}/tmp/recommendation-region-mismatch-repair}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${RUN_TS_UTC}}"
SUMMARY_OUT="${ARTIFACT_DIR}/recommendation-region-mismatch-repair-summary.txt"
TARGETS_OUT="${ARTIFACT_DIR}/target-users.tsv"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
DRY_RUN="$(smoke_normalize_bool "${DRY_RUN}")"

mkdir -p "${ARTIFACT_DIR}"

env_file="$(smoke_resolve_env_file "${ENV_FILE:-${ROOT_DIR}/.env}" "${ROOT_DIR}")"
jwt_secret="$(smoke_load_env_value "${env_file}" JWT_SECRET)"
access_expiration_ms="$(smoke_load_env_value "${env_file}" JWT_ACCESS_EXPIRATION 1800000)"

if [[ -z "${jwt_secret}" ]]; then
  echo "JWT_SECRET is empty; cannot mint user access token" >&2
  exit 1
fi

read -r -d '' TARGET_SQL <<SQL || true
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
    p.user_key,
    max(p.recommended_at) as recommended_at,
    count(*) as mismatch_rows,
    count(*) filter (where p.source_type = 'GOV24') as gov24_mismatch_rows
  from projection p
  where p.branch_mode = 'SIDO'
    and p.region_projection = 'REGION_MISMATCH'
  group by p.user_key
)
select
  u.user_key,
  u.id,
  to_char(m.recommended_at at time zone 'Asia/Seoul', 'YYYY-MM-DD HH24:MI:SS') as recommended_at_kst,
  m.mismatch_rows,
  m.gov24_mismatch_rows
from mismatched m
join users u on u.user_key = m.user_key
order by m.recommended_at desc, u.user_key desc
limit ${USER_LIMIT};
SQL

smoke_db_query "${TARGET_SQL}" > "${TARGETS_OUT}"

target_user_count="$(awk 'NF {count++} END {print count+0}' "${TARGETS_OUT}")"
success_count=0
failure_count=0

while IFS=$'\t' read -r user_key user_id recommended_at_kst mismatch_rows gov24_mismatch_rows; do
  [[ -n "${user_key}" ]] || continue
  if [[ "${DRY_RUN}" == "true" ]]; then
    continue
  fi

  access_token="$(smoke_mint_access_token "${jwt_secret}" "${user_key}" "${user_id}" "ROLE_USER" "${access_expiration_ms}")"
  response_file="${ARTIFACT_DIR}/refresh-${user_key}.json"
  status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/recommendations/refresh?personal=true" "${response_file}" \
      -H "Authorization: Bearer ${access_token}"
  )"
  if [[ "${status}" == "200" ]]; then
    success_count=$((success_count + 1))
  else
    failure_count=$((failure_count + 1))
  fi
done < "${TARGETS_OUT}"

cat > "${SUMMARY_OUT}" <<EOF
recommendation_region_mismatch_repair_status=ok
dry_run=${DRY_RUN}
top_window_limit=${TOP_WINDOW_LIMIT}
user_limit=${USER_LIMIT}
target_user_count=${target_user_count}
refresh_success_count=${success_count}
refresh_failure_count=${failure_count}
targets_out=${TARGETS_OUT}
EOF

cat "${SUMMARY_OUT}"
echo
echo "[TARGET USERS]"
cat "${TARGETS_OUT}"
