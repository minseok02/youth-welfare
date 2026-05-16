#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HOUSING_ARTIFACT_DIR="${ARTIFACT_DIR}/housing"
EDUCATION_ARTIFACT_DIR="${ARTIFACT_DIR}/education"

cleanup() {
  rm -rf "${ARTIFACT_DIR}"
}

if [[ "${KEEP_ARTIFACTS:-false}" != "true" ]]; then
  trap cleanup EXIT
fi

mkdir -p "${HOUSING_ARTIFACT_DIR}" "${EDUCATION_ARTIFACT_DIR}"

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

run_and_prefix "housing" "${HOUSING_ARTIFACT_DIR}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-gov24-housing-signal-smoke.sh"

run_and_prefix "education" "${EDUCATION_ARTIFACT_DIR}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-gov24-education-signal-smoke.sh"

echo
echo "gov24 signal suite passed"
echo "artifact_dir=${ARTIFACT_DIR}"
if [[ "${KEEP_ARTIFACTS:-false}" == "true" ]]; then
  echo "housing_artifact_dir=${HOUSING_ARTIFACT_DIR}"
  echo "education_artifact_dir=${EDUCATION_ARTIFACT_DIR}"
fi
