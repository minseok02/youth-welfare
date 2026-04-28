#!/usr/bin/env bash
set -euo pipefail
set +H

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-${ROOT_DIR}/docker-compose.yml}"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env}"
DB_SERVICE="${DB_SERVICE:-db}"
DB_CONTAINER_NAME="${DB_CONTAINER_NAME:-youth-welfare-db}"
RECOVERY_CONTAINER_NAME="${RECOVERY_CONTAINER_NAME:-youth-welfare-db-recovery}"
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.0}"
WAIT_SECONDS="${WAIT_SECONDS:-60}"
PRESERVE_EXISTING_ENV="${PRESERVE_EXISTING_ENV:-false}"
DB_WAS_STOPPED=false
PRIMARY_DB_RESTARTED=false

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

    if [[ "${PRESERVE_EXISTING_ENV}" == "true" && -n "${!key+x}" ]]; then
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

sql_escape() {
  printf "%s" "$1" | sed "s/'/''/g"
}

wait_for_mysql() {
  local container_name="$1"
  local mysql_user="$2"
  local mysql_password="$3"
  local started_at now

  started_at="$(date +%s)"
  while true; do
    if docker exec -e MYSQL_PWD="${mysql_password}" "${container_name}" \
      mysql -u"${mysql_user}" -e "SELECT 1" >/dev/null 2>&1; then
      return 0
    fi

    now="$(date +%s)"
    if (( now - started_at >= WAIT_SECONDS )); then
      echo "timed out waiting for mysql container=${container_name} user=${mysql_user}" >&2
      return 1
    fi
    sleep 1
  done
}

wait_for_recovery_mysql() {
  local started_at now

  started_at="$(date +%s)"
  while true; do
    if docker exec "${RECOVERY_CONTAINER_NAME}" mysql -uroot -e "SELECT 1" >/dev/null 2>&1; then
      return 0
    fi

    now="$(date +%s)"
    if (( now - started_at >= WAIT_SECONDS )); then
      echo "timed out waiting for recovery mysql" >&2
      return 1
    fi
    sleep 1
  done
}

restart_primary_db() {
  docker rm -f "${RECOVERY_CONTAINER_NAME}" >/dev/null 2>&1 || true
  docker compose -f "${COMPOSE_FILE}" up -d "${DB_SERVICE}" >/dev/null
  PRIMARY_DB_RESTARTED=true
}

cleanup() {
  if docker ps -a --format '{{.Names}}' | grep -qx "${RECOVERY_CONTAINER_NAME}"; then
    docker rm -f "${RECOVERY_CONTAINER_NAME}" >/dev/null 2>&1 || true
  fi
  if [[ "${DB_WAS_STOPPED}" == "true" && "${PRIMARY_DB_RESTARTED}" == "false" ]]; then
    docker compose -f "${COMPOSE_FILE}" up -d "${DB_SERVICE}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

load_env_file

require_non_empty DB_PASSWORD "${DB_PASSWORD:-}"

APP_CORE_PASSWORD="${DB_PASSWORD}"
ROOT_PASSWORD="${DB_PASSWORD}"
MIGRATION_PASSWORD="${DB_MIGRATION_PASSWORD:-${DB_PASSWORD}}"
APP_PII_PASSWORD="${DB_APP_PII_PASSWORD:-${DB_PASSWORD}}"
NOTIFICATION_RO_PASSWORD="${DB_NOTIFICATION_PII_RO_PASSWORD:-${DB_PASSWORD}}"

APP_CORE_USERNAME="${TARGET_DB_USERNAME:-app_core_rw}"
MIGRATION_USERNAME="${TARGET_DB_MIGRATION_USERNAME:-migration_admin}"
APP_PII_USERNAME="${TARGET_DB_APP_PII_USERNAME:-app_pii_rw}"
NOTIFICATION_RO_USERNAME="${TARGET_DB_NOTIFICATION_PII_RO_USERNAME:-notification_pii_ro}"

MYSQL_VOLUME="${MYSQL_VOLUME:-$(docker inspect "${DB_CONTAINER_NAME}" --format '{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{println .Name}}{{end}}{{end}}' | tr -d '\r')}"
require_non_empty MYSQL_VOLUME "${MYSQL_VOLUME}"

echo "stopping primary db container for local account reconcile: ${DB_SERVICE}"
docker compose -f "${COMPOSE_FILE}" stop "${DB_SERVICE}" >/dev/null
DB_WAS_STOPPED=true
docker rm -f "${RECOVERY_CONTAINER_NAME}" >/dev/null 2>&1 || true

echo "starting recovery mysql against volume ${MYSQL_VOLUME}"
docker run -d --rm \
  --name "${RECOVERY_CONTAINER_NAME}" \
  -e MYSQL_ALLOW_EMPTY_PASSWORD=yes \
  -v "${MYSQL_VOLUME}:/var/lib/mysql" \
  "${MYSQL_IMAGE}" \
  --skip-grant-tables \
  --skip-networking=0 \
  --skip-host-cache >/dev/null

wait_for_recovery_mysql

root_password_esc="$(sql_escape "${ROOT_PASSWORD}")"
app_core_username_esc="$(sql_escape "${APP_CORE_USERNAME}")"
app_core_password_esc="$(sql_escape "${APP_CORE_PASSWORD}")"
migration_username_esc="$(sql_escape "${MIGRATION_USERNAME}")"
migration_password_esc="$(sql_escape "${MIGRATION_PASSWORD}")"
app_pii_username_esc="$(sql_escape "${APP_PII_USERNAME}")"
app_pii_password_esc="$(sql_escape "${APP_PII_PASSWORD}")"
notification_ro_username_esc="$(sql_escape "${NOTIFICATION_RO_USERNAME}")"
notification_ro_password_esc="$(sql_escape "${NOTIFICATION_RO_PASSWORD}")"

echo "reconciling local runtime accounts"
docker exec -i "${RECOVERY_CONTAINER_NAME}" mysql -uroot <<SQL
FLUSH PRIVILEGES;

ALTER USER IF EXISTS 'root'@'localhost' IDENTIFIED BY '${root_password_esc}';
ALTER USER IF EXISTS 'root'@'%' IDENTIFIED BY '${root_password_esc}';

FLUSH PRIVILEGES;
SQL

echo "restarting primary db container"
restart_primary_db
wait_for_mysql "${DB_CONTAINER_NAME}" root "${ROOT_PASSWORD}"

echo "applying runtime account grants on primary db"
docker exec -i -e MYSQL_PWD="${ROOT_PASSWORD}" "${DB_CONTAINER_NAME}" mysql -uroot <<SQL
CREATE USER IF NOT EXISTS '${app_core_username_esc}'@'%' IDENTIFIED BY '${app_core_password_esc}';
CREATE USER IF NOT EXISTS '${migration_username_esc}'@'%' IDENTIFIED BY '${migration_password_esc}';
CREATE USER IF NOT EXISTS '${app_pii_username_esc}'@'%' IDENTIFIED BY '${app_pii_password_esc}';
CREATE USER IF NOT EXISTS '${notification_ro_username_esc}'@'%' IDENTIFIED BY '${notification_ro_password_esc}';

ALTER USER '${app_core_username_esc}'@'%' IDENTIFIED BY '${app_core_password_esc}';
ALTER USER '${migration_username_esc}'@'%' IDENTIFIED BY '${migration_password_esc}';
ALTER USER '${app_pii_username_esc}'@'%' IDENTIFIED BY '${app_pii_password_esc}';
ALTER USER '${notification_ro_username_esc}'@'%' IDENTIFIED BY '${notification_ro_password_esc}';

REVOKE ALL PRIVILEGES, GRANT OPTION FROM '${app_core_username_esc}'@'%';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '${migration_username_esc}'@'%';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '${app_pii_username_esc}'@'%';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '${notification_ro_username_esc}'@'%';

GRANT SELECT, INSERT, UPDATE, DELETE ON youth_welfare.* TO '${app_core_username_esc}'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON youth_welfare_pii.user_pii TO '${app_pii_username_esc}'@'%';
GRANT SELECT (user_key, email_enc) ON youth_welfare_pii.user_pii TO '${notification_ro_username_esc}'@'%';
GRANT ALL PRIVILEGES ON youth_welfare.* TO '${migration_username_esc}'@'%';
GRANT ALL PRIVILEGES ON youth_welfare_pii.* TO '${migration_username_esc}'@'%';

FLUSH PRIVILEGES;
SQL

docker exec -e MYSQL_PWD="${ROOT_PASSWORD}" "${DB_CONTAINER_NAME}" \
  mysql -uroot -e "SHOW GRANTS FOR '${APP_CORE_USERNAME}'@'%'; SHOW GRANTS FOR '${APP_PII_USERNAME}'@'%'; SHOW GRANTS FOR '${NOTIFICATION_RO_USERNAME}'@'%'; SHOW GRANTS FOR '${MIGRATION_USERNAME}'@'%';" >/dev/null

docker run --rm --network youth-welfare_default -e MYSQL_PWD="${APP_CORE_PASSWORD}" "${MYSQL_IMAGE}" \
  mysql -hdb -u"${APP_CORE_USERNAME}" -e "SELECT 1" >/dev/null
docker run --rm --network youth-welfare_default -e MYSQL_PWD="${APP_PII_PASSWORD}" "${MYSQL_IMAGE}" \
  mysql -hdb -u"${APP_PII_USERNAME}" -e "SELECT 1" >/dev/null
docker run --rm --network youth-welfare_default -e MYSQL_PWD="${NOTIFICATION_RO_PASSWORD}" "${MYSQL_IMAGE}" \
  mysql -hdb -u"${NOTIFICATION_RO_USERNAME}" -e "SELECT 1" >/dev/null
docker run --rm --network youth-welfare_default -e MYSQL_PWD="${MIGRATION_PASSWORD}" "${MYSQL_IMAGE}" \
  mysql -hdb -u"${MIGRATION_USERNAME}" -e "SELECT 1" >/dev/null

echo "local runtime db accounts reconciled successfully"
