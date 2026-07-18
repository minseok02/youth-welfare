#!/usr/bin/env bash

smoke_trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf "%s" "${value}"
}

smoke_unquote() {
  local value="$1"
  if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf "%s" "${value}"
}

smoke_first_csv_value() {
  local value="$1"
  value="${value%%,*}"
  smoke_trim "${value}"
}

smoke_first_file_line() {
  local file_path="$1"
  local value
  IFS= read -r value < "${file_path}" || value=""
  smoke_trim "${value}"
}

smoke_load_env_file() {
  local env_file="$1"
  local line key value

  [[ -f "${env_file}" ]] || return 0

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "$(smoke_trim "${line}")" ]] && continue
    [[ "$(smoke_trim "${line}")" == \#* ]] && continue
    [[ "${line}" != *=* ]] && continue

    key="$(smoke_trim "${line%%=*}")"
    value="${line#*=}"
    value="$(smoke_unquote "${value}")"

    if [[ "${key}" == export\ * ]]; then
      key="$(smoke_trim "${key#export }")"
    fi

    if [[ ! "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      continue
    fi

    if [[ -z "${!key+x}" ]]; then
      export "${key}=${value}"
    fi
  done < "${env_file}"
}

smoke_load_env_value() {
  local env_file="$1"
  local lookup_key="$2"
  local default_value="${3:-}"
  local line key value
  local result="${default_value}"

  [[ -f "${env_file}" ]] || {
    printf "%s" "${result}"
    return 0
  }

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "$(smoke_trim "${line}")" ]] && continue
    [[ "$(smoke_trim "${line}")" == \#* ]] && continue
    [[ "${line}" != *=* ]] && continue

    key="$(smoke_trim "${line%%=*}")"
    value="${line#*=}"
    value="$(smoke_unquote "${value}")"

    if [[ "${key}" == export\ * ]]; then
      key="$(smoke_trim "${key#export }")"
    fi

    if [[ "${key}" == "${lookup_key}" ]]; then
      result="${value}"
    fi
  done < "${env_file}"

  printf "%s" "${result}"
}

smoke_default_root_dir() {
  cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd
}

smoke_resolve_env_file() {
  local raw_env_file="${1:-${ENV_FILE:-$(smoke_default_root_dir)/.env}}"
  local root_dir="${2:-$(smoke_default_root_dir)}"
  local candidate=""
  local dir_part=""
  local base_part=""

  if [[ -z "${raw_env_file}" ]]; then
    printf '%s' ""
    return 0
  fi

  case "${raw_env_file}" in
    /*)
      printf '%s' "${raw_env_file}"
      return 0
      ;;
  esac

  if [[ -f "${raw_env_file}" ]]; then
    dir_part="$(cd "$(dirname "${raw_env_file}")" && pwd)"
    base_part="$(basename "${raw_env_file}")"
    printf '%s/%s' "${dir_part}" "${base_part}"
    return 0
  fi

  candidate="${root_dir}/${raw_env_file}"
  if [[ -f "${candidate}" ]]; then
    printf '%s' "${candidate}"
    return 0
  fi

  printf '%s' "${raw_env_file}"
}

smoke_resolve_admin_credentials() {
  local root_dir="$1"
  local env_file
  local admin_email_file="${ADMIN_EMAIL_FILE:-/tmp/youth-welfare-admin-smoke-email}"
  local admin_password_file="${ADMIN_PASSWORD_FILE:-/tmp/youth-welfare-admin-smoke-password}"
  local env_admin_email
  local env_admin_password
  local env_security_admin_emails

  env_file="$(smoke_resolve_env_file "${ENV_FILE:-${root_dir}/.env}" "${root_dir}")"

  env_admin_email="$(smoke_load_env_value "${env_file}" ADMIN_EMAIL)"
  env_admin_password="$(smoke_load_env_value "${env_file}" ADMIN_PASSWORD)"
  env_security_admin_emails="$(smoke_load_env_value "${env_file}" SECURITY_ADMIN_EMAILS)"

  if [[ -z "${SECURITY_ADMIN_EMAILS:-}" && -n "${env_security_admin_emails}" ]]; then
    export SECURITY_ADMIN_EMAILS
    SECURITY_ADMIN_EMAILS="${env_security_admin_emails}"
  fi

  if [[ -z "${ADMIN_EMAIL:-}" && -r "${admin_email_file}" ]]; then
    export ADMIN_EMAIL
    ADMIN_EMAIL="$(smoke_first_file_line "${admin_email_file}")"
  fi

  if [[ -z "${ADMIN_PASSWORD:-}" && -r "${admin_password_file}" ]]; then
    export ADMIN_PASSWORD
    ADMIN_PASSWORD="$(smoke_first_file_line "${admin_password_file}")"
  fi

  if [[ -z "${ADMIN_EMAIL:-}" && -n "${env_admin_email}" ]]; then
    export ADMIN_EMAIL
    ADMIN_EMAIL="${env_admin_email}"
  fi

  if [[ -z "${ADMIN_PASSWORD:-}" && -n "${env_admin_password}" ]]; then
    export ADMIN_PASSWORD
    ADMIN_PASSWORD="${env_admin_password}"
  fi

  if [[ -z "${ADMIN_EMAIL:-}" && -n "${SECURITY_ADMIN_EMAILS:-}" ]]; then
    export ADMIN_EMAIL
    ADMIN_EMAIL="$(smoke_first_csv_value "${SECURITY_ADMIN_EMAILS}")"
  fi
}

smoke_extract_jwt_roles_from_token() {
  local token="$1"

  smoke_require_command python3

  python3 - "${token}" <<'PY'
import base64
import json
import sys

token = sys.argv[1].strip()
if not token:
    print("")
    raise SystemExit(0)

parts = token.split(".")
if len(parts) < 2:
    print("")
    raise SystemExit(0)

payload = parts[1] + "=" * (-len(parts[1]) % 4)
try:
    decoded = json.loads(base64.urlsafe_b64decode(payload))
except Exception:
    print("")
    raise SystemExit(0)

roles = decoded.get("roles") or []
if not isinstance(roles, list):
    print("")
    raise SystemExit(0)

print(",".join(str(role) for role in roles))
PY
}

smoke_mint_access_token() {
  local jwt_secret="$1"
  local user_key="$2"
  local user_id="$3"
  local roles_csv="$4"
  local access_expiration_ms="$5"

  smoke_require_command python3

  python3 - "${jwt_secret}" "${user_key}" "${user_id}" "${roles_csv}" "${access_expiration_ms}" <<'PY'
import base64
import hashlib
import hmac
import json
import sys
import time

secret, user_key, user_id_raw, roles_csv, expiration_ms_raw = sys.argv[1:6]
user_id = int(user_id_raw)
expiration_ms = int(expiration_ms_raw)
issued_at_ms = int(time.time() * 1000)

secret_bytes = secret.encode("utf-8")
secret_len = len(secret_bytes)
if secret_len >= 64:
    alg = "HS512"
    digest = hashlib.sha512
elif secret_len >= 48:
    alg = "HS384"
    digest = hashlib.sha384
else:
    alg = "HS256"
    digest = hashlib.sha256

header = {"alg": alg, "typ": "JWT"}
payload = {
    "sub": user_key,
    "uid": user_id,
    "roles": [role for role in roles_csv.split(",") if role],
    "iat": issued_at_ms // 1000,
    "exp": (issued_at_ms + expiration_ms) // 1000,
    "iatm": issued_at_ms,
}

def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")

header_b64 = b64url(json.dumps(header, separators=(",", ":"), ensure_ascii=False).encode("utf-8"))
payload_b64 = b64url(json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode("utf-8"))
signing_input = f"{header_b64}.{payload_b64}".encode("ascii")
signature = hmac.new(secret_bytes, signing_input, digest).digest()
print(f"{header_b64}.{payload_b64}.{b64url(signature)}")
PY
}

smoke_resolve_admin_access_token() {
  local root_dir="$1"
  local env_file
  local admin_access_token_file="${ADMIN_ACCESS_TOKEN_FILE:-/tmp/youth-welfare-admin-smoke-access-token}"
  local allow_admin_jwt_mint
  local jwt_secret
  local access_expiration_ms
  local admin_lookup_hash
  local user_row=""
  local user_id=""
  local user_key=""

  if [[ -z "${ADMIN_ACCESS_TOKEN:-}" && -r "${admin_access_token_file}" ]]; then
    export ADMIN_ACCESS_TOKEN
    ADMIN_ACCESS_TOKEN="$(smoke_first_file_line "${admin_access_token_file}")"
  fi

  if [[ -n "${ADMIN_ACCESS_TOKEN:-}" ]]; then
    return 0
  fi

  allow_admin_jwt_mint="$(smoke_normalize_bool "${ALLOW_ADMIN_JWT_MINT:-false}")"
  if [[ "${allow_admin_jwt_mint}" != "true" ]]; then
    return 0
  fi

  smoke_resolve_admin_credentials "${root_dir}"

  if [[ -z "${ADMIN_EMAIL:-}" ]]; then
    return 0
  fi

  env_file="$(smoke_resolve_env_file "${ENV_FILE:-${root_dir}/.env}" "${root_dir}")"
  jwt_secret="$(smoke_load_env_value "${env_file}" JWT_SECRET)"
  access_expiration_ms="$(smoke_load_env_value "${env_file}" JWT_ACCESS_EXPIRATION 1800000)"

  if [[ -z "${jwt_secret}" ]]; then
    return 0
  fi

  admin_lookup_hash="$(smoke_sha256_hex "${ADMIN_EMAIL}")"

  set +e
  user_row="$(
    smoke_db_query "
      WITH admin_candidate AS (
        SELECT u.id, u.user_key
        FROM auth_users au
        JOIN users u ON u.user_key = au.user_key
        WHERE au.email_lookup_hash = '${admin_lookup_hash}'
          AND COALESCE(au.is_active, true) = true
          AND COALESCE(u.is_active, true) = true
        UNION ALL
        SELECT u.id, u.user_key
        FROM users u
        WHERE lower(COALESCE(u.email, '')) = lower('${ADMIN_EMAIL}')
          AND COALESCE(u.is_active, true) = true
      )
      SELECT id, user_key
      FROM admin_candidate
      LIMIT 1;
    " 2>/dev/null
  )"
  set -e

  if [[ -z "${user_row}" ]]; then
    return 0
  fi

  IFS=$'\t' read -r user_id user_key <<< "${user_row}"

  if [[ -z "${user_id}" || -z "${user_key}" ]]; then
    return 0
  fi

  export ADMIN_ACCESS_TOKEN
  ADMIN_ACCESS_TOKEN="$(smoke_mint_access_token "${jwt_secret}" "${user_key}" "${user_id}" "ROLE_USER,ROLE_ADMIN" "${access_expiration_ms}")"
}

smoke_login_admin_access_token() {
  local root_dir="$1"
  local app_base_url="$2"
  local login_dir
  local login_body
  local login_response
  local login_status
  local roles_csv

  smoke_resolve_admin_credentials "${root_dir}"
  smoke_resolve_admin_access_token "${root_dir}"

  if [[ -n "${ADMIN_ACCESS_TOKEN:-}" ]]; then
    return 0
  fi

  : "${ADMIN_EMAIL:?ADMIN_EMAIL is empty; export ADMIN_EMAIL or set SECURITY_ADMIN_EMAILS/.env or /tmp/youth-welfare-admin-smoke-email}"
  : "${ADMIN_PASSWORD:?ADMIN_PASSWORD is empty; export ADMIN_PASSWORD or set /tmp/youth-welfare-admin-smoke-password}"

  smoke_require_command python3

  login_dir="$(mktemp -d)"
  login_body="${login_dir}/admin-login-body.json"
  login_response="${login_dir}/admin-login-response.json"

  python3 - "${ADMIN_EMAIL}" "${ADMIN_PASSWORD}" "${login_body}" <<'PY'
import json
import sys

email, password, output_path = sys.argv[1:4]
with open(output_path, "w", encoding="utf-8") as fp:
    json.dump({"email": email, "password": password}, fp, ensure_ascii=False)
PY

  login_status="$(
    smoke_http_status POST "${app_base_url}/api/auth/login" "${login_response}" \
      -H "Content-Type: application/json" \
      --data-binary "@${login_body}"
  )"
  if [[ "${login_status}" != "200" ]]; then
    echo "admin smoke login failed: expected 200, got ${login_status}" >&2
    smoke_print_redacted_file_for_log "${login_response}"
    rm -rf "${login_dir}"
    return 1
  fi

  ADMIN_ACCESS_TOKEN="$(
    python3 - "${login_response}" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    print(json.load(fp)["data"]["accessToken"])
PY
  )"
  export ADMIN_ACCESS_TOKEN

  roles_csv="$(smoke_extract_jwt_roles_from_token "${ADMIN_ACCESS_TOKEN}")"
  if [[ ",${roles_csv}," != *",ROLE_ADMIN,"* ]]; then
    echo "admin smoke login succeeded but ROLE_ADMIN is missing for ${ADMIN_EMAIL}" >&2
    rm -rf "${login_dir}"
    return 1
  fi

  rm -rf "${login_dir}"
}

smoke_require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing required command: $1" >&2
    exit 1
  }
}

smoke_normalize_bool() {
  local value="${1,,}"
  case "${value}" in
    true|false) printf '%s' "${value}" ;;
    *)
      echo "unsupported flag value: ${1}" >&2
      exit 1
      ;;
  esac
}

smoke_normalize_tri_state() {
  local value="${1,,}"
  case "${value}" in
    true|false|auto) printf '%s' "${value}" ;;
    *)
      echo "unsupported tri-state value: ${1}" >&2
      exit 1
      ;;
  esac
}

smoke_now_ts_utc() {
  date -u +%Y%m%dT%H%M%SZ
}

smoke_now_iso_utc() {
  date -u +%Y-%m-%dT%H:%M:%SZ
}

smoke_now_iso_kst() {
  TZ=Asia/Seoul date +%Y-%m-%dT%H:%M:%S%z
}

smoke_now_ms() {
  date +%s%3N
}

smoke_duration_step() {
  local label="$1"
  local output_file="$2"
  shift 2

  local start_ms end_ms exit_code duration_ms
  start_ms="$(smoke_now_ms)"
  set +e
  "$@" > "${output_file}" 2>&1
  exit_code=$?
  set -e
  end_ms="$(smoke_now_ms)"
  duration_ms=$((end_ms - start_ms))

  printf '%s\t%s\t%s\t%s\n' "${label}" "${exit_code}" "${duration_ms}" "${output_file}"
  return "${exit_code}"
}

smoke_update_links() {
  if (( $# == 0 || $# % 2 != 0 )); then
    echo "smoke_update_links requires target/link pairs" >&2
    exit 1
  fi

  local target link link_dir target_path target_dir target_base
  while (( $# > 0 )); do
    target="$1"
    link="$2"
    link_dir="$(dirname -- "${link}")"
    smoke_secure_mkdir "${link_dir}"

    target_path="${target}"
    if [[ "${target}" != /* && ( -e "${target}" || -L "${target}" ) ]]; then
      target_dir="$(dirname -- "${target}")"
      target_base="$(basename -- "${target}")"
      target_path="$(cd "${target_dir}" && pwd -P)/${target_base}"
    fi

    ln -sfn "${target_path}" "${link}"
    shift 2
  done
}

smoke_secure_mkdir() {
  local dir="$1"

  mkdir -p "${dir}"
  case "${dir}" in
    ""|"."|"/") return 0 ;;
  esac
  chmod 700 "${dir}" 2>/dev/null || true
}

smoke_restrict_artifact_permissions() {
  local artifact_dir="$1"

  [[ -d "${artifact_dir}" ]] || return 0
  case "${artifact_dir}" in
    ""|"."|"/") return 0 ;;
  esac

  chmod 700 "${artifact_dir}" 2>/dev/null || true
  find "${artifact_dir}" -type d -exec chmod 700 {} + 2>/dev/null || true
  find "${artifact_dir}" -type f -exec chmod 600 {} + 2>/dev/null || true
}

smoke_redact_stream_for_log() {
  if command -v python3 >/dev/null 2>&1; then
    python3 -c "$(cat <<'PY'
import re
import sys

literal_patterns = [
    (re.compile(r"eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}"), "<redacted-jwt>"),
    (re.compile(r"(?i)(Bearer\s+)[A-Za-z0-9._~+/=-]+"), r"\1<redacted>"),
    (re.compile(r"(?i)\b(Set-Cookie:\s*)[^\r\n]+"), r"\1<redacted>"),
    (re.compile(r"(?i)\b(Cookie:\s*)[^\r\n]+"), r"\1<redacted>"),
    (re.compile(r"(?i)\b((?:jdbc:)?(?:postgresql|postgres|mysql)://)[^/@\s:]+:[^/@\s]+@"), r"\1<redacted>@"),
    (re.compile(r"(?i)([?&;](?:password|sslpassword|token|access_token|refresh_token|api_key|apikey|client_secret|secret|key|authorization_code|verification_code|reset_code|code)=)[^&;\s]*"), r"\1<redacted>"),
    (re.compile(r"(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b"), "<redacted-email>"),
]

json_key_pattern = re.compile(
    r'("(?i:accessToken|refreshToken|idToken|token|password|authorization|cookie|setCookie|jwtSecret|jwt_secret|apiKey|api_key|clientSecret|client_secret|secret|email|userKey)"\s*:\s*)"[^"]*"'
)

kv_pattern = re.compile(
    r"(?im)^(\s*(?:access_token|refresh_token|admin_access_token|jwt_secret|api_key|client_secret|secret|password|authorization|cookie|set_cookie|email|user_key|userKey|targetUserKey)\s*[=:]\s*).*$"
)

env_key_pattern = re.compile(
    r"(?im)^(\s*(?:export\s+)?(?:[A-Z0-9_]*(?:PASSWORD|SECRET|TOKEN|API_KEY|CLIENT_SECRET)[A-Z0-9_]*|AUTHORIZATION|COOKIE|SET_COOKIE|EMAIL|USER_KEY|TARGET_USER_KEY)\s*=\s*).*$"
)

def redact(text: str) -> str:
    redacted = json_key_pattern.sub(r'\1"<redacted>"', text)
    redacted = kv_pattern.sub(r"\1<redacted>", redacted)
    redacted = env_key_pattern.sub(r"\1<redacted>", redacted)
    for pattern, replacement in literal_patterns:
        redacted = pattern.sub(replacement, redacted)
    return redacted

for line in sys.stdin:
    sys.stdout.write(redact(line))
PY
)"
    return $?
  fi

  sed -E \
    -e 's#(Bearer[[:space:]]+)[A-Za-z0-9._~+/=-]+#\1<redacted>#Ig' \
    -e 's#((jdbc:)?(postgresql|postgres|mysql)://)[^/@[:space:]:]+:[^/@[:space:]]+@#\1<redacted>@#Ig' \
    -e 's#([?&;](password|sslpassword|token|access_token|refresh_token|api_key|apikey|client_secret|secret|key|authorization_code|verification_code|reset_code|code)=)[^&;[:space:]]*#\1<redacted>#Ig' \
    -e 's#^([[:space:]]*(export[[:space:]]+)?[A-Z0-9_]*(PASSWORD|SECRET|TOKEN|API_KEY|CLIENT_SECRET)[A-Z0-9_]*[[:space:]]*=[[:space:]]*).*$#\1<redacted>#Ig'
}

smoke_print_redacted_file_for_log() {
  local file_path="$1"

  [[ -f "${file_path}" ]] || return 0
  smoke_redact_stream_for_log < "${file_path}" >&2
}

smoke_publish_file() {
  local source_file="$1"
  local dest_file="$2"
  local dest_dir

  dest_dir="$(dirname "${dest_file}")"
  smoke_secure_mkdir "${dest_dir}"
  install -m 600 "${source_file}" "${dest_file}"
}

smoke_publish_dir_snapshot() {
  local source_dir="$1"
  local dest_dir="$2"
  local dest_parent tmp_dir

  dest_parent="$(dirname "${dest_dir}")"
  smoke_secure_mkdir "${dest_parent}"
  smoke_restrict_artifact_permissions "${source_dir}"
  tmp_dir="${dest_dir}.tmp.$$"
  rm -rf "${tmp_dir}"
  smoke_secure_mkdir "${tmp_dir}"
  cp -a "${source_dir}/." "${tmp_dir}/"
  smoke_restrict_artifact_permissions "${tmp_dir}"
  rm -rf "${dest_dir}"
  mv "${tmp_dir}" "${dest_dir}"
  smoke_restrict_artifact_permissions "${dest_dir}"
}

smoke_sanitize_artifacts() {
  local artifact_dir="$1"

  [[ -d "${artifact_dir}" ]] || return 0

  find "${artifact_dir}" -type f \( \
    -name '*.cookie' -o \
    -name '*cookie*' -o \
    -name '*cookies*' \
  \) -delete 2>/dev/null || true

  if ! command -v python3 >/dev/null 2>&1; then
    smoke_restrict_artifact_permissions "${artifact_dir}"
    return 0
  fi

  python3 - "${artifact_dir}" <<'PY'
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])

literal_patterns = [
    # JWT-shaped access tokens.
    (re.compile(r"eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}"), "<redacted-jwt>"),
    # Authorization headers in stdout/stderr artifacts.
    (re.compile(r"(?i)(Bearer\s+)[A-Za-z0-9._~+/=-]+"), r"\1<redacted>"),
    # HTTP cookie headers and JDBC/SQL URLs can appear in retained command output.
    (re.compile(r"(?i)\b(Set-Cookie:\s*)[^\r\n]+"), r"\1<redacted>"),
    (re.compile(r"(?i)\b(Cookie:\s*)[^\r\n]+"), r"\1<redacted>"),
    (re.compile(r"(?i)\b((?:jdbc:)?(?:postgresql|postgres|mysql)://)[^/@\s:]+:[^/@\s]+@"), r"\1<redacted>@"),
    (re.compile(r"(?i)([?&;](?:password|sslpassword|token|access_token|refresh_token|api_key|apikey|client_secret|secret|key|authorization_code|verification_code|reset_code|code)=)[^&;\s]*"), r"\1<redacted>"),
    # Email addresses are not needed in retained smoke artifacts.
    (re.compile(r"(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b"), "<redacted-email>"),
]

json_key_pattern = re.compile(
    r'("(?i:accessToken|refreshToken|idToken|token|password|authorization|cookie|setCookie|jwtSecret|jwt_secret|apiKey|api_key|clientSecret|client_secret|secret|email|userKey)"\s*:\s*)"[^"]*"'
)

kv_pattern = re.compile(
    r"(?im)^(\s*(?:access_token|refresh_token|admin_access_token|jwt_secret|api_key|client_secret|secret|password|authorization|cookie|set_cookie|email|user_key|userKey|targetUserKey)\s*[=:]\s*).*$"
)

env_key_pattern = re.compile(
    r"(?im)^(\s*(?:export\s+)?(?:[A-Z0-9_]*(?:PASSWORD|SECRET|TOKEN|API_KEY|CLIENT_SECRET)[A-Z0-9_]*|AUTHORIZATION|COOKIE|SET_COOKIE|EMAIL|USER_KEY|TARGET_USER_KEY)\s*=\s*).*$"
)

for path in root.rglob("*"):
    if not path.is_file():
        continue
    try:
        raw = path.read_bytes()
    except OSError:
        continue
    if b"\x00" in raw:
        continue
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        text = raw.decode("utf-8", errors="ignore")

    redacted = json_key_pattern.sub(r'\1"<redacted>"', text)
    redacted = kv_pattern.sub(r"\1<redacted>", redacted)
    redacted = env_key_pattern.sub(r"\1<redacted>", redacted)
    for pattern, replacement in literal_patterns:
        redacted = pattern.sub(replacement, redacted)

    if redacted != text:
        path.write_text(redacted, encoding="utf-8")
PY

  smoke_restrict_artifact_permissions "${artifact_dir}"
}

smoke_http_status() {
  local method="$1"
  local url="$2"
  local output_file="$3"
  shift 3
  curl -sS -o "${output_file}" -w "%{http_code}" -X "${method}" "$url" "$@"
}

smoke_sha256_hex() {
  local raw_value="$1"

  smoke_require_command python3

  python3 - "${raw_value}" <<'PY'
import hashlib
import sys

print(hashlib.sha256(sys.argv[1].strip().lower().encode("utf-8")).hexdigest())
PY
}

smoke_user_email_shadow_value() {
  local raw_email="$1"

  smoke_require_command python3

  python3 - "${raw_email}" <<'PY'
import hashlib
import sys

safe_domains = {
    "example.com",
    "example.org",
    "example.net",
    "youth-welfare.dev",
    "realuser.app",
    "smoke.local",
    "localhost",
}

email = (sys.argv[1] or "").strip().lower()
if not email:
    print(email)
    raise SystemExit

domain = email.rsplit("@", 1)[1] if "@" in email else ""
if (
    domain in safe_domains
    or domain.endswith(".local")
    or domain.endswith(".test")
    or domain.endswith(".invalid")
):
    print(email)
else:
    print("shadow_" + hashlib.sha256(email.encode("utf-8")).hexdigest())
PY
}

smoke_sql_quote() {
  local raw_value="$1"
  printf "'%s'" "${raw_value//\'/\'\'}"
}

smoke_health_status_is_up() {
  local health_response_file="$1"

  smoke_require_command python3

  python3 - "${health_response_file}" <<'PY'
import json
import sys

try:
    with open(sys.argv[1], "r", encoding="utf-8") as fp:
        payload = json.load(fp)
except Exception:
    sys.exit(1)

sys.exit(0 if payload.get("status") == "UP" else 1)
PY
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
      if [[ "${status}" == "200" ]] && smoke_health_status_is_up "${health_response_file}"; then
        printf '%s' "${status}"
        return 0
      fi
    fi

    if (( attempt == retries )); then
      echo "health check failed after ${retries} attempts" >&2
      if [[ -s "${health_stderr_file}" ]]; then
        smoke_print_redacted_file_for_log "${health_stderr_file}"
      fi
      if [[ -f "${health_response_file}" ]]; then
        smoke_print_redacted_file_for_log "${health_response_file}"
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
    smoke_print_redacted_file_for_log "${file_path}"
    exit 1
  fi
}

smoke_db_container_available() {
  local db_container_name="$1"

  command -v docker >/dev/null 2>&1 || return 1
  docker ps --format '{{.Names}}' | grep -Fxq "${db_container_name}"
}

smoke_resolve_db_mode() {
  local requested_mode="${SMOKE_DB_MODE:-auto}"
  local db_container_name="${DB_CONTAINER_NAME:-${POSTGRES_CONTAINER_NAME:-youth-welfare-db}}"

  case "${requested_mode}" in
    auto)
      if smoke_db_container_available "${db_container_name}"; then
        printf '%s' "docker"
      else
        printf '%s' "postgres"
      fi
      ;;
    docker|postgres)
      printf '%s' "${requested_mode}"
      ;;
    *)
      echo "unsupported SMOKE_DB_MODE: ${requested_mode} (expected auto, docker, or postgres)" >&2
      exit 1
      ;;
  esac
}

smoke_jdbc_to_postgres_url() {
  local raw_url="$1"

  smoke_require_command python3

  python3 - "${raw_url}" <<'PY'
import sys
from urllib.parse import parse_qsl, quote, urlencode, urlparse

raw = sys.argv[1].strip()
if raw.startswith("jdbc:"):
    raw = raw[5:]

parsed = urlparse(raw)
if parsed.scheme not in {"postgres", "postgresql"}:
    raise SystemExit(f"unsupported postgres url scheme: {parsed.scheme}")

params = []
search_path = None
for key, value in parse_qsl(parsed.query, keep_blank_values=True):
    if key == "currentSchema" and value and search_path is None:
        search_path = value
        continue
    params.append((key, value))

if search_path:
    params.append(("options", f"-csearch_path={search_path}"))

query = urlencode(params, doseq=True, quote_via=quote)
url = f"postgresql://{parsed.netloc}{parsed.path}"
if query:
    url = f"{url}?{query}"
print(url)
PY
}

smoke_resolve_db_connection_url() {
  local env_file
  local db_direct_url="${DB_DIRECT_URL:-}"
  local db_url="${DB_URL:-}"

  env_file="$(smoke_resolve_env_file "${ENV_FILE:-$(smoke_default_root_dir)/.env}")"

  if [[ -z "${db_direct_url}" ]]; then
    db_direct_url="$(smoke_load_env_value "${env_file}" DB_DIRECT_URL)"
  fi
  if [[ -z "${db_url}" ]]; then
    db_url="$(smoke_load_env_value "${env_file}" DB_URL)"
  fi

  if [[ -n "${db_direct_url}" ]]; then
    smoke_jdbc_to_postgres_url "${db_direct_url}"
    return 0
  fi

  if [[ -n "${db_url}" ]]; then
    smoke_jdbc_to_postgres_url "${db_url}"
    return 0
  fi

  return 1
}

smoke_run_psql_direct() {
  local db_query_password="$1"
  shift

  if command -v psql >/dev/null 2>&1; then
    PGPASSWORD="${db_query_password}" psql "$@"
    return 0
  fi

  smoke_require_command docker

  docker run --rm --network host \
    -e PGPASSWORD="${db_query_password}" \
    "${SMOKE_PSQL_DOCKER_IMAGE:-postgres:16}" \
    psql "$@"
}

smoke_db_query() {
  local sql="$1"
  local db_mode
  local db_container_name="${DB_CONTAINER_NAME:-${POSTGRES_CONTAINER_NAME:-youth-welfare-db}}"
  local db_name="${DB_NAME:-youth_welfare}"
  local env_file
  local db_query_username="${DB_QUERY_USERNAME:-}"
  local db_query_password="${DB_QUERY_PASSWORD:-}"
  local db_connection_url=""

  env_file="$(smoke_resolve_env_file "${ENV_FILE:-$(smoke_default_root_dir)/.env}")"

  if [[ -z "${db_query_username}" ]]; then
    db_query_username="$(smoke_load_env_value "${env_file}" DB_QUERY_USERNAME)"
  fi
  if [[ -z "${db_query_username}" ]]; then
    db_query_username="$(smoke_load_env_value "${env_file}" DB_MIGRATION_USERNAME)"
  fi
  if [[ -z "${db_query_username}" ]]; then
    db_query_username="$(smoke_load_env_value "${env_file}" DB_USERNAME)"
  fi
  if [[ -z "${db_query_username}" ]]; then
    db_query_username="$(smoke_load_env_value "${env_file}" DB_ADMIN_RO_USERNAME)"
  fi
  if [[ -z "${db_query_username}" ]]; then
    db_query_username="migration_admin"
  fi

  if [[ -z "${db_query_password}" ]]; then
    db_query_password="$(smoke_load_env_value "${env_file}" DB_QUERY_PASSWORD)"
  fi
  if [[ -z "${db_query_password}" ]]; then
    db_query_password="$(smoke_load_env_value "${env_file}" DB_MIGRATION_PASSWORD)"
  fi
  if [[ -z "${db_query_password}" ]]; then
    db_query_password="$(smoke_load_env_value "${env_file}" DB_PASSWORD)"
  fi
  if [[ -z "${db_query_password}" ]]; then
    db_query_password="$(smoke_load_env_value "${env_file}" DB_ADMIN_RO_PASSWORD)"
  fi
  if [[ -z "${db_query_password}" ]]; then
    db_query_password="welfare1234!"
  fi

  db_mode="$(smoke_resolve_db_mode)"

  if [[ "${db_mode}" == "docker" ]]; then
    smoke_require_command docker
    docker exec -e PGPASSWORD="${db_query_password}" "${db_container_name}" \
      psql -v ON_ERROR_STOP=1 -U "${db_query_username}" -d "${db_name}" -At -F $'\t' -c "${sql}"
    return 0
  fi

  db_connection_url="$(smoke_resolve_db_connection_url)" || {
    echo "DB_DIRECT_URL or DB_URL is required for SMOKE_DB_MODE=postgres" >&2
    exit 1
  }

  smoke_run_psql_direct "${db_query_password}" \
    --set ON_ERROR_STOP=1 --username "${db_query_username}" --dbname "${db_connection_url}" -At -F $'\t' -c "${sql}"
}

smoke_db_apply_file() {
  local file_path="$1"
  local db_mode
  local db_container_name="${DB_CONTAINER_NAME:-${POSTGRES_CONTAINER_NAME:-youth-welfare-db}}"
  local db_name="${DB_NAME:-youth_welfare}"
  local env_file
  local db_query_username="${DB_QUERY_USERNAME:-}"
  local db_query_password="${DB_QUERY_PASSWORD:-}"
  local db_connection_url=""

  env_file="$(smoke_resolve_env_file "${ENV_FILE:-$(smoke_default_root_dir)/.env}")"

  if [[ -z "${db_query_username}" ]]; then
    db_query_username="$(smoke_load_env_value "${env_file}" DB_QUERY_USERNAME)"
  fi
  if [[ -z "${db_query_username}" ]]; then
    db_query_username="$(smoke_load_env_value "${env_file}" DB_MIGRATION_USERNAME)"
  fi
  if [[ -z "${db_query_username}" ]]; then
    db_query_username="$(smoke_load_env_value "${env_file}" DB_USERNAME)"
  fi
  if [[ -z "${db_query_username}" ]]; then
    db_query_username="$(smoke_load_env_value "${env_file}" DB_ADMIN_RO_USERNAME)"
  fi
  if [[ -z "${db_query_username}" ]]; then
    db_query_username="migration_admin"
  fi

  if [[ -z "${db_query_password}" ]]; then
    db_query_password="$(smoke_load_env_value "${env_file}" DB_QUERY_PASSWORD)"
  fi
  if [[ -z "${db_query_password}" ]]; then
    db_query_password="$(smoke_load_env_value "${env_file}" DB_MIGRATION_PASSWORD)"
  fi
  if [[ -z "${db_query_password}" ]]; then
    db_query_password="$(smoke_load_env_value "${env_file}" DB_PASSWORD)"
  fi
  if [[ -z "${db_query_password}" ]]; then
    db_query_password="$(smoke_load_env_value "${env_file}" DB_ADMIN_RO_PASSWORD)"
  fi
  if [[ -z "${db_query_password}" ]]; then
    db_query_password="welfare1234!"
  fi

  db_mode="$(smoke_resolve_db_mode)"

  if [[ "${db_mode}" == "docker" ]]; then
    smoke_require_command docker
    docker exec -i -e PGPASSWORD="${db_query_password}" "${db_container_name}" \
      psql -v ON_ERROR_STOP=1 -U "${db_query_username}" -d "${db_name}" -f - < "${file_path}"
    return 0
  fi

  db_connection_url="$(smoke_resolve_db_connection_url)" || {
    echo "DB_DIRECT_URL or DB_URL is required for SMOKE_DB_MODE=postgres" >&2
    exit 1
  }

  smoke_run_psql_direct "${db_query_password}" \
    --set ON_ERROR_STOP=1 --username "${db_query_username}" --dbname "${db_connection_url}" -f "${file_path}"
}

smoke_recommendation_historical_target_family_csv() {
  printf '%s' "2736,3257,3281,3575,3714"
}

smoke_resolve_recommendation_local_target_family_ids_csv() {
  local user_key="$1"
  local limit="${2:-5}"
  local exclude_top_saved_limit="${3:-10}"
  local allow_historical_fallback="${ALLOW_RECOMMENDATION_HISTORICAL_TARGET_FAMILY_FALLBACK:-false}"
  local quoted_user_key
  local result=""

  quoted_user_key="$(smoke_sql_quote "${user_key}")"

  result="$(smoke_db_query "
    with user_ctx as (
      select age, income_level, region_code, sido
      from user_profiles
      where user_key = ${quoted_user_key}
    ),
    latest_batch as (
      select max(recommended_at) as recommended_at
      from user_recommendations
      where user_key = ${quoted_user_key}
    ),
    top_saved as (
      select ur.service_id
      from user_recommendations ur
      join latest_batch lb
        on lb.recommended_at = ur.recommended_at
      where ur.user_key = ${quoted_user_key}
      order by ur.final_score desc, ur.id asc
      limit ${exclude_top_saved_limit}
    ),
    ranked_candidates as (
      select distinct
             ws.id as service_id,
             case when coalesce(ws.title, '') like '%청년%' then 0 else 1 end as title_priority,
             case
               when regexp_replace(coalesce(ws.life_stage, ''), '\\s+', '', 'g') = '청년' then 0
               when exists (
                    select 1
                    from service_tags st_life
                    where st_life.service_id = ws.id
                      and st_life.tag_type = 'LIFE_STAGE'
                      and st_life.tag_value = '청년'
               ) then 1
               else 2
             end as youth_focus_priority,
             case when exists (
                  select 1
                  from service_tags st_target
                  where st_target.service_id = ws.id
                    and st_target.tag_type = 'TARGET_GROUP'
                    and st_target.tag_value not like '%청년%'
             ) then 1 else 0 end as special_target_priority,
             case when uc.region_code is not null and sr.region_code = uc.region_code then 0 else 1 end as region_priority,
             case when exists (
                  select 1
                  from service_tags st_theme
                  where st_theme.service_id = ws.id
                    and st_theme.tag_type = 'INTEREST_THEME'
                    and st_theme.tag_value in ('주거', '생활지원', '교육', '일자리', '서민금융')
             ) then 0 else 1 end as theme_priority,
             case when exists (
                  select 1
                  from service_tags st_keyword
                  where st_keyword.service_id = ws.id
                    and st_keyword.tag_type = 'KEYWORD'
                    and st_keyword.tag_value in (
                        '주거지원', '월세보증금', '주거급여지원',
                        '금융지원', '생활안정자금', '융자',
                        '바우처', '돌봄서비스', '교육지원'
                    )
             ) then 0 else 1 end as keyword_priority,
             coalesce(ws.last_modified_at, ws.registered_at, ws.created_at) as sort_ts
      from user_ctx uc
      join welfare_services ws
        on ws.source_type = 'BOKJIRO_LOCAL'
       and ws.status in ('ACTIVE', 'UPCOMING')
       and (ws.min_age is null or ws.min_age <= uc.age)
       and (ws.max_age is null or ws.max_age >= uc.age)
       and (
            (ws.min_income is null and ws.max_income is null)
            or (ws.min_income = 0 and ws.max_income = 0)
            or (
                (ws.min_income is null or ws.min_income <= uc.income_level)
                and (ws.max_income is null or ws.max_income >= uc.income_level)
            )
       )
       and ws.search_youth_relevant is true
       and ws.unified_category in ('주거', '금융·생활지원', '교육·직업훈련', '일자리', '가족·돌봄', '문화·여가')
      join service_regions sr
        on sr.service_id = ws.id
      where (
            (uc.region_code is not null and sr.region_code = uc.region_code)
            or (uc.sido is not null and sr.sido_name = uc.sido)
          )
        and not exists (
            select 1
            from top_saved ts
            where ts.service_id = ws.id
        )
        and (
            coalesce(ws.title, '') like '%청년%'
            or coalesce(ws.life_stage, '') like '%청년%'
            or exists (
                select 1
                from service_tags st
                where st.service_id = ws.id
                  and (
                      (st.tag_type = 'LIFE_STAGE' and st.tag_value like '%청년%')
                      or (st.tag_type = 'INTEREST_THEME' and st.tag_value in ('주거', '생활지원', '교육', '일자리', '서민금융'))
                      or (st.tag_type = 'KEYWORD' and st.tag_value in (
                          '주거지원', '월세보증금', '주거급여지원',
                          '금융지원', '생활안정자금', '융자',
                          '바우처', '돌봄서비스', '교육지원'
                      ))
                  )
            )
        )
    )
    select coalesce(string_agg(service_id::text, ',' order by title_priority, youth_focus_priority, special_target_priority, region_priority, theme_priority, keyword_priority, sort_ts desc, service_id desc), '')
    from (
      select service_id, title_priority, youth_focus_priority, special_target_priority, region_priority, theme_priority, keyword_priority, sort_ts
      from ranked_candidates
      order by title_priority, youth_focus_priority, special_target_priority, region_priority, theme_priority, keyword_priority, sort_ts desc, service_id desc
      limit ${limit}
    ) picked;
  ")"

  if [[ -z "${result}" ]]; then
    result="$(smoke_db_query "
      with user_ctx as (
        select age, income_level, region_code, sido
        from user_profiles
        where user_key = ${quoted_user_key}
      ),
      ranked_candidates as (
        select distinct
               ws.id as service_id,
               case when coalesce(ws.title, '') like '%청년%' then 0 else 1 end as title_priority,
               case when uc.region_code is not null and sr.region_code = uc.region_code then 0 else 1 end as region_priority,
               coalesce(ws.last_modified_at, ws.registered_at, ws.created_at) as sort_ts
        from user_ctx uc
        join welfare_services ws
          on ws.source_type = 'BOKJIRO_LOCAL'
         and ws.status in ('ACTIVE', 'UPCOMING')
         and (ws.min_age is null or ws.min_age <= uc.age)
         and (ws.max_age is null or ws.max_age >= uc.age)
         and (
              (ws.min_income is null and ws.max_income is null)
              or (ws.min_income = 0 and ws.max_income = 0)
              or (
                  (ws.min_income is null or ws.min_income <= uc.income_level)
                  and (ws.max_income is null or ws.max_income >= uc.income_level)
              )
         )
         and ws.search_youth_relevant is true
         and ws.unified_category <> '기타'
        join service_regions sr
          on sr.service_id = ws.id
        where (
              (uc.region_code is not null and sr.region_code = uc.region_code)
              or (uc.sido is not null and sr.sido_name = uc.sido)
            )
          and (
              coalesce(ws.title, '') like '%청년%'
              or coalesce(ws.life_stage, '') like '%청년%'
              or exists (
                  select 1
                  from service_tags st
                  where st.service_id = ws.id
                    and st.tag_type = 'LIFE_STAGE'
                    and st.tag_value like '%청년%'
              )
          )
      )
      select coalesce(string_agg(service_id::text, ',' order by title_priority, region_priority, sort_ts desc, service_id desc), '')
      from (
        select service_id, title_priority, region_priority, sort_ts
        from ranked_candidates
        order by title_priority, region_priority, sort_ts desc, service_id desc
        limit ${limit}
      ) picked;
    ")"
  fi

  if [[ -z "${result}" ]]; then
    result="$(smoke_db_query "
      with user_ctx as (
        select age, income_level, region_code, sido
        from user_profiles
        where user_key = ${quoted_user_key}
      ),
      latest_batch as (
        select max(recommended_at) as recommended_at
        from user_recommendations
        where user_key = ${quoted_user_key}
      ),
      top_saved as (
        select ur.service_id
        from user_recommendations ur
        join latest_batch lb
          on lb.recommended_at = ur.recommended_at
        where ur.user_key = ${quoted_user_key}
        order by ur.final_score desc, ur.id asc
        limit ${exclude_top_saved_limit}
      ),
      ranked_candidates as (
        select distinct
               ws.id as service_id,
               case
                 when ws.source_type = 'GOV24' then 0
                 when ws.source_type = 'YOUTH' then 1
                 else 2
               end as source_priority,
               case when coalesce(ws.title, '') like '%청년%' then 0 else 1 end as title_priority,
               case
                 when coalesce(ws.unified_category, '') in ('주거', '금융·생활지원', '교육·직업훈련', '일자리') then 0
                 when coalesce(ws.unified_category, '') in ('가족·돌봄', '문화·여가') then 1
                 else 2
               end as category_priority,
               coalesce(ws.last_modified_at, ws.registered_at, ws.created_at) as sort_ts
        from user_ctx uc
        join welfare_services ws
          on ws.source_type in ('GOV24', 'YOUTH')
         and ws.status in ('ACTIVE', 'UPCOMING')
         and (ws.min_age is null or ws.min_age <= uc.age)
         and (ws.max_age is null or ws.max_age >= uc.age)
         and (
              (ws.min_income is null and ws.max_income is null)
              or (ws.min_income = 0 and ws.max_income = 0)
              or (
                  (ws.min_income is null or ws.min_income <= uc.income_level)
                  and (ws.max_income is null or ws.max_income >= uc.income_level)
              )
         )
         and ws.search_youth_relevant is true
         and coalesce(ws.unified_category, '') <> '기타'
        left join service_regions sr
          on sr.service_id = ws.id
        where not exists (
              select 1
              from top_saved ts
              where ts.service_id = ws.id
            )
          and (
              coalesce(ws.title, '') like '%청년%'
              or coalesce(ws.life_stage, '') like '%청년%'
              or exists (
                  select 1
                  from service_tags st
                  where st.service_id = ws.id
                    and st.tag_type = 'LIFE_STAGE'
                    and st.tag_value like '%청년%'
              )
          )
          and (
              (uc.region_code is not null and sr.region_code = uc.region_code)
              or (uc.sido is not null and sr.sido_name = uc.sido)
              or (uc.sido is not null and coalesce(ws.title, '') like '%' || replace(replace(replace(uc.sido, '광역시', ''), '특별시', ''), '특별자치시', '') || '%')
              or (uc.sido is not null and coalesce(ws.description, '') like '%' || uc.sido || '%')
              or (uc.sido is not null and coalesce(ws.description, '') like '%' || replace(replace(replace(uc.sido, '광역시', ''), '특별시', ''), '특별자치시', '') || '%')
          )
      )
      select coalesce(string_agg(service_id::text, ',' order by source_priority, category_priority, title_priority, sort_ts desc, service_id desc), '')
      from (
        select service_id, source_priority, category_priority, title_priority, sort_ts
        from ranked_candidates
        order by source_priority, category_priority, title_priority, sort_ts desc, service_id desc
        limit ${limit}
      ) picked;
    ")"
  fi

  if [[ -z "${result}" && "${allow_historical_fallback}" == "true" ]]; then
    result="$(smoke_recommendation_historical_target_family_csv)"
  fi

  printf '%s' "${result}"
}

smoke_redis_cli() {
  local redis_host="${REDIS_HOST:-}"
  local redis_port="${REDIS_PORT:-6379}"
  local redis_tls="${REDIS_TLS:-}"
  local redis_password="${REDIS_PASSWORD:-}"
  local redis_container_name="${REDIS_CONTAINER_NAME:-youth-welfare-redis}"
  local env_file
  local -a redis_args=()

  env_file="$(smoke_resolve_env_file "${ENV_FILE:-$(smoke_default_root_dir)/.env}")"
  if [[ -z "${redis_host}" ]]; then
    redis_host="$(smoke_load_env_value "${env_file}" REDIS_HOST)"
  fi
  if [[ "${redis_port}" == "6379" ]]; then
    redis_port="$(smoke_load_env_value "${env_file}" REDIS_PORT 6379)"
  fi
  if [[ -z "${redis_tls}" ]]; then
    redis_tls="$(smoke_load_env_value "${env_file}" REDIS_TLS false)"
  fi
  if [[ -z "${redis_password}" ]]; then
    redis_password="$(smoke_load_env_value "${env_file}" REDIS_PASSWORD)"
  fi

  if [[ "${redis_tls}" == "true" ]]; then
    redis_args+=(--tls)
  fi
  if [[ -n "${redis_password}" ]]; then
    redis_args+=(-a "${redis_password}" --no-auth-warning)
  fi

  if [[ -n "${redis_host}" && "${redis_host}" != "redis" && "${redis_host}" != "localhost" && "${redis_host}" != "127.0.0.1" ]]; then
    if command -v redis-cli >/dev/null 2>&1; then
      redis-cli "${redis_args[@]}" -h "${redis_host}" -p "${redis_port}" "$@"
      return
    fi

    smoke_require_command docker
    docker run --rm redis:7-alpine redis-cli "${redis_args[@]}" -h "${redis_host}" -p "${redis_port}" "$@"
    return
  fi

  smoke_require_command docker
  docker exec "${redis_container_name}" redis-cli "${redis_args[@]}" "$@"
}

smoke_seed_verified_email() {
  local raw_email="$1"
  local verified_ttl_seconds="${EMAIL_VERIFIED_TTL_SECONDS:-900}"
  local hash

  hash="$(smoke_sha256_hex "${raw_email}")"

  smoke_redis_cli SETEX "email-verify:verified:${hash}" "${verified_ttl_seconds}" 1 >/dev/null
}

smoke_clear_recommendation_refresh_rate_limit() {
  local user_key="$1"

  if [[ -z "${user_key}" ]]; then
    return 0
  fi

  smoke_redis_cli DEL \
    "recommend:rate-limit:refresh:personal:${user_key}" \
    "recommend:rate-limit:refresh:shared:${user_key}" >/dev/null
}

smoke_ensure_admin_account() {
  local app_base_url="$1"
  local admin_email="$2"
  local admin_password="$3"
  local bootstrap_mode="${SMOKE_ADMIN_BOOTSTRAP_MODE:-auto}"
  local db_mode
  local admin_email_hash
  local existing_auth_count
  local existing_user_count
  local bootstrap_email
  local bootstrap_dir
  local signup_response
  local signup_status
  local bootstrap_user_key
  local admin_stored_email
  local bootstrap_email_hash
  local admin_stored_email_sql
  local admin_email_hash_sql
  local bootstrap_email_hash_sql

  db_mode="$(smoke_resolve_db_mode)"
  case "${bootstrap_mode}" in
    auto)
      if [[ "${db_mode}" != "docker" ]]; then
        return 0
      fi
      ;;
    true)
      ;;
    false)
      return 0
      ;;
    *)
      echo "unsupported SMOKE_ADMIN_BOOTSTRAP_MODE: ${bootstrap_mode} (expected auto, true, or false)" >&2
      exit 1
      ;;
  esac

  admin_email_hash="$(smoke_sha256_hex "${admin_email}")"
  existing_auth_count="$(smoke_db_query "SELECT COUNT(*) FROM auth_users WHERE email_lookup_hash = $(smoke_sql_quote "${admin_email_hash}");")"
  if [[ "${existing_auth_count}" != "0" ]]; then
    return 0
  fi

  admin_stored_email="$(smoke_user_email_shadow_value "${admin_email}")"
  existing_user_count="$(
    smoke_db_query "SELECT COUNT(*) FROM users WHERE email IN ($(smoke_sql_quote "${admin_email}"), $(smoke_sql_quote "${admin_stored_email}"));"
  )"
  if [[ "${existing_user_count}" != "0" ]]; then
    echo "admin bootstrap refused: users.email already contains admin email shadow/plain value but auth_users does not" >&2
    exit 1
  fi

  bootstrap_email="$(smoke_build_email "admin.bootstrap.smoke")"
  bootstrap_dir="$(mktemp -d)"
  signup_response="${bootstrap_dir}/signup.json"

  smoke_seed_verified_email "${bootstrap_email}"
  signup_status="$(
    smoke_http_status POST "${app_base_url}/api/auth/signup" "${signup_response}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"email\": \"${bootstrap_email}\",
        \"password\": \"${admin_password}\",
        \"name\": \"관리자\",
        \"birthDate\": \"1998-01-10\",
      \"privacyNoticeConfirmed\": true,
      \"optionalProfileConsentAgreed\": true,
        \"sido\": \"서울특별시\",
        \"sgg\": \"중구\",
        \"incomeLevel\": 5,
        \"employmentStatus\": \"미취업\",
        \"householdType\": \"1인 가구\"
      }"
  )"
  if [[ "${signup_status}" != "200" ]]; then
    echo "admin bootstrap signup failed: expected 200, got ${signup_status}" >&2
    smoke_print_redacted_file_for_log "${signup_response}"
    rm -rf "${bootstrap_dir}"
    exit 1
  fi

  bootstrap_email_hash="$(smoke_sha256_hex "${bootstrap_email}")"
  bootstrap_email_hash_sql="$(smoke_sql_quote "${bootstrap_email_hash}")"
  bootstrap_user_key="$(smoke_db_query "SELECT user_key FROM auth_users WHERE email_lookup_hash = ${bootstrap_email_hash_sql} LIMIT 1;")"
  if [[ -z "${bootstrap_user_key}" ]]; then
    echo "admin bootstrap failed: auth user_key not found for bootstrap email lookup hash" >&2
    rm -rf "${bootstrap_dir}"
    exit 1
  fi

  admin_stored_email_sql="$(smoke_sql_quote "${admin_stored_email}")"
  admin_email_hash_sql="$(smoke_sql_quote "${admin_email_hash}")"
  smoke_db_query "UPDATE users SET email = ${admin_stored_email_sql} WHERE user_key = $(smoke_sql_quote "${bootstrap_user_key}");" >/dev/null
  smoke_db_query "UPDATE auth_users SET email_lookup_hash = ${admin_email_hash_sql} WHERE user_key = $(smoke_sql_quote "${bootstrap_user_key}");" >/dev/null

  rm -rf "${bootstrap_dir}"
}

smoke_ensure_policy_fixture() {
  local fixture_mode="${SMOKE_POLICY_FIXTURE_MODE:-auto}"
  local db_mode
  local searchable_service_count

  db_mode="$(smoke_resolve_db_mode)"
  case "${fixture_mode}" in
    auto)
      if [[ "${db_mode}" != "docker" ]]; then
        return 0
      fi
      ;;
    true)
      ;;
    false)
      return 0
      ;;
    *)
      echo "unsupported SMOKE_POLICY_FIXTURE_MODE: ${fixture_mode} (expected auto, true, or false)" >&2
      exit 1
      ;;
  esac

  searchable_service_count="$(smoke_db_query "SELECT COUNT(*) FROM welfare_services WHERE search_youth_relevant IS TRUE AND status IN ('ACTIVE', 'UPCOMING');")"
  if [[ "${searchable_service_count}" != "0" ]]; then
    return 0
  fi

  smoke_db_query "
    INSERT INTO welfare_services (
      source_type,
      source_id,
      title,
      description,
      support_content,
      category_main,
      category_sub,
      keyword,
      min_age,
      max_age,
      min_income,
      max_income,
      apply_start_date,
      apply_end_date,
      life_stage,
      apply_method_name,
      host_org,
      operating_org,
      detail_url,
      unified_category,
      is_youth_specific,
      search_youth_relevant,
      status,
      is_online_apply,
      api_view_count,
      view_count,
      registered_at,
      last_modified_at
    )
    VALUES (
      'YOUTH',
      'SMOKE-SEED-POLICY',
      '청년 취업 지원 점검 정책',
      'fresh smoke 검증용 최소 정책 데이터입니다.',
      '청년 구직 활동과 취업 준비를 지원합니다.',
      '일자리',
      '취업지원',
      '청년,취업,점검',
      18,
      34,
      0,
      10,
      CURRENT_DATE,
      CURRENT_DATE + 30,
      '청년',
      '온라인 신청',
      '청년정책점검',
      '청년정책점검',
      'https://example.com/smoke-policy',
      '일자리',
      TRUE,
      TRUE,
      'ACTIVE',
      TRUE,
      0,
      0,
      CURRENT_TIMESTAMP,
      CURRENT_TIMESTAMP
    )
    ON CONFLICT (source_type, source_id) DO UPDATE
    SET title = EXCLUDED.title,
        description = EXCLUDED.description,
        support_content = EXCLUDED.support_content,
        category_main = EXCLUDED.category_main,
        category_sub = EXCLUDED.category_sub,
        keyword = EXCLUDED.keyword,
        min_age = EXCLUDED.min_age,
        max_age = EXCLUDED.max_age,
        min_income = EXCLUDED.min_income,
        max_income = EXCLUDED.max_income,
        apply_start_date = EXCLUDED.apply_start_date,
        apply_end_date = EXCLUDED.apply_end_date,
        life_stage = EXCLUDED.life_stage,
        apply_method_name = EXCLUDED.apply_method_name,
        host_org = EXCLUDED.host_org,
        operating_org = EXCLUDED.operating_org,
        detail_url = EXCLUDED.detail_url,
        unified_category = EXCLUDED.unified_category,
        is_youth_specific = EXCLUDED.is_youth_specific,
        search_youth_relevant = EXCLUDED.search_youth_relevant,
        status = EXCLUDED.status,
        is_online_apply = EXCLUDED.is_online_apply,
        api_view_count = EXCLUDED.api_view_count,
        view_count = EXCLUDED.view_count,
        registered_at = EXCLUDED.registered_at,
        last_modified_at = EXCLUDED.last_modified_at;
  " >/dev/null
}

smoke_unique_suffix() {
  smoke_require_command python3

  python3 <<'PY'
import uuid

print(uuid.uuid4().hex[:14])
PY
}

smoke_build_email() {
  local email_prefix="$1"
  local email_domain="${SMOKE_EMAIL_DOMAIN:-example.com}"
  smoke_require_command python3

  python3 - "${email_prefix}" "$(smoke_unique_suffix)" "${email_domain}" <<'PY'
import re
import sys

prefix = sys.argv[1].strip().lower()
suffix = sys.argv[2].strip().lower()
domain = sys.argv[3].strip().lower()

safe_prefix = re.sub(r"[^a-z0-9]+", ".", prefix)
safe_prefix = re.sub(r"\.+", ".", safe_prefix).strip(".") or "smoke"
safe_prefix = safe_prefix[:12].rstrip(".") or "smoke"

safe_domain = re.sub(r"[^a-z0-9.-]+", "-", domain)
safe_domain = re.sub(r"\.{2,}", ".", safe_domain).strip(".-") or "example.com"

if "." not in safe_domain:
    safe_domain = f"{safe_domain}.com"

print(f"{safe_prefix}.{suffix}@{safe_domain}")
PY
}
