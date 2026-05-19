#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

USER_COHORT="${USER_COHORT:-real_user}"
TOP_N="${TOP_N:-20}"
SAMPLE_LIMIT="${SAMPLE_LIMIT:-20}"

case "${USER_COHORT}" in
  all|example|bounded_local|real_non_example|real_user|local_real_non_example_seed|non_example)
    ;;
  *)
    echo "USER_COHORT must be one of: all, example, bounded_local, real_non_example, real_user, local_real_non_example_seed, non_example" >&2
    exit 1
    ;;
esac

smoke_require_command python3

rows="$(
  smoke_db_query "
    with latest_batch as (
      select user_key, max(recommended_at) as recommended_at
      from user_recommendations
      group by user_key
    ),
    ranked_latest as (
      select
        ur.user_key,
        u.email,
        coalesce(nullif(u.account_origin, ''), 'REAL_USER') as account_origin,
        case
          when coalesce(nullif(u.account_origin, ''), 'REAL_USER') = 'EXAMPLE_SMOKE' then 'EXAMPLE'
          when coalesce(nullif(u.account_origin, ''), 'REAL_USER') = 'BOUNDED_LOCAL' then 'BOUNDED_LOCAL'
          else 'REAL_NON_EXAMPLE'
        end as user_cohort,
        ur.service_id,
        coalesce(ws.title, '') as title,
        coalesce(ws.source_type::text, '') as source_type,
        coalesce(ws.unified_category, '') as category,
        coalesce(ur.ai_status::text, '') as ai_status,
        coalesce(ur.ai_score::text, '') as ai_score,
        coalesce(ur.ai_reason, '') as ai_reason,
        row_number() over (
          partition by ur.user_key
          order by ur.final_score desc nulls last, ur.service_id desc
        ) as saved_rank
      from latest_batch lb
      join user_recommendations ur
        on ur.user_key = lb.user_key
       and ur.recommended_at = lb.recommended_at
      join users u
        on u.user_key = ur.user_key
      left join welfare_services ws
        on ws.id = ur.service_id
      where u.withdrawn_at is null
    )
    select
      user_key,
      email,
      account_origin,
      user_cohort,
      service_id,
      title,
      source_type,
      category,
      ai_status,
      ai_score,
      ai_reason,
      saved_rank
    from ranked_latest
    where saved_rank <= ${TOP_N}
      and (
        '${USER_COHORT}' = 'all'
        or ('${USER_COHORT}' = 'example' and user_cohort = 'EXAMPLE')
        or ('${USER_COHORT}' = 'bounded_local' and user_cohort = 'BOUNDED_LOCAL')
        or ('${USER_COHORT}' = 'real_non_example' and user_cohort = 'REAL_NON_EXAMPLE')
        or ('${USER_COHORT}' = 'real_user' and account_origin = 'REAL_USER')
        or ('${USER_COHORT}' = 'local_real_non_example_seed' and account_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED')
        or ('${USER_COHORT}' = 'non_example' and user_cohort in ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE'))
      )
    order by user_key, saved_rank;
  "
)"

RAW_ROWS="${rows}" python3 - "${USER_COHORT}" "${TOP_N}" "${SAMPLE_LIMIT}" <<'PY'
import csv
import io
import os
import sys
from collections import Counter

user_cohort = sys.argv[1]
top_n = int(sys.argv[2])
sample_limit = int(sys.argv[3])

raw = os.environ.get("RAW_ROWS", "")
reader = csv.reader(io.StringIO(raw), delimiter="\t")
rows = []
for row in reader:
    if len(row) != 12:
        continue
    rows.append({
        "userKey": row[0],
        "email": row[1],
        "accountOrigin": row[2],
        "userCohort": row[3],
        "serviceId": row[4],
        "title": row[5],
        "sourceType": row[6],
        "category": row[7],
        "aiStatus": row[8],
        "aiScore": row[9],
        "aiReason": row[10],
        "savedRank": row[11],
    })

def classify(reason: str) -> str:
    text = (reason or "").strip()
    if not text:
        return "BLANK_REASON"
    if any(token in text for token in ["소득 수준", "소득 5분위", "기초생활수급자", "저소득층"]):
        return "INCOME_MISMATCH"
    if any(token in text for token in ["대학생", "장학금", "학자금", "청소년", "아동", "학생"]):
        return "STUDENT_AUDIENCE_MISMATCH"
    if any(token in text for token in ["강화군민", "거주", "지역", "인천 거주자"]):
        return "REGION_MISMATCH"
    if any(token in text for token in ["직접적인 도움이 적", "직접적인 도움이 되지", "연관성이 낮", "직접적인 혜택이 적"]):
        return "LOW_DIRECT_HELP"
    if any(token in text for token in ["해당되지 않", "대상으로 하", "대상군", "적합하지 않"]):
        return "AUDIENCE_MISMATCH"
    return "OTHER"

zero_rows = []
for row in rows:
    if row["aiStatus"] != "SCORED":
        continue
    try:
        score = float(row["aiScore"])
    except ValueError:
        continue
    if score == 0.0:
        row["bucket"] = classify(row["aiReason"])
        zero_rows.append(row)

bucket_counts = Counter(row["bucket"] for row in zero_rows)
source_counts = Counter((row["sourceType"] or "UNKNOWN") for row in zero_rows)
category_counts = Counter((row["category"] or "UNKNOWN") for row in zero_rows)
origin_counts = Counter((row["accountOrigin"] or "UNKNOWN") for row in zero_rows)

scope_users = len({row["userKey"] for row in rows})
zero_users = len({row["userKey"] for row in zero_rows})

def encode(counter: Counter) -> str:
    if not counter:
        return ""
    return ",".join(f"{key}:{counter[key]}" for key in sorted(counter))

print(f"audit_user_cohort={user_cohort}")
print(f"top_n={top_n}")
print(f"sample_limit={sample_limit}")
print(f"scope_users={scope_users}")
print(f"top_window_rows={len(rows)}")
print(f"zero_ai_rows={len(zero_rows)}")
print(f"zero_ai_users={zero_users}")
print(f"zero_ai_reason_buckets={encode(bucket_counts)}")
print(f"zero_ai_source_distribution={encode(source_counts)}")
print(f"zero_ai_category_distribution={encode(category_counts)}")
print(f"zero_ai_account_origin_distribution={encode(origin_counts)}")

print()
print("[AI ZERO REASON DISTRIBUTION SAMPLE]")
for row in zero_rows[:sample_limit]:
    print(
        f"userKey={row['userKey']}\t"
        f"accountOrigin={row['accountOrigin']}\t"
        f"savedRank={row['savedRank']}\t"
        f"serviceId={row['serviceId']}\t"
        f"bucket={row['bucket']}\t"
        f"sourceType={row['sourceType']}\t"
        f"category={row['category']}\t"
        f"savedAiReason={row['aiReason']}\t"
        f"title={row['title']}"
    )
PY
