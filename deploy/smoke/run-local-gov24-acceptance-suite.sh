#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
GOV24_ACCEPTANCE_ROOT="${GOV24_ACCEPTANCE_ROOT:-${ROOT_DIR}/tmp/gov24-acceptance-suite}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${GOV24_ACCEPTANCE_ROOT}/${RUN_TS_UTC}}"
SUMMARY_OUT="${ARTIFACT_DIR}/gov24-acceptance-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/gov24-acceptance-summary.json"
DURATIONS_TSV="${ARTIFACT_DIR}/gov24-acceptance-durations.tsv"
LATEST_ARTIFACT_LINK="${GOV24_ACCEPTANCE_ROOT}/latest"
LATEST_SUMMARY_LINK="${GOV24_ACCEPTANCE_ROOT}/latest-gov24-acceptance-summary.txt"
LATEST_JSON_LINK="${GOV24_ACCEPTANCE_ROOT}/latest-gov24-acceptance-summary.json"

RUN_GOV24_REGION_BACKFILL="${RUN_GOV24_REGION_BACKFILL:-true}"
RUN_GOV24_REGION_COVERAGE="${RUN_GOV24_REGION_COVERAGE:-true}"
RUN_GOV24_COLLECT_EMBEDDING_BOUNDARY="${RUN_GOV24_COLLECT_EMBEDDING_BOUNDARY:-true}"
RUN_GOV24_SIDECAR_BACKFILL="${RUN_GOV24_SIDECAR_BACKFILL:-true}"
RUN_GOV24_QUALITY_AUDIT="${RUN_GOV24_QUALITY_AUDIT:-true}"
RUN_GOV24_SUPPORT_CONDITIONS_VALIDATION="${RUN_GOV24_SUPPORT_CONDITIONS_VALIDATION:-true}"
RUN_GOV24_TAXONOMY_VALIDATION="${RUN_GOV24_TAXONOMY_VALIDATION:-true}"
RUN_GOV24_FILTER_AXIS_AUDIT="${RUN_GOV24_FILTER_AXIS_AUDIT:-true}"
RUN_GOV24_RECOMMEND_SURFACE_AUDIT="${RUN_GOV24_RECOMMEND_SURFACE_AUDIT:-true}"
RUN_GOV24_RECOMMEND_SCORE_AUDIT="${RUN_GOV24_RECOMMEND_SCORE_AUDIT:-true}"
RUN_GOV24_SIGNAL_SMOKES="${RUN_GOV24_SIGNAL_SMOKES:-false}"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

run_step() {
  local label="$1"
  local output_file="$2"
  shift 2

  smoke_print_step "${label}"
  set +e
  smoke_duration_step "${label}" "${output_file}" "$@" >> "${DURATIONS_TSV}"
  local exit_code=$?
  set -e
  cat "${output_file}"
  return "${exit_code}"
}

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
RUN_GOV24_REGION_BACKFILL="$(smoke_normalize_bool "${RUN_GOV24_REGION_BACKFILL}")"
RUN_GOV24_REGION_COVERAGE="$(smoke_normalize_bool "${RUN_GOV24_REGION_COVERAGE}")"
RUN_GOV24_COLLECT_EMBEDDING_BOUNDARY="$(smoke_normalize_bool "${RUN_GOV24_COLLECT_EMBEDDING_BOUNDARY}")"
RUN_GOV24_SIDECAR_BACKFILL="$(smoke_normalize_bool "${RUN_GOV24_SIDECAR_BACKFILL}")"
RUN_GOV24_QUALITY_AUDIT="$(smoke_normalize_bool "${RUN_GOV24_QUALITY_AUDIT}")"
RUN_GOV24_SUPPORT_CONDITIONS_VALIDATION="$(smoke_normalize_bool "${RUN_GOV24_SUPPORT_CONDITIONS_VALIDATION}")"
RUN_GOV24_TAXONOMY_VALIDATION="$(smoke_normalize_bool "${RUN_GOV24_TAXONOMY_VALIDATION}")"
RUN_GOV24_FILTER_AXIS_AUDIT="$(smoke_normalize_bool "${RUN_GOV24_FILTER_AXIS_AUDIT}")"
RUN_GOV24_RECOMMEND_SURFACE_AUDIT="$(smoke_normalize_bool "${RUN_GOV24_RECOMMEND_SURFACE_AUDIT}")"
RUN_GOV24_RECOMMEND_SCORE_AUDIT="$(smoke_normalize_bool "${RUN_GOV24_RECOMMEND_SCORE_AUDIT}")"
RUN_GOV24_SIGNAL_SMOKES="$(smoke_normalize_bool "${RUN_GOV24_SIGNAL_SMOKES}")"

smoke_require_command bash
smoke_require_command python3
smoke_require_command tee

mkdir -p "${ARTIFACT_DIR}"
printf 'label\texit_code\tduration_ms\toutput_file\n' > "${DURATIONS_TSV}"

if [[ "${RUN_GOV24_REGION_BACKFILL}" == "true" ]]; then
  run_step \
    "gov24_region_backfill" \
    "${ARTIFACT_DIR}/gov24-region-backfill.txt" \
    env APP_BASE_URL="${APP_BASE_URL}" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-gov24-region-backfill-smoke.sh"
fi

if [[ "${RUN_GOV24_REGION_COVERAGE}" == "true" ]]; then
  run_step \
    "gov24_region_coverage" \
    "${ARTIFACT_DIR}/gov24-region-coverage.txt" \
    env APP_BASE_URL="${APP_BASE_URL}" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-gov24-region-coverage-audit.sh"
fi

if [[ "${RUN_GOV24_COLLECT_EMBEDDING_BOUNDARY}" == "true" ]]; then
  run_step \
    "gov24_collect_embedding_boundary" \
    "${ARTIFACT_DIR}/gov24-collect-embedding-boundary.txt" \
    env APP_BASE_URL="${APP_BASE_URL}" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-gov24-collect-embedding-boundary-smoke.sh"
fi

if [[ "${RUN_GOV24_SIDECAR_BACKFILL}" == "true" ]]; then
  run_step \
    "gov24_sidecar_backfill" \
    "${ARTIFACT_DIR}/gov24-sidecar-backfill.txt" \
    env APP_BASE_URL="${APP_BASE_URL}" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-gov24-sidecar-backfill-smoke.sh"
fi

if [[ "${RUN_GOV24_QUALITY_AUDIT}" == "true" ]]; then
  run_step \
    "gov24_quality_audit" \
    "${ARTIFACT_DIR}/gov24-quality-audit.txt" \
    env APP_BASE_URL="${APP_BASE_URL}" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-gov24-quality-audit.sh"
fi

if [[ "${RUN_GOV24_SUPPORT_CONDITIONS_VALIDATION}" == "true" ]]; then
  mkdir -p "${ARTIFACT_DIR}/gov24-support-conditions-validation-artifacts"
  run_step \
    "gov24_support_conditions_validation" \
    "${ARTIFACT_DIR}/gov24-support-conditions-validation.txt" \
    env APP_BASE_URL="${APP_BASE_URL}" \
    KEEP_ARTIFACTS=true \
    ARTIFACT_DIR="${ARTIFACT_DIR}/gov24-support-conditions-validation-artifacts" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-gov24-support-conditions-validation.sh"
fi

if [[ "${RUN_GOV24_TAXONOMY_VALIDATION}" == "true" ]]; then
  mkdir -p "${ARTIFACT_DIR}/gov24-taxonomy-validation-artifacts"
  run_step \
    "gov24_taxonomy_validation" \
    "${ARTIFACT_DIR}/gov24-taxonomy-validation.txt" \
    env APP_BASE_URL="${APP_BASE_URL}" \
    KEEP_ARTIFACTS=true \
    ARTIFACT_DIR="${ARTIFACT_DIR}/gov24-taxonomy-validation-artifacts" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-gov24-taxonomy-validation.sh"
fi

if [[ "${RUN_GOV24_FILTER_AXIS_AUDIT}" == "true" ]]; then
  mkdir -p "${ARTIFACT_DIR}/gov24-filter-axis-artifacts"
  run_step \
    "gov24_filter_axis_audit" \
    "${ARTIFACT_DIR}/gov24-filter-axis-audit.txt" \
    env APP_BASE_URL="${APP_BASE_URL}" \
    KEEP_ARTIFACTS=true \
    ARTIFACT_DIR="${ARTIFACT_DIR}/gov24-filter-axis-artifacts" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-gov24-filter-axis-audit.sh"
fi

if [[ "${RUN_GOV24_RECOMMEND_SURFACE_AUDIT}" == "true" ]]; then
  run_step \
    "gov24_recommend_surface_audit" \
    "${ARTIFACT_DIR}/gov24-recommend-surface-audit.txt" \
    env APP_BASE_URL="${APP_BASE_URL}" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-gov24-recommend-surface-audit.sh"
fi

if [[ "${RUN_GOV24_RECOMMEND_SCORE_AUDIT}" == "true" ]]; then
  run_step \
    "gov24_recommend_score_audit" \
    "${ARTIFACT_DIR}/gov24-recommend-score-audit.txt" \
    env APP_BASE_URL="${APP_BASE_URL}" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-gov24-recommend-score-audit.sh"
fi

if [[ "${RUN_GOV24_SIGNAL_SMOKES}" == "true" ]]; then
  mkdir -p "${ARTIFACT_DIR}/gov24-housing-signal-artifacts"
  run_step \
    "gov24_housing_signal_smoke" \
    "${ARTIFACT_DIR}/gov24-housing-signal-smoke.txt" \
    env APP_BASE_URL="${APP_BASE_URL}" KEEP_ARTIFACTS=true \
    ARTIFACT_DIR="${ARTIFACT_DIR}/gov24-housing-signal-artifacts" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-gov24-housing-signal-smoke.sh"

  mkdir -p "${ARTIFACT_DIR}/gov24-education-signal-artifacts"
  run_step \
    "gov24_education_signal_smoke" \
    "${ARTIFACT_DIR}/gov24-education-signal-smoke.txt" \
    env APP_BASE_URL="${APP_BASE_URL}" KEEP_ARTIFACTS=true \
    ARTIFACT_DIR="${ARTIFACT_DIR}/gov24-education-signal-artifacts" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-gov24-education-signal-smoke.sh"
fi

python3 - "${DURATIONS_TSV}" "${SUMMARY_OUT}" "${JSON_OUT}" "${APP_BASE_URL}" <<'PY'
import csv
import json
import sys
from pathlib import Path

durations_path = Path(sys.argv[1])
summary_path = Path(sys.argv[2])
json_path = Path(sys.argv[3])
app_base_url = sys.argv[4]

with durations_path.open("r", encoding="utf-8") as fp:
    rows = list(csv.DictReader(fp, delimiter="\t"))

failed = [row for row in rows if int(row["exit_code"]) != 0]
suite_duration_ms = sum(int(row["duration_ms"]) for row in rows)

lines = [
    "gov24_acceptance_suite=passed" if not failed else "gov24_acceptance_suite=failed",
    f"app_base_url={app_base_url}",
    f"suite_duration_ms={suite_duration_ms}",
    f"step_count={len(rows)}",
]
for row in rows:
    lines.append(f"{row['label']}_exit_code={row['exit_code']}")
    lines.append(f"{row['label']}_duration_ms={row['duration_ms']}")
    lines.append(f"{row['label']}_output_file={row['output_file']}")

summary_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
json_path.write_text(json.dumps({
    "status": "passed" if not failed else "failed",
    "appBaseUrl": app_base_url,
    "suiteDurationMs": suite_duration_ms,
    "stepCount": len(rows),
    "steps": rows,
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print(summary_path.read_text(encoding="utf-8"), end="")
if failed:
    raise SystemExit(1)
PY

ln -sfn "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
ln -sfn "${SUMMARY_OUT}" "${LATEST_SUMMARY_LINK}"
ln -sfn "${JSON_OUT}" "${LATEST_JSON_LINK}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
