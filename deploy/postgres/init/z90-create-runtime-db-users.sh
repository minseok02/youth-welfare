#!/bin/sh
set -eu

db_name="${POSTGRES_DB:?POSTGRES_DB is required}"
app_username="${DB_USERNAME:-app_core_rw}"
app_password="${DB_PASSWORD:?DB_PASSWORD is required}"
app_pii_username="${DB_APP_PII_USERNAME:-app_pii_rw}"
app_pii_password="${DB_APP_PII_PASSWORD:-$app_password}"
notification_ro_username="${DB_NOTIFICATION_PII_RO_USERNAME:-notification_pii_ro}"
notification_ro_password="${DB_NOTIFICATION_PII_RO_PASSWORD:-$app_password}"
admin_ro_username="${DB_ADMIN_RO_USERNAME:-admin_dashboard_ro}"
admin_ro_password="${DB_ADMIN_RO_PASSWORD:-$app_password}"
cluster_ai_cleanup_username="${DB_CLUSTER_AI_CLEANUP_USERNAME:-cluster_ai_cleanup_rw}"
cluster_ai_cleanup_password="${DB_CLUSTER_AI_CLEANUP_PASSWORD:-$app_password}"
recommendation_retention_cleanup_username="${DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME:-recommendation_retention_cleanup_rw}"
recommendation_retention_cleanup_password="${DB_RECOMMENDATION_RETENTION_CLEANUP_PASSWORD:-$app_password}"
collect_execution_lock_cleanup_username="${DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME:-collect_execution_lock_cleanup_rw}"
collect_execution_lock_cleanup_password="${DB_COLLECT_EXECUTION_LOCK_CLEANUP_PASSWORD:-$app_password}"
web_push_subscription_cleanup_username="${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME:-web_push_subscription_cleanup_rw}"
web_push_subscription_cleanup_password="${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_PASSWORD:-$app_password}"
migration_username="${DB_MIGRATION_USERNAME:-migration_admin}"
migration_password="${DB_MIGRATION_PASSWORD:-$app_password}"

psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER:-postgres}" --dbname "${db_name}" <<SQL
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_username}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${app_username}', '${app_password}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${app_username}', '${app_password}');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_pii_username}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${app_pii_username}', '${app_pii_password}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${app_pii_username}', '${app_pii_password}');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${notification_ro_username}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${notification_ro_username}', '${notification_ro_password}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${notification_ro_username}', '${notification_ro_password}');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${admin_ro_username}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${admin_ro_username}', '${admin_ro_password}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${admin_ro_username}', '${admin_ro_password}');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${cluster_ai_cleanup_username}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${cluster_ai_cleanup_username}', '${cluster_ai_cleanup_password}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${cluster_ai_cleanup_username}', '${cluster_ai_cleanup_password}');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${recommendation_retention_cleanup_username}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${recommendation_retention_cleanup_username}', '${recommendation_retention_cleanup_password}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${recommendation_retention_cleanup_username}', '${recommendation_retention_cleanup_password}');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${collect_execution_lock_cleanup_username}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${collect_execution_lock_cleanup_username}', '${collect_execution_lock_cleanup_password}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${collect_execution_lock_cleanup_username}', '${collect_execution_lock_cleanup_password}');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${web_push_subscription_cleanup_username}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${web_push_subscription_cleanup_username}', '${web_push_subscription_cleanup_password}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${web_push_subscription_cleanup_username}', '${web_push_subscription_cleanup_password}');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${migration_username}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${migration_username}', '${migration_password}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${migration_username}', '${migration_password}');
    END IF;
END
\$\$;

GRANT CONNECT ON DATABASE ${db_name} TO ${app_username}, ${app_pii_username}, ${notification_ro_username}, ${admin_ro_username}, ${cluster_ai_cleanup_username}, ${recommendation_retention_cleanup_username}, ${collect_execution_lock_cleanup_username}, ${web_push_subscription_cleanup_username}, ${migration_username};

GRANT USAGE ON SCHEMA public TO ${app_username}, ${admin_ro_username}, ${cluster_ai_cleanup_username}, ${recommendation_retention_cleanup_username}, ${collect_execution_lock_cleanup_username}, ${web_push_subscription_cleanup_username}, ${migration_username};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ${app_username};
GRANT SELECT ON ALL TABLES IN SCHEMA public TO ${admin_ro_username};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ${app_username};

DO \$\$
BEGIN
    IF to_regclass('public.cluster_ai_results') IS NOT NULL THEN
        EXECUTE format('GRANT DELETE ON TABLE public.cluster_ai_results TO %I', '${cluster_ai_cleanup_username}');
        EXECUTE format('GRANT SELECT (created_at) ON TABLE public.cluster_ai_results TO %I', '${cluster_ai_cleanup_username}');
    END IF;
END
\$\$;

DO \$\$
BEGIN
    IF to_regclass('public.user_recommendations') IS NOT NULL THEN
        EXECUTE format('GRANT DELETE ON TABLE public.user_recommendations TO %I', '${recommendation_retention_cleanup_username}');
        EXECUTE format('GRANT SELECT (recommended_at, is_bookmarked) ON TABLE public.user_recommendations TO %I', '${recommendation_retention_cleanup_username}');
    END IF;
END
\$\$;

DO \$\$
BEGIN
    IF to_regclass('public.collect_execution_locks') IS NOT NULL THEN
        EXECUTE format('GRANT DELETE ON TABLE public.collect_execution_locks TO %I', '${collect_execution_lock_cleanup_username}');
        EXECUTE format('GRANT SELECT (lock_name, owner_token) ON TABLE public.collect_execution_locks TO %I', '${collect_execution_lock_cleanup_username}');
    END IF;
END
\$\$;

DO \$\$
BEGIN
    IF to_regclass('public.web_push_subscriptions') IS NOT NULL THEN
        EXECUTE format('GRANT DELETE ON TABLE public.web_push_subscriptions TO %I', '${web_push_subscription_cleanup_username}');
        EXECUTE format('GRANT SELECT (id, user_key) ON TABLE public.web_push_subscriptions TO %I', '${web_push_subscription_cleanup_username}');
    END IF;
END
\$\$;

GRANT USAGE ON SCHEMA youth_welfare_pii TO ${app_pii_username}, ${notification_ro_username}, ${migration_username};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA youth_welfare_pii TO ${app_pii_username};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA youth_welfare_pii TO ${app_pii_username};
GRANT SELECT (user_key, email_enc) ON youth_welfare_pii.user_pii TO ${notification_ro_username};

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO ${migration_username};
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA youth_welfare_pii TO ${migration_username};
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO ${migration_username};
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA youth_welfare_pii TO ${migration_username};

ALTER DEFAULT PRIVILEGES FOR ROLE ${migration_username} IN SCHEMA public
GRANT SELECT ON TABLES TO ${admin_ro_username};
SQL
