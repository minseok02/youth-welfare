#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-/home/ubuntu/youth-welfare}"
DISK_PATH="${DISK_PATH:-/}"
RUN_WHEN_USED_PERCENT_GE="${RUN_WHEN_USED_PERCENT_GE:-70}"
FORCE="${FORCE:-false}"
DRY_RUN="${DRY_RUN:-true}"

PERFORMANCE_ARTIFACT_ROOT="${PERFORMANCE_ARTIFACT_ROOT:-${ROOT_DIR}/tmp/performance}"
PERFORMANCE_RETENTION_DAYS="${PERFORMANCE_RETENTION_DAYS:-2}"
CLEAN_PERFORMANCE_ARTIFACTS="${CLEAN_PERFORMANCE_ARTIFACTS:-true}"

STABILITY_ARTIFACT_ROOT="${STABILITY_ARTIFACT_ROOT:-${ROOT_DIR}/tmp/stability}"
STABILITY_RETENTION_DAYS="${STABILITY_RETENTION_DAYS:-14}"
CLEAN_STABILITY_ARTIFACTS="${CLEAN_STABILITY_ARTIFACTS:-false}"

NPM_CACHE_DIR="${NPM_CACHE_DIR:-${HOME:-/home/ubuntu}/.npm}"
CLEAN_NPM_CACHE="${CLEAN_NPM_CACHE:-true}"

CLEAN_DOCKER_BUILDER_CACHE="${CLEAN_DOCKER_BUILDER_CACHE:-true}"
DOCKER_BUILDER_PRUNE_UNTIL="${DOCKER_BUILDER_PRUNE_UNTIL:-168h}"

log() {
  printf '[%s] %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*"
}

is_true() {
  [[ "${1}" == "true" || "${1}" == "1" || "${1}" == "yes" ]]
}

require_non_negative_int() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    log "ERROR ${name} must be a non-negative integer: ${value}"
    exit 2
  fi
}

real_path() {
  readlink -m "$1"
}

used_percent() {
  df -P "${DISK_PATH}" | awk 'NR == 2 { gsub("%", "", $5); print $5 }'
}

show_usage() {
  log "disk $(df -h "${DISK_PATH}" | awk 'NR == 2 { print $3 " used / " $4 " available / " $5 " used" }')"
  for path in "${PERFORMANCE_ARTIFACT_ROOT}" "${STABILITY_ARTIFACT_ROOT}" "${NPM_CACHE_DIR}"; do
    if [[ -e "${path}" ]]; then
      du -sh "${path}" 2>/dev/null || true
    fi
  done
  if command -v docker >/dev/null 2>&1; then
    docker system df 2>/dev/null || true
  fi
}

delete_path_under_root() {
  local path="$1"
  local root="$2"
  local root_real path_real
  root_real="$(real_path "${root}")"
  path_real="$(real_path "${path}")"

  case "${path_real}" in
    "${root_real}"/*) ;;
    *)
      log "ERROR refusing to delete outside ${root_real}: ${path_real}"
      exit 3
      ;;
  esac

  if is_true "${DRY_RUN}"; then
    log "dry-run delete ${path_real}"
    return
  fi

  if [[ -d "${path_real}" ]]; then
    find "${path_real}" -mindepth 1 -depth -delete
    rmdir "${path_real}"
  else
    rm -f "${path_real}"
  fi
  log "deleted ${path_real}"
}

clean_expired_children() {
  local root="$1"
  local retention_days="$2"
  local label="$3"

  require_non_negative_int "${label}_RETENTION_DAYS" "${retention_days}"

  if [[ ! -d "${root}" ]]; then
    log "skip ${label}: missing ${root}"
    return
  fi

  log "scan ${label}: root=${root} retention_days=${retention_days}"
  find "${root}" -mindepth 1 -maxdepth 1 -mtime +"${retention_days}" -print0 |
    while IFS= read -r -d '' child; do
      delete_path_under_root "${child}" "${root}"
    done
}

run_npm_cache_cleanup() {
  if [[ ! -d "${NPM_CACHE_DIR}" ]]; then
    log "skip npm cache: missing ${NPM_CACHE_DIR}"
    return
  fi
  if ! command -v npm >/dev/null 2>&1; then
    log "skip npm cache: npm command not found"
    return
  fi
  if is_true "${DRY_RUN}"; then
    log "dry-run npm cache clean --force (${NPM_CACHE_DIR})"
    return
  fi
  npm cache clean --force
  log "cleaned npm cache ${NPM_CACHE_DIR}"
}

run_docker_builder_cleanup() {
  if ! command -v docker >/dev/null 2>&1; then
    log "skip docker builder cache: docker command not found"
    return
  fi
  if is_true "${DRY_RUN}"; then
    log "dry-run docker builder prune -af --filter until=${DOCKER_BUILDER_PRUNE_UNTIL}"
    return
  fi
  docker builder prune -af --filter "until=${DOCKER_BUILDER_PRUNE_UNTIL}"
  log "cleaned docker builder cache until=${DOCKER_BUILDER_PRUNE_UNTIL}"
}

main() {
  require_non_negative_int RUN_WHEN_USED_PERCENT_GE "${RUN_WHEN_USED_PERCENT_GE}"

  local before_used
  before_used="$(used_percent)"
  before_used="${before_used:-0}"

  log "begin dry_run=${DRY_RUN} force=${FORCE} used_percent=${before_used} threshold=${RUN_WHEN_USED_PERCENT_GE}"
  show_usage

  if ! is_true "${FORCE}" && (( before_used < RUN_WHEN_USED_PERCENT_GE )); then
    log "skip cleanup: used_percent=${before_used} below threshold=${RUN_WHEN_USED_PERCENT_GE}"
    exit 0
  fi

  if is_true "${CLEAN_PERFORMANCE_ARTIFACTS}"; then
    clean_expired_children "${PERFORMANCE_ARTIFACT_ROOT}" "${PERFORMANCE_RETENTION_DAYS}" "performance"
  fi

  if is_true "${CLEAN_STABILITY_ARTIFACTS}"; then
    clean_expired_children "${STABILITY_ARTIFACT_ROOT}" "${STABILITY_RETENTION_DAYS}" "stability"
  fi

  if is_true "${CLEAN_NPM_CACHE}"; then
    run_npm_cache_cleanup
  fi

  if is_true "${CLEAN_DOCKER_BUILDER_CACHE}"; then
    run_docker_builder_cleanup
  fi

  log "after cleanup"
  show_usage
}

main "$@"
