#!/usr/bin/env bash

PERF_ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${PERF_ROOT_DIR}/deploy/smoke/smoke-common.sh"

perf_now_ts_utc() {
  smoke_now_ts_utc
}

perf_now_ms() {
  date +%s%3N
}

perf_require_python() {
  smoke_require_command python3
}

perf_default_artifact_dir() {
  local suite_name="$1"
  local run_ts="${2:-$(perf_now_ts_utc)}"
  printf '%s/tmp/performance/%s/%s' "${PERF_ROOT_DIR}" "${suite_name}" "${run_ts}"
}

perf_publish_latest() {
  local artifact_dir="$1"
  local latest_dir="$2"
  local summary_file="${3:-}"
  local latest_summary="${4:-}"
  local json_file="${5:-}"
  local latest_json="${6:-}"

  smoke_publish_dir_snapshot "${artifact_dir}" "${latest_dir}"

  if [[ -n "${summary_file}" && -n "${latest_summary}" && -f "${summary_file}" ]]; then
    rm -f "${latest_summary}"
    smoke_publish_file "${summary_file}" "${latest_summary}"
  fi

  if [[ -n "${json_file}" && -n "${latest_json}" && -f "${json_file}" ]]; then
    rm -f "${latest_json}"
    smoke_publish_file "${json_file}" "${latest_json}"
  fi
}

perf_write_run_context() {
  local output_file="$1"
  local app_base_url="${APP_BASE_URL:-http://127.0.0.1:8082}"
  local head
  local status

  head="$(cd "${PERF_ROOT_DIR}" && git rev-parse HEAD 2>/dev/null || true)"
  status="$(cd "${PERF_ROOT_DIR}" && git status -sb 2>/dev/null | head -n 1 || true)"

  {
    echo "generated_at_utc=$(smoke_now_iso_utc)"
    echo "git_head=${head}"
    echo "git_status=${status}"
    echo "app_base_url=${app_base_url}"
    echo "host=$(hostname 2>/dev/null || true)"
  } > "${output_file}"
}

perf_duration_step() {
  local label="$1"
  local output_file="$2"
  shift 2

  local start_ms end_ms exit_code duration_ms
  start_ms="$(perf_now_ms)"
  set +e
  "$@" > "${output_file}" 2>&1
  exit_code=$?
  set -e
  end_ms="$(perf_now_ms)"
  duration_ms=$((end_ms - start_ms))

  printf '%s\t%s\t%s\t%s\n' "${label}" "${exit_code}" "${duration_ms}" "${output_file}"
  return "${exit_code}"
}
