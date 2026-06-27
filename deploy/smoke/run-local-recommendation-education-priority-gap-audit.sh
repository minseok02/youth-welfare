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
ARTIFACT_ROOT="${ARTIFACT_ROOT:-${ROOT_DIR}/tmp/recommendation-education-priority-gap-audit}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${RUN_TS_UTC}}"
mkdir -p "${ARTIFACT_DIR}"

if ! docker ps --format '{{.Names}}' | grep -Fxq "${DB_CONTAINER_NAME}"; then
  echo "db container not running: ${DB_CONTAINER_NAME}" >&2
  exit 1
fi

ROOT_CAUSE_OUT="${ARTIFACT_DIR}/education-priority-root-cause.out"
MISSING_EDU_OUT="${ARTIFACT_DIR}/education-priority-missing-candidate-samples.out"
SUMMARY_OUT="${ARTIFACT_DIR}/education-priority-gap-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/education-priority-gap-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/education-priority-gap-note.md"

smoke_print_step "education priority gap root cause aggregate"
docker exec -i "${DB_CONTAINER_NAME}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -At -F $'\t' <<'SQL' | tee "${ROOT_CAUSE_OUT}"
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
latest_rows AS (
    SELECT ur.user_key,
           ur.service_id,
           ur.rule_weighted_score,
           ur.ai_score,
           ur.final_score,
           ws.title,
           COALESCE(ws.unified_category, '기타') AS category,
           ROW_NUMBER() OVER (
               PARTITION BY ur.user_key
               ORDER BY ur.final_score DESC, ur.id DESC
           ) AS rn_all
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
    JOIN education_users eu
      ON eu.user_key = ur.user_key
    JOIN welfare_services ws
      ON ws.id = ur.service_id
),
education_best AS (
    SELECT DISTINCT ON (user_key)
           user_key,
           title,
           rule_weighted_score,
           ai_score,
           final_score
    FROM latest_rows
    WHERE category = '교육·직업훈련'
    ORDER BY user_key, final_score DESC
)
SELECT winner.category,
       COUNT(*) AS users,
       SUM(CASE WHEN edu.user_key IS NULL THEN 1 ELSE 0 END) AS missing_education_candidate_users,
       SUM(CASE WHEN edu.user_key IS NOT NULL THEN 1 ELSE 0 END) AS users_with_education_candidate,
       ROUND(AVG(winner.final_score), 4) AS avg_winner_final,
       ROUND(AVG(COALESCE(edu.final_score, 0)), 4) AS avg_best_education_final,
       ROUND(AVG(winner.final_score - COALESCE(edu.final_score, 0)), 4) AS avg_gap_vs_education,
       ROUND(AVG(COALESCE(winner.rule_weighted_score, 0)), 4) AS avg_winner_rule,
       ROUND(AVG(COALESCE(edu.rule_weighted_score, 0)), 4) AS avg_education_rule,
       ROUND(AVG(COALESCE(winner.ai_score, 0)), 4) AS avg_winner_ai,
       ROUND(AVG(COALESCE(edu.ai_score, 0)), 4) AS avg_education_ai,
       ROUND(AVG(CASE WHEN edu.user_key IS NOT NULL THEN winner.final_score - edu.final_score END), 4) AS avg_gap_when_education_present
FROM latest_rows winner
LEFT JOIN education_best edu
  ON edu.user_key = winner.user_key
WHERE winner.rn_all = 1
GROUP BY winner.category
ORDER BY users DESC, winner.category;
SQL

smoke_print_step "education priority missing candidate samples"
docker exec -i "${DB_CONTAINER_NAME}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -At -F $'\t' <<'SQL' | tee "${MISSING_EDU_OUT}"
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
latest_rows AS (
    SELECT ur.user_key,
           ur.service_id,
           ur.rule_weighted_score,
           ur.ai_score,
           ur.final_score,
           ws.title,
           COALESCE(ws.unified_category, '기타') AS category,
           ROW_NUMBER() OVER (
               PARTITION BY ur.user_key
               ORDER BY ur.final_score DESC, ur.id DESC
           ) AS rn_all
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
    JOIN education_users eu
      ON eu.user_key = ur.user_key
    JOIN welfare_services ws
      ON ws.id = ur.service_id
),
education_best AS (
    SELECT DISTINCT ON (user_key)
           user_key,
           title,
           rule_weighted_score,
           ai_score,
           final_score
    FROM latest_rows
    WHERE category = '교육·직업훈련'
    ORDER BY user_key, final_score DESC
)
SELECT winner.category,
       winner.title,
       COUNT(*) AS users
FROM latest_rows winner
LEFT JOIN education_best edu
  ON edu.user_key = winner.user_key
WHERE winner.rn_all = 1
  AND winner.category <> '교육·직업훈련'
  AND edu.user_key IS NULL
GROUP BY winner.category, winner.title
ORDER BY users DESC, winner.category, winner.title
LIMIT 10;
SQL

python3 - "${ROOT_CAUSE_OUT}" "${MISSING_EDU_OUT}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" <<'PY'
import json
import sys
from datetime import UTC, datetime, timedelta, timezone
from pathlib import Path

root_cause_path = Path(sys.argv[1])
missing_path = Path(sys.argv[2])
summary_path = Path(sys.argv[3])
json_path = Path(sys.argv[4])
note_path = Path(sys.argv[5])

rows = []
for line in root_cause_path.read_text(encoding="utf-8").splitlines():
    if not line.strip():
        continue
    (
        category,
        users,
        missing_education_candidate_users,
        users_with_education_candidate,
        avg_winner_final,
        avg_best_education_final,
        avg_gap_vs_education,
        avg_winner_rule,
        avg_education_rule,
        avg_winner_ai,
        avg_education_ai,
        avg_gap_when_education_present,
    ) = line.split("\t")
    rows.append(
        {
            "winnerCategory": category,
            "users": int(users),
            "missingEducationCandidateUsers": int(missing_education_candidate_users),
            "usersWithEducationCandidate": int(users_with_education_candidate),
            "avgWinnerFinal": float(avg_winner_final),
            "avgBestEducationFinal": float(avg_best_education_final),
            "avgGapVsEducation": float(avg_gap_vs_education),
            "avgWinnerRule": float(avg_winner_rule),
            "avgEducationRule": float(avg_education_rule),
            "avgWinnerAi": float(avg_winner_ai),
            "avgEducationAi": float(avg_education_ai),
            "avgGapWhenEducationPresent": None if avg_gap_when_education_present == "" else float(avg_gap_when_education_present),
        }
    )

missing_rows = []
for line in missing_path.read_text(encoding="utf-8").splitlines():
    if not line.strip():
        continue
    category, title, users = line.split("\t")
    missing_rows.append(
        {
            "winnerCategory": category,
            "winnerTitle": title,
            "users": int(users),
        }
    )

education_row = next((row for row in rows if row["winnerCategory"] == "교육·직업훈련"), None)
job_row = next((row for row in rows if row["winnerCategory"] == "일자리"), None)
finance_row = next((row for row in rows if row["winnerCategory"] == "금융·생활지원"), None)
other_row = next((row for row in rows if row["winnerCategory"] == "기타"), None)

decision = "BASELINE_HEALTHY"
root_cause = "UNDETERMINED"
if job_row and finance_row:
    if job_row["users"] >= 20 and (job_row["avgGapWhenEducationPresent"] or 0) >= 0.10 and finance_row["missingEducationCandidateUsers"] >= 2:
        decision = "EDUCATION_PRIORITY_SIGNAL_SKEW"
        root_cause = "JOB_SIGNAL_SCORE_SKEW_AND_FINANCE_CANDIDATE_ABSENCE"

generated_at_utc = datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")
generated_at_kst = datetime.now(timezone(timedelta(hours=9))).replace(microsecond=0).isoformat()

summary_lines = [
    f"generated_at_utc={generated_at_utc}",
    f"generated_at_kst={generated_at_kst}",
    f"decision_class={decision}",
    f"root_cause_class={root_cause}",
]

if education_row:
    summary_lines.extend(
        [
            f"education_top1_users={education_row['users']}",
            f"education_missing_candidate_users={education_row['missingEducationCandidateUsers']}",
        ]
    )
if job_row:
    summary_lines.extend(
        [
            f"job_top1_users={job_row['users']}",
            f"job_missing_education_candidate_users={job_row['missingEducationCandidateUsers']}",
            f"job_avg_gap_when_education_present={job_row['avgGapWhenEducationPresent']:.4f}",
            f"job_avg_winner_rule={job_row['avgWinnerRule']:.4f}",
            f"job_avg_education_rule={job_row['avgEducationRule']:.4f}",
            f"job_avg_winner_ai={job_row['avgWinnerAi']:.4f}",
            f"job_avg_education_ai={job_row['avgEducationAi']:.4f}",
        ]
    )
if finance_row:
    summary_lines.extend(
        [
            f"finance_top1_users={finance_row['users']}",
            f"finance_missing_education_candidate_users={finance_row['missingEducationCandidateUsers']}",
            f"finance_avg_gap_when_education_present={(finance_row['avgGapWhenEducationPresent'] or 0):.4f}",
            f"finance_avg_winner_rule={finance_row['avgWinnerRule']:.4f}",
            f"finance_avg_education_rule={finance_row['avgEducationRule']:.4f}",
            f"finance_avg_winner_ai={finance_row['avgWinnerAi']:.4f}",
            f"finance_avg_education_ai={finance_row['avgEducationAi']:.4f}",
        ]
    )
if other_row:
    summary_lines.extend(
        [
            f"other_top1_users={other_row['users']}",
            f"other_missing_education_candidate_users={other_row['missingEducationCandidateUsers']}",
        ]
    )

if missing_rows:
    summary_lines.extend(
        [
            f"dominant_missing_candidate_title={missing_rows[0]['winnerTitle']}",
            f"dominant_missing_candidate_category={missing_rows[0]['winnerCategory']}",
            f"dominant_missing_candidate_users={missing_rows[0]['users']}",
        ]
    )

summary_path.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "generatedAtUtc": generated_at_utc,
    "generatedAtKst": generated_at_kst,
    "decisionClass": decision,
    "rootCauseClass": root_cause,
    "winnerCategoryBreakdown": rows,
    "missingEducationCandidateSamples": missing_rows,
}
json_path.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Recommendation Education Priority Gap Audit",
    "",
    f"- decision_class: `{decision}`",
    f"- root_cause_class: `{root_cause}`",
    "",
    "## winner category breakdown",
]
for row in rows:
    note_lines.append(
        f"- `{row['winnerCategory']}`: users=`{row['users']}`, missing_education_candidate_users=`{row['missingEducationCandidateUsers']}`, avg_gap_vs_education=`{row['avgGapVsEducation']:.4f}`, avg_winner_rule=`{row['avgWinnerRule']:.4f}`, avg_education_rule=`{row['avgEducationRule']:.4f}`, avg_winner_ai=`{row['avgWinnerAi']:.4f}`, avg_education_ai=`{row['avgEducationAi']:.4f}`"
    )

note_lines.extend([
    "",
    "## reading",
])
if job_row:
    note_lines.append(
        f"- `일자리` top1 users=`{job_row['users']}` 중 교육 경쟁 후보가 있는 사용자가 `{job_row['usersWithEducationCandidate']}`명이고, 그 경우 평균 final gap은 `{job_row['avgGapWhenEducationPresent']:.4f}` 입니다. rule score는 크게 벌어지지 않지만 AI/final 쪽 우위가 더 큽니다."
    )
if finance_row:
    note_lines.append(
        f"- `금융·생활지원` top1 users=`{finance_row['users']}` 중 `{finance_row['missingEducationCandidateUsers']}`명은 latest batch 상위 저장 row에 교육 카테고리 후보 자체가 없습니다."
    )
if missing_rows:
    note_lines.append(
        f"- 교육 후보 부재 대표 샘플은 `{missing_rows[0]['winnerTitle']}` / `{missing_rows[0]['winnerCategory']}` / `{missing_rows[0]['users']}` users 입니다."
    )

note_lines.extend([
    "",
    "## missing education candidate samples",
])
for row in missing_rows:
    note_lines.append(
        f"- `{row['winnerTitle']}` / `{row['winnerCategory']}` / `{row['users']}` users"
    )

note_path.write_text("\n".join(note_lines) + "\n", encoding="utf-8")
PY

smoke_sanitize_artifacts "${ARTIFACT_DIR}"
smoke_update_links \
  "${ARTIFACT_DIR}" "${ARTIFACT_ROOT}/latest" \
  "${SUMMARY_OUT}" "${ARTIFACT_ROOT}/latest-education-priority-gap-summary.txt" \
  "${JSON_OUT}" "${ARTIFACT_ROOT}/latest-education-priority-gap-summary.json" \
  "${NOTE_OUT}" "${ARTIFACT_ROOT}/latest-education-priority-gap-note.md"

cat "${SUMMARY_OUT}"
