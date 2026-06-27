#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

smoke_require_command docker
smoke_require_command python3

TIMESTAMP_UTC="$(smoke_now_ts_utc)"
ARTIFACT_ROOT="${ROOT_DIR}/tmp/recommendation-review-gate-recent-window-audit"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${TIMESTAMP_UTC}}"
mkdir -p "${ARTIFACT_DIR}"

RECENT_WINDOW_HOURS="${RECENT_WINDOW_HOURS:-24}"
TARGET_SERVICE_ID="${TARGET_SERVICE_ID:-2622}"
SUMMARY_OUTPUT="${ARTIFACT_DIR}/review-gate-recent-window-summary.txt"
TOP1_OUTPUT="${ARTIFACT_DIR}/recent-top1-origin-mix.tsv"

smoke_print_step "recent latest-batch review snapshot"
docker exec -i youth-welfare-db psql -U postgres -d youth_welfare -AtF $'\t' <<SQL > "${TOP1_OUTPUT}"
WITH latest AS (
  SELECT user_key, max(recommended_at) AS recommended_at
  FROM user_recommendations
  GROUP BY user_key
),
recent_latest AS (
  SELECT l.user_key, l.recommended_at
  FROM latest l
  WHERE l.recommended_at >= now() - interval '${RECENT_WINDOW_HOURS} hours'
),
ranked AS (
  SELECT ur.user_key,
         coalesce(nullif(u.account_origin, ''), 'REAL_USER') AS account_origin,
         ur.recommended_at,
         ws.id AS service_id,
         ws.title,
         ur.final_score,
         row_number() OVER (
           PARTITION BY ur.user_key
           ORDER BY ur.final_score DESC, ur.id DESC
         ) AS rn
  FROM user_recommendations ur
  JOIN recent_latest rl
    ON rl.user_key = ur.user_key
   AND rl.recommended_at = ur.recommended_at
  JOIN users u
    ON u.user_key = ur.user_key
  JOIN welfare_services ws
    ON ws.id = ur.service_id
),
top1 AS (
  SELECT *
  FROM ranked
  WHERE rn = 1
),
leader AS (
  SELECT service_id
  FROM top1
  GROUP BY service_id
  ORDER BY COUNT(*) DESC, service_id
  LIMIT 1
),
top1_origin_mix AS (
  SELECT account_origin,
         count(*) AS users
  FROM top1
  WHERE service_id = (SELECT service_id FROM leader)
  GROUP BY account_origin
)
SELECT 'summary',
       (SELECT count(*)::text FROM top1),
       (SELECT count(*)::text FROM top1 WHERE account_origin = 'EXAMPLE_SMOKE'),
       (SELECT count(*)::text FROM top1 WHERE account_origin = 'REAL_USER'),
       (SELECT count(*)::text FROM top1 WHERE account_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED'),
       coalesce((SELECT service_id::text FROM leader), ''),
       coalesce((SELECT title FROM top1 WHERE service_id = (SELECT service_id FROM leader) LIMIT 1), ''),
       coalesce((SELECT count(*)::text FROM top1 WHERE service_id = (SELECT service_id FROM leader)), '0'),
       coalesce((SELECT count(*)::text FROM top1 WHERE service_id = (SELECT service_id FROM leader) AND account_origin = 'REAL_USER'), '0'),
       coalesce((SELECT count(*)::text FROM top1 WHERE service_id = ${TARGET_SERVICE_ID}), '0'),
       coalesce((SELECT count(*)::text FROM top1 WHERE service_id = ${TARGET_SERVICE_ID} AND account_origin = 'REAL_USER'), '0')
UNION ALL
SELECT 'leader_origin',
       account_origin,
       users::text,
       '',
       '',
       '',
       '',
       '',
       '',
       '',
       ''
FROM top1_origin_mix
ORDER BY 1, 2;
SQL

python3 - "${TOP1_OUTPUT}" "${SUMMARY_OUTPUT}" "${RECENT_WINDOW_HOURS}" "${TARGET_SERVICE_ID}" <<'PY'
import csv
import sys

top1_path, summary_path, recent_window_hours, target_service_id = sys.argv[1:]
summary = None
origin_mix = []
with open(top1_path, "r", encoding="utf-8") as fp:
    reader = csv.reader(fp, delimiter="\t")
    for row in reader:
        if not row:
            continue
        if row[0] == "summary":
            summary = {
                "recent_latest_batch_users": int(row[1] or 0),
                "recent_example_users": int(row[2] or 0),
                "recent_real_user_users": int(row[3] or 0),
                "recent_local_real_non_example_seed_users": int(row[4] or 0),
                "recent_top1_leader_service_id": row[5],
                "recent_top1_leader_title": row[6],
                "recent_top1_leader_users": int(row[7] or 0),
                "recent_top1_leader_real_user_users": int(row[8] or 0),
                "recent_target_top1_users": int(row[9] or 0),
                "recent_target_top1_real_user_users": int(row[10] or 0),
            }
        elif row[0] == "leader_origin":
            origin_mix.append((row[1], int(row[2] or 0)))

if summary is None:
    raise SystemExit("missing summary row")

leader_users = summary["recent_top1_leader_users"]
latest_users = summary["recent_latest_batch_users"]
share = (leader_users / latest_users * 100) if latest_users else 0.0
summary["recent_top1_leader_share_pct"] = f"{share:.2f}"

if (
    summary["recent_top1_leader_service_id"] != target_service_id
    and summary["recent_target_top1_users"] == 0
    and summary["recent_real_user_users"] > 0
):
    blocker_class = "RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE"
    next_step = "USE_RECENT_WINDOW_AS_SUPPLEMENTAL_REVIEW_CONTEXT"
elif summary["recent_top1_leader_service_id"] == target_service_id:
    blocker_class = "RECENT_WINDOW_STILL_TARGET_DOMINANT"
    next_step = "KEEP_TRACING_CURRENT_TARGET_LEADER_PATH"
else:
    blocker_class = "RECENT_WINDOW_INCONCLUSIVE"
    next_step = "COMPARE_RECENT_WINDOW_WITH_FULL_LATEST_BATCH"

with open(summary_path, "w", encoding="utf-8") as fp:
    fp.write(f"recent_window_hours={recent_window_hours}\n")
    fp.write(f"target_service_id={target_service_id}\n")
    for key in [
        "recent_latest_batch_users",
        "recent_example_users",
        "recent_real_user_users",
        "recent_local_real_non_example_seed_users",
        "recent_top1_leader_service_id",
        "recent_top1_leader_title",
        "recent_top1_leader_users",
        "recent_top1_leader_real_user_users",
        "recent_top1_leader_share_pct",
        "recent_target_top1_users",
        "recent_target_top1_real_user_users",
    ]:
        fp.write(f"{key}={summary[key]}\n")
    fp.write("recent_top1_leader_origin_mix=" + ",".join(f"{origin}:{count}" for origin, count in origin_mix) + "\n")
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

smoke_sanitize_artifacts "${ARTIFACT_DIR}"
smoke_update_links \
  "${ARTIFACT_DIR}" "${ARTIFACT_ROOT}/latest" \
  "${SUMMARY_OUTPUT}" "${ARTIFACT_ROOT}/latest-review-gate-recent-window-summary.txt" \
  "${TOP1_OUTPUT}" "${ARTIFACT_ROOT}/latest-recent-top1-origin-mix.tsv"

echo
cat "${SUMMARY_OUTPUT}"
echo "summary_output=${SUMMARY_OUTPUT}"
