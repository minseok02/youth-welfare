#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env.production}"
SCHEMA_FILE="${SCHEMA_FILE:-${ROOT_DIR}/backend/src/main/resources/db/schema.sql}"
PATCH_DIR="${PATCH_DIR:-${ROOT_DIR}/deploy/postgres/patches}"

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

  if [[ ! -f "${ENV_FILE}" ]]; then
    echo "env file not found: ${ENV_FILE}" >&2
    exit 1
  fi

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "$(trim "${line}")" ]] && continue
    [[ "$(trim "${line}")" == \#* ]] && continue
    [[ "${line}" != *=* ]] && continue

    key="$(trim "${line%%=*}")"
    value="${line#*=}"
    value="$(unquote "${value}")"

    if [[ "${key}" == export\ * ]]; then
      key="$(trim "${key#export }")"
    fi

    if [[ ! "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      continue
    fi

    if [[ -n "${!key+x}" ]]; then
      continue
    fi

    export "${key}=${value}"
  done < "${ENV_FILE}"
}

require_non_empty() {
  local name="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    echo "missing required env: ${name}" >&2
    exit 1
  fi
}

extract_host_port() {
  local jdbc_url="$1"
  local remainder host_port

  remainder="${jdbc_url#jdbc:postgresql://}"
  host_port="${remainder%%/*}"
  printf "%s\n" "${host_port}"
}

extract_db_name() {
  local jdbc_url="$1"
  local remainder database_name

  remainder="${jdbc_url#jdbc:postgresql://}"
  database_name="${remainder#*/}"
  database_name="${database_name%%\?*}"
  database_name="${database_name%%;*}"
  printf "%s\n" "${database_name}"
}

if ! command -v psql >/dev/null 2>&1; then
  echo "psql command not found; install postgresql-client first" >&2
  exit 1
fi

if [[ ! -f "${SCHEMA_FILE}" ]]; then
  echo "schema file not found: ${SCHEMA_FILE}" >&2
  exit 1
fi

if [[ ! -d "${PATCH_DIR}" ]]; then
  echo "patch directory not found: ${PATCH_DIR}" >&2
  exit 1
fi

load_env_file

require_non_empty RDS_MASTER_USERNAME "${RDS_MASTER_USERNAME:-}"
require_non_empty RDS_MASTER_PASSWORD "${RDS_MASTER_PASSWORD:-}"
require_non_empty DB_URL "${DB_URL:-}"
require_non_empty DB_USERNAME "${DB_USERNAME:-}"
require_non_empty DB_PASSWORD "${DB_PASSWORD:-}"
require_non_empty DB_APP_PII_USERNAME "${DB_APP_PII_USERNAME:-}"
require_non_empty DB_APP_PII_PASSWORD "${DB_APP_PII_PASSWORD:-}"
require_non_empty DB_NOTIFICATION_PII_RO_USERNAME "${DB_NOTIFICATION_PII_RO_USERNAME:-}"
require_non_empty DB_NOTIFICATION_PII_RO_PASSWORD "${DB_NOTIFICATION_PII_RO_PASSWORD:-}"
require_non_empty DB_ADMIN_RO_USERNAME "${DB_ADMIN_RO_USERNAME:-}"
require_non_empty DB_ADMIN_RO_PASSWORD "${DB_ADMIN_RO_PASSWORD:-}"
require_non_empty DB_CLUSTER_AI_CLEANUP_USERNAME "${DB_CLUSTER_AI_CLEANUP_USERNAME:-}"
require_non_empty DB_CLUSTER_AI_CLEANUP_PASSWORD "${DB_CLUSTER_AI_CLEANUP_PASSWORD:-}"
require_non_empty DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME "${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME:-}"
require_non_empty DB_RECOMMENDATION_RETENTION_CLEANUP_PASSWORD "${DB_RECOMMENDATION_RETENTION_CLEANUP_PASSWORD:-}"
require_non_empty DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME "${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME:-}"
require_non_empty DB_COLLECT_EXECUTION_LOCK_CLEANUP_PASSWORD "${DB_COLLECT_EXECUTION_LOCK_CLEANUP_PASSWORD:-}"
require_non_empty DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME "${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME:-}"
require_non_empty DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_PASSWORD "${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_PASSWORD:-}"
require_non_empty DB_MIGRATION_USERNAME "${DB_MIGRATION_USERNAME:-}"
require_non_empty DB_MIGRATION_PASSWORD "${DB_MIGRATION_PASSWORD:-}"

DB_HOST_PORT="$(extract_host_port "${DB_URL}")"
DB_HOST="${DB_HOST_PORT%%:*}"
DB_PORT="${DB_HOST_PORT##*:}"
if [[ "${DB_HOST}" == "${DB_PORT}" ]]; then
  DB_PORT="5432"
fi
DB_NAME="$(extract_db_name "${DB_URL}")"

if [[ "${DB_NAME}" != "youth_welfare" ]]; then
  echo "expected DB_URL to point to youth_welfare, got ${DB_NAME}" >&2
  exit 1
fi

psql_base=(
  psql
  -v ON_ERROR_STOP=1
  -h "${DB_HOST}"
  -p "${DB_PORT}"
  -U "${RDS_MASTER_USERNAME}"
  -d "${DB_NAME}"
)

psql_migration_base=(
  psql
  -v ON_ERROR_STOP=1
  -h "${DB_HOST}"
  -p "${DB_PORT}"
  -U "${DB_MIGRATION_USERNAME}"
  -d "${DB_NAME}"
)

export PGPASSWORD="${RDS_MASTER_PASSWORD}"

echo "applying base schema to ${DB_HOST}:${DB_PORT}/${DB_NAME}"
"${psql_base[@]}" < "${SCHEMA_FILE}"

echo "creating runtime roles and grants"
"${psql_base[@]}" <<SQL
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${DB_USERNAME}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${DB_USERNAME}', '${DB_PASSWORD}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${DB_USERNAME}', '${DB_PASSWORD}');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${DB_APP_PII_USERNAME}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${DB_APP_PII_USERNAME}', '${DB_APP_PII_PASSWORD}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${DB_APP_PII_USERNAME}', '${DB_APP_PII_PASSWORD}');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${DB_NOTIFICATION_PII_RO_USERNAME}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${DB_NOTIFICATION_PII_RO_USERNAME}', '${DB_NOTIFICATION_PII_RO_PASSWORD}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${DB_NOTIFICATION_PII_RO_USERNAME}', '${DB_NOTIFICATION_PII_RO_PASSWORD}');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${DB_ADMIN_RO_USERNAME}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${DB_ADMIN_RO_USERNAME}', '${DB_ADMIN_RO_PASSWORD}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${DB_ADMIN_RO_USERNAME}', '${DB_ADMIN_RO_PASSWORD}');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${DB_CLUSTER_AI_CLEANUP_USERNAME}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${DB_CLUSTER_AI_CLEANUP_USERNAME}', '${DB_CLUSTER_AI_CLEANUP_PASSWORD}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${DB_CLUSTER_AI_CLEANUP_USERNAME}', '${DB_CLUSTER_AI_CLEANUP_PASSWORD}');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME}', '${DB_RECOMMENDATION_RETENTION_CLEANUP_PASSWORD}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME}', '${DB_RECOMMENDATION_RETENTION_CLEANUP_PASSWORD}');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME}', '${DB_COLLECT_EXECUTION_LOCK_CLEANUP_PASSWORD}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME}', '${DB_COLLECT_EXECUTION_LOCK_CLEANUP_PASSWORD}');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME}', '${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_PASSWORD}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME}', '${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_PASSWORD}');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${DB_MIGRATION_USERNAME}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${DB_MIGRATION_USERNAME}', '${DB_MIGRATION_PASSWORD}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${DB_MIGRATION_USERNAME}', '${DB_MIGRATION_PASSWORD}');
    END IF;
END
\$\$;

GRANT CONNECT ON DATABASE ${DB_NAME} TO ${DB_USERNAME}, ${DB_APP_PII_USERNAME}, ${DB_NOTIFICATION_PII_RO_USERNAME}, ${DB_ADMIN_RO_USERNAME}, ${DB_CLUSTER_AI_CLEANUP_USERNAME}, ${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME}, ${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME}, ${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME}, ${DB_MIGRATION_USERNAME};

GRANT USAGE ON SCHEMA public TO ${DB_USERNAME}, ${DB_ADMIN_RO_USERNAME}, ${DB_CLUSTER_AI_CLEANUP_USERNAME}, ${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME}, ${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME}, ${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME}, ${DB_MIGRATION_USERNAME};
GRANT CREATE ON SCHEMA public TO ${DB_MIGRATION_USERNAME};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ${DB_USERNAME};
GRANT SELECT ON ALL TABLES IN SCHEMA public TO ${DB_ADMIN_RO_USERNAME};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ${DB_USERNAME};

DO \$\$
BEGIN
    IF to_regclass('public.cluster_ai_results') IS NOT NULL THEN
        EXECUTE format('GRANT DELETE ON TABLE public.cluster_ai_results TO %I', '${DB_CLUSTER_AI_CLEANUP_USERNAME}');
        EXECUTE format('GRANT SELECT (created_at) ON TABLE public.cluster_ai_results TO %I', '${DB_CLUSTER_AI_CLEANUP_USERNAME}');
    END IF;
END
\$\$;

DO \$\$
BEGIN
    IF to_regclass('public.user_recommendations') IS NOT NULL THEN
        EXECUTE format('GRANT DELETE ON TABLE public.user_recommendations TO %I', '${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME}');
        EXECUTE format('GRANT SELECT (recommended_at, is_bookmarked) ON TABLE public.user_recommendations TO %I', '${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME}');
    END IF;
END
\$\$;

DO \$\$
BEGIN
    IF to_regclass('public.collect_execution_locks') IS NOT NULL THEN
        EXECUTE format('GRANT DELETE ON TABLE public.collect_execution_locks TO %I', '${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME}');
        EXECUTE format('GRANT SELECT (lock_name, owner_token) ON TABLE public.collect_execution_locks TO %I', '${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME}');
    END IF;
END
\$\$;

DO \$\$
BEGIN
    IF to_regclass('public.web_push_subscriptions') IS NOT NULL THEN
        EXECUTE format('GRANT DELETE ON TABLE public.web_push_subscriptions TO %I', '${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME}');
        EXECUTE format('GRANT SELECT (id, user_key) ON TABLE public.web_push_subscriptions TO %I', '${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME}');
    END IF;
END
\$\$;

GRANT USAGE ON SCHEMA youth_welfare_pii TO ${DB_APP_PII_USERNAME}, ${DB_NOTIFICATION_PII_RO_USERNAME}, ${DB_MIGRATION_USERNAME};
GRANT CREATE ON SCHEMA youth_welfare_pii TO ${DB_MIGRATION_USERNAME};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA youth_welfare_pii TO ${DB_APP_PII_USERNAME};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA youth_welfare_pii TO ${DB_APP_PII_USERNAME};
GRANT SELECT (user_key, email_enc) ON youth_welfare_pii.user_pii TO ${DB_NOTIFICATION_PII_RO_USERNAME};

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO ${DB_MIGRATION_USERNAME};
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA youth_welfare_pii TO ${DB_MIGRATION_USERNAME};
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO ${DB_MIGRATION_USERNAME};
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA youth_welfare_pii TO ${DB_MIGRATION_USERNAME};

ALTER DEFAULT PRIVILEGES FOR ROLE ${RDS_MASTER_USERNAME} IN SCHEMA public
GRANT SELECT ON TABLES TO ${DB_ADMIN_RO_USERNAME};
SQL

echo "configuring migration role default privileges"
PGPASSWORD="${DB_MIGRATION_PASSWORD}" "${psql_migration_base[@]}" \
  -v "admin_ro_username=${DB_ADMIN_RO_USERNAME}" <<'SQL'
SELECT format(
    'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO %I',
    :'admin_ro_username'
) \gexec
SQL

export PGPASSWORD="${RDS_MASTER_PASSWORD}"

shopt -s nullglob
patches=("${PATCH_DIR}"/*.sql)
shopt -u nullglob

for patch in "${patches[@]}"; do
  echo "applying $(basename "${patch}")"
  "${psql_base[@]}" \
    -v "admin_ro_username=${DB_ADMIN_RO_USERNAME}" \
    -v "admin_ro_password=${DB_ADMIN_RO_PASSWORD}" \
    -v "cluster_ai_cleanup_username=${DB_CLUSTER_AI_CLEANUP_USERNAME}" \
    -v "cluster_ai_cleanup_password=${DB_CLUSTER_AI_CLEANUP_PASSWORD}" \
    -v "recommendation_retention_cleanup_username=${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME}" \
    -v "recommendation_retention_cleanup_password=${DB_RECOMMENDATION_RETENTION_CLEANUP_PASSWORD}" \
    -v "collect_execution_lock_cleanup_username=${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME}" \
    -v "collect_execution_lock_cleanup_password=${DB_COLLECT_EXECUTION_LOCK_CLEANUP_PASSWORD}" \
    -v "web_push_subscription_cleanup_username=${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME}" \
    -v "web_push_subscription_cleanup_password=${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_PASSWORD}" \
    -v "migration_username=${DB_MIGRATION_USERNAME}" \
    < "${patch}"
done

echo "rds runtime bootstrap applied"
