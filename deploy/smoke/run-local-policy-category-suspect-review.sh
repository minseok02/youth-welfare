#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"
RUN_TS_UTC="${RUN_TS_UTC:-$(smoke_now_ts_utc)}"
ARTIFACT_ROOT="${ARTIFACT_ROOT:-${ROOT_DIR}/tmp/policy-category-suspect-review}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${RUN_TS_UTC}}"
SAMPLE_LIMIT="${SAMPLE_LIMIT:-300}"

ROWS_TSV="${ARTIFACT_DIR}/policy-category-suspect-review-rows.tsv"
REVIEW_TSV="${ARTIFACT_DIR}/policy-category-suspect-review-classified.tsv"
SUMMARY_TXT="${ARTIFACT_DIR}/policy-category-suspect-review-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/policy-category-suspect-review-summary.json"
NOTE_MD="${ARTIFACT_DIR}/policy-category-suspect-review-note.md"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ "${KEEP_ARTIFACTS}" != "true" ]]; then
    rm -rf "${ARTIFACT_DIR}"
  fi
}
trap cleanup EXIT

mkdir -p "${ARTIFACT_DIR}"
smoke_require_command python3

if [[ ! "${SAMPLE_LIMIT}" =~ ^[0-9]+$ ]] || (( SAMPLE_LIMIT <= 0 || SAMPLE_LIMIT > 1000 )); then
  echo "SAMPLE_LIMIT must be between 1 and 1000" >&2
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
         ws.source_id,
         ws.unified_category,
         replace(coalesce(ws.category_main, ''), chr(9), ' ') as category_main,
         replace(coalesce(ws.category_sub, ''), chr(9), ' ') as category_sub,
         replace(coalesce(ws.keyword, ''), chr(9), ' ') as keyword,
         replace(ws.title, chr(9), ' ') as title,
         replace(coalesce(ws.description, ''), chr(9), ' ') as description,
         replace(coalesce(ws.support_content, ''), chr(9), ' ') as support_content,
         replace(coalesce(ws.host_org, ''), chr(9), ' ') as host_org,
         coalesce(ws.api_view_count, 0) as api_view_count,
         coalesce(ws.view_count, 0) as view_count,
         coalesce(string_agg(distinct st.tag_type || ':' || st.tag_value, '|' order by st.tag_type || ':' || st.tag_value), '') as tags
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
  left join service_tags st on st.service_id = ws.id
  group by sr.rule_key,
           sr.expected_category,
           sr.reason,
           ws.id,
           ws.source_type,
           ws.source_id,
           ws.unified_category,
           ws.category_main,
           ws.category_sub,
           ws.keyword,
           ws.title,
           ws.description,
           ws.support_content,
           ws.host_org,
           ws.api_view_count,
           ws.view_count
)
select rule_key,
       expected_category,
       reason,
       id,
       source_type,
       source_id,
       unified_category,
       category_main,
       category_sub,
       keyword,
       title,
       left(description, 260),
       left(support_content, 320),
       host_org,
       api_view_count,
       view_count,
       tags
from matched
order by api_view_count desc, view_count desc, id desc
limit ${SAMPLE_LIMIT};
" > "${ROWS_TSV}"

python3 - "${ROWS_TSV}" "${REVIEW_TSV}" "${SUMMARY_TXT}" "${SUMMARY_JSON}" "${NOTE_MD}" "${ARTIFACT_DIR}" <<'PY'
import csv
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

rows_path = Path(sys.argv[1])
review_path = Path(sys.argv[2])
summary_path = Path(sys.argv[3])
json_path = Path(sys.argv[4])
note_path = Path(sys.argv[5])
artifact_dir = sys.argv[6]

housing_re = re.compile(
    r"(청년월세|월세|전월세|전세|임차보증금|임대보증금|주거비|주거안정|주거자금|"
    r"임대주택|공공임대|매입임대|행복주택|이사비|중개보수|주택임차|주택구입|"
    r"주거환경|주거 안정|정주여건|기숙사|숙소|보금자리|셰어하우스|입주자|주거공간|연계주택)"
)
title_housing_re = re.compile(
    r"(청년월세|월세|전월세|전세|임차보증금|임대보증금|주거비|주거안정|주거자금|"
    r"임대주택|공공임대|매입임대|행복주택|이사비|중개보수|주택임차|주택구입|"
    r"주거 패키지|기숙사|숙소|보금자리|학숙|연계주택|주거지원|주거비 지원)"
)
weak_housing_re = re.compile(r"(주거급여|주거·자립|주거 마련|주거비용)")
housing_purpose_asset_re = re.compile(r"(주택드림|청약통장|주택청약|주거.*통장)")
finance_asset_re = re.compile(r"(통장|적금|저축|자산형성|자립정착금|생활안정자금)")
job_re = re.compile(r"(창업|소상공인|사업자|사업화|취업|근로자|노동자|직업훈련|일경험)")
education_re = re.compile(r"(자격시험|응시료|면접|어학|자격증|교육비|장학|학비|훈련)")
culture_re = re.compile(r"(문화|예술|공연|전시|영화|도서|체육|스포츠|관람)")
finance_re = re.compile(r"(대출|융자|이자|보증|자금|현금|생활비)")


def norm(value):
    return (value or "").strip()


def has(pattern, *values):
    return bool(pattern.search(" ".join(norm(value) for value in values)))


def classify(row):
    rule_key = row["ruleKey"]
    category = row["unifiedCategory"]
    source_type = row["sourceType"]
    field = row["categoryMain"]
    title = row["title"]
    description = row["description"]
    support = row["supportContent"]
    keyword = row["keyword"]
    tags = row["tags"]

    strong_housing = has(housing_re, title, description, support, keyword, tags)
    weak_housing = has(weak_housing_re, field, title, description, support, tags)
    asset = has(finance_asset_re, title, description, support, keyword, tags)
    job = has(job_re, title, description, support, keyword, tags)
    education = has(education_re, title, description, support, keyword, tags)
    culture = has(culture_re, field, title, description, support, keyword, tags)
    finance = has(finance_re, title, description, support, keyword, tags)
    title_housing = has(title_housing_re, title)
    housing_purpose_asset = has(housing_purpose_asset_re, title, description, support, keyword, tags)
    business_context = has(re.compile(r"(창업|사업자|소상공인|사업장|기업|농업인|공장|점포)"), title, description, support)
    travel_or_crisis_context = has(re.compile(r"(행려자|부랑인|숙식|친지방문|타지방.*여행)"), title, description, support)
    community_context = has(re.compile(r"(커뮤니티|동아리|네트워크|거버넌스|활동비|사회문제|문화예술)"), title, description, support)

    if category == "금융·생활지원" and asset and not title_housing:
        return (
            "LOW",
            "LIKELY_FALSE_POSITIVE_KEEP",
            category,
            "asset/self-reliance support can mention housing expenses, but the primary benefit is finance/life support",
        )
    if category == "금융·생활지원" and title_housing:
        return (
            "HIGH",
            "LIKELY_REMAP_TO_HOUSING",
            "주거",
            "title/support has direct housing benefit terms while current category is finance/life",
        )
    if category == "일자리" and title_housing and not business_context and "창업자금" not in title:
        return (
            "HIGH",
            "LIKELY_REMAP_TO_HOUSING",
            "주거",
            "job-targeted policy but benefit itself is housing cost/space",
        )
    if category == "일자리" and strong_housing and business_context:
        return (
            "MEDIUM",
            "MIXED_REVIEW",
            category,
            "employment/startup and housing benefits are both central",
        )
    if category == "일자리" and strong_housing:
        return (
            "MEDIUM",
            "MANUAL_REVIEW",
            category,
            "housing benefit appears outside the title; verify whether it is central or incidental",
        )
    if category == "주거" and asset and housing_purpose_asset:
        return (
            "LOW",
            "LIKELY_MIXED_KEEP",
            category,
            "asset-building product is explicitly tied to housing purpose",
        )
    if category == "주거" and asset and not strong_housing:
        return (
            "HIGH",
            "LIKELY_REMAP_TO_FINANCE",
            "금융·생활지원",
            "asset-building terms dominate and no direct housing benefit term is present",
        )
    if category == "주거" and job and travel_or_crisis_context:
        return (
            "MEDIUM",
            "MANUAL_REVIEW",
            category,
            "job term appears in travel/crisis context, not as a primary employment benefit",
        )
    if category == "주거" and job and community_context:
        return (
            "MEDIUM",
            "MANUAL_REVIEW",
            category,
            "community/activity support mentions employment as one field; category needs manual taxonomy review",
        )
    if category == "주거" and job and not strong_housing:
        return (
            "HIGH",
            "LIKELY_REMAP_TO_JOB",
            "일자리",
            "job/startup terms dominate and no direct housing benefit term is present",
        )
    if category == "문화·여가" and finance and not culture:
        return (
            "MEDIUM",
            "LIKELY_REMAP_TO_FINANCE",
            "금융·생활지원",
            "finance terms dominate without culture signal",
        )
    if category == "주거" and (job or education or asset) and strong_housing:
        return (
            "LOW",
            "LIKELY_MIXED_KEEP",
            category,
            "non-housing eligibility/context exists but direct housing benefit is present",
        )
    if category == "금융·생활지원" and weak_housing and not strong_housing:
        return (
            "LOW",
            "LIKELY_FALSE_POSITIVE_KEEP",
            category,
            "housing term appears as eligibility/self-reliance context, not direct housing benefit",
        )
    if category == "문화·여가" and culture:
        return (
            "LOW",
            "LIKELY_FALSE_POSITIVE_KEEP",
            category,
            "culture signal is central; financial term appears as support method/amount",
        )
    if rule_key == "housing_exam_terms" and category == "주거" and strong_housing:
        return (
            "LOW",
            "LIKELY_MIXED_KEEP",
            category,
            "education/exam term is context, but direct housing benefit is present",
        )
    return (
        "MEDIUM",
        "MANUAL_REVIEW",
        category,
        f"insufficient heuristic confidence source={source_type} field={field}",
    )


rows = []
with rows_path.open(encoding="utf-8") as fp:
    for raw in csv.reader(fp, delimiter="\t"):
        if not raw:
            continue
        row = {
            "ruleKey": raw[0],
            "expectedCategory": raw[1],
            "reason": raw[2],
            "serviceId": int(raw[3]),
            "sourceType": raw[4],
            "sourceId": raw[5],
            "unifiedCategory": raw[6],
            "categoryMain": raw[7],
            "categorySub": raw[8],
            "keyword": raw[9],
            "title": raw[10],
            "description": raw[11],
            "supportContent": raw[12],
            "hostOrg": raw[13],
            "apiViewCount": int(raw[14]),
            "viewCount": int(raw[15]),
            "tags": raw[16],
        }
        severity, decision, proposed, rationale = classify(row)
        row.update({
            "severity": severity,
            "reviewDecision": decision,
            "proposedCategory": proposed,
            "rationale": rationale,
        })
        rows.append(row)

fieldnames = [
    "severity",
    "reviewDecision",
    "proposedCategory",
    "ruleKey",
    "serviceId",
    "sourceType",
    "sourceId",
    "unifiedCategory",
    "categoryMain",
    "categorySub",
    "keyword",
    "title",
    "apiViewCount",
    "viewCount",
    "rationale",
    "description",
    "supportContent",
    "hostOrg",
    "tags",
]
with review_path.open("w", encoding="utf-8", newline="") as fp:
    writer = csv.DictWriter(fp, fieldnames=fieldnames, delimiter="\t", extrasaction="ignore")
    for row in rows:
        writer.writerow(row)

decision_counts = Counter(row["reviewDecision"] for row in rows)
severity_counts = Counter(row["severity"] for row in rows)
rule_decision_counts = Counter((row["ruleKey"], row["reviewDecision"]) for row in rows)
high_rows = [row for row in rows if row["severity"] == "HIGH"]
high_unique_rows = []
seen_high_services = set()
for row in high_rows:
    key = (row["serviceId"], row["proposedCategory"])
    if key in seen_high_services:
        continue
    seen_high_services.add(key)
    high_unique_rows.append(row)
high_by_proposed = Counter(row["proposedCategory"] for row in high_rows)
high_by_source = Counter(row["sourceType"] for row in high_rows)
high_unique_by_proposed = Counter(row["proposedCategory"] for row in high_unique_rows)
high_unique_by_source = Counter(row["sourceType"] for row in high_unique_rows)

decision = "PASSED"
if high_rows:
    decision = "REMAP_CANDIDATE_REVIEW"
elif any(row["severity"] == "MEDIUM" for row in rows):
    decision = "MANUAL_REVIEW"

summary_lines = [
    "policy_category_suspect_review=completed",
    f"artifact_dir={artifact_dir}",
    f"sampled_rows={len(rows)}",
    f"high_confidence_rows={len(high_rows)}",
    f"high_confidence_unique_services={len(high_unique_rows)}",
    f"decision={decision}",
]
for key, count in sorted(severity_counts.items()):
    summary_lines.append(f"severity={key}|count={count}")
for key, count in decision_counts.most_common():
    summary_lines.append(f"review_decision={key}|count={count}")
for key, count in high_unique_by_proposed.most_common():
    summary_lines.append(f"high_unique_proposed_category={key}|count={count}")
summary_path.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

payload = {
    "artifactDir": artifact_dir,
    "decision": decision,
    "sampledRows": len(rows),
    "highConfidenceRows": len(high_rows),
    "highConfidenceUniqueServices": len(high_unique_rows),
    "severityCounts": dict(severity_counts),
    "reviewDecisionCounts": dict(decision_counts),
    "highProposedCategoryCounts": dict(high_by_proposed),
    "highSourceTypeCounts": dict(high_by_source),
    "highUniqueProposedCategoryCounts": dict(high_unique_by_proposed),
    "highUniqueSourceTypeCounts": dict(high_unique_by_source),
    "ruleDecisionCounts": {
        f"{rule}|{review_decision}": count
        for (rule, review_decision), count in rule_decision_counts.items()
    },
    "topHighConfidenceRows": high_rows[:40],
    "topHighConfidenceUniqueRows": high_unique_rows[:40],
}
json_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Policy Category Suspect Review",
    "",
    f"- decision: `{decision}`",
    f"- sampled_rows: `{len(rows)}`",
    f"- high_confidence_rows: `{len(high_rows)}`",
    f"- high_confidence_unique_services: `{len(high_unique_rows)}`",
    "",
    "This is a read-only classification pass. It does not update categories.",
    "",
    "## Decision Counts",
]
for key, count in decision_counts.most_common():
    note_lines.append(f"- `{key}`: `{count}`")
note_lines.extend(["", "## High Confidence Candidates"])
for row in high_unique_rows[:30]:
    note_lines.append(
        f"- `{row['serviceId']}` `{row['title']}` "
        f"`{row['unifiedCategory']}` -> `{row['proposedCategory']}` "
        f"source=`{row['sourceType']}` views=`{row['apiViewCount']}/{row['viewCount']}` "
        f"decision=`{row['reviewDecision']}`"
    )
note_lines.extend(["", "## Rule Decision Counts"])
for (rule, review_decision), count in sorted(rule_decision_counts.items()):
    note_lines.append(f"- `{rule}` / `{review_decision}`: `{count}`")
note_path.write_text("\n".join(note_lines) + "\n", encoding="utf-8")

print(summary_path.read_text(encoding="utf-8"), end="")
PY

smoke_sanitize_artifacts "${ARTIFACT_DIR}"
smoke_update_links \
  "${ARTIFACT_DIR}" "${ARTIFACT_ROOT}/latest" \
  "${SUMMARY_TXT}" "${ARTIFACT_ROOT}/latest-policy-category-suspect-review-summary.txt" \
  "${SUMMARY_JSON}" "${ARTIFACT_ROOT}/latest-policy-category-suspect-review-summary.json" \
  "${NOTE_MD}" "${ARTIFACT_ROOT}/latest-policy-category-suspect-review-note.md"
