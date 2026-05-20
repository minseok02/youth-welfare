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

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${migration_username}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', '${migration_username}', '${migration_password}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', '${migration_username}', '${migration_password}');
    END IF;
END
\$\$;

GRANT CONNECT ON DATABASE ${db_name} TO ${app_username}, ${app_pii_username}, ${notification_ro_username}, ${admin_ro_username}, ${migration_username};

GRANT USAGE ON SCHEMA public TO ${app_username}, ${admin_ro_username}, ${migration_username};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ${app_username};
GRANT SELECT ON ALL TABLES IN SCHEMA public TO ${admin_ro_username};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ${app_username};

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
