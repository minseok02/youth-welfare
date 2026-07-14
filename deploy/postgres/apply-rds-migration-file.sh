#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env.production}"
DRY_RUN="${DRY_RUN:-false}"
RECORD_HISTORY="${RECORD_HISTORY:-true}"
NOTE="${NOTE:-manual RDS master apply}"

usage() {
  cat <<'EOF'
Usage:
  ENV_FILE=.env.production deploy/postgres/apply-rds-migration-file.sh [--dry-run] <migration.sql>

Applies one PostgreSQL migration file to the RDS runtime database with the
RDS master/owner account, then records the script in schema_migration_history.

Environment:
  ENV_FILE          env file containing RDS_MASTER_USERNAME/PASSWORD and DB_URL
  DRY_RUN          true prints the plan and verifies connection only
  RECORD_HISTORY   false skips schema_migration_history upsert
  NOTE             history note, default "manual RDS master apply"
EOF
}

normalize_bool() {
  local value="${1,,}"
  case "${value}" in
    true|false) printf '%s' "${value}" ;;
    *)
      echo "unsupported boolean value: ${1}" >&2
      exit 1
      ;;
  esac
}

migration_version_from_name() {
  local script_name="$1"

  if [[ "${script_name}" =~ ^V([0-9]{4})_([0-9]{2})_([0-9]{2})_([0-9]{2})__([A-Za-z0-9_]+)\.sql$ ]]; then
    printf '%s.%s.%s.%s\t%s\n' \
      "${BASH_REMATCH[1]}" \
      "${BASH_REMATCH[2]}" \
      "${BASH_REMATCH[3]}" \
      "${BASH_REMATCH[4]}" \
      "${BASH_REMATCH[5]}"
    return 0
  fi

  echo "unsupported migration file name: ${script_name}" >&2
  echo "expected: VYYYY_MM_DD_NN__description.sql" >&2
  exit 1
}

sql_literal() {
  local value="$1"
  printf "'%s'" "${value//\'/\'\'}"
}

migration_file=""
while (($# > 0)); do
  case "$1" in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    -*)
      echo "unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
    *)
      migration_file="$1"
      shift
      ;;
  esac
done

if [[ -z "${migration_file}" && $# -gt 0 ]]; then
  migration_file="$1"
fi

if [[ -z "${migration_file}" ]]; then
  usage >&2
  exit 1
fi

if [[ "${migration_file}" != /* ]]; then
  migration_file="${ROOT_DIR}/${migration_file}"
fi

if [[ ! -f "${migration_file}" ]]; then
  echo "migration file not found: ${migration_file}" >&2
  exit 1
fi

DRY_RUN="$(normalize_bool "${DRY_RUN}")"
RECORD_HISTORY="$(normalize_bool "${RECORD_HISTORY}")"

env_file="$(smoke_resolve_env_file "${ENV_FILE}" "${ROOT_DIR}")"
if [[ ! -f "${env_file}" ]]; then
  echo "env file not found: ${env_file}" >&2
  exit 1
fi

rds_master_username="${RDS_MASTER_USERNAME:-$(smoke_load_env_value "${env_file}" RDS_MASTER_USERNAME)}"
rds_master_password="${RDS_MASTER_PASSWORD:-$(smoke_load_env_value "${env_file}" RDS_MASTER_PASSWORD)}"
db_url="$(smoke_resolve_db_connection_url)" || {
  echo "DB_URL or DB_DIRECT_URL is required" >&2
  exit 1
}

if [[ -z "${rds_master_username}" ]]; then
  echo "missing required env: RDS_MASTER_USERNAME" >&2
  exit 1
fi
if [[ -z "${rds_master_password}" ]]; then
  echo "missing required env: RDS_MASTER_PASSWORD" >&2
  exit 1
fi

script_name="$(basename "${migration_file}")"
IFS=$'\t' read -r migration_version migration_description < <(migration_version_from_name "${script_name}")
script_sha256="$(sha256sum "${migration_file}" | awk '{print $1}')"

if [[ ! "${script_sha256}" =~ ^[0-9a-f]{64}$ ]]; then
  echo "failed to compute valid sha256 for ${migration_file}" >&2
  exit 1
fi

master_psql() {
  smoke_run_psql_direct "${rds_master_password}" \
    --set ON_ERROR_STOP=1 \
    --username "${rds_master_username}" \
    --dbname "${db_url}" \
    "$@"
}

master_query() {
  local sql="$1"
  master_psql -At -F $'\t' -c "${sql}"
}

current_user="$(master_query "select current_user;")"
history_exists="$(master_query "select (to_regclass('public.schema_migration_history') is not null)::text;")"
if [[ "${history_exists}" != "true" ]]; then
  echo "schema_migration_history is missing; apply V2026_06_27_01 first" >&2
  exit 1
fi

already_recorded="$(master_query "select count(*) from public.schema_migration_history where script_name = $(sql_literal "${script_name}");")"

echo "migration_file=${migration_file}"
echo "script_name=${script_name}"
echo "version=${migration_version}"
echo "description=${migration_description}"
echo "script_sha256=${script_sha256}"
echo "db_user=${current_user}"
echo "history_existing_rows=${already_recorded}"
echo "dry_run=${DRY_RUN}"
echo "record_history=${RECORD_HISTORY}"

if [[ "${DRY_RUN}" == "true" ]]; then
  echo "dry_run_result=planned"
  exit 0
fi

master_psql -f "${migration_file}"

if [[ "${RECORD_HISTORY}" == "true" ]]; then
  master_psql <<SQL
INSERT INTO public.schema_migration_history (version, description, script_name, script_sha256, note)
VALUES (
  $(sql_literal "${migration_version}"),
  $(sql_literal "${migration_description}"),
  $(sql_literal "${script_name}"),
  $(sql_literal "${script_sha256}"),
  $(sql_literal "${NOTE}")
)
ON CONFLICT (script_name) DO UPDATE
SET script_sha256 = EXCLUDED.script_sha256,
    note = EXCLUDED.note
RETURNING version, script_name, applied_by;
SQL
fi

history_rows="$(master_query "select count(*) from public.schema_migration_history where script_name = $(sql_literal "${script_name}") and script_sha256 = $(sql_literal "${script_sha256}");")"
if [[ "${RECORD_HISTORY}" == "true" && "${history_rows}" != "1" ]]; then
  echo "history verification failed for ${script_name}" >&2
  exit 1
fi

echo "apply_result=ok"
