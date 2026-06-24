#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-/home/ubuntu/youth-welfare}"
OPS_ENV_FILE="${OPS_ENV_FILE:-${HOME:-/home/ubuntu}/.config/youth-welfare/ops.env}"
CALLER_ALERT_WEBHOOK_URL_SET="${ALERT_WEBHOOK_URL+x}"
CALLER_ALERT_WEBHOOK_URL="${ALERT_WEBHOOK_URL-}"

if [[ -r "${OPS_ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${OPS_ENV_FILE}"
fi
if [[ -n "${CALLER_ALERT_WEBHOOK_URL_SET}" ]]; then
  ALERT_WEBHOOK_URL="${CALLER_ALERT_WEBHOOK_URL}"
fi

ALERT_WEBHOOK_URL="${ALERT_WEBHOOK_URL:-}"
LOG_ALERT_RUN_BASELINES="${LOG_ALERT_RUN_BASELINES:-true}"
LOG_ALERT_APP_SINCE="${LOG_ALERT_APP_SINCE:-10m}"
LOG_ALERT_NGINX_TAIL_LINES="${LOG_ALERT_NGINX_TAIL_LINES:-2000}"
LOG_ALERT_NOTIFY_OK="${LOG_ALERT_NOTIFY_OK:-false}"
LOG_ALERT_STATE_DIR="${LOG_ALERT_STATE_DIR:-${HOME:-/home/ubuntu}/.local/state/youth-welfare/ops}"
LOG_ALERT_LOG_DIR="${LOG_ALERT_LOG_DIR:-/var/log/youth-welfare/ops}"
LOG_ALERT_LAST_STATUS_FILE="${LOG_ALERT_STATE_DIR}/log-alert.last-status"
LOG_ALERT_LOG_FILE="${LOG_ALERT_LOG_DIR}/log-alert.log"

mkdir -p "${LOG_ALERT_STATE_DIR}" "${LOG_ALERT_LOG_DIR}"

log_line() {
  printf '[%s] %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*" >> "${LOG_ALERT_LOG_FILE}"
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

cd "${ROOT_DIR}"

if [[ "${LOG_ALERT_RUN_BASELINES}" == "true" ]]; then
  APP_LOG_SINCE="${LOG_ALERT_APP_SINCE}" bash deploy/performance/run-local-app-log-observability-baseline.sh >/tmp/youth-welfare-app-log-alert-baseline.out
  NGINX_LOG_TAIL_LINES="${LOG_ALERT_NGINX_TAIL_LINES}" bash deploy/performance/run-local-nginx-log-observability-baseline.sh >/tmp/youth-welfare-nginx-log-alert-baseline.out
fi

set +e
ALERT_OUTPUT="$(bash deploy/performance/evaluate-log-alert-thresholds.sh 2>&1)"
ALERT_EXIT=$?
set -e

printf '%s\n' "${ALERT_OUTPUT}"

STATUS="$(printf '%s\n' "${ALERT_OUTPUT}" | awk -F= '/^LOG_ALERT_STATUS=/{print $2; exit}')"
STATUS="${STATUS:-unknown}"
PREVIOUS_STATUS="$(cat "${LOG_ALERT_LAST_STATUS_FILE}" 2>/dev/null || true)"
printf '%s' "${STATUS}" > "${LOG_ALERT_LAST_STATUS_FILE}"
log_line "status=${STATUS} previous=${PREVIOUS_STATUS:-none} exit=${ALERT_EXIT}"

SHOULD_NOTIFY=false
if [[ "${STATUS}" == "warning" || "${STATUS}" == "critical" || "${STATUS}" == "unknown" ]]; then
  SHOULD_NOTIFY=true
elif [[ "${LOG_ALERT_NOTIFY_OK}" == "true" && "${STATUS}" != "${PREVIOUS_STATUS}" ]]; then
  SHOULD_NOTIFY=true
fi

if [[ "${SHOULD_NOTIFY}" == "true" ]]; then
  MESSAGE="$(
    {
      echo "youth-welfare log alert status=${STATUS}"
      printf '%s\n' "${ALERT_OUTPUT}" | sed -n '1,12p'
    } | sed ':a;N;$!ba;s/\n/\\n/g'
  )"
  send_alert_webhook "${MESSAGE}"
fi

exit "${ALERT_EXIT}"
