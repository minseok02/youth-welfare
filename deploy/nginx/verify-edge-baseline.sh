#!/usr/bin/env bash
set -euo pipefail

PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-https://youthmoa.kr}"
PUBLIC_ROOT_URL="${PUBLIC_ROOT_URL:-${PUBLIC_BASE_URL}/}"
ACTUATOR_URL="${ACTUATOR_URL:-${PUBLIC_BASE_URL}/actuator/health}"
ALLOWED_ROOT_STATUS_CSV="${ALLOWED_ROOT_STATUS_CSV:-200}"
ALLOWED_ACTUATOR_STATUS_CSV="${ALLOWED_ACTUATOR_STATUS_CSV:-403,404}"
REQUIRE_HSTS="${REQUIRE_HSTS:-true}"
REQUIRE_CSP="${REQUIRE_CSP:-true}"
PRINT_SUMMARY="${PRINT_SUMMARY:-true}"

normalize_bool() {
  local value="${1,,}"
  case "${value}" in
    true|false) printf "%s" "${value}" ;;
    *)
      echo "unsupported boolean value: ${1}" >&2
      exit 1
      ;;
  esac
}

csv_contains() {
  local csv="$1"
  local needle="$2"
  local item
  IFS=',' read -r -a items <<< "${csv}"
  for item in "${items[@]}"; do
    item="${item#"${item%%[![:space:]]*}"}"
    item="${item%"${item##*[![:space:]]}"}"
    if [[ "${item}" == "${needle}" ]]; then
      return 0
    fi
  done
  return 1
}

fetch_headers() {
  local url="$1"
  local headers_file="$2"
  curl -ksS -D "${headers_file}" -o /dev/null "${url}"
}

status_from_headers() {
  local headers_file="$1"
  awk '/^HTTP\// {code=$2} END {print code}' "${headers_file}"
}

header_count() {
  local headers_file="$1"
  local header_name="$2"
  grep -i -c "^${header_name}:" "${headers_file}" || true
}

first_header_value() {
  local headers_file="$1"
  local header_name="$2"
  grep -i "^${header_name}:" "${headers_file}" | head -n1 | cut -d: -f2- | sed 's/^[[:space:]]*//'
}

assert_header_single() {
  local headers_file="$1"
  local header_name="$2"
  local count
  count="$(header_count "${headers_file}" "${header_name}")"
  if [[ "${count}" != "1" ]]; then
    echo "expected exactly one ${header_name} header, got ${count}" >&2
    exit 1
  fi
}

if ! command -v curl >/dev/null 2>&1; then
  echo "curl command not found" >&2
  exit 1
fi

REQUIRE_HSTS="$(normalize_bool "${REQUIRE_HSTS}")"
REQUIRE_CSP="$(normalize_bool "${REQUIRE_CSP}")"
PRINT_SUMMARY="$(normalize_bool "${PRINT_SUMMARY}")"

ROOT_HEADERS="$(mktemp)"
ACTUATOR_HEADERS="$(mktemp)"
trap 'rm -f "${ROOT_HEADERS}" "${ACTUATOR_HEADERS}"' EXIT

fetch_headers "${PUBLIC_ROOT_URL}" "${ROOT_HEADERS}"
fetch_headers "${ACTUATOR_URL}" "${ACTUATOR_HEADERS}"

ROOT_STATUS="$(status_from_headers "${ROOT_HEADERS}")"
ACTUATOR_STATUS="$(status_from_headers "${ACTUATOR_HEADERS}")"

if ! csv_contains "${ALLOWED_ROOT_STATUS_CSV}" "${ROOT_STATUS}"; then
  echo "unexpected root status: ${ROOT_STATUS} (allowed: ${ALLOWED_ROOT_STATUS_CSV})" >&2
  exit 1
fi

if ! csv_contains "${ALLOWED_ACTUATOR_STATUS_CSV}" "${ACTUATOR_STATUS}"; then
  echo "unexpected actuator status: ${ACTUATOR_STATUS} (allowed: ${ALLOWED_ACTUATOR_STATUS_CSV})" >&2
  exit 1
fi

assert_header_single "${ROOT_HEADERS}" "X-Frame-Options"
assert_header_single "${ROOT_HEADERS}" "X-Content-Type-Options"

if [[ "${REQUIRE_HSTS}" == "true" ]]; then
  assert_header_single "${ROOT_HEADERS}" "Strict-Transport-Security"
fi

if [[ "${REQUIRE_CSP}" == "true" ]]; then
  assert_header_single "${ROOT_HEADERS}" "Content-Security-Policy"
fi

SERVER_HEADER="$(first_header_value "${ROOT_HEADERS}" "Server")"
if [[ -n "${SERVER_HEADER}" ]] && [[ "${SERVER_HEADER}" =~ nginx/[0-9] ]]; then
  echo "server header still exposes nginx version: ${SERVER_HEADER}" >&2
  exit 1
fi

if [[ "${PRINT_SUMMARY}" == "true" ]]; then
  echo "nginx edge baseline verification passed"
  echo "public_root_url=${PUBLIC_ROOT_URL}"
  echo "actuator_url=${ACTUATOR_URL}"
  echo "root_status=${ROOT_STATUS}"
  echo "actuator_status=${ACTUATOR_STATUS}"
  echo "server_header=${SERVER_HEADER:-<absent>}"
  echo "strict_transport_security=$(first_header_value "${ROOT_HEADERS}" "Strict-Transport-Security")"
  echo "x_frame_options=$(first_header_value "${ROOT_HEADERS}" "X-Frame-Options")"
  echo "x_content_type_options=$(first_header_value "${ROOT_HEADERS}" "X-Content-Type-Options")"
  echo "content_security_policy=$(first_header_value "${ROOT_HEADERS}" "Content-Security-Policy")"
fi
