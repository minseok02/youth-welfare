#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env.production}"

if [[ "${ENV_FILE}" != /* ]]; then
  ENV_FILE="${ROOT_DIR}/${ENV_FILE}"
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "env file not found: ${ENV_FILE}" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
. "${ENV_FILE}"
set +a

if [[ -z "${DB_URL:-}" ]]; then
  echo "DB_URL is required" >&2
  exit 1
fi
if [[ -z "${DB_MIGRATION_USERNAME:-}" || -z "${DB_MIGRATION_PASSWORD:-}" ]]; then
  echo "DB_MIGRATION_USERNAME and DB_MIGRATION_PASSWORD are required" >&2
  exit 1
fi

if ! command -v psql >/dev/null 2>&1; then
  echo "psql command not found" >&2
  exit 1
fi

echo "AUDIT_OPERATIONAL_DB_STATE"
echo "env_file=${ENV_FILE}"

PGPASSWORD="${DB_MIGRATION_PASSWORD}" psql "${DB_URL#jdbc:}" \
  -U "${DB_MIGRATION_USERNAME}" \
  -X \
  -v ON_ERROR_STOP=1 <<'SQL'
\pset pager off
\pset format aligned

select current_database() as db, current_user as db_user, version() as postgres_version;

with expected_tables(table_schema, table_name) as (
  values
    ('public','auth_users'),
    ('public','users'),
    ('public','user_profiles'),
    ('public','user_pii_sync_queue'),
    ('public','chat_retrieval_snapshots'),
    ('public','chat_sessions'),
    ('public','notification_attempt_logs'),
    ('public','policy_error_reports'),
    ('public','policy_duplicate_review_records'),
    ('public','policy_link_review_records'),
    ('public','collect_execution_locks'),
    ('public','schema_migration_history'),
    ('youth_welfare_pii','user_pii')
)
select 'table_presence' as section,
       e.table_schema || '.' || e.table_name as item,
       case when t.table_name is null then 'MISSING' else 'OK' end as status
from expected_tables e
left join information_schema.tables t on t.table_schema=e.table_schema and t.table_name=e.table_name
order by e.table_schema, e.table_name;

select 'db_integrity' as section, 'auth_without_users' as metric, count(*)::text as value
from auth_users au left join users u on u.user_key=au.user_key where u.user_key is null
union all
select 'db_integrity', 'profiles_without_users', count(*)::text
from user_profiles up left join users u on u.user_key=up.user_key where u.user_key is null
union all
select 'db_integrity', 'pii_without_users', count(*)::text
from youth_welfare_pii.user_pii p left join users u on u.user_key=p.user_key where u.user_key is null
union all
select 'db_integrity', 'users_without_auth', count(*)::text
from users u left join auth_users au on au.user_key=u.user_key where au.user_key is null
union all
select 'db_integrity', 'users_without_pii', count(*)::text
from users u left join youth_welfare_pii.user_pii p on p.user_key=u.user_key where p.user_key is null
union all
select 'db_integrity', 'chat_snapshots_nonnull_orphan_session', count(*)::text
from chat_retrieval_snapshots crs left join chat_sessions cs on cs.id=crs.session_id
where crs.session_id is not null and cs.id is null
union all
select 'db_integrity', 'user_pii_sync_pending_or_failed', count(*)::text
from user_pii_sync_queue where status in ('PENDING','FAILED');

select 'ops_queue' as section, 'unread_user_alerts' as metric, count(*)::text as value
from user_alerts where status='UNREAD'
union all
select 'ops_queue', 'notification_failed_like', count(*)::text
from notification_attempt_logs
where lower(outcome) in ('failed','retryable_failed','terminal_failed','send_failed','error')
   or error_type is not null
union all
select 'ops_queue', 'policy_error_reports_open', count(*)::text
from policy_error_reports where status='OPEN'
union all
select 'ops_queue', 'policy_duplicate_reviews_unreviewed', count(*)::text
from policy_duplicate_review_records where reviewed_at is null
union all
select 'ops_queue', 'policy_link_reviews_unreviewed', count(*)::text
from policy_link_review_records where reviewed_at is null
union all
select 'ops_queue', 'collect_locks_active', count(*)::text
from collect_execution_locks where locked_until > now();

select 'db_runtime' as section, 'active_queries_over_5m' as metric, count(*)::text as value
from pg_stat_activity
where state='active' and pid <> pg_backend_pid() and now() - query_start > interval '5 minutes'
union all
select 'db_runtime', 'waiting_locks', count(*)::text
from pg_locks where not granted
union all
select 'db_runtime', 'idle_in_transaction_over_5m', count(*)::text
from pg_stat_activity
where state='idle in transaction' and now() - state_change > interval '5 minutes'
union all
select 'db_runtime', 'current_connections', count(*)::text
from pg_stat_activity where datname = current_database()
union all
select 'db_runtime', 'max_connections', setting
from pg_settings where name='max_connections'
union all
select 'db_runtime', 'database_size', pg_size_pretty(pg_database_size(current_database()));

select 'schema_contract' as section,
       'fk_crs_session' as item,
       coalesce(max(pg_get_constraintdef(oid)), 'MISSING') as status
from pg_constraint
where conname='fk_crs_session'
union all
select 'schema_contract',
       'fk_auth_users_user_key',
       coalesce(max(pg_get_constraintdef(oid)), 'MISSING')
from pg_constraint
where conname='fk_auth_users_user_key'
union all
select 'schema_contract',
       'fk_user_profiles_user_key',
       coalesce(max(pg_get_constraintdef(oid)), 'MISSING')
from pg_constraint
where conname='fk_user_profiles_user_key'
union all
select 'schema_contract',
       'fk_user_pii_user_key',
       coalesce(max(pg_get_constraintdef(oid)), 'MISSING')
from pg_constraint
where conname='fk_user_pii_user_key'
union all
select 'schema_contract',
       'idx_crs_session_id',
       case when count(*) = 1 then 'OK' else 'MISSING' end
from pg_indexes
where schemaname='public' and tablename='chat_retrieval_snapshots' and indexname='idx_crs_session_id'
union all
select 'schema_contract',
       'idx_upsq_status_synced',
       case when count(*) = 1 then 'OK' else 'MISSING' end
from pg_indexes
where schemaname='public' and tablename='user_pii_sync_queue' and indexname='idx_upsq_status_synced';

select 'schema_migration_history' as section,
       case when to_regclass('public.schema_migration_history') is null then 'MISSING' else 'OK' end as status;
SQL
