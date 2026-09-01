#!/usr/bin/env bash
set -euo pipefail

PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-https://youthmoa.kr}"
PUBLIC_ROOT_URL="${PUBLIC_ROOT_URL:-${PUBLIC_BASE_URL}/}"
ACTUATOR_URL="${ACTUATOR_URL:-${PUBLIC_BASE_URL}/actuator/health}"
VERIFY_ROOT_MODULE_ASSET="${VERIFY_ROOT_MODULE_ASSET:-true}"
ALLOWED_ROOT_STATUS_CSV="${ALLOWED_ROOT_STATUS_CSV:-200}"
ALLOWED_ACTUATOR_STATUS_CSV="${ALLOWED_ACTUATOR_STATUS_CSV:-403,404}"
ALLOWED_BLOCKED_STATUS_CSV="${ALLOWED_BLOCKED_STATUS_CSV:-403,404}"
BLOCKED_EDGE_PATHS_CSV="${BLOCKED_EDGE_PATHS_CSV:-/actuator,/swagger-ui,/swagger-ui/index.html,/v3/api-docs,/v3/api-docs/swagger-config,/.env,/application.log,/dump.sql,/backup.tar.gz,/backup.archive,/wp-login.php,/xmlrpc.php,/cgi-bin/test.cgi}"
ALLOWED_UNAUTHENTICATED_ADMIN_API_STATUS_CSV="${ALLOWED_UNAUTHENTICATED_ADMIN_API_STATUS_CSV:-401}"
UNAUTHENTICATED_ADMIN_API_PATHS_CSV="${UNAUTHENTICATED_ADMIN_API_PATHS_CSV:-/api/admin/dashboard/summary,/api/admin/dashboard/attention-feed,/api/admin/users/pii-sync-status}"
REQUIRE_HSTS="${REQUIRE_HSTS:-true}"
REQUIRE_CSP="${REQUIRE_CSP:-true}"
EXPECTED_CSP_CONNECT_SRC_CSV="${EXPECTED_CSP_CONNECT_SRC_CSV:-'self',https://youthmoa.kr,https://www.youthmoa.kr}"
PRINT_SUMMARY="${PRINT_SUMMARY:-true}"
BLOCKED_EDGE_STATUS_SUMMARY=()
UNAUTHENTICATED_ADMIN_API_STATUS_SUMMARY=()
ROOT_MODULE_ASSET_URL=""
ROOT_MODULE_ASSET_STATUS=""
ROOT_MODULE_ASSET_CONTENT_TYPE=""

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

fetch_body() {
  local url="$1"
  local body_file="$2"
  curl -ksS -o "${body_file}" "${url}"
}

fetch_headers_and_body() {
  local url="$1"
  local headers_file="$2"
  local body_file="$3"
  curl -ksS -D "${headers_file}" -o "${body_file}" "${url}"
}

public_url_for_path() {
  local path="$1"
  local base="${PUBLIC_BASE_URL%/}"

  if [[ "${path}" == http://* || "${path}" == https://* ]]; then
    printf "%s" "${path}"
    return 0
  fi

  if [[ "${path}" != /* ]]; then
    path="/${path}"
  fi
  printf "%s%s" "${base}" "${path}"
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

csp_directive_sources() {
  local csp_value="$1"
  local directive_name="$2"
  awk -v directive_name="${directive_name}" '
    BEGIN { RS = ";" }
    {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", $0)
      split($0, parts, /[[:space:]]+/)
      if (parts[1] == directive_name) {
        sub(/^[^[:space:]]+[[:space:]]*/, "", $0)
        print $0
        exit
      }
    }
  ' <<< "${csp_value}"
}

space_list_contains() {
  local haystack="$1"
  local needle="$2"
  local item
  for item in ${haystack}; do
    if [[ "${item}" == "${needle}" ]]; then
      return 0
    fi
  done
  return 1
}

assert_csp_connect_src_contains_expected() {
  local csp_value="$1"
  local expected_csv="$2"
  local connect_src
  local expected
  connect_src="$(csp_directive_sources "${csp_value}" "connect-src")"
  if [[ -z "${connect_src}" ]]; then
    echo "Content-Security-Policy is missing connect-src directive" >&2
    exit 1
  fi

  IFS=',' read -r -a expected_items <<< "${expected_csv}"
  for expected in "${expected_items[@]}"; do
    expected="${expected#"${expected%%[![:space:]]*}"}"
    expected="${expected%"${expected##*[![:space:]]}"}"
    if [[ -n "${expected}" ]] && ! space_list_contains "${connect_src}" "${expected}"; then
      echo "Content-Security-Policy connect-src is missing ${expected}: ${connect_src}" >&2
      exit 1
    fi
  done
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

extract_root_module_asset_path() {
  local body_file="$1"
  sed -nE 's/.*<script[^>]+type="module"[^>]+src="([^"]+)".*/\1/p' "${body_file}" | head -n1
}

assert_root_module_asset() {
  local root_body_file="$1"
  local asset_headers_file="$2"
  local asset_body_file="$3"
  local asset_path

  asset_path="$(extract_root_module_asset_path "${root_body_file}")"
  if [[ -z "${asset_path}" ]]; then
    echo "root HTML is missing module script asset" >&2
    exit 1
  fi

  ROOT_MODULE_ASSET_URL="$(public_url_for_path "${asset_path}")"
  fetch_headers_and_body "${ROOT_MODULE_ASSET_URL}" "${asset_headers_file}" "${asset_body_file}"
  ROOT_MODULE_ASSET_STATUS="$(status_from_headers "${asset_headers_file}")"
  ROOT_MODULE_ASSET_CONTENT_TYPE="$(first_header_value "${asset_headers_file}" "Content-Type")"

  if [[ "${ROOT_MODULE_ASSET_STATUS}" != "200" ]]; then
    echo "root module asset returned ${ROOT_MODULE_ASSET_STATUS}: ${ROOT_MODULE_ASSET_URL}" >&2
    exit 1
  fi

  case "${ROOT_MODULE_ASSET_CONTENT_TYPE}" in
    application/javascript*|text/javascript*) ;;
    *)
      echo "root module asset has unexpected content type: ${ROOT_MODULE_ASSET_CONTENT_TYPE} (${ROOT_MODULE_ASSET_URL})" >&2
      exit 1
      ;;
  esac

  if grep -Eqi '<!doctype html|<html' "${asset_body_file}"; then
    echo "root module asset returned HTML fallback: ${ROOT_MODULE_ASSET_URL}" >&2
    exit 1
  fi
}

assert_blocked_edge_paths() {
  local path url headers_file status idx

  IFS=',' read -r -a blocked_paths <<< "${BLOCKED_EDGE_PATHS_CSV}"
  idx=0
  for path in "${blocked_paths[@]}"; do
    path="${path#"${path%%[![:space:]]*}"}"
    path="${path%"${path##*[![:space:]]}"}"
    [[ -z "${path}" ]] && continue

    url="$(public_url_for_path "${path}")"
    headers_file="${BLOCKED_HEADERS_DIR}/blocked-${idx}.headers"
    fetch_headers "${url}" "${headers_file}"
    status="$(status_from_headers "${headers_file}")"
    if ! csv_contains "${ALLOWED_BLOCKED_STATUS_CSV}" "${status}"; then
      echo "unexpected blocked edge path status: ${path} -> ${status} (allowed: ${ALLOWED_BLOCKED_STATUS_CSV})" >&2
      exit 1
    fi
    BLOCKED_EDGE_STATUS_SUMMARY+=("${path}=${status}")
    idx=$((idx + 1))
  done
}

assert_unauthenticated_admin_api_paths() {
  local path url headers_file status idx

  IFS=',' read -r -a admin_api_paths <<< "${UNAUTHENTICATED_ADMIN_API_PATHS_CSV}"
  idx=0
  for path in "${admin_api_paths[@]}"; do
    path="${path#"${path%%[![:space:]]*}"}"
    path="${path%"${path##*[![:space:]]}"}"
    [[ -z "${path}" ]] && continue

    url="$(public_url_for_path "${path}")"
    headers_file="${BLOCKED_HEADERS_DIR}/admin-api-${idx}.headers"
    fetch_headers "${url}" "${headers_file}"
    status="$(status_from_headers "${headers_file}")"
    if ! csv_contains "${ALLOWED_UNAUTHENTICATED_ADMIN_API_STATUS_CSV}" "${status}"; then
      echo "unexpected unauthenticated admin API status: ${path} -> ${status} (allowed: ${ALLOWED_UNAUTHENTICATED_ADMIN_API_STATUS_CSV})" >&2
      exit 1
    fi
    UNAUTHENTICATED_ADMIN_API_STATUS_SUMMARY+=("${path}=${status}")
    idx=$((idx + 1))
  done
}

if ! command -v curl >/dev/null 2>&1; then
  echo "curl command not found" >&2
  exit 1
fi

REQUIRE_HSTS="$(normalize_bool "${REQUIRE_HSTS}")"
REQUIRE_CSP="$(normalize_bool "${REQUIRE_CSP}")"
PRINT_SUMMARY="$(normalize_bool "${PRINT_SUMMARY}")"
VERIFY_ROOT_MODULE_ASSET="$(normalize_bool "${VERIFY_ROOT_MODULE_ASSET}")"

ROOT_HEADERS="$(mktemp)"
ROOT_BODY="$(mktemp)"
ACTUATOR_HEADERS="$(mktemp)"
ROOT_MODULE_ASSET_HEADERS="$(mktemp)"
ROOT_MODULE_ASSET_BODY="$(mktemp)"
BLOCKED_HEADERS_DIR="$(mktemp -d)"
trap 'rm -f "${ROOT_HEADERS}" "${ROOT_BODY}" "${ACTUATOR_HEADERS}" "${ROOT_MODULE_ASSET_HEADERS}" "${ROOT_MODULE_ASSET_BODY}"; rm -rf "${BLOCKED_HEADERS_DIR}"' EXIT

fetch_headers "${PUBLIC_ROOT_URL}" "${ROOT_HEADERS}"
fetch_body "${PUBLIC_ROOT_URL}" "${ROOT_BODY}"
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

assert_blocked_edge_paths
assert_unauthenticated_admin_api_paths

assert_header_single "${ROOT_HEADERS}" "X-Frame-Options"
assert_header_single "${ROOT_HEADERS}" "X-Content-Type-Options"

if [[ "${REQUIRE_HSTS}" == "true" ]]; then
  assert_header_single "${ROOT_HEADERS}" "Strict-Transport-Security"
fi

if [[ "${REQUIRE_CSP}" == "true" ]]; then
  assert_header_single "${ROOT_HEADERS}" "Content-Security-Policy"
  CSP_HEADER="$(first_header_value "${ROOT_HEADERS}" "Content-Security-Policy")"
  assert_csp_connect_src_contains_expected "${CSP_HEADER}" "${EXPECTED_CSP_CONNECT_SRC_CSV}"
fi

if [[ "${VERIFY_ROOT_MODULE_ASSET}" == "true" ]]; then
  assert_root_module_asset "${ROOT_BODY}" "${ROOT_MODULE_ASSET_HEADERS}" "${ROOT_MODULE_ASSET_BODY}"
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
  if [[ "${VERIFY_ROOT_MODULE_ASSET}" == "true" ]]; then
    echo "root_module_asset_url=${ROOT_MODULE_ASSET_URL}"
    echo "root_module_asset_status=${ROOT_MODULE_ASSET_STATUS}"
    echo "root_module_asset_content_type=${ROOT_MODULE_ASSET_CONTENT_TYPE}"
  fi
  for blocked_status in "${BLOCKED_EDGE_STATUS_SUMMARY[@]}"; do
    echo "blocked_path_status=${blocked_status}"
  done
  for admin_api_status in "${UNAUTHENTICATED_ADMIN_API_STATUS_SUMMARY[@]}"; do
    echo "unauthenticated_admin_api_status=${admin_api_status}"
  done
  echo "server_header=${SERVER_HEADER:-<absent>}"
  echo "strict_transport_security=$(first_header_value "${ROOT_HEADERS}" "Strict-Transport-Security")"
  echo "x_frame_options=$(first_header_value "${ROOT_HEADERS}" "X-Frame-Options")"
  echo "x_content_type_options=$(first_header_value "${ROOT_HEADERS}" "X-Content-Type-Options")"
  echo "content_security_policy=$(first_header_value "${ROOT_HEADERS}" "Content-Security-Policy")"
  if [[ "${REQUIRE_CSP}" == "true" ]]; then
    echo "csp_connect_src=$(csp_directive_sources "${CSP_HEADER}" "connect-src")"
  fi
fi
