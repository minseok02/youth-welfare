#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REPO_ENV_FILE="${ROOT_DIR}/.env"
CREATE_SIDECAR_SQL="${ROOT_DIR}/backend/src/main/resources/db/migration-draft/V2026_04_30_01__create_policy_sidecars.sql"
SEED_NORMALIZATION_SQL="${ROOT_DIR}/backend/src/main/resources/db/migration-draft/V2026_04_30_02__seed_policy_normalization_codes.sql"
CREATE_SUMMARY_SLOT_SQL="${ROOT_DIR}/backend/src/main/resources/db/migration-draft/V2026_05_02_01__add_service_taxonomy_summary_slots.sql"
BACKFILL_SUMMARY_SLOT_SQL="${ROOT_DIR}/backend/src/main/resources/db/migration-draft/V2026_05_02_02__backfill_service_taxonomy_summary_slots.sql"
WIDEN_SUMMARY_SLOT_SQL="${ROOT_DIR}/backend/src/main/resources/db/migration-draft/V2026_05_02_03__widen_service_taxonomy_summary_slot_label.sql"

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf "%s" "${value}"
}

unquote() {
  local value="$1"
  if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf "%s" "${value}"
}

load_env_file() {
  local line key value

  [[ -f "${REPO_ENV_FILE}" ]] || return 0

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "$(trim "${line}")" ]] && continue
    [[ "$(trim "${line}")" == \#* ]] && continue
    [[ "${line}" == *=* ]] || continue

    key="$(trim "${line%%=*}")"
    value="$(unquote "${line#*=}")"

    if [[ "${key}" == export\ * ]]; then
      key="$(trim "${key#export }")"
    fi
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    [[ -n "${!key+x}" ]] && continue
    export "${key}=${value}"
  done < "${REPO_ENV_FILE}"
}

mysql_exec() {
  local sql="$1"
  if command -v mysql >/dev/null 2>&1; then
    MYSQL_PWD="${DB_MIGRATION_PASSWORD}" mysql \
      --default-character-set=utf8mb4 \
      --batch \
      --skip-column-names \
      -h "${DB_HOST}" \
      -P "${DB_PORT}" \
      -u "${DB_MIGRATION_USERNAME}" \
      "${DB_NAME}" \
      -e "${sql}"
    return 0
  fi

  docker exec -e MYSQL_PWD="${DB_MIGRATION_PASSWORD}" -i "${MYSQL_CONTAINER_NAME}" \
    mysql --default-character-set=utf8mb4 --batch --skip-column-names \
    -u"${DB_MIGRATION_USERNAME}" "${DB_NAME}" -e "${sql}"
}

apply_sql_file() {
  local file_path="$1"
  if command -v mysql >/dev/null 2>&1; then
    MYSQL_PWD="${DB_MIGRATION_PASSWORD}" mysql \
      --default-character-set=utf8mb4 \
      -h "${DB_HOST}" \
      -P "${DB_PORT}" \
      -u "${DB_MIGRATION_USERNAME}" \
      "${DB_NAME}" < "${file_path}"
    return 0
  fi

  docker exec -e MYSQL_PWD="${DB_MIGRATION_PASSWORD}" -i "${MYSQL_CONTAINER_NAME}" \
    mysql --default-character-set=utf8mb4 -u"${DB_MIGRATION_USERNAME}" "${DB_NAME}" < "${file_path}"
}

table_exists() {
  local table_name="$1"
  [[ "$(
    mysql_exec "
      SELECT COUNT(*)
      FROM information_schema.tables
      WHERE table_schema = '${DB_NAME}'
        AND table_name = '${table_name}';
    "
  )" == "1" ]]
}

load_env_file

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3307}"
DB_NAME="${DB_NAME:-youth_welfare}"
DB_MIGRATION_USERNAME="${DB_MIGRATION_USERNAME:-migration_admin}"
DB_MIGRATION_PASSWORD="${DB_MIGRATION_PASSWORD:-${DB_PASSWORD:-welfare1234!}}"
MYSQL_CONTAINER_NAME="${MYSQL_CONTAINER_NAME:-youth-welfare-db}"
APPLY_SEED_WHEN_EMPTY_ONLY="${APPLY_SEED_WHEN_EMPTY_ONLY:-true}"

[[ -f "${CREATE_SIDECAR_SQL}" ]] || { echo "missing SQL file: ${CREATE_SIDECAR_SQL}" >&2; exit 1; }
[[ -f "${SEED_NORMALIZATION_SQL}" ]] || { echo "missing SQL file: ${SEED_NORMALIZATION_SQL}" >&2; exit 1; }
[[ -f "${CREATE_SUMMARY_SLOT_SQL}" ]] || { echo "missing SQL file: ${CREATE_SUMMARY_SLOT_SQL}" >&2; exit 1; }
[[ -f "${BACKFILL_SUMMARY_SLOT_SQL}" ]] || { echo "missing SQL file: ${BACKFILL_SUMMARY_SLOT_SQL}" >&2; exit 1; }
[[ -f "${WIDEN_SUMMARY_SLOT_SQL}" ]] || { echo "missing SQL file: ${WIDEN_SUMMARY_SLOT_SQL}" >&2; exit 1; }

apply_sql_file "${CREATE_SIDECAR_SQL}"
apply_sql_file "${CREATE_SUMMARY_SLOT_SQL}"
apply_sql_file "${WIDEN_SUMMARY_SLOT_SQL}"

if ! table_exists "service_taxonomies"; then
  echo "service_taxonomies table still missing after sidecar create SQL" >&2
  exit 1
fi

if ! table_exists "service_taxonomy_summary_slots"; then
  echo "service_taxonomy_summary_slots table still missing after summary slot create SQL" >&2
  exit 1
fi

taxonomy_count="$(
  mysql_exec "
    SELECT COUNT(*)
    FROM service_taxonomies;
  "
)"

if [[ "${APPLY_SEED_WHEN_EMPTY_ONLY}" != "true" || "${taxonomy_count}" == "0" ]]; then
  apply_sql_file "${SEED_NORMALIZATION_SQL}"
fi

apply_sql_file "${BACKFILL_SUMMARY_SLOT_SQL}"

taxonomy_count="$(
  mysql_exec "
    SELECT COUNT(*)
    FROM service_taxonomies;
  "
)"
education_target_rows="$(
  mysql_exec "
    SELECT COUNT(*)
    FROM welfare_services ws
    JOIN service_taxonomies st ON st.service_id = ws.id
    WHERE ws.unified_category = '기타'
      AND st.youth_major_label = '교육';
  "
)"
summary_slot_count="$(
  mysql_exec "
    SELECT COUNT(*)
    FROM service_taxonomy_summary_slots;
  "
)"
summary_slot_education_services="$(
  mysql_exec "
    SELECT COUNT(DISTINCT stss.service_id)
    FROM service_taxonomy_summary_slots stss
    JOIN welfare_services ws ON ws.id = stss.service_id
    WHERE stss.slot_key = 'YOUTH_MAJOR'
      AND stss.slot_label = '교육'
      AND ws.unified_category = '기타';
  "
)"

echo "service_taxonomies=${taxonomy_count}"
echo "service_taxonomy_summary_slots=${summary_slot_count}"
echo "education_target_rows=${education_target_rows}"
echo "slot_education_services=${summary_slot_education_services}"
