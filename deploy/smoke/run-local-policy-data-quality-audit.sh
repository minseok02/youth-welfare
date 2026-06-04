#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/policy-data-quality-audit}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

SUMMARY_OUT="${ARTIFACT_DIR}/policy-data-quality-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/policy-data-quality-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/policy-data-quality-note.md"
RAW_METRICS_OUT="${ARTIFACT_DIR}/policy-data-quality-metrics.tsv"
RAW_DUPES_OUT="${ARTIFACT_DIR}/policy-data-quality-duplicates.tsv"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-policy-data-quality-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-policy-data-quality-summary.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-policy-data-quality-note.md"

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
  select ws.id, ws.source_type, ws.source_id, ws.title, ws.host_org, ws.operating_org, ws.status,
         ws.detail_url, ws.apply_start_date, ws.apply_end_date
  from welfare_services ws
)
select 'missing_detail_url' as metric, source_type, count(*)::text as value
from base where coalesce(detail_url,'')='' group by source_type
union all
select 'missing_apply_period_any', source_type, count(*)::text
from base where apply_start_date is null or apply_end_date is null group by source_type
union all
select 'missing_host_org', source_type, count(*)::text
from base where coalesce(host_org,'')='' group by source_type
union all
select 'inactive_status', source_type, count(*)::text
from base where status <> 'ACTIVE' group by source_type
union all
select 'duplicate_title_host_group', source_type, count(*)::text
from (
  select source_type, title, host_org
  from base
  group by source_type, title, host_org
  having count(*) > 1
) d group by source_type
union all
select 'duplicate_title_host_rows', source_type, coalesce(sum(cnt),0)::text
from (
  select source_type, title, host_org, count(*) as cnt
  from base
  group by source_type, title, host_org
  having count(*) > 1
) d group by source_type
order by metric, source_type;
" > "${RAW_METRICS_OUT}"

smoke_db_query "
with dupes as (
  select source_type, title, host_org, count(*) as cnt,
         string_agg(source_id, ', ' order by source_id) as source_ids
  from welfare_services
  group by source_type, title, host_org
  having count(*) > 1
)
select source_type, replace(title, E'\t', ' '), replace(coalesce(host_org,''), E'\t', ' '), cnt, source_ids
from dupes
order by cnt desc, source_type, title
limit 30;
" > "${RAW_DUPES_OUT}"

python3 - "${RAW_METRICS_OUT}" "${RAW_DUPES_OUT}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" <<'PY'
import csv
import json
import sys
from collections import defaultdict
from pathlib import Path

metrics_path = Path(sys.argv[1])
dupes_path = Path(sys.argv[2])
summary_out = Path(sys.argv[3])
json_out = Path(sys.argv[4])
note_out = Path(sys.argv[5])
artifact_dir = sys.argv[6]

metrics = defaultdict(dict)
with metrics_path.open(encoding="utf-8") as f:
    for metric, source_type, value in csv.reader(f, delimiter="\t"):
        metrics[metric][source_type] = int(value)

duplicate_samples = []
with dupes_path.open(encoding="utf-8") as f:
    for source_type, title, host_org, cnt, source_ids in csv.reader(f, delimiter="\t"):
        duplicate_samples.append({
            "sourceType": source_type,
            "title": title,
            "hostOrg": host_org,
            "count": int(cnt),
            "sourceIds": source_ids,
        })

missing_detail_url = metrics.get("missing_detail_url", {})
missing_apply_period = metrics.get("missing_apply_period_any", {})
missing_host_org = metrics.get("missing_host_org", {})
inactive_status = metrics.get("inactive_status", {})
duplicate_groups = metrics.get("duplicate_title_host_group", {})
duplicate_rows = metrics.get("duplicate_title_host_rows", {})

priority_duplicate_sources = {
    source: duplicate_groups.get(source, 0)
    for source in ("YOUTH", "BOKJIRO_LOCAL", "GOV24", "BOKJIRO_CENTRAL")
}

if priority_duplicate_sources.get("YOUTH", 0) > 0 or priority_duplicate_sources.get("BOKJIRO_LOCAL", 0) > 0:
    decision_class = "DUPLICATE_REVIEW_PRIORITY"
    operator_reading = (
        "Current field completeness gaps are mostly structural by source, but duplicate title/host groups remain "
        "the most actionable data-quality queue. Review YOUTH and BOKJIRO_LOCAL duplicate groups before broad parser changes."
    )
    next_action = "docs/policy/policy-data-quality-audit-runbook.md"
else:
    decision_class = "STRUCTURAL_GAPS_ONLY"
    operator_reading = (
        "Current quality gaps look structural by source contract rather than indicating a fresh broad data regression."
    )
    next_action = "bash deploy/smoke/run-local-policy-search-scenario-audit.sh"

summary_lines = [
    "policy_data_quality_audit=passed",
    f"artifact_dir={artifact_dir}",
    f"missing_detail_url_youth={missing_detail_url.get('YOUTH', 0)}",
    f"missing_apply_period_gov24={missing_apply_period.get('GOV24', 0)}",
    f"missing_apply_period_bokjiro_local={missing_apply_period.get('BOKJIRO_LOCAL', 0)}",
    f"missing_apply_period_bokjiro_central={missing_apply_period.get('BOKJIRO_CENTRAL', 0)}",
    f"missing_apply_period_youth={missing_apply_period.get('YOUTH', 0)}",
    f"missing_host_org_bokjiro_local={missing_host_org.get('BOKJIRO_LOCAL', 0)}",
    f"inactive_status_gov24={inactive_status.get('GOV24', 0)}",
    f"inactive_status_youth={inactive_status.get('YOUTH', 0)}",
    f"duplicate_groups_youth={duplicate_groups.get('YOUTH', 0)}",
    f"duplicate_rows_youth={duplicate_rows.get('YOUTH', 0)}",
    f"duplicate_groups_bokjiro_local={duplicate_groups.get('BOKJIRO_LOCAL', 0)}",
    f"duplicate_rows_bokjiro_local={duplicate_rows.get('BOKJIRO_LOCAL', 0)}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    f"next_action={next_action}",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": artifact_dir,
    "missingDetailUrl": missing_detail_url,
    "missingApplyPeriodAny": missing_apply_period,
    "missingHostOrg": missing_host_org,
    "inactiveStatus": inactive_status,
    "duplicateTitleHostGroups": duplicate_groups,
    "duplicateTitleHostRows": duplicate_rows,
    "duplicateSamples": duplicate_samples,
    "decisionClass": decision_class,
    "operatorReading": operator_reading,
    "nextAction": next_action,
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Policy Data Quality Audit",
    "",
    f"- `decision_class`: `{decision_class}`",
    f"- `missing_detail_url_youth`: `{missing_detail_url.get('YOUTH', 0)}`",
    f"- `missing_apply_period_gov24`: `{missing_apply_period.get('GOV24', 0)}`",
    f"- `missing_apply_period_bokjiro_local`: `{missing_apply_period.get('BOKJIRO_LOCAL', 0)}`",
    f"- `missing_apply_period_bokjiro_central`: `{missing_apply_period.get('BOKJIRO_CENTRAL', 0)}`",
    f"- `missing_apply_period_youth`: `{missing_apply_period.get('YOUTH', 0)}`",
    f"- `missing_host_org_bokjiro_local`: `{missing_host_org.get('BOKJIRO_LOCAL', 0)}`",
    f"- `inactive_status_gov24`: `{inactive_status.get('GOV24', 0)}`",
    f"- `inactive_status_youth`: `{inactive_status.get('YOUTH', 0)}`",
    f"- `duplicate_groups_youth`: `{duplicate_groups.get('YOUTH', 0)}`",
    f"- `duplicate_groups_bokjiro_local`: `{duplicate_groups.get('BOKJIRO_LOCAL', 0)}`",
    "",
    "## Operator Reading",
    "",
    operator_reading,
    "",
    "## Top Duplicate Samples",
    "",
]
for sample in duplicate_samples[:10]:
    note_lines.append(
        f"- `{sample['sourceType']}` `{sample['title']}` / `{sample['hostOrg'] or '(empty)'}` / "
        f"`count={sample['count']}`"
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
