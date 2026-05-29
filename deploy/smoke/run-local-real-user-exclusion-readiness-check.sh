#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

TOP_N="${TOP_N:-20}"
SAMPLE_LIMIT="${SAMPLE_LIMIT:-10}"
REQUIRE_READY="${REQUIRE_READY:-false}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
READINESS_OUTPUT="${ARTIFACT_DIR}/real-user-readiness.out"
DISTRIBUTION_OUTPUT="${ARTIFACT_DIR}/real-user-zero-reason-distribution.out"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

normalize_flag() {
  local value="${1,,}"
  case "${value}" in
    true|false) printf '%s' "${value}" ;;
    *)
      echo "unsupported flag value: ${1}" >&2
      exit 1
      ;;
  esac
}

extract_key_value() {
  local output_file="$1"
  local key="$2"
  python3 - "${output_file}" "${key}" <<'PY'
import sys

output_file, key = sys.argv[1], sys.argv[2]
prefix = f"{key}="

with open(output_file, "r", encoding="utf-8") as fp:
    for raw_line in fp:
        line = raw_line.strip()
        if line.startswith(prefix):
            print(line[len(prefix):])
            break
PY
}

REQUIRE_READY="$(normalize_flag "${REQUIRE_READY}")"
KEEP_ARTIFACTS="$(normalize_flag "${KEEP_ARTIFACTS}")"

smoke_require_command bash
mkdir -p "${ARTIFACT_DIR}"

smoke_print_step "real-user readiness check"
REQUIRE_READY="${REQUIRE_READY}" \
KEEP_ARTIFACTS=true \
bash "${ROOT_DIR}/deploy/smoke/run-local-real-user-readiness-check.sh" | tee "${READINESS_OUTPUT}"

DASHBOARD_REAL_USER_GATE="$(extract_key_value "${READINESS_OUTPUT}" "dashboard_real_user_gate")"
BREAKDOWN_REAL_USER_COHORT_GATE="$(extract_key_value "${READINESS_OUTPUT}" "breakdown_real_user_cohort_gate")"

echo
if [[ "${DASHBOARD_REAL_USER_GATE}" == "READY_REAL_USER_TRAFFIC" && "${BREAKDOWN_REAL_USER_COHORT_GATE}" == "READY_REAL_USER_COHORT" ]]; then
  smoke_print_step "real-user zero-ai reason distribution"
  USER_COHORT=real_user \
  TOP_N="${TOP_N}" \
  SAMPLE_LIMIT="${SAMPLE_LIMIT}" \
  KEEP_ARTIFACTS=true \
  ARTIFACT_DIR="${ARTIFACT_DIR}/zero-reason-distribution-artifact" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-ai-zero-reason-distribution-audit.sh" | tee "${DISTRIBUTION_OUTPUT}"
  echo "real_user_distribution_executed=true"
else
  echo "real_user_distribution_executed=false"
  echo "real_user_distribution_skip_reason=REAL_USER_GATE_NOT_READY"
  echo "dashboard_real_user_gate=${DASHBOARD_REAL_USER_GATE}"
  echo "breakdown_real_user_cohort_gate=${BREAKDOWN_REAL_USER_COHORT_GATE}"
fi
