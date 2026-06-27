#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

smoke_require_command bash
smoke_require_command docker
smoke_require_command python3

DB_CONTAINER_NAME="${DB_CONTAINER_NAME:-youth-welfare-db}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_DB="${POSTGRES_DB:-youth_welfare}"
RUN_TS_UTC="${RUN_TS_UTC:-$(smoke_now_ts_utc)}"
ARTIFACT_ROOT="${ARTIFACT_ROOT:-${ROOT_DIR}/tmp/recommendation-education-priority-signal-audit}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${RUN_TS_UTC}}"
mkdir -p "${ARTIFACT_DIR}"

if ! docker ps --format '{{.Names}}' | grep -Fxq "${DB_CONTAINER_NAME}"; then
  echo "db container not running: ${DB_CONTAINER_NAME}" >&2
  exit 1
fi

SERVICE_SIGNAL_OUT="${ARTIFACT_DIR}/education-priority-winner-service-signals.out"
SUMMARY_OUT="${ARTIFACT_DIR}/education-priority-signal-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/education-priority-signal-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/education-priority-signal-note.md"

smoke_print_step "education priority mismatch winner signals"
docker exec -i "${DB_CONTAINER_NAME}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -At -F $'\t' <<'SQL' | tee "${SERVICE_SIGNAL_OUT}"
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
education_users AS (
    SELECT up.user_key
    FROM user_priorities up
    JOIN priority_options po
      ON po.id = up.priority_option_id
    WHERE up.priority_rank = 1
      AND po.code = 'EDUCATION'
),
latest_top1 AS (
    SELECT ur.user_key,
           ur.service_id,
           ROW_NUMBER() OVER (
               PARTITION BY ur.user_key
               ORDER BY ur.final_score DESC, ur.id DESC
           ) AS rn
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
    JOIN education_users eu
      ON eu.user_key = ur.user_key
),
mismatch_winners AS (
    SELECT t.user_key,
           ws.id AS service_id,
           ws.title,
           COALESCE(ws.unified_category, '기타') AS category
    FROM latest_top1 t
    JOIN welfare_services ws
      ON ws.id = t.service_id
    WHERE t.rn = 1
      AND COALESCE(ws.unified_category, '기타') <> '교육·직업훈련'
),
term_rollup AS (
    SELECT stt.service_id,
           string_agg(DISTINCT CASE WHEN stt.term_group = 'INTEREST_THEME' THEN stt.term_label END, '||') FILTER (WHERE stt.term_group = 'INTEREST_THEME') AS interest_themes,
           string_agg(DISTINCT CASE WHEN stt.term_group = 'LIFE_STAGE' THEN stt.term_label END, '||') FILTER (WHERE stt.term_group = 'LIFE_STAGE') AS life_stages,
           string_agg(DISTINCT CASE WHEN stt.term_group = 'GOV24_SERVICE_FIELD' THEN stt.term_label END, '||') FILTER (WHERE stt.term_group = 'GOV24_SERVICE_FIELD') AS gov24_service_fields,
           string_agg(DISTINCT CASE WHEN stt.term_group = 'GOV24_BENEFIT_TYPE_TOKEN' THEN stt.term_label END, '||') FILTER (WHERE stt.term_group = 'GOV24_BENEFIT_TYPE_TOKEN') AS gov24_benefit_types
    FROM service_taxonomy_terms stt
    GROUP BY stt.service_id
),
fact_rollup AS (
    SELECT sf.service_id,
           COUNT(*) FILTER (WHERE sf.fact_group = 'EDUCATION') AS education_fact_count,
           COUNT(*) FILTER (WHERE sf.fact_merge_key LIKE 'GOV24_BUSINESS_STAGE:%') AS business_stage_fact_count,
           string_agg(DISTINCT CASE WHEN sf.fact_group = 'EDUCATION' THEN sf.fact_merge_key END, '||') FILTER (WHERE sf.fact_group = 'EDUCATION') AS education_fact_keys,
           string_agg(DISTINCT CASE WHEN sf.fact_group = 'AGE' THEN COALESCE(sf.raw_value, sf.text_value, sf.fact_merge_key) END, '||') FILTER (WHERE sf.fact_group = 'AGE') AS age_fact_summary
    FROM service_facts sf
    GROUP BY sf.service_id
)
SELECT mw.title,
       mw.category,
       COUNT(*) AS users,
       COALESCE(st.compat_unified_category_label, '') AS compat_category_label,
       COALESCE(st.youth_major_label, '') AS youth_major_label,
       COALESCE(term.gov24_service_fields, '') AS gov24_service_fields,
       COALESCE(term.gov24_benefit_types, '') AS gov24_benefit_types,
       COALESCE(term.interest_themes, '') AS interest_themes,
       COALESCE(term.life_stages, '') AS life_stages,
       COALESCE(fact.education_fact_count, 0) AS education_fact_count,
       COALESCE(fact.business_stage_fact_count, 0) AS business_stage_fact_count,
       COALESCE(fact.age_fact_summary, '') AS age_fact_summary,
       COALESCE(fact.education_fact_keys, '') AS education_fact_keys
FROM mismatch_winners mw
LEFT JOIN service_taxonomies st
  ON st.service_id = mw.service_id
LEFT JOIN term_rollup term
  ON term.service_id = mw.service_id
LEFT JOIN fact_rollup fact
  ON fact.service_id = mw.service_id
GROUP BY mw.title, mw.category,
         st.compat_unified_category_label,
         st.youth_major_label,
         term.gov24_service_fields,
         term.gov24_benefit_types,
         term.interest_themes,
         term.life_stages,
         fact.education_fact_count,
         fact.business_stage_fact_count,
         fact.age_fact_summary,
         fact.education_fact_keys
ORDER BY users DESC, mw.category, mw.title
LIMIT 20;
SQL

python3 - "${SERVICE_SIGNAL_OUT}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" <<'PY'
import json
import sys
from datetime import UTC, datetime, timedelta, timezone
from pathlib import Path

signal_path = Path(sys.argv[1])
summary_path = Path(sys.argv[2])
json_path = Path(sys.argv[3])
note_path = Path(sys.argv[4])

rows = []
for line in signal_path.read_text(encoding="utf-8").splitlines():
    if not line.strip():
        continue
    (
        title,
        category,
        users,
        compat_category_label,
        youth_major_label,
        gov24_service_fields,
        gov24_benefit_types,
        interest_themes,
        life_stages,
        education_fact_count,
        business_stage_fact_count,
        age_fact_summary,
        education_fact_keys,
    ) = line.split("\t")
    rows.append(
        {
            "title": title,
            "category": category,
            "users": int(users),
            "compatCategoryLabel": compat_category_label,
            "youthMajorLabel": youth_major_label,
            "gov24ServiceFields": gov24_service_fields,
            "gov24BenefitTypes": gov24_benefit_types,
            "interestThemes": interest_themes,
            "lifeStages": life_stages,
            "educationFactCount": int(education_fact_count),
            "businessStageFactCount": int(business_stage_fact_count),
            "ageFactSummary": age_fact_summary,
            "educationFactKeys": education_fact_keys,
        }
    )

job_rows = [row for row in rows if row["category"] == "일자리"]
finance_rows = [row for row in rows if row["category"] == "금융·생활지원"]

job_with_job_field = sum(1 for row in job_rows if "고용·창업" in row["gov24ServiceFields"])
finance_with_education_theme = sum(1 for row in finance_rows if "교육" in row["interestThemes"])
finance_without_education_facts = sum(1 for row in finance_rows if row["educationFactCount"] == 0)

decision = "BASELINE_HEALTHY"
signal_class = "UNDETERMINED"
if job_rows or finance_rows:
    decision = "EDUCATION_PRIORITY_SIGNAL_PROFILE_CONFIRMED"
    signal_class = "JOB_FIELD_AND_MIXED_THEME_WINNERS"

generated_at_utc = datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")
generated_at_kst = datetime.now(timezone(timedelta(hours=9))).replace(microsecond=0).isoformat()

summary_lines = [
    f"generated_at_utc={generated_at_utc}",
    f"generated_at_kst={generated_at_kst}",
    f"decision_class={decision}",
    f"signal_class={signal_class}",
    f"row_count={len(rows)}",
    f"job_winner_service_count={len(job_rows)}",
    f"job_winner_with_gov24_job_field_count={job_with_job_field}",
    f"finance_winner_service_count={len(finance_rows)}",
    f"finance_winner_with_education_theme_count={finance_with_education_theme}",
    f"finance_winner_without_education_fact_count={finance_without_education_facts}",
]
if rows:
    leader = rows[0]
    summary_lines.extend(
        [
            f"top_signal_title={leader['title']}",
            f"top_signal_category={leader['category']}",
            f"top_signal_users={leader['users']}",
        ]
    )
summary_path.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "generatedAtUtc": generated_at_utc,
    "generatedAtKst": generated_at_kst,
    "decisionClass": decision,
    "signalClass": signal_class,
    "rows": rows,
    "signalHighlights": {
        "jobWinnerWithGov24JobFieldCount": job_with_job_field,
        "financeWinnerWithEducationThemeCount": finance_with_education_theme,
        "financeWinnerWithoutEducationFactCount": finance_without_education_facts,
    },
}
json_path.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Recommendation Education Priority Signal Audit",
    "",
    f"- decision_class: `{decision}`",
    f"- signal_class: `{signal_class}`",
    "",
    "## signal highlights",
    f"- `일자리` winner services: `{len(job_rows)}`",
    f"- 그중 `GOV24_SERVICE_FIELD=고용·창업` 포함: `{job_with_job_field}`",
    f"- `금융·생활지원` winner services: `{len(finance_rows)}`",
    f"- 그중 `INTEREST_THEME=교육` 포함: `{finance_with_education_theme}`",
    f"- 그중 `education_fact_count=0`: `{finance_without_education_facts}`",
    "",
    "## winner service signals",
]
for row in rows:
    note_lines.append(
        f"- `{row['title']}` / `{row['category']}` / `{row['users']}` users"
    )
    note_lines.append(
        f"  - gov24_field=`{row['gov24ServiceFields']}` benefit=`{row['gov24BenefitTypes']}` interest=`{row['interestThemes']}` life_stage=`{row['lifeStages']}` education_fact_count=`{row['educationFactCount']}` business_stage_fact_count=`{row['businessStageFactCount']}`"
    )

note_path.write_text("\n".join(note_lines) + "\n", encoding="utf-8")
PY

smoke_sanitize_artifacts "${ARTIFACT_DIR}"
smoke_update_links \
  "${ARTIFACT_DIR}" "${ARTIFACT_ROOT}/latest" \
  "${SUMMARY_OUT}" "${ARTIFACT_ROOT}/latest-education-priority-signal-summary.txt" \
  "${JSON_OUT}" "${ARTIFACT_ROOT}/latest-education-priority-signal-summary.json" \
  "${NOTE_OUT}" "${ARTIFACT_ROOT}/latest-education-priority-signal-note.md"

cat "${SUMMARY_OUT}"
