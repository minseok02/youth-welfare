#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-/home/ubuntu/youth-welfare}"
OPS_ENV_FILE="${OPS_ENV_FILE:-${HOME:-/home/ubuntu}/.config/youth-welfare/ops.env}"
ENV_FILE="${ENV_FILE:-.env.production}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.elasticache.yml}"
APP_HEALTH_URL="${APP_HEALTH_URL:-http://127.0.0.1:8082/actuator/health}"
MARK_BEGIN="# >>> youth-welfare basic ops >>>"
MARK_END="# <<< youth-welfare basic ops <<<"

tmp="$(mktemp)"
trap 'rm -f "${tmp}"' EXIT

crontab -l 2>/dev/null | sed "/${MARK_BEGIN}/,/${MARK_END}/d" > "${tmp}" || true

cat >> "${tmp}" <<EOF
${MARK_BEGIN}
*/2 * * * * ROOT_DIR='${ROOT_DIR}' OPS_ENV_FILE='${OPS_ENV_FILE}' ENV_FILE='${ENV_FILE}' COMPOSE_FILE='${COMPOSE_FILE}' APP_HEALTH_URL='${APP_HEALTH_URL}' bash '${ROOT_DIR}/deploy/ops/app-watchdog.sh'
20 3 * * * RUN_WHEN_USED_PERCENT_GE=75 PRUNE_VOLUMES=false bash '${ROOT_DIR}/deploy/ops/docker-prune-safe.sh'
${MARK_END}
EOF

crontab "${tmp}"
echo "installed youth-welfare basic ops cron"
