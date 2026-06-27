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
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
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
KEEP_ARTIFACTS="$(normalize_flag "${KEEP_ARTIFACTS}")"

smoke_require_command bash
mkdir -p "${ARTIFACT_DIR}"

db_mode="$(smoke_resolve_db_mode)"

smoke_print_step "real-user ctr readiness audit"
if [[ "${db_mode}" == "postgres" ]]; then
  smoke_db_query "
WITH scoped_logs AS (
    SELECT rl.user_key, rl.service_id, rl.is_clicked
    FROM recommendation_logs rl
    JOIN users u
      ON u.user_key = rl.user_key
    WHERE COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'REAL_USER'
),
agg AS (
    SELECT COUNT(DISTINCT user_key) AS scope_users,
           COUNT(DISTINCT user_key) FILTER (WHERE is_clicked) AS clicked_users,
           COUNT(DISTINCT service_id) FILTER (WHERE is_clicked) AS clicked_services,
           COUNT(*) AS total_logs
    FROM scoped_logs
)
SELECT 'audit_scope_users=' || scope_users FROM agg
UNION ALL
SELECT 'ctr_clicked_users=' || clicked_users FROM agg
UNION ALL
SELECT 'ctr_clicked_services=' || clicked_services FROM agg;

SELECT '[tuning_readiness]';
WITH scoped_logs AS (
    SELECT rl.user_key
    FROM recommendation_logs rl
    JOIN users u
      ON u.user_key = rl.user_key
    WHERE COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'REAL_USER'
),
agg AS (
    SELECT COUNT(*) AS total_logs
    FROM scoped_logs
)
SELECT CASE
           WHEN total_logs = 0 THEN 'DEFERRED_EMPTY_REAL_USER_COHORT'
           ELSE 'DIAGNOSTIC_REAL_USER_ONLY_TRAFFIC'
       END
FROM agg;
" | tee "${CTR_OUTPUT}"
else
  USER_COHORT=real_user \
  KEEP_ARTIFACTS=true \
  ARTIFACT_DIR="${ARTIFACT_DIR}/ctr-artifact" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-ctr-readiness-audit.sh" | tee "${CTR_OUTPUT}"
fi

smoke_print_step "real-user concentration audit"
if [[ "${db_mode}" == "postgres" ]]; then
  smoke_db_query "
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
scoped_rows AS (
    SELECT ur.user_key,
           ur.service_id,
           ur.final_score
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
    JOIN users u
      ON u.user_key = ur.user_key
    WHERE COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'REAL_USER'
),
top1 AS (
    SELECT *
    FROM (
        SELECT sr.user_key,
               sr.service_id,
               sr.final_score,
               ROW_NUMBER() OVER (
                   PARTITION BY sr.user_key
                   ORDER BY sr.final_score DESC, sr.service_id DESC
               ) AS rn
        FROM scoped_rows sr
    ) ranked
    WHERE rn = 1
),
top1_summary AS (
    SELECT service_id,
           COUNT(*) AS users_as_top1
    FROM top1
    GROUP BY service_id
),
top1_leader AS (
    SELECT users_as_top1
    FROM top1_summary
    ORDER BY users_as_top1 DESC, service_id
    LIMIT 1
),
base AS (
    SELECT (SELECT COUNT(DISTINCT user_key) FROM scoped_rows) AS latest_batch_users,
           (SELECT users_as_top1 FROM top1_leader) AS top1_leader_users
)
SELECT 'latest_batch_users=' || latest_batch_users FROM base
UNION ALL
SELECT 'top1_leader_share_pct=' ||
       COALESCE(ROUND(top1_leader_users * 100.0 / NULLIF(latest_batch_users, 0), 2)::text, '')
FROM base;

SELECT '[concentration_readiness]';
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
scoped_rows AS (
    SELECT ur.user_key
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
    JOIN users u
      ON u.user_key = ur.user_key
    WHERE COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'REAL_USER'
),
top1 AS (
    SELECT COUNT(DISTINCT user_key) AS top1_users
    FROM scoped_rows
)
SELECT CASE
           WHEN top1_users = 0 THEN 'DEFERRED_EMPTY_COHORT'
           ELSE 'BALANCED_ENOUGH_FOR_LOGIC_REVIEW'
       END
FROM top1;

SELECT '[real_user_cohort_gate]';
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
scoped_rows AS (
    SELECT DISTINCT ur.user_key
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
    JOIN users u
      ON u.user_key = ur.user_key
    WHERE COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'REAL_USER'
)
SELECT CASE
           WHEN COUNT(*) = 0 THEN 'DEFERRED_EMPTY_REAL_USER_COHORT'
           ELSE 'DIAGNOSTIC_REAL_USER_ONLY_COHORT'
       END
FROM scoped_rows;

SELECT '[signal_quality]';
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
scoped_rows AS (
    SELECT DISTINCT ur.user_key
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
    JOIN users u
      ON u.user_key = ur.user_key
    WHERE COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'REAL_USER'
)
SELECT CASE
           WHEN COUNT(*) = 0 THEN 'EMPTY_REAL_USER_COHORT'
           ELSE 'REAL_USER_ONLY_COHORT'
       END
FROM scoped_rows;
" | tee "${CONCENTRATION_OUTPUT}"
else
  USER_COHORT=real_user \
  KEEP_ARTIFACTS=true \
  ARTIFACT_DIR="${ARTIFACT_DIR}/concentration-artifact" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-concentration-audit.sh" | tee "${CONCENTRATION_OUTPUT}"
fi

smoke_print_step "admin dashboard summary"
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS}" \
TREND_WINDOW_DAYS_CSV="${TREND_WINDOW_DAYS_CSV}" \
KEEP_ARTIFACTS=true \
ARTIFACT_DIR="${ARTIFACT_DIR}/admin-dashboard-artifact" \
bash "${ROOT_DIR}/deploy/smoke/run-local-admin-dashboard-smoke.sh" | tee "${DASHBOARD_OUTPUT}"

smoke_print_step "admin recommendation breakdowns"
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS}" \
BREAKDOWN_LIMIT="${BREAKDOWN_LIMIT}" \
KEEP_ARTIFACTS=true \
ARTIFACT_DIR="${ARTIFACT_DIR}/admin-breakdowns-artifact" \
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
