#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/similar-users-viewed-audit}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

WINDOW_DAYS="${SIMILAR_USERS_VIEWED_WINDOW_DAYS:-30}"
MIN_SIMILAR_USERS="${SIMILAR_USERS_VIEWED_MIN_SIMILAR_USERS:-2}"
MIN_SIMILARITY_SCORE="${SIMILAR_USERS_VIEWED_MIN_SIMILARITY_SCORE:-3.0}"
TARGET_USER_LIMIT="${SIMILAR_USERS_VIEWED_TARGET_USER_LIMIT:-50}"

RAW_METRICS_OUT="${ARTIFACT_DIR}/similar-users-viewed-metrics.tsv"
SUMMARY_OUT="${ARTIFACT_DIR}/similar-users-viewed-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/similar-users-viewed-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/similar-users-viewed-note.md"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-similar-users-viewed-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-similar-users-viewed-summary.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-similar-users-viewed-note.md"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
mkdir -p "${ARTIFACT_DIR}"
smoke_require_command python3

smoke_db_query "
WITH params AS (
    SELECT
        ${WINDOW_DAYS}::int AS window_days,
        ${MIN_SIMILAR_USERS}::int AS min_similar_users,
        ${MIN_SIMILARITY_SCORE}::numeric AS min_similarity_score,
        ${TARGET_USER_LIMIT}::int AS target_user_limit,
        NOW() - (${WINDOW_DAYS}::text || ' days')::interval AS viewed_since
),
real_profiles AS (
    SELECT
        u.user_key,
        up.age,
        up.age_band,
        up.sido,
        up.region_code,
        up.income_level,
        EXISTS (
            SELECT 1
            FROM user_attributes ua
            WHERE ua.user_key = up.user_key
              AND ua.attr_type IN ('INTEREST_FIELD', 'TARGET_TYPE')
        ) AS has_attributes,
        EXISTS (
            SELECT 1
            FROM user_priorities upr
            WHERE upr.user_key = up.user_key
        ) AS has_priorities
    FROM users u
    JOIN user_profiles up ON up.user_key = u.user_key
    WHERE u.is_active = true
      AND u.account_origin = 'REAL_USER'
),
profile_summary AS (
    SELECT
        COUNT(*) AS active_real_profiles,
        COUNT(*) FILTER (
            WHERE region_code IS NOT NULL
               OR sido IS NOT NULL
               OR age_band IS NOT NULL
               OR income_level IS NOT NULL
               OR has_attributes
               OR has_priorities
        ) AS profiles_with_similarity_signals
    FROM real_profiles
),
active_real_summary AS (
    SELECT COUNT(*) AS active_real_users
    FROM users
    WHERE is_active = true
      AND account_origin = 'REAL_USER'
),
recent_view_summary AS (
    SELECT
        COUNT(*) AS recent_view_rows_30d,
        COUNT(DISTINCT rpv.user_key) AS recent_view_users_30d,
        COUNT(DISTINCT rpv.service_id) AS recent_view_services_30d
    FROM recent_policy_views rpv
    JOIN users u ON u.user_key = rpv.user_key
    CROSS JOIN params p
    WHERE u.is_active = true
      AND u.account_origin = 'REAL_USER'
      AND rpv.last_viewed_at >= p.viewed_since
),
target_users AS (
    SELECT rp.*
    FROM real_profiles rp
    WHERE rp.region_code IS NOT NULL
       OR rp.sido IS NOT NULL
       OR rp.age_band IS NOT NULL
       OR rp.income_level IS NOT NULL
       OR rp.has_attributes
       OR rp.has_priorities
    ORDER BY rp.user_key
    LIMIT (SELECT target_user_limit FROM params)
),
target_interest_fields AS (
    SELECT tu.user_key AS target_user_key, ua.attr_value
    FROM target_users tu
    JOIN user_attributes ua ON ua.user_key = tu.user_key
    WHERE ua.attr_type = 'INTEREST_FIELD'
),
target_target_types AS (
    SELECT tu.user_key AS target_user_key, ua.attr_value
    FROM target_users tu
    JOIN user_attributes ua ON ua.user_key = tu.user_key
    WHERE ua.attr_type = 'TARGET_TYPE'
),
target_priority_codes AS (
    SELECT tu.user_key AS target_user_key, po.code
    FROM target_users tu
    JOIN user_priorities upr ON upr.user_key = tu.user_key
    JOIN priority_options po ON po.id = upr.priority_option_id
),
similarity_pairs AS (
    SELECT
        tu.user_key AS target_user_key,
        rp.user_key AS similar_user_key,
        (
            CASE WHEN tu.region_code IS NOT NULL AND rp.region_code = tu.region_code THEN 4.0 ELSE 0.0 END
          + CASE WHEN tu.sido IS NOT NULL AND rp.sido = tu.sido THEN 2.0 ELSE 0.0 END
          + CASE WHEN tu.age_band IS NOT NULL AND rp.age_band = tu.age_band THEN 2.0 ELSE 0.0 END
          + CASE WHEN tu.income_level IS NOT NULL AND rp.income_level BETWEEN GREATEST(1, tu.income_level - 1) AND LEAST(10, tu.income_level + 1) THEN 1.0 ELSE 0.0 END
          + CASE WHEN EXISTS (
                SELECT 1
                FROM target_interest_fields tif
                JOIN user_attributes ua
                  ON ua.user_key = rp.user_key
                 AND ua.attr_type = 'INTEREST_FIELD'
                 AND ua.attr_value = tif.attr_value
                WHERE tif.target_user_key = tu.user_key
            ) THEN 1.5 ELSE 0.0 END
          + CASE WHEN EXISTS (
                SELECT 1
                FROM target_target_types ttt
                JOIN user_attributes ua
                  ON ua.user_key = rp.user_key
                 AND ua.attr_type = 'TARGET_TYPE'
                 AND ua.attr_value = ttt.attr_value
                WHERE ttt.target_user_key = tu.user_key
            ) THEN 1.0 ELSE 0.0 END
          + CASE WHEN EXISTS (
                SELECT 1
                FROM target_priority_codes tpc
                JOIN user_priorities upr ON upr.user_key = rp.user_key
                JOIN priority_options po
                  ON po.id = upr.priority_option_id
                 AND po.code = tpc.code
                WHERE tpc.target_user_key = tu.user_key
            ) THEN 1.5 ELSE 0.0 END
        ) AS similarity_score
    FROM target_users tu
    JOIN real_profiles rp ON rp.user_key <> tu.user_key
),
eligible_pairs AS (
    SELECT sp.*
    FROM similarity_pairs sp
    CROSS JOIN params p
    WHERE sp.similarity_score >= p.min_similarity_score
),
eligible_summary_by_target AS (
    SELECT target_user_key, COUNT(*) AS eligible_similar_users
    FROM eligible_pairs
    GROUP BY target_user_key
),
policy_groups AS (
    SELECT
        ep.target_user_key,
        rpv.service_id,
        COUNT(DISTINCT ep.similar_user_key) AS similar_user_count,
        COUNT(*) AS recent_view_count,
        BOOL_OR(own_rpv.user_key IS NOT NULL) AS excluded_by_own_recent_view,
        BOOL_OR(own_ur.user_key IS NOT NULL) AS excluded_by_latest_recommendation
    FROM eligible_pairs ep
    JOIN target_users tu ON tu.user_key = ep.target_user_key
    JOIN recent_policy_views rpv ON rpv.user_key = ep.similar_user_key
    JOIN welfare_services ws ON ws.id = rpv.service_id
    CROSS JOIN params p
    LEFT JOIN recent_policy_views own_rpv
      ON own_rpv.user_key = ep.target_user_key
     AND own_rpv.service_id = rpv.service_id
     AND own_rpv.last_viewed_at >= p.viewed_since
    LEFT JOIN user_recommendations own_ur
      ON own_ur.user_key = ep.target_user_key
     AND own_ur.service_id = rpv.service_id
     AND own_ur.recommended_at = (
         SELECT MAX(latest_ur.recommended_at)
         FROM user_recommendations latest_ur
         WHERE latest_ur.user_key = ep.target_user_key
     )
    WHERE rpv.last_viewed_at >= p.viewed_since
      AND ws.status IN ('ACTIVE', 'UPCOMING')
      AND ws.search_youth_relevant = true
      AND (ws.min_age IS NULL OR ws.min_age <= COALESCE(tu.age, 25))
      AND (ws.max_age IS NULL OR ws.max_age >= COALESCE(tu.age, 25))
      AND (
            (ws.min_income IS NULL AND ws.max_income IS NULL)
            OR (ws.min_income = 0 AND ws.max_income = 0)
            OR (
                (ws.min_income IS NULL OR ws.min_income <= COALESCE(tu.income_level, 5))
                AND (ws.max_income IS NULL OR ws.max_income >= COALESCE(tu.income_level, 5))
            )
          )
    GROUP BY ep.target_user_key, rpv.service_id
),
result_groups AS (
    SELECT pg.*
    FROM policy_groups pg
    CROSS JOIN params p
    WHERE pg.similar_user_count >= p.min_similar_users
      AND pg.excluded_by_own_recent_view = false
      AND pg.excluded_by_latest_recommendation = false
),
sample_summary AS (
    SELECT
        COUNT(*) AS sampled_target_users,
        COUNT(*) FILTER (WHERE COALESCE(est.eligible_similar_users, 0) > 0) AS sampled_targets_with_eligible_similar_users,
        COALESCE(SUM(est.eligible_similar_users), 0) AS eligible_similar_user_pairs,
        COALESCE(ROUND(AVG(est.eligible_similar_users)::numeric, 2), 0) AS avg_eligible_similar_users,
        COALESCE(MAX(est.eligible_similar_users), 0) AS max_eligible_similar_users
    FROM target_users tu
    LEFT JOIN eligible_summary_by_target est ON est.target_user_key = tu.user_key
),
candidate_summary AS (
    SELECT
        COUNT(*) AS candidate_policy_groups_before_exclusions,
        COUNT(*) FILTER (WHERE similar_user_count >= (SELECT min_similar_users FROM params)) AS candidate_policy_groups_min_sample,
        COUNT(*) FILTER (WHERE similar_user_count >= (SELECT min_similar_users FROM params) AND excluded_by_own_recent_view) AS excluded_by_own_recent_view_groups,
        COUNT(*) FILTER (WHERE similar_user_count >= (SELECT min_similar_users FROM params) AND excluded_by_latest_recommendation) AS excluded_by_latest_recommendation_groups,
        COUNT(*) FILTER (
            WHERE similar_user_count >= (SELECT min_similar_users FROM params)
              AND excluded_by_own_recent_view = false
              AND excluded_by_latest_recommendation = false
        ) AS result_policy_groups,
        COUNT(DISTINCT target_user_key) FILTER (
            WHERE similar_user_count >= (SELECT min_similar_users FROM params)
        ) AS targets_with_min_sample_policy_candidates,
        COUNT(DISTINCT target_user_key) FILTER (
            WHERE similar_user_count >= (SELECT min_similar_users FROM params)
              AND excluded_by_own_recent_view = false
              AND excluded_by_latest_recommendation = false
        ) AS targets_with_result_candidates
    FROM policy_groups
),
index_summary AS (
    SELECT COUNT(*) AS expected_index_count
    FROM pg_indexes
    WHERE schemaname = 'public'
      AND indexname IN (
          'idx_users_real_active_user_key',
          'idx_rpv_last_viewed_user_service',
          'idx_ua_type_value_user_key',
          'idx_up_option_user_key',
          'idx_ur_user_key_recommended_service'
      )
)
SELECT 'window_days', window_days::text FROM params
UNION ALL SELECT 'min_similar_users', min_similar_users::text FROM params
UNION ALL SELECT 'min_similarity_score', min_similarity_score::text FROM params
UNION ALL SELECT 'target_user_limit', target_user_limit::text FROM params
UNION ALL SELECT 'active_real_users', active_real_users::text FROM active_real_summary
UNION ALL SELECT 'active_real_profiles', active_real_profiles::text FROM profile_summary
UNION ALL SELECT 'profiles_with_similarity_signals', profiles_with_similarity_signals::text FROM profile_summary
UNION ALL SELECT 'recent_view_rows_window', recent_view_rows_30d::text FROM recent_view_summary
UNION ALL SELECT 'recent_view_users_window', recent_view_users_30d::text FROM recent_view_summary
UNION ALL SELECT 'recent_view_services_window', recent_view_services_30d::text FROM recent_view_summary
UNION ALL SELECT 'sampled_target_users', sampled_target_users::text FROM sample_summary
UNION ALL SELECT 'sampled_targets_with_eligible_similar_users', sampled_targets_with_eligible_similar_users::text FROM sample_summary
UNION ALL SELECT 'eligible_similar_user_pairs', eligible_similar_user_pairs::text FROM sample_summary
UNION ALL SELECT 'avg_eligible_similar_users', avg_eligible_similar_users::text FROM sample_summary
UNION ALL SELECT 'max_eligible_similar_users', max_eligible_similar_users::text FROM sample_summary
UNION ALL SELECT 'candidate_policy_groups_before_exclusions', candidate_policy_groups_before_exclusions::text FROM candidate_summary
UNION ALL SELECT 'candidate_policy_groups_min_sample', candidate_policy_groups_min_sample::text FROM candidate_summary
UNION ALL SELECT 'excluded_by_own_recent_view_groups', excluded_by_own_recent_view_groups::text FROM candidate_summary
UNION ALL SELECT 'excluded_by_latest_recommendation_groups', excluded_by_latest_recommendation_groups::text FROM candidate_summary
UNION ALL SELECT 'result_policy_groups', result_policy_groups::text FROM candidate_summary
UNION ALL SELECT 'targets_with_min_sample_policy_candidates', targets_with_min_sample_policy_candidates::text FROM candidate_summary
UNION ALL SELECT 'targets_with_result_candidates', targets_with_result_candidates::text FROM candidate_summary
UNION ALL SELECT 'expected_index_count', expected_index_count::text FROM index_summary;
" > "${RAW_METRICS_OUT}"

python3 - "${RAW_METRICS_OUT}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" <<'PY'
import csv
import json
import sys
from pathlib import Path

metrics_path = Path(sys.argv[1])
summary_out = Path(sys.argv[2])
json_out = Path(sys.argv[3])
note_out = Path(sys.argv[4])
artifact_dir = sys.argv[5]

int_keys = {
    "window_days",
    "min_similar_users",
    "target_user_limit",
    "active_real_users",
    "active_real_profiles",
    "profiles_with_similarity_signals",
    "recent_view_rows_window",
    "recent_view_users_window",
    "recent_view_services_window",
    "sampled_target_users",
    "sampled_targets_with_eligible_similar_users",
    "eligible_similar_user_pairs",
    "max_eligible_similar_users",
    "candidate_policy_groups_before_exclusions",
    "candidate_policy_groups_min_sample",
    "excluded_by_own_recent_view_groups",
    "excluded_by_latest_recommendation_groups",
    "result_policy_groups",
    "targets_with_min_sample_policy_candidates",
    "targets_with_result_candidates",
    "expected_index_count",
}
float_keys = {
    "min_similarity_score",
    "avg_eligible_similar_users",
}

metrics = {}
with metrics_path.open(encoding="utf-8") as f:
    for row in csv.reader(f, delimiter="\t"):
        if not row:
            continue
        key = row[0]
        value = row[1] if len(row) > 1 else ""
        if key in int_keys:
            metrics[key] = int(value or 0)
        elif key in float_keys:
            metrics[key] = float(value or 0)
        else:
            metrics[key] = value

active_real_users = metrics.get("active_real_users", 0)
profiles_with_signals = metrics.get("profiles_with_similarity_signals", 0)
recent_view_users = metrics.get("recent_view_users_window", 0)
sampled_targets = metrics.get("sampled_target_users", 0)
targets_with_similar = metrics.get("sampled_targets_with_eligible_similar_users", 0)
candidate_groups = metrics.get("candidate_policy_groups_before_exclusions", 0)
min_sample_groups = metrics.get("candidate_policy_groups_min_sample", 0)
result_groups = metrics.get("result_policy_groups", 0)
expected_index_count = metrics.get("expected_index_count", 0)
min_similar_users = metrics.get("min_similar_users", 2)

if expected_index_count < 5:
    decision_class = "MIGRATION_INDEX_MISSING"
    operator_reading = "similar-users-viewed read path index가 일부 없습니다. migration 적용 상태를 먼저 확인합니다."
elif active_real_users < min_similar_users + 1:
    decision_class = "REAL_USER_SAMPLE_THIN"
    operator_reading = "REAL_USER 자체가 유사 사용자 표본을 만들 만큼 충분하지 않습니다."
elif profiles_with_signals == 0 or sampled_targets == 0:
    decision_class = "PROFILE_SIGNAL_THIN"
    operator_reading = "유사도 계산에 쓸 프로필 신호가 부족합니다."
elif recent_view_users < min_similar_users:
    decision_class = "RECENT_VIEW_SAMPLE_THIN"
    operator_reading = "최근 조회 window 안의 REAL_USER 조회 표본이 부족해 결과가 비는 상태입니다."
elif targets_with_similar == 0:
    decision_class = "SIMILAR_USER_SAMPLE_THIN"
    operator_reading = "프로필 신호는 있지만 유사도 기준을 넘는 사용자 쌍이 부족합니다."
elif candidate_groups == 0:
    decision_class = "POLICY_CANDIDATE_EMPTY_AFTER_FILTERS"
    operator_reading = "유사 사용자는 있지만 정책 상태/청년/나이/소득 필터를 통과한 조회 후보가 없습니다."
elif min_sample_groups == 0:
    decision_class = "MIN_SIMILAR_USERS_GATE_EMPTY"
    operator_reading = "정책별 유사 사용자 최소 표본 기준을 통과한 후보가 없습니다."
elif result_groups == 0:
    decision_class = "DUPLICATE_OR_LATEST_BATCH_EXCLUSION_EMPTY"
    operator_reading = "후보는 있으나 현재 사용자 최근 조회 또는 최신 추천 batch 중복 제외 뒤 결과가 비었습니다."
else:
    decision_class = "OBSERVE_NONEMPTY_READY"
    operator_reading = "현재 표본에서는 similar-users-viewed 결과 후보가 생성될 수 있습니다. empty rate와 클릭/북마크 반응을 관찰합니다."

summary_lines = [
    "similar_users_viewed_audit=passed",
    f"artifact_dir={artifact_dir}",
    f"window_days={metrics.get('window_days', 0)}",
    f"min_similar_users={min_similar_users}",
    f"min_similarity_score={metrics.get('min_similarity_score', 0)}",
    f"target_user_limit={metrics.get('target_user_limit', 0)}",
    f"active_real_users={active_real_users}",
    f"active_real_profiles={metrics.get('active_real_profiles', 0)}",
    f"profiles_with_similarity_signals={profiles_with_signals}",
    f"recent_view_rows_window={metrics.get('recent_view_rows_window', 0)}",
    f"recent_view_users_window={recent_view_users}",
    f"recent_view_services_window={metrics.get('recent_view_services_window', 0)}",
    f"sampled_target_users={sampled_targets}",
    f"sampled_targets_with_eligible_similar_users={targets_with_similar}",
    f"eligible_similar_user_pairs={metrics.get('eligible_similar_user_pairs', 0)}",
    f"avg_eligible_similar_users={metrics.get('avg_eligible_similar_users', 0)}",
    f"max_eligible_similar_users={metrics.get('max_eligible_similar_users', 0)}",
    f"candidate_policy_groups_before_exclusions={candidate_groups}",
    f"candidate_policy_groups_min_sample={min_sample_groups}",
    f"excluded_by_own_recent_view_groups={metrics.get('excluded_by_own_recent_view_groups', 0)}",
    f"excluded_by_latest_recommendation_groups={metrics.get('excluded_by_latest_recommendation_groups', 0)}",
    f"result_policy_groups={result_groups}",
    f"targets_with_min_sample_policy_candidates={metrics.get('targets_with_min_sample_policy_candidates', 0)}",
    f"targets_with_result_candidates={metrics.get('targets_with_result_candidates', 0)}",
    f"expected_index_count={expected_index_count}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    "next_action=docs/recommendation/recommendation-similar-users-viewed-audit-runbook.md",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

payload = {
    "status": "passed",
    "artifactDir": artifact_dir,
    "metrics": metrics,
    "decisionClass": decision_class,
    "operatorReading": operator_reading,
    "nextAction": "docs/recommendation/recommendation-similar-users-viewed-audit-runbook.md",
}
json_out.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# similar-users-viewed audit note",
    "",
    f"- decision: `{decision_class}`",
    f"- reading: {operator_reading}",
    f"- active real users: `{active_real_users}`",
    f"- profiles with similarity signals: `{profiles_with_signals}`",
    f"- recent view users in window: `{recent_view_users}`",
    f"- sampled target users: `{sampled_targets}`",
    f"- targets with eligible similar users: `{targets_with_similar}`",
    f"- candidate policy groups before exclusions: `{candidate_groups}`",
    f"- result policy groups: `{result_groups}`",
    f"- expected index count: `{expected_index_count}`",
    "",
    "This audit is read-only and aggregate-only. It does not print user keys, emails, or raw behavior rows.",
]
note_out.write_text("\n".join(note_lines) + "\n", encoding="utf-8")

print(summary_out.read_text(encoding="utf-8"), end="")
PY

if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
  smoke_update_links \
    "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}" \
    "${SUMMARY_OUT}" "${LATEST_SUMMARY_LINK}" \
    "${JSON_OUT}" "${LATEST_JSON_LINK}" \
    "${NOTE_OUT}" "${LATEST_NOTE_LINK}"
fi
