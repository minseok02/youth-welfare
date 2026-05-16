#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
SURFACE_ARTIFACT_DIR="${ARTIFACT_DIR}/surface"
SCORE_ARTIFACT_DIR="${ARTIFACT_DIR}/score"
AI_STATUS_ARTIFACT_DIR="${ARTIFACT_DIR}/ai-status"
SIGNAL_ARTIFACT_DIR="${ARTIFACT_DIR}/signal"

cleanup() {
  rm -rf "${ARTIFACT_DIR}"
}

if [[ "${KEEP_ARTIFACTS:-false}" != "true" ]]; then
  trap cleanup EXIT
fi

mkdir -p \
  "${SURFACE_ARTIFACT_DIR}" \
  "${SCORE_ARTIFACT_DIR}" \
  "${AI_STATUS_ARTIFACT_DIR}" \
  "${SIGNAL_ARTIFACT_DIR}"

run_and_prefix() {
  local suite_name="$1"
  local artifact_dir="$2"
  shift 2

  mkdir -p "${artifact_dir}"
  local output
  output="$(KEEP_ARTIFACTS=true ARTIFACT_DIR="${artifact_dir}" "$@")"

  while IFS= read -r line; do
    [[ -n "${line}" ]] || continue
    printf '[%s] %s\n' "${suite_name}" "${line}"
  done <<< "${output}"
}

run_and_prefix "surface" "${SURFACE_ARTIFACT_DIR}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-gov24-recommend-surface-audit.sh"

run_and_prefix "score" "${SCORE_ARTIFACT_DIR}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-gov24-recommend-score-audit.sh"

run_and_prefix "ai-status" "${AI_STATUS_ARTIFACT_DIR}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-gov24-ai-status-audit.sh"

run_and_prefix "signal" "${SIGNAL_ARTIFACT_DIR}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-gov24-signal-suite.sh"

echo
echo "gov24 recommendation suite passed"
echo "artifact_dir=${ARTIFACT_DIR}"
if [[ "${KEEP_ARTIFACTS:-false}" == "true" ]]; then
  echo "surface_artifact_dir=${SURFACE_ARTIFACT_DIR}"
  echo "score_artifact_dir=${SCORE_ARTIFACT_DIR}"
  echo "ai_status_artifact_dir=${AI_STATUS_ARTIFACT_DIR}"
  echo "signal_artifact_dir=${SIGNAL_ARTIFACT_DIR}"
fi

