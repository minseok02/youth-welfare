#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
THRESHOLD_DOC="${THRESHOLD_DOC:-${ROOT_DIR}/docs/core/log-alert-thresholds.md}"

python3 - "${THRESHOLD_DOC}" <<'PY'
import sys
from pathlib import Path

doc_path = Path(sys.argv[1])
if not doc_path.exists():
    raise SystemExit(f"missing threshold doc: {doc_path}")

text = doc_path.read_text(encoding="utf-8")
required_alert_ids = [
    "COLLECT_FAILED_JOB_RATE",
    "SEARCH_ZERO_RESULT_RATE",
    "RECOMMENDATION_RUN_FAILURE_RATE",
    "NOTIFICATION_RETRY_BACKLOG",
    "WEB_PUSH_DISABLED_RATIO",
    "DB_AUDIT_INTEGRITY",
]
required_sources = [
    "run-local-ops-observation-suite.sh",
    "run-local-chat-observability-audit.sh",
    "run-local-notification-backlog-audit.sh",
    "evaluate-operational-alert-thresholds.sh",
    "recommendation_run_logs",
    "notification_attempt_logs",
    "web_push_subscriptions",
    "audit-operational-db-state.sh",
]
required_metrics = [
    "collect_failed_jobs_in_window",
    "chat_observability_window_7d_zero_result_rate_pct",
    "retryable_failed_total",
    "retryable_failed_due_now",
    "terminal_failed_total",
    "disabled subscription ratio",
    "active_users_without_pii",
    "waiting_locks",
]

failures = []
for alert_id in required_alert_ids:
    rows = [line for line in text.splitlines() if f"`{alert_id}`" in line]
    if not rows:
        failures.append(f"missing alert_id {alert_id}")
        continue
    row = rows[0]
    if "warning:" not in row:
        failures.append(f"{alert_id} row is missing warning threshold")
    if "critical:" not in row:
        failures.append(f"{alert_id} row is missing critical threshold")

for source in required_sources:
    if source not in text:
        failures.append(f"missing source reference {source}")

for metric in required_metrics:
    if metric not in text:
        failures.append(f"missing metric reference {metric}")

if failures:
    for failure in failures:
        print(failure, file=sys.stderr)
    raise SystemExit(1)

print("operational alert threshold contract passed")
PY
