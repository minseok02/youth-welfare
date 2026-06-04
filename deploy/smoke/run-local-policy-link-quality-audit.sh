#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/policy-link-quality-audit}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

SUMMARY_OUT="${ARTIFACT_DIR}/policy-link-quality-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/policy-link-quality-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/policy-link-quality-note.md"
RAW_METRICS_OUT="${ARTIFACT_DIR}/policy-link-quality-metrics.tsv"
RAW_SAMPLES_OUT="${ARTIFACT_DIR}/policy-link-quality-samples.tsv"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-policy-link-quality-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-policy-link-quality-summary.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-policy-link-quality-note.md"

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
  select
    ws.id,
    ws.source_type,
    ws.source_id,
    ws.title,
    coalesce(ws.detail_url, '') as detail_url,
    case when coalesce(wsd.reference_urls_json, '') <> '' then 1 else 0 end as has_reference_urls
  from welfare_services ws
  left join welfare_service_details wsd on wsd.service_id = ws.id
)
select 'total', source_type, count(*)::text
from base group by source_type
union all
select 'missing_detail_url', source_type, count(*)::text
from base where detail_url = '' group by source_type
union all
select 'has_detail_url', source_type, count(*)::text
from base where detail_url <> '' group by source_type
union all
select 'has_reference_urls', source_type, count(*)::text
from base where has_reference_urls = 1 group by source_type
union all
select 'missing_any_link', source_type, count(*)::text
from base where detail_url = '' and has_reference_urls = 0 group by source_type
union all
select 'missing_any_link_active_visible', source_type, count(*)::text
from (
  select
    ws.source_type,
    coalesce(ws.detail_url, '') as detail_url,
    case when coalesce(wsd.reference_urls_json::text, '') <> '' and coalesce(wsd.reference_urls_json::text, '') <> '[]' then 1 else 0 end as has_reference_urls,
    ws.status,
    ws.apply_end_date
  from welfare_services ws
  left join welfare_service_details wsd on wsd.service_id = ws.id
) visible_base
where detail_url = ''
  and has_reference_urls = 0
  and status in ('ACTIVE', 'UPCOMING')
  and (apply_end_date is null or apply_end_date >= current_date)
group by source_type
union all
select 'missing_any_link_active_past_end_tail', source_type, count(*)::text
from (
  select
    ws.source_type,
    coalesce(ws.detail_url, '') as detail_url,
    case when coalesce(wsd.reference_urls_json::text, '') <> '' and coalesce(wsd.reference_urls_json::text, '') <> '[]' then 1 else 0 end as has_reference_urls,
    ws.status,
    ws.apply_end_date
  from welfare_services ws
  left join welfare_service_details wsd on wsd.service_id = ws.id
) tail_base
where detail_url = ''
  and has_reference_urls = 0
  and status in ('ACTIVE', 'UPCOMING')
  and apply_end_date is not null
  and apply_end_date < current_date
group by source_type
order by 1, 2;
" > "${RAW_METRICS_OUT}"

smoke_db_query "
with base as (
  select
    ws.source_type,
    ws.source_id,
    replace(ws.title, E'\t', ' ') as title,
    coalesce(nullif(ws.detail_url, ''), '(empty)') as detail_url,
    case when coalesce(wsd.reference_urls_json, '') <> '' then 'Y' else 'N' end as has_reference_urls
  from welfare_services ws
  left join welfare_service_details wsd on wsd.service_id = ws.id
  where coalesce(ws.detail_url, '') = ''
    and coalesce(wsd.reference_urls_json, '') = ''
)
select source_type, source_id, title, detail_url, has_reference_urls
from base
order by source_type, source_id
limit 30;
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
    for source_type, source_id, title, detail_url, has_reference_urls in csv.reader(f, delimiter="\t"):
        samples.append({
            "sourceType": source_type,
            "sourceId": source_id,
            "title": title,
            "detailUrl": detail_url,
            "hasReferenceUrls": has_reference_urls == "Y",
        })

missing_any_link = metrics.get("missing_any_link", {})
missing_any_link_active_visible = metrics.get("missing_any_link_active_visible", {})
missing_any_link_active_past_end_tail = metrics.get("missing_any_link_active_past_end_tail", {})
decision_class = "BASELINE_HEALTHY"
operator_reading = (
    "대표 detail_url 또는 referenceUrlsJson 기준으로 바로 볼 broad link quality regression은 두드러지지 않습니다."
)
next_action = "bash deploy/smoke/run-local-policy-search-scenario-audit.sh"

if missing_any_link_active_visible.get("YOUTH", 0) > 0 or missing_any_link_active_visible.get("BOKJIRO_LOCAL", 0) > 0:
    decision_class = "ACTIVE_LINK_REVIEW_PRIORITY"
    operator_reading = (
        "대표 링크가 비는 row 전체보다, 현재 사용자에게 노출될 수 있는 ACTIVE/UPCOMING 정책의 "
        "missing-any-link row를 먼저 review 하는 편이 낫습니다."
    )
    next_action = "docs/policy/policy-link-quality-audit-runbook.md"
elif missing_any_link.get("YOUTH", 0) > 0 or missing_any_link.get("BOKJIRO_LOCAL", 0) > 0:
    decision_class = "LINK_REVIEW_PRIORITY"
    operator_reading = (
        "source contract 때문에 대표 링크가 비는 row는 남아 있지만, 실제 정책 상세 CTA 품질 관점에서는 "
        "YOUTH / BOKJIRO_LOCAL 의 missing-any-link sample을 먼저 review 하는 편이 낫습니다."
    )
    next_action = "docs/policy/policy-link-quality-audit-runbook.md"

summary_lines = [
    "policy_link_quality_audit=passed",
    f"artifact_dir={artifact_dir}",
    f"missing_any_link_youth={missing_any_link.get('YOUTH', 0)}",
    f"missing_any_link_bokjiro_local={missing_any_link.get('BOKJIRO_LOCAL', 0)}",
    f"missing_any_link_bokjiro_central={missing_any_link.get('BOKJIRO_CENTRAL', 0)}",
    f"missing_any_link_gov24={missing_any_link.get('GOV24', 0)}",
    f"missing_any_link_active_visible_youth={missing_any_link_active_visible.get('YOUTH', 0)}",
    f"missing_any_link_active_visible_bokjiro_local={missing_any_link_active_visible.get('BOKJIRO_LOCAL', 0)}",
    f"missing_any_link_active_past_end_tail_youth={missing_any_link_active_past_end_tail.get('YOUTH', 0)}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    f"next_action={next_action}",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": artifact_dir,
    "metrics": metrics,
    "missingAnyLinkSamples": samples,
    "decisionClass": decision_class,
    "operatorReading": operator_reading,
    "nextAction": next_action,
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Policy Link Quality Audit",
    "",
    f"- `decision_class`: `{decision_class}`",
    f"- `missing_any_link_youth`: `{missing_any_link.get('YOUTH', 0)}`",
    f"- `missing_any_link_bokjiro_local`: `{missing_any_link.get('BOKJIRO_LOCAL', 0)}`",
    f"- `missing_any_link_bokjiro_central`: `{missing_any_link.get('BOKJIRO_CENTRAL', 0)}`",
    f"- `missing_any_link_gov24`: `{missing_any_link.get('GOV24', 0)}`",
    f"- `missing_any_link_active_visible_youth`: `{missing_any_link_active_visible.get('YOUTH', 0)}`",
    f"- `missing_any_link_active_visible_bokjiro_local`: `{missing_any_link_active_visible.get('BOKJIRO_LOCAL', 0)}`",
    f"- `missing_any_link_active_past_end_tail_youth`: `{missing_any_link_active_past_end_tail.get('YOUTH', 0)}`",
    "",
    "## Operator Reading",
    "",
    operator_reading,
    "",
    "## Missing Link Samples",
    "",
]
for sample in samples[:10]:
    note_lines.append(
        f"- `{sample['sourceType']}` `{sample['title']}` / sourceId=`{sample['sourceId']}` / referenceUrls=`{sample['hasReferenceUrls']}`"
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
