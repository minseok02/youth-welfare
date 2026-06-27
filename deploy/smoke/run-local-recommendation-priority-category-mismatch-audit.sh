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
ARTIFACT_ROOT="${ARTIFACT_ROOT:-${ROOT_DIR}/tmp/recommendation-priority-category-mismatch-audit}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${RUN_TS_UTC}}"
mkdir -p "${ARTIFACT_DIR}"

if ! docker ps --format '{{.Names}}' | grep -Fxq "${DB_CONTAINER_NAME}"; then
  echo "db container not running: ${DB_CONTAINER_NAME}" >&2
  exit 1
fi

MISMATCH_OUT="${ARTIFACT_DIR}/priority-category-mismatch.out"
TOP1_OUT="${ARTIFACT_DIR}/priority-top1-samples.out"
SUMMARY_OUT="${ARTIFACT_DIR}/priority-category-mismatch-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/priority-category-mismatch-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/priority-category-mismatch-note.md"

smoke_print_step "priority/category mismatch aggregate"
docker exec -i "${DB_CONTAINER_NAME}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -At -F $'\t' <<'SQL' | tee "${MISMATCH_OUT}"
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
top1 AS (
    SELECT ur.user_key,
           service_id,
           ROW_NUMBER() OVER (
               PARTITION BY ur.user_key
               ORDER BY ur.final_score DESC, ur.id DESC
           ) AS rn
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
),
first_priority AS (
    SELECT up.user_key,
           po.code AS first_code
    FROM user_priorities up
    JOIN priority_options po
      ON po.id = up.priority_option_id
    WHERE up.priority_rank = 1
),
scored AS (
    SELECT fp.first_code,
           COALESCE(ws.unified_category, '기타') AS top1_category,
           CASE
               WHEN fp.first_code = 'HOUSING' AND COALESCE(ws.unified_category, '기타') = '주거' THEN 1
               WHEN fp.first_code = 'JOB' AND COALESCE(ws.unified_category, '기타') = '일자리' THEN 1
               WHEN fp.first_code = 'EDUCATION' AND COALESCE(ws.unified_category, '기타') = '교육·직업훈련' THEN 1
               WHEN fp.first_code = 'FINANCE' AND COALESCE(ws.unified_category, '기타') = '금융·생활지원' THEN 1
               WHEN fp.first_code = 'CULTURE' AND COALESCE(ws.unified_category, '기타') = '문화·여가' THEN 1
               WHEN fp.first_code = 'PARTICIPATION' AND COALESCE(ws.unified_category, '기타') = '참여·기회' THEN 1
               WHEN fp.first_code = 'FAMILY' AND COALESCE(ws.unified_category, '기타') = '가족·돌봄' THEN 1
               ELSE 0
           END AS matches_first_priority
    FROM top1 t
    JOIN first_priority fp
      ON fp.user_key = t.user_key
    JOIN welfare_services ws
      ON ws.id = t.service_id
    WHERE t.rn = 1
)
SELECT first_code,
       COUNT(*) AS users,
       SUM(matches_first_priority) AS matched_users,
       COUNT(*) - SUM(matches_first_priority) AS mismatched_users,
       ROUND((COUNT(*) - SUM(matches_first_priority)) * 100.0 / COUNT(*), 2) AS mismatch_pct
FROM scored
GROUP BY first_code
ORDER BY mismatch_pct DESC, first_code;
SQL

smoke_print_step "priority/category top1 samples"
docker exec -i "${DB_CONTAINER_NAME}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -At -F $'\t' <<'SQL' | tee "${TOP1_OUT}"
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
top1 AS (
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
),
first_priority AS (
    SELECT up.user_key,
           po.code AS first_code
    FROM user_priorities up
    JOIN priority_options po
      ON po.id = up.priority_option_id
    WHERE up.priority_rank = 1
)
SELECT fp.first_code,
       ws.title,
       COALESCE(ws.unified_category, '기타') AS category,
       COUNT(*) AS users
FROM top1 t
JOIN first_priority fp
  ON fp.user_key = t.user_key
JOIN welfare_services ws
  ON ws.id = t.service_id
WHERE t.rn = 1
GROUP BY fp.first_code, ws.title, COALESCE(ws.unified_category, '기타')
ORDER BY fp.first_code, users DESC, category;
SQL

python3 - "${MISMATCH_OUT}" "${TOP1_OUT}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" <<'PY'
import json
import sys
from collections import defaultdict
from datetime import UTC, datetime, timedelta, timezone
from pathlib import Path

mismatch_path = Path(sys.argv[1])
top1_path = Path(sys.argv[2])
summary_path = Path(sys.argv[3])
json_path = Path(sys.argv[4])
note_path = Path(sys.argv[5])

priority_metrics = []
for line in mismatch_path.read_text(encoding="utf-8").splitlines():
    if not line.strip():
        continue
    first_code, users, matched, mismatched, mismatch_pct = line.split("\t")
    priority_metrics.append({
        "firstCode": first_code,
        "users": int(users),
        "matchedUsers": int(matched),
        "mismatchedUsers": int(mismatched),
        "mismatchPct": float(mismatch_pct),
    })

top1_rows = []
for line in top1_path.read_text(encoding="utf-8").splitlines():
    if not line.strip():
        continue
    first_code, title, category, users = line.split("\t")
    top1_rows.append({
        "firstCode": first_code,
        "title": title,
        "category": category,
        "users": int(users),
    })

top1_by_priority = defaultdict(list)
for row in top1_rows:
    top1_by_priority[row["firstCode"]].append(row)

education_metric = next((row for row in priority_metrics if row["firstCode"] == "EDUCATION"), None)
education_top = top1_by_priority.get("EDUCATION", [])[:8]
housing_metric = next((row for row in priority_metrics if row["firstCode"] == "HOUSING"), None)
finance_metric = next((row for row in priority_metrics if row["firstCode"] == "FINANCE"), None)
family_metric = next((row for row in priority_metrics if row["firstCode"] == "FAMILY"), None)

decision = "BASELINE_HEALTHY"
if education_metric and education_metric["mismatchPct"] >= 50:
    decision = "EDUCATION_PRIORITY_MISMATCH"

generated_at_utc = datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")
generated_at_kst = datetime.now(timezone(timedelta(hours=9))).replace(microsecond=0).isoformat()

summary_lines = [
    f"generated_at_utc={generated_at_utc}",
    f"generated_at_kst={generated_at_kst}",
    f"decision_class={decision}",
]
for row in priority_metrics:
    prefix = row["firstCode"].lower()
    summary_lines.extend([
        f"{prefix}_users={row['users']}",
        f"{prefix}_matched_users={row['matchedUsers']}",
        f"{prefix}_mismatched_users={row['mismatchedUsers']}",
        f"{prefix}_mismatch_pct={row['mismatchPct']:.2f}",
    ])

if education_top:
    leader = education_top[0]
    summary_lines.extend([
        f"education_top1_title={leader['title']}",
        f"education_top1_category={leader['category']}",
        f"education_top1_users={leader['users']}",
    ])

summary_path.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "generatedAtUtc": generated_at_utc,
    "generatedAtKst": generated_at_kst,
    "decisionClass": decision,
    "priorityMetrics": priority_metrics,
    "educationTop1Samples": education_top,
    "comparisonHighlights": {
        "housing": housing_metric,
        "finance": finance_metric,
        "family": family_metric,
        "education": education_metric,
    },
}
json_path.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Recommendation Priority/Category Mismatch Audit",
    "",
    f"- decision_class: `{decision}`",
    "",
    "## mismatch summary",
]
for row in priority_metrics:
    note_lines.append(
        f"- `{row['firstCode']}`: users=`{row['users']}`, matched=`{row['matchedUsers']}`, mismatched=`{row['mismatchedUsers']}`, mismatch_pct=`{row['mismatchPct']:.2f}`"
    )

note_lines.extend([
    "",
    "## education top1 samples",
])
for row in education_top:
    note_lines.append(
        f"- `{row['title']}` / `{row['category']}` / `{row['users']}` users"
    )

note_path.write_text("\n".join(note_lines) + "\n", encoding="utf-8")
PY

smoke_sanitize_artifacts "${ARTIFACT_DIR}"
smoke_update_links \
  "${ARTIFACT_DIR}" "${ARTIFACT_ROOT}/latest" \
  "${SUMMARY_OUT}" "${ARTIFACT_ROOT}/latest-priority-category-mismatch-summary.txt" \
  "${JSON_OUT}" "${ARTIFACT_ROOT}/latest-priority-category-mismatch-summary.json" \
  "${NOTE_OUT}" "${ARTIFACT_ROOT}/latest-priority-category-mismatch-note.md"

cat "${SUMMARY_OUT}"
