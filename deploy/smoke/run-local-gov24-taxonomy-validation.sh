#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/gov24-taxonomy-validation}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

SUMMARY_OUT="${ARTIFACT_DIR}/gov24-taxonomy-validation-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/gov24-taxonomy-validation-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/gov24-taxonomy-validation-note.md"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-gov24-taxonomy-validation-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-gov24-taxonomy-validation-summary.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-gov24-taxonomy-validation-note.md"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"

smoke_require_command python3
mkdir -p "${ARTIFACT_DIR}"

if [[ -f "${ROOT_DIR}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${ROOT_DIR}/.env"
  set +a
fi

smoke_print_step "fetch live gov24 axis inventory"
python3 "${ROOT_DIR}/scripts/fetch_gov24_axis_frequency.py"

smoke_print_step "validate gov24 taxonomy inventory"
python3 "${ROOT_DIR}/scripts/validate_gov24_taxonomy_inventory.py"

python3 - "${ROOT_DIR}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" <<'PY'
import json
import sys
from pathlib import Path

root_dir = Path(sys.argv[1])
summary_out = Path(sys.argv[2])
json_out = Path(sys.argv[3])
note_out = Path(sys.argv[4])
artifact_dir = sys.argv[5]

validation_path = root_dir / "tmp/gov24-taxonomy-validation/latest-gov24-taxonomy-validation.json"
axis_path = root_dir / "tmp/gov24-axis-frequency/latest-gov24-axis-frequency.json"

validation = json.loads(validation_path.read_text(encoding="utf-8"))
axis = json.loads(axis_path.read_text(encoding="utf-8"))

axis_summaries = {}
for axis_name, data in validation["axes"].items():
    live_axis = axis["axes"][axis_name]
    axis_summaries[axis_name] = {
        "status": data["status"],
        "live_label_count": data["liveLabelCount"],
        "java_label_count": data["javaLabelCount"],
        "sql_label_count": data["sqlLabelCount"],
        "raw_unique_count": live_axis["rawUniqueCount"],
        "token_unique_count": live_axis["tokenUniqueCount"],
    }

status = validation["status"]
operator_reading = (
    "Gov24 3축 live inventory, Java code map, SQL seed가 일치한다."
    if status == "ok"
    else "Gov24 3축 live inventory와 Java/SQL seed 사이에 drift가 있다. code map 또는 migration seed를 다시 맞춰야 한다."
)
next_action = (
    "docs/policy/policy-gov24-canonical-promotion-plan.md"
    if status == "ok"
    else "backend/src/main/resources/db/migration/V2026_06_01_01__seed_gov24_taxonomy_codes.sql"
)

summary_lines = [
    f"gov24_taxonomy_validation_status={status}",
    f"artifact_dir={artifact_dir}",
    f"axis_frequency_artifact={axis_path.relative_to(root_dir)}",
    f"validation_artifact={validation_path.relative_to(root_dir)}",
    f"total_services={axis['source']['totalCount']}",
    f"service_field_labels={axis_summaries['서비스분야']['live_label_count']}",
    f"user_type_tokens={axis_summaries['사용자구분']['token_unique_count']}",
    f"benefit_type_tokens={axis_summaries['지원유형']['token_unique_count']}",
    f"operator_reading={operator_reading}",
    f"next_action={next_action}",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "status": status,
    "artifact_dir": artifact_dir,
    "axis_frequency_artifact": str(axis_path.relative_to(root_dir)),
    "validation_artifact": str(validation_path.relative_to(root_dir)),
    "total_services": axis["source"]["totalCount"],
    "axes": axis_summaries,
    "operator_reading": operator_reading,
    "next_action": next_action,
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Gov24 Taxonomy Validation",
    "",
    f"- `status`: `{status}`",
    f"- `total_services`: `{axis['source']['totalCount']}`",
    f"- `service_field_labels`: `{axis_summaries['서비스분야']['live_label_count']}`",
    f"- `user_type_tokens`: `{axis_summaries['사용자구분']['token_unique_count']}`",
    f"- `benefit_type_tokens`: `{axis_summaries['지원유형']['token_unique_count']}`",
    f"- `next_action`: `{next_action}`",
    "",
    "## Axis Status",
    "",
]
for axis_name, data in axis_summaries.items():
    note_lines.append(
        f"- `{axis_name}`: status=`{data['status']}`, live=`{data['live_label_count']}`, java=`{data['java_label_count']}`, sql=`{data['sql_label_count']}`"
    )
note_lines.extend(["", "## Operator Reading", "", operator_reading])
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
