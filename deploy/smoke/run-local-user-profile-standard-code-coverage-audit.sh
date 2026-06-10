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
WITH joined AS (
    SELECT
        u.user_key,
        COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS account_origin,
        up.user_key IS NOT NULL AS has_profile_row,
        NULLIF(BTRIM(u.house_tenure_code), '') AS u_house_tenure_code,
        NULLIF(BTRIM(u.housing_type_code), '') AS u_housing_type_code,
        NULLIF(BTRIM(u.basic_living_recipient_type_code), '') AS u_basic_living_recipient_type_code,
        NULLIF(BTRIM(u.disability_grade_code), '') AS u_disability_grade_code,
        NULLIF(BTRIM(up.house_tenure_code), '') AS p_house_tenure_code,
        NULLIF(BTRIM(up.housing_type_code), '') AS p_housing_type_code,
        NULLIF(BTRIM(up.basic_living_recipient_type_code), '') AS p_basic_living_recipient_type_code,
        NULLIF(BTRIM(up.disability_grade_code), '') AS p_disability_grade_code
    FROM users u
    LEFT JOIN user_profiles up
      ON up.user_key = u.user_key
),
scored AS (
    SELECT
        *,
        ((u_house_tenure_code IS NOT NULL)::int
         + (u_housing_type_code IS NOT NULL)::int
         + (u_basic_living_recipient_type_code IS NOT NULL)::int
         + (u_disability_grade_code IS NOT NULL)::int) AS u_filled_count,
        ((p_house_tenure_code IS NOT NULL)::int
         + (p_housing_type_code IS NOT NULL)::int
         + (p_basic_living_recipient_type_code IS NOT NULL)::int
         + (p_disability_grade_code IS NOT NULL)::int) AS p_filled_count,
        (
            (u_house_tenure_code IS NULL AND p_house_tenure_code IS NOT NULL)
            OR (u_housing_type_code IS NULL AND p_housing_type_code IS NOT NULL)
            OR (u_basic_living_recipient_type_code IS NULL AND p_basic_living_recipient_type_code IS NOT NULL)
            OR (u_disability_grade_code IS NULL AND p_disability_grade_code IS NOT NULL)
        ) AS profile_only_gap,
        (
            (u_house_tenure_code IS NOT NULL AND p_house_tenure_code IS NULL)
            OR (u_housing_type_code IS NOT NULL AND p_housing_type_code IS NULL)
            OR (u_basic_living_recipient_type_code IS NOT NULL AND p_basic_living_recipient_type_code IS NULL)
            OR (u_disability_grade_code IS NOT NULL AND p_disability_grade_code IS NULL)
        ) AS user_only_gap,
        (
            (u_house_tenure_code IS NOT NULL AND p_house_tenure_code IS NOT NULL AND u_house_tenure_code <> p_house_tenure_code)
            OR (u_housing_type_code IS NOT NULL AND p_housing_type_code IS NOT NULL AND u_housing_type_code <> p_housing_type_code)
            OR (u_basic_living_recipient_type_code IS NOT NULL AND p_basic_living_recipient_type_code IS NOT NULL AND u_basic_living_recipient_type_code <> p_basic_living_recipient_type_code)
            OR (u_disability_grade_code IS NOT NULL AND p_disability_grade_code IS NOT NULL AND u_disability_grade_code <> p_disability_grade_code)
        ) AS conflicting_value_gap
    FROM joined
)
SELECT
    COUNT(*) AS total_users,
    COUNT(*) FILTER (WHERE has_profile_row) AS users_with_profile_row,
    COUNT(*) FILTER (WHERE NOT has_profile_row) AS users_without_profile_row,
    COUNT(*) FILTER (WHERE u_filled_count > 0) AS users_with_any_standard_code,
    COUNT(*) FILTER (WHERE u_filled_count = 4) AS users_with_all_standard_codes,
    COUNT(*) FILTER (WHERE u_filled_count = 0) AS users_missing_all_standard_codes,
    COUNT(*) FILTER (WHERE u_house_tenure_code IS NOT NULL) AS users_house_tenure_code_filled,
    COUNT(*) FILTER (WHERE u_housing_type_code IS NOT NULL) AS users_housing_type_code_filled,
    COUNT(*) FILTER (WHERE u_basic_living_recipient_type_code IS NOT NULL) AS users_basic_living_recipient_type_code_filled,
    COUNT(*) FILTER (WHERE u_disability_grade_code IS NOT NULL) AS users_disability_grade_code_filled,
    COUNT(*) FILTER (WHERE has_profile_row AND p_filled_count > 0) AS profiles_with_any_standard_code,
    COUNT(*) FILTER (WHERE has_profile_row AND p_filled_count = 4) AS profiles_with_all_standard_codes,
    COUNT(*) FILTER (WHERE has_profile_row AND p_filled_count = 0) AS profiles_missing_all_standard_codes,
    COUNT(*) FILTER (WHERE profile_only_gap) AS profile_only_gap_rows,
    COUNT(*) FILTER (WHERE user_only_gap) AS user_only_gap_rows,
    COUNT(*) FILTER (WHERE profile_only_gap OR user_only_gap) AS safe_reconcile_candidate_rows,
    COUNT(*) FILTER (WHERE conflicting_value_gap) AS conflicting_value_gap_rows,
    COUNT(*) FILTER (WHERE account_origin <> 'EXAMPLE_SMOKE') AS non_example_total_users,
    COUNT(*) FILTER (WHERE account_origin <> 'EXAMPLE_SMOKE' AND u_filled_count > 0) AS non_example_users_with_any_standard_code,
    COUNT(*) FILTER (WHERE account_origin <> 'EXAMPLE_SMOKE' AND u_filled_count = 0) AS non_example_users_missing_all_standard_codes,
    COUNT(*) FILTER (WHERE account_origin <> 'EXAMPLE_SMOKE' AND (profile_only_gap OR user_only_gap)) AS non_example_safe_reconcile_candidate_rows,
    COUNT(*) FILTER (WHERE account_origin <> 'EXAMPLE_SMOKE' AND conflicting_value_gap) AS non_example_conflicting_value_gap_rows,
    COUNT(*) FILTER (WHERE account_origin = 'REAL_USER') AS real_user_total_users,
    COUNT(*) FILTER (WHERE account_origin = 'REAL_USER' AND u_filled_count = 0) AS real_user_users_missing_all_standard_codes,
    COUNT(*) FILTER (WHERE account_origin = 'EXAMPLE_SMOKE') AS example_smoke_total_users,
    COUNT(*) FILTER (WHERE account_origin = 'EXAMPLE_SMOKE' AND u_filled_count = 0) AS example_smoke_users_missing_all_standard_codes,
    COUNT(*) FILTER (WHERE account_origin = 'BOUNDED_LOCAL') AS bounded_local_total_users,
    COUNT(*) FILTER (WHERE account_origin = 'BOUNDED_LOCAL' AND u_filled_count = 0) AS bounded_local_users_missing_all_standard_codes
FROM scored;
")"

python3 - "${RESULT_ROW}" <<'PY'
import sys

keys = [
    "total_users",
    "users_with_profile_row",
    "users_without_profile_row",
    "users_with_any_standard_code",
    "users_with_all_standard_codes",
    "users_missing_all_standard_codes",
    "users_house_tenure_code_filled",
    "users_housing_type_code_filled",
    "users_basic_living_recipient_type_code_filled",
    "users_disability_grade_code_filled",
    "profiles_with_any_standard_code",
    "profiles_with_all_standard_codes",
    "profiles_missing_all_standard_codes",
    "profile_only_gap_rows",
    "user_only_gap_rows",
    "safe_reconcile_candidate_rows",
    "conflicting_value_gap_rows",
    "non_example_total_users",
    "non_example_users_with_any_standard_code",
    "non_example_users_missing_all_standard_codes",
    "non_example_safe_reconcile_candidate_rows",
    "non_example_conflicting_value_gap_rows",
    "real_user_total_users",
    "real_user_users_missing_all_standard_codes",
    "example_smoke_total_users",
    "example_smoke_users_missing_all_standard_codes",
    "bounded_local_total_users",
    "bounded_local_users_missing_all_standard_codes",
]
values = sys.argv[1].rstrip("\n").split("\t")
for key, value in zip(keys, values):
    print(f"METRIC {key}={value}")
print("OK user_profile_standard_code_coverage_audit")
PY
