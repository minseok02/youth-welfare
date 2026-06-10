#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_BACKEND_TESTS="${RUN_BACKEND_TESTS:-true}"
RUN_FRONTEND_BASELINE="${RUN_FRONTEND_BASELINE:-true}"
RUN_FRONTEND_E2E="${RUN_FRONTEND_E2E:-true}"
RUN_FRONTEND_ADMIN_E2E="${RUN_FRONTEND_ADMIN_E2E:-false}"
FRONTEND_E2E_MODE="${FRONTEND_E2E_MODE:-local-dev}"
FRONTEND_PUBLIC_BASE_URL="${FRONTEND_PUBLIC_BASE_URL:-${PUBLIC_BASE_URL:-}}"
RUN_OPS_BASELINE="${RUN_OPS_BASELINE:-true}"
RUN_OPS_OBSERVATION="${RUN_OPS_OBSERVATION:-true}"
RUN_COLLECT_LEGACY_REPAIR="${RUN_COLLECT_LEGACY_REPAIR:-true}"
ACTIVE_BASELINE_ROOT="${ACTIVE_BASELINE_ROOT:-${ROOT_DIR}/tmp/active-baseline-suite}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ACTIVE_BASELINE_ROOT}/${RUN_TS_UTC}}"
SUMMARY_OUT="${ARTIFACT_DIR}/active-baseline-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/active-baseline-summary.json"
DURATIONS_TSV="${ARTIFACT_DIR}/active-baseline-durations.tsv"
LATEST_ARTIFACT_LINK="${ACTIVE_BASELINE_ROOT}/latest"
LATEST_SUMMARY_LINK="${ACTIVE_BASELINE_ROOT}/latest-active-baseline-summary.txt"
LATEST_JSON_LINK="${ACTIVE_BASELINE_ROOT}/latest-active-baseline-summary.json"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

run_command_step() {
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

run_script_step() {
  local label="$1"
  local output_file="$2"
  shift 2

  run_command_step "${label}" "${output_file}" "$@"
}

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
RUN_BACKEND_TESTS="$(smoke_normalize_bool "${RUN_BACKEND_TESTS}")"
RUN_FRONTEND_BASELINE="$(smoke_normalize_bool "${RUN_FRONTEND_BASELINE}")"
RUN_FRONTEND_E2E="$(smoke_normalize_bool "${RUN_FRONTEND_E2E}")"
RUN_FRONTEND_ADMIN_E2E="$(smoke_normalize_bool "${RUN_FRONTEND_ADMIN_E2E}")"
RUN_OPS_BASELINE="$(smoke_normalize_bool "${RUN_OPS_BASELINE}")"
RUN_OPS_OBSERVATION="$(smoke_normalize_bool "${RUN_OPS_OBSERVATION}")"
RUN_COLLECT_LEGACY_REPAIR="$(smoke_normalize_bool "${RUN_COLLECT_LEGACY_REPAIR}")"

smoke_require_command bash
smoke_require_command tee

case "${FRONTEND_E2E_MODE}" in
  local-dev|deployed-origin|skip)
    ;;
  *)
    echo "FRONTEND_E2E_MODE must be one of: local-dev, deployed-origin, skip" >&2
    exit 1
    ;;
esac

mkdir -p "${ARTIFACT_DIR}"
printf 'label\texit_code\tduration_ms\toutput_file\n' > "${DURATIONS_TSV}"

if [[ "${RUN_BACKEND_TESTS}" == "true" ]]; then
  run_command_step \
    "backend_tests" \
    "${ARTIFACT_DIR}/backend-test.txt" \
    bash -lc "cd '${ROOT_DIR}/backend' && ./gradlew test --no-daemon"
fi

if [[ "${RUN_FRONTEND_BASELINE}" == "true" ]]; then
  run_command_step \
    "frontend_lint" \
    "${ARTIFACT_DIR}/frontend-lint.txt" \
    bash -lc "cd '${ROOT_DIR}/frontend' && npm run lint"

  run_command_step \
    "frontend_build" \
    "${ARTIFACT_DIR}/frontend-build.txt" \
    bash -lc "cd '${ROOT_DIR}/frontend' && npm run build"

  if [[ "${RUN_FRONTEND_E2E}" == "true" ]]; then
    case "${FRONTEND_E2E_MODE}" in
      local-dev)
        run_command_step \
          "frontend_e2e" \
          "${ARTIFACT_DIR}/frontend-e2e.txt" \
          bash -lc "cd '${ROOT_DIR}/frontend' && npm run test:e2e"
        ;;
      deployed-origin)
        if [[ -z "${FRONTEND_PUBLIC_BASE_URL}" ]]; then
          echo "FRONTEND_PUBLIC_BASE_URL or PUBLIC_BASE_URL is required for FRONTEND_E2E_MODE=deployed-origin" >&2
          exit 1
        fi
        smoke_resolve_admin_credentials "${ROOT_DIR}"
        if [[ -z "${E2E_USER_EMAIL:-}" ]]; then
          E2E_USER_EMAIL="playwright.active.baseline.${RUN_TS_UTC,,}@example.com"
        fi
        if [[ -z "${E2E_USER_PASSWORD:-}" ]]; then
          E2E_USER_PASSWORD="Password123!"
        fi
        PLAYWRIGHT_GREP_INVERT="@dev-only"
        if [[ "${RUN_FRONTEND_ADMIN_E2E}" != "true" ]]; then
          PLAYWRIGHT_GREP_INVERT="${PLAYWRIGHT_GREP_INVERT}|@admin-required"
        fi
        run_command_step \
          "frontend_e2e_bootstrap" \
          "${ARTIFACT_DIR}/frontend-e2e-bootstrap.txt" \
          bash -lc "cd '${ROOT_DIR}/frontend' && APP_BASE_URL='${APP_BASE_URL}' HEALTH_URL='${APP_BASE_URL}/actuator/health' RUN_ADMIN_SETUP='${RUN_FRONTEND_ADMIN_E2E}' E2E_ADMIN_EMAIL='${ADMIN_EMAIL:-}' E2E_ADMIN_PASSWORD='${ADMIN_PASSWORD:-}' E2E_USER_EMAIL='${E2E_USER_EMAIL}' E2E_USER_PASSWORD='${E2E_USER_PASSWORD}' ENV_FILE='${ENV_FILE:-}' DB_QUERY_USERNAME='${DB_QUERY_USERNAME:-}' DB_QUERY_PASSWORD='${DB_QUERY_PASSWORD:-}' bash ./scripts/bootstrap-playwright-smoke-data.sh"
        run_command_step \
          "frontend_e2e" \
          "${ARTIFACT_DIR}/frontend-e2e.txt" \
          bash -lc "cd '${ROOT_DIR}/frontend' && ENV_FILE='${ENV_FILE:-}' E2E_ADMIN_EMAIL='${ADMIN_EMAIL:-}' E2E_ADMIN_PASSWORD='${ADMIN_PASSWORD:-}' E2E_USER_EMAIL='${E2E_USER_EMAIL}' E2E_USER_PASSWORD='${E2E_USER_PASSWORD}' PLAYWRIGHT_SKIP_WEBSERVER=true PLAYWRIGHT_BASE_URL='${FRONTEND_PUBLIC_BASE_URL}' PLAYWRIGHT_GREP_INVERT='${PLAYWRIGHT_GREP_INVERT}' npm run test:e2e"
        ;;
      skip)
        cat <<'EOF' > "${ARTIFACT_DIR}/frontend-e2e.txt"
frontend_e2e=skipped
EOF
        printf 'frontend_e2e\t0\t0\t%s\n' "${ARTIFACT_DIR}/frontend-e2e.txt" >> "${DURATIONS_TSV}"
        cat "${ARTIFACT_DIR}/frontend-e2e.txt"
        ;;
    esac
  else
    cat <<'EOF' > "${ARTIFACT_DIR}/frontend-e2e.txt"
frontend_e2e=disabled
EOF
    printf 'frontend_e2e\t0\t0\t%s\n' "${ARTIFACT_DIR}/frontend-e2e.txt" >> "${DURATIONS_TSV}"
    cat "${ARTIFACT_DIR}/frontend-e2e.txt"
  fi
fi

if [[ "${RUN_OPS_BASELINE}" == "true" ]]; then
  run_script_step \
    "ops_baseline" \
    "${ARTIFACT_DIR}/ops-baseline.txt" \
    env ARTIFACT_DIR="${ARTIFACT_DIR}/ops-baseline-artifacts" \
    APP_BASE_URL="${APP_BASE_URL}" \
    KEEP_ARTIFACTS=true \
    bash "${ROOT_DIR}/deploy/smoke/run-local-ops-baseline-suite.sh"
fi

if [[ "${RUN_OPS_OBSERVATION}" == "true" ]]; then
  run_script_step \
    "ops_observation" \
    "${ARTIFACT_DIR}/ops-observation.txt" \
    env ARTIFACT_DIR="${ARTIFACT_DIR}/ops-observation-artifacts" \
    APP_BASE_URL="${APP_BASE_URL}" \
    KEEP_ARTIFACTS=true \
    bash "${ROOT_DIR}/deploy/smoke/run-local-ops-observation-suite.sh"
fi

if [[ "${RUN_COLLECT_LEGACY_REPAIR}" == "true" ]]; then
  run_script_step \
    "collect_legacy_repair" \
    "${ARTIFACT_DIR}/collect-legacy-repair.txt" \
    env ARTIFACT_DIR="${ARTIFACT_DIR}/collect-legacy-repair-artifacts" \
    APP_BASE_URL="${APP_BASE_URL}" \
    KEEP_ARTIFACTS=true \
    bash "${ROOT_DIR}/deploy/smoke/run-local-collect-legacy-repair-suite.sh"
fi

python3 - "${DURATIONS_TSV}" "${SUMMARY_OUT}" "${JSON_OUT}" "${APP_BASE_URL}" "${ARTIFACT_DIR}" "${RUN_TS_UTC}" "${RUN_BACKEND_TESTS}" "${RUN_FRONTEND_BASELINE}" "${RUN_FRONTEND_E2E}" "${FRONTEND_E2E_MODE}" "${FRONTEND_PUBLIC_BASE_URL}" "${RUN_OPS_BASELINE}" "${RUN_OPS_OBSERVATION}" "${RUN_COLLECT_LEGACY_REPAIR}" <<'PY'
import csv
import json
import sys
from pathlib import Path

durations_path = Path(sys.argv[1])
summary_out = Path(sys.argv[2])
json_out = Path(sys.argv[3])
app_base_url = sys.argv[4]
artifact_dir = sys.argv[5]
generated_at_utc = sys.argv[6]
run_backend_tests = sys.argv[7]
run_frontend_baseline = sys.argv[8]
run_frontend_e2e = sys.argv[9]
frontend_e2e_mode = sys.argv[10]
frontend_public_base_url = sys.argv[11]
run_ops_baseline = sys.argv[12]
run_ops_observation = sys.argv[13]
run_collect_legacy_repair = sys.argv[14]

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
suite_duration_ms = sum(int(row["duration_ms"]) for row in rows)
suite_duration_seconds = suite_duration_ms / 1000
ops_observation_summary = read_key_values(f"{artifact_dir}/ops-observation-artifacts/ops-observation-summary.txt")

lines = [
    f"active_baseline_suite={'failed' if failed else 'passed'}",
    f"generated_at_utc={generated_at_utc}",
    f"app_base_url={app_base_url}",
    f"artifact_dir={artifact_dir}",
    f"durations_tsv={artifact_dir}/active-baseline-durations.tsv",
    f"suite_duration_ms={suite_duration_ms}",
    f"suite_duration_seconds={suite_duration_seconds:.3f}",
    f"run_backend_tests={run_backend_tests}",
    f"run_frontend_baseline={run_frontend_baseline}",
    f"run_frontend_e2e={run_frontend_e2e}",
    f"frontend_e2e_mode={frontend_e2e_mode}",
    f"frontend_public_base_url={frontend_public_base_url}",
    f"run_ops_baseline={run_ops_baseline}",
    f"run_ops_observation={run_ops_observation}",
    f"run_collect_legacy_repair={run_collect_legacy_repair}",
]

for row in rows:
    seconds = int(row["duration_ms"]) / 1000
    lines.append(
        f"{row['label']}_duration_ms={row['duration_ms']}"
    )
    lines.append(
        f"{row['label']}_duration_seconds={seconds:.3f}"
    )

if run_backend_tests == "true":
    lines.append(f"backend_test_stdout={artifact_dir}/backend-test.txt")
if run_frontend_baseline == "true":
    lines.append(f"frontend_lint_stdout={artifact_dir}/frontend-lint.txt")
    lines.append(f"frontend_build_stdout={artifact_dir}/frontend-build.txt")
    lines.append(f"frontend_e2e_stdout={artifact_dir}/frontend-e2e.txt")
if run_ops_baseline == "true":
    lines.append(f"ops_baseline_stdout={artifact_dir}/ops-baseline.txt")
if run_ops_observation == "true":
    lines.append(f"ops_observation_stdout={artifact_dir}/ops-observation.txt")
    lines.append(f"ops_observation_status={ops_observation_summary.get('ops_observation_suite', '')}")
    lines.append(f"ops_observation_decision_class={ops_observation_summary.get('decision_class', '')}")
    lines.append(f"ops_attention_feed_status={ops_observation_summary.get('attention_feed_status', '')}")
    lines.append(f"ops_attention_feed_item_count={ops_observation_summary.get('attention_feed_item_count', '')}")
    lines.append(
        f"ops_attention_feed_warning_item_count="
        f"{ops_observation_summary.get('attention_feed_warning_item_count', '')}"
    )
    lines.append(f"ops_attention_feed_item_keys={ops_observation_summary.get('attention_feed_item_keys', '')}")
    lines.append(f"ops_attention_feed_item_titles={ops_observation_summary.get('attention_feed_item_titles', '')}")
    lines.append(
        f"ops_user_profile_standard_code_users_with_any_standard_code="
        f"{ops_observation_summary.get('user_profile_standard_code_users_with_any_standard_code', '')}"
    )
    lines.append(
        f"ops_user_profile_standard_code_users_missing_all_standard_codes="
        f"{ops_observation_summary.get('user_profile_standard_code_users_missing_all_standard_codes', '')}"
    )
    lines.append(
        f"ops_user_profile_standard_code_non_example_users_missing_all_standard_codes="
        f"{ops_observation_summary.get('user_profile_standard_code_non_example_users_missing_all_standard_codes', '')}"
    )
    lines.append(
        f"ops_user_profile_standard_code_example_smoke_users_missing_all_standard_codes="
        f"{ops_observation_summary.get('user_profile_standard_code_example_smoke_users_missing_all_standard_codes', '')}"
    )
    lines.append(
        f"ops_recommendation_standard_code_housing_positive_rule_delta_rows="
        f"{ops_observation_summary.get('recommendation_standard_code_housing_positive_rule_delta_rows', '')}"
    )
    lines.append(
        f"ops_recommendation_standard_code_welfare_positive_rule_scenarios="
        f"{ops_observation_summary.get('recommendation_standard_code_welfare_positive_rule_scenarios', '')}"
    )
    lines.append(
        f"ops_recommendation_standard_code_welfare_max_rule_delta="
        f"{ops_observation_summary.get('recommendation_standard_code_welfare_max_rule_delta', '')}"
    )
if run_collect_legacy_repair == "true":
    lines.append(f"collect_legacy_repair_stdout={artifact_dir}/collect-legacy-repair.txt")

summary_out.write_text("\n".join(lines) + "\n", encoding="utf-8")
json_out.write_text(json.dumps({
    "artifact_dir": artifact_dir,
    "generated_at_utc": generated_at_utc,
    "app_base_url": app_base_url,
    "suite_duration_ms": suite_duration_ms,
    "suite_duration_seconds": suite_duration_seconds,
    "run_backend_tests": run_backend_tests,
    "run_frontend_baseline": run_frontend_baseline,
    "run_frontend_e2e": run_frontend_e2e,
    "frontend_e2e_mode": frontend_e2e_mode,
    "frontend_public_base_url": frontend_public_base_url,
    "run_ops_baseline": run_ops_baseline,
    "run_ops_observation": run_ops_observation,
    "run_collect_legacy_repair": run_collect_legacy_repair,
    "ops_observation": {
        "status": ops_observation_summary.get("ops_observation_suite", ""),
        "decision_class": ops_observation_summary.get("decision_class", ""),
        "attention_feed_status": ops_observation_summary.get("attention_feed_status", ""),
        "attention_feed_item_count": ops_observation_summary.get("attention_feed_item_count", ""),
        "attention_feed_warning_item_count": ops_observation_summary.get(
            "attention_feed_warning_item_count", ""
        ),
        "attention_feed_item_keys": ops_observation_summary.get("attention_feed_item_keys", ""),
        "attention_feed_item_titles": ops_observation_summary.get("attention_feed_item_titles", ""),
        "user_profile_standard_code_users_with_any_standard_code": ops_observation_summary.get(
            "user_profile_standard_code_users_with_any_standard_code", ""
        ),
        "user_profile_standard_code_users_missing_all_standard_codes": ops_observation_summary.get(
            "user_profile_standard_code_users_missing_all_standard_codes", ""
        ),
        "recommendation_standard_code_housing_positive_rule_delta_rows": ops_observation_summary.get(
            "recommendation_standard_code_housing_positive_rule_delta_rows", ""
        ),
        "recommendation_standard_code_welfare_positive_rule_scenarios": ops_observation_summary.get(
            "recommendation_standard_code_welfare_positive_rule_scenarios", ""
        ),
        "recommendation_standard_code_welfare_max_rule_delta": ops_observation_summary.get(
            "recommendation_standard_code_welfare_max_rule_delta", ""
        ),
    },
    "steps": rows,
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(summary_out.read_text(encoding="utf-8"), end="")
if failed:
    raise SystemExit(1)
PY

smoke_publish_dir_snapshot "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
smoke_publish_file "${SUMMARY_OUT}" "${LATEST_SUMMARY_LINK}"
smoke_publish_file "${JSON_OUT}" "${LATEST_JSON_LINK}"

echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
