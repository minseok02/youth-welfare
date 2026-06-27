#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
CURRENT_PRIORITY_ROOT="${CURRENT_PRIORITY_ROOT:-${ROOT_DIR}/tmp/current-priority-suite}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_ACTIVE_BASELINE="${RUN_ACTIVE_BASELINE:-true}"
RUN_RECOMMENDATION_OBSERVATION="${RUN_RECOMMENDATION_OBSERVATION:-true}"
REUSE_RECENT_ACTIVE_BASELINE="${REUSE_RECENT_ACTIVE_BASELINE:-true}"
ACTIVE_BASELINE_REUSE_TTL_SECONDS="${ACTIVE_BASELINE_REUSE_TTL_SECONDS:-900}"
ACTIVE_BASELINE_ROOT="${ACTIVE_BASELINE_ROOT:-${ROOT_DIR}/tmp/active-baseline-suite}"
ACTIVE_BASELINE_RUN_BACKEND_TESTS="${ACTIVE_BASELINE_RUN_BACKEND_TESTS:-true}"
ACTIVE_BASELINE_RUN_FRONTEND_BASELINE="${ACTIVE_BASELINE_RUN_FRONTEND_BASELINE:-true}"
ACTIVE_BASELINE_RUN_FRONTEND_E2E="${ACTIVE_BASELINE_RUN_FRONTEND_E2E:-true}"
ACTIVE_BASELINE_RUN_FRONTEND_ADMIN_E2E="${ACTIVE_BASELINE_RUN_FRONTEND_ADMIN_E2E:-false}"
ACTIVE_BASELINE_RUN_OPS_BASELINE="${ACTIVE_BASELINE_RUN_OPS_BASELINE:-true}"
ACTIVE_BASELINE_RUN_OPS_OBSERVATION="${ACTIVE_BASELINE_RUN_OPS_OBSERVATION:-true}"
ACTIVE_BASELINE_RUN_COLLECT_LEGACY_REPAIR="${ACTIVE_BASELINE_RUN_COLLECT_LEGACY_REPAIR:-true}"

RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${CURRENT_PRIORITY_ROOT}/${RUN_TS_UTC}}"
SUMMARY_OUT="${ARTIFACT_DIR}/current-priority-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/current-priority-summary.json"
DURATIONS_TSV="${ARTIFACT_DIR}/current-priority-durations.tsv"
LATEST_ARTIFACT_LINK="${CURRENT_PRIORITY_ROOT}/latest"
LATEST_SUMMARY_LINK="${CURRENT_PRIORITY_ROOT}/latest-current-priority-summary.txt"
LATEST_JSON_LINK="${CURRENT_PRIORITY_ROOT}/latest-current-priority-summary.json"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
RUN_ACTIVE_BASELINE="$(smoke_normalize_bool "${RUN_ACTIVE_BASELINE}")"
RUN_RECOMMENDATION_OBSERVATION="$(smoke_normalize_bool "${RUN_RECOMMENDATION_OBSERVATION}")"
REUSE_RECENT_ACTIVE_BASELINE="$(smoke_normalize_bool "${REUSE_RECENT_ACTIVE_BASELINE}")"
ACTIVE_BASELINE_RUN_BACKEND_TESTS="$(smoke_normalize_bool "${ACTIVE_BASELINE_RUN_BACKEND_TESTS}")"
ACTIVE_BASELINE_RUN_FRONTEND_BASELINE="$(smoke_normalize_bool "${ACTIVE_BASELINE_RUN_FRONTEND_BASELINE}")"
ACTIVE_BASELINE_RUN_FRONTEND_E2E="$(smoke_normalize_bool "${ACTIVE_BASELINE_RUN_FRONTEND_E2E}")"
ACTIVE_BASELINE_RUN_FRONTEND_ADMIN_E2E="$(smoke_normalize_bool "${ACTIVE_BASELINE_RUN_FRONTEND_ADMIN_E2E}")"
ACTIVE_BASELINE_RUN_OPS_BASELINE="$(smoke_normalize_bool "${ACTIVE_BASELINE_RUN_OPS_BASELINE}")"
ACTIVE_BASELINE_RUN_OPS_OBSERVATION="$(smoke_normalize_bool "${ACTIVE_BASELINE_RUN_OPS_OBSERVATION}")"
ACTIVE_BASELINE_RUN_COLLECT_LEGACY_REPAIR="$(smoke_normalize_bool "${ACTIVE_BASELINE_RUN_COLLECT_LEGACY_REPAIR}")"

smoke_require_command bash
smoke_require_command tee
mkdir -p "${ARTIFACT_DIR}"
printf 'label\texit_code\tduration_ms\toutput_file\n' > "${DURATIONS_TSV}"

run_priority_step() {
  local label="$1"
  local output_file="$2"
  shift 2

  set +e
  smoke_duration_step "${label}" "${output_file}" "$@" >> "${DURATIONS_TSV}"
  local exit_code=$?
  set -e
  cat "${output_file}"
  return "${exit_code}"
}

read_summary_value() {
  local summary_file="$1"
  local key="$2"
  grep -E "^${key}=" "${summary_file}" | tail -n 1 | cut -d= -f2-
}

reuse_recent_active_baseline_if_possible() {
  local latest_summary="${ACTIVE_BASELINE_ROOT}/latest-active-baseline-summary.txt"
  local latest_json="${ACTIVE_BASELINE_ROOT}/latest-active-baseline-summary.json"
  local latest_artifact="${ACTIVE_BASELINE_ROOT}/latest"
  local latest_output="${ARTIFACT_DIR}/active-baseline.out"

  [[ "${REUSE_RECENT_ACTIVE_BASELINE}" == "true" ]] || return 1
  [[ -f "${latest_summary}" ]] || return 1
  [[ -f "${latest_json}" ]] || return 1
  [[ -d "${latest_artifact}" ]] || return 1

  local now_epoch file_epoch age_seconds
  now_epoch="$(date +%s)"
  file_epoch="$(stat -c %Y "${latest_summary}")"
  age_seconds="$((now_epoch - file_epoch))"
  (( age_seconds <= ACTIVE_BASELINE_REUSE_TTL_SECONDS )) || return 1

  [[ "$(read_summary_value "${latest_summary}" "active_baseline_suite")" == "passed" ]] || return 1
  [[ "$(read_summary_value "${latest_summary}" "app_base_url")" == "${APP_BASE_URL}" ]] || return 1
  [[ "$(read_summary_value "${latest_summary}" "run_backend_tests")" == "${ACTIVE_BASELINE_RUN_BACKEND_TESTS}" ]] || return 1
  [[ "$(read_summary_value "${latest_summary}" "run_frontend_baseline")" == "${ACTIVE_BASELINE_RUN_FRONTEND_BASELINE}" ]] || return 1
  [[ "$(read_summary_value "${latest_summary}" "run_frontend_e2e")" == "${ACTIVE_BASELINE_RUN_FRONTEND_E2E}" ]] || return 1
  [[ "$(read_summary_value "${latest_summary}" "run_frontend_admin_e2e")" == "${ACTIVE_BASELINE_RUN_FRONTEND_ADMIN_E2E}" ]] || return 1
  [[ "$(read_summary_value "${latest_summary}" "frontend_e2e_mode")" == "${FRONTEND_E2E_MODE:-local-dev}" ]] || return 1
  [[ "$(read_summary_value "${latest_summary}" "frontend_public_base_url")" == "${FRONTEND_PUBLIC_BASE_URL:-${PUBLIC_BASE_URL:-}}" ]] || return 1
  [[ "$(read_summary_value "${latest_summary}" "playwright_grep")" == "${PLAYWRIGHT_GREP:-}" ]] || return 1
  [[ "$(read_summary_value "${latest_summary}" "playwright_grep_invert")" == "${PLAYWRIGHT_GREP_INVERT:-}" ]] || return 1
  [[ "$(read_summary_value "${latest_summary}" "run_ops_baseline")" == "${ACTIVE_BASELINE_RUN_OPS_BASELINE}" ]] || return 1
  [[ "$(read_summary_value "${latest_summary}" "run_ops_observation")" == "${ACTIVE_BASELINE_RUN_OPS_OBSERVATION}" ]] || return 1
  [[ "$(read_summary_value "${latest_summary}" "run_collect_legacy_repair")" == "${ACTIVE_BASELINE_RUN_COLLECT_LEGACY_REPAIR}" ]] || return 1

  cp "${latest_summary}" "${ARTIFACT_DIR}/active-baseline-reused-summary.txt"
  cp "${latest_json}" "${ARTIFACT_DIR}/active-baseline-reused-summary.json"
  cat > "${latest_output}" <<EOF
active_baseline=reused_recent_pass
reuse_age_seconds=${age_seconds}
reuse_ttl_seconds=${ACTIVE_BASELINE_REUSE_TTL_SECONDS}
reused_summary=${ARTIFACT_DIR}/active-baseline-reused-summary.txt
reused_json=${ARTIFACT_DIR}/active-baseline-reused-summary.json
reused_artifact_dir=${latest_artifact}
EOF
  printf 'active_baseline\t0\t%s\t%s\n' \
    "$(read_summary_value "${latest_summary}" "suite_duration_ms")" \
    "${latest_output}" >> "${DURATIONS_TSV}"
  cat "${latest_output}"
  return 0
}

if [[ "${RUN_ACTIVE_BASELINE}" == "true" ]]; then
  smoke_print_step "active baseline"
  if ! reuse_recent_active_baseline_if_possible; then
    run_priority_step \
      "active_baseline" \
      "${ARTIFACT_DIR}/active-baseline.out" \
      env \
      APP_BASE_URL="${APP_BASE_URL}" \
      KEEP_ARTIFACTS=true \
      ARTIFACT_DIR="${ARTIFACT_DIR}/active-baseline-artifact" \
      ACTIVE_BASELINE_ROOT="${ACTIVE_BASELINE_ROOT}" \
      RUN_BACKEND_TESTS="${ACTIVE_BASELINE_RUN_BACKEND_TESTS}" \
      RUN_FRONTEND_BASELINE="${ACTIVE_BASELINE_RUN_FRONTEND_BASELINE}" \
      RUN_FRONTEND_E2E="${ACTIVE_BASELINE_RUN_FRONTEND_E2E}" \
      RUN_FRONTEND_ADMIN_E2E="${ACTIVE_BASELINE_RUN_FRONTEND_ADMIN_E2E}" \
      RUN_OPS_BASELINE="${ACTIVE_BASELINE_RUN_OPS_BASELINE}" \
      RUN_OPS_OBSERVATION="${ACTIVE_BASELINE_RUN_OPS_OBSERVATION}" \
      RUN_COLLECT_LEGACY_REPAIR="${ACTIVE_BASELINE_RUN_COLLECT_LEGACY_REPAIR}" \
      FRONTEND_E2E_MODE="${FRONTEND_E2E_MODE:-local-dev}" \
      FRONTEND_PUBLIC_BASE_URL="${FRONTEND_PUBLIC_BASE_URL:-${PUBLIC_BASE_URL:-}}" \
      bash "${ROOT_DIR}/deploy/smoke/run-local-active-baseline-suite.sh"
  fi
fi

if [[ "${RUN_RECOMMENDATION_OBSERVATION}" == "true" ]]; then
  smoke_print_step "recommendation observation"
  run_priority_step \
    "recommendation_observation" \
    "${ARTIFACT_DIR}/recommendation-observation.out" \
    env \
    APP_BASE_URL="${APP_BASE_URL}" \
    KEEP_ARTIFACTS=true \
    ARTIFACT_DIR="${ARTIFACT_DIR}/recommendation-observation-artifact" \
    bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-observation-suite.sh"
fi

python3 - "${DURATIONS_TSV}" "${SUMMARY_OUT}" "${JSON_OUT}" "${ARTIFACT_DIR}" "${APP_BASE_URL}" "${RUN_ACTIVE_BASELINE}" "${RUN_RECOMMENDATION_OBSERVATION}" "${REUSE_RECENT_ACTIVE_BASELINE}" "${ACTIVE_BASELINE_REUSE_TTL_SECONDS}" "${ACTIVE_BASELINE_RUN_BACKEND_TESTS}" "${ACTIVE_BASELINE_RUN_FRONTEND_BASELINE}" "${ACTIVE_BASELINE_RUN_FRONTEND_E2E}" "${ACTIVE_BASELINE_RUN_FRONTEND_ADMIN_E2E}" "${ACTIVE_BASELINE_RUN_OPS_BASELINE}" "${ACTIVE_BASELINE_RUN_OPS_OBSERVATION}" "${ACTIVE_BASELINE_RUN_COLLECT_LEGACY_REPAIR}" <<'PY'
import csv
import json
import sys
from pathlib import Path

durations_path = Path(sys.argv[1])
summary_out = Path(sys.argv[2])
json_out = Path(sys.argv[3])
artifact_dir = sys.argv[4]
app_base_url = sys.argv[5]
run_active_baseline = sys.argv[6]
run_recommendation_observation = sys.argv[7]
reuse_recent_active_baseline = sys.argv[8]
active_baseline_reuse_ttl_seconds = sys.argv[9]
active_baseline_run_backend_tests = sys.argv[10]
active_baseline_run_frontend_baseline = sys.argv[11]
active_baseline_run_frontend_e2e = sys.argv[12]
active_baseline_run_frontend_admin_e2e = sys.argv[13]
active_baseline_run_ops_baseline = sys.argv[14]
active_baseline_run_ops_observation = sys.argv[15]
active_baseline_run_collect_legacy_repair = sys.argv[16]

def read_key_values(path_str: str) -> dict[str, str]:
    path = Path(path_str)
    if not path.exists():
        return {}
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if "=" not in raw_line:
            continue
        key, value = raw_line.split("=", 1)
        values[key.strip()] = value.strip()
    return values

rows = list(csv.DictReader(durations_path.open(encoding="utf-8"), delimiter="\t"))
failed = [row for row in rows if row["exit_code"] != "0"]
active_baseline_reused = False
active_baseline_values: dict[str, str] = {}
recommendation_values = read_key_values(
    f"{artifact_dir}/recommendation-observation-artifact/recommendation-observation-summary.txt"
)

lines = [
    f"current_priority_suite={'failed' if failed else 'passed'}",
    f"artifact_dir={artifact_dir}",
    f"durations_tsv={artifact_dir}/current-priority-durations.tsv",
    f"app_base_url={app_base_url}",
    f"run_active_baseline={run_active_baseline}",
    f"run_recommendation_observation={run_recommendation_observation}",
    f"reuse_recent_active_baseline={reuse_recent_active_baseline}",
    f"active_baseline_reuse_ttl_seconds={active_baseline_reuse_ttl_seconds}",
    f"active_baseline_run_backend_tests={active_baseline_run_backend_tests}",
    f"active_baseline_run_frontend_baseline={active_baseline_run_frontend_baseline}",
    f"active_baseline_run_frontend_e2e={active_baseline_run_frontend_e2e}",
    f"active_baseline_run_frontend_admin_e2e={active_baseline_run_frontend_admin_e2e}",
    f"active_baseline_run_ops_baseline={active_baseline_run_ops_baseline}",
    f"active_baseline_run_ops_observation={active_baseline_run_ops_observation}",
    f"active_baseline_run_collect_legacy_repair={active_baseline_run_collect_legacy_repair}",
]

for row in rows:
    seconds = int(row["duration_ms"]) / 1000
    if row["label"] == "active_baseline":
        output_text = Path(row["output_file"]).read_text(encoding="utf-8", errors="replace")
        active_baseline_reused = "active_baseline=reused_recent_pass" in output_text
        if active_baseline_reused:
            active_baseline_values = read_key_values(f"{artifact_dir}/active-baseline-reused-summary.txt")
        else:
            active_baseline_values = read_key_values(
                f"{artifact_dir}/active-baseline-artifact/active-baseline-summary.txt"
            )
    lines.append(f"{row['label']}_duration_ms={row['duration_ms']}")
    lines.append(f"{row['label']}_duration_seconds={seconds:.3f}")

lines.append(f"active_baseline_reused={str(active_baseline_reused).lower()}")

if run_active_baseline == "true":
    lines.append(f"active_baseline_stdout={artifact_dir}/active-baseline.out")
    lines.append(f"active_baseline_status={active_baseline_values.get('active_baseline_suite', '')}")
    lines.append(
        f"active_baseline_ops_observation_status={active_baseline_values.get('ops_observation_status', '')}"
    )
    lines.append(
        f"active_baseline_attention_feed_status={active_baseline_values.get('ops_attention_feed_status', '')}"
    )
    lines.append(
        f"active_baseline_attention_feed_item_count="
        f"{active_baseline_values.get('ops_attention_feed_item_count', '')}"
    )
    lines.append(
        f"active_baseline_attention_feed_warning_item_count="
        f"{active_baseline_values.get('ops_attention_feed_warning_item_count', '')}"
    )
    lines.append(
        f"active_baseline_attention_feed_item_keys="
        f"{active_baseline_values.get('ops_attention_feed_item_keys', '')}"
    )
    lines.append(
        f"active_baseline_attention_feed_item_titles="
        f"{active_baseline_values.get('ops_attention_feed_item_titles', '')}"
    )
    lines.append(
        f"active_baseline_user_profile_standard_code_users_with_any_standard_code="
        f"{active_baseline_values.get('ops_user_profile_standard_code_users_with_any_standard_code', '')}"
    )
    lines.append(
        f"active_baseline_user_profile_standard_code_users_missing_all_standard_codes="
        f"{active_baseline_values.get('ops_user_profile_standard_code_users_missing_all_standard_codes', '')}"
    )
    lines.append(
        f"active_baseline_user_profile_standard_code_non_example_users_missing_all_standard_codes="
        f"{active_baseline_values.get('ops_user_profile_standard_code_non_example_users_missing_all_standard_codes', '')}"
    )
    lines.append(
        f"active_baseline_user_profile_standard_code_example_smoke_users_missing_all_standard_codes="
        f"{active_baseline_values.get('ops_user_profile_standard_code_example_smoke_users_missing_all_standard_codes', '')}"
    )
    lines.append(
        f"active_baseline_recommendation_standard_code_housing_positive_rule_delta_rows="
        f"{active_baseline_values.get('ops_recommendation_standard_code_housing_positive_rule_delta_rows', '')}"
    )
    lines.append(
        f"active_baseline_recommendation_standard_code_welfare_positive_rule_scenarios="
        f"{active_baseline_values.get('ops_recommendation_standard_code_welfare_positive_rule_scenarios', '')}"
    )
if run_recommendation_observation == "true":
    lines.append(f"recommendation_observation_stdout={artifact_dir}/recommendation-observation.out")
    lines.append(
        f"recommendation_standard_code_observation_status="
        f"{recommendation_values.get('recommendation_observation_suite', '')}"
    )
    lines.append(
        f"recommendation_standard_code_housing_positive_rule_delta_rows="
        f"{recommendation_values.get('housing_standard_code_effect_positive_rule_delta_rows', '')}"
    )
    lines.append(
        f"recommendation_standard_code_housing_max_rule_delta="
        f"{recommendation_values.get('housing_standard_code_effect_max_rule_delta', '')}"
    )
    lines.append(
        f"recommendation_standard_code_welfare_scenario_count="
        f"{recommendation_values.get('welfare_standard_code_matrix_scenario_count', '')}"
    )
    lines.append(
        f"recommendation_standard_code_welfare_positive_rule_scenarios="
        f"{recommendation_values.get('welfare_standard_code_matrix_positive_rule_scenarios', '')}"
    )
    lines.append(
        f"recommendation_standard_code_welfare_max_rule_delta="
        f"{recommendation_values.get('welfare_standard_code_matrix_max_rule_delta', '')}"
    )

summary_out.write_text("\n".join(lines) + "\n", encoding="utf-8")
json_out.write_text(json.dumps({
    "artifact_dir": artifact_dir,
    "app_base_url": app_base_url,
    "run_active_baseline": run_active_baseline,
    "run_recommendation_observation": run_recommendation_observation,
    "reuse_recent_active_baseline": reuse_recent_active_baseline,
    "active_baseline_reuse_ttl_seconds": active_baseline_reuse_ttl_seconds,
    "active_baseline_run_backend_tests": active_baseline_run_backend_tests,
    "active_baseline_run_frontend_baseline": active_baseline_run_frontend_baseline,
    "active_baseline_run_frontend_e2e": active_baseline_run_frontend_e2e,
    "active_baseline_run_frontend_admin_e2e": active_baseline_run_frontend_admin_e2e,
    "active_baseline_run_ops_baseline": active_baseline_run_ops_baseline,
    "active_baseline_run_ops_observation": active_baseline_run_ops_observation,
    "active_baseline_run_collect_legacy_repair": active_baseline_run_collect_legacy_repair,
    "active_baseline_reused": active_baseline_reused,
    "active_baseline_summary": active_baseline_values,
    "recommendation_observation_summary": recommendation_values,
    "steps": rows,
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(summary_out.read_text(encoding="utf-8"), end="")
if failed:
    raise SystemExit(1)
PY

python3 - "${SUMMARY_OUT}" "${JSON_OUT}" "${ARTIFACT_DIR}" <<'PY'
import json
import sys
from pathlib import Path

summary_out = Path(sys.argv[1])
json_out = Path(sys.argv[2])
artifact_dir = sys.argv[3]

values = {}
for raw_line in summary_out.read_text(encoding="utf-8").splitlines():
    if "=" not in raw_line:
        continue
    key, value = raw_line.split("=", 1)
    values[key.strip()] = value.strip()

values["artifact_dir"] = artifact_dir
json_out.write_text(json.dumps(values, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

smoke_sanitize_artifacts "${ARTIFACT_DIR}"
smoke_publish_dir_snapshot "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
smoke_publish_file "${SUMMARY_OUT}" "${LATEST_SUMMARY_LINK}"
smoke_publish_file "${JSON_OUT}" "${LATEST_JSON_LINK}"

echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
