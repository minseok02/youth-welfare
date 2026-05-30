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

DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME="${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME:-recommendation_review_gate_command_rw}"
DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD="${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD:-${DB_PASSWORD:-}}"

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
require_non_empty DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME "${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME}"
require_non_empty DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD "${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD}"
require_non_empty DB_CHAT_SESSION_CLEANUP_USERNAME "${DB_CHAT_SESSION_CLEANUP_USERNAME:-}"
require_non_empty DB_CHAT_SESSION_CLEANUP_PASSWORD "${DB_CHAT_SESSION_CLEANUP_PASSWORD:-}"
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
"${psql_base[@]}" \
  -v "db_name=${DB_NAME}" \
  -v "db_username=${DB_USERNAME}" \
  -v "db_password=${DB_PASSWORD}" \
  -v "db_app_pii_username=${DB_APP_PII_USERNAME}" \
  -v "db_app_pii_password=${DB_APP_PII_PASSWORD}" \
  -v "db_notification_pii_ro_username=${DB_NOTIFICATION_PII_RO_USERNAME}" \
  -v "db_notification_pii_ro_password=${DB_NOTIFICATION_PII_RO_PASSWORD}" \
  -v "db_admin_ro_username=${DB_ADMIN_RO_USERNAME}" \
  -v "db_admin_ro_password=${DB_ADMIN_RO_PASSWORD}" \
  -v "db_recommendation_review_gate_command_username=${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME}" \
  -v "db_recommendation_review_gate_command_password=${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD}" \
  -v "db_chat_session_cleanup_username=${DB_CHAT_SESSION_CLEANUP_USERNAME}" \
  -v "db_chat_session_cleanup_password=${DB_CHAT_SESSION_CLEANUP_PASSWORD}" \
  -v "db_cluster_ai_cleanup_username=${DB_CLUSTER_AI_CLEANUP_USERNAME}" \
  -v "db_cluster_ai_cleanup_password=${DB_CLUSTER_AI_CLEANUP_PASSWORD}" \
  -v "db_recommendation_retention_cleanup_username=${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME}" \
  -v "db_recommendation_retention_cleanup_password=${DB_RECOMMENDATION_RETENTION_CLEANUP_PASSWORD}" \
  -v "db_collect_execution_lock_cleanup_username=${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME}" \
  -v "db_collect_execution_lock_cleanup_password=${DB_COLLECT_EXECUTION_LOCK_CLEANUP_PASSWORD}" \
  -v "db_web_push_subscription_cleanup_username=${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME}" \
  -v "db_web_push_subscription_cleanup_password=${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_PASSWORD}" \
  -v "db_migration_username=${DB_MIGRATION_USERNAME}" \
  -v "db_migration_password=${DB_MIGRATION_PASSWORD}" \
  -v "rds_master_username=${RDS_MASTER_USERNAME}" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_username', :'db_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_username') \gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'db_username', :'db_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_username') \gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_app_pii_username', :'db_app_pii_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_app_pii_username') \gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'db_app_pii_username', :'db_app_pii_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_app_pii_username') \gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_notification_pii_ro_username', :'db_notification_pii_ro_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_notification_pii_ro_username') \gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'db_notification_pii_ro_username', :'db_notification_pii_ro_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_notification_pii_ro_username') \gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_admin_ro_username', :'db_admin_ro_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_admin_ro_username') \gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'db_admin_ro_username', :'db_admin_ro_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_admin_ro_username') \gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_recommendation_review_gate_command_username', :'db_recommendation_review_gate_command_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_recommendation_review_gate_command_username') \gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'db_recommendation_review_gate_command_username', :'db_recommendation_review_gate_command_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_recommendation_review_gate_command_username') \gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_chat_session_cleanup_username', :'db_chat_session_cleanup_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_chat_session_cleanup_username') \gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'db_chat_session_cleanup_username', :'db_chat_session_cleanup_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_chat_session_cleanup_username') \gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_cluster_ai_cleanup_username', :'db_cluster_ai_cleanup_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_cluster_ai_cleanup_username') \gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'db_cluster_ai_cleanup_username', :'db_cluster_ai_cleanup_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_cluster_ai_cleanup_username') \gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_recommendation_retention_cleanup_username', :'db_recommendation_retention_cleanup_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_recommendation_retention_cleanup_username') \gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'db_recommendation_retention_cleanup_username', :'db_recommendation_retention_cleanup_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_recommendation_retention_cleanup_username') \gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_collect_execution_lock_cleanup_username', :'db_collect_execution_lock_cleanup_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_collect_execution_lock_cleanup_username') \gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'db_collect_execution_lock_cleanup_username', :'db_collect_execution_lock_cleanup_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_collect_execution_lock_cleanup_username') \gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_web_push_subscription_cleanup_username', :'db_web_push_subscription_cleanup_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_web_push_subscription_cleanup_username') \gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'db_web_push_subscription_cleanup_username', :'db_web_push_subscription_cleanup_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_web_push_subscription_cleanup_username') \gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_migration_username', :'db_migration_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_migration_username') \gexec
SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'db_migration_username', :'db_migration_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_migration_username') \gexec

GRANT CONNECT ON DATABASE :"db_name" TO :"db_username", :"db_app_pii_username", :"db_notification_pii_ro_username", :"db_admin_ro_username", :"db_recommendation_review_gate_command_username", :"db_chat_session_cleanup_username", :"db_cluster_ai_cleanup_username", :"db_recommendation_retention_cleanup_username", :"db_collect_execution_lock_cleanup_username", :"db_web_push_subscription_cleanup_username", :"db_migration_username";

GRANT USAGE ON SCHEMA public TO :"db_username", :"db_admin_ro_username", :"db_recommendation_review_gate_command_username", :"db_chat_session_cleanup_username", :"db_cluster_ai_cleanup_username", :"db_recommendation_retention_cleanup_username", :"db_collect_execution_lock_cleanup_username", :"db_web_push_subscription_cleanup_username", :"db_migration_username";
GRANT CREATE ON SCHEMA public TO :"db_migration_username";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"db_username";
GRANT SELECT ON ALL TABLES IN SCHEMA public TO :"db_admin_ro_username";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO :"db_username";

SELECT format('REVOKE DELETE ON TABLE public.chat_sessions FROM %I', :'db_username')
WHERE to_regclass('public.chat_sessions') IS NOT NULL \gexec
SELECT format('REVOKE DELETE ON TABLE public.recent_policy_views FROM %I', :'db_username')
WHERE to_regclass('public.recent_policy_views') IS NOT NULL \gexec
SELECT format('REVOKE ALL PRIVILEGES ON TABLE public.recommendation_review_gate_promotion_approvals FROM %I', :'db_username')
WHERE to_regclass('public.recommendation_review_gate_promotion_approvals') IS NOT NULL \gexec
SELECT format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.recommendation_review_gate_promotion_approvals TO %I', :'db_recommendation_review_gate_command_username')
WHERE to_regclass('public.recommendation_review_gate_promotion_approvals') IS NOT NULL \gexec
SELECT format('GRANT DELETE ON TABLE public.chat_sessions TO %I', :'db_chat_session_cleanup_username')
WHERE to_regclass('public.chat_sessions') IS NOT NULL \gexec
SELECT format('GRANT SELECT (id, user_key) ON TABLE public.chat_sessions TO %I', :'db_chat_session_cleanup_username')
WHERE to_regclass('public.chat_sessions') IS NOT NULL \gexec

SELECT format('GRANT DELETE ON TABLE public.cluster_ai_results TO %I', :'db_cluster_ai_cleanup_username')
WHERE to_regclass('public.cluster_ai_results') IS NOT NULL \gexec
SELECT format('GRANT SELECT (created_at) ON TABLE public.cluster_ai_results TO %I', :'db_cluster_ai_cleanup_username')
WHERE to_regclass('public.cluster_ai_results') IS NOT NULL \gexec

SELECT format('GRANT DELETE ON TABLE public.user_recommendations TO %I', :'db_recommendation_retention_cleanup_username')
WHERE to_regclass('public.user_recommendations') IS NOT NULL \gexec
SELECT format('GRANT SELECT (recommended_at, is_bookmarked) ON TABLE public.user_recommendations TO %I', :'db_recommendation_retention_cleanup_username')
WHERE to_regclass('public.user_recommendations') IS NOT NULL \gexec

SELECT format('GRANT DELETE ON TABLE public.collect_execution_locks TO %I', :'db_collect_execution_lock_cleanup_username')
WHERE to_regclass('public.collect_execution_locks') IS NOT NULL \gexec
SELECT format('GRANT SELECT (lock_name, owner_token) ON TABLE public.collect_execution_locks TO %I', :'db_collect_execution_lock_cleanup_username')
WHERE to_regclass('public.collect_execution_locks') IS NOT NULL \gexec

SELECT format('GRANT DELETE ON TABLE public.web_push_subscriptions TO %I', :'db_web_push_subscription_cleanup_username')
WHERE to_regclass('public.web_push_subscriptions') IS NOT NULL \gexec
SELECT format('GRANT SELECT (id, user_key) ON TABLE public.web_push_subscriptions TO %I', :'db_web_push_subscription_cleanup_username')
WHERE to_regclass('public.web_push_subscriptions') IS NOT NULL \gexec

GRANT USAGE ON SCHEMA youth_welfare_pii TO :"db_app_pii_username", :"db_notification_pii_ro_username", :"db_migration_username";
GRANT CREATE ON SCHEMA youth_welfare_pii TO :"db_migration_username";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA youth_welfare_pii TO :"db_app_pii_username";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA youth_welfare_pii TO :"db_app_pii_username";
GRANT SELECT (user_key, email_enc) ON youth_welfare_pii.user_pii TO :"db_notification_pii_ro_username";

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO :"db_migration_username";
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA youth_welfare_pii TO :"db_migration_username";
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO :"db_migration_username";
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA youth_welfare_pii TO :"db_migration_username";

ALTER DEFAULT PRIVILEGES FOR ROLE :"rds_master_username" IN SCHEMA public
GRANT SELECT ON TABLES TO :"db_admin_ro_username";
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
    -v "recommendation_review_gate_command_username=${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME}" \
    -v "recommendation_review_gate_command_password=${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD}" \
    -v "chat_session_cleanup_username=${DB_CHAT_SESSION_CLEANUP_USERNAME}" \
    -v "chat_session_cleanup_password=${DB_CHAT_SESSION_CLEANUP_PASSWORD}" \
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
