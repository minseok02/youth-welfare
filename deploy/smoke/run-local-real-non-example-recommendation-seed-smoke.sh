#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-real.non.example.seed}"
SMOKE_EMAIL_DOMAIN="${SMOKE_EMAIL_DOMAIN:-cohortseed.app}"
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS:-14}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
CLICK_OUTPUT="${ARTIFACT_DIR}/recommendation-click.out"
CTR_OUTPUT="${ARTIFACT_DIR}/ctr-real-non-example.out"
CONCENTRATION_OUTPUT="${ARTIFACT_DIR}/concentration-real-non-example.out"
LOG_ROW_OUTPUT="${ARTIFACT_DIR}/log-row.txt"
WINDOW_ROW_OUTPUT="${ARTIFACT_DIR}/window-row.txt"

cleanup() {
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

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

domain_classification() {
  local lowered="${1,,}"
  if [[ "${lowered}" == "example.com" ]]; then
    printf '%s' "EXAMPLE"
  elif [[ "${lowered}" == "smoke.local" || "${lowered}" == "localhost" || "${lowered}" == *.local || "${lowered}" == *.test || "${lowered}" == *.invalid ]]; then
    printf '%s' "BOUNDED_LOCAL"
  else
    printf '%s' "REAL_NON_EXAMPLE"
  fi
}

smoke_require_command bash
smoke_require_command python3
smoke_require_command docker

if [[ "$(domain_classification "${SMOKE_EMAIL_DOMAIN}")" != "REAL_NON_EXAMPLE" ]]; then
  echo "SMOKE_EMAIL_DOMAIN must classify as REAL_NON_EXAMPLE under current cohort rules: ${SMOKE_EMAIL_DOMAIN}" >&2
  exit 1
fi

smoke_print_step "seed real-non-example recommendation click (${SMOKE_EMAIL_DOMAIN})"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX}" \
SMOKE_EMAIL_DOMAIN="${SMOKE_EMAIL_DOMAIN}" \
bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-click-smoke.sh" | tee "${CLICK_OUTPUT}"

SEED_EMAIL="$(extract_key_value "${CLICK_OUTPUT}" "smoke_email")"
SEED_LOG_ID="$(extract_key_value "${CLICK_OUTPUT}" "log_id")"
SEED_SERVICE_ID="$(extract_key_value "${CLICK_OUTPUT}" "service_id")"

if [[ -z "${SEED_EMAIL}" || -z "${SEED_LOG_ID}" ]]; then
  echo "failed to extract real-non-example seed outputs" >&2
  cat "${CLICK_OUTPUT}" >&2
  exit 1
fi

smoke_print_step "verify seeded log row is real-non-example"
smoke_db_query \
  "SELECT COALESCE(u.email, ''), rl.user_key, rl.service_id, rl.is_clicked
   FROM recommendation_logs rl
   LEFT JOIN users u ON u.user_key = rl.user_key
   WHERE rl.id = ${SEED_LOG_ID};" \
  > "${LOG_ROW_OUTPUT}"

ROW_EMAIL="$(awk -F $'\t' 'NR==1 {print $1}' "${LOG_ROW_OUTPUT}")"
ROW_USER_KEY="$(awk -F $'\t' 'NR==1 {print $2}' "${LOG_ROW_OUTPUT}")"
ROW_SERVICE_ID="$(awk -F $'\t' 'NR==1 {print $3}' "${LOG_ROW_OUTPUT}")"
ROW_IS_CLICKED="$(awk -F $'\t' 'NR==1 {print $4}' "${LOG_ROW_OUTPUT}")"
ROW_DOMAIN="${ROW_EMAIL##*@}"

if [[ "${ROW_EMAIL}" != "${SEED_EMAIL}" ]]; then
  echo "seeded row email mismatch: expected ${SEED_EMAIL}, got ${ROW_EMAIL}" >&2
  cat "${LOG_ROW_OUTPUT}" >&2
  exit 1
fi

if [[ "$(domain_classification "${ROW_DOMAIN}")" != "REAL_NON_EXAMPLE" ]]; then
  echo "seeded row domain was not classified as REAL_NON_EXAMPLE: ${ROW_DOMAIN}" >&2
  cat "${LOG_ROW_OUTPUT}" >&2
  exit 1
fi

if [[ "${ROW_IS_CLICKED}" != "t" && "${ROW_IS_CLICKED}" != "true" ]]; then
  echo "seeded log row is not clicked" >&2
  cat "${LOG_ROW_OUTPUT}" >&2
  exit 1
fi

smoke_print_step "verify real-non-example cohort counts in ${SUMMARY_WINDOW_DAYS}d window"
smoke_db_query \
  "SELECT COUNT(*),
          COUNT(DISTINCT rl.user_key),
          COUNT(DISTINCT CASE WHEN rl.is_clicked THEN rl.user_key END)
   FROM recommendation_logs rl
   LEFT JOIN users u ON u.user_key = rl.user_key
   WHERE rl.sent_at >= NOW() - INTERVAL '${SUMMARY_WINDOW_DAYS} days'
     AND lower(split_part(coalesce(u.email, ''), '@', 2)) <> 'example.com'
     AND lower(split_part(coalesce(u.email, ''), '@', 2)) <> 'smoke.local'
     AND lower(split_part(coalesce(u.email, ''), '@', 2)) <> 'localhost'
     AND lower(split_part(coalesce(u.email, ''), '@', 2)) NOT LIKE '%.local'
     AND lower(split_part(coalesce(u.email, ''), '@', 2)) NOT LIKE '%.test'
     AND lower(split_part(coalesce(u.email, ''), '@', 2)) NOT LIKE '%.invalid';" \
  > "${WINDOW_ROW_OUTPUT}"

WINDOW_REAL_NON_EXAMPLE_LOGS="$(awk -F $'\t' 'NR==1 {print $1}' "${WINDOW_ROW_OUTPUT}")"
WINDOW_REAL_NON_EXAMPLE_USERS="$(awk -F $'\t' 'NR==1 {print $2}' "${WINDOW_ROW_OUTPUT}")"
WINDOW_REAL_NON_EXAMPLE_CLICKED_USERS="$(awk -F $'\t' 'NR==1 {print $3}' "${WINDOW_ROW_OUTPUT}")"

if [[ "${WINDOW_REAL_NON_EXAMPLE_LOGS}" == "0" || "${WINDOW_REAL_NON_EXAMPLE_USERS}" == "0" ]]; then
  echo "real-non-example cohort is still empty after seed" >&2
  cat "${WINDOW_ROW_OUTPUT}" >&2
  exit 1
fi

smoke_print_step "rerun ctr audit for real-non-example cohort"
USER_COHORT=real_non_example bash "${ROOT_DIR}/deploy/smoke/run-local-ctr-readiness-audit.sh" | tee "${CTR_OUTPUT}"

CTR_SCOPE_LOGS="$(extract_key_value "${CTR_OUTPUT}" "audit_scope_logs")"
CTR_SCOPE_USERS="$(extract_key_value "${CTR_OUTPUT}" "audit_scope_users")"
CTR_CLICKED_LOGS="$(extract_key_value "${CTR_OUTPUT}" "ctr_clicked_logs")"
CTR_CLICKED_USERS="$(extract_key_value "${CTR_OUTPUT}" "ctr_clicked_users")"
CTR_READINESS="$(extract_section_value "${CTR_OUTPUT}" "[tuning_readiness]")"

smoke_print_step "rerun concentration audit for real-non-example cohort"
USER_COHORT=real_non_example bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-concentration-audit.sh" | tee "${CONCENTRATION_OUTPUT}"

CONCENTRATION_ROWS="$(extract_key_value "${CONCENTRATION_OUTPUT}" "latest_batch_rows")"
CONCENTRATION_USERS="$(extract_key_value "${CONCENTRATION_OUTPUT}" "latest_batch_users")"
CONCENTRATION_REAL_NON_EXAMPLE_USERS="$(extract_key_value "${CONCENTRATION_OUTPUT}" "latest_batch_real_non_example_users")"
CONCENTRATION_READINESS="$(extract_section_value "${CONCENTRATION_OUTPUT}" "[concentration_readiness]")"
CONCENTRATION_SIGNAL_QUALITY="$(extract_section_value "${CONCENTRATION_OUTPUT}" "[signal_quality]")"

echo
echo "real-non-example recommendation seed smoke passed"
echo "seed_email=${SEED_EMAIL}"
echo "seed_email_domain=${SMOKE_EMAIL_DOMAIN}"
echo "seed_user_key=${ROW_USER_KEY}"
echo "seed_service_id=${ROW_SERVICE_ID:-${SEED_SERVICE_ID}}"
echo "seed_log_id=${SEED_LOG_ID}"
echo "window_real_non_example_logs=${WINDOW_REAL_NON_EXAMPLE_LOGS}"
echo "window_real_non_example_users=${WINDOW_REAL_NON_EXAMPLE_USERS}"
echo "window_real_non_example_clicked_users=${WINDOW_REAL_NON_EXAMPLE_CLICKED_USERS}"
echo "ctr_scope_logs=${CTR_SCOPE_LOGS}"
echo "ctr_scope_users=${CTR_SCOPE_USERS}"
echo "ctr_clicked_logs=${CTR_CLICKED_LOGS}"
echo "ctr_clicked_users=${CTR_CLICKED_USERS}"
echo "ctr_readiness=${CTR_READINESS}"
echo "latest_batch_rows=${CONCENTRATION_ROWS}"
echo "latest_batch_users=${CONCENTRATION_USERS}"
echo "latest_batch_real_non_example_users=${CONCENTRATION_REAL_NON_EXAMPLE_USERS}"
echo "concentration_readiness=${CONCENTRATION_READINESS}"
echo "signal_quality=${CONCENTRATION_SIGNAL_QUALITY}"
