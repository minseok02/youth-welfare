#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SURFACE_DOC="${SURFACE_DOC:-${ROOT_DIR}/docs/core/admin-dashboard-alert-surface-contract.md}"
ADMIN_PAGE="${ADMIN_PAGE:-${ROOT_DIR}/frontend/src/pages/AdminDashboardPage.jsx}"

python3 - "${SURFACE_DOC}" "${ADMIN_PAGE}" <<'PY'
import sys
from pathlib import Path

doc_path = Path(sys.argv[1])
page_path = Path(sys.argv[2])

if not doc_path.exists():
    raise SystemExit(f"missing admin dashboard alert surface contract: {doc_path}")
if not page_path.exists():
    raise SystemExit(f"missing admin dashboard page: {page_path}")

doc = doc_path.read_text(encoding="utf-8")
page = page_path.read_text(encoding="utf-8")

required_contract_ids = [
    "ADMIN_DASHBOARD_ALERT_SURFACE_AUTHORITY",
    "ADMIN_DASHBOARD_ALERT_SURFACE_MAP",
    "ADMIN_DASHBOARD_ALERT_SURFACE_LIMITS",
    "ADMIN_DASHBOARD_ALERT_SURFACE_VERIFICATION",
]
required_alert_ids = [
    "COLLECT_FAILED_JOB_RATE",
    "SEARCH_ZERO_RESULT_RATE",
    "RECOMMENDATION_RUN_FAILURE_RATE",
    "NOTIFICATION_RETRY_BACKLOG",
    "WEB_PUSH_DISABLED_RATIO",
]
required_doc_terms = [
    "evaluate-operational-alert-thresholds.sh",
    "GET /api/admin/dashboard/collect-failures",
    "GET /api/admin/dashboard/search-failures",
    "GET /api/admin/dashboard/recommendation-run-summary",
    "GET /api/admin/dashboard/notification-attempt-summary",
    "GET /api/admin/dashboard/summary",
    "failedJobsInWindow",
    "partialSuccessJobsInWindow",
    "zeroResultSearchesInWindow",
    "errorRuns",
    "noCandidateRuns",
    "averageDurationMs",
    "retryableFailedNotifications",
    "terminalFailedNotifications",
    "disabledAttempts",
    "endpointHost",
    "nextAction",
    "zero-result rate",
    "disabled ratio",
]
required_page_terms = [
    "/api/admin/dashboard/collect-failures",
    "/api/admin/dashboard/search-failures",
    "/api/admin/dashboard/recommendation-run-summary",
    "/api/admin/dashboard/notification-attempt-summary",
    "admin-collect-triage",
    "admin-search-triage",
    "admin-recommendation-run-summary",
    "admin-notification-attempt-summary",
    "failedJobsInWindow",
    "partialSuccessJobsInWindow",
    "zeroResultSearchesInWindow",
    "errorRuns",
    "noCandidateRuns",
    "averageDurationMs",
    "disabledAttempts",
    "endpointHost",
    "nextAction",
    "다음 조치",
]

failures = []
for item in required_contract_ids + required_alert_ids + required_doc_terms:
    if item not in doc:
        failures.append(f"missing doc term {item}")

for item in required_page_terms:
    if item not in page:
        failures.append(f"missing dashboard page term {item}")

if "dashboard 화면은 alert status를 직접 계산하지 않습니다." not in doc:
    failures.append("missing dashboard non-authority decision")

if failures:
    for failure in failures:
        print(failure, file=sys.stderr)
    raise SystemExit(1)

print("admin dashboard alert surface contract passed")
PY
