#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
OPS_OBSERVATION_ROOT="${OPS_OBSERVATION_ROOT:-${ROOT_DIR}/tmp/ops-observation}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OPS_OBSERVATION_ROOT}/${RUN_TS_UTC}}"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS:-14}"
TREND_WINDOW_DAYS_CSV="${TREND_WINDOW_DAYS_CSV:-1,7,30}"
BREAKDOWN_LIMIT="${BREAKDOWN_LIMIT:-3}"
COLLECT_LIMIT="${COLLECT_LIMIT:-5}"

CHILD_ARTIFACT_DIR="${ARTIFACT_DIR}/ops-baseline-artifact"
CHILD_OUTPUT="${ARTIFACT_DIR}/ops-baseline.txt"
SUMMARY_OUT="${ARTIFACT_DIR}/ops-observation-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/ops-observation.json"
NOTE_OUT="${ARTIFACT_DIR}/ops-observation-note.md"
DURATIONS_TSV="${ARTIFACT_DIR}/ops-observation-durations.tsv"

LATEST_ARTIFACT_LINK="${OPS_OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OPS_OBSERVATION_ROOT}/latest-ops-observation-summary.txt"
LATEST_JSON_LINK="${OPS_OBSERVATION_ROOT}/latest-ops-observation.json"
LATEST_NOTE_LINK="${OPS_OBSERVATION_ROOT}/latest-ops-observation-note.md"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
mkdir -p "${ARTIFACT_DIR}" "${CHILD_ARTIFACT_DIR}"
printf 'label\texit_code\tduration_ms\toutput_file\n' > "${DURATIONS_TSV}"

smoke_require_command bash
smoke_require_command python3
smoke_resolve_admin_credentials "${ROOT_DIR}"

smoke_print_step "ops baseline"
set +e
ARTIFACT_DIR="${CHILD_ARTIFACT_DIR}" \
KEEP_ARTIFACTS=true \
APP_BASE_URL="${APP_BASE_URL}" \
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS}" \
TREND_WINDOW_DAYS_CSV="${TREND_WINDOW_DAYS_CSV}" \
BREAKDOWN_LIMIT="${BREAKDOWN_LIMIT}" \
COLLECT_LIMIT="${COLLECT_LIMIT}" \
smoke_duration_step "ops_baseline" "${CHILD_OUTPUT}" \
  bash "${ROOT_DIR}/deploy/smoke/run-local-ops-baseline-suite.sh" >> "${DURATIONS_TSV}"
STATUS=$?
set -e
cat "${CHILD_OUTPUT}"
if [[ "${STATUS}" -ne 0 ]]; then
  exit "${STATUS}"
fi

python3 - "${DURATIONS_TSV}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" "${RUN_TS_UTC}" "${APP_BASE_URL}" "${SUMMARY_WINDOW_DAYS}" "${TREND_WINDOW_DAYS_CSV}" "${BREAKDOWN_LIMIT}" "${COLLECT_LIMIT}" "${CHILD_ARTIFACT_DIR}" <<'PY'
import csv
import json
import sys
from pathlib import Path


def read_key_values(path: Path):
    data = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        if "=" not in raw_line:
            continue
        key, value = raw_line.split("=", 1)
        data[key.strip()] = value.strip()
    return data


durations_path = Path(sys.argv[1])
summary_out = Path(sys.argv[2])
json_out = Path(sys.argv[3])
note_out = Path(sys.argv[4])
artifact_dir = sys.argv[5]
generated_at_utc = sys.argv[6]
app_base_url = sys.argv[7]
summary_window_days = sys.argv[8]
trend_window_days_csv = sys.argv[9]
breakdown_limit = sys.argv[10]
collect_limit = sys.argv[11]
child_artifact_dir = Path(sys.argv[12])

rows = list(csv.DictReader(durations_path.open(encoding="utf-8"), delimiter="\t"))
suite_duration_ms = sum(int(row["duration_ms"]) for row in rows)
suite_duration_seconds = suite_duration_ms / 1000

dashboard = read_key_values(child_artifact_dir / "admin-dashboard" / "stdout.txt")
collect = read_key_values(child_artifact_dir / "admin-collect-failures" / "stdout.txt")
breakdowns = read_key_values(child_artifact_dir / "admin-recommendation-breakdowns" / "stdout.txt")

failed_jobs = int(collect.get("failed_jobs_in_window", "0"))
partial_jobs = int(collect.get("partial_success_jobs_in_window", "0"))
open_circuits = int(collect.get("open_collect_circuits", "0"))

if failed_jobs == 0 and partial_jobs == 0 and open_circuits == 0:
    decision_class = "BASELINE_HEALTHY"
    operator_reading = (
        "Ops baseline is healthy. Keep dashboard, collect failures, and recommendation breakdown surfaces on the current contract."
    )
else:
    decision_class = "INVESTIGATE_COLLECT_DRIFT"
    operator_reading = (
        "Ops baseline shows collect failure drift. Read collect failures first, then confirm recommendation/admin surfaces are still aligned."
    )

next_action = "docs/core/ops-baseline-runbook.md"

lines = [
    "ops_observation_suite=passed",
    f"artifact_dir={artifact_dir}",
    f"generated_at_utc={generated_at_utc}",
    f"suite_duration_ms={suite_duration_ms}",
    f"suite_duration_seconds={suite_duration_seconds:.3f}",
    f"app_base_url={app_base_url}",
    f"summary_window_days={summary_window_days}",
    f"trend_window_days_csv={trend_window_days_csv}",
    f"collect_limit={collect_limit}",
    f"breakdown_limit={breakdown_limit}",
    f"collect_failed_jobs_in_window={failed_jobs}",
    f"collect_partial_success_jobs_in_window={partial_jobs}",
    f"open_collect_circuits={open_circuits}",
    f"collect_lane_count={collect.get('lane_count', '')}",
    f"recommendation_real_user_traffic_gate_in_window={dashboard.get('recommendation_real_user_traffic_gate_in_window', '')}",
    f"recommendation_review_gate={dashboard.get('recommendation_review_gate', '')}",
    f"recommendation_top1_leader_signal_summary={dashboard.get('recommendation_top1_leader_signal_summary', '')}",
    f"recommendation_recent_window_review_reading={dashboard.get('recommendation_recent_window_review_reading', '')}",
    f"breakdown_recent_clicked_sample_user_cohort={breakdowns.get('recent_clicked_sample_user_cohort', '')}",
    f"breakdown_real_user_traffic_gate_in_window={breakdowns.get('real_user_traffic_gate_in_window', '')}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    f"next_action={next_action}",
]

for row in rows:
    seconds = int(row["duration_ms"]) / 1000
    lines.append(f"{row['label']}_duration_ms={row['duration_ms']}")
    lines.append(f"{row['label']}_duration_seconds={seconds:.3f}")

summary_out.write_text("\n".join(lines) + "\n", encoding="utf-8")

payload = {
    "artifact_dir": artifact_dir,
    "generated_at_utc": generated_at_utc,
    "suite_duration_ms": suite_duration_ms,
    "suite_duration_seconds": suite_duration_seconds,
    "app_base_url": app_base_url,
    "summary_window_days": int(summary_window_days),
    "trend_window_days_csv": trend_window_days_csv,
    "collect_limit": int(collect_limit),
    "breakdown_limit": int(breakdown_limit),
    "collect_failed_jobs_in_window": failed_jobs,
    "collect_partial_success_jobs_in_window": partial_jobs,
    "open_collect_circuits": open_circuits,
    "collect_lane_count": int(collect.get("lane_count", "0") or 0),
    "recommendation_real_user_traffic_gate_in_window": dashboard.get("recommendation_real_user_traffic_gate_in_window", ""),
    "recommendation_review_gate": dashboard.get("recommendation_review_gate", ""),
    "recommendation_top1_leader_signal_summary": dashboard.get("recommendation_top1_leader_signal_summary", ""),
    "recommendation_recent_window_review_reading": dashboard.get("recommendation_recent_window_review_reading", ""),
    "breakdown_recent_clicked_sample_user_cohort": breakdowns.get("recent_clicked_sample_user_cohort", ""),
    "breakdown_real_user_traffic_gate_in_window": breakdowns.get("real_user_traffic_gate_in_window", ""),
    "decision_class": decision_class,
    "operator_reading": operator_reading,
    "next_action": next_action,
}
for row in rows:
    payload[f"{row['label']}_duration_ms"] = int(row["duration_ms"])
    payload[f"{row['label']}_duration_seconds"] = int(row["duration_ms"]) / 1000

json_out.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Ops Observation",
    "",
    f"- `decision_class`: `{decision_class}`",
    f"- `collect_failed_jobs_in_window`: `{failed_jobs}`",
    f"- `collect_partial_success_jobs_in_window`: `{partial_jobs}`",
    f"- `open_collect_circuits`: `{open_circuits}`",
    f"- `recommendation_review_gate`: `{dashboard.get('recommendation_review_gate', '')}`",
    f"- `recommendation_real_user_traffic_gate_in_window`: `{dashboard.get('recommendation_real_user_traffic_gate_in_window', '')}`",
    f"- `suite_duration_ms`: `{suite_duration_ms}`",
    f"- `next_action`: `{next_action}`",
    "",
    "## Operator Reading",
    "",
    operator_reading,
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
