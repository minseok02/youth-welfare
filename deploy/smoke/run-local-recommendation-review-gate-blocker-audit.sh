#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

smoke_require_command bash
smoke_require_command docker
smoke_require_command python3

TIMESTAMP_UTC="$(smoke_now_ts_utc)"
ARTIFACT_ROOT="${ROOT_DIR}/tmp/recommendation-review-gate-blocker-audit"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${TIMESTAMP_UTC}}"
mkdir -p "${ARTIFACT_DIR}"

MIXED_OUTPUT="${ARTIFACT_DIR}/mixed-concentration.out"
REAL_USER_OUTPUT="${ARTIFACT_DIR}/real-user-concentration.out"
LEADER_MIX_OUTPUT="${ARTIFACT_DIR}/leader-origin-mix.out"
LEADER_GAP_OUTPUT="${ARTIFACT_DIR}/leader-transition-gap.out"
SUMMARY_OUTPUT="${ARTIFACT_DIR}/review-gate-blocker-summary.txt"

extract_key_value() {
  local output_file="$1"
  local key="$2"
  python3 - "${output_file}" "${key}" <<'PY'
import sys

output_file, key = sys.argv[1], sys.argv[2]
prefix = f"{key}="

with open(output_file, "r", encoding="utf-8") as fp:
    for raw_line in fp:
        line = raw_line.strip()
        if line.startswith(prefix):
            print(line[len(prefix):])
            break
PY
}

extract_section_value() {
  local output_file="$1"
  local key="$2"
  python3 - "${output_file}" "${key}" <<'PY'
import sys

output_file, key = sys.argv[1], sys.argv[2]
marker = f"[{key}]"
capture = False

with open(output_file, "r", encoding="utf-8") as fp:
    for raw_line in fp:
        line = raw_line.strip()
        if capture:
            if line:
                print(line)
            break
        if line == marker:
            capture = True
PY
}

smoke_print_step "mixed latest-batch concentration"
USER_COHORT=all \
bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-concentration-audit.sh" | tee "${MIXED_OUTPUT}"

smoke_print_step "real-user-only concentration"
USER_COHORT=real_user \
bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-concentration-audit.sh" | tee "${REAL_USER_OUTPUT}"

smoke_print_step "mixed top1 leader origin mix"
docker exec -i youth-welfare-db psql -U postgres -d youth_welfare -At <<'SQL' | tee "${LEADER_MIX_OUTPUT}"
WITH latest AS (
  SELECT user_key, MAX(recommended_at) AS recommended_at
  FROM user_recommendations
  GROUP BY user_key
),
latest_rows AS (
  SELECT ur.user_key,
         ur.service_id,
         ur.final_score,
         COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS account_origin,
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
),
top1 AS (
  SELECT *
  FROM latest_rows
  WHERE rn = 1
),
leader AS (
  SELECT service_id
  FROM top1
  GROUP BY service_id
  ORDER BY COUNT(*) DESC, service_id
  LIMIT 1
)
SELECT 'mixed_top1_leader_origin=' || account_origin || '|' || COUNT(*)
FROM top1
WHERE service_id = (SELECT service_id FROM leader)
GROUP BY account_origin
ORDER BY COUNT(*) DESC, account_origin;
SQL

MIXED_LATEST_BATCH_USERS="$(extract_key_value "${MIXED_OUTPUT}" "latest_batch_users")"
MIXED_EXAMPLE_USERS="$(extract_key_value "${MIXED_OUTPUT}" "latest_batch_example_users")"
MIXED_LOCAL_SEED_USERS="$(extract_key_value "${MIXED_OUTPUT}" "latest_batch_local_real_non_example_seed_users")"
MIXED_REAL_USER_USERS="$(extract_key_value "${MIXED_OUTPUT}" "latest_batch_real_user_users")"
MIXED_TOP1_LEADER_SERVICE_ID="$(extract_key_value "${MIXED_OUTPUT}" "top1_leader_service_id")"
MIXED_TOP1_LEADER_TITLE="$(extract_key_value "${MIXED_OUTPUT}" "top1_leader_title")"
MIXED_TOP1_LEADER_USERS="$(extract_key_value "${MIXED_OUTPUT}" "top1_leader_users")"
MIXED_TOP1_LEADER_SHARE_PCT="$(extract_key_value "${MIXED_OUTPUT}" "top1_leader_share_pct")"
MIXED_CONCENTRATION_READINESS="$(extract_section_value "${MIXED_OUTPUT}" "concentration_readiness")"
MIXED_SIGNAL_QUALITY="$(extract_section_value "${MIXED_OUTPUT}" "signal_quality")"

REAL_USER_TOP1_LEADER_SERVICE_ID="$(extract_key_value "${REAL_USER_OUTPUT}" "top1_leader_service_id")"
REAL_USER_TOP1_LEADER_TITLE="$(extract_key_value "${REAL_USER_OUTPUT}" "top1_leader_title")"
REAL_USER_TOP1_LEADER_USERS="$(extract_key_value "${REAL_USER_OUTPUT}" "top1_leader_users")"
REAL_USER_TOP1_LEADER_SHARE_PCT="$(extract_key_value "${REAL_USER_OUTPUT}" "top1_leader_share_pct")"
REAL_USER_CONCENTRATION_READINESS="$(extract_section_value "${REAL_USER_OUTPUT}" "concentration_readiness")"
REAL_USER_LATEST_BATCH_USERS="$(extract_key_value "${REAL_USER_OUTPUT}" "latest_batch_users")"

docker exec -i youth-welfare-db psql -U postgres -d youth_welfare -At <<'SQL' > "${LEADER_GAP_OUTPUT}"
WITH latest AS (
  SELECT user_key, MAX(recommended_at) AS recommended_at
  FROM user_recommendations
  GROUP BY user_key
),
real_user_rows AS (
  SELECT ur.user_key,
         ur.service_id,
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
  WHERE COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'REAL_USER'
),
mixed_top1_leader AS (
  SELECT service_id
  FROM (
    SELECT ur.user_key,
           ur.service_id,
           ROW_NUMBER() OVER (
               PARTITION BY ur.user_key
               ORDER BY ur.final_score DESC, ur.id DESC
           ) AS rn
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
  ) ranked
  WHERE rn = 1
  GROUP BY service_id
  ORDER BY COUNT(*) DESC, service_id
  LIMIT 1
)
SELECT 'real_user_mixed_leader_top1_count=' || COUNT(*) FILTER (WHERE rn = 1)
FROM real_user_rows
WHERE service_id = (SELECT service_id FROM mixed_top1_leader)
UNION ALL
SELECT 'real_user_mixed_leader_top3_count=' || COUNT(*) FILTER (WHERE rn <= 3)
FROM real_user_rows
WHERE service_id = (SELECT service_id FROM mixed_top1_leader)
UNION ALL
SELECT 'real_user_mixed_leader_top5_count=' || COUNT(*) FILTER (WHERE rn <= 5)
FROM real_user_rows
WHERE service_id = (SELECT service_id FROM mixed_top1_leader)
UNION ALL
SELECT 'real_user_mixed_leader_top10_count=' || COUNT(*) FILTER (WHERE rn <= 10)
FROM real_user_rows
WHERE service_id = (SELECT service_id FROM mixed_top1_leader)
UNION ALL
SELECT 'real_user_mixed_leader_any_rank_count=' || COUNT(*)
FROM real_user_rows
WHERE service_id = (SELECT service_id FROM mixed_top1_leader);
SQL

MIXED_TOP1_LEADER_REAL_USER_USERS="$(
  python3 - "${LEADER_MIX_OUTPUT}" <<'PY'
import sys

value = "0"
with open(sys.argv[1], "r", encoding="utf-8") as fp:
    for raw_line in fp:
        line = raw_line.strip()
        if line.startswith("mixed_top1_leader_origin=REAL_USER|"):
            value = line.rsplit("|", 1)[-1]
            break
print(value)
PY
)"

REAL_USER_MIXED_LEADER_TOP1_COUNT="$(extract_key_value "${LEADER_GAP_OUTPUT}" "real_user_mixed_leader_top1_count")"
REAL_USER_MIXED_LEADER_TOP3_COUNT="$(extract_key_value "${LEADER_GAP_OUTPUT}" "real_user_mixed_leader_top3_count")"
REAL_USER_MIXED_LEADER_TOP5_COUNT="$(extract_key_value "${LEADER_GAP_OUTPUT}" "real_user_mixed_leader_top5_count")"
REAL_USER_MIXED_LEADER_TOP10_COUNT="$(extract_key_value "${LEADER_GAP_OUTPUT}" "real_user_mixed_leader_top10_count")"
REAL_USER_MIXED_LEADER_ANY_RANK_COUNT="$(extract_key_value "${LEADER_GAP_OUTPUT}" "real_user_mixed_leader_any_rank_count")"

if [[ "${MIXED_TOP1_LEADER_REAL_USER_USERS}" == "0" && "${REAL_USER_MIXED_LEADER_TOP10_COUNT}" == "0" ]]; then
  BLOCKER_CLASS="MIXED_BATCH_NON_REAL_DOMINANCE_WITH_NO_REAL_USER_PATH"
  NEXT_ACTION="INVESTIGATE_SAME_PROFILE_EXAMPLE_VS_REAL_USER_DIFFERENTIAL"
elif [[ "${MIXED_TOP1_LEADER_REAL_USER_USERS}" == "0" ]]; then
  BLOCKER_CLASS="MIXED_BATCH_NON_REAL_DOMINANCE"
  NEXT_ACTION="KEEP_REAL_USER_TRAFFIC_AND_OBSERVE_LEADER_TRANSITION"
else
  BLOCKER_CLASS="REAL_USER_LEADER_PRESENT"
  NEXT_ACTION="RECHECK_REVIEW_GATE_WITH_LIVE_READINESS"
fi

cat > "${SUMMARY_OUTPUT}" <<EOF
generated_at_utc=$(smoke_now_iso_utc)
generated_at_kst=$(smoke_now_iso_kst)
mixed_latest_batch_users=${MIXED_LATEST_BATCH_USERS}
mixed_example_users=${MIXED_EXAMPLE_USERS}
mixed_local_real_non_example_seed_users=${MIXED_LOCAL_SEED_USERS}
mixed_real_user_users=${MIXED_REAL_USER_USERS}
mixed_top1_leader_service_id=${MIXED_TOP1_LEADER_SERVICE_ID}
mixed_top1_leader_title=${MIXED_TOP1_LEADER_TITLE}
mixed_top1_leader_users=${MIXED_TOP1_LEADER_USERS}
mixed_top1_leader_share_pct=${MIXED_TOP1_LEADER_SHARE_PCT}
mixed_top1_leader_real_user_users=${MIXED_TOP1_LEADER_REAL_USER_USERS}
real_user_mixed_leader_top1_count=${REAL_USER_MIXED_LEADER_TOP1_COUNT}
real_user_mixed_leader_top3_count=${REAL_USER_MIXED_LEADER_TOP3_COUNT}
real_user_mixed_leader_top5_count=${REAL_USER_MIXED_LEADER_TOP5_COUNT}
real_user_mixed_leader_top10_count=${REAL_USER_MIXED_LEADER_TOP10_COUNT}
real_user_mixed_leader_any_rank_count=${REAL_USER_MIXED_LEADER_ANY_RANK_COUNT}
mixed_concentration_readiness=${MIXED_CONCENTRATION_READINESS}
mixed_signal_quality=${MIXED_SIGNAL_QUALITY}
real_user_latest_batch_users=${REAL_USER_LATEST_BATCH_USERS}
real_user_top1_leader_service_id=${REAL_USER_TOP1_LEADER_SERVICE_ID}
real_user_top1_leader_title=${REAL_USER_TOP1_LEADER_TITLE}
real_user_top1_leader_users=${REAL_USER_TOP1_LEADER_USERS}
real_user_top1_leader_share_pct=${REAL_USER_TOP1_LEADER_SHARE_PCT}
real_user_concentration_readiness=${REAL_USER_CONCENTRATION_READINESS}
blocker_class=${BLOCKER_CLASS}
operator_next_step=${NEXT_ACTION}
artifact_dir=${ARTIFACT_DIR}
EOF

smoke_update_links \
  "${ARTIFACT_DIR}" "${ARTIFACT_ROOT}/latest" \
  "${SUMMARY_OUTPUT}" "${ARTIFACT_ROOT}/latest-review-gate-blocker-summary.txt"

echo
cat "${SUMMARY_OUTPUT}"
echo "summary_output=${SUMMARY_OUTPUT}"
