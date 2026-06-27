#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/gov24-support-conditions-validation}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

SUMMARY_OUT="${ARTIFACT_DIR}/gov24-support-conditions-validation-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/gov24-support-conditions-validation-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/gov24-support-conditions-validation-note.md"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-gov24-support-conditions-validation-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-gov24-support-conditions-validation-summary.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-gov24-support-conditions-validation-note.md"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"

smoke_require_command python3
mkdir -p "${ARTIFACT_DIR}"

smoke_print_step "validate gov24 support conditions inventory"
python3 "${ROOT_DIR}/scripts/validate_gov24_support_conditions_inventory.py"

python3 - "${ROOT_DIR}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" <<'PY'
import json
import sys
from pathlib import Path

root_dir = Path(sys.argv[1])
summary_out = Path(sys.argv[2])
json_out = Path(sys.argv[3])
note_out = Path(sys.argv[4])
artifact_dir = sys.argv[5]

validation_path = root_dir / "tmp/gov24-support-conditions-validation/latest-gov24-support-conditions-validation.json"
validation = json.loads(validation_path.read_text(encoding="utf-8"))

status = validation["status"]
official = validation["official"]
label_comparison = validation["labelComparison"]
audit_alignment = validation["qualityAuditAlignment"]

operator_reading = (
    "Gov24 supportConditions 공식 Swagger JA inventory, mapper 정의, quality audit 목록이 일치한다."
    if status == "ok"
    else "Gov24 supportConditions Swagger inventory와 mapper 또는 quality audit 목록 사이에 drift가 있다."
)
next_action = (
    "docs/policy/policy-gov24-support-unmapped-inventory.md"
    if status == "ok"
    else "backend/src/main/java/com/example/welfare/collect/mapper/WelfareServiceMapper.java"
)

summary_lines = [
    f"gov24_support_conditions_validation_status={status}",
    f"artifact_dir={artifact_dir}",
    f"validation_artifact={validation_path.relative_to(root_dir)}",
    f"official_code_count={official['codeCount']}",
    f"handled_code_count={official['handledCodeCount']}",
    f"deferred_code_count={official['deferredCodeCount']}",
    f"unexpected_unhandled_count={len(official['unexpectedUnhandledCodes'])}",
    f"unexpected_extra_count={len(official['unexpectedHandledExtraCodes'])}",
    f"allowed_label_override_count={len(label_comparison['allowedOverrides'])}",
    f"unexpected_label_mismatch_count={len(label_comparison['unexpectedMismatches'])}",
    f"operator_reading={operator_reading}",
    f"next_action={next_action}",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "status": status,
    "artifact_dir": artifact_dir,
    "validation_artifact": str(validation_path.relative_to(root_dir)),
    "official_code_count": official["codeCount"],
    "handled_code_count": official["handledCodeCount"],
    "deferred_code_count": official["deferredCodeCount"],
    "unexpected_unhandled_codes": official["unexpectedUnhandledCodes"],
    "unexpected_handled_extra_codes": official["unexpectedHandledExtraCodes"],
    "allowed_label_overrides": label_comparison["allowedOverrides"],
    "unexpected_label_mismatches": label_comparison["unexpectedMismatches"],
    "quality_audit_alignment": audit_alignment,
    "operator_reading": operator_reading,
    "next_action": next_action,
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Gov24 Support Conditions Validation",
    "",
    f"- `status`: `{status}`",
    f"- `official_code_count`: `{official['codeCount']}`",
    f"- `handled_code_count`: `{official['handledCodeCount']}`",
    f"- `deferred_code_count`: `{official['deferredCodeCount']}`",
    f"- `unexpected_unhandled_count`: `{len(official['unexpectedUnhandledCodes'])}`",
    f"- `unexpected_extra_count`: `{len(official['unexpectedHandledExtraCodes'])}`",
    f"- `allowed_label_override_count`: `{len(label_comparison['allowedOverrides'])}`",
    f"- `unexpected_label_mismatch_count`: `{len(label_comparison['unexpectedMismatches'])}`",
    f"- `next_action`: `{next_action}`",
    "",
    "## Operator Reading",
    "",
    operator_reading,
]
note_out.write_text("\n".join(note_lines) + "\n", encoding="utf-8")
PY
smoke_sanitize_artifacts "${ARTIFACT_DIR}"

smoke_sanitize_artifacts "${ARTIFACT_DIR}"
smoke_publish_dir_snapshot "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
smoke_publish_file "${SUMMARY_OUT}" "${LATEST_SUMMARY_LINK}"
smoke_publish_file "${JSON_OUT}" "${LATEST_JSON_LINK}"
smoke_publish_file "${NOTE_OUT}" "${LATEST_NOTE_LINK}"

cat "${SUMMARY_OUT}"
echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
echo "latest_note_link=${LATEST_NOTE_LINK}"
