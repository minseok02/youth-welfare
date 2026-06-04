#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/youth-duplicate-candidate-audit}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

SUMMARY_OUT="${ARTIFACT_DIR}/youth-duplicate-candidate-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/youth-duplicate-candidate-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/youth-duplicate-candidate-note.md"
RAW_METRICS_OUT="${ARTIFACT_DIR}/youth-duplicate-candidate-metrics.tsv"
RAW_SAMPLES_OUT="${ARTIFACT_DIR}/youth-duplicate-candidate-samples.tsv"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-youth-duplicate-candidate-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-youth-duplicate-candidate-summary.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-youth-duplicate-candidate-note.md"

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
with grouped as (
  select title,
         coalesce(host_org, '') as host_org,
         coalesce(operating_org, '') as operating_org,
         count(*) as row_count,
         count(distinct coalesce(detail_url, '')) as distinct_detail_url_count,
         count(distinct coalesce(apply_start_date::text, '')) as distinct_apply_start_count,
         count(distinct coalesce(apply_end_date::text, '')) as distinct_apply_end_count,
         min(apply_start_date) as min_apply_start_date,
         max(apply_start_date) as max_apply_start_date,
         min(apply_end_date) as min_apply_end_date,
         max(apply_end_date) as max_apply_end_date
  from welfare_services
  where source_type = 'YOUTH'
  group by title, coalesce(host_org, ''), coalesce(operating_org, '')
  having count(*) > 1
),
classified as (
  select *,
         case
           when distinct_apply_start_count <= 1 and distinct_apply_end_count <= 1 and distinct_detail_url_count <= 1
             then 'exact_duplicate_candidate'
           when distinct_apply_start_count <= 1 and distinct_apply_end_count <= 1 and distinct_detail_url_count > 1
             then 'mirror_or_channel_variant_candidate'
           else 'date_or_contract_drift_candidate'
         end as candidate_class
  from grouped
)
select 'duplicate_groups_total', count(*)::text from classified
union all
select 'duplicate_rows_total', coalesce(sum(row_count), 0)::text from classified
union all
select 'exact_duplicate_groups', count(*)::text from classified where candidate_class = 'exact_duplicate_candidate'
union all
select 'exact_duplicate_rows', coalesce(sum(row_count), 0)::text from classified where candidate_class = 'exact_duplicate_candidate'
union all
select 'mirror_variant_groups', count(*)::text from classified where candidate_class = 'mirror_or_channel_variant_candidate'
union all
select 'mirror_variant_rows', coalesce(sum(row_count), 0)::text from classified where candidate_class = 'mirror_or_channel_variant_candidate'
union all
select 'date_or_contract_drift_groups', count(*)::text from classified where candidate_class = 'date_or_contract_drift_candidate'
union all
select 'date_or_contract_drift_rows', coalesce(sum(row_count), 0)::text from classified where candidate_class = 'date_or_contract_drift_candidate'
union all
select 'groups_with_blank_host_org', count(*)::text from classified where host_org = ''
union all
select 'groups_with_blank_operating_org', count(*)::text from classified where operating_org = '';
" > "${RAW_METRICS_OUT}"

smoke_db_query "
with grouped as (
  select title,
         coalesce(host_org, '') as host_org,
         coalesce(operating_org, '') as operating_org,
         count(*) as row_count,
         count(distinct coalesce(detail_url, '')) as distinct_detail_url_count,
         count(distinct coalesce(apply_start_date::text, '')) as distinct_apply_start_count,
         count(distinct coalesce(apply_end_date::text, '')) as distinct_apply_end_count,
         min(apply_start_date) as min_apply_start_date,
         max(apply_start_date) as max_apply_start_date,
         min(apply_end_date) as min_apply_end_date,
         max(apply_end_date) as max_apply_end_date,
         string_agg(source_id, ', ' order by source_id) as source_ids,
         string_agg(coalesce(nullif(detail_url, ''), '(empty)'), ' || ' order by source_id) as detail_urls
  from welfare_services
  where source_type = 'YOUTH'
  group by title, coalesce(host_org, ''), coalesce(operating_org, '')
  having count(*) > 1
),
classified as (
  select *,
         case
           when distinct_apply_start_count <= 1 and distinct_apply_end_count <= 1 and distinct_detail_url_count <= 1
             then 'exact_duplicate_candidate'
           when distinct_apply_start_count <= 1 and distinct_apply_end_count <= 1 and distinct_detail_url_count > 1
             then 'mirror_or_channel_variant_candidate'
           else 'date_or_contract_drift_candidate'
         end as candidate_class
  from grouped
)
select candidate_class,
       replace(title, E'\t', ' '),
       replace(host_org, E'\t', ' '),
       replace(operating_org, E'\t', ' '),
       row_count,
       coalesce(min_apply_start_date::text, ''),
       coalesce(max_apply_start_date::text, ''),
       coalesce(min_apply_end_date::text, ''),
       coalesce(max_apply_end_date::text, ''),
       distinct_detail_url_count,
       replace(source_ids, E'\t', ' '),
       replace(detail_urls, E'\t', ' ')
from classified
order by
  case candidate_class
    when 'exact_duplicate_candidate' then 1
    when 'mirror_or_channel_variant_candidate' then 2
    else 3
  end,
  row_count desc,
  title
limit 30;
" > "${RAW_SAMPLES_OUT}"

python3 - "${RAW_METRICS_OUT}" "${RAW_SAMPLES_OUT}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" <<'PY'
import csv
import json
import sys
from pathlib import Path

metrics_path = Path(sys.argv[1])
samples_path = Path(sys.argv[2])
summary_out = Path(sys.argv[3])
json_out = Path(sys.argv[4])
note_out = Path(sys.argv[5])
artifact_dir = sys.argv[6]

metrics = {}
with metrics_path.open(encoding="utf-8") as f:
    for row in csv.reader(f, delimiter="\t"):
        if not row:
            continue
        metrics[row[0]] = int(row[1])

samples = []
with samples_path.open(encoding="utf-8") as f:
    for row in csv.reader(f, delimiter="\t"):
        if not row:
            continue
        samples.append({
            "candidateClass": row[0],
            "title": row[1],
            "hostOrg": row[2],
            "operatingOrg": row[3],
            "rowCount": int(row[4]),
            "minApplyStartDate": row[5] or None,
            "maxApplyStartDate": row[6] or None,
            "minApplyEndDate": row[7] or None,
            "maxApplyEndDate": row[8] or None,
            "distinctDetailUrlCount": int(row[9]),
            "sourceIds": row[10],
            "detailUrls": row[11],
        })

exact_groups = metrics.get("exact_duplicate_groups", 0)
mirror_groups = metrics.get("mirror_variant_groups", 0)
drift_groups = metrics.get("date_or_contract_drift_groups", 0)

if exact_groups > 0 or mirror_groups > 0:
    decision_class = "YOUTH_TRUE_DUPLICATE_REVIEW_PRIORITY"
    operator_reading = (
        "YOUTH duplicate groups contain real same-org/same-period candidates. "
        "Prefer exact-URL duplicates first, then mirror/channel variants, before touching broader queue rules."
    )
else:
    decision_class = "YOUTH_DUPLICATE_TAIL_ONLY"
    operator_reading = (
        "Current YOUTH duplicate groups are mostly date/contract drift tails rather than obvious same-row duplicates."
    )

summary_lines = [
    "youth_duplicate_candidate_audit=passed",
    f"artifact_dir={artifact_dir}",
    f"duplicate_groups_total={metrics.get('duplicate_groups_total', 0)}",
    f"duplicate_rows_total={metrics.get('duplicate_rows_total', 0)}",
    f"exact_duplicate_groups={exact_groups}",
    f"exact_duplicate_rows={metrics.get('exact_duplicate_rows', 0)}",
    f"mirror_variant_groups={mirror_groups}",
    f"mirror_variant_rows={metrics.get('mirror_variant_rows', 0)}",
    f"date_or_contract_drift_groups={drift_groups}",
    f"date_or_contract_drift_rows={metrics.get('date_or_contract_drift_rows', 0)}",
    f"groups_with_blank_host_org={metrics.get('groups_with_blank_host_org', 0)}",
    f"groups_with_blank_operating_org={metrics.get('groups_with_blank_operating_org', 0)}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    "next_action=docs/policy/policy-duplicate-review-runbook.md",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": artifact_dir,
    "metrics": metrics,
    "samples": samples,
    "decisionClass": decision_class,
    "operatorReading": operator_reading,
    "nextAction": "docs/policy/policy-duplicate-review-runbook.md",
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Youth Duplicate Candidate Audit",
    "",
    f"- `decision_class`: `{decision_class}`",
    f"- `duplicate_groups_total`: `{metrics.get('duplicate_groups_total', 0)}`",
    f"- `exact_duplicate_groups`: `{exact_groups}`",
    f"- `mirror_variant_groups`: `{mirror_groups}`",
    f"- `date_or_contract_drift_groups`: `{drift_groups}`",
    "",
    "## Operator Reading",
    "",
    operator_reading,
    "",
    "## Top Candidate Samples",
    "",
]
for sample in samples[:10]:
    note_lines.append(
        f"- `{sample['candidateClass']}` / `{sample['title']}` / "
        f"`{sample['hostOrg'] or '(empty)'}` / `{sample['operatingOrg'] or '(empty)'}` / "
        f"`rows={sample['rowCount']}` / `detailUrls={sample['distinctDetailUrlCount']}`"
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
