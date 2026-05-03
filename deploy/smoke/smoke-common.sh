#!/usr/bin/env bash

smoke_require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing required command: $1" >&2
    exit 1
  }
}

smoke_http_status() {
  local method="$1"
  local url="$2"
  local output_file="$3"
  shift 3
  curl -sS -o "${output_file}" -w "%{http_code}" -X "${method}" "$url" "$@"
}

smoke_wait_for_health() {
  local retries="$1"
  local delay_seconds="$2"
  local health_url="$3"
  local health_response_file="$4"
  local health_stderr_file="$5"
  local status=""
  local attempt=1

  while (( attempt <= retries )); do
    if status="$(smoke_http_status GET "${health_url}" "${health_response_file}" 2>"${health_stderr_file}")"; then
      if [[ "${status}" == "200" ]]; then
        printf '%s' "${status}"
        return 0
      fi
    fi

    if (( attempt == retries )); then
      echo "health check failed after ${retries} attempts" >&2
      if [[ -s "${health_stderr_file}" ]]; then
        cat "${health_stderr_file}" >&2
      fi
      if [[ -f "${health_response_file}" ]]; then
        cat "${health_response_file}" >&2
      fi
      return 1
    fi

    sleep "${delay_seconds}"
    attempt=$((attempt + 1))
  done
}

smoke_print_step() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

smoke_assert_status() {
  local expected="$1"
  local actual="$2"
  local context="$3"
  local file_path="$4"
  if [[ "${expected}" != "${actual}" ]]; then
    echo "${context} failed: expected ${expected}, got ${actual}" >&2
    cat "${file_path}" >&2
    exit 1
  fi
}
