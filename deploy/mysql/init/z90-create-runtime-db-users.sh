#!/bin/bash
set -euo pipefail

mysql_root_password="${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
app_username="${MYSQL_APP_USERNAME:-app_core_rw}"
app_password="${MYSQL_APP_PASSWORD:-$mysql_root_password}"
migration_username="${MYSQL_MIGRATION_USERNAME:-migration_admin}"
migration_password="${MYSQL_MIGRATION_PASSWORD:-$mysql_root_password}"
app_pii_username="${MYSQL_APP_PII_USERNAME:-app_pii_rw}"
app_pii_password="${MYSQL_APP_PII_PASSWORD:-$mysql_root_password}"
notification_ro_username="${MYSQL_NOTIFICATION_PII_RO_USERNAME:-notification_pii_ro}"
notification_ro_password="${MYSQL_NOTIFICATION_PII_RO_PASSWORD:-$mysql_root_password}"

sql_escape() {
  printf "%s" "$1" | sed "s/'/''/g"
}

app_username_esc="$(sql_escape "$app_username")"
app_password_esc="$(sql_escape "$app_password")"
migration_username_esc="$(sql_escape "$migration_username")"
migration_password_esc="$(sql_escape "$migration_password")"
app_pii_username_esc="$(sql_escape "$app_pii_username")"
app_pii_password_esc="$(sql_escape "$app_pii_password")"
notification_ro_username_esc="$(sql_escape "$notification_ro_username")"
notification_ro_password_esc="$(sql_escape "$notification_ro_password")"

mysql --protocol=socket -uroot "-p${mysql_root_password}" <<SQL
CREATE USER IF NOT EXISTS '${app_username_esc}'@'%' IDENTIFIED BY '${app_password_esc}';
CREATE USER IF NOT EXISTS '${migration_username_esc}'@'%' IDENTIFIED BY '${migration_password_esc}';
CREATE USER IF NOT EXISTS '${app_pii_username_esc}'@'%' IDENTIFIED BY '${app_pii_password_esc}';
CREATE USER IF NOT EXISTS '${notification_ro_username_esc}'@'%' IDENTIFIED BY '${notification_ro_password_esc}';

GRANT SELECT, INSERT, UPDATE, DELETE ON youth_welfare.* TO '${app_username_esc}'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON youth_welfare_pii.user_pii TO '${app_username_esc}'@'%';

GRANT SELECT, INSERT, UPDATE, DELETE ON youth_welfare_pii.user_pii TO '${app_pii_username_esc}'@'%';
GRANT SELECT (user_key, email_enc) ON youth_welfare_pii.user_pii TO '${notification_ro_username_esc}'@'%';

GRANT ALL PRIVILEGES ON youth_welfare.* TO '${migration_username_esc}'@'%';
GRANT ALL PRIVILEGES ON youth_welfare_pii.* TO '${migration_username_esc}'@'%';

FLUSH PRIVILEGES;
SQL
