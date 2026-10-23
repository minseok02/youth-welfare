#!/usr/bin/env bash
set -euo pipefail

# Existing local DB only. Does not source env files, start containers or run the other runtime patches.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PATCH_FILE="${ROOT_DIR}/deploy/postgres/patches/V2026_10_23_01__add_collect_list_diff_snapshots.sql"
mode="${1:---check}"
if [[ $# -gt 1 || ( "${mode}" != "--check" && "${mode}" != "--apply" ) ]]; then
  echo "usage: bash deploy/postgres/apply-local-collect-snapshot-patch.sh [--check|--apply]" >&2
  exit 2
fi

if [[ "${mode}" == "--apply" && ( "${ALLOW_LOCAL_DOCKER_DB:-false}" != "true" || "${SNAPSHOT_BACKUP_CONFIRMED:-false}" != "true" ) ]]; then
  echo "apply requires ALLOW_LOCAL_DOCKER_DB=true and SNAPSHOT_BACKUP_CONFIRMED=true after target/backup review" >&2
  exit 2
fi

app_role="${SNAPSHOT_APP_ROLE:-app_core_rw}"
query_role="${SNAPSHOT_QUERY_ROLE:-admin_dashboard_ro}"
if [[ ! "${app_role}" =~ ^[a-z_][a-z0-9_]{0,62}$ || ! "${query_role}" =~ ^[a-z_][a-z0-9_]{0,62}$ || "${app_role}" == "${query_role}" ]]; then
  echo "snapshot app/query roles must be distinct lowercase PostgreSQL identifiers (max 63 characters)" >&2
  exit 2
fi
if [[ ! -f "${PATCH_FILE}" ]]; then
  echo "snapshot patch file is missing" >&2
  exit 1
fi

local_docker() {
  env -u DOCKER_HOST -u DOCKER_CONTEXT docker --host unix:///var/run/docker.sock "$@"
}

container_state="$(local_docker inspect --format '{{.State.Running}}|{{index .Config.Labels "com.youth-welfare.local-db-guard"}}|{{index .Config.Labels "com.docker.compose.service"}}' youth-welfare-db)"
if [[ "${container_state}" != "true|true|db" ]]; then
  echo "expected running local guarded Compose db container youth-welfare-db; refusing PostgreSQL execution" >&2
  exit 1
fi

connection_role="${query_role}"
check_only=true
if [[ "${mode}" == "--apply" ]]; then
  connection_role=migration_admin
  check_only=false
fi

echo "target=local-docker/youth-welfare-db database=youth_welfare mode=${mode#--}"
local_docker exec -i youth-welfare-db psql -X --no-password \
  --host /var/run/postgresql --port 5432 --username "${connection_role}" --dbname youth_welfare \
  --set ON_ERROR_STOP=1 --set "snapshot_check_only=${check_only}" \
  --set "app_username=${app_role}" --set "admin_ro_username=${query_role}" < "${PATCH_FILE}"

if [[ "${mode}" == "--apply" ]]; then
  echo "local snapshot patch applied; collection/runtime behavior still requires separate verification"
fi
