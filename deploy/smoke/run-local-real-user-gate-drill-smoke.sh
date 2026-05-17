#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS:-14}"
TREND_WINDOW_DAYS_CSV="${TREND_WINDOW_DAYS_CSV:-1,7,30}"
BREAKDOWN_LIMIT="${BREAKDOWN_LIMIT:-3}"
TARGET_REAL_USER_USERS="${TARGET_REAL_USER_USERS:-3}"
SEED_EMAIL_PREFIX="${SEED_EMAIL_PREFIX:-real.user.gate.drill}"
SEED_EMAIL_DOMAIN="${SEED_EMAIL_DOMAIN:-cohortseed.app}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
SELECTED_USERS_FILE="${ARTIFACT_DIR}/selected-users.tsv"
PROMOTED_USERS_FILE="${ARTIFACT_DIR}/promoted-users.tsv"
CTR_ALL_OUTPUT="${ARTIFACT_DIR}/ctr-all.out"
CTR_REAL_USER_OUTPUT="${ARTIFACT_DIR}/ctr-real-user.out"
CONCENTRATION_ALL_OUTPUT="${ARTIFACT_DIR}/concentration-all.out"
CONCENTRATION_REAL_USER_OUTPUT="${ARTIFACT_DIR}/concentration-real-user.out"
DASHBOARD_OUTPUT="${ARTIFACT_DIR}/admin-dashboard.out"
BREAKDOWN_OUTPUT="${ARTIFACT_DIR}/admin-breakdowns.out"

PROMOTION_ACTIVE="false"

cleanup() {
  restore_promoted_users || true
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

refresh_selected_seed_users() {
  smoke_db_query \
    "SELECT u.user_key,
            COALESCE(u.account_origin, ''),
            COALESCE(u.email, '')
     FROM users u
     JOIN recommendation_logs rl
       ON rl.user_key = u.user_key
     WHERE COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'LOCAL_REAL_NON_EXAMPLE_SEED'
       AND rl.sent_at >= NOW() - INTERVAL '${SUMMARY_WINDOW_DAYS} days'
     GROUP BY u.user_key, u.account_origin, u.email
     HAVING COUNT(*) FILTER (WHERE rl.is_clicked) > 0
     ORDER BY MAX(rl.sent_at) DESC
     LIMIT ${TARGET_REAL_USER_USERS};" \
    > "${SELECTED_USERS_FILE}"
}

selected_user_count() {
  awk 'NF > 0 {count += 1} END {print count + 0}' "${SELECTED_USERS_FILE}"
}

seed_additional_local_real_users_if_needed() {
  local current_count
  local missing_count
  local seed_index
  local click_output
  local seed_email
  local seed_row
  local seed_user_key
  local seed_origin

  refresh_selected_seed_users
  current_count="$(selected_user_count)"
  if (( current_count >= TARGET_REAL_USER_USERS )); then
    return 0
  fi

  missing_count="$((TARGET_REAL_USER_USERS - current_count))"
  for (( seed_index=1; seed_index<=missing_count; seed_index++ )); do
    click_output="${ARTIFACT_DIR}/click-seed-${seed_index}.out"
    smoke_print_step "seed additional local-real-non-example user ${seed_index}/${missing_count} (${SEED_EMAIL_DOMAIN})"
    SMOKE_EMAIL_PREFIX="${SEED_EMAIL_PREFIX}" \
    SMOKE_EMAIL_DOMAIN="${SEED_EMAIL_DOMAIN}" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-click-smoke.sh" | tee "${click_output}"

    seed_email="$(extract_key_value "${click_output}" "smoke_email")"
    if [[ -z "${seed_email}" ]]; then
      echo "failed to extract seed email from ${click_output}" >&2
      cat "${click_output}" >&2
      exit 1
    fi

    seed_row="$(smoke_db_query \
      "SELECT COALESCE(user_key, ''), COALESCE(account_origin, '')
       FROM users
       WHERE email = '${seed_email}';")"
    seed_user_key="$(awk -F $'\t' 'NR==1 {print $1}' <<< "${seed_row}")"
    seed_origin="$(awk -F $'\t' 'NR==1 {print $2}' <<< "${seed_row}")"

    if [[ -z "${seed_user_key}" || "${seed_origin}" != "LOCAL_REAL_NON_EXAMPLE_SEED" ]]; then
      echo "new seed user lookup failed for ${seed_email}" >&2
      printf '%s\n' "${seed_row}" >&2
      exit 1
    fi
  done

  refresh_selected_seed_users
  current_count="$(selected_user_count)"
  if (( current_count < TARGET_REAL_USER_USERS )); then
    echo "failed to secure ${TARGET_REAL_USER_USERS} local real-non-example seed users for drill" >&2
    cat "${SELECTED_USERS_FILE}" >&2
    exit 1
  fi
}

promote_selected_users_to_real_user() {
  local user_key
  local original_origin
  local email

  : > "${PROMOTED_USERS_FILE}"

  while IFS=$'\t' read -r user_key original_origin email; do
    [[ -n "${user_key}" ]] || continue
    printf '%s\t%s\t%s\n' "${user_key}" "${original_origin}" "${email}" >> "${PROMOTED_USERS_FILE}"
    smoke_db_query \
      "UPDATE users
       SET account_origin = 'REAL_USER'
       WHERE user_key = '${user_key}';" \
      >/dev/null
  done < "${SELECTED_USERS_FILE}"

  PROMOTION_ACTIVE="true"
}

restore_promoted_users() {
  local user_key
  local original_origin
  local email

  if [[ "${PROMOTION_ACTIVE}" != "true" || ! -s "${PROMOTED_USERS_FILE}" ]]; then
    return 0
  fi

  while IFS=$'\t' read -r user_key original_origin email; do
    [[ -n "${user_key}" ]] || continue
    smoke_db_query \
      "UPDATE users
       SET account_origin = '${original_origin}'
       WHERE user_key = '${user_key}';" \
      >/dev/null
  done < "${PROMOTED_USERS_FILE}"

  PROMOTION_ACTIVE="false"
}

smoke_require_command bash
smoke_require_command python3
smoke_require_command docker

if ! [[ "${TARGET_REAL_USER_USERS}" =~ ^[0-9]+$ ]] || (( TARGET_REAL_USER_USERS < 3 )); then
  echo "TARGET_REAL_USER_USERS must be an integer >= 3" >&2
  exit 1
fi

seed_additional_local_real_users_if_needed

smoke_print_step "promote selected local-real-non-example seed users to REAL_USER"
promote_selected_users_to_real_user

smoke_print_step "rerun ctr readiness audit (all)"
bash "${ROOT_DIR}/deploy/smoke/run-local-ctr-readiness-audit.sh" | tee "${CTR_ALL_OUTPUT}"

smoke_print_step "rerun ctr readiness audit (real_user)"
USER_COHORT=real_user bash "${ROOT_DIR}/deploy/smoke/run-local-ctr-readiness-audit.sh" | tee "${CTR_REAL_USER_OUTPUT}"

smoke_print_step "rerun concentration audit (all)"
bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-concentration-audit.sh" | tee "${CONCENTRATION_ALL_OUTPUT}"

smoke_print_step "rerun concentration audit (real_user)"
USER_COHORT=real_user bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-concentration-audit.sh" | tee "${CONCENTRATION_REAL_USER_OUTPUT}"

smoke_print_step "rerun admin dashboard smoke"
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS}" \
TREND_WINDOW_DAYS_CSV="${TREND_WINDOW_DAYS_CSV}" \
bash "${ROOT_DIR}/deploy/smoke/run-local-admin-dashboard-smoke.sh" | tee "${DASHBOARD_OUTPUT}"

smoke_print_step "rerun admin recommendation breakdowns smoke"
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS}" \
BREAKDOWN_LIMIT="${BREAKDOWN_LIMIT}" \
bash "${ROOT_DIR}/deploy/smoke/run-local-admin-recommendation-breakdowns-smoke.sh" | tee "${BREAKDOWN_OUTPUT}"

CTR_ALL_READINESS="$(extract_section_value "${CTR_ALL_OUTPUT}" "[tuning_readiness]")"
CTR_REAL_USER_READINESS="$(extract_section_value "${CTR_REAL_USER_OUTPUT}" "[tuning_readiness]")"
CTR_ALL_REAL_USER_USERS="$(extract_key_value "${CTR_ALL_OUTPUT}" "ctr_real_user_users")"
CTR_ALL_REAL_USER_CLICKED_USERS="$(extract_key_value "${CTR_ALL_OUTPUT}" "ctr_real_user_clicked_users")"

CONCENTRATION_ALL_READINESS="$(extract_section_value "${CONCENTRATION_ALL_OUTPUT}" "[concentration_readiness]")"
CONCENTRATION_ALL_REAL_USER_GATE="$(extract_section_value "${CONCENTRATION_ALL_OUTPUT}" "[real_user_cohort_gate]")"
CONCENTRATION_ALL_SIGNAL_QUALITY="$(extract_section_value "${CONCENTRATION_ALL_OUTPUT}" "[signal_quality]")"
CONCENTRATION_REAL_USER_SIGNAL_QUALITY="$(extract_section_value "${CONCENTRATION_REAL_USER_OUTPUT}" "[signal_quality]")"

DASHBOARD_REAL_USER_GATE="$(extract_key_value "${DASHBOARD_OUTPUT}" "recommendation_real_user_traffic_gate_in_window")"
DASHBOARD_REVIEW_GATE="$(extract_key_value "${DASHBOARD_OUTPUT}" "recommendation_review_gate")"
DASHBOARD_TOP1_SIGNAL="$(extract_key_value "${DASHBOARD_OUTPUT}" "recommendation_top1_leader_signal_summary")"
DASHBOARD_REAL_USER_USERS="$(extract_key_value "${DASHBOARD_OUTPUT}" "recommendation_real_user_users_in_window")"

BREAKDOWN_REAL_USER_GATE="$(extract_key_value "${BREAKDOWN_OUTPUT}" "real_user_traffic_gate_in_window")"
BREAKDOWN_REVIEW_GATE="$(extract_key_value "${BREAKDOWN_OUTPUT}" "recommendation_review_gate")"
BREAKDOWN_TOP1_SIGNAL="$(extract_key_value "${BREAKDOWN_OUTPUT}" "top1_leader_signal_summary")"
BREAKDOWN_REAL_USER_COHORT_GATE="$(extract_key_value "${BREAKDOWN_OUTPUT}" "latest_batch_real_user_cohort_gate")"

if [[ "${CTR_ALL_READINESS}" != "READY_FOR_WEIGHT_REVIEW" ]]; then
  echo "expected all-cohort ctr readiness to open during real-user gate drill, got ${CTR_ALL_READINESS}" >&2
  exit 1
fi

if [[ "${CONCENTRATION_ALL_REAL_USER_GATE}" != "READY_REAL_USER_COHORT" ]]; then
  echo "expected concentration real-user cohort gate to open, got ${CONCENTRATION_ALL_REAL_USER_GATE}" >&2
  exit 1
fi

if [[ "${DASHBOARD_REAL_USER_GATE}" != "READY_REAL_USER_TRAFFIC" ]]; then
  echo "expected dashboard real-user traffic gate to open, got ${DASHBOARD_REAL_USER_GATE}" >&2
  exit 1
fi

if [[ "${BREAKDOWN_REAL_USER_GATE}" != "READY_REAL_USER_TRAFFIC" ]]; then
  echo "expected breakdown real-user traffic gate to open, got ${BREAKDOWN_REAL_USER_GATE}" >&2
  exit 1
fi

if [[ "${BREAKDOWN_REAL_USER_COHORT_GATE}" != "READY_REAL_USER_COHORT" ]]; then
  echo "expected breakdown latest batch real-user cohort gate to open, got ${BREAKDOWN_REAL_USER_COHORT_GATE}" >&2
  exit 1
fi

restore_promoted_users
RESTORED_LOCAL_REAL_NON_EXAMPLE_SEED_COUNT="0"
while IFS=$'\t' read -r restored_user_key restored_origin restored_email; do
  [[ -n "${restored_user_key}" ]] || continue
  restored_now="$(
    smoke_db_query \
      "SELECT COALESCE(account_origin, '')
       FROM users
       WHERE user_key = '${restored_user_key}';"
  )"
  if [[ "${restored_now}" != "LOCAL_REAL_NON_EXAMPLE_SEED" ]]; then
    echo "failed to restore promoted user ${restored_user_key} (${restored_email}) back to LOCAL_REAL_NON_EXAMPLE_SEED" >&2
    exit 1
  fi
  RESTORED_LOCAL_REAL_NON_EXAMPLE_SEED_COUNT="$((RESTORED_LOCAL_REAL_NON_EXAMPLE_SEED_COUNT + 1))"
done < "${PROMOTED_USERS_FILE}"

echo
echo "real-user gate drill smoke passed"
echo "summary_window_days=${SUMMARY_WINDOW_DAYS}"
echo "target_real_user_users=${TARGET_REAL_USER_USERS}"
echo "selected_user_count=$(selected_user_count)"
echo "selected_users=$(tr '\n' ';' < "${SELECTED_USERS_FILE}" | sed 's/;$//')"
echo "ctr_all_real_user_users=${CTR_ALL_REAL_USER_USERS}"
echo "ctr_all_real_user_clicked_users=${CTR_ALL_REAL_USER_CLICKED_USERS}"
echo "ctr_all_readiness=${CTR_ALL_READINESS}"
echo "ctr_real_user_readiness=${CTR_REAL_USER_READINESS}"
echo "concentration_all_readiness=${CONCENTRATION_ALL_READINESS}"
echo "concentration_all_real_user_cohort_gate=${CONCENTRATION_ALL_REAL_USER_GATE}"
echo "concentration_all_signal_quality=${CONCENTRATION_ALL_SIGNAL_QUALITY}"
echo "concentration_real_user_signal_quality=${CONCENTRATION_REAL_USER_SIGNAL_QUALITY}"
echo "dashboard_real_user_gate=${DASHBOARD_REAL_USER_GATE}"
echo "dashboard_review_gate=${DASHBOARD_REVIEW_GATE}"
echo "dashboard_top1_signal_summary=${DASHBOARD_TOP1_SIGNAL}"
echo "dashboard_real_user_users_in_window=${DASHBOARD_REAL_USER_USERS}"
echo "breakdown_real_user_gate=${BREAKDOWN_REAL_USER_GATE}"
echo "breakdown_review_gate=${BREAKDOWN_REVIEW_GATE}"
echo "breakdown_top1_signal_summary=${BREAKDOWN_TOP1_SIGNAL}"
echo "breakdown_real_user_cohort_gate=${BREAKDOWN_REAL_USER_COHORT_GATE}"
echo "restored_local_real_non_example_seed_count=${RESTORED_LOCAL_REAL_NON_EXAMPLE_SEED_COUNT}"
