#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/policy-host-org-quality-audit}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

SUMMARY_OUT="${ARTIFACT_DIR}/policy-host-org-quality-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/policy-host-org-quality-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/policy-host-org-quality-note.md"
RAW_METRICS_OUT="${ARTIFACT_DIR}/policy-host-org-quality-metrics.tsv"
RAW_SAMPLES_OUT="${ARTIFACT_DIR}/policy-host-org-quality-samples.tsv"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-policy-host-org-quality-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-policy-host-org-quality-summary.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-policy-host-org-quality-note.md"

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
  select source_type,
         source_id,
         title,
         coalesce(host_org, '') as host_org,
         coalesce(operating_org, '') as operating_org
  from welfare_services
)
select 'missing_host' as metric, source_type, count(*)::text
from base where host_org = '' group by source_type
union all
select 'missing_operating', source_type, count(*)::text
from base where operating_org = '' group by source_type
union all
select 'same_host_operating', source_type, count(*)::text
from base where host_org <> '' and host_org = operating_org group by source_type
union all
select 'placeholder_host', source_type, count(*)::text
from base where host_org in ('-', '없음', '미정', 'N/A', 'null') group by source_type
order by 1, 2;
" > "${RAW_METRICS_OUT}"

smoke_db_query "
select source_type,
       source_id,
       replace(title, E'\t', ' ') as title,
       replace(coalesce(host_org, ''), E'\t', ' ') as host_org,
       replace(coalesce(operating_org, ''), E'\t', ' ') as operating_org
from welfare_services
where coalesce(host_org, '') = ''
   or coalesce(operating_org, '') = ''
order by source_type, source_id
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
    for source_type, source_id, title, host_org, operating_org in csv.reader(f, delimiter="\t"):
        samples.append({
            "sourceType": source_type,
            "sourceId": source_id,
            "title": title,
            "hostOrg": host_org,
            "operatingOrg": operating_org,
        })

missing_host = metrics.get("missing_host", {})
missing_operating = metrics.get("missing_operating", {})
same_host_operating = metrics.get("same_host_operating", {})
placeholder_host = metrics.get("placeholder_host", {})

decision_class = "SOURCE_CONTRACT_DOMINANT"
operator_reading = (
    "기관명 품질은 broad parser failure보다 source contract 해석 이슈가 큽니다. "
    "특히 BOKJIRO_LOCAL 은 host_org 가 비고 operating_org 만 내려오는 구조로 읽는 편이 맞습니다."
)
next_action = "docs/policy/policy-host-org-quality-audit-runbook.md"

if sum(placeholder_host.values()) > 0:
    decision_class = "PLACEHOLDER_HOST_REVIEW_PRIORITY"
    operator_reading = (
        "placeholder host_org 값이 실제 노출 품질을 해칠 수 있어, broad source contract 해석보다 placeholder row review가 먼저입니다."
    )

summary_lines = [
    "policy_host_org_quality_audit=passed",
    f"artifact_dir={artifact_dir}",
    f"missing_host_bokjiro_local={missing_host.get('BOKJIRO_LOCAL', 0)}",
    f"missing_host_youth={missing_host.get('YOUTH', 0)}",
    f"missing_operating_youth={missing_operating.get('YOUTH', 0)}",
    f"missing_operating_bokjiro_local={missing_operating.get('BOKJIRO_LOCAL', 0)}",
    f"same_host_operating_youth={same_host_operating.get('YOUTH', 0)}",
    f"same_host_operating_gov24={same_host_operating.get('GOV24', 0)}",
    f"placeholder_host_total={sum(placeholder_host.values())}",
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
    "# Policy Host/Operating Org Quality Audit",
    "",
    f"- `decision_class`: `{decision_class}`",
    f"- `missing_host_bokjiro_local`: `{missing_host.get('BOKJIRO_LOCAL', 0)}`",
    f"- `missing_host_youth`: `{missing_host.get('YOUTH', 0)}`",
    f"- `missing_operating_youth`: `{missing_operating.get('YOUTH', 0)}`",
    f"- `missing_operating_bokjiro_local`: `{missing_operating.get('BOKJIRO_LOCAL', 0)}`",
    f"- `same_host_operating_youth`: `{same_host_operating.get('YOUTH', 0)}`",
    f"- `same_host_operating_gov24`: `{same_host_operating.get('GOV24', 0)}`",
    f"- `placeholder_host_total`: `{sum(placeholder_host.values())}`",
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
        f"- `{sample['sourceType']}` `{sample['title']}` / host=`{sample['hostOrg'] or '(empty)'}` / "
        f"operating=`{sample['operatingOrg'] or '(empty)'}`"
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
