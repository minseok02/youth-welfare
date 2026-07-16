#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-/home/ubuntu/youth-welfare}"
LOG_DIR="${LOG_DIR:-${ROOT_DIR}/tmp/ops/runtime-disk-cleanup}"
LOCK_FILE="${LOCK_FILE:-${LOG_DIR}/cleanup.lock}"
SCHEDULE="${SCHEDULE:-20 4 * * 0}"
RUN_WHEN_USED_PERCENT_GE="${RUN_WHEN_USED_PERCENT_GE:-70}"
PERFORMANCE_RETENTION_DAYS="${PERFORMANCE_RETENTION_DAYS:-2}"
STABILITY_RETENTION_DAYS="${STABILITY_RETENTION_DAYS:-14}"
DOCKER_BUILDER_PRUNE_UNTIL="${DOCKER_BUILDER_PRUNE_UNTIL:-168h}"
PRINT_ONLY="${PRINT_ONLY:-false}"
MARK_BEGIN="# >>> youth-welfare runtime disk cleanup >>>"
MARK_END="# <<< youth-welfare runtime disk cleanup <<<"

tmp="$(mktemp)"
trap 'rm -f "${tmp}"' EXIT

mkdir -p "${LOG_DIR}"

crontab -l 2>/dev/null | sed "/${MARK_BEGIN}/,/${MARK_END}/d" > "${tmp}" || true

cat >> "${tmp}" <<EOF
${MARK_BEGIN}
${SCHEDULE} cd '${ROOT_DIR}' && mkdir -p '${LOG_DIR}' && flock -n '${LOCK_FILE}' env ROOT_DIR='${ROOT_DIR}' DRY_RUN=false FORCE=false RUN_WHEN_USED_PERCENT_GE='${RUN_WHEN_USED_PERCENT_GE}' PERFORMANCE_RETENTION_DAYS='${PERFORMANCE_RETENTION_DAYS}' CLEAN_PERFORMANCE_ARTIFACTS=true CLEAN_STABILITY_ARTIFACTS=false STABILITY_RETENTION_DAYS='${STABILITY_RETENTION_DAYS}' CLEAN_NPM_CACHE=true CLEAN_DOCKER_BUILDER_CACHE=true DOCKER_BUILDER_PRUNE_UNTIL='${DOCKER_BUILDER_PRUNE_UNTIL}' bash '${ROOT_DIR}/deploy/ops/cleanup-runtime-disk-artifacts.sh' >> '${LOG_DIR}/cleanup-cron.log' 2>&1
${MARK_END}
EOF

if [[ "${PRINT_ONLY}" == "true" ]]; then
  cat "${tmp}"
  exit 0
fi

crontab "${tmp}"
echo "installed youth-welfare runtime disk cleanup cron"
