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

python3 - "${DATA_QUALITY_OUTPUT}" "${LINK_SAMPLE_OUTPUT}" "${DUPLICATE_OUTPUT}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" <<'PY'
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
summary_out = Path(sys.argv[4])
json_out = Path(sys.argv[5])
note_out = Path(sys.argv[6])
artifact_dir = sys.argv[7]

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

if exact_duplicate_groups > 0 or mirror_variant_groups > 0:
    decision_class = "DUPLICATE_THEN_LINK_PRIORITY"
    operator_reading = (
        "현재 정책 backlog는 YOUTH exact/mirror duplicate 후보가 먼저 보이는 상태입니다. "
        "duplicate queue에서 exact -> mirror 순으로 먼저 줄이고, 링크 queue는 benefit/support와 모집형을 병행 review 하는 편이 맞습니다."
    )
    next_action = "docs/policy/policy-duplicate-review-runbook.md"
elif active_visible_youth_total > 0:
    decision_class = "LINK_REVIEW_PRIORITY"
    operator_reading = (
        "duplicate exact 후보는 잔량이 작고, 현재는 active visible YOUTH link-empty 후보를 bucket 기준으로 review 하는 편이 더 직접적입니다."
    )
    next_action = "docs/policy/policy-link-review-queue-runbook.md"
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
    f"- `next_action`: `{next_action}`",
    "",
    "## Operator Reading",
    "",
    operator_reading,
    "",
    "## Backlog Order",
    "",
    "1. `YOUTH exact duplicate`",
    "2. `YOUTH mirror/channel variant`",
    "3. `YOUTH/BOKJIRO_LOCAL` 링크 review bucket",
    "4. `date/contract drift` tail",
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
