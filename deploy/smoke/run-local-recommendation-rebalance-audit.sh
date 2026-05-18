#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
TARGET_USER_KEY="${TARGET_USER_KEY:-}"
TARGET_SERVICE_IDS_CSV="${TARGET_SERVICE_IDS_CSV:-2736,3257,3281,3575,3714}"
BASE_FETCH_SIZE="${BASE_FETCH_SIZE:-150}"
BASE_WINDOW_LIMIT="${BASE_WINDOW_LIMIT:-50}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/admin-login.json"
DIAGNOSTICS_RESPONSE="${ARTIFACT_DIR}/recommendation-diagnostics.json"
RAW_BASE_TSV="${ARTIFACT_DIR}/raw-base-candidates.tsv"

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

extract_access_token() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

print(payload["data"]["accessToken"])
PY
}

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

build_diagnostics_query_args() {
  python3 - "$1" <<'PY'
import sys
from urllib.parse import quote

for raw in sys.argv[1].split(","):
    value = raw.strip()
    if value:
        print(f"serviceId={quote(value)}")
PY
}

FOCUS_USER_KEY="$(resolve_target_user_key)"
if [[ -z "${FOCUS_USER_KEY}" ]]; then
  echo "missing target user_key and no recommendation batch exists" >&2
  exit 1
fi

read -r -d '' SQL <<SQL || true
with target_ids as (
  select unnest(string_to_array('${TARGET_SERVICE_IDS_CSV}', ','))::bigint as service_id
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
    end as branch_mode
  from user_ctx uc
),
base_candidates as (
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
    coalesce(ws.last_modified_at, ws.registered_at, ws.created_at) as sort_ts
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
base_ranked as (
  select
    bc.service_id,
    bc.title,
    bc.source_type,
    bc.unified_category,
    bc.search_youth_relevant,
    bc.life_stage,
    case
      when coalesce(bc.has_any_region, false) = false then 'NATIONWIDE'
      when coalesce(bc.exact_region_match, false) = true then 'EXACT_REGION'
      when coalesce(bc.exact_sido_match, false) = true then 'EXACT_SIDO'
      else 'REGION_MISMATCH'
    end as region_projection,
    case
      when (select branch_mode from params) = 'REGION_CODE'
           and bc.source_type = 'BOKJIRO_LOCAL'
           and bc.exact_region_match then 0
      when (select branch_mode from params) = 'REGION_CODE'
           and bc.source_type = 'BOKJIRO_LOCAL'
           and bc.exact_sido_match
           and bc.search_youth_relevant = true
           and bc.unified_category <> '기타' then 1
      when (select branch_mode from params) = 'REGION_CODE'
           and bc.exact_region_match then 2
      when (select branch_mode from params) = 'SIDO'
           and bc.source_type = 'BOKJIRO_LOCAL'
           and bc.exact_sido_match then 0
      when (select branch_mode from params) = 'SIDO'
           and bc.exact_sido_match then 1
      else 3
    end as tier_region_branch,
    case
      when (select branch_mode from params) = 'REGION_CODE'
           and bc.source_type = 'BOKJIRO_LOCAL'
           and bc.exact_region_match
           and bc.search_youth_relevant = true then 0
      when (select branch_mode from params) = 'REGION_CODE'
           and bc.source_type = 'BOKJIRO_LOCAL'
           and bc.exact_region_match then 1
      when (select branch_mode from params) = 'REGION_CODE'
           and bc.source_type = 'BOKJIRO_LOCAL'
           and bc.exact_sido_match
           and bc.search_youth_relevant = true
           and bc.unified_category <> '기타' then 2
      when (select branch_mode from params) = 'SIDO'
           and bc.source_type = 'BOKJIRO_LOCAL'
           and bc.exact_sido_match
           and bc.search_youth_relevant = true then 0
      when (select branch_mode from params) = 'SIDO'
           and bc.source_type = 'BOKJIRO_LOCAL'
           and bc.exact_sido_match then 1
      else 3
    end as tier_youth_local,
    case
      when (select branch_mode from params) = 'REGION_CODE'
           and bc.source_type = 'BOKJIRO_LOCAL'
           and bc.exact_region_match
           and bc.unified_category <> '기타' then 0
      when (select branch_mode from params) = 'REGION_CODE'
           and bc.source_type = 'BOKJIRO_LOCAL'
           and bc.exact_region_match then 1
      when (select branch_mode from params) = 'REGION_CODE'
           and bc.source_type = 'BOKJIRO_LOCAL'
           and bc.exact_sido_match
           and bc.search_youth_relevant = true
           and bc.unified_category <> '기타' then 2
      when (select branch_mode from params) = 'SIDO'
           and bc.source_type = 'BOKJIRO_LOCAL'
           and bc.exact_sido_match
           and bc.unified_category <> '기타' then 0
      when (select branch_mode from params) = 'SIDO'
           and bc.source_type = 'BOKJIRO_LOCAL'
           and bc.exact_sido_match then 1
      else 3
    end as tier_local_category,
    row_number() over (
      order by
        case
          when (select branch_mode from params) = 'REGION_CODE'
               and bc.source_type = 'BOKJIRO_LOCAL'
               and bc.exact_region_match then 0
          when (select branch_mode from params) = 'REGION_CODE'
               and bc.source_type = 'BOKJIRO_LOCAL'
               and bc.exact_sido_match
               and bc.search_youth_relevant = true
               and bc.unified_category <> '기타' then 1
          when (select branch_mode from params) = 'SIDO'
               and bc.source_type = 'BOKJIRO_LOCAL'
               and bc.exact_sido_match then 0
          when (select branch_mode from params) = 'REGION_CODE'
               and bc.exact_region_match then 2
          when (select branch_mode from params) = 'SIDO'
               and bc.exact_sido_match then 1
          else 3
        end asc,
        case
          when (select branch_mode from params) = 'REGION_CODE'
               and bc.source_type = 'BOKJIRO_LOCAL'
               and bc.exact_region_match
               and bc.search_youth_relevant = true then 0
          when (select branch_mode from params) = 'REGION_CODE'
               and bc.source_type = 'BOKJIRO_LOCAL'
               and bc.exact_sido_match
               and bc.search_youth_relevant = true
               and bc.unified_category <> '기타' then 1
          when (select branch_mode from params) = 'SIDO'
               and bc.source_type = 'BOKJIRO_LOCAL'
               and bc.exact_sido_match
               and bc.search_youth_relevant = true then 0
          when (select branch_mode from params) = 'REGION_CODE'
               and bc.source_type = 'BOKJIRO_LOCAL'
               and bc.exact_region_match then 2
          when (select branch_mode from params) = 'SIDO'
               and bc.source_type = 'BOKJIRO_LOCAL'
               and bc.exact_sido_match then 1
          else 3
        end asc,
        case
          when (select branch_mode from params) = 'REGION_CODE'
               and bc.source_type = 'BOKJIRO_LOCAL'
               and bc.exact_region_match
               and bc.unified_category <> '기타' then 0
          when (select branch_mode from params) = 'REGION_CODE'
               and bc.source_type = 'BOKJIRO_LOCAL'
               and bc.exact_sido_match
               and bc.search_youth_relevant = true
               and bc.unified_category <> '기타' then 1
          when (select branch_mode from params) = 'SIDO'
               and bc.source_type = 'BOKJIRO_LOCAL'
               and bc.exact_sido_match
               and bc.unified_category <> '기타' then 0
          when (select branch_mode from params) = 'REGION_CODE'
               and bc.source_type = 'BOKJIRO_LOCAL'
               and bc.exact_region_match then 2
          when (select branch_mode from params) = 'SIDO'
               and bc.source_type = 'BOKJIRO_LOCAL'
               and bc.exact_sido_match then 1
          else 3
        end asc,
        bc.sort_ts desc,
        bc.service_id desc
    ) as raw_rank
  from base_candidates bc
)
select
  br.service_id,
  br.title,
  br.source_type,
  br.unified_category,
  br.search_youth_relevant,
  br.life_stage,
  br.region_projection,
  br.tier_region_branch,
  br.tier_youth_local,
  br.tier_local_category,
  br.raw_rank
from base_ranked br
where br.raw_rank <= ${BASE_FETCH_SIZE}
order by br.raw_rank;
SQL

smoke_db_query "${SQL}" > "${RAW_BASE_TSV}"

RAW_SERVICE_IDS_CSV="$(
  python3 - "${RAW_BASE_TSV}" <<'PY'
import sys
ids = []
for line in open(sys.argv[1], "r", encoding="utf-8"):
    line = line.rstrip("\n")
    if not line:
        continue
    ids.append(line.split("\t", 1)[0])
print(",".join(ids))
PY
)"

if [[ -z "${RAW_SERVICE_IDS_CSV}" ]]; then
  echo "no raw base candidates resolved" >&2
  exit 1
fi

smoke_require_command curl
smoke_require_command python3

smoke_resolve_admin_credentials "${ROOT_DIR}"
: "${ADMIN_EMAIL:?ADMIN_EMAIL is empty; export ADMIN_EMAIL or set SECURITY_ADMIN_EMAILS/.env or /tmp/youth-welfare-admin-smoke-email}"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD is empty; export ADMIN_PASSWORD or set /tmp/youth-welfare-admin-smoke-password}"

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

smoke_print_step "admin login (${ADMIN_EMAIL})"
LOGIN_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${LOGIN_RESPONSE}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${ADMIN_EMAIL}\",
      \"password\": \"${ADMIN_PASSWORD}\"
    }"
)"
smoke_assert_status 200 "${LOGIN_STATUS}" "admin login" "${LOGIN_RESPONSE}"
ADMIN_TOKEN="$(extract_access_token "${LOGIN_RESPONSE}")"

query_args="$(build_diagnostics_query_args "${RAW_SERVICE_IDS_CSV}" | paste -sd'&' -)"
if [[ -z "${query_args}" ]]; then
  echo "failed to build diagnostics query args" >&2
  exit 1
fi

smoke_print_step "recommendation diagnostics"
DIAGNOSTICS_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/recommendation-diagnostics?userKey=${FOCUS_USER_KEY}&${query_args}" "${DIAGNOSTICS_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${DIAGNOSTICS_STATUS}" "recommendation diagnostics" "${DIAGNOSTICS_RESPONSE}"

python3 - "${RAW_BASE_TSV}" "${DIAGNOSTICS_RESPONSE}" "${TARGET_SERVICE_IDS_CSV}" "${BASE_WINDOW_LIMIT}" <<'PY'
import json
import sys
from collections import OrderedDict

raw_tsv_path, diagnostics_path, target_csv, base_window_limit = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
target_ids = {int(v) for v in target_csv.split(",") if v.strip()}

raw_rows = []
for line in open(raw_tsv_path, "r", encoding="utf-8"):
    line = line.rstrip("\n")
    if not line:
        continue
    service_id, title, source_type, category, youth_relevant, life_stage, region_projection, tier_region_branch, tier_youth_local, tier_local_category, raw_rank = line.split("\t")
    raw_rows.append({
        "serviceId": int(service_id),
        "title": title,
        "sourceType": source_type,
        "category": category,
        "searchYouthRelevant": youth_relevant == "t",
        "lifeStage": life_stage,
        "regionProjection": region_projection,
        "tierRegionBranch": int(tier_region_branch),
        "tierYouthLocal": int(tier_youth_local),
        "tierLocalCategory": int(tier_local_category),
        "rawRank": int(raw_rank),
    })

with open(diagnostics_path, "r", encoding="utf-8") as fp:
    payload = json.load(fp)["data"]
    diagnostics = payload["services"]

diag_by_id = {int(row["serviceId"]): row for row in diagnostics}

print(f"METRIC no_priority_profile={payload.get('noPriorityProfile')}")

passed_rows = []
source_rank_counters = {}
for row in raw_rows:
    diag = diag_by_id.get(row["serviceId"], {})
    row["passBase"] = bool(diag.get("passedBaseFilters"))
    row["retainBase"] = bool(diag.get("retainedBaseWindow"))
    row["actualBaseRank"] = diag.get("baseRetrievalRank")
    row["actualRetainedBaseRank"] = diag.get("retainedBaseRank")
    row["actualMergedRank"] = diag.get("mergedCandidateRank")
    row["dropStage"] = diag.get("dropStage")
    row["primary"] = diag.get("primaryAudienceRelevant")
    row["age"] = diag.get("ageConstraintMatched")
    if row["passBase"]:
        source = row["sourceType"]
        source_rank_counters[source] = source_rank_counters.get(source, 0) + 1
        row["sourcePassRank"] = source_rank_counters[source]
        passed_rows.append(row)
    else:
        row["sourcePassRank"] = None

by_source = OrderedDict()
for row in passed_rows:
    by_source.setdefault(row["sourceType"], []).append(row)

rebalanced = []
offset = 0
while True:
    appended = False
    for source_rows in by_source.values():
        if offset < len(source_rows):
            rebalanced.append(source_rows[offset])
            appended = True
    if not appended:
        break
    offset += 1

rebalance_rank_by_id = {}
for idx, row in enumerate(rebalanced, start=1):
    rebalance_rank_by_id[row["serviceId"]] = idx

for row in raw_rows:
    row["rebalanceRank"] = rebalance_rank_by_id.get(row["serviceId"])
    row["retainedBySimulation"] = row["rebalanceRank"] is not None and row["rebalanceRank"] <= base_window_limit

print(f"METRIC target_service_ids_csv={target_csv}")
print(f"METRIC raw_base_candidate_count={len(raw_rows)}")
print(f"METRIC passed_base_candidate_count={len(passed_rows)}")
print(f"METRIC base_window_limit={base_window_limit}")
print()

print("[TARGET REBALANCE]")
for row in [r for r in raw_rows if r["serviceId"] in target_ids]:
    print(
        f"{row['serviceId']}\t"
        f"raw_rank={row['rawRank']}\t"
        f"actual_base_rank={row['actualBaseRank']}\t"
        f"pass_base={row['passBase']}\t"
        f"source={row['sourceType']}\t"
        f"source_pass_rank={row['sourcePassRank']}\t"
        f"rebalance_rank={row['rebalanceRank']}\t"
        f"retain_base={row['retainBase']}\t"
        f"actual_retain_base_rank={row['actualRetainedBaseRank']}\t"
        f"retain_sim={row['retainedBySimulation']}\t"
        f"actual_merged_rank={row['actualMergedRank']}\t"
        f"dropStage={row['dropStage']}\t"
        f"projection={row['regionProjection']}\t"
        f"tiers={row['tierRegionBranch']}/{row['tierYouthLocal']}/{row['tierLocalCategory']}\t"
        f"category={row['category']}\t"
        f"title={row['title']}"
    )

print()
actual_base_rows = [r for r in raw_rows if r["actualBaseRank"] is not None]
actual_base_rows.sort(key=lambda r: (r["actualBaseRank"], r["serviceId"]))

print("[ACTUAL TOP BASE]")
for row in actual_base_rows[:base_window_limit]:
    marker = "target" if row["serviceId"] in target_ids else "-"
    print(
        f"{row['actualBaseRank']}\t{marker}\t{row['serviceId']}\t{row['sourceType']}\t"
        f"raw_rank={row['rawRank']}\trebalance_rank={row['rebalanceRank']}\t"
        f"projection={row['regionProjection']}\ttiers={row['tierRegionBranch']}/{row['tierYouthLocal']}/{row['tierLocalCategory']}\t"
        f"category={row['category']}\ttitle={row['title']}"
    )

print()
print("[TARGET ACTUAL BASE NEIGHBORS]")
for target in [r for r in raw_rows if r["serviceId"] in target_ids and r["actualBaseRank"] is not None]:
    lower = max(1, target["actualBaseRank"] - 3)
    upper = target["actualBaseRank"] + 3
    print(f"## target={target['serviceId']} actual_base_rank={target['actualBaseRank']} title={target['title']}")
    for row in actual_base_rows:
        rank = row["actualBaseRank"]
        if rank is None or rank < lower or rank > upper:
            continue
        marker = "target" if row["serviceId"] == target["serviceId"] else "-"
        print(
            f"{rank}\t{marker}\t{row['serviceId']}\t{row['sourceType']}\t"
            f"raw_rank={row['rawRank']}\trebalance_rank={row['rebalanceRank']}\t"
            f"projection={row['regionProjection']}\ttiers={row['tierRegionBranch']}/{row['tierYouthLocal']}/{row['tierLocalCategory']}\t"
            f"category={row['category']}\ttitle={row['title']}"
        )
    print()

print()
print("[TOP REBALANCED BASE]")
for row in rebalanced[:base_window_limit]:
    marker = "target" if row["serviceId"] in target_ids else "-"
    print(
        f"{row['rebalanceRank']}\t{marker}\t{row['serviceId']}\t{row['sourceType']}\t"
        f"source_pass_rank={row['sourcePassRank']}\tprojection={row['regionProjection']}\t"
        f"category={row['category']}\ttitle={row['title']}"
    )

print()
print("[BLOCKER REBALANCED BASE]")
for row in rebalanced[base_window_limit:base_window_limit + 20]:
    marker = "target" if row["serviceId"] in target_ids else "-"
    print(
        f"{row['rebalanceRank']}\t{marker}\t{row['serviceId']}\t{row['sourceType']}\t"
        f"source_pass_rank={row['sourcePassRank']}\tprojection={row['regionProjection']}\t"
        f"category={row['category']}\ttitle={row['title']}"
    )

print()
print(f"METRIC artifact_dir={diagnostics_path.rsplit('/', 1)[0]}")
PY
