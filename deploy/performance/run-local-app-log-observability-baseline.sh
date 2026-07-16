#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

APP_LOG_FILE="${APP_LOG_FILE:-}"
APP_LOG_TAIL_LINES="${APP_LOG_TAIL_LINES:-10000}"
APP_LOG_SINCE="${APP_LOG_SINCE:-}"
APP_LOG_ROOT="${APP_LOG_ROOT:-${ROOT_DIR}/tmp/performance/app-log-observability}"
APP_LOG_COMPOSE_FILE="${APP_LOG_COMPOSE_FILE:-${ROOT_DIR}/docker-compose.prod.elasticache.yml}"
APP_LOG_COMPOSE_SERVICE="${APP_LOG_COMPOSE_SERVICE:-app}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${APP_LOG_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
LOG_SAMPLE="${ARTIFACT_DIR}/app-tail.log"
SUMMARY_TXT="${ARTIFACT_DIR}/app-log-observability-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/app-log-observability-summary.json"
LATEST_DIR="${APP_LOG_ROOT}/latest"
LATEST_SUMMARY_TXT="${APP_LOG_ROOT}/latest-app-log-observability-summary.txt"
LATEST_SUMMARY_JSON="${APP_LOG_ROOT}/latest-app-log-observability-summary.json"

perf_require_python
mkdir -p "${ARTIFACT_DIR}"
perf_sanitize_artifacts_on_exit "${ARTIFACT_DIR}"
perf_write_run_context "${CONTEXT_TXT}"

if [[ -n "${APP_LOG_FILE}" && -r "${APP_LOG_FILE}" ]]; then
  tail -n "${APP_LOG_TAIL_LINES}" "${APP_LOG_FILE}" 2>/dev/null | smoke_redact_stream_for_log > "${LOG_SAMPLE}" || true
elif command -v docker >/dev/null 2>&1; then
  if [[ -n "${APP_LOG_SINCE}" ]]; then
    docker compose -f "${APP_LOG_COMPOSE_FILE}" logs --no-color --since="${APP_LOG_SINCE}" "${APP_LOG_COMPOSE_SERVICE}" 2>/dev/null | smoke_redact_stream_for_log > "${LOG_SAMPLE}" || true
  else
    docker compose -f "${APP_LOG_COMPOSE_FILE}" logs --no-color --tail="${APP_LOG_TAIL_LINES}" "${APP_LOG_COMPOSE_SERVICE}" 2>/dev/null | smoke_redact_stream_for_log > "${LOG_SAMPLE}" || true
  fi
else
  : > "${LOG_SAMPLE}"
fi

python3 - "${LOG_SAMPLE}" "${SUMMARY_TXT}" "${SUMMARY_JSON}" "${CONTEXT_TXT}" "${APP_LOG_FILE}" "${APP_LOG_COMPOSE_FILE}" "${APP_LOG_COMPOSE_SERVICE}" "${APP_LOG_SINCE}" <<'PY'
import collections
import json
import re
import statistics
import sys
from pathlib import Path

sample_path, summary_txt, summary_json, context_path = map(Path, sys.argv[1:5])
app_log_file, compose_file, compose_service, app_log_since = sys.argv[5:9]
lines = [line for line in sample_path.read_text(encoding="utf-8", errors="replace").splitlines() if line.strip()]

api_count = 0
api_status_counts = collections.Counter()
api_path_counts = collections.Counter()
api_error_counts = collections.Counter()
api_durations = []
api_durations_by_path = collections.defaultdict(list)
api_slow_samples = []
auth_counts = collections.Counter()
admin_counts = collections.Counter()
recommendation_counts = collections.Counter()
notification_counts = collections.Counter()
user_action_counts = collections.Counter()
raw_error_lines = 0
raw_warn_lines = 0

token_re = re.compile(r'([A-Za-z][A-Za-z0-9]*)=([^ ]+)')
duration_re = re.compile(r'durationMs=([0-9]+)')

def tokens(line):
    return dict(token_re.findall(line))

for line in lines:
    lower = line.lower()
    if " error " in lower or "\terror\t" in lower or "exception" in lower or "caused by" in lower:
        raw_error_lines += 1
    if " warn " in lower or "\twarn\t" in lower:
        raw_warn_lines += 1

    if "[ApiRequest]" in line:
        api_count += 1
        data = tokens(line)
        status = data.get("status", "unknown")
        method = data.get("method", "unknown")
        path = data.get("path", "unknown")
        error_code = data.get("errorCode", "null")
        api_status_counts[status] += 1
        api_path_counts[f"{method} {path}"] += 1
        if error_code != "null":
            api_error_counts[error_code] += 1
        duration_match = duration_re.search(line)
        if duration_match:
            duration_ms = int(duration_match.group(1))
            path_key = f"{method} {path}"
            api_durations.append(duration_ms)
            api_durations_by_path[path_key].append(duration_ms)
            api_slow_samples.append({
                "method": method,
                "path": path,
                "status": status,
                "duration_ms": duration_ms,
                "error_code": error_code if error_code != "null" else None,
            })
        continue

    if "[AuthAudit]" in line:
        data = tokens(line)
        auth_counts[f"{data.get('event', 'unknown')}:{data.get('outcome', 'unknown')}"] += 1
        continue

    if "[AdminAudit]" in line:
        data = tokens(line)
        admin_counts[f"{data.get('event', 'unknown')}:{data.get('outcome', 'unknown')}"] += 1
        continue

    if "[RecommendationRun]" in line:
        data = tokens(line)
        recommendation_counts[data.get("outcome", "unknown")] += 1
        continue

    if "[NotificationAttempt]" in line:
        data = tokens(line)
        notification_counts[f"{data.get('channel', 'unknown')}:{data.get('outcome', 'unknown')}"] += 1
        continue

    if "[UserAction]" in line:
        data = tokens(line)
        user_action_counts[data.get("action", "unknown")] += 1

def pct(values, p):
    if not values:
        return None
    values = sorted(values)
    k = (len(values) - 1) * p
    f = int(k)
    c = min(f + 1, len(values) - 1)
    if f == c:
        return values[f]
    return values[f] + (values[c] - values[f]) * (k - f)

duration_summary = None
if api_durations:
    duration_summary = {
        "count": len(api_durations),
        "p50_ms": pct(api_durations, 0.50),
        "p95_ms": pct(api_durations, 0.95),
        "p99_ms": pct(api_durations, 0.99),
        "max_ms": max(api_durations),
        "avg_ms": statistics.mean(api_durations),
    }

duration_by_path = {}
for path, values in sorted(api_durations_by_path.items()):
    duration_by_path[path] = {
        "count": len(values),
        "p50_ms": pct(values, 0.50),
        "p95_ms": pct(values, 0.95),
        "p99_ms": pct(values, 0.99),
        "max_ms": max(values),
        "avg_ms": statistics.mean(values),
    }

api_slow_samples = sorted(api_slow_samples, key=lambda item: item["duration_ms"], reverse=True)[:20]

context = {}
for raw in context_path.read_text(encoding="utf-8").splitlines():
    if "=" in raw:
        key, value = raw.split("=", 1)
        context[key] = value

payload = {
    "context": context,
    "app_log_file": app_log_file or None,
    "compose_file": compose_file,
    "compose_service": compose_service,
    "app_log_since": app_log_since or None,
    "sample_line_count": len(lines),
    "raw_error_lines": raw_error_lines,
    "raw_warn_lines": raw_warn_lines,
    "api_request_count": api_count,
    "api_status_counts": dict(api_status_counts),
    "api_error_counts": dict(api_error_counts),
    "api_top_paths": api_path_counts.most_common(30),
    "api_duration_summary": duration_summary,
    "api_duration_by_path": duration_by_path,
    "api_slow_samples": api_slow_samples,
    "auth_audit_counts": dict(auth_counts),
    "admin_audit_counts": dict(admin_counts),
    "recommendation_run_counts": dict(recommendation_counts),
    "notification_attempt_counts": dict(notification_counts),
    "user_action_counts": dict(user_action_counts),
}
summary_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

out = ["app_log_observability_baseline=passed"]
out.append(f"sample_line_count={len(lines)}")
out.append(f"raw_error_lines={raw_error_lines}")
out.append(f"raw_warn_lines={raw_warn_lines}")
out.append(f"api_request_count={api_count}")
for status, count in sorted(api_status_counts.items()):
    out.append(f"api_status_{status}={count}")
for error_code, count in sorted(api_error_counts.items()):
    out.append(f"api_error_code_{error_code}={count}")
if duration_summary:
    out.append(
        "api_duration "
        f"p50_ms={duration_summary['p50_ms']:.0f} "
        f"p95_ms={duration_summary['p95_ms']:.0f} "
        f"p99_ms={duration_summary['p99_ms']:.0f} "
        f"max_ms={duration_summary['max_ms']:.0f}"
    )
for key, count in auth_counts.most_common(12):
    out.append(f"auth_audit count={count} key={key}")
for key, count in admin_counts.most_common(12):
    out.append(f"admin_audit count={count} key={key}")
for key, count in recommendation_counts.most_common(12):
    out.append(f"recommendation_run count={count} outcome={key}")
for key, count in notification_counts.most_common(12):
    out.append(f"notification_attempt count={count} key={key}")
for key, count in user_action_counts.most_common(12):
    out.append(f"user_action count={count} action={key}")
summary_txt.write_text("\n".join(out) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")
PY

perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
