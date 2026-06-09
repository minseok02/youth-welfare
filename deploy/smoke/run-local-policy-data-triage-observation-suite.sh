#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/policy-data-triage-observation}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

DATA_QUALITY_OUTPUT="${ARTIFACT_DIR}/policy-data-quality.out"
DATA_QUALITY_ARTIFACT_DIR="${ARTIFACT_DIR}/policy-data-quality-artifact"
LINK_SAMPLE_OUTPUT="${ARTIFACT_DIR}/policy-link-review-sample.out"
LINK_SAMPLE_ARTIFACT_DIR="${ARTIFACT_DIR}/policy-link-review-sample-artifact"
DUPLICATE_OUTPUT="${ARTIFACT_DIR}/youth-duplicate-candidate.out"
DUPLICATE_ARTIFACT_DIR="${ARTIFACT_DIR}/youth-duplicate-candidate-artifact"
QUEUE_METRICS_OUTPUT="${ARTIFACT_DIR}/policy-review-queue-metrics.out"

SUMMARY_OUT="${ARTIFACT_DIR}/policy-data-triage-observation-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/policy-data-triage-observation.json"
NOTE_OUT="${ARTIFACT_DIR}/policy-data-triage-observation-note.md"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-policy-data-triage-observation-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-policy-data-triage-observation.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-policy-data-triage-observation-note.md"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"

smoke_require_command bash
smoke_require_command python3
smoke_require_command tee
mkdir -p "${ARTIFACT_DIR}" "${DATA_QUALITY_ARTIFACT_DIR}" "${LINK_SAMPLE_ARTIFACT_DIR}" "${DUPLICATE_ARTIFACT_DIR}"

smoke_print_step "policy data triage observation"
KEEP_ARTIFACTS=true ARTIFACT_DIR="${DATA_QUALITY_ARTIFACT_DIR}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-policy-data-quality-audit.sh" | tee "${DATA_QUALITY_OUTPUT}"
KEEP_ARTIFACTS=true ARTIFACT_DIR="${LINK_SAMPLE_ARTIFACT_DIR}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-policy-link-review-sample-audit.sh" | tee "${LINK_SAMPLE_OUTPUT}"
KEEP_ARTIFACTS=true ARTIFACT_DIR="${DUPLICATE_ARTIFACT_DIR}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-youth-duplicate-candidate-audit.sh" | tee "${DUPLICATE_OUTPUT}"

smoke_db_query "
with duplicate_groups as (
    select
        ws.source_type,
        ws.title,
        coalesce(ws.host_org, '') as host_org_key,
        count(distinct coalesce(ws.detail_url, '')) as distinct_detail_url_count,
        count(distinct coalesce(ws.apply_start_date::text, '')) as distinct_apply_start_count,
        count(distinct coalesce(ws.apply_end_date::text, '')) as distinct_apply_end_count,
        count(*) as duplicate_count,
        max(ws.created_at) as latest_created_at
    from welfare_services ws
    where ws.source_type in ('YOUTH', 'BOKJIRO_LOCAL')
    group by ws.source_type, ws.title, coalesce(ws.host_org, '')
    having count(*) > 1
),
classified_duplicate_groups as (
    select
        dg.*,
        case
            when dg.source_type = 'YOUTH'
                and dg.distinct_apply_start_count <= 1
                and dg.distinct_apply_end_count <= 1
                and dg.distinct_detail_url_count <= 1
                then 'exact_duplicate_candidate'
            when dg.source_type = 'YOUTH'
                and dg.distinct_apply_start_count <= 1
                and dg.distinct_apply_end_count <= 1
                and dg.distinct_detail_url_count > 1
                then 'mirror_or_channel_variant_candidate'
            when dg.source_type = 'BOKJIRO_LOCAL'
                and dg.host_org_key = ''
                then 'title_only_false_positive_risk'
            else 'date_or_contract_drift_candidate'
        end as review_class
    from duplicate_groups dg
),
open_duplicate_groups as (
    select cdg.*
    from classified_duplicate_groups cdg
    left join policy_duplicate_review_records pr
      on pr.source_type = cdg.source_type
     and pr.title = cdg.title
     and pr.host_org_key = cdg.host_org_key
    where pr.id is null
),
open_link_reviews as (
    select ws.id as service_id
    from welfare_services ws
    left join welfare_service_details wsd on wsd.service_id = ws.id
    left join policy_link_review_records plrr on plrr.service_id = ws.id
    where coalesce(ws.detail_url, '') = ''
      and (coalesce(wsd.reference_urls_json::text, '') = '' or coalesce(wsd.reference_urls_json::text, '') = '[]')
      and ws.status in ('ACTIVE', 'UPCOMING')
      and (ws.apply_end_date is null or ws.apply_end_date >= current_date)
      and plrr.id is null
)
select 'policy_duplicate_open_groups', count(*)::text from open_duplicate_groups
union all
select 'policy_duplicate_open_rows', coalesce(sum(duplicate_count), 0)::text from open_duplicate_groups
union all
select 'policy_duplicate_open_exact_groups',
       count(*) filter (where review_class = 'exact_duplicate_candidate')::text
from open_duplicate_groups
union all
select 'policy_duplicate_open_mirror_groups',
       count(*) filter (where review_class = 'mirror_or_channel_variant_candidate')::text
from open_duplicate_groups
union all
select 'policy_duplicate_open_drift_groups',
       count(*) filter (where review_class = 'date_or_contract_drift_candidate')::text
from open_duplicate_groups
union all
select 'policy_duplicate_open_title_only_groups',
       count(*) filter (where review_class = 'title_only_false_positive_risk')::text
from open_duplicate_groups
union all
select 'policy_link_open_reviews', count(*)::text from open_link_reviews;
" > "${QUEUE_METRICS_OUTPUT}"

python3 - "${DATA_QUALITY_OUTPUT}" "${LINK_SAMPLE_OUTPUT}" "${DUPLICATE_OUTPUT}" "${QUEUE_METRICS_OUTPUT}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" <<'PY'
import json
import sys
from pathlib import Path


def parse_kv(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        if "=" not in raw_line:
            continue
        key, value = raw_line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def as_int(values: dict[str, str], key: str) -> int:
    try:
        return int(values.get(key, "0") or "0")
    except ValueError:
        return 0


data_quality = parse_kv(Path(sys.argv[1]))
link_sample = parse_kv(Path(sys.argv[2]))
duplicate = parse_kv(Path(sys.argv[3]))
queue_metrics = parse_kv(Path(sys.argv[4]))
summary_out = Path(sys.argv[5])
json_out = Path(sys.argv[6])
note_out = Path(sys.argv[7])
artifact_dir = sys.argv[8]

duplicate_groups_youth = as_int(data_quality, "duplicate_groups_youth")
duplicate_groups_bokjiro_local = as_int(data_quality, "duplicate_groups_bokjiro_local")
exact_duplicate_groups = as_int(duplicate, "exact_duplicate_groups")
mirror_variant_groups = as_int(duplicate, "mirror_variant_groups")
date_or_contract_drift_groups = as_int(duplicate, "date_or_contract_drift_groups")
active_visible_youth_total = as_int(link_sample, "active_visible_youth_total")
benefit_support_count = as_int(link_sample, "benefit_support_count")
announcement_recruitment_count = as_int(link_sample, "announcement_recruitment_count")
program_event_count = as_int(link_sample, "program_event_count")
other_count = as_int(link_sample, "other_count")
policy_duplicate_open_groups = as_int(queue_metrics, "policy_duplicate_open_groups")
policy_duplicate_open_rows = as_int(queue_metrics, "policy_duplicate_open_rows")
policy_duplicate_open_exact_groups = as_int(queue_metrics, "policy_duplicate_open_exact_groups")
policy_duplicate_open_mirror_groups = as_int(queue_metrics, "policy_duplicate_open_mirror_groups")
policy_duplicate_open_drift_groups = as_int(queue_metrics, "policy_duplicate_open_drift_groups")
policy_duplicate_open_title_only_groups = as_int(queue_metrics, "policy_duplicate_open_title_only_groups")
policy_link_open_reviews = as_int(queue_metrics, "policy_link_open_reviews")

if policy_duplicate_open_exact_groups > 0 or policy_duplicate_open_mirror_groups > 0:
    decision_class = "DUPLICATE_THEN_LINK_PRIORITY"
    operator_reading = (
        "현재 정책 운영 queue에는 YOUTH exact/mirror duplicate 후보가 열려 있습니다. "
        "duplicate queue에서 exact -> mirror 순으로 먼저 줄이고, 링크 queue는 benefit/support와 모집형을 병행 review 하는 편이 맞습니다."
    )
    next_action = "docs/policy/policy-duplicate-review-runbook.md"
elif policy_link_open_reviews > 0:
    decision_class = "LINK_REVIEW_PRIORITY"
    operator_reading = (
        "duplicate exact/mirror 운영 queue는 우선순위가 낮고, 현재는 열린 정책 링크 review를 bucket 기준으로 줄이는 편이 더 직접적입니다."
    )
    next_action = "docs/policy/policy-link-review-queue-runbook.md"
elif policy_duplicate_open_groups > 0:
    decision_class = "DRIFT_TAIL_PRIORITY"
    operator_reading = (
        "exact/mirror 우선 운영 queue는 닫혔고, 남은 duplicate open queue는 drift/title-only tail 위주입니다."
    )
    next_action = "docs/policy/policy-duplicate-review-runbook.md"
elif exact_duplicate_groups > 0 or mirror_variant_groups > 0 or active_visible_youth_total > 0:
    decision_class = "REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS"
    operator_reading = (
        "운영 review queue는 닫혀 있지만 raw audit에는 duplicate/link 후보가 계속 보입니다. "
        "이 값은 source/data 품질 잔량으로 관찰하고, 새 OPEN queue가 생길 때만 운영 review를 재개합니다."
    )
    next_action = "docs/policy/policy-data-quality-triage-runbook.md"
else:
    decision_class = "BACKLOG_STABLE"
    operator_reading = "현재 정책 backlog는 큰 drift 없이 관리 가능한 수준입니다. queue 운영 cadence만 유지하면 됩니다."
    next_action = "docs/policy/policy-data-quality-triage-runbook.md"

summary_lines = [
    "policy_data_triage_observation_suite=passed",
    f"artifact_dir={artifact_dir}",
    f"duplicate_groups_youth={duplicate_groups_youth}",
    f"duplicate_groups_bokjiro_local={duplicate_groups_bokjiro_local}",
    f"exact_duplicate_groups={exact_duplicate_groups}",
    f"mirror_variant_groups={mirror_variant_groups}",
    f"date_or_contract_drift_groups={date_or_contract_drift_groups}",
    f"active_visible_youth_total={active_visible_youth_total}",
    f"benefit_support_count={benefit_support_count}",
    f"announcement_recruitment_count={announcement_recruitment_count}",
    f"program_event_count={program_event_count}",
    f"other_count={other_count}",
    f"policy_duplicate_open_groups={policy_duplicate_open_groups}",
    f"policy_duplicate_open_rows={policy_duplicate_open_rows}",
    f"policy_duplicate_open_exact_groups={policy_duplicate_open_exact_groups}",
    f"policy_duplicate_open_mirror_groups={policy_duplicate_open_mirror_groups}",
    f"policy_duplicate_open_drift_groups={policy_duplicate_open_drift_groups}",
    f"policy_duplicate_open_title_only_groups={policy_duplicate_open_title_only_groups}",
    f"policy_link_open_reviews={policy_link_open_reviews}",
    f"data_quality_decision_class={data_quality.get('decision_class', '')}",
    f"link_sample_decision_class={link_sample.get('decision_class', '')}",
    f"duplicate_candidate_decision_class={duplicate.get('decision_class', '')}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    f"next_action={next_action}",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_out.write_text(json.dumps({
    "artifact_dir": artifact_dir,
    "data_quality": data_quality,
    "link_sample": link_sample,
    "duplicate_candidate": duplicate,
    "queue_metrics": queue_metrics,
    "decision_class": decision_class,
    "operator_reading": operator_reading,
    "next_action": next_action,
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Policy Data Triage Observation",
    "",
    f"- `decision_class`: `{decision_class}`",
    f"- `duplicate_groups_youth`: `{duplicate_groups_youth}`",
    f"- `duplicate_groups_bokjiro_local`: `{duplicate_groups_bokjiro_local}`",
    f"- `exact_duplicate_groups`: `{exact_duplicate_groups}`",
    f"- `mirror_variant_groups`: `{mirror_variant_groups}`",
    f"- `date_or_contract_drift_groups`: `{date_or_contract_drift_groups}`",
    f"- `active_visible_youth_total`: `{active_visible_youth_total}`",
    f"- `benefit_support_count`: `{benefit_support_count}`",
    f"- `announcement_recruitment_count`: `{announcement_recruitment_count}`",
    f"- `program_event_count`: `{program_event_count}`",
    f"- `other_count`: `{other_count}`",
    f"- `policy_duplicate_open_groups`: `{policy_duplicate_open_groups}`",
    f"- `policy_duplicate_open_exact_groups`: `{policy_duplicate_open_exact_groups}`",
    f"- `policy_duplicate_open_mirror_groups`: `{policy_duplicate_open_mirror_groups}`",
    f"- `policy_link_open_reviews`: `{policy_link_open_reviews}`",
    f"- `next_action`: `{next_action}`",
    "",
    "## Operator Reading",
    "",
    operator_reading,
    "",
    "## Backlog Order",
    "",
    "1. 열린 `YOUTH exact duplicate` 운영 queue",
    "2. 열린 `YOUTH mirror/channel variant` 운영 queue",
    "3. 열린 `YOUTH/BOKJIRO_LOCAL` 링크 review bucket",
    "4. raw audit duplicate/link 잔량 관찰",
]
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
