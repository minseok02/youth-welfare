#!/usr/bin/env bash
set -euo pipefail

APP_ROOT="${APP_ROOT:-/home/ubuntu/youth-welfare}"
ENV_FILE="${ENV_FILE:-.env.production}"
SMOKE_DB_MODE="${SMOKE_DB_MODE:-postgres}"
APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
FRONTEND_E2E_MODE="${FRONTEND_E2E_MODE:-deployed-origin}"
FRONTEND_PUBLIC_BASE_URL="${FRONTEND_PUBLIC_BASE_URL:-https://youthmoa.kr}"
NIGHTLY_OPS_HANDOFF_LOG_ROOT="${NIGHTLY_OPS_HANDOFF_LOG_ROOT:-/var/log/youth-welfare/nightly-ops-handoff}"

NIGHTLY_SCHEDULE="${NIGHTLY_SCHEDULE:-10 1 * * *}"
FRONTEND_WEEKLY_SCHEDULE="${FRONTEND_WEEKLY_SCHEDULE:-30 1 * * 1}"
CLEANUP_SCHEDULE="${CLEANUP_SCHEDULE:-45 1 * * *}"

INSTALL_FRONTEND_WEEKLY="${INSTALL_FRONTEND_WEEKLY:-true}"
INSTALL_CLEANUP_CRON="${INSTALL_CLEANUP_CRON:-true}"
PRINT_ONLY="${PRINT_ONLY:-false}"
CRONTAB_BIN="${CRONTAB_BIN:-crontab}"

BLOCK_START="# >>> youth-welfare nightly ops handoff >>>"
BLOCK_END="# <<< youth-welfare nightly ops handoff <<<"

mkdir -p "${NIGHTLY_OPS_HANDOFF_LOG_ROOT}"

build_block() {
  cat <<EOF
${BLOCK_START}
${NIGHTLY_SCHEDULE} ENV_FILE=${ENV_FILE} SMOKE_DB_MODE=${SMOKE_DB_MODE} APP_BASE_URL='${APP_BASE_URL}' FRONTEND_E2E_MODE=${FRONTEND_E2E_MODE} FRONTEND_PUBLIC_BASE_URL='${FRONTEND_PUBLIC_BASE_URL}' ${APP_ROOT}/deploy/smoke/run-nightly-ops-handoff.sh >> ${NIGHTLY_OPS_HANDOFF_LOG_ROOT}/nightly-cron.log 2>&1
EOF

  if [[ "${INSTALL_FRONTEND_WEEKLY}" == "true" ]]; then
    cat <<EOF
${FRONTEND_WEEKLY_SCHEDULE} RUN_FRONTEND_OBSERVATION=true ENV_FILE=${ENV_FILE} SMOKE_DB_MODE=${SMOKE_DB_MODE} APP_BASE_URL='${APP_BASE_URL}' FRONTEND_E2E_MODE=${FRONTEND_E2E_MODE} FRONTEND_PUBLIC_BASE_URL='${FRONTEND_PUBLIC_BASE_URL}' ${APP_ROOT}/deploy/smoke/run-nightly-ops-handoff.sh >> ${NIGHTLY_OPS_HANDOFF_LOG_ROOT}/frontend-weekly-cron.log 2>&1
EOF
  fi

  if [[ "${INSTALL_CLEANUP_CRON}" == "true" ]]; then
    cat <<EOF
${CLEANUP_SCHEDULE} NIGHTLY_OPS_HANDOFF_LOG_ROOT=${NIGHTLY_OPS_HANDOFF_LOG_ROOT} SUMMARY_RETENTION_DAYS=30 ARTIFACT_RETENTION_DAYS=14 ${APP_ROOT}/deploy/smoke/cleanup-nightly-ops-handoff-artifacts.sh >> ${NIGHTLY_OPS_HANDOFF_LOG_ROOT}/cleanup-cron.log 2>&1
EOF
  fi

  cat <<EOF
${BLOCK_END}
EOF
}

if [[ "${PRINT_ONLY}" == "true" ]]; then
  build_block
  exit 0
fi

tmp_file="$(mktemp)"
current_file="$(mktemp)"
trap 'rm -f "${tmp_file}" "${current_file}"' EXIT

if ! "${CRONTAB_BIN}" -l > "${current_file}" 2>/dev/null; then
  : > "${current_file}"
fi

awk -v start="${BLOCK_START}" -v end="${BLOCK_END}" '
  $0 == start { skip=1; next }
  $0 == end { skip=0; next }
  skip != 1 { print }
' "${current_file}" > "${tmp_file}"

{
  cat "${tmp_file}"
  if [[ -s "${tmp_file}" ]]; then
    printf '\n'
  fi
  build_block
} | "${CRONTAB_BIN}" -

printf 'installed nightly ops handoff cron block at %s\n' "${NIGHTLY_OPS_HANDOFF_LOG_ROOT}"
