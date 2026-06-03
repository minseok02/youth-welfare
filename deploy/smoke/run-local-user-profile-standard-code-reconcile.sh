#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DB_CONTAINER_NAME="${DB_CONTAINER_NAME:-youth-welfare-db}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_DB="${POSTGRES_DB:-youth_welfare}"

run_sql() {
  local sql="$1"
  docker exec -i "${DB_CONTAINER_NAME}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -At <<SQL
${sql}
SQL
}

echo "[before]"
run_sql "
SELECT
  COUNT(*) FILTER (WHERE house_tenure_code IS NOT NULL),
  COUNT(*) FILTER (WHERE housing_type_code IS NOT NULL),
  COUNT(*) FILTER (WHERE basic_living_recipient_type_code IS NOT NULL),
  COUNT(*) FILTER (WHERE disability_grade_code IS NOT NULL)
FROM users;
"

echo
echo "[reconcile]"
run_sql "
WITH synced_users AS (
  UPDATE users u
     SET house_tenure_code = COALESCE(u.house_tenure_code, up.house_tenure_code),
         housing_type_code = COALESCE(u.housing_type_code, up.housing_type_code),
         basic_living_recipient_type_code = COALESCE(u.basic_living_recipient_type_code, up.basic_living_recipient_type_code),
         disability_grade_code = COALESCE(u.disability_grade_code, up.disability_grade_code)
    FROM user_profiles up
   WHERE up.user_key = u.user_key
     AND (
       (u.house_tenure_code IS NULL AND up.house_tenure_code IS NOT NULL)
       OR (u.housing_type_code IS NULL AND up.housing_type_code IS NOT NULL)
       OR (u.basic_living_recipient_type_code IS NULL AND up.basic_living_recipient_type_code IS NOT NULL)
       OR (u.disability_grade_code IS NULL AND up.disability_grade_code IS NOT NULL)
     )
  RETURNING 1
), synced_profiles AS (
  UPDATE user_profiles up
     SET house_tenure_code = COALESCE(up.house_tenure_code, u.house_tenure_code),
         housing_type_code = COALESCE(up.housing_type_code, u.housing_type_code),
         basic_living_recipient_type_code = COALESCE(up.basic_living_recipient_type_code, u.basic_living_recipient_type_code),
         disability_grade_code = COALESCE(up.disability_grade_code, u.disability_grade_code)
    FROM users u
   WHERE up.user_key = u.user_key
     AND (
       (up.house_tenure_code IS NULL AND u.house_tenure_code IS NOT NULL)
       OR (up.housing_type_code IS NULL AND u.housing_type_code IS NOT NULL)
       OR (up.basic_living_recipient_type_code IS NULL AND u.basic_living_recipient_type_code IS NOT NULL)
       OR (up.disability_grade_code IS NULL AND u.disability_grade_code IS NOT NULL)
     )
  RETURNING 1
)
SELECT
  COALESCE((SELECT COUNT(*) FROM synced_users), 0),
  COALESCE((SELECT COUNT(*) FROM synced_profiles), 0);
"

echo
echo "[after]"
run_sql "
SELECT
  COUNT(*) FILTER (WHERE house_tenure_code IS NOT NULL),
  COUNT(*) FILTER (WHERE housing_type_code IS NOT NULL),
  COUNT(*) FILTER (WHERE basic_living_recipient_type_code IS NOT NULL),
  COUNT(*) FILTER (WHERE disability_grade_code IS NOT NULL)
FROM users;
"
