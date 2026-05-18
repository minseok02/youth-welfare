#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

TARGET_USER_KEY="${TARGET_USER_KEY:-}"
TARGET_SERVICE_IDS_CSV="${TARGET_SERVICE_IDS_CSV:-2736,3257,3281,3575,3714}"
TOP_COMPETITOR_LIMIT="${TOP_COMPETITOR_LIMIT:-3}"
LATEST_FETCH_SIZE="${LATEST_FETCH_SIZE:-20}"
BLOCKER_WINDOW_LIMIT="${BLOCKER_WINDOW_LIMIT:-40}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
JSONL_FILE="${ARTIFACT_DIR}/latest-window-audit.jsonl"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

normalize_bool() {
  local value="${1,,}"
  case "${value}" in
    true|false) printf '%s' "${value}" ;;
    *)
      echo "unsupported boolean value: ${1}" >&2
      exit 1
      ;;
  esac
}

KEEP_ARTIFACTS="$(normalize_bool "${KEEP_ARTIFACTS}")"

resolve_target_user_key() {
  if [[ -n "${TARGET_USER_KEY}" ]]; then
    printf '%s' "${TARGET_USER_KEY}"
    return 0
  fi

  smoke_db_query "
    with latest_per_user as (
      select user_key, max(recommended_at) as recommended_at
      from user_recommendations
      group by user_key
    )
    select user_key
    from latest_per_user
    order by recommended_at desc, user_key desc
    limit 1;
  "
}

resolve_top_competitor_ids_csv() {
  local user_key="$1"

  smoke_db_query "
    with target_batch as (
      select max(recommended_at) as recommended_at
      from user_recommendations
      where user_key = '${user_key}'
    ),
    ranked as (
      select
        ws.id as service_id,
        row_number() over (
          order by ur.final_score desc, ur.id asc
        ) as final_rank
      from user_recommendations ur
      join target_batch tb
        on tb.recommended_at = ur.recommended_at
      join welfare_services ws
        on ws.id = ur.service_id
      where ur.user_key = '${user_key}'
    )
    select coalesce(string_agg(service_id::text, ',' order by final_rank), '')
    from ranked
    where final_rank <= ${TOP_COMPETITOR_LIMIT};
  "
}

merge_csv_ids() {
  python3 - "$1" "$2" <<'PY'
import sys

seen = set()
merged = []
for raw in sys.argv[1:]:
    for part in raw.split(","):
        value = part.strip()
        if not value or value in seen:
            continue
        seen.add(value)
        merged.append(value)

print(",".join(merged))
PY
}

FOCUS_USER_KEY="$(resolve_target_user_key)"
if [[ -z "${FOCUS_USER_KEY}" ]]; then
  echo "missing target user_key and no recommendation batch exists" >&2
  exit 1
fi

TOP_COMPETITOR_IDS_CSV="$(resolve_top_competitor_ids_csv "${FOCUS_USER_KEY}")"
FOCUS_SERVICE_IDS_CSV="$(merge_csv_ids "${TARGET_SERVICE_IDS_CSV}" "${TOP_COMPETITOR_IDS_CSV}")"

if [[ -z "${FOCUS_SERVICE_IDS_CSV}" ]]; then
  echo "no focus service ids resolved" >&2
  exit 1
fi

read -r -d '' SQL <<SQL || true
with target_family_ids as (
  select unnest(string_to_array('${TARGET_SERVICE_IDS_CSV}', ','))::bigint as service_id
),
focus_ids as (
  select unnest(string_to_array('${FOCUS_SERVICE_IDS_CSV}', ','))::bigint as service_id
),
user_ctx as (
  select
    up.user_key,
    up.age,
    up.age_band,
    up.sido,
    up.sgg,
    nullif(trim(up.region_code), '') as region_code,
    up.income_level
  from user_profiles up
  where up.user_key = '${FOCUS_USER_KEY}'
),
params as (
  select
    uc.*,
    case
      when uc.region_code is not null then 'REGION_CODE'
      when uc.sido is not null and trim(uc.sido) <> '' then 'SIDO'
      else 'NONE'
    end as branch_mode,
    ${LATEST_FETCH_SIZE}::int as latest_fetch_size,
    ${BLOCKER_WINDOW_LIMIT}::int as blocker_window_limit
  from user_ctx uc
),
latest_candidates as (
  select
    ws.id as service_id,
    ws.title,
    ws.source_type,
    coalesce(ws.unified_category, '') as unified_category,
    ws.search_youth_relevant,
    coalesce(ws.life_stage, '') as life_stage,
    exists (
      select 1 from service_regions sr1
      where sr1.service_id = ws.id
    ) as has_any_region,
    exists (
      select 1
      from service_regions sr2
      join params p on true
      where sr2.service_id = ws.id
        and p.region_code is not null
        and sr2.region_code = p.region_code
    ) as exact_region_match,
    exists (
      select 1
      from service_regions sr3
      join params p on true
      where sr3.service_id = ws.id
        and p.sido is not null
        and sr3.sido_name = p.sido
    ) as exact_sido_match,
    ws.created_at as sort_ts
  from welfare_services ws
  join params p on true
  where ws.status in ('ACTIVE', 'UPCOMING')
    and (ws.min_age is null or ws.min_age <= p.age)
    and (ws.max_age is null or ws.max_age >= p.age)
    and (
      (ws.min_income is null and ws.max_income is null)
      or (ws.min_income = 0 and ws.max_income = 0)
      or (
        (ws.min_income is null or ws.min_income <= p.income_level)
        and (ws.max_income is null or ws.max_income >= p.income_level)
      )
    )
    and (
      p.branch_mode = 'NONE'
      or (
        p.branch_mode = 'REGION_CODE'
        and (
          not exists (
            select 1 from service_regions sr4
            where sr4.service_id = ws.id
          )
          or exists (
            select 1 from service_regions sr5
            where sr5.service_id = ws.id
              and sr5.region_code = p.region_code
          )
          or (
            ws.source_type = 'BOKJIRO_LOCAL'
            and ws.search_youth_relevant = true
            and coalesce(ws.unified_category, '') <> '기타'
            and exists (
              select 1 from service_regions sr5s
              where sr5s.service_id = ws.id
                and p.sido is not null
                and sr5s.sido_name = p.sido
            )
          )
        )
      )
      or (
        p.branch_mode = 'SIDO'
        and (
          not exists (
            select 1 from service_regions sr6
            where sr6.service_id = ws.id
          )
          or exists (
            select 1 from service_regions sr7
            where sr7.service_id = ws.id
              and sr7.sido_name = p.sido
          )
        )
      )
    )
),
latest_ranked as (
  select
    lc.*,
    case
      when coalesce(lc.has_any_region, false) = false then 'NATIONWIDE'
      when coalesce(lc.exact_region_match, false) = true then 'EXACT_REGION'
      when coalesce(lc.exact_sido_match, false) = true then 'EXACT_SIDO'
      else 'REGION_MISMATCH'
    end as region_projection,
    case
      when (select branch_mode from params) = 'REGION_CODE'
           and lc.source_type = 'BOKJIRO_LOCAL'
           and lc.exact_region_match then 0
      when (select branch_mode from params) = 'REGION_CODE'
           and lc.source_type = 'BOKJIRO_LOCAL'
           and lc.exact_sido_match
           and lc.search_youth_relevant = true
           and lc.unified_category <> '기타' then 1
      when (select branch_mode from params) = 'SIDO'
           and lc.source_type = 'BOKJIRO_LOCAL'
           and lc.exact_sido_match then 0
      when (select branch_mode from params) = 'REGION_CODE'
           and lc.exact_region_match then 2
      when (select branch_mode from params) = 'SIDO'
           and lc.exact_sido_match then 1
      else 3
    end as region_tier,
    case
      when (select branch_mode from params) = 'REGION_CODE'
           and lc.source_type = 'BOKJIRO_LOCAL'
           and lc.exact_region_match
           and lc.search_youth_relevant = true then 0
      when (select branch_mode from params) = 'REGION_CODE'
           and lc.source_type = 'BOKJIRO_LOCAL'
           and lc.exact_sido_match
           and lc.search_youth_relevant = true
           and lc.unified_category <> '기타' then 1
      when (select branch_mode from params) = 'SIDO'
           and lc.source_type = 'BOKJIRO_LOCAL'
           and lc.exact_sido_match
           and lc.search_youth_relevant = true then 0
      when (select branch_mode from params) = 'REGION_CODE'
           and lc.source_type = 'BOKJIRO_LOCAL'
           and lc.exact_region_match then 2
      when (select branch_mode from params) = 'SIDO'
           and lc.source_type = 'BOKJIRO_LOCAL'
           and lc.exact_sido_match then 1
      else 3
    end as youth_tier,
    case
      when (select branch_mode from params) = 'REGION_CODE'
           and lc.source_type = 'BOKJIRO_LOCAL'
           and lc.exact_region_match
           and lc.unified_category <> '기타' then 0
      when (select branch_mode from params) = 'REGION_CODE'
           and lc.source_type = 'BOKJIRO_LOCAL'
           and lc.exact_sido_match
           and lc.search_youth_relevant = true
           and lc.unified_category <> '기타' then 1
      when (select branch_mode from params) = 'SIDO'
           and lc.source_type = 'BOKJIRO_LOCAL'
           and lc.exact_sido_match
           and lc.unified_category <> '기타' then 0
      when (select branch_mode from params) = 'REGION_CODE'
           and lc.source_type = 'BOKJIRO_LOCAL'
           and lc.exact_region_match then 2
      when (select branch_mode from params) = 'SIDO'
           and lc.source_type = 'BOKJIRO_LOCAL'
           and lc.exact_sido_match then 1
      else 3
    end as category_tier,
    row_number() over (
      order by
        case
          when (select branch_mode from params) = 'REGION_CODE'
               and lc.source_type = 'BOKJIRO_LOCAL'
               and lc.exact_region_match then 0
          when (select branch_mode from params) = 'REGION_CODE'
               and lc.source_type = 'BOKJIRO_LOCAL'
               and lc.exact_sido_match
               and lc.search_youth_relevant = true
               and lc.unified_category <> '기타' then 1
          when (select branch_mode from params) = 'SIDO'
               and lc.source_type = 'BOKJIRO_LOCAL'
               and lc.exact_sido_match then 0
          when (select branch_mode from params) = 'REGION_CODE'
               and lc.exact_region_match then 2
          when (select branch_mode from params) = 'SIDO'
               and lc.exact_sido_match then 1
          else 3
        end asc,
        case
          when (select branch_mode from params) = 'REGION_CODE'
               and lc.source_type = 'BOKJIRO_LOCAL'
               and lc.exact_region_match
               and lc.search_youth_relevant = true then 0
          when (select branch_mode from params) = 'REGION_CODE'
               and lc.source_type = 'BOKJIRO_LOCAL'
               and lc.exact_sido_match
               and lc.search_youth_relevant = true
               and lc.unified_category <> '기타' then 1
          when (select branch_mode from params) = 'SIDO'
               and lc.source_type = 'BOKJIRO_LOCAL'
               and lc.exact_sido_match
               and lc.search_youth_relevant = true then 0
          when (select branch_mode from params) = 'REGION_CODE'
               and lc.source_type = 'BOKJIRO_LOCAL'
               and lc.exact_region_match then 2
          when (select branch_mode from params) = 'SIDO'
               and lc.source_type = 'BOKJIRO_LOCAL'
               and lc.exact_sido_match then 1
          else 3
        end asc,
        case
          when (select branch_mode from params) = 'REGION_CODE'
               and lc.source_type = 'BOKJIRO_LOCAL'
               and lc.exact_region_match
               and lc.unified_category <> '기타' then 0
          when (select branch_mode from params) = 'REGION_CODE'
               and lc.source_type = 'BOKJIRO_LOCAL'
               and lc.exact_sido_match
               and lc.search_youth_relevant = true
               and lc.unified_category <> '기타' then 1
          when (select branch_mode from params) = 'SIDO'
               and lc.source_type = 'BOKJIRO_LOCAL'
               and lc.exact_sido_match
               and lc.unified_category <> '기타' then 0
          when (select branch_mode from params) = 'REGION_CODE'
               and lc.source_type = 'BOKJIRO_LOCAL'
               and lc.exact_region_match then 2
          when (select branch_mode from params) = 'SIDO'
               and lc.source_type = 'BOKJIRO_LOCAL'
               and lc.exact_sido_match then 1
          else 3
        end asc,
        lc.sort_ts desc,
        lc.service_id desc
    ) as rank_no
  from latest_candidates lc
),
target_meta as (
  select
    ws.id as service_id,
    ws.title,
    ws.source_type,
    coalesce(ws.unified_category, '') as unified_category,
    ws.search_youth_relevant,
    coalesce(ws.life_stage, '') as life_stage
  from welfare_services ws
  where ws.id in (select service_id from target_family_ids)
),
rows as (
  select json_build_object(
    'recordType', 'metric',
    'key', 'target_user_key',
    'value', (select user_key from params)
  )::text as row_json
  union all
  select json_build_object(
    'recordType', 'metric',
    'key', 'target_service_ids_csv',
    'value', '${TARGET_SERVICE_IDS_CSV}'
  )::text
  union all
  select json_build_object(
    'recordType', 'metric',
    'key', 'top_competitor_ids_csv',
    'value', '${TOP_COMPETITOR_IDS_CSV}'
  )::text
  union all
  select json_build_object(
    'recordType', 'metric',
    'key', 'query_context',
    'value',
      (select branch_mode from params) || ':' ||
      coalesce((select region_code from params), '') || ':' ||
      coalesce((select sido from params), '') || ':' ||
      coalesce((select age::text from params), 'null') || ':' ||
      coalesce((select income_level::text from params), 'null') || ':' ||
      (select latest_fetch_size::text from params) || ':' ||
      (select blocker_window_limit::text from params)
  )::text
  union all
  select json_build_object(
    'recordType', 'metric',
    'key', 'candidate_counts',
    'value', 'latest=' || (select count(*)::text from latest_ranked)
  )::text
  union all
  select json_build_object(
    'recordType', 'target',
    'scope', 'latest',
    'serviceId', tm.service_id,
    'title', tm.title,
    'sourceType', tm.source_type,
    'unifiedCategory', tm.unified_category,
    'searchYouthRelevant', tm.search_youth_relevant,
    'lifeStage', tm.life_stage,
    'presentInQuery', (lr.service_id is not null),
    'rank', lr.rank_no,
    'inWindow', (lr.rank_no is not null and lr.rank_no <= (select latest_fetch_size from params)),
    'regionProjection', lr.region_projection,
    'regionTier', lr.region_tier,
    'youthTier', lr.youth_tier,
    'categoryTier', lr.category_tier
  )::text
  from target_meta tm
  left join latest_ranked lr
    on lr.service_id = tm.service_id
  union all
  select json_build_object(
    'recordType', 'top',
    'scope', 'latest',
    'serviceId', lr.service_id,
    'title', lr.title,
    'sourceType', lr.source_type,
    'unifiedCategory', lr.unified_category,
    'searchYouthRelevant', lr.search_youth_relevant,
    'regionProjection', lr.region_projection,
    'regionTier', lr.region_tier,
    'youthTier', lr.youth_tier,
    'categoryTier', lr.category_tier,
    'rank', lr.rank_no
  )::text
  from latest_ranked lr
  where lr.rank_no <= (select latest_fetch_size from params)
  union all
  select json_build_object(
    'recordType', 'blocker',
    'scope', 'latest',
    'serviceId', lr.service_id,
    'title', lr.title,
    'sourceType', lr.source_type,
    'unifiedCategory', lr.unified_category,
    'searchYouthRelevant', lr.search_youth_relevant,
    'regionProjection', lr.region_projection,
    'regionTier', lr.region_tier,
    'youthTier', lr.youth_tier,
    'categoryTier', lr.category_tier,
    'rank', lr.rank_no,
    'isTargetFamily', exists (
      select 1 from target_family_ids tf
      where tf.service_id = lr.service_id
    ),
    'isTopCompetitor', exists (
      select 1 from focus_ids fi
      where fi.service_id = lr.service_id
        and fi.service_id not in (select service_id from target_family_ids)
    )
  )::text
  from latest_ranked lr
  where lr.rank_no > (select latest_fetch_size from params)
    and lr.rank_no <= (select blocker_window_limit from params)
)
select row_json
from rows;
SQL

smoke_print_step "Running latest-window audit"
smoke_db_query "${SQL}" > "${JSONL_FILE}"

python3 - "${JSONL_FILE}" <<'PY'
import json
import sys
from collections import OrderedDict

rows = []
with open(sys.argv[1], "r", encoding="utf-8") as fp:
    for line in fp:
        line = line.strip()
        if not line:
            continue
        rows.append(json.loads(line))

metrics = OrderedDict()
targets = []
top_rows = []
blockers = []
for row in rows:
    record_type = row.get("recordType")
    if record_type == "metric":
        metrics[row["key"]] = row.get("value")
    elif record_type == "target":
        targets.append(row)
    elif record_type == "top":
        top_rows.append(row)
    elif record_type == "blocker":
        blockers.append(row)

for key, value in metrics.items():
    print(f"METRIC {key}={value}")

print()
print("[TARGET LATEST]")
for row in sorted(targets, key=lambda item: item["serviceId"]):
    rank = row.get("rank")
    print(
        f"{row['serviceId']}\t"
        f"present={row.get('presentInQuery')}\t"
        f"rank={rank if rank is not None else 'None'}\t"
        f"in_window={row.get('inWindow')}\t"
        f"projection={row.get('regionProjection')}\t"
        f"tiers={row.get('regionTier')}/{row.get('youthTier')}/{row.get('categoryTier')}\t"
        f"searchYouthRelevant={row.get('searchYouthRelevant')}\t"
        f"category={row.get('unifiedCategory')}\t"
        f"title={row.get('title')}"
    )

print()
print("[TOP LATEST]")
for row in sorted(top_rows, key=lambda item: item["rank"]):
    print(
        f"{row['rank']}\t{row['serviceId']}\t{row.get('sourceType')}\t"
        f"{row.get('regionProjection')}\t"
        f"tiers={row.get('regionTier')}/{row.get('youthTier')}/{row.get('categoryTier')}\t"
        f"searchYouthRelevant={row.get('searchYouthRelevant')}\t"
        f"category={row.get('unifiedCategory')}\t"
        f"title={row.get('title')}"
    )

print()
print("[BLOCKER LATEST]")
for row in sorted(blockers, key=lambda item: item["rank"]):
    flags = []
    if row.get("isTargetFamily"):
        flags.append("target")
    if row.get("isTopCompetitor"):
        flags.append("competitor")
    flag_text = ",".join(flags) if flags else "-"
    print(
        f"{row['rank']}\t{row['serviceId']}\t{flag_text}\t{row.get('sourceType')}\t"
        f"{row.get('regionProjection')}\t"
        f"tiers={row.get('regionTier')}/{row.get('youthTier')}/{row.get('categoryTier')}\t"
        f"searchYouthRelevant={row.get('searchYouthRelevant')}\t"
        f"category={row.get('unifiedCategory')}\t"
        f"title={row.get('title')}"
    )

print()
print(f"METRIC artifact_dir={sys.argv[1].rsplit('/', 1)[0]}")
PY
