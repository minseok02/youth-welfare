#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/performance/perf-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_CONTAINER_NAME="${APP_CONTAINER_NAME:-youth-welfare-app}"
JVM_RUNTIME_ROOT="${JVM_RUNTIME_ROOT:-${ROOT_DIR}/tmp/performance/jvm-runtime}"
RUN_TS_UTC="$(perf_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${JVM_RUNTIME_ROOT}/${RUN_TS_UTC}}"
CONTEXT_TXT="${ARTIFACT_DIR}/run-context.txt"
HEALTH_JSON="${ARTIFACT_DIR}/actuator-health.json"
METRICS_LIST_JSON="${ARTIFACT_DIR}/actuator-metrics-list.json"
METRICS_DIR="${ARTIFACT_DIR}/actuator-metrics"
DOCKER_STATS_TSV="${ARTIFACT_DIR}/docker-stats.tsv"
DOCKER_INSPECT_JSON="${ARTIFACT_DIR}/docker-inspect.json"
RECENT_LOGS="${ARTIFACT_DIR}/app-recent-logs.txt"
SUMMARY_TXT="${ARTIFACT_DIR}/jvm-runtime-summary.txt"
SUMMARY_JSON="${ARTIFACT_DIR}/jvm-runtime-summary.json"
LATEST_DIR="${JVM_RUNTIME_ROOT}/latest"
LATEST_SUMMARY_TXT="${JVM_RUNTIME_ROOT}/latest-jvm-runtime-summary.txt"
LATEST_SUMMARY_JSON="${JVM_RUNTIME_ROOT}/latest-jvm-runtime-summary.json"

smoke_require_command curl
perf_require_python
mkdir -p "${ARTIFACT_DIR}" "${METRICS_DIR}"
perf_write_run_context "${CONTEXT_TXT}"

health_status="$(curl -sS -o "${HEALTH_JSON}" -w '%{http_code}' "${APP_BASE_URL}/actuator/health" || true)"
metrics_status="$(curl -sS -o "${METRICS_LIST_JSON}" -w '%{http_code}' "${APP_BASE_URL}/actuator/metrics" || true)"

metric_names=(
  "jvm.memory.used"
  "jvm.memory.max"
  "jvm.gc.pause"
  "jvm.threads.live"
  "jvm.threads.peak"
  "hikaricp.connections"
  "hikaricp.connections.active"
  "hikaricp.connections.idle"
  "hikaricp.connections.pending"
  "http.server.requests"
  "process.cpu.usage"
  "system.cpu.usage"
  "tomcat.threads.current"
  "tomcat.threads.busy"
)

if [[ "${metrics_status}" == "200" ]]; then
  for metric_name in "${metric_names[@]}"; do
    safe_name="${metric_name//./-}"
    curl -sS -o "${METRICS_DIR}/${safe_name}.json" "${APP_BASE_URL}/actuator/metrics/${metric_name}" || true
  done
fi

if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -Fxq "${APP_CONTAINER_NAME}"; then
  docker stats --no-stream --format '{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}\t{{.PIDs}}' "${APP_CONTAINER_NAME}" > "${DOCKER_STATS_TSV}" || true
  docker inspect "${APP_CONTAINER_NAME}" > "${DOCKER_INSPECT_JSON}" || true
  docker logs --since 10m "${APP_CONTAINER_NAME}" > "${RECENT_LOGS}" 2>&1 || true
else
  : > "${DOCKER_STATS_TSV}"
  : > "${DOCKER_INSPECT_JSON}"
  : > "${RECENT_LOGS}"
fi

python3 - \
  "${health_status}" \
  "${metrics_status}" \
  "${HEALTH_JSON}" \
  "${METRICS_DIR}" \
  "${DOCKER_STATS_TSV}" \
  "${DOCKER_INSPECT_JSON}" \
  "${RECENT_LOGS}" \
  "${SUMMARY_TXT}" \
  "${SUMMARY_JSON}" \
  "${CONTEXT_TXT}" <<'PY'
import json
import re
import sys
from pathlib import Path

health_status, metrics_status = sys.argv[1:3]
health_path, metrics_dir, docker_stats_path, docker_inspect_path, logs_path, summary_txt, summary_json, context_path = map(Path, sys.argv[3:11])

def load_json(path):
    try:
        text = path.read_text(encoding="utf-8")
        return json.loads(text) if text.strip() else None
    except Exception:
        return None

context = {}
for line in context_path.read_text(encoding="utf-8").splitlines():
    if "=" in line:
        key, value = line.split("=", 1)
        context[key] = value

metrics = {}
if metrics_status == "200":
    for path in sorted(metrics_dir.glob("*.json")):
        payload = load_json(path)
        if not payload:
            continue
        name = payload.get("name") or path.stem.replace("-", ".")
        measurements = {
            item.get("statistic", "value"): item.get("value")
            for item in payload.get("measurements", [])
        }
        metrics[name] = {
            "description": payload.get("description"),
            "baseUnit": payload.get("baseUnit"),
            "measurements": measurements,
        }

docker_stats = docker_stats_path.read_text(encoding="utf-8", errors="replace").strip()
inspect_payload = load_json(docker_inspect_path) or []
container = inspect_payload[0] if inspect_payload else {}
host_config = container.get("HostConfig", {})
state = container.get("State", {})
logs_text = logs_path.read_text(encoding="utf-8", errors="replace")
exception_count = len(re.findall(r"\b(Exception|ERROR|Caused by:)\b", logs_text))

payload = {
    "context": context,
    "health_status": health_status,
    "health": load_json(health_path),
    "actuator_metrics_status": metrics_status,
    "actuator_metrics_available": metrics_status == "200",
    "metrics": metrics,
    "docker_stats_raw": docker_stats,
    "container": {
        "name": container.get("Name"),
        "image": container.get("Config", {}).get("Image"),
        "running": state.get("Running"),
        "started_at": state.get("StartedAt"),
        "restart_count": container.get("RestartCount"),
        "memory_limit_bytes": host_config.get("Memory"),
        "pids_limit": host_config.get("PidsLimit"),
        "read_only_rootfs": host_config.get("ReadonlyRootfs"),
    },
    "recent_log_exception_count": exception_count,
    "recent_logs_path": str(logs_path),
}
summary_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

failed = health_status != "200" or not payload["container"].get("running", False)
lines = [f"jvm_runtime_baseline={'failed' if failed else 'passed'}"]
lines.append(f"health_status={health_status}")
lines.append(f"actuator_metrics_status={metrics_status}")
lines.append(f"actuator_metrics_available={str(metrics_status == '200').lower()}")
if docker_stats:
    lines.append(f"docker_stats={docker_stats}")
for key, value in payload["container"].items():
    lines.append(f"container_{key}={value}")
lines.append(f"recent_log_exception_count={exception_count}")
if metrics_status != "200":
    lines.append("metrics_note=actuator metrics endpoint is not currently exposed to this baseline script")
summary_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(summary_txt.read_text(encoding="utf-8"), end="")
if failed:
    raise SystemExit(1)
PY

perf_publish_latest "${ARTIFACT_DIR}" "${LATEST_DIR}" "${SUMMARY_TXT}" "${LATEST_SUMMARY_TXT}" "${SUMMARY_JSON}" "${LATEST_SUMMARY_JSON}"

echo "artifact_dir=${ARTIFACT_DIR}"
echo "latest_artifact_dir=${LATEST_DIR}"
echo "latest_summary=${LATEST_SUMMARY_TXT}"
echo "latest_json=${LATEST_SUMMARY_JSON}"
