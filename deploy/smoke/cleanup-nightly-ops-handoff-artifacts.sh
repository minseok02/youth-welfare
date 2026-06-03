#!/usr/bin/env bash
set -euo pipefail

NIGHTLY_OPS_HANDOFF_LOG_ROOT="${NIGHTLY_OPS_HANDOFF_LOG_ROOT:-/var/log/youth-welfare/nightly-ops-handoff}"
SUMMARY_RETENTION_DAYS="${SUMMARY_RETENTION_DAYS:-30}"
ARTIFACT_RETENTION_DAYS="${ARTIFACT_RETENTION_DAYS:-14}"
DRY_RUN="${DRY_RUN:-false}"

ARTIFACT_ROOT="${NIGHTLY_OPS_HANDOFF_LOG_ROOT}/artifacts"

require_positive_integer() {
  local name="$1"
  local value="$2"
  if [[ ! "${value}" =~ ^[0-9]+$ ]] || [[ "${value}" -le 0 ]]; then
    echo "${name} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

remove_path() {
  local path="$1"
  if [[ "${DRY_RUN}" == "true" ]]; then
    echo "DRY_RUN remove ${path}"
    return 0
  fi

  rm -rf -- "${path}"
  echo "removed ${path}"
}

cleanup_summary_files() {
  local found=0
  while IFS= read -r path; do
    found=1
    remove_path "${path}"
  done < <(find "${NIGHTLY_OPS_HANDOFF_LOG_ROOT}" -maxdepth 1 -type f \( -name 'nightly-summary-*.log' -o -name '*-cron.log' \) -mtime +"${SUMMARY_RETENTION_DAYS}")

  if [[ "${found}" -eq 0 ]]; then
    echo "no expired summary/runtime logs"
  fi
}

cleanup_artifact_dirs() {
  local found=0
  if [[ ! -d "${ARTIFACT_ROOT}" ]]; then
    echo "artifact root missing: ${ARTIFACT_ROOT}"
    return 0
  fi

  while IFS= read -r path; do
    found=1
    remove_path "${path}"
  done < <(find "${ARTIFACT_ROOT}" -mindepth 1 -maxdepth 1 -type d -mtime +"${ARTIFACT_RETENTION_DAYS}")

  if [[ "${found}" -eq 0 ]]; then
    echo "no expired artifact dirs"
  fi
}

main() {
  require_positive_integer "SUMMARY_RETENTION_DAYS" "${SUMMARY_RETENTION_DAYS}"
  require_positive_integer "ARTIFACT_RETENTION_DAYS" "${ARTIFACT_RETENTION_DAYS}"

  mkdir -p "${NIGHTLY_OPS_HANDOFF_LOG_ROOT}"
  cleanup_summary_files
  cleanup_artifact_dirs
}

main "$@"
