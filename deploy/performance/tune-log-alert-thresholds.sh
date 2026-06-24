#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

TUNE_WINDOW="${LOG_ALERT_TUNE_WINDOW:-24h}"
TUNE_ROOT="${LOG_ALERT_TUNE_ROOT:-${ROOT_DIR}/tmp/performance/log-alert-threshold-tuning}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${TUNE_ROOT}/${RUN_TS_UTC}}"
APP_ARTIFACT_DIR="${ARTIFACT_DIR}/app-log-observability"
NGINX_ARTIFACT_DIR="${ARTIFACT_DIR}/nginx-log-observability"
SUMMARY_TXT="${ARTIFACT_DIR}/log-alert-threshold-tuning-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/log-alert-threshold-tuning-summary.json"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
LATEST_DIR="${TUNE_ROOT}/latest"
LATEST_SUMMARY_TXT="${TUNE_ROOT}/latest-log-alert-threshold-tuning-summary.txt"
LATEST_SUMMARY_JSON="${TUNE_ROOT}/latest-log-alert-threshold-tuning-summary.json"
EXCLUDED_API_P95_PATHS="${LOG_ALERT_EXCLUDED_API_P95_PATHS:-POST /api/recommendations/refresh}"
RUN_BASELINES="${LOG_ALERT_TUNE_RUN_BASELINES:-true}"
NGINX_LOG_TAIL_LINES="${NGINX_LOG_TUNE_TAIL_LINES:-50000}"

mkdir -p "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"

if [[ "${RUN_BASELINES}" == "true" ]]; then
  APP_LOG_SINCE="${TUNE_WINDOW}" ARTIFACT_DIR="${APP_ARTIFACT_DIR}" \
    bash "${ROOT_DIR}/deploy/performance/run-local-app-log-observability-baseline.sh" \
    > "${ARTIFACT_DIR}/app-log-observability.out"
  NGINX_LOG_TAIL_LINES="${NGINX_LOG_TAIL_LINES}" ARTIFACT_DIR="${NGINX_ARTIFACT_DIR}" \
    bash "${ROOT_DIR}/deploy/performance/run-local-nginx-log-observability-baseline.sh" \
    > "${ARTIFACT_DIR}/nginx-log-observability.out"
fi

APP_JSON="${APP_ARTIFACT_DIR}/app-log-observability-summary.json"
NGINX_JSON="${NGINX_ARTIFACT_DIR}/nginx-log-observability-summary.json"
if [[ "${RUN_BASELINES}" != "true" ]]; then
  APP_JSON="${APP_JSON_OVERRIDE:-${ROOT_DIR}/tmp/performance/app-log-observability/latest-app-log-observability-summary.json}"
  NGINX_JSON="${NGINX_JSON_OVERRIDE:-${ROOT_DIR}/tmp/performance/nginx-log-observability/latest-nginx-log-observability-summary.json}"
fi

python3 - \
  "${APP_JSON}" \
  "${NGINX_JSON}" \
  "${SUMMARY_TXT}" \
  "${SUMMARY_JSON}" \
  "${CONTEXT_TXT}" \
  "${TUNE_WINDOW}" \
  "${EXCLUDED_API_P95_PATHS}" <<'PY'
import json
import math
import sys
from pathlib import Path

app_path, nginx_path, summary_txt, summary_json, context_path = map(Path, sys.argv[1:6])
window = sys.argv[6]
excluded_paths = {item.strip() for item in sys.argv[7].split(",") if item.strip()}

def read_json(path):
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))

def pct(values, percentile):
    if not values:
        return None
    values = sorted(values)
    k = (len(values) - 1) * percentile
    f = int(k)
    c = min(f + 1, len(values) - 1)
    if f == c:
        return values[f]
    return values[f] + (values[c] - values[f]) * (k - f)

def ceil_to(value, step):
    return int(math.ceil(value / step) * step)

context = {}
if context_path.exists():
    for raw in context_path.read_text(encoding="utf-8").splitlines():
        if "=" in raw:
            key, value = raw.split("=", 1)
            context[key] = value

app = read_json(app_path)
nginx = read_json(nginx_path)

interactive_path_p95s = []
excluded_latency = []
for path, summary in (app.get("api_duration_by_path") or {}).items():
    count = int(summary.get("count") or 0)
    p95 = summary.get("p95_ms")
    if count <= 0 or p95 is None:
        continue
    if path in excluded_paths:
        excluded_latency.append({"path": path, "count": count, "p95_ms": float(p95)})
        continue
    interactive_path_p95s.extend([float(p95)] * count)

interactive_p95 = pct(interactive_path_p95s, 0.95)
nginx_request_p95 = ((nginx.get("request_time_summary") or {}).get("p95_seconds"))
nginx_upstream_p95 = ((nginx.get("upstream_time_summary") or {}).get("p95_seconds"))

app_status = app.get("api_status_counts") or {}
nginx_status = nginx.get("status_counts") or {}
app_5xx = sum(int(count) for status, count in app_status.items() if str(status).startswith("5"))
nginx_5xx = sum(int(count) for status, count in nginx_status.items() if str(status).startswith("5"))
error_counts = {str(key): int(value) for key, value in (app.get("api_error_counts") or {}).items()}

warn_api = 1500
crit_api = 3000
if interactive_p95 is not None:
    warn_api = max(1500, ceil_to(interactive_p95 * 3, 100))
    crit_api = max(3000, ceil_to(interactive_p95 * 6, 100))

warn_nginx = 1.5
crit_nginx = 3.0
if nginx_request_p95 is not None:
    warn_nginx = max(1.5, math.ceil(float(nginx_request_p95) * 3 * 10) / 10)
    crit_nginx = max(3.0, math.ceil(float(nginx_request_p95) * 6 * 10) / 10)

recommendations = {
    "LOG_ALERT_WARN_API_P95_MS": warn_api,
    "LOG_ALERT_CRIT_API_P95_MS": crit_api,
    "LOG_ALERT_WARN_NGINX_P95_SECONDS": warn_nginx,
    "LOG_ALERT_CRIT_NGINX_P95_SECONDS": crit_nginx,
    "LOG_ALERT_WARN_5XX": 1,
    "LOG_ALERT_CRIT_5XX": max(5, app_5xx + nginx_5xx + 5),
    "LOG_ALERT_CRIT_ERROR_CODE_REPEAT": 10,
    "LOG_ALERT_EXCLUDED_API_P95_PATHS": ",".join(sorted(excluded_paths)),
}

notes = []
if error_counts:
    notes.append("errorCode 반복은 자동 완화하지 않습니다. 반복 원인을 먼저 확인하세요.")
if "P003" in error_counts:
    notes.append("P003는 rate-limit 신호입니다. smoke/수동 검증 트래픽이면 artifact window를 분리하세요.")
if excluded_latency:
    notes.append("batch/refresh endpoint latency는 interactive p95 판정에서 제외하고 별도 관찰합니다.")

payload = {
    "context": context,
    "window": window,
    "app_json": str(app_path),
    "nginx_json": str(nginx_path),
    "observed": {
        "interactive_api_p95_ms": interactive_p95,
        "nginx_request_p95_seconds": nginx_request_p95,
        "nginx_upstream_p95_seconds": nginx_upstream_p95,
        "app_5xx": app_5xx,
        "nginx_5xx": nginx_5xx,
        "api_error_counts": error_counts,
        "excluded_latency": excluded_latency,
    },
    "recommended_env": recommendations,
    "notes": notes,
}
summary_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

lines = ["log_alert_threshold_tuning=passed"]
lines.append(f"window={window}")
if interactive_p95 is not None:
    lines.append(f"observed_interactive_api_p95_ms={interactive_p95:.0f}")
if nginx_request_p95 is not None:
    lines.append(f"observed_nginx_request_p95_seconds={float(nginx_request_p95):.3f}")
if nginx_upstream_p95 is not None:
    lines.append(f"observed_nginx_upstream_p95_seconds={float(nginx_upstream_p95):.3f}")
lines.append(f"observed_app_5xx={app_5xx}")
lines.append(f"observed_nginx_5xx={nginx_5xx}")
for code, count in sorted(error_counts.items()):
    lines.append(f"observed_api_error_code_{code}={count}")
for key, value in recommendations.items():
    lines.append(f"recommended_{key}={value}")
for item in excluded_latency:
    lines.append(f"excluded_latency path={item['path']} count={item['count']} p95_ms={item['p95_ms']:.0f}")
for note in notes:
    lines.append(f"note={note}")
summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")
PY

perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
