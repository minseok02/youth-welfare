#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

smoke_require_command docker
smoke_require_command python3

TIMESTAMP_UTC="$(smoke_now_ts_utc)"
ARTIFACT_ROOT="${ROOT_DIR}/tmp/recommendation-review-gate-staleness-audit"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${TIMESTAMP_UTC}}"
mkdir -p "${ARTIFACT_DIR}"

TARGET_SERVICE_ID="${TARGET_SERVICE_ID:-2622}"
RAW_SUMMARY_OUTPUT="${ARTIFACT_DIR}/staleness-raw.tsv"
STALE_USERS_OUTPUT="${ARTIFACT_DIR}/stale-top1-users.tsv"
SUMMARY_OUTPUT="${ARTIFACT_DIR}/review-gate-staleness-summary.txt"

smoke_print_step "mixed latest batch staleness snapshot"
docker exec -i youth-welfare-db psql -U postgres -d youth_welfare -AtF $'\t' <<SQL > "${RAW_SUMMARY_OUTPUT}"
WITH latest AS (
  SELECT user_key, max(recommended_at) AS recommended_at
  FROM user_recommendations
  GROUP BY user_key
),
ranked AS (
  SELECT ur.user_key,
         coalesce(nullif(u.account_origin, ''), 'REAL_USER') AS account_origin,
         u.email,
         ur.recommended_at,
         ws.id AS service_id,
         ws.title,
         ur.final_score,
         row_number() OVER (
           PARTITION BY ur.user_key
           ORDER BY ur.final_score DESC, ur.id DESC
         ) AS rn
  FROM user_recommendations ur
  JOIN latest l
    ON l.user_key = ur.user_key
   AND l.recommended_at = ur.recommended_at
  JOIN users u
    ON u.user_key = ur.user_key
  JOIN welfare_services ws
    ON ws.id = ur.service_id
),
origin_summary AS (
  SELECT account_origin,
         count(*) FILTER (WHERE rn = 1) AS latest_users,
         count(*) FILTER (WHERE rn = 1 AND recommended_at >= now() - interval '1 hour') AS latest_users_last_1h,
         count(*) FILTER (WHERE rn = 1 AND recommended_at >= now() - interval '24 hours') AS latest_users_last_24h,
         min(recommended_at) FILTER (WHERE rn = 1) AS oldest_latest,
         max(recommended_at) FILTER (WHERE rn = 1) AS newest_latest
  FROM ranked
  GROUP BY account_origin
),
target_summary AS (
  SELECT account_origin,
         count(*) FILTER (WHERE rn = 1 AND service_id = ${TARGET_SERVICE_ID}) AS target_top1_users,
         count(*) FILTER (WHERE rn = 1 AND service_id = ${TARGET_SERVICE_ID} AND recommended_at >= now() - interval '1 hour') AS target_top1_last_1h,
         count(*) FILTER (WHERE rn = 1 AND service_id = ${TARGET_SERVICE_ID} AND recommended_at >= now() - interval '24 hours') AS target_top1_last_24h,
         min(recommended_at) FILTER (WHERE rn = 1 AND service_id = ${TARGET_SERVICE_ID}) AS target_oldest_top1,
         max(recommended_at) FILTER (WHERE rn = 1 AND service_id = ${TARGET_SERVICE_ID}) AS target_newest_top1
  FROM ranked
  GROUP BY account_origin
)
SELECT os.account_origin,
       os.latest_users,
       os.latest_users_last_1h,
       os.latest_users_last_24h,
       coalesce(to_char(os.oldest_latest, 'YYYY-MM-DD HH24:MI:SS'), ''),
       coalesce(to_char(os.newest_latest, 'YYYY-MM-DD HH24:MI:SS'), ''),
       coalesce(ts.target_top1_users, 0),
       coalesce(ts.target_top1_last_1h, 0),
       coalesce(ts.target_top1_last_24h, 0),
       coalesce(to_char(ts.target_oldest_top1, 'YYYY-MM-DD HH24:MI:SS'), ''),
       coalesce(to_char(ts.target_newest_top1, 'YYYY-MM-DD HH24:MI:SS'), '')
FROM origin_summary os
LEFT JOIN target_summary ts
  ON ts.account_origin = os.account_origin
ORDER BY os.account_origin;
SQL

smoke_print_step "stale target-top1 users"
docker exec -i youth-welfare-db psql -U postgres -d youth_welfare -AtF $'\t' <<SQL > "${STALE_USERS_OUTPUT}"
WITH latest AS (
  SELECT user_key, max(recommended_at) AS recommended_at
  FROM user_recommendations
  GROUP BY user_key
),
ranked AS (
  SELECT ur.user_key,
         coalesce(nullif(u.account_origin, ''), 'REAL_USER') AS account_origin,
         u.email,
         ur.recommended_at,
         ws.id AS service_id,
         ws.title,
         ur.final_score,
         row_number() OVER (
           PARTITION BY ur.user_key
           ORDER BY ur.final_score DESC, ur.id DESC
         ) AS rn
  FROM user_recommendations ur
  JOIN latest l
    ON l.user_key = ur.user_key
   AND l.recommended_at = ur.recommended_at
  JOIN users u
    ON u.user_key = ur.user_key
  JOIN welfare_services ws
    ON ws.id = ur.service_id
)
SELECT account_origin,
       user_key,
       email,
       to_char(recommended_at, 'YYYY-MM-DD HH24:MI:SS'),
       final_score
FROM ranked
WHERE rn = 1
  AND service_id = ${TARGET_SERVICE_ID}
ORDER BY recommended_at ASC, user_key
LIMIT 30;
SQL

python3 - "${RAW_SUMMARY_OUTPUT}" "${SUMMARY_OUTPUT}" "${TARGET_SERVICE_ID}" <<'PY'
import csv
import sys

raw_path, summary_path, target_service_id = sys.argv[1:]
rows = {}
with open(raw_path, "r", encoding="utf-8") as fp:
    reader = csv.reader(fp, delimiter="\t")
    for row in reader:
        if not row:
            continue
        rows[row[0]] = {
            "latest_users": int(row[1]),
            "latest_users_last_1h": int(row[2]),
            "latest_users_last_24h": int(row[3]),
            "oldest_latest": row[4],
            "newest_latest": row[5],
            "target_top1_users": int(row[6]),
            "target_top1_last_1h": int(row[7]),
            "target_top1_last_24h": int(row[8]),
            "target_oldest_top1": row[9],
            "target_newest_top1": row[10],
        }

example = rows.get("EXAMPLE_SMOKE", {})
real_user = rows.get("REAL_USER", {})

if (
    example.get("target_top1_users", 0) > 0
    and example.get("target_top1_last_24h", 0) == 0
    and real_user.get("target_top1_users", 0) == 0
):
    blocker_class = "HISTORICAL_EXAMPLE_LATEST_BATCH_DOMINANCE"
    next_step = "REFRESH_OR_EXPIRE_STALE_EXAMPLE_BATCHES_BEFORE_REVIEWING_LEADER"
else:
    blocker_class = "NON_HISTORICAL_TARGET_LEADER_OR_SHARED_RECENCY"
    next_step = "KEEP_TRACING_CURRENT_FLOW_DIFFERENTIAL"

with open(summary_path, "w", encoding="utf-8") as fp:
    fp.write(f"target_service_id={target_service_id}\n")
    for origin in ["EXAMPLE_SMOKE", "REAL_USER", "LOCAL_REAL_NON_EXAMPLE_SEED", "BOUNDED_LOCAL"]:
        data = rows.get(origin)
        if not data:
            continue
        key = origin.lower()
        fp.write(f"{key}_latest_users={data['latest_users']}\n")
        fp.write(f"{key}_latest_users_last_1h={data['latest_users_last_1h']}\n")
        fp.write(f"{key}_latest_users_last_24h={data['latest_users_last_24h']}\n")
        fp.write(f"{key}_oldest_latest={data['oldest_latest']}\n")
        fp.write(f"{key}_newest_latest={data['newest_latest']}\n")
        fp.write(f"{key}_target_top1_users={data['target_top1_users']}\n")
        fp.write(f"{key}_target_top1_last_1h={data['target_top1_last_1h']}\n")
        fp.write(f"{key}_target_top1_last_24h={data['target_top1_last_24h']}\n")
        fp.write(f"{key}_target_oldest_top1={data['target_oldest_top1']}\n")
        fp.write(f"{key}_target_newest_top1={data['target_newest_top1']}\n")
    fp.write(f"blocker_class={blocker_class}\n")
    fp.write(f"operator_next_step={next_step}\n")
PY

{
  echo "generated_at_utc=$(smoke_now_iso_utc)"
  echo "generated_at_kst=$(smoke_now_iso_kst)"
  cat "${SUMMARY_OUTPUT}"
  echo "artifact_dir=${ARTIFACT_DIR}"
} > "${SUMMARY_OUTPUT}.tmp"
mv "${SUMMARY_OUTPUT}.tmp" "${SUMMARY_OUTPUT}"

smoke_update_links \
  "${ARTIFACT_DIR}" "${ARTIFACT_ROOT}/latest" \
  "${SUMMARY_OUTPUT}" "${ARTIFACT_ROOT}/latest-review-gate-staleness-summary.txt" \
  "${STALE_USERS_OUTPUT}" "${ARTIFACT_ROOT}/latest-stale-top1-users.tsv"

echo
cat "${SUMMARY_OUTPUT}"
echo "summary_output=${SUMMARY_OUTPUT}"
