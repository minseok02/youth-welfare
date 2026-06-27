#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

OPS_SUMMARY="${OPS_SUMMARY:-${ROOT_DIR}/tmp/ops-observation/latest-ops-observation-summary.txt}"
NOTIFICATION_BACKLOG_SUMMARY="${NOTIFICATION_BACKLOG_SUMMARY:-${ROOT_DIR}/tmp/notification-backlog-audit/latest-notification-backlog-summary.txt}"
CHAT_OBSERVABILITY_SUMMARY="${CHAT_OBSERVABILITY_SUMMARY:-${ROOT_DIR}/tmp/chat-observability-audit/latest-chat-observability-summary.txt}"
RECOMMENDATION_RUN_SUMMARY="${RECOMMENDATION_RUN_SUMMARY:-}"
WEB_PUSH_SUMMARY="${WEB_PUSH_SUMMARY:-}"
OPERATIONAL_DB_AUDIT_SUMMARY="${OPERATIONAL_DB_AUDIT_SUMMARY:-}"

COLLECT_WARN_FAILED_RATE_PCT="${OP_ALERT_COLLECT_WARN_FAILED_RATE_PCT:-5}"
COLLECT_CRIT_FAILED_RATE_PCT="${OP_ALERT_COLLECT_CRIT_FAILED_RATE_PCT:-20}"
COLLECT_CRIT_FAILED_JOBS="${OP_ALERT_COLLECT_CRIT_FAILED_JOBS:-3}"
SEARCH_WARN_ZERO_RESULT_RATE_PCT="${OP_ALERT_SEARCH_WARN_ZERO_RESULT_RATE_PCT:-20}"
SEARCH_CRIT_ZERO_RESULT_RATE_PCT="${OP_ALERT_SEARCH_CRIT_ZERO_RESULT_RATE_PCT:-40}"
SEARCH_WARN_NON_BRANCH_ZERO_RESULT_RATE_PCT="${OP_ALERT_SEARCH_WARN_NON_BRANCH_ZERO_RESULT_RATE_PCT:-15}"
SEARCH_CRIT_NON_BRANCH_ZERO_RESULT_RATE_PCT="${OP_ALERT_SEARCH_CRIT_NON_BRANCH_ZERO_RESULT_RATE_PCT:-25}"
RECOMMENDATION_WARN_FAILURE_RATE_PCT="${OP_ALERT_RECOMMENDATION_WARN_FAILURE_RATE_PCT:-5}"
RECOMMENDATION_CRIT_FAILURE_RATE_PCT="${OP_ALERT_RECOMMENDATION_CRIT_FAILURE_RATE_PCT:-20}"
RECOMMENDATION_CRIT_ERROR_10M="${OP_ALERT_RECOMMENDATION_CRIT_ERROR_10M:-3}"
RECOMMENDATION_WARN_DURATION_MS="${OP_ALERT_RECOMMENDATION_WARN_DURATION_MS:-5000}"
NOTIFICATION_WARN_RETRYABLE_TOTAL="${OP_ALERT_NOTIFICATION_WARN_RETRYABLE_TOTAL:-10}"
NOTIFICATION_CRIT_RETRYABLE_TOTAL="${OP_ALERT_NOTIFICATION_CRIT_RETRYABLE_TOTAL:-100}"
NOTIFICATION_CRIT_DUE_NOW="${OP_ALERT_NOTIFICATION_CRIT_DUE_NOW:-10}"
WEB_PUSH_WARN_MIN_SUBSCRIPTIONS="${OP_ALERT_WEB_PUSH_WARN_MIN_SUBSCRIPTIONS:-20}"
WEB_PUSH_WARN_DISABLED_RATIO_PCT="${OP_ALERT_WEB_PUSH_WARN_DISABLED_RATIO_PCT:-10}"
WEB_PUSH_CRIT_DISABLED_RATIO_PCT="${OP_ALERT_WEB_PUSH_CRIT_DISABLED_RATIO_PCT:-25}"
WEB_PUSH_CRIT_FAILURE_15M="${OP_ALERT_WEB_PUSH_CRIT_FAILURE_15M:-10}"
DB_AUDIT_WARN_ACTIVE_QUERIES_OVER_5M="${OP_ALERT_DB_AUDIT_WARN_ACTIVE_QUERIES_OVER_5M:-1}"
DB_AUDIT_CRIT_WAITING_LOCKS="${OP_ALERT_DB_AUDIT_CRIT_WAITING_LOCKS:-1}"

python3 - \
  "${OPS_SUMMARY}" \
  "${NOTIFICATION_BACKLOG_SUMMARY}" \
  "${CHAT_OBSERVABILITY_SUMMARY}" \
  "${RECOMMENDATION_RUN_SUMMARY}" \
  "${WEB_PUSH_SUMMARY}" \
  "${OPERATIONAL_DB_AUDIT_SUMMARY}" \
  "${COLLECT_WARN_FAILED_RATE_PCT}" \
  "${COLLECT_CRIT_FAILED_RATE_PCT}" \
  "${COLLECT_CRIT_FAILED_JOBS}" \
  "${SEARCH_WARN_ZERO_RESULT_RATE_PCT}" \
  "${SEARCH_CRIT_ZERO_RESULT_RATE_PCT}" \
  "${SEARCH_WARN_NON_BRANCH_ZERO_RESULT_RATE_PCT}" \
  "${SEARCH_CRIT_NON_BRANCH_ZERO_RESULT_RATE_PCT}" \
  "${RECOMMENDATION_WARN_FAILURE_RATE_PCT}" \
  "${RECOMMENDATION_CRIT_FAILURE_RATE_PCT}" \
  "${RECOMMENDATION_CRIT_ERROR_10M}" \
  "${RECOMMENDATION_WARN_DURATION_MS}" \
  "${NOTIFICATION_WARN_RETRYABLE_TOTAL}" \
  "${NOTIFICATION_CRIT_RETRYABLE_TOTAL}" \
  "${NOTIFICATION_CRIT_DUE_NOW}" \
  "${WEB_PUSH_WARN_MIN_SUBSCRIPTIONS}" \
  "${WEB_PUSH_WARN_DISABLED_RATIO_PCT}" \
  "${WEB_PUSH_CRIT_DISABLED_RATIO_PCT}" \
  "${WEB_PUSH_CRIT_FAILURE_15M}" \
  "${DB_AUDIT_WARN_ACTIVE_QUERIES_OVER_5M}" \
  "${DB_AUDIT_CRIT_WAITING_LOCKS}" <<'PY' | smoke_redact_stream_for_log
import sys
from pathlib import Path

(
    ops_summary_path,
    notification_backlog_summary_path,
    chat_observability_summary_path,
    recommendation_run_summary_path,
    web_push_summary_path,
    operational_db_audit_summary_path,
    collect_warn_failed_rate_pct,
    collect_crit_failed_rate_pct,
    collect_crit_failed_jobs,
    search_warn_zero_result_rate_pct,
    search_crit_zero_result_rate_pct,
    search_warn_non_branch_zero_result_rate_pct,
    search_crit_non_branch_zero_result_rate_pct,
    recommendation_warn_failure_rate_pct,
    recommendation_crit_failure_rate_pct,
    recommendation_crit_error_10m,
    recommendation_warn_duration_ms,
    notification_warn_retryable_total,
    notification_crit_retryable_total,
    notification_crit_due_now,
    web_push_warn_min_subscriptions,
    web_push_warn_disabled_ratio_pct,
    web_push_crit_disabled_ratio_pct,
    web_push_crit_failure_15m,
    db_audit_warn_active_queries_over_5m,
    db_audit_crit_waiting_locks,
) = sys.argv[1:]

collect_warn_failed_rate_pct = float(collect_warn_failed_rate_pct)
collect_crit_failed_rate_pct = float(collect_crit_failed_rate_pct)
collect_crit_failed_jobs = int(collect_crit_failed_jobs)
search_warn_zero_result_rate_pct = float(search_warn_zero_result_rate_pct)
search_crit_zero_result_rate_pct = float(search_crit_zero_result_rate_pct)
search_warn_non_branch_zero_result_rate_pct = float(search_warn_non_branch_zero_result_rate_pct)
search_crit_non_branch_zero_result_rate_pct = float(search_crit_non_branch_zero_result_rate_pct)
recommendation_warn_failure_rate_pct = float(recommendation_warn_failure_rate_pct)
recommendation_crit_failure_rate_pct = float(recommendation_crit_failure_rate_pct)
recommendation_crit_error_10m = int(recommendation_crit_error_10m)
recommendation_warn_duration_ms = float(recommendation_warn_duration_ms)
notification_warn_retryable_total = int(notification_warn_retryable_total)
notification_crit_retryable_total = int(notification_crit_retryable_total)
notification_crit_due_now = int(notification_crit_due_now)
web_push_warn_min_subscriptions = int(web_push_warn_min_subscriptions)
web_push_warn_disabled_ratio_pct = float(web_push_warn_disabled_ratio_pct)
web_push_crit_disabled_ratio_pct = float(web_push_crit_disabled_ratio_pct)
web_push_crit_failure_15m = int(web_push_crit_failure_15m)
db_audit_warn_active_queries_over_5m = int(db_audit_warn_active_queries_over_5m)
db_audit_crit_waiting_locks = int(db_audit_crit_waiting_locks)

critical = []
warning = []
skipped = []
observed = []


def read_key_values(path_value, *, optional=False):
    if not path_value:
        if optional:
            return {}
        warning.append("missing required artifact path")
        return {}
    path = Path(path_value)
    if not path.exists():
        if optional:
            skipped.append(f"optional artifact missing {path}")
        else:
            warning.append(f"missing artifact {path}")
        return {}
    data = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key.strip()] = value.strip()
    observed.append(str(path))
    return data


def as_int(values, key, default=0):
    try:
        return int(str(values.get(key, default) or default))
    except ValueError:
        warning.append(f"invalid integer metric {key}={values.get(key)}")
        return default


def as_float(values, key, default=0.0):
    try:
        return float(str(values.get(key, default) or default))
    except ValueError:
        warning.append(f"invalid float metric {key}={values.get(key)}")
        return default


def add_critical(alert_id, message):
    critical.append(f"{alert_id}: {message}")


def add_warning(alert_id, message):
    warning.append(f"{alert_id}: {message}")


ops = read_key_values(ops_summary_path)
notification = read_key_values(notification_backlog_summary_path)
chat = read_key_values(chat_observability_summary_path)
recommendation = read_key_values(recommendation_run_summary_path, optional=True)
web_push = read_key_values(web_push_summary_path, optional=True)
db_audit = read_key_values(operational_db_audit_summary_path, optional=True)

# COLLECT_FAILED_JOB_RATE
failed_jobs = as_int(ops, "collect_failed_jobs_in_window")
partial_jobs = as_int(ops, "collect_partial_success_jobs_in_window")
open_circuits = as_int(ops, "open_collect_circuits")
lane_count = as_int(ops, "collect_lane_count")
collect_failed_rate = (failed_jobs * 100 / lane_count) if lane_count > 0 else 0.0
if open_circuits > 0:
    add_critical("COLLECT_FAILED_JOB_RATE", f"open_collect_circuits {open_circuits} > 0")
elif failed_jobs >= collect_crit_failed_jobs:
    add_critical("COLLECT_FAILED_JOB_RATE", f"collect_failed_jobs_in_window {failed_jobs} >= {collect_crit_failed_jobs}")
elif collect_failed_rate >= collect_crit_failed_rate_pct:
    add_critical("COLLECT_FAILED_JOB_RATE", f"collect failed job rate {collect_failed_rate:.2f}% >= {collect_crit_failed_rate_pct:.2f}%")
elif failed_jobs > 0 or partial_jobs > 0:
    add_warning("COLLECT_FAILED_JOB_RATE", f"failed={failed_jobs} partial={partial_jobs}")
elif collect_failed_rate >= collect_warn_failed_rate_pct:
    add_warning("COLLECT_FAILED_JOB_RATE", f"collect failed job rate {collect_failed_rate:.2f}% >= {collect_warn_failed_rate_pct:.2f}%")

# SEARCH_ZERO_RESULT_RATE
zero_rate = as_float(ops, "chat_observability_window_7d_zero_result_rate_pct", None)
if zero_rate is None:
    zero_rate = as_float(chat, "window_7d_zero_result_rate_pct")
non_branch_zero_rate = as_float(chat, "window_7d_zero_result_non_branch_rate_pct")
if zero_rate >= search_crit_zero_result_rate_pct:
    add_critical("SEARCH_ZERO_RESULT_RATE", f"7d zero-result rate {zero_rate:.2f}% >= {search_crit_zero_result_rate_pct:.2f}%")
elif non_branch_zero_rate >= search_crit_non_branch_zero_result_rate_pct:
    add_critical("SEARCH_ZERO_RESULT_RATE", f"7d non-branch zero-result rate {non_branch_zero_rate:.2f}% >= {search_crit_non_branch_zero_result_rate_pct:.2f}%")
elif zero_rate >= search_warn_zero_result_rate_pct:
    add_warning("SEARCH_ZERO_RESULT_RATE", f"7d zero-result rate {zero_rate:.2f}% >= {search_warn_zero_result_rate_pct:.2f}%")
elif non_branch_zero_rate >= search_warn_non_branch_zero_result_rate_pct:
    add_warning("SEARCH_ZERO_RESULT_RATE", f"7d non-branch zero-result rate {non_branch_zero_rate:.2f}% >= {search_warn_non_branch_zero_result_rate_pct:.2f}%")

# RECOMMENDATION_RUN_FAILURE_RATE, optional DB/API artifact.
if recommendation:
    recommendation_source_available = str(recommendation.get("operational_alert_source_available", "true")).lower() != "false"
    if not recommendation_source_available:
        source_missing = recommendation.get("source_missing", "recommendation_run_logs")
        skipped.append(f"RECOMMENDATION_RUN_FAILURE_RATE: source unavailable {source_missing}")
    else:
        error_10m = as_int(recommendation, "recommendation_error_10m")
        no_candidates_30m = as_int(recommendation, "recommendation_no_candidates_30m")
        failure_rate_30m = as_float(recommendation, "recommendation_failure_rate_30m_pct")
        avg_duration_30m = as_float(recommendation, "recommendation_avg_duration_ms_30m")
        traffic_without_success_1h = str(recommendation.get("recommendation_traffic_without_success_1h", "false")).lower() == "true"
        if error_10m >= recommendation_crit_error_10m:
            add_critical("RECOMMENDATION_RUN_FAILURE_RATE", f"ERROR 10m {error_10m} >= {recommendation_crit_error_10m}")
        elif failure_rate_30m >= recommendation_crit_failure_rate_pct:
            add_critical("RECOMMENDATION_RUN_FAILURE_RATE", f"30m failure rate {failure_rate_30m:.2f}% >= {recommendation_crit_failure_rate_pct:.2f}%")
        elif traffic_without_success_1h:
            add_critical("RECOMMENDATION_RUN_FAILURE_RATE", "traffic observed but no SUCCESS run in 1h")
        elif error_10m > 0 or no_candidates_30m > 0:
            add_warning("RECOMMENDATION_RUN_FAILURE_RATE", f"error_10m={error_10m} no_candidates_30m={no_candidates_30m}")
        elif failure_rate_30m >= recommendation_warn_failure_rate_pct:
            add_warning("RECOMMENDATION_RUN_FAILURE_RATE", f"30m failure rate {failure_rate_30m:.2f}% >= {recommendation_warn_failure_rate_pct:.2f}%")
        elif avg_duration_30m >= recommendation_warn_duration_ms:
            add_warning("RECOMMENDATION_RUN_FAILURE_RATE", f"30m avg duration {avg_duration_30m:.0f}ms >= {recommendation_warn_duration_ms:.0f}ms")
else:
    skipped.append("RECOMMENDATION_RUN_FAILURE_RATE: set RECOMMENDATION_RUN_SUMMARY to evaluate DB/API recommendation run metrics")

# NOTIFICATION_RETRY_BACKLOG
retryable_total = as_int(notification, "retryable_failed_total")
retryable_due_now = as_int(notification, "retryable_failed_due_now")
terminal_total = as_int(notification, "terminal_failed_total")
if terminal_total > 0:
    add_critical("NOTIFICATION_RETRY_BACKLOG", f"terminal_failed_total {terminal_total} > 0")
elif retryable_total >= notification_crit_retryable_total:
    add_critical("NOTIFICATION_RETRY_BACKLOG", f"retryable_failed_total {retryable_total} >= {notification_crit_retryable_total}")
elif retryable_due_now >= notification_crit_due_now:
    add_critical("NOTIFICATION_RETRY_BACKLOG", f"retryable_failed_due_now {retryable_due_now} >= {notification_crit_due_now}")
elif retryable_due_now > 0:
    add_warning("NOTIFICATION_RETRY_BACKLOG", f"retryable_failed_due_now {retryable_due_now} > 0")
elif retryable_total >= notification_warn_retryable_total:
    add_warning("NOTIFICATION_RETRY_BACKLOG", f"retryable_failed_total {retryable_total} >= {notification_warn_retryable_total}")

# WEB_PUSH_DISABLED_RATIO, optional DB/API artifact.
if web_push:
    web_push_source_available = str(web_push.get("operational_alert_source_available", "true")).lower() != "false"
    if not web_push_source_available:
        source_missing = web_push.get("source_missing", "web_push_subscriptions,notification_attempt_logs")
        skipped.append(f"WEB_PUSH_DISABLED_RATIO: source unavailable {source_missing}")
    else:
        subscription_total = as_int(web_push, "web_push_subscription_total")
        disabled_ratio = as_float(web_push, "web_push_disabled_ratio_pct")
        disabled_or_gateway_failure_15m = as_int(web_push, "web_push_disabled_or_gateway_failure_15m")
        disabled_attempt_multiplier_1h = as_float(web_push, "notification_disabled_attempt_multiplier_1h")
        if subscription_total >= web_push_warn_min_subscriptions and disabled_ratio >= web_push_crit_disabled_ratio_pct:
            add_critical("WEB_PUSH_DISABLED_RATIO", f"disabled ratio {disabled_ratio:.2f}% >= {web_push_crit_disabled_ratio_pct:.2f}%")
        elif disabled_or_gateway_failure_15m >= web_push_crit_failure_15m:
            add_critical("WEB_PUSH_DISABLED_RATIO", f"15m disabled/gateway failures {disabled_or_gateway_failure_15m} >= {web_push_crit_failure_15m}")
        elif subscription_total >= web_push_warn_min_subscriptions and disabled_ratio >= web_push_warn_disabled_ratio_pct:
            add_warning("WEB_PUSH_DISABLED_RATIO", f"disabled ratio {disabled_ratio:.2f}% >= {web_push_warn_disabled_ratio_pct:.2f}%")
        elif disabled_attempt_multiplier_1h >= 3:
            add_warning("WEB_PUSH_DISABLED_RATIO", f"disabled outcome multiplier {disabled_attempt_multiplier_1h:.2f} >= 3.00")
else:
    skipped.append("WEB_PUSH_DISABLED_RATIO: set WEB_PUSH_SUMMARY to evaluate DB/API web push metrics")

# DB_AUDIT_INTEGRITY, optional DB audit artifact.
if db_audit:
    db_audit_source_available = str(db_audit.get("operational_alert_source_available", "true")).lower() != "false"
    if not db_audit_source_available:
        source_missing = db_audit.get("source_missing", "operational-db-audit")
        skipped.append(f"DB_AUDIT_INTEGRITY: source unavailable {source_missing}")
    else:
        projection_drift = (
            as_int(db_audit, "auth_without_users")
            + as_int(db_audit, "profiles_without_users")
            + as_int(db_audit, "pii_without_users")
        )
        active_users_without_pii = as_int(db_audit, "active_users_without_pii")
        chat_orphans = as_int(db_audit, "chat_snapshots_nonnull_orphan_session")
        pii_sync_pending_or_failed = as_int(db_audit, "user_pii_sync_pending_or_failed")
        notification_failed_like = as_int(db_audit, "notification_failed_like")
        waiting_locks = as_int(db_audit, "waiting_locks")
        active_queries_over_5m = as_int(db_audit, "active_queries_over_5m")
        if projection_drift > 0:
            add_critical("DB_AUDIT_INTEGRITY", f"user projection drift rows {projection_drift} > 0")
        elif active_users_without_pii > 0:
            add_critical("DB_AUDIT_INTEGRITY", f"active users without PII {active_users_without_pii} > 0")
        elif chat_orphans > 0:
            add_critical("DB_AUDIT_INTEGRITY", f"nonnull chat snapshot orphan rows {chat_orphans} > 0")
        elif waiting_locks >= db_audit_crit_waiting_locks:
            add_critical("DB_AUDIT_INTEGRITY", f"waiting locks {waiting_locks} >= {db_audit_crit_waiting_locks}")
        elif pii_sync_pending_or_failed > 0:
            add_warning("DB_AUDIT_INTEGRITY", f"PII sync pending/failed rows {pii_sync_pending_or_failed} > 0")
        elif notification_failed_like > 0:
            add_warning("DB_AUDIT_INTEGRITY", f"notification failed-like attempts {notification_failed_like} > 0")
        elif active_queries_over_5m >= db_audit_warn_active_queries_over_5m:
            add_warning("DB_AUDIT_INTEGRITY", f"active queries over 5m {active_queries_over_5m} >= {db_audit_warn_active_queries_over_5m}")
else:
    skipped.append("DB_AUDIT_INTEGRITY: set OPERATIONAL_DB_AUDIT_SUMMARY to evaluate DB audit metrics")

status = "critical" if critical else "warning" if warning else "ok"
print(f"OP_ALERT_STATUS={status}")
for item in critical:
    print(f"critical={item}")
for item in warning:
    print(f"warning={item}")
for item in skipped:
    print(f"skipped={item}")
for path in observed:
    print(f"artifact={path}")

raise SystemExit(2 if critical else 0)
PY
