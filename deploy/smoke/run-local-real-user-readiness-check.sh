#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS:-14}"
TREND_WINDOW_DAYS_CSV="${TREND_WINDOW_DAYS_CSV:-1,7,30}"
BREAKDOWN_LIMIT="${BREAKDOWN_LIMIT:-3}"
REQUIRE_READY="${REQUIRE_READY:-false}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
CTR_OUTPUT="${ARTIFACT_DIR}/ctr-real-user.out"
CONCENTRATION_OUTPUT="${ARTIFACT_DIR}/concentration-real-user.out"
DASHBOARD_OUTPUT="${ARTIFACT_DIR}/admin-dashboard.out"
BREAKDOWN_OUTPUT="${ARTIFACT_DIR}/admin-breakdowns.out"

cleanup() {
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

extract_section_value() {
  local output_file="$1"
  local marker="$2"
  python3 - "${output_file}" "${marker}" <<'PY'
import sys

output_file, marker = sys.argv[1], sys.argv[2]

with open(output_file, "r", encoding="utf-8") as fp:
    lines = [line.rstrip("\n") for line in fp]

for index, line in enumerate(lines):
    if line == marker and index + 1 < len(lines):
        print(lines[index + 1].strip())
        break
PY
}

REQUIRE_READY="$(normalize_flag "${REQUIRE_READY}")"

smoke_require_command bash

smoke_print_step "real-user ctr readiness audit"
USER_COHORT=real_user bash "${ROOT_DIR}/deploy/smoke/run-local-ctr-readiness-audit.sh" | tee "${CTR_OUTPUT}"

smoke_print_step "real-user concentration audit"
USER_COHORT=real_user bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-concentration-audit.sh" | tee "${CONCENTRATION_OUTPUT}"

smoke_print_step "admin dashboard summary"
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS}" \
TREND_WINDOW_DAYS_CSV="${TREND_WINDOW_DAYS_CSV}" \
bash "${ROOT_DIR}/deploy/smoke/run-local-admin-dashboard-smoke.sh" | tee "${DASHBOARD_OUTPUT}"

smoke_print_step "admin recommendation breakdowns"
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS}" \
BREAKDOWN_LIMIT="${BREAKDOWN_LIMIT}" \
bash "${ROOT_DIR}/deploy/smoke/run-local-admin-recommendation-breakdowns-smoke.sh" | tee "${BREAKDOWN_OUTPUT}"

CTR_SCOPE_USERS="$(extract_key_value "${CTR_OUTPUT}" "audit_scope_users")"
CTR_CLICKED_USERS="$(extract_key_value "${CTR_OUTPUT}" "ctr_clicked_users")"
CTR_CLICKED_SERVICES="$(extract_key_value "${CTR_OUTPUT}" "ctr_clicked_services")"
CTR_READINESS="$(extract_section_value "${CTR_OUTPUT}" "[tuning_readiness]")"

CONCENTRATION_USERS="$(extract_key_value "${CONCENTRATION_OUTPUT}" "latest_batch_users")"
CONCENTRATION_TOP1_SHARE_PCT="$(extract_key_value "${CONCENTRATION_OUTPUT}" "top1_leader_share_pct")"
CONCENTRATION_READINESS="$(extract_section_value "${CONCENTRATION_OUTPUT}" "[concentration_readiness]")"
CONCENTRATION_REAL_USER_COHORT_GATE="$(extract_section_value "${CONCENTRATION_OUTPUT}" "[real_user_cohort_gate]")"
CONCENTRATION_SIGNAL_QUALITY="$(extract_section_value "${CONCENTRATION_OUTPUT}" "[signal_quality]")"

DASHBOARD_REAL_USER_USERS="$(extract_key_value "${DASHBOARD_OUTPUT}" "recommendation_real_user_users_in_window")"
DASHBOARD_REAL_USER_GATE="$(extract_key_value "${DASHBOARD_OUTPUT}" "recommendation_real_user_traffic_gate_in_window")"
DASHBOARD_REVIEW_GATE="$(extract_key_value "${DASHBOARD_OUTPUT}" "recommendation_review_gate")"
DASHBOARD_TOP1_SIGNAL_SUMMARY="$(extract_key_value "${DASHBOARD_OUTPUT}" "recommendation_top1_leader_signal_summary")"

BREAKDOWN_REAL_USER_USERS="$(extract_key_value "${BREAKDOWN_OUTPUT}" "real_user_users_in_window")"
BREAKDOWN_REAL_USER_GATE="$(extract_key_value "${BREAKDOWN_OUTPUT}" "real_user_traffic_gate_in_window")"
BREAKDOWN_REVIEW_GATE="$(extract_key_value "${BREAKDOWN_OUTPUT}" "recommendation_review_gate")"
BREAKDOWN_REAL_USER_COHORT_GATE="$(extract_key_value "${BREAKDOWN_OUTPUT}" "latest_batch_real_user_cohort_gate")"
BREAKDOWN_TOP1_SIGNAL_SUMMARY="$(extract_key_value "${BREAKDOWN_OUTPUT}" "top1_leader_signal_summary")"

if [[ "${REQUIRE_READY}" == "true" ]]; then
  if [[ "${DASHBOARD_REAL_USER_GATE}" != "READY_REAL_USER_TRAFFIC" ]]; then
    echo "dashboard real-user gate is not ready: ${DASHBOARD_REAL_USER_GATE}" >&2
    exit 1
  fi
  if [[ "${BREAKDOWN_REAL_USER_GATE}" != "READY_REAL_USER_TRAFFIC" ]]; then
    echo "breakdown real-user gate is not ready: ${BREAKDOWN_REAL_USER_GATE}" >&2
    exit 1
  fi
  if [[ "${BREAKDOWN_REAL_USER_COHORT_GATE}" != "READY_REAL_USER_COHORT" ]]; then
    echo "breakdown real-user cohort gate is not ready: ${BREAKDOWN_REAL_USER_COHORT_GATE}" >&2
    exit 1
  fi
fi

echo
echo "real-user readiness check passed"
echo "summary_window_days=${SUMMARY_WINDOW_DAYS}"
echo "breakdown_limit=${BREAKDOWN_LIMIT}"
echo "require_ready=${REQUIRE_READY}"
echo "ctr_real_user_scope_users=${CTR_SCOPE_USERS}"
echo "ctr_real_user_clicked_users=${CTR_CLICKED_USERS}"
echo "ctr_real_user_clicked_services=${CTR_CLICKED_SERVICES}"
echo "ctr_real_user_readiness=${CTR_READINESS}"
echo "concentration_real_user_users=${CONCENTRATION_USERS}"
echo "concentration_top1_share_pct=${CONCENTRATION_TOP1_SHARE_PCT}"
echo "concentration_readiness=${CONCENTRATION_READINESS}"
echo "concentration_real_user_cohort_gate=${CONCENTRATION_REAL_USER_COHORT_GATE}"
echo "concentration_signal_quality=${CONCENTRATION_SIGNAL_QUALITY}"
echo "dashboard_real_user_users_in_window=${DASHBOARD_REAL_USER_USERS}"
echo "dashboard_real_user_gate=${DASHBOARD_REAL_USER_GATE}"
echo "dashboard_review_gate=${DASHBOARD_REVIEW_GATE}"
echo "dashboard_top1_signal_summary=${DASHBOARD_TOP1_SIGNAL_SUMMARY}"
echo "breakdown_real_user_users_in_window=${BREAKDOWN_REAL_USER_USERS}"
echo "breakdown_real_user_gate=${BREAKDOWN_REAL_USER_GATE}"
echo "breakdown_review_gate=${BREAKDOWN_REVIEW_GATE}"
echo "breakdown_real_user_cohort_gate=${BREAKDOWN_REAL_USER_COHORT_GATE}"
echo "breakdown_top1_signal_summary=${BREAKDOWN_TOP1_SIGNAL_SUMMARY}"
