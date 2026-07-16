#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"
APP_JSON="${APP_JSON:-${ROOT_DIR}/tmp/performance/app-log-observability/latest-app-log-observability-summary.json}"
NGINX_JSON="${NGINX_JSON:-${ROOT_DIR}/tmp/performance/nginx-log-observability/latest-nginx-log-observability-summary.json}"
WARN_5XX="${LOG_ALERT_WARN_5XX:-1}"
CRIT_5XX="${LOG_ALERT_CRIT_5XX:-5}"
CRIT_ERROR_CODE_REPEAT="${LOG_ALERT_CRIT_ERROR_CODE_REPEAT:-10}"
REPEAT_ERROR_CODE_EXCLUDES="${LOG_ALERT_REPEAT_ERROR_CODE_EXCLUDES:-A006}"
WARN_API_P95_MS="${LOG_ALERT_WARN_API_P95_MS:-1500}"
CRIT_API_P95_MS="${LOG_ALERT_CRIT_API_P95_MS:-3000}"
MIN_API_P95_COUNT="${LOG_ALERT_MIN_API_P95_COUNT:-3}"
EXCLUDED_API_P95_PATHS="${LOG_ALERT_EXCLUDED_API_P95_PATHS:-POST /api/recommendations/refresh}"
EXCLUDED_API_P95_PREFIXES="${LOG_ALERT_EXCLUDED_API_P95_PREFIXES:-GET /api/admin/,POST /api/admin/}"
WARN_NGINX_P95_SECONDS="${LOG_ALERT_WARN_NGINX_P95_SECONDS:-1.5}"
CRIT_NGINX_P95_SECONDS="${LOG_ALERT_CRIT_NGINX_P95_SECONDS:-3.0}"
WARN_RAW_ERRORS="${LOG_ALERT_WARN_RAW_ERRORS:-1}"

python3 - \
  "${APP_JSON}" \
  "${NGINX_JSON}" \
  "${WARN_5XX}" \
  "${CRIT_5XX}" \
  "${CRIT_ERROR_CODE_REPEAT}" \
  "${REPEAT_ERROR_CODE_EXCLUDES}" \
  "${WARN_API_P95_MS}" \
  "${CRIT_API_P95_MS}" \
  "${MIN_API_P95_COUNT}" \
  "${EXCLUDED_API_P95_PATHS}" \
  "${EXCLUDED_API_P95_PREFIXES}" \
  "${WARN_NGINX_P95_SECONDS}" \
  "${CRIT_NGINX_P95_SECONDS}" \
  "${WARN_RAW_ERRORS}" <<'PY' | smoke_redact_stream_for_log
import json
import sys
from pathlib import Path

app_path = Path(sys.argv[1])
nginx_path = Path(sys.argv[2])
warn_5xx = int(sys.argv[3])
crit_5xx = int(sys.argv[4])
crit_error_code_repeat = int(sys.argv[5])
repeat_error_code_excludes = {
    item.strip()
    for item in sys.argv[6].split(",")
    if item.strip()
}
warn_api_p95_ms = float(sys.argv[7])
crit_api_p95_ms = float(sys.argv[8])
min_api_p95_count = int(sys.argv[9])
excluded_api_p95_paths = {
    item.strip()
    for item in sys.argv[10].split(",")
    if item.strip()
}
excluded_api_p95_prefixes = {
    item.strip()
    for item in sys.argv[11].split(",")
    if item.strip()
}
warn_nginx_p95_seconds = float(sys.argv[12])
crit_nginx_p95_seconds = float(sys.argv[13])
warn_raw_errors = int(sys.argv[14])

critical = []
warning = []
observation = []

def load_json(path):
    if not path.exists():
        warning.append(f"missing artifact {path}")
        return {}
    return json.loads(path.read_text(encoding="utf-8"))

app = load_json(app_path)
nginx = load_json(nginx_path)

app_status = app.get("api_status_counts") or {}
app_5xx = sum(int(count) for status, count in app_status.items() if str(status).startswith("5"))
if app_5xx >= crit_5xx:
    critical.append(f"app api 5xx count {app_5xx} >= {crit_5xx}")
elif app_5xx >= warn_5xx:
    warning.append(f"app api 5xx count {app_5xx} >= {warn_5xx}")

for code, count in sorted((app.get("api_error_counts") or {}).items()):
    count = int(count)
    if code in repeat_error_code_excludes:
        observation.append(f"excluded errorCode {code} repeated {count}")
        continue
    if count >= crit_error_code_repeat:
        critical.append(f"api errorCode {code} repeated {count} >= {crit_error_code_repeat}")

api_duration_by_path = app.get("api_duration_by_path") or {}
interactive_durations = []
excluded_latency_observations = []
for path, summary in api_duration_by_path.items():
    count = int(summary.get("count") or 0)
    if count <= 0:
        continue
    p95 = summary.get("p95_ms")
    if p95 is None:
        continue
    if path in excluded_api_p95_paths or any(path.startswith(prefix) for prefix in excluded_api_p95_prefixes):
        excluded_latency_observations.append(f"{path} p95 {float(p95):.0f}ms count {count}")
        continue
    # The baseline artifact keeps per-path percentiles, not raw durations. Weighting
    # p95 by count is good enough for alert routing and avoids one heavy batch API
    # dominating ordinary request latency.
    interactive_durations.extend([float(p95)] * count)

api_duration = app.get("api_duration_summary") or {}
api_p95 = None
api_p95_count = 0
if interactive_durations:
    interactive_durations.sort()
    index = min(int(round((len(interactive_durations) - 1) * 0.95)), len(interactive_durations) - 1)
    api_p95 = interactive_durations[index]
    api_p95_count = len(interactive_durations)
elif not api_duration_by_path and api_duration.get("p95_ms") is not None:
    api_p95 = float(api_duration.get("p95_ms"))
    api_p95_count = int(api_duration.get("count") or 0)

if api_p95 is not None:
    if api_p95_count < min_api_p95_count:
        observation.append(
            f"interactive api p95 {api_p95:.0f}ms observed with sample count "
            f"{api_p95_count} < {min_api_p95_count}"
        )
    elif api_p95 >= crit_api_p95_ms:
        critical.append(f"interactive api p95 {api_p95:.0f}ms >= {crit_api_p95_ms:.0f}ms")
    elif api_p95 >= warn_api_p95_ms:
        warning.append(f"interactive api p95 {api_p95:.0f}ms >= {warn_api_p95_ms:.0f}ms")

raw_errors = int(app.get("raw_error_lines") or 0)
if raw_errors >= warn_raw_errors:
    warning.append(f"raw app error lines {raw_errors} >= {warn_raw_errors}")

nginx_status = nginx.get("status_counts") or {}
nginx_5xx_classification = nginx.get("five_xx_classification") or {}
if nginx_5xx_classification:
    nginx_5xx = int(nginx_5xx_classification.get("user_5xx") or 0)
    alb_health_5xx = int(nginx_5xx_classification.get("alb_health_5xx") or 0)
    probe_5xx = int(nginx_5xx_classification.get("probe_5xx") or 0)
    if alb_health_5xx > 0:
        observation.append(f"nginx /alb-health 5xx count {alb_health_5xx}")
    if probe_5xx > 0:
        observation.append(f"nginx probe 5xx count {probe_5xx}")
else:
    nginx_5xx = sum(int(count) for status, count in nginx_status.items() if str(status).startswith("5"))
if nginx_5xx >= crit_5xx:
    critical.append(f"nginx user 5xx count {nginx_5xx} >= {crit_5xx}")
elif nginx_5xx >= warn_5xx:
    warning.append(f"nginx user 5xx count {nginx_5xx} >= {warn_5xx}")

request_summary = nginx.get("request_time_summary") or {}
request_p95 = request_summary.get("p95_seconds")
if request_p95 is not None:
    request_p95 = float(request_p95)
    if request_p95 >= crit_nginx_p95_seconds:
        critical.append(f"nginx request_time p95 {request_p95:.3f}s >= {crit_nginx_p95_seconds:.3f}s")
    elif request_p95 >= warn_nginx_p95_seconds:
        warning.append(f"nginx request_time p95 {request_p95:.3f}s >= {warn_nginx_p95_seconds:.3f}s")
elif nginx:
    warning.append("nginx request_time unavailable; reload timed log_format or wait for new access lines")

status = "critical" if critical else "warning" if warning else "ok"
print(f"LOG_ALERT_STATUS={status}")
for item in critical:
    print(f"critical={item}")
for item in warning:
    print(f"warning={item}")
for item in observation:
    print(f"observation={item}")
for item in excluded_latency_observations:
    print(f"excluded_latency={item}")
if app_path.exists():
    print(f"app_json={app_path}")
if nginx_path.exists():
    print(f"nginx_json={nginx_path}")

raise SystemExit(2 if critical else 0)
PY
