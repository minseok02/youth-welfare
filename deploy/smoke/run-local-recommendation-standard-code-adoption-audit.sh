#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

metric() {
  local key="$1"
  local value="$2"
  printf 'METRIC %s=%s\n' "${key}" "${value}"
}

smoke_require_command bash
smoke_require_command python3

RESULT_ROW="$(smoke_db_query "
WITH latest_batch AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
latest_rows AS (
    SELECT ur.user_key, ur.final_score
    FROM user_recommendations ur
    JOIN latest_batch lb
      ON lb.user_key = ur.user_key
     AND lb.recommended_at = ur.recommended_at
),
user_codes AS (
    SELECT
        u.user_key,
        NULLIF(BTRIM(u.house_tenure_code), '') AS house_tenure_code,
        NULLIF(BTRIM(u.housing_type_code), '') AS housing_type_code,
        NULLIF(BTRIM(u.basic_living_recipient_type_code), '') AS basic_living_recipient_type_code,
        NULLIF(BTRIM(u.disability_grade_code), '') AS disability_grade_code
    FROM users u
),
scored_users AS (
    SELECT
        user_key,
        ((house_tenure_code IS NOT NULL)::int
         + (housing_type_code IS NOT NULL)::int
         + (basic_living_recipient_type_code IS NOT NULL)::int
         + (disability_grade_code IS NOT NULL)::int) AS filled_count
    FROM user_codes
)
SELECT
    COUNT(DISTINCT lr.user_key) AS latest_batch_user_count,
    COUNT(*) AS latest_batch_row_count,
    COUNT(DISTINCT lr.user_key) FILTER (WHERE su.filled_count > 0) AS latest_batch_users_with_any_standard_code,
    COUNT(DISTINCT lr.user_key) FILTER (WHERE su.filled_count = 4) AS latest_batch_users_with_all_standard_codes,
    COUNT(DISTINCT lr.user_key) FILTER (WHERE su.filled_count = 0) AS latest_batch_users_missing_all_standard_codes,
    COUNT(*) FILTER (WHERE su.filled_count > 0) AS latest_batch_rows_with_any_standard_code,
    COUNT(*) FILTER (WHERE su.filled_count = 4) AS latest_batch_rows_with_all_standard_codes,
    COUNT(*) FILTER (WHERE su.filled_count = 0) AS latest_batch_rows_missing_all_standard_codes,
    ROUND(
        100.0 * COUNT(DISTINCT lr.user_key) FILTER (WHERE su.filled_count > 0)
        / NULLIF(COUNT(DISTINCT lr.user_key), 0),
        2
    ) AS latest_batch_users_with_any_standard_code_share_pct,
    ROUND(
        100.0 * COUNT(DISTINCT lr.user_key) FILTER (WHERE su.filled_count = 0)
        / NULLIF(COUNT(DISTINCT lr.user_key), 0),
        2
    ) AS latest_batch_users_missing_all_standard_codes_share_pct,
    ROUND(COALESCE(AVG(CASE WHEN su.filled_count > 0 THEN lr.final_score END), 0), 5) AS latest_batch_avg_final_score_with_any_standard_code,
    ROUND(COALESCE(AVG(CASE WHEN su.filled_count = 0 THEN lr.final_score END), 0), 5) AS latest_batch_avg_final_score_missing_all_standard_codes
FROM latest_rows lr
JOIN scored_users su
  ON su.user_key = lr.user_key;
")"

python3 - "${RESULT_ROW}" <<'PY'
import sys

keys = [
    "latest_batch_user_count",
    "latest_batch_row_count",
    "latest_batch_users_with_any_standard_code",
    "latest_batch_users_with_all_standard_codes",
    "latest_batch_users_missing_all_standard_codes",
    "latest_batch_rows_with_any_standard_code",
    "latest_batch_rows_with_all_standard_codes",
    "latest_batch_rows_missing_all_standard_codes",
    "latest_batch_users_with_any_standard_code_share_pct",
    "latest_batch_users_missing_all_standard_codes_share_pct",
    "latest_batch_avg_final_score_with_any_standard_code",
    "latest_batch_avg_final_score_missing_all_standard_codes",
]
values = sys.argv[1].rstrip("\n").split("\t")
for key, value in zip(keys, values):
    print(f"METRIC {key}={value}")
print("OK recommendation_standard_code_adoption_audit")
PY
