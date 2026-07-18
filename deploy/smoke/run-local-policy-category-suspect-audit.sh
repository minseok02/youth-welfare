#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"
RUN_TS_UTC="${RUN_TS_UTC:-$(smoke_now_ts_utc)}"
ARTIFACT_ROOT="${ARTIFACT_ROOT:-${ROOT_DIR}/tmp/policy-category-suspect-audit}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${RUN_TS_UTC}}"
SAMPLE_LIMIT_PER_RULE="${SAMPLE_LIMIT_PER_RULE:-40}"

SUMMARY_OUT="${ARTIFACT_DIR}/policy-category-suspect-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/policy-category-suspect-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/policy-category-suspect-note.md"
SAMPLES_TSV="${ARTIFACT_DIR}/policy-category-suspect-samples.tsv"
COUNTS_TSV="${ARTIFACT_DIR}/policy-category-suspect-counts.tsv"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ "${KEEP_ARTIFACTS}" != "true" ]]; then
    rm -rf "${ARTIFACT_DIR}"
  fi
}
trap cleanup EXIT

mkdir -p "${ARTIFACT_DIR}"
smoke_require_command python3

if [[ ! "${SAMPLE_LIMIT_PER_RULE}" =~ ^[0-9]+$ ]] || (( SAMPLE_LIMIT_PER_RULE <= 0 || SAMPLE_LIMIT_PER_RULE > 200 )); then
  echo "SAMPLE_LIMIT_PER_RULE must be between 1 and 200" >&2
  exit 1
fi

smoke_db_query "
with suspect_rules(rule_key, expected_category, pattern, reason) as (
  values
    ('housing_finance_terms','주거','(통장|적금|저축|자산형성|디딤돌 통장|노동자 통장)','주거 카테고리 안의 금융/자산형성 강신호'),
    ('housing_job_terms','주거','(창업|취업|직업훈련|사업자|사업화)','주거 카테고리 안의 일자리/창업 강신호'),
    ('housing_exam_terms','주거','(면접|자격|시험|응시료|교육)','주거 카테고리 안의 교육/시험 강신호'),
    ('finance_housing_terms','금융·생활지원','(월세|전세|임대|주택|주거|보증금|청약)','금융 카테고리 안의 주거 강신호'),
    ('job_housing_terms','일자리','(월세|전세|임대|주택|주거|보증금|이사비)','일자리 카테고리 안의 주거 강신호'),
    ('culture_finance_terms','문화·여가','(대출|융자|이자|통장|적금|저축|생활비)','문화 카테고리 안의 금융 강신호')
),
matched as (
  select sr.rule_key,
         sr.expected_category,
         sr.reason,
         ws.id,
         ws.source_type,
         ws.unified_category,
         replace(ws.title, chr(9), ' ') as title,
         replace(coalesce(ws.description, ''), chr(9), ' ') as description,
         replace(coalesce(ws.support_content, ''), chr(9), ' ') as support_content,
         coalesce(ws.api_view_count, 0) as api_view_count,
         coalesce(ws.view_count, 0) as view_count,
         row_number() over (
           partition by sr.rule_key
           order by coalesce(ws.api_view_count, 0) desc,
                    coalesce(ws.view_count, 0) desc,
                    ws.id desc
         ) as rn
  from suspect_rules sr
  join welfare_services ws
    on ws.search_youth_relevant is true
   and ws.status in ('ACTIVE','UPCOMING')
   and ws.unified_category = sr.expected_category
   and (
        coalesce(ws.title, '') ~ sr.pattern
        or coalesce(ws.description, '') ~ sr.pattern
        or coalesce(ws.support_content, '') ~ sr.pattern
        or coalesce(ws.keyword, '') ~ sr.pattern
   )
)
select rule_key,
       expected_category,
       reason,
       count(*) as suspect_count
from matched
group by rule_key, expected_category, reason
order by suspect_count desc, rule_key;
" > "${COUNTS_TSV}"

smoke_db_query "
with suspect_rules(rule_key, expected_category, pattern, reason) as (
  values
    ('housing_finance_terms','주거','(통장|적금|저축|자산형성|디딤돌 통장|노동자 통장)','주거 카테고리 안의 금융/자산형성 강신호'),
    ('housing_job_terms','주거','(창업|취업|직업훈련|사업자|사업화)','주거 카테고리 안의 일자리/창업 강신호'),
    ('housing_exam_terms','주거','(면접|자격|시험|응시료|교육)','주거 카테고리 안의 교육/시험 강신호'),
    ('finance_housing_terms','금융·생활지원','(월세|전세|임대|주택|주거|보증금|청약)','금융 카테고리 안의 주거 강신호'),
    ('job_housing_terms','일자리','(월세|전세|임대|주택|주거|보증금|이사비)','일자리 카테고리 안의 주거 강신호'),
    ('culture_finance_terms','문화·여가','(대출|융자|이자|통장|적금|저축|생활비)','문화 카테고리 안의 금융 강신호')
),
matched as (
  select sr.rule_key,
         sr.expected_category,
         sr.reason,
         ws.id,
         ws.source_type,
         ws.unified_category,
         replace(ws.title, chr(9), ' ') as title,
         replace(coalesce(ws.host_org, ''), chr(9), ' ') as host_org,
         replace(coalesce(ws.support_content, ''), chr(9), ' ') as support_content,
         coalesce(ws.api_view_count, 0) as api_view_count,
         coalesce(ws.view_count, 0) as view_count,
         row_number() over (
           partition by sr.rule_key
           order by coalesce(ws.api_view_count, 0) desc,
                    coalesce(ws.view_count, 0) desc,
                    ws.id desc
         ) as rn
  from suspect_rules sr
  join welfare_services ws
    on ws.search_youth_relevant is true
   and ws.status in ('ACTIVE','UPCOMING')
   and ws.unified_category = sr.expected_category
   and (
        coalesce(ws.title, '') ~ sr.pattern
        or coalesce(ws.description, '') ~ sr.pattern
        or coalesce(ws.support_content, '') ~ sr.pattern
        or coalesce(ws.keyword, '') ~ sr.pattern
   )
)
select rule_key,
       expected_category,
       reason,
       id,
       source_type,
       unified_category,
       title,
       host_org,
       left(support_content, 180),
       api_view_count,
       view_count
from matched
where rn <= ${SAMPLE_LIMIT_PER_RULE}
order by rule_key, rn;
" > "${SAMPLES_TSV}"

python3 - "${COUNTS_TSV}" "${SAMPLES_TSV}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" <<'PY'
import csv
import json
import sys
from collections import defaultdict
from pathlib import Path

counts_path = Path(sys.argv[1])
samples_path = Path(sys.argv[2])
summary_path = Path(sys.argv[3])
json_path = Path(sys.argv[4])
note_path = Path(sys.argv[5])
artifact_dir = sys.argv[6]

counts = []
with counts_path.open(encoding="utf-8") as fp:
    for row in csv.reader(fp, delimiter="\t"):
        if row:
            counts.append({
                "ruleKey": row[0],
                "expectedCategory": row[1],
                "reason": row[2],
                "suspectCount": int(row[3]),
            })

samples_by_rule = defaultdict(list)
with samples_path.open(encoding="utf-8") as fp:
    for row in csv.reader(fp, delimiter="\t"):
        if row:
            sample = {
                "ruleKey": row[0],
                "expectedCategory": row[1],
                "reason": row[2],
                "serviceId": int(row[3]),
                "sourceType": row[4],
                "unifiedCategory": row[5],
                "title": row[6],
                "hostOrg": row[7],
                "supportPreview": row[8],
                "apiViewCount": int(row[9]),
                "viewCount": int(row[10]),
            }
            samples_by_rule[row[0]].append(sample)

total = sum(item["suspectCount"] for item in counts)
housing_finance = next((item["suspectCount"] for item in counts if item["ruleKey"] == "housing_finance_terms"), 0)
decision = "ATTENTION" if total else "PASSED"
if housing_finance >= 10:
    decision = "REVIEW_PRIORITY"

summary_lines = [
    "policy_category_suspect_audit=completed",
    f"artifact_dir={artifact_dir}",
    f"rule_count={len(counts)}",
    f"total_suspect_rows={total}",
    f"housing_finance_suspect_rows={housing_finance}",
    f"decision={decision}",
]
for item in counts:
    summary_lines.append(
        f"rule={item['ruleKey']}|category={item['expectedCategory']}|count={item['suspectCount']}|reason={item['reason']}"
    )
summary_path.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

payload = {
    "artifactDir": artifact_dir,
    "decision": decision,
    "totalSuspectRows": total,
    "counts": counts,
    "samplesByRule": samples_by_rule,
}
json_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Policy Category Suspect Audit",
    "",
    f"- decision: `{decision}`",
    f"- total_suspect_rows: `{total}`",
    f"- housing_finance_suspect_rows: `{housing_finance}`",
    "",
    "This is a heuristic sampling audit. It does not change policy categories automatically.",
    "",
    "## Rules",
]
for item in counts:
    note_lines.append(
        f"- `{item['ruleKey']}` category=`{item['expectedCategory']}` count=`{item['suspectCount']}` reason=`{item['reason']}`"
    )
note_lines.extend(["", "## Top Samples"])
for item in counts:
    note_lines.append("")
    note_lines.append(f"### {item['ruleKey']}")
    for sample in samples_by_rule.get(item["ruleKey"], [])[:10]:
        note_lines.append(
            f"- `{sample['serviceId']}` `{sample['title']}` source=`{sample['sourceType']}` "
            f"views=`{sample['apiViewCount']}/{sample['viewCount']}` host=`{sample['hostOrg']}`"
        )
note_path.write_text("\n".join(note_lines) + "\n", encoding="utf-8")

print(summary_path.read_text(encoding="utf-8"), end="")
PY

smoke_sanitize_artifacts "${ARTIFACT_DIR}"
smoke_update_links \
  "${ARTIFACT_DIR}" "${ARTIFACT_ROOT}/latest" \
  "${SUMMARY_OUT}" "${ARTIFACT_ROOT}/latest-policy-category-suspect-summary.txt" \
  "${JSON_OUT}" "${ARTIFACT_ROOT}/latest-policy-category-suspect-summary.json" \
  "${NOTE_OUT}" "${ARTIFACT_ROOT}/latest-policy-category-suspect-note.md"
