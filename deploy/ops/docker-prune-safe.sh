#!/usr/bin/env bash
set -euo pipefail

LOG_DIR="${LOG_DIR:-/var/log/youth-welfare/ops}"
LOG_FILE="${LOG_FILE:-${LOG_DIR}/docker-prune-safe.log}"
DISK_PATH="${DISK_PATH:-/}"
RUN_WHEN_USED_PERCENT_GE="${RUN_WHEN_USED_PERCENT_GE:-75}"
PRUNE_VOLUMES="${PRUNE_VOLUMES:-false}"

mkdir -p "${LOG_DIR}"

log_line() {
  printf '[%s] %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*" >> "${LOG_FILE}"
}

used_percent="$(df -P "${DISK_PATH}" | awk 'NR == 2 { gsub("%", "", $5); print $5 }')"
used_percent="${used_percent:-0}"

if (( used_percent < RUN_WHEN_USED_PERCENT_GE )); then
  log_line "skip used_percent=${used_percent} threshold=${RUN_WHEN_USED_PERCENT_GE}"
  exit 0
fi

log_line "begin used_percent=${used_percent} prune_volumes=${PRUNE_VOLUMES}"
docker system df >> "${LOG_FILE}" 2>&1 || true

if [[ "${PRUNE_VOLUMES}" == "true" ]]; then
  docker system prune -af --volumes >> "${LOG_FILE}" 2>&1
else
  docker system prune -af >> "${LOG_FILE}" 2>&1
fi

docker builder prune -af >> "${LOG_FILE}" 2>&1 || true
docker system df >> "${LOG_FILE}" 2>&1 || true
log_line "done used_percent_after=$(df -P "${DISK_PATH}" | awk 'NR == 2 { gsub("%", "", $5); print $5 }')"
