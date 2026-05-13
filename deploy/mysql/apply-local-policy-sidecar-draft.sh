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

apply_mysql_sql_file() {
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

postgres_exec() {
  local sql="$1"
  if command -v psql >/dev/null 2>&1; then
    PGPASSWORD="${DB_MIGRATION_PASSWORD}" psql \
      -h "${DB_HOST}" \
      -p "${DB_PORT}" \
      -U "${DB_MIGRATION_USERNAME}" \
      -d "${DB_NAME}" \
      -At \
      -c "${sql}"
    return 0
  fi

  docker exec -e PGPASSWORD="${DB_MIGRATION_PASSWORD}" -i "${DB_CONTAINER_NAME}" \
    psql -U "${DB_MIGRATION_USERNAME}" -d "${DB_NAME}" -At -c "${sql}"
}

db_exec() {
  local sql="$1"
  if [[ "${DB_FLAVOR}" == "postgres" ]]; then
    postgres_exec "${sql}"
    return 0
  fi
  mysql_exec "${sql}"
}

table_exists() {
  local table_name="$1"
  if [[ "${DB_FLAVOR}" == "postgres" ]]; then
    [[ "$(
      db_exec "
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = '${table_name}';
      "
    )" == "1" ]]
    return 0
  fi

  [[ "$(
    db_exec "
      SELECT COUNT(*)
      FROM information_schema.tables
      WHERE table_schema = '${DB_NAME}'
        AND table_name = '${table_name}';
    "
  )" == "1" ]]
}

detect_db_flavor() {
  if [[ "${DB_URL:-}" == jdbc:postgresql:* ]]; then
    printf "%s" "postgres"
    return 0
  fi

  if docker inspect "${DB_CONTAINER_NAME}" >/dev/null 2>&1 \
    && docker exec "${DB_CONTAINER_NAME}" psql --version >/dev/null 2>&1; then
    printf "%s" "postgres"
    return 0
  fi

  printf "%s" "mysql"
}

load_env_file

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3307}"
DB_NAME="${DB_NAME:-youth_welfare}"
DB_MIGRATION_USERNAME="${DB_MIGRATION_USERNAME:-migration_admin}"
DB_MIGRATION_PASSWORD="${DB_MIGRATION_PASSWORD:-${DB_PASSWORD:-welfare1234!}}"
DB_CONTAINER_NAME="${DB_CONTAINER_NAME:-${MYSQL_CONTAINER_NAME:-youth-welfare-db}}"
APPLY_SEED_WHEN_EMPTY_ONLY="${APPLY_SEED_WHEN_EMPTY_ONLY:-true}"
DB_FLAVOR="$(detect_db_flavor)"

if [[ "${DB_FLAVOR}" == "postgres" ]]; then
  if [[ "${DB_PORT}" == "3307" ]]; then
    DB_PORT="5433"
  fi
fi

if [[ "${DB_FLAVOR}" != "postgres" ]]; then
  [[ -f "${CREATE_SIDECAR_SQL}" ]] || { echo "missing SQL file: ${CREATE_SIDECAR_SQL}" >&2; exit 1; }
  [[ -f "${SEED_NORMALIZATION_SQL}" ]] || { echo "missing SQL file: ${SEED_NORMALIZATION_SQL}" >&2; exit 1; }
  [[ -f "${CREATE_SUMMARY_SLOT_SQL}" ]] || { echo "missing SQL file: ${CREATE_SUMMARY_SLOT_SQL}" >&2; exit 1; }
  [[ -f "${BACKFILL_SUMMARY_SLOT_SQL}" ]] || { echo "missing SQL file: ${BACKFILL_SUMMARY_SLOT_SQL}" >&2; exit 1; }
  [[ -f "${WIDEN_SUMMARY_SLOT_SQL}" ]] || { echo "missing SQL file: ${WIDEN_SUMMARY_SLOT_SQL}" >&2; exit 1; }
fi

if [[ "${DB_FLAVOR}" == "postgres" ]]; then
  echo "postgres_runtime_detected=true"
  echo "legacy_mysql_draft_apply_skipped=true"
  echo "message=Current PostgreSQL main already integrates sidecar schema; legacy MySQL draft SQL is not re-applied."
else
  apply_mysql_sql_file "${CREATE_SIDECAR_SQL}"
  apply_mysql_sql_file "${CREATE_SUMMARY_SLOT_SQL}"
  apply_mysql_sql_file "${WIDEN_SUMMARY_SLOT_SQL}"
fi

if ! table_exists "service_taxonomies"; then
  if [[ "${DB_FLAVOR}" == "postgres" ]]; then
    echo "service_taxonomies table missing in PostgreSQL runtime; bootstrap current schema/collect flow instead of legacy draft apply" >&2
  else
    echo "service_taxonomies table still missing after sidecar create SQL" >&2
  fi
  exit 1
fi

if ! table_exists "service_taxonomy_summary_slots"; then
  if [[ "${DB_FLAVOR}" == "postgres" ]]; then
    echo "service_taxonomy_summary_slots table missing in PostgreSQL runtime; bootstrap current schema/collect flow instead of legacy draft apply" >&2
  else
    echo "service_taxonomy_summary_slots table still missing after summary slot create SQL" >&2
  fi
  exit 1
fi

taxonomy_count="$(
  db_exec "
    SELECT COUNT(*)
    FROM service_taxonomies;
  "
)"

if [[ "${DB_FLAVOR}" != "postgres" && ( "${APPLY_SEED_WHEN_EMPTY_ONLY}" != "true" || "${taxonomy_count}" == "0" ) ]]; then
  apply_mysql_sql_file "${SEED_NORMALIZATION_SQL}"
fi

if [[ "${DB_FLAVOR}" != "postgres" ]]; then
  apply_mysql_sql_file "${BACKFILL_SUMMARY_SLOT_SQL}"
fi

taxonomy_count="$(
  db_exec "
    SELECT COUNT(*)
    FROM service_taxonomies;
  "
)"
education_target_rows="$(
  db_exec "
    SELECT COUNT(*)
    FROM welfare_services ws
    JOIN service_taxonomies st ON st.service_id = ws.id
    LEFT JOIN (
      SELECT service_id, MAX(slot_label) AS slot_label
      FROM service_taxonomy_summary_slots
      WHERE slot_key = 'YOUTH_MAJOR'
      GROUP BY service_id
    ) stss_youth_major ON stss_youth_major.service_id = ws.id
    WHERE ws.unified_category = '기타'
      AND COALESCE(stss_youth_major.slot_label, st.youth_major_label) = '교육';
  "
)"
summary_slot_count="$(
  db_exec "
    SELECT COUNT(*)
    FROM service_taxonomy_summary_slots;
  "
)"
summary_slot_education_services="$(
  db_exec "
    SELECT COUNT(DISTINCT stss.service_id)
    FROM service_taxonomy_summary_slots stss
    JOIN welfare_services ws ON ws.id = stss.service_id
    WHERE stss.slot_key = 'YOUTH_MAJOR'
      AND stss.slot_label = '교육'
      AND ws.unified_category = '기타';
  "
)"
summary_slot_density="$(
  db_exec "
    SELECT 'GOV24_BENEFIT_TYPE', COUNT(DISTINCT service_id)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'GOV24_BENEFIT_TYPE'
    UNION ALL
    SELECT 'GOV24_SERVICE_FIELD', COUNT(DISTINCT service_id)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'GOV24_SERVICE_FIELD'
    UNION ALL
    SELECT 'GOV24_USER_TYPE', COUNT(DISTINCT service_id)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'GOV24_USER_TYPE'
    UNION ALL
    SELECT 'PROVISION_METHOD', COUNT(DISTINCT service_id)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'PROVISION_METHOD'
    UNION ALL
    SELECT 'YOUTH_MAJOR', COUNT(DISTINCT service_id)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'YOUTH_MAJOR'
    UNION ALL
    SELECT 'YOUTH_MID', COUNT(DISTINCT service_id)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'YOUTH_MID';
  "
)"
summary_slot_row_density="$(
  db_exec "
    SELECT 'GOV24_BENEFIT_TYPE', COUNT(*)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'GOV24_BENEFIT_TYPE'
    UNION ALL
    SELECT 'GOV24_SERVICE_FIELD', COUNT(*)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'GOV24_SERVICE_FIELD'
    UNION ALL
    SELECT 'GOV24_USER_TYPE', COUNT(*)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'GOV24_USER_TYPE'
    UNION ALL
    SELECT 'PROVISION_METHOD', COUNT(*)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'PROVISION_METHOD'
    UNION ALL
    SELECT 'YOUTH_MAJOR', COUNT(*)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'YOUTH_MAJOR'
    UNION ALL
    SELECT 'YOUTH_MID', COUNT(*)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'YOUTH_MID';
  "
)"

echo "service_taxonomies=${taxonomy_count}"
echo "service_taxonomy_summary_slots=${summary_slot_count}"
echo "education_target_rows=${education_target_rows}"
echo "slot_education_services=${summary_slot_education_services}"
while IFS= read -r density_line; do
  [[ -n "${density_line}" ]] || continue
  slot_key="${density_line%%$'\t'*}"
  slot_count="${density_line#*$'\t'}"
  echo "slot_density_${slot_key}=${slot_count}"
done <<< "${summary_slot_density}"
while IFS= read -r density_line; do
  [[ -n "${density_line}" ]] || continue
  slot_key="${density_line%%$'\t'*}"
  slot_count="${density_line#*$'\t'}"
  echo "slot_row_density_${slot_key}=${slot_count}"
done <<< "${summary_slot_row_density}"
