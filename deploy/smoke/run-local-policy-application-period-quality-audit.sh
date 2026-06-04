#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/policy-application-period-quality-audit}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

SUMMARY_OUT="${ARTIFACT_DIR}/policy-application-period-quality-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/policy-application-period-quality-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/policy-application-period-quality-note.md"
RAW_METRICS_OUT="${ARTIFACT_DIR}/policy-application-period-quality-metrics.tsv"
RAW_SAMPLES_OUT="${ARTIFACT_DIR}/policy-application-period-quality-samples.tsv"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-policy-application-period-quality-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-policy-application-period-quality-summary.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-policy-application-period-quality-note.md"

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
with base as (
  select source_type, source_id, title, status, apply_start_date, apply_end_date, end_date
  from welfare_services
)
select 'missing_any' as metric, source_type, count(*)::text
from base
where apply_start_date is null or apply_end_date is null
group by source_type
union all
select 'invalid_range', source_type, count(*)::text
from base
where apply_start_date is not null
  and apply_end_date is not null
  and apply_start_date > apply_end_date
group by source_type
union all
select 'active_past_end', source_type, count(*)::text
from base
where status in ('ACTIVE', 'UPCOMING')
  and apply_end_date is not null
  and apply_end_date < current_date
group by source_type
union all
select 'active_past_end_future_end_tail', source_type, count(*)::text
from base
where status in ('ACTIVE', 'UPCOMING')
  and apply_end_date is not null
  and apply_end_date < current_date
  and end_date is not null
  and end_date >= current_date
group by source_type
union all
select 'active_past_end_true_review', source_type, count(*)::text
from base
where status in ('ACTIVE', 'UPCOMING')
  and apply_end_date is not null
  and apply_end_date < current_date
  and (end_date is null or end_date < current_date)
group by source_type
union all
select 'closed_future_end', source_type, count(*)::text
from base
where status = 'CLOSED'
  and apply_end_date is not null
  and apply_end_date >= current_date
group by source_type
order by 1, 2;
" > "${RAW_METRICS_OUT}"

smoke_db_query "
with issues as (
  select source_type,
         source_id,
         replace(title, E'\t', ' ') as title,
         status,
         coalesce(apply_start_date::text, '') as apply_start_date,
         coalesce(apply_end_date::text, '') as apply_end_date,
         coalesce(end_date::text, '') as end_date,
         case
           when apply_start_date is not null
             and apply_end_date is not null
             and apply_start_date > apply_end_date then 'invalid_range'
           when status in ('ACTIVE', 'UPCOMING')
             and apply_end_date is not null
             and apply_end_date < current_date then 'active_past_end'
           when status = 'CLOSED'
             and apply_end_date is not null
             and apply_end_date >= current_date then 'closed_future_end'
           when apply_start_date is null or apply_end_date is null then 'missing_any'
         end as issue
  from welfare_services
)
select issue, source_type, source_id, title, status, apply_start_date, apply_end_date, end_date
from issues
where issue is not null
order by issue, source_type, source_id
limit 40;
" > "${RAW_SAMPLES_OUT}"

python3 - "${RAW_METRICS_OUT}" "${RAW_SAMPLES_OUT}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" <<'PY'
import csv
import json
import sys
from collections import defaultdict
from pathlib import Path

metrics_path = Path(sys.argv[1])
samples_path = Path(sys.argv[2])
summary_out = Path(sys.argv[3])
json_out = Path(sys.argv[4])
note_out = Path(sys.argv[5])
artifact_dir = sys.argv[6]

metrics = defaultdict(dict)
with metrics_path.open(encoding="utf-8") as f:
    for metric, source_type, value in csv.reader(f, delimiter="\t"):
        metrics[metric][source_type] = int(value)

samples = []
with samples_path.open(encoding="utf-8") as f:
    for issue, source_type, source_id, title, status, apply_start_date, apply_end_date, end_date in csv.reader(f, delimiter="\t"):
        samples.append({
            "issue": issue,
            "sourceType": source_type,
            "sourceId": source_id,
            "title": title,
            "status": status,
            "applyStartDate": apply_start_date,
            "applyEndDate": apply_end_date,
            "endDate": end_date,
        })

missing_any = metrics.get("missing_any", {})
invalid_range = metrics.get("invalid_range", {})
active_past_end = metrics.get("active_past_end", {})
active_past_end_future_end_tail = metrics.get("active_past_end_future_end_tail", {})
active_past_end_true_review = metrics.get("active_past_end_true_review", {})
closed_future_end = metrics.get("closed_future_end", {})

decision_class = "SOURCE_CONTRACT_DOMINANT"
operator_reading = (
    "신청기간 누락은 source contract 영향이 크고, broad parser failure로 읽을 상황은 아닙니다."
)
next_action = "docs/policy/policy-application-period-quality-audit-runbook.md"

if sum(active_past_end_true_review.values()) > 0 or sum(closed_future_end.values()) > 0 or sum(invalid_range.values()) > 0:
    decision_class = "STATUS_DATE_REVIEW_PRIORITY"
    if sum(active_past_end_true_review.values()) > 0:
        operator_reading = (
            "신청기간 누락보다 status/date 불일치가 더 actionable 합니다. "
            "특히 ACTIVE/UPCOMING 상태인데 apply_end_date 가 지난 row를 먼저 review 하는 편이 낫습니다."
        )
    elif sum(closed_future_end.values()) > 0:
        operator_reading = (
            "ACTIVE/UPCOMING stale row는 남은 true review 대상이 없고, 현재 actionable 잔량은 "
            "`CLOSED + 미래 apply_end_date` 쪽입니다."
        )
    else:
        operator_reading = (
            "신청기간 누락보다 status/date 불일치가 더 actionable 합니다."
        )
elif sum(active_past_end_future_end_tail.values()) > 0:
    decision_class = "EXPECTED_WINDOW_CLOSED_TAIL"
    operator_reading = (
        "남은 ACTIVE/UPCOMING + 과거 apply_end_date row는 대부분 end_date 가 아직 미래인 source tail 입니다. "
        "ACTIVE_ONLY 검색/목록에서는 이미 숨겨지므로 broad 운영 장애로 보기보다 bounded source 해석 이슈로 읽는 편이 맞습니다."
    )

summary_lines = [
    "policy_application_period_quality_audit=passed",
    f"artifact_dir={artifact_dir}",
    f"missing_any_youth={missing_any.get('YOUTH', 0)}",
    f"missing_any_bokjiro_local={missing_any.get('BOKJIRO_LOCAL', 0)}",
    f"missing_any_bokjiro_central={missing_any.get('BOKJIRO_CENTRAL', 0)}",
    f"missing_any_gov24={missing_any.get('GOV24', 0)}",
    f"invalid_range_total={sum(invalid_range.values())}",
    f"active_past_end_youth={active_past_end.get('YOUTH', 0)}",
    f"active_past_end_gov24={active_past_end.get('GOV24', 0)}",
    f"active_past_end_youth_future_end_tail={active_past_end_future_end_tail.get('YOUTH', 0)}",
    f"active_past_end_youth_true_review={active_past_end_true_review.get('YOUTH', 0)}",
    f"active_past_end_gov24_true_review={active_past_end_true_review.get('GOV24', 0)}",
    f"closed_future_end_total={sum(closed_future_end.values())}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    f"next_action={next_action}",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": artifact_dir,
    "metrics": metrics,
    "samples": samples,
    "decisionClass": decision_class,
    "operatorReading": operator_reading,
    "nextAction": next_action,
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Policy Application Period Quality Audit",
    "",
    f"- `decision_class`: `{decision_class}`",
    f"- `missing_any_youth`: `{missing_any.get('YOUTH', 0)}`",
    f"- `missing_any_bokjiro_local`: `{missing_any.get('BOKJIRO_LOCAL', 0)}`",
    f"- `missing_any_bokjiro_central`: `{missing_any.get('BOKJIRO_CENTRAL', 0)}`",
    f"- `missing_any_gov24`: `{missing_any.get('GOV24', 0)}`",
    f"- `invalid_range_total`: `{sum(invalid_range.values())}`",
    f"- `active_past_end_youth`: `{active_past_end.get('YOUTH', 0)}`",
    f"- `active_past_end_gov24`: `{active_past_end.get('GOV24', 0)}`",
    f"- `active_past_end_youth_future_end_tail`: `{active_past_end_future_end_tail.get('YOUTH', 0)}`",
    f"- `active_past_end_youth_true_review`: `{active_past_end_true_review.get('YOUTH', 0)}`",
    f"- `active_past_end_gov24_true_review`: `{active_past_end_true_review.get('GOV24', 0)}`",
    f"- `closed_future_end_total`: `{sum(closed_future_end.values())}`",
    "",
    "## Operator Reading",
    "",
    operator_reading,
    "",
    "## Samples",
    "",
]
for sample in samples[:12]:
    note_lines.append(
        f"- `{sample['issue']}` / `{sample['sourceType']}` `{sample['title']}` "
        f"(status=`{sample['status']}`, `{sample['applyStartDate']}` ~ `{sample['applyEndDate']}`, endDate=`{sample['endDate']}`)"
    )
note_out.write_text("\n".join(note_lines) + "\n", encoding="utf-8")
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
