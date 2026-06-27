#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

smoke_require_command docker
smoke_require_command python3

TIMESTAMP_UTC="$(smoke_now_ts_utc)"
ARTIFACT_ROOT="${ROOT_DIR}/tmp/recommendation-same-profile-origin-differential-audit"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${TIMESTAMP_UTC}}"
mkdir -p "${ARTIFACT_DIR}"

PROFILE_SIDO="${PROFILE_SIDO:-인천광역시}"
PROFILE_SGG="${PROFILE_SGG:-중구}"
PROFILE_INCOME_LEVEL="${PROFILE_INCOME_LEVEL:-5}"
PROFILE_EMPLOYMENT_STATUS="${PROFILE_EMPLOYMENT_STATUS:-미취업}"
PROFILE_HOUSEHOLD_TYPE="${PROFILE_HOUSEHOLD_TYPE:-1인 가구}"
TARGET_SERVICE_ID="${TARGET_SERVICE_ID:-2622}"

RAW_ROWS_OUTPUT="${ARTIFACT_DIR}/exact-profile-ranked-rows.tsv"
SUMMARY_OUTPUT="${ARTIFACT_DIR}/same-profile-origin-differential-summary.txt"
TOP1_OUTPUT="${ARTIFACT_DIR}/top1-distribution.tsv"
TARGET_RANK_OUTPUT="${ARTIFACT_DIR}/target-rank-distribution.tsv"

smoke_print_step "same-profile latest batch rows"
docker exec -i youth-welfare-db psql -U postgres -d youth_welfare -AtF $'\t' <<SQL > "${RAW_ROWS_OUTPUT}"
WITH latest AS (
  SELECT user_key, MAX(recommended_at) AS recommended_at
  FROM user_recommendations
  GROUP BY user_key
),
ranked AS (
  SELECT ur.user_key,
         COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS account_origin,
         u.email,
         u.birth_date,
         up.sido,
         up.sgg,
         up.income_level,
         up.employment_status,
         up.household_type,
         ur.service_id,
         ws.title,
         ur.final_score,
         ROW_NUMBER() OVER (
             PARTITION BY ur.user_key
             ORDER BY ur.final_score DESC, ur.id DESC
         ) AS rn
  FROM user_recommendations ur
  JOIN latest l
    ON l.user_key = ur.user_key
   AND l.recommended_at = ur.recommended_at
  JOIN users u
    ON u.user_key = ur.user_key
  JOIN user_profiles up
    ON up.user_key = u.user_key
  JOIN welfare_services ws
    ON ws.id = ur.service_id
  WHERE up.sido = '${PROFILE_SIDO}'
    AND up.sgg = '${PROFILE_SGG}'
    AND up.income_level = ${PROFILE_INCOME_LEVEL}
    AND up.employment_status = '${PROFILE_EMPLOYMENT_STATUS}'
    AND up.household_type = '${PROFILE_HOUSEHOLD_TYPE}'
)
SELECT user_key,
       account_origin,
       email,
       COALESCE(birth_date::text, ''),
       sido,
       sgg,
       income_level,
       employment_status,
       household_type,
       service_id,
       title,
       final_score,
       rn
FROM ranked
WHERE rn <= 10
ORDER BY account_origin, user_key, rn;
SQL

python3 - "${RAW_ROWS_OUTPUT}" "${SUMMARY_OUTPUT}" "${TOP1_OUTPUT}" "${TARGET_RANK_OUTPUT}" "${TARGET_SERVICE_ID}" \
  "${PROFILE_SIDO}" "${PROFILE_SGG}" "${PROFILE_INCOME_LEVEL}" "${PROFILE_EMPLOYMENT_STATUS}" "${PROFILE_HOUSEHOLD_TYPE}" <<'PY'
import csv
import sys
from collections import Counter, defaultdict

(
    raw_rows_path,
    summary_path,
    top1_path,
    target_rank_path,
    target_service_id_raw,
    profile_sido,
    profile_sgg,
    profile_income_level,
    profile_employment_status,
    profile_household_type,
) = sys.argv[1:]

target_service_id = int(target_service_id_raw)
rows = []
with open(raw_rows_path, "r", encoding="utf-8") as fp:
    reader = csv.reader(fp, delimiter="\t")
    for row in reader:
        if not row:
            continue
        rows.append(
            {
                "user_key": row[0],
                "account_origin": row[1],
                "email": row[2],
                "birth_date": row[3],
                "sido": row[4],
                "sgg": row[5],
                "income_level": row[6],
                "employment_status": row[7],
                "household_type": row[8],
                "service_id": int(row[9]),
                "title": row[10],
                "final_score": float(row[11]),
                "rn": int(row[12]),
            }
        )

user_origin = {}
target_title = ""
origin_ranked = defaultdict(list)
origin_top1 = defaultdict(Counter)
origin_target_ranks = defaultdict(Counter)

for row in rows:
    origin = row["account_origin"]
    user_key = row["user_key"]
    user_origin[user_key] = origin
    origin_ranked[origin].append(row)
    if row["rn"] == 1:
        origin_top1[origin][(row["service_id"], row["title"])] += 1
    if row["service_id"] == target_service_id:
        origin_target_ranks[origin][row["rn"]] += 1
        target_title = row["title"]

origin_user_counts = Counter(user_origin.values())
all_origins = ["EXAMPLE_SMOKE", "REAL_USER", "LOCAL_REAL_NON_EXAMPLE_SEED", "BOUNDED_LOCAL"]
for origin in list(origin_ranked.keys()):
    if origin not in all_origins:
        all_origins.append(origin)

def top1_metrics(origin: str):
    total_users = origin_user_counts.get(origin, 0)
    if total_users == 0 or not origin_top1[origin]:
        return "", "", "0", "0.00"
    (service_id, title), count = origin_top1[origin].most_common(1)[0]
    share = (count / total_users) * 100 if total_users else 0.0
    return str(service_id), title, str(count), f"{share:.2f}"

def target_metrics(origin: str):
    rank_counts = origin_target_ranks[origin]
    top1 = rank_counts.get(1, 0)
    top3 = sum(v for r, v in rank_counts.items() if r <= 3)
    top5 = sum(v for r, v in rank_counts.items() if r <= 5)
    top10 = sum(v for r, v in rank_counts.items() if r <= 10)
    any_rank = sum(rank_counts.values())
    distribution = ",".join(f"{rank}:{rank_counts[rank]}" for rank in sorted(rank_counts)) if rank_counts else ""
    return str(top1), str(top3), str(top5), str(top10), str(any_rank), distribution

example_any = sum(origin_target_ranks["EXAMPLE_SMOKE"].values())
real_user_any = sum(origin_target_ranks["REAL_USER"].values())
if example_any > 0 and real_user_any == 0:
    blocker_class = "SAME_PROFILE_EXAMPLE_REAL_USER_DIFFERENTIAL"
    next_step = "TRACE_ORIGIN_OR_FLOW_DIFFERENTIAL_IN_RECOMMENDATION_PATH"
elif example_any > 0 and real_user_any > 0:
    blocker_class = "SAME_PROFILE_SHARED_PATH"
    next_step = "COMPARE_SCORE_AND_RANK_DELTA_BY_ORIGIN"
else:
    blocker_class = "TARGET_SERVICE_ABSENT_FOR_ALL_ORIGINS"
    next_step = "RECHECK_TARGET_SERVICE_AND_PROFILE_SELECTION"

with open(summary_path, "w", encoding="utf-8") as fp:
    fp.write(f"profile_sido={profile_sido}\n")
    fp.write(f"profile_sgg={profile_sgg}\n")
    fp.write(f"profile_income_level={profile_income_level}\n")
    fp.write(f"profile_employment_status={profile_employment_status}\n")
    fp.write(f"profile_household_type={profile_household_type}\n")
    fp.write(f"target_service_id={target_service_id}\n")
    fp.write(f"target_service_title={target_title}\n")
    fp.write(f"exact_profile_total_users={sum(origin_user_counts.values())}\n")
    fp.write(f"exact_profile_example_users={origin_user_counts.get('EXAMPLE_SMOKE', 0)}\n")
    fp.write(f"exact_profile_real_user_users={origin_user_counts.get('REAL_USER', 0)}\n")
    fp.write(f"exact_profile_local_real_non_example_seed_users={origin_user_counts.get('LOCAL_REAL_NON_EXAMPLE_SEED', 0)}\n")
    fp.write(f"exact_profile_bounded_local_users={origin_user_counts.get('BOUNDED_LOCAL', 0)}\n")
    for origin in all_origins:
        key = origin.lower()
        top1_id, top1_title, top1_users, top1_share = top1_metrics(origin)
        fp.write(f"{key}_top1_leader_service_id={top1_id}\n")
        fp.write(f"{key}_top1_leader_title={top1_title}\n")
        fp.write(f"{key}_top1_leader_users={top1_users}\n")
        fp.write(f"{key}_top1_leader_share_pct={top1_share}\n")
        target_top1, target_top3, target_top5, target_top10, target_any, target_distribution = target_metrics(origin)
        fp.write(f"{key}_target_top1_count={target_top1}\n")
        fp.write(f"{key}_target_top3_count={target_top3}\n")
        fp.write(f"{key}_target_top5_count={target_top5}\n")
        fp.write(f"{key}_target_top10_count={target_top10}\n")
        fp.write(f"{key}_target_any_rank_count={target_any}\n")
        fp.write(f"{key}_target_rank_distribution={target_distribution}\n")
    fp.write(f"blocker_class={blocker_class}\n")
    fp.write(f"operator_next_step={next_step}\n")

with open(top1_path, "w", encoding="utf-8", newline="") as fp:
    writer = csv.writer(fp, delimiter="\t")
    writer.writerow(["account_origin", "service_id", "title", "users"])
    for origin in all_origins:
        for (service_id, title), count in origin_top1[origin].most_common():
            writer.writerow([origin, service_id, title, count])

with open(target_rank_path, "w", encoding="utf-8", newline="") as fp:
    writer = csv.writer(fp, delimiter="\t")
    writer.writerow(["account_origin", "rank", "users"])
    for origin in all_origins:
        for rank, count in sorted(origin_target_ranks[origin].items()):
            writer.writerow([origin, rank, count])
PY

{
  echo "generated_at_utc=$(smoke_now_iso_utc)"
  echo "generated_at_kst=$(smoke_now_iso_kst)"
  cat "${SUMMARY_OUTPUT}"
  echo "artifact_dir=${ARTIFACT_DIR}"
} > "${SUMMARY_OUTPUT}.tmp"
mv "${SUMMARY_OUTPUT}.tmp" "${SUMMARY_OUTPUT}"

smoke_sanitize_artifacts "${ARTIFACT_DIR}"
smoke_update_links \
  "${ARTIFACT_DIR}" "${ARTIFACT_ROOT}/latest" \
  "${SUMMARY_OUTPUT}" "${ARTIFACT_ROOT}/latest-same-profile-origin-differential-summary.txt" \
  "${TOP1_OUTPUT}" "${ARTIFACT_ROOT}/latest-top1-distribution.tsv" \
  "${TARGET_RANK_OUTPUT}" "${ARTIFACT_ROOT}/latest-target-rank-distribution.tsv"

echo
cat "${SUMMARY_OUTPUT}"
echo "summary_output=${SUMMARY_OUTPUT}"
