#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REPLAY_SCRIPT="${ROOT_DIR}/deploy/smoke/run-local-education-priority-replay.sh"

REPLAY_LOG_ROOT="${REPLAY_LOG_ROOT:-/var/log/youth-welfare/openai-replay}"
RUN_TS_UTC="${RUN_TS_UTC:-$(date -u +%Y-%m-%dT%H%M%SZ)}"
SUMMARY_DATE="${SUMMARY_DATE:-$(date +%F)}"
REPLAY_SUMMARY_TS="${REPLAY_SUMMARY_TS:-$(date +%Y-%m-%dT%H:%M:%S%z)}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${REPLAY_LOG_ROOT}/artifacts/${RUN_TS_UTC}}"
REPLAY_SUMMARY_APPEND_FILE="${REPLAY_SUMMARY_APPEND_FILE:-${REPLAY_LOG_ROOT}/nightly-summary-${SUMMARY_DATE}.log}"

mkdir -p "${REPLAY_LOG_ROOT}" "$(dirname "${REPLAY_SUMMARY_APPEND_FILE}")" "${ARTIFACT_DIR}"

export USE_REAL_OPENAI_FOR_REPLAY="${USE_REAL_OPENAI_FOR_REPLAY:-true}"
export KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"
export REPLAY_SUMMARY_APPEND_FILE
export REPLAY_SUMMARY_TS
export ARTIFACT_DIR

exec "${REPLAY_SCRIPT}" "$@"
