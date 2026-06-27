#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env.production}"
PRINT_SUMMARY="${PRINT_SUMMARY:-true}"

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

normalize_bool() {
  local value="${1,,}"
  case "${value}" in
    true|false) printf "%s" "${value}" ;;
    *)
      echo "unsupported boolean value: ${1}" >&2
      exit 1
      ;;
  esac
}

if ! command -v psql >/dev/null 2>&1; then
  echo "psql command not found; install postgresql-client first" >&2
  exit 1
fi

PRINT_SUMMARY="$(normalize_bool "${PRINT_SUMMARY}")"
load_env_file

require_non_empty RDS_MASTER_USERNAME "${RDS_MASTER_USERNAME:-}"
require_non_empty RDS_MASTER_PASSWORD "${RDS_MASTER_PASSWORD:-}"
require_non_empty DB_URL "${DB_URL:-}"
require_non_empty DB_USERNAME "${DB_USERNAME:-}"
require_non_empty DB_PASSWORD "${DB_PASSWORD:-}"
require_non_empty DB_ADMIN_RO_USERNAME "${DB_ADMIN_RO_USERNAME:-}"
require_non_empty DB_ADMIN_RO_PASSWORD "${DB_ADMIN_RO_PASSWORD:-}"
require_non_empty DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME "${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME:-}"
require_non_empty DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD "${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD:-}"
require_non_empty DB_RECOMMENDATION_PERSISTENCE_COMMAND_USERNAME "${DB_RECOMMENDATION_PERSISTENCE_COMMAND_USERNAME:-}"
require_non_empty DB_RECOMMENDATION_PERSISTENCE_COMMAND_PASSWORD "${DB_RECOMMENDATION_PERSISTENCE_COMMAND_PASSWORD:-}"
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
require_non_empty DB_APP_PII_USERNAME "${DB_APP_PII_USERNAME:-}"
require_non_empty DB_APP_PII_PASSWORD "${DB_APP_PII_PASSWORD:-}"
require_non_empty DB_NOTIFICATION_PII_RO_USERNAME "${DB_NOTIFICATION_PII_RO_USERNAME:-}"
require_non_empty DB_NOTIFICATION_PII_RO_PASSWORD "${DB_NOTIFICATION_PII_RO_PASSWORD:-}"
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

master_psql() {
  PGPASSWORD="${RDS_MASTER_PASSWORD}" \
  psql -X -v ON_ERROR_STOP=1 -At \
    -h "${DB_HOST}" \
    -p "${DB_PORT}" \
    -U "${RDS_MASTER_USERNAME}" \
    -d "${DB_NAME}" \
    "$@"
}

check_login() {
  local label="$1"
  local username="$2"
  local password="$3"

  PGPASSWORD="${password}" \
  psql -X -v ON_ERROR_STOP=1 -At \
    -h "${DB_HOST}" \
    -p "${DB_PORT}" \
    -U "${username}" \
    -d "${DB_NAME}" \
    -c "select current_user" >/dev/null

  echo "login_ok=${label}:${username}"
}

expect_master_bool() {
  local label="$1"
  local expected="$2"
  local query="$3"
  local actual

  actual="$(master_psql -c "${query}")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "grant check failed: ${label} expected=${expected} actual=${actual}" >&2
    exit 1
  fi
  echo "grant_ok=${label}:${actual}"
}

if [[ "${PRINT_SUMMARY}" == "true" ]]; then
  echo "verifying RDS runtime roles at ${DB_HOST}:${DB_PORT}/${DB_NAME}"
fi

check_login primary "${DB_USERNAME}" "${DB_PASSWORD}"
check_login admin_ro "${DB_ADMIN_RO_USERNAME}" "${DB_ADMIN_RO_PASSWORD}"
check_login recommendation_review_gate_command "${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME}" "${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD}"
check_login recommendation_persistence_command "${DB_RECOMMENDATION_PERSISTENCE_COMMAND_USERNAME}" "${DB_RECOMMENDATION_PERSISTENCE_COMMAND_PASSWORD}"
check_login chat_session_cleanup "${DB_CHAT_SESSION_CLEANUP_USERNAME}" "${DB_CHAT_SESSION_CLEANUP_PASSWORD}"
check_login cluster_ai_cleanup "${DB_CLUSTER_AI_CLEANUP_USERNAME}" "${DB_CLUSTER_AI_CLEANUP_PASSWORD}"
check_login recommendation_retention_cleanup "${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME}" "${DB_RECOMMENDATION_RETENTION_CLEANUP_PASSWORD}"
check_login collect_execution_lock_cleanup "${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME}" "${DB_COLLECT_EXECUTION_LOCK_CLEANUP_PASSWORD}"
check_login web_push_subscription_cleanup "${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME}" "${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_PASSWORD}"
check_login pii_rw "${DB_APP_PII_USERNAME}" "${DB_APP_PII_PASSWORD}"
check_login notification_pii_ro "${DB_NOTIFICATION_PII_RO_USERNAME}" "${DB_NOTIFICATION_PII_RO_PASSWORD}"
check_login migration "${DB_MIGRATION_USERNAME}" "${DB_MIGRATION_PASSWORD}"

expect_master_bool admin_ro_policy_error_reports_select t \
  "select has_table_privilege('${DB_ADMIN_RO_USERNAME}', 'public.policy_error_reports', 'SELECT')"
expect_master_bool admin_ro_support_inquiries_select t \
  "select has_table_privilege('${DB_ADMIN_RO_USERNAME}', 'public.support_inquiries', 'SELECT')"
expect_master_bool admin_ro_policy_duplicate_review_records_select t \
  "select has_table_privilege('${DB_ADMIN_RO_USERNAME}', 'public.policy_duplicate_review_records', 'SELECT')"
expect_master_bool admin_ro_notification_attempt_logs_select t \
  "select has_table_privilege('${DB_ADMIN_RO_USERNAME}', 'public.notification_attempt_logs', 'SELECT')"
expect_master_bool admin_ro_support_inquiries_insert f \
  "select has_table_privilege('${DB_ADMIN_RO_USERNAME}', 'public.support_inquiries', 'INSERT')"
expect_master_bool admin_ro_support_inquiries_update f \
  "select has_table_privilege('${DB_ADMIN_RO_USERNAME}', 'public.support_inquiries', 'UPDATE')"
expect_master_bool admin_ro_support_inquiries_delete f \
  "select has_table_privilege('${DB_ADMIN_RO_USERNAME}', 'public.support_inquiries', 'DELETE')"
expect_master_bool app_core_rw_chat_sessions_delete f \
  "select has_table_privilege('${DB_USERNAME}', 'public.chat_sessions', 'DELETE')"
expect_master_bool app_core_rw_cluster_ai_results_delete f \
  "select has_table_privilege('${DB_USERNAME}', 'public.cluster_ai_results', 'DELETE')"
expect_master_bool app_core_rw_collect_execution_locks_delete f \
  "select has_table_privilege('${DB_USERNAME}', 'public.collect_execution_locks', 'DELETE')"
expect_master_bool app_core_rw_web_push_subscriptions_delete f \
  "select has_table_privilege('${DB_USERNAME}', 'public.web_push_subscriptions', 'DELETE')"
expect_master_bool app_core_rw_recent_policy_views_delete f \
  "select has_table_privilege('${DB_USERNAME}', 'public.recent_policy_views', 'DELETE')"
expect_master_bool app_core_rw_rrgpa_select f \
  "select has_table_privilege('${DB_USERNAME}', 'public.recommendation_review_gate_promotion_approvals', 'SELECT')"
expect_master_bool app_core_rw_rrgpa_insert f \
  "select has_table_privilege('${DB_USERNAME}', 'public.recommendation_review_gate_promotion_approvals', 'INSERT')"
expect_master_bool app_core_rw_rrgpa_update f \
  "select has_table_privilege('${DB_USERNAME}', 'public.recommendation_review_gate_promotion_approvals', 'UPDATE')"
expect_master_bool app_core_rw_rrgpa_delete f \
  "select has_table_privilege('${DB_USERNAME}', 'public.recommendation_review_gate_promotion_approvals', 'DELETE')"
expect_master_bool app_core_rw_user_recommendations_delete f \
  "select has_table_privilege('${DB_USERNAME}', 'public.user_recommendations', 'DELETE')"
expect_master_bool recommendation_review_gate_command_select t \
  "select has_table_privilege('${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME}', 'public.recommendation_review_gate_promotion_approvals', 'SELECT')"
expect_master_bool recommendation_review_gate_command_insert t \
  "select has_table_privilege('${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME}', 'public.recommendation_review_gate_promotion_approvals', 'INSERT')"
expect_master_bool recommendation_review_gate_command_update t \
  "select has_table_privilege('${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME}', 'public.recommendation_review_gate_promotion_approvals', 'UPDATE')"
expect_master_bool recommendation_review_gate_command_delete t \
  "select has_table_privilege('${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME}', 'public.recommendation_review_gate_promotion_approvals', 'DELETE')"
expect_master_bool recommendation_persistence_command_insert t \
  "select has_table_privilege('${DB_RECOMMENDATION_PERSISTENCE_COMMAND_USERNAME}', 'public.user_recommendations', 'INSERT')"
expect_master_bool recommendation_persistence_command_delete t \
  "select has_table_privilege('${DB_RECOMMENDATION_PERSISTENCE_COMMAND_USERNAME}', 'public.user_recommendations', 'DELETE')"
expect_master_bool recommendation_persistence_command_user_key_select t \
  "select has_column_privilege('${DB_RECOMMENDATION_PERSISTENCE_COMMAND_USERNAME}', 'public.user_recommendations', 'user_key', 'SELECT')"
expect_master_bool recommendation_persistence_command_sequence_usage t \
  "select has_sequence_privilege('${DB_RECOMMENDATION_PERSISTENCE_COMMAND_USERNAME}', 'public.user_recommendations_id_seq', 'USAGE')"
expect_master_bool recommendation_persistence_command_recent_policy_views_delete t \
  "select has_table_privilege('${DB_RECOMMENDATION_PERSISTENCE_COMMAND_USERNAME}', 'public.recent_policy_views', 'DELETE')"
expect_master_bool recommendation_persistence_command_recent_policy_views_user_key_select t \
  "select has_column_privilege('${DB_RECOMMENDATION_PERSISTENCE_COMMAND_USERNAME}', 'public.recent_policy_views', 'user_key', 'SELECT')"
expect_master_bool chat_session_cleanup_delete t \
  "select has_table_privilege('${DB_CHAT_SESSION_CLEANUP_USERNAME}', 'public.chat_sessions', 'DELETE')"
expect_master_bool chat_session_cleanup_select_id t \
  "select has_column_privilege('${DB_CHAT_SESSION_CLEANUP_USERNAME}', 'public.chat_sessions', 'id', 'SELECT')"
expect_master_bool chat_session_cleanup_select_user_key t \
  "select has_column_privilege('${DB_CHAT_SESSION_CLEANUP_USERNAME}', 'public.chat_sessions', 'user_key', 'SELECT')"
expect_master_bool cluster_ai_cleanup_delete t \
  "select has_table_privilege('${DB_CLUSTER_AI_CLEANUP_USERNAME}', 'public.cluster_ai_results', 'DELETE')"
expect_master_bool cluster_ai_cleanup_select_created_at t \
  "select has_column_privilege('${DB_CLUSTER_AI_CLEANUP_USERNAME}', 'public.cluster_ai_results', 'created_at', 'SELECT')"
expect_master_bool recommendation_retention_cleanup_delete t \
  "select has_table_privilege('${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME}', 'public.user_recommendations', 'DELETE')"
expect_master_bool recommendation_retention_cleanup_select_recommended_at t \
  "select has_column_privilege('${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME}', 'public.user_recommendations', 'recommended_at', 'SELECT')"
expect_master_bool recommendation_retention_cleanup_select_is_bookmarked t \
  "select has_column_privilege('${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME}', 'public.user_recommendations', 'is_bookmarked', 'SELECT')"
expect_master_bool collect_execution_lock_cleanup_delete t \
  "select has_table_privilege('${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME}', 'public.collect_execution_locks', 'DELETE')"
expect_master_bool collect_execution_lock_cleanup_select_lock_name t \
  "select has_column_privilege('${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME}', 'public.collect_execution_locks', 'lock_name', 'SELECT')"
expect_master_bool collect_execution_lock_cleanup_select_owner_token t \
  "select has_column_privilege('${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME}', 'public.collect_execution_locks', 'owner_token', 'SELECT')"
expect_master_bool web_push_subscription_cleanup_delete t \
  "select has_table_privilege('${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME}', 'public.web_push_subscriptions', 'DELETE')"
expect_master_bool web_push_subscription_cleanup_select_id t \
  "select has_column_privilege('${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME}', 'public.web_push_subscriptions', 'id', 'SELECT')"
expect_master_bool web_push_subscription_cleanup_select_user_key t \
  "select has_column_privilege('${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME}', 'public.web_push_subscriptions', 'user_key', 'SELECT')"
expect_master_bool app_core_rw_user_pii_select f \
  "select has_table_privilege('${DB_USERNAME}', 'youth_welfare_pii.user_pii', 'SELECT')"
expect_master_bool app_pii_rw_user_pii_select t \
  "select has_table_privilege('${DB_APP_PII_USERNAME}', 'youth_welfare_pii.user_pii', 'SELECT')"
expect_master_bool app_pii_rw_user_pii_insert t \
  "select has_table_privilege('${DB_APP_PII_USERNAME}', 'youth_welfare_pii.user_pii', 'INSERT')"
expect_master_bool app_pii_rw_user_pii_update t \
  "select has_table_privilege('${DB_APP_PII_USERNAME}', 'youth_welfare_pii.user_pii', 'UPDATE')"
expect_master_bool app_pii_rw_user_pii_delete t \
  "select has_table_privilege('${DB_APP_PII_USERNAME}', 'youth_welfare_pii.user_pii', 'DELETE')"
expect_master_bool notification_pii_ro_user_pii_select_user_key t \
  "select has_column_privilege('${DB_NOTIFICATION_PII_RO_USERNAME}', 'youth_welfare_pii.user_pii', 'user_key', 'SELECT')"
expect_master_bool notification_pii_ro_user_pii_select_email_enc t \
  "select has_column_privilege('${DB_NOTIFICATION_PII_RO_USERNAME}', 'youth_welfare_pii.user_pii', 'email_enc', 'SELECT')"
expect_master_bool notification_pii_ro_user_pii_select_name_enc f \
  "select has_column_privilege('${DB_NOTIFICATION_PII_RO_USERNAME}', 'youth_welfare_pii.user_pii', 'name_enc', 'SELECT')"
expect_master_bool notification_pii_ro_user_pii_select_birth_date_enc f \
  "select has_column_privilege('${DB_NOTIFICATION_PII_RO_USERNAME}', 'youth_welfare_pii.user_pii', 'birth_date_enc', 'SELECT')"
expect_master_bool notification_pii_ro_user_pii_insert f \
  "select has_table_privilege('${DB_NOTIFICATION_PII_RO_USERNAME}', 'youth_welfare_pii.user_pii', 'INSERT')"

if [[ "${PRINT_SUMMARY}" == "true" ]]; then
  echo "rds runtime privilege verification passed"
  echo "env_file=${ENV_FILE}"
  echo "db_host=${DB_HOST}"
  echo "db_port=${DB_PORT}"
  echo "db_name=${DB_NAME}"
fi
