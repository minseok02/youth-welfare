#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/policy-link-review-sample-audit}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

SUMMARY_OUT="${ARTIFACT_DIR}/policy-link-review-sample-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/policy-link-review-sample-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/policy-link-review-sample-note.md"
RAW_BUCKETS_OUT="${ARTIFACT_DIR}/policy-link-review-sample-buckets.tsv"
RAW_SAMPLES_OUT="${ARTIFACT_DIR}/policy-link-review-sample-rows.tsv"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-policy-link-review-sample-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-policy-link-review-sample-summary.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-policy-link-review-sample-note.md"

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
with candidate as (
  select
    ws.id,
    ws.source_id,
    replace(ws.title, E'\t', ' ') as title,
    coalesce(nullif(replace(ws.host_org, E'\t', ' '), ''), '(empty)') as host_org,
    coalesce(nullif(replace(ws.operating_org, E'\t', ' '), ''), '(empty)') as operating_org,
    coalesce(nullif(replace(ws.category_main, E'\t', ' '), ''), '(empty)') as category_main,
    coalesce(nullif(replace(ws.category_sub, E'\t', ' '), ''), '(empty)') as category_sub,
    ws.apply_end_date,
    ws.end_date,
    ws.created_at,
    case
      when ws.title ~ '(모집|공고|선발|접수|신청자|참여자|참가자|추가모집|수강생)' then 'announcement_recruitment'
      when ws.title ~ '(지원금|지원사업|지원 프로그램|수당|장학금|이자 지원|응시료|바우처|급여|보조금|축하금|조리비(용)? 지원|보험( 가입| 지원)?)' then 'benefit_support'
      when ws.title ~ '(프로그램|교육|아카데미|캠프|멘토링|기획단|탐방|실험실|클래스|강좌)' then 'program_event'
      when ws.title ~ '(대회|축제|행사|공연|전시|페스티벌)' then 'event_culture'
      else 'other'
    end as review_bucket
  from welfare_services ws
  left join welfare_service_details wsd on wsd.service_id = ws.id
  where ws.source_type = 'YOUTH'
    and coalesce(ws.detail_url, '') = ''
    and (coalesce(wsd.reference_urls_json::text, '') = '' or coalesce(wsd.reference_urls_json::text, '') = '[]')
    and ws.status in ('ACTIVE', 'UPCOMING')
    and (ws.apply_end_date is null or ws.apply_end_date >= current_date)
)
select review_bucket, count(*)::text
from candidate
group by review_bucket
order by count(*) desc, review_bucket;
" > "${RAW_BUCKETS_OUT}"

smoke_db_query "
with candidate as (
  select
    ws.id,
    ws.source_id,
    replace(ws.title, E'\t', ' ') as title,
    coalesce(nullif(replace(ws.host_org, E'\t', ' '), ''), '(empty)') as host_org,
    coalesce(nullif(replace(ws.operating_org, E'\t', ' '), ''), '(empty)') as operating_org,
    coalesce(nullif(replace(ws.category_main, E'\t', ' '), ''), '(empty)') as category_main,
    coalesce(nullif(replace(ws.category_sub, E'\t', ' '), ''), '(empty)') as category_sub,
    ws.apply_end_date,
    ws.end_date,
    ws.created_at,
    case
      when ws.title ~ '(모집|공고|선발|접수|신청자|참여자|참가자|추가모집|수강생)' then 'announcement_recruitment'
      when ws.title ~ '(지원금|지원사업|지원 프로그램|수당|장학금|이자 지원|응시료|바우처|급여|보조금|축하금|조리비(용)? 지원|보험( 가입| 지원)?)' then 'benefit_support'
      when ws.title ~ '(프로그램|교육|아카데미|캠프|멘토링|기획단|탐방|실험실|클래스|강좌)' then 'program_event'
      when ws.title ~ '(대회|축제|행사|공연|전시|페스티벌)' then 'event_culture'
      else 'other'
    end as review_bucket
  from welfare_services ws
  left join welfare_service_details wsd on wsd.service_id = ws.id
  where ws.source_type = 'YOUTH'
    and coalesce(ws.detail_url, '') = ''
    and (coalesce(wsd.reference_urls_json::text, '') = '' or coalesce(wsd.reference_urls_json::text, '') = '[]')
    and ws.status in ('ACTIVE', 'UPCOMING')
    and (ws.apply_end_date is null or ws.apply_end_date >= current_date)
)
select review_bucket,
       source_id,
       title,
       host_org,
       operating_org,
       category_main,
       category_sub,
       coalesce(apply_end_date::text, ''),
       coalesce(end_date::text, ''),
       created_at::text
from (
  select c.*,
         row_number() over (partition by review_bucket order by created_at desc, id desc) as bucket_rank
  from candidate c
) ranked
where bucket_rank <= 8
order by review_bucket, bucket_rank;
" > "${RAW_SAMPLES_OUT}"

python3 - "${RAW_BUCKETS_OUT}" "${RAW_SAMPLES_OUT}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" <<'PY'
import csv
import json
import sys
from collections import OrderedDict, defaultdict
from pathlib import Path

bucket_path = Path(sys.argv[1])
sample_path = Path(sys.argv[2])
summary_out = Path(sys.argv[3])
json_out = Path(sys.argv[4])
note_out = Path(sys.argv[5])
artifact_dir = sys.argv[6]

bucket_counts = OrderedDict()
with bucket_path.open(encoding="utf-8") as f:
    for bucket, value in csv.reader(f, delimiter="\t"):
        bucket_counts[bucket] = int(value)

samples = defaultdict(list)
with sample_path.open(encoding="utf-8") as f:
    for row in csv.reader(f, delimiter="\t"):
        bucket, source_id, title, host_org, operating_org, category_main, category_sub, apply_end, end_date, created_at = row
        samples[bucket].append({
            "sourceId": source_id,
            "title": title,
            "hostOrg": host_org,
            "operatingOrg": operating_org,
            "categoryMain": category_main,
            "categorySub": category_sub,
            "applyEndDate": apply_end or None,
            "endDate": end_date or None,
            "createdAt": created_at,
        })

active_visible_total = sum(bucket_counts.values())
announcement_count = bucket_counts.get("announcement_recruitment", 0)
benefit_count = bucket_counts.get("benefit_support", 0)
program_count = bucket_counts.get("program_event", 0)
event_count = bucket_counts.get("event_culture", 0)
other_count = bucket_counts.get("other", 0)

if benefit_count >= max(announcement_count, program_count, event_count, other_count):
    decision_class = "BENEFIT_LINK_FIX_PRIORITY"
    operator_reading = "현재 노출되는 링크 공백 YOUTH 후보 중 급부/지원형 정책 비중이 가장 크므로, 단순 source contract 설명보다 실제 CTA 보완 우선순위를 먼저 잡는 편이 맞습니다."
    next_action = "정책 링크 review queue에서 benefit_support bucket부터 확인"
elif announcement_count + program_count + event_count > benefit_count:
    decision_class = "CONTRACT_STYLE_REVIEW_PRIORITY"
    operator_reading = "현재 노출되는 링크 공백 YOUTH 후보 다수는 모집·공고·프로그램형이라, broad 링크 보완보다 source contract성 페이지 부재 review를 먼저 구분하는 편이 맞습니다."
    next_action = "정책 링크 review queue에서 announcement/program/event bucket을 우선 review"
else:
    decision_class = "MIXED_LINK_REVIEW_PRIORITY"
    operator_reading = "링크 공백 YOUTH 후보가 급부형과 공고/프로그램형으로 섞여 있으므로, 운영 queue에서 title bucket 기준으로 두 부류를 나눠 review하는 편이 맞습니다."
    next_action = "정책 링크 review queue를 bucket 기준으로 review"

summary_lines = [
    "policy_link_review_sample_audit=passed",
    f"artifact_dir={artifact_dir}",
    f"active_visible_youth_total={active_visible_total}",
    f"announcement_recruitment_count={announcement_count}",
    f"benefit_support_count={benefit_count}",
    f"program_event_count={program_count}",
    f"event_culture_count={event_count}",
    f"other_count={other_count}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    f"next_action={next_action}",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifactDir": artifact_dir,
    "bucketCounts": bucket_counts,
    "samples": samples,
    "decisionClass": decision_class,
    "operatorReading": operator_reading,
    "nextAction": next_action,
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

bucket_labels = {
    "announcement_recruitment": "공고/모집형",
    "benefit_support": "지원금/급부형",
    "program_event": "프로그램형",
    "event_culture": "행사/문화형",
    "other": "기타",
}

note_lines = [
    "# Policy Link Review Sample Audit",
    "",
    f"- `decision_class`: `{decision_class}`",
    f"- `active_visible_youth_total`: `{active_visible_total}`",
    f"- `announcement_recruitment_count`: `{announcement_count}`",
    f"- `benefit_support_count`: `{benefit_count}`",
    f"- `program_event_count`: `{program_count}`",
    f"- `event_culture_count`: `{event_count}`",
    f"- `other_count`: `{other_count}`",
    "",
    "## Operator Reading",
    "",
    operator_reading,
    "",
    "## Representative Samples",
    "",
]

for bucket, rows in samples.items():
    note_lines.append(f"### {bucket_labels.get(bucket, bucket)}")
    for row in rows[:5]:
        note_lines.append(
            f"- `{row['title']}` / sourceId=`{row['sourceId']}` / host=`{row['hostOrg']}` / 운영=`{row['operatingOrg']}`"
        )
    note_lines.append("")

note_out.write_text("\n".join(note_lines).rstrip() + "\n", encoding="utf-8")
PY

smoke_publish_dir_snapshot "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
smoke_publish_file "${SUMMARY_OUT}" "${LATEST_SUMMARY_LINK}"
smoke_publish_file "${JSON_OUT}" "${LATEST_JSON_LINK}"
smoke_publish_file "${NOTE_OUT}" "${LATEST_NOTE_LINK}"

cat "${SUMMARY_OUT}"
echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
echo "latest_note_link=${LATEST_NOTE_LINK}"
