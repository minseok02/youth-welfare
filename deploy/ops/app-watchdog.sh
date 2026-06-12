#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-/home/ubuntu/youth-welfare}"
OPS_ENV_FILE="${OPS_ENV_FILE:-${HOME:-/home/ubuntu}/.config/youth-welfare/ops.env}"
CALLER_HEALTHCHECKS_PING_URL_SET="${HEALTHCHECKS_PING_URL+x}"
CALLER_HEALTHCHECKS_PING_URL="${HEALTHCHECKS_PING_URL-}"
CALLER_ALERT_WEBHOOK_URL_SET="${ALERT_WEBHOOK_URL+x}"
CALLER_ALERT_WEBHOOK_URL="${ALERT_WEBHOOK_URL-}"
if [[ -r "${OPS_ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${OPS_ENV_FILE}"
fi
if [[ -n "${CALLER_HEALTHCHECKS_PING_URL_SET}" ]]; then
  HEALTHCHECKS_PING_URL="${CALLER_HEALTHCHECKS_PING_URL}"
fi
if [[ -n "${CALLER_ALERT_WEBHOOK_URL_SET}" ]]; then
  ALERT_WEBHOOK_URL="${CALLER_ALERT_WEBHOOK_URL}"
fi
ENV_FILE="${ENV_FILE:-.env.production}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
COMPOSE_SERVICE="${COMPOSE_SERVICE:-app}"
APP_HEALTH_URL="${APP_HEALTH_URL:-http://127.0.0.1:8082/actuator/health}"
STATE_DIR="${STATE_DIR:-${HOME:-/home/ubuntu}/.local/state/youth-welfare/ops}"
LOG_DIR="${LOG_DIR:-/var/log/youth-welfare/ops}"
FAIL_THRESHOLD="${FAIL_THRESHOLD:-2}"
MIN_RESTART_INTERVAL_SECONDS="${MIN_RESTART_INTERVAL_SECONDS:-600}"
RESTART_TIMEOUT_SECONDS="${RESTART_TIMEOUT_SECONDS:-120}"
HEALTHCHECKS_PING_URL="${HEALTHCHECKS_PING_URL:-}"
ALERT_WEBHOOK_URL="${ALERT_WEBHOOK_URL:-}"

mkdir -p "${STATE_DIR}" "${LOG_DIR}"

FAIL_COUNT_FILE="${STATE_DIR}/app-watchdog.fail-count"
LAST_RESTART_FILE="${STATE_DIR}/app-watchdog.last-restart"
LOG_FILE="${LOG_DIR}/app-watchdog.log"

now_epoch() {
  date +%s
}

log_line() {
  printf '[%s] %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*" >> "${LOG_FILE}"
}

read_number_file() {
  local path="$1"
  local default_value="${2:-0}"
  if [[ -r "${path}" ]]; then
    tr -dc '0-9' < "${path}" | head -c 20
  else
    printf '%s' "${default_value}"
  fi
}

send_healthchecks_ping() {
  local suffix="${1:-}"
  [[ -n "${HEALTHCHECKS_PING_URL}" ]] || return 0
  curl -fsS --max-time 5 "${HEALTHCHECKS_PING_URL}${suffix}" >/dev/null 2>&1 || true
}

send_alert_webhook() {
  local message="$1"
  [[ -n "${ALERT_WEBHOOK_URL}" ]] || return 0
  python3 - "${ALERT_WEBHOOK_URL}" "${message}" <<'PY' >/dev/null 2>&1 || true
import json
import sys
import urllib.request

url, message = sys.argv[1:3]
body = json.dumps({"text": message, "content": message}, ensure_ascii=False).encode("utf-8")
request = urllib.request.Request(url, data=body, headers={"Content-Type": "application/json"}, method="POST")
urllib.request.urlopen(request, timeout=5).read()
PY
}

health_ok() {
  local response
  response="$(curl -fsS --max-time 5 "${APP_HEALTH_URL}" 2>/dev/null || true)"
  printf '%s' "${response}" | python3 -c 'import json, sys; raise SystemExit(0 if json.load(sys.stdin).get("status") == "UP" else 1)' 2>/dev/null
}

if health_ok; then
  printf '0' > "${FAIL_COUNT_FILE}"
  send_healthchecks_ping ""
  log_line "health=UP"
  exit 0
fi

fail_count="$(read_number_file "${FAIL_COUNT_FILE}" 0)"
fail_count="${fail_count:-0}"
fail_count=$((fail_count + 1))
printf '%s' "${fail_count}" > "${FAIL_COUNT_FILE}"
log_line "health=DOWN fail_count=${fail_count}"
send_healthchecks_ping "/fail"

if (( fail_count < FAIL_THRESHOLD )); then
  exit 0
fi

last_restart="$(read_number_file "${LAST_RESTART_FILE}" 0)"
last_restart="${last_restart:-0}"
now="$(now_epoch)"
if (( now - last_restart < MIN_RESTART_INTERVAL_SECONDS )); then
  log_line "restart=skipped reason=min_interval last_restart=${last_restart}"
  exit 0
fi

log_line "restart=begin service=${COMPOSE_SERVICE}"
send_alert_webhook "youth-welfare app health failed ${fail_count} times; restarting ${COMPOSE_SERVICE} container"

cd "${ROOT_DIR}"
if timeout "${RESTART_TIMEOUT_SECONDS}" docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" restart "${COMPOSE_SERVICE}" >> "${LOG_FILE}" 2>&1; then
  printf '%s' "${now}" > "${LAST_RESTART_FILE}"
  log_line "restart=done service=${COMPOSE_SERVICE}"
else
  log_line "restart=failed service=${COMPOSE_SERVICE}"
  send_alert_webhook "youth-welfare app restart failed; manual check required"
  exit 1
fi
