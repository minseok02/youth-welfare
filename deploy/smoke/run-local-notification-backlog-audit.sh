#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/notification-backlog-audit}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

SUMMARY_OUT="${ARTIFACT_DIR}/notification-backlog-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/notification-backlog-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/notification-backlog-note.md"
RAW_METRICS_OUT="${ARTIFACT_DIR}/notification-backlog-metrics.tsv"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-notification-backlog-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-notification-backlog-summary.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-notification-backlog-note.md"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
mkdir -p "${ARTIFACT_DIR}"
smoke_require_command python3

smoke_db_query "
with alert_summary as (
  select
    count(*) filter (where status = 'UNREAD') as unread_total,
    count(*) filter (where status = 'UNREAD' and kind = 'RECOMMENDATION_DIGEST') as unread_digest,
    count(*) filter (where status = 'UNREAD' and kind = 'DEADLINE_REMINDER') as unread_deadline,
    count(*) filter (where status = 'UNREAD' and kind = 'SYSTEM') as unread_system,
    count(*) filter (where status = 'UNREAD' and created_at < now() - interval '7 days') as stale_unread_7d,
    count(*) filter (where status = 'UNREAD' and created_at < now() - interval '14 days') as stale_unread_14d,
    min(created_at) filter (where status = 'UNREAD') as oldest_unread_created_at,
    max(created_at) filter (where status = 'UNREAD') as newest_unread_created_at
  from user_alerts
), notification_summary as (
  select
    count(*) filter (where status = 'FAILED' and next_retry_at is not null) as retryable_failed_total,
    count(*) filter (where status = 'FAILED' and next_retry_at is not null and next_retry_at <= now()) as retryable_failed_due_now,
    count(*) filter (where status = 'FAILED' and next_retry_at is not null and next_retry_at > now()) as retryable_failed_scheduled_later,
    count(*) filter (where status = 'FAILED' and next_retry_at is null) as terminal_failed_total,
    count(*) filter (where status = 'FAILED' and channel = 'email' and next_retry_at is not null) as retryable_failed_email,
    count(*) filter (where status = 'FAILED' and channel = 'kakao' and next_retry_at is not null) as retryable_failed_kakao,
    count(*) filter (where status = 'FAILED' and channel = 'email' and next_retry_at is null) as terminal_failed_email,
    count(*) filter (where status = 'FAILED' and channel = 'kakao' and next_retry_at is null) as terminal_failed_kakao,
    count(*) filter (where status = 'SENT' and sent_at >= now() - interval '24 hours') as sent_last_24h,
    count(*) filter (where status = 'SENT' and sent_at >= now() - interval '7 days') as sent_last_7d,
    min(next_retry_at) filter (where status = 'FAILED' and next_retry_at is not null) as earliest_retry_at
  from notifications
)
select 'unread_total', coalesce(unread_total, 0)::text from alert_summary
union all
select 'unread_digest', coalesce(unread_digest, 0)::text from alert_summary
union all
select 'unread_deadline', coalesce(unread_deadline, 0)::text from alert_summary
union all
select 'unread_system', coalesce(unread_system, 0)::text from alert_summary
union all
select 'stale_unread_7d', coalesce(stale_unread_7d, 0)::text from alert_summary
union all
select 'stale_unread_14d', coalesce(stale_unread_14d, 0)::text from alert_summary
union all
select 'oldest_unread_created_at', coalesce(to_char(oldest_unread_created_at, 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') from alert_summary
union all
select 'newest_unread_created_at', coalesce(to_char(newest_unread_created_at, 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') from alert_summary
union all
select 'retryable_failed_total', coalesce(retryable_failed_total, 0)::text from notification_summary
union all
select 'retryable_failed_due_now', coalesce(retryable_failed_due_now, 0)::text from notification_summary
union all
select 'retryable_failed_scheduled_later', coalesce(retryable_failed_scheduled_later, 0)::text from notification_summary
union all
select 'terminal_failed_total', coalesce(terminal_failed_total, 0)::text from notification_summary
union all
select 'retryable_failed_email', coalesce(retryable_failed_email, 0)::text from notification_summary
union all
select 'retryable_failed_kakao', coalesce(retryable_failed_kakao, 0)::text from notification_summary
union all
select 'terminal_failed_email', coalesce(terminal_failed_email, 0)::text from notification_summary
union all
select 'terminal_failed_kakao', coalesce(terminal_failed_kakao, 0)::text from notification_summary
union all
select 'sent_last_24h', coalesce(sent_last_24h, 0)::text from notification_summary
union all
select 'sent_last_7d', coalesce(sent_last_7d, 0)::text from notification_summary
union all
select 'earliest_retry_at', coalesce(to_char(earliest_retry_at, 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') from notification_summary;
" > "${RAW_METRICS_OUT}"

python3 - "${RAW_METRICS_OUT}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" <<'PY'
import csv
import json
import sys
from pathlib import Path

metrics_path = Path(sys.argv[1])
summary_out = Path(sys.argv[2])
json_out = Path(sys.argv[3])
note_out = Path(sys.argv[4])
artifact_dir = sys.argv[5]

int_keys = {
    "unread_total",
    "unread_digest",
    "unread_deadline",
    "unread_system",
    "stale_unread_7d",
    "stale_unread_14d",
    "retryable_failed_total",
    "retryable_failed_due_now",
    "retryable_failed_scheduled_later",
    "terminal_failed_total",
    "retryable_failed_email",
    "retryable_failed_kakao",
    "terminal_failed_email",
    "terminal_failed_kakao",
    "sent_last_24h",
    "sent_last_7d",
}
metrics = {}
with metrics_path.open(encoding="utf-8") as f:
    for row in csv.reader(f, delimiter="\t"):
        if not row:
            continue
        key, value = row[0], row[1]
        metrics[key] = int(value) if key in int_keys else (value or None)

if metrics["terminal_failed_total"] > 0:
    decision_class = "TERMINAL_NOTIFICATION_FAILURE_PRIORITY"
    operator_reading = "종결 실패 알림이 남아 있어 재시도보다 근본 원인 확인이 우선입니다."
elif metrics["retryable_failed_due_now"] > 0:
    decision_class = "RETRYABLE_NOTIFICATION_RETRY_PRIORITY"
    operator_reading = "재시도 대기 알림이 만기 상태입니다. retry runner 또는 채널 상태를 먼저 확인합니다."
elif metrics["stale_unread_7d"] > 0:
    decision_class = "STALE_UNREAD_ALERT_REVIEW_PRIORITY"
    operator_reading = "장기 미열람 알림이 남아 있어 unread backlog triage가 우선입니다."
elif metrics["unread_total"] > 0:
    decision_class = "UNREAD_ALERT_BACKLOG"
    operator_reading = "실패 알림은 없지만 안 읽은 알림이 누적돼 있습니다."
else:
    decision_class = "BASELINE_HEALTHY"
    operator_reading = "현재 unread/failed 알림 backlog는 운영 기준선 안에 있습니다."

summary_lines = [
    "notification_backlog_audit=passed",
    f"artifact_dir={artifact_dir}",
    f"unread_total={metrics['unread_total']}",
    f"unread_digest={metrics['unread_digest']}",
    f"unread_deadline={metrics['unread_deadline']}",
    f"unread_system={metrics['unread_system']}",
    f"stale_unread_7d={metrics['stale_unread_7d']}",
    f"stale_unread_14d={metrics['stale_unread_14d']}",
    f"oldest_unread_created_at={metrics.get('oldest_unread_created_at') or ''}",
    f"retryable_failed_total={metrics['retryable_failed_total']}",
    f"retryable_failed_due_now={metrics['retryable_failed_due_now']}",
    f"retryable_failed_scheduled_later={metrics['retryable_failed_scheduled_later']}",
    f"terminal_failed_total={metrics['terminal_failed_total']}",
    f"retryable_failed_email={metrics['retryable_failed_email']}",
    f"retryable_failed_kakao={metrics['retryable_failed_kakao']}",
    f"terminal_failed_email={metrics['terminal_failed_email']}",
    f"terminal_failed_kakao={metrics['terminal_failed_kakao']}",
    f"sent_last_24h={metrics['sent_last_24h']}",
    f"sent_last_7d={metrics['sent_last_7d']}",
    f"earliest_retry_at={metrics.get('earliest_retry_at') or ''}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    "next_action=docs/core/notification-backlog-audit-runbook.md",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": artifact_dir,
    "metrics": metrics,
    "decisionClass": decision_class,
    "operatorReading": operator_reading,
    "nextAction": "docs/core/notification-backlog-audit-runbook.md",
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Notification Backlog Audit",
    "",
    f"- `decision_class`: `{decision_class}`",
    f"- `unread_total`: `{metrics['unread_total']}`",
    f"- `stale_unread_7d`: `{metrics['stale_unread_7d']}`",
    f"- `retryable_failed_total`: `{metrics['retryable_failed_total']}`",
    f"- `terminal_failed_total`: `{metrics['terminal_failed_total']}`",
    "",
    "## Operator Reading",
    "",
    operator_reading,
    "",
    "## Breakdown",
    "",
    f"- unread digest: `{metrics['unread_digest']}`",
    f"- unread deadline: `{metrics['unread_deadline']}`",
    f"- unread system: `{metrics['unread_system']}`",
    f"- retryable email: `{metrics['retryable_failed_email']}`",
    f"- retryable kakao: `{metrics['retryable_failed_kakao']}`",
    f"- terminal email: `{metrics['terminal_failed_email']}`",
    f"- terminal kakao: `{metrics['terminal_failed_kakao']}`",
    "",
    "## Timing",
    "",
    f"- oldest unread: `{metrics.get('oldest_unread_created_at') or '(none)'}`",
    f"- earliest retry: `{metrics.get('earliest_retry_at') or '(none)'}`",
    f"- sent last 24h: `{metrics['sent_last_24h']}`",
    f"- sent last 7d: `{metrics['sent_last_7d']}`",
]
note_out.write_text("\n".join(note_lines) + "\n", encoding="utf-8")
PY

smoke_publish_dir_snapshot "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
smoke_publish_file "${SUMMARY_OUT}" "${LATEST_SUMMARY_LINK}"
smoke_publish_file "${JSON_OUT}" "${LATEST_JSON_LINK}"
smoke_publish_file "${NOTE_OUT}" "${LATEST_NOTE_LINK}"

cat "${SUMMARY_OUT}"
echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
echo "latest_note_link=${LATEST_NOTE_LINK}"
