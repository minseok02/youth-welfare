#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/notification-backlog-sample-audit}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

SUMMARY_OUT="${ARTIFACT_DIR}/notification-backlog-sample-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/notification-backlog-sample-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/notification-backlog-sample-note.md"
RAW_METRICS_OUT="${ARTIFACT_DIR}/notification-backlog-sample-metrics.tsv"
RAW_SAMPLES_OUT="${ARTIFACT_DIR}/notification-backlog-sample-samples.tsv"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-notification-backlog-sample-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-notification-backlog-sample-summary.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-notification-backlog-sample-note.md"

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
with unread as (
  select *
  from user_alerts
  where status = 'UNREAD'
)
select 'unread_total', count(*)::text from unread
union all
select 'unique_users_with_unread', count(distinct user_key)::text from unread
union all
select 'digest_unread', count(*) filter (where kind = 'RECOMMENDATION_DIGEST')::text from unread
union all
select 'deadline_unread', count(*) filter (where kind = 'DEADLINE_REMINDER')::text from unread
union all
select 'system_unread', count(*) filter (where kind = 'SYSTEM')::text from unread
union all
select 'users_with_2plus_unread', count(*)::text
  from (
    select user_key
    from unread
    group by user_key
    having count(*) >= 2
  ) grouped
union all
select 'users_with_5plus_unread', count(*)::text
  from (
    select user_key
    from unread
    group by user_key
    having count(*) >= 5
  ) grouped
union all
select 'stale_unread_7d', count(*) filter (where created_at < now() - interval '7 days')::text from unread
union all
select 'stale_unread_14d', count(*) filter (where created_at < now() - interval '14 days')::text from unread
union all
select 'oldest_unread_created_at', coalesce(to_char(min(created_at), 'YYYY-MM-DD\"T\"HH24:MI:SS'), '') from unread;
" > "${RAW_METRICS_OUT}"

smoke_db_query "
with unread as (
  select id,
         user_key,
         kind,
         title,
         deeplink_url,
         created_at,
         case
           when created_at < now() - interval '14 days' then 'stale_14d'
           when created_at < now() - interval '7 days' then 'stale_7d'
           else 'recent'
         end as age_bucket
  from user_alerts
  where status = 'UNREAD'
),
sample_titles as (
  select age_bucket,
         kind,
         title,
         count(*) as row_count,
         min(created_at) as oldest_created_at,
         max(created_at) as newest_created_at,
         count(distinct user_key) as distinct_users
  from unread
  group by age_bucket, kind, title
)
select age_bucket,
       kind,
       replace(title, E'\t', ' '),
       row_count,
       distinct_users,
       to_char(oldest_created_at, 'YYYY-MM-DD\"T\"HH24:MI:SS'),
       to_char(newest_created_at, 'YYYY-MM-DD\"T\"HH24:MI:SS')
from sample_titles
order by
  case age_bucket
    when 'stale_14d' then 1
    when 'stale_7d' then 2
    else 3
  end,
  row_count desc,
  title
limit 20;
" > "${RAW_SAMPLES_OUT}"

python3 - "${RAW_METRICS_OUT}" "${RAW_SAMPLES_OUT}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" <<'PY'
import csv
import json
import sys
from pathlib import Path

metrics_path = Path(sys.argv[1])
samples_path = Path(sys.argv[2])
summary_out = Path(sys.argv[3])
json_out = Path(sys.argv[4])
note_out = Path(sys.argv[5])
artifact_dir = sys.argv[6]

metrics = {}
with metrics_path.open(encoding="utf-8") as f:
    for row in csv.reader(f, delimiter="\t"):
        if not row:
            continue
        key, value = row[0], row[1]
        if key == "oldest_unread_created_at":
            metrics[key] = value or None
        else:
            metrics[key] = int(value)

samples = []
with samples_path.open(encoding="utf-8") as f:
    for row in csv.reader(f, delimiter="\t"):
        if not row:
            continue
        samples.append({
            "ageBucket": row[0],
            "kind": row[1],
            "title": row[2],
            "rowCount": int(row[3]),
            "distinctUsers": int(row[4]),
            "oldestCreatedAt": row[5],
            "newestCreatedAt": row[6],
        })

if metrics["stale_unread_14d"] > 0:
    decision_class = "STALE_UNREAD_14D_SAMPLE_REVIEW"
    operator_reading = "2주 이상 미열람 알림이 남아 있어 sample title 기준으로 먼저 triage 해야 합니다."
elif metrics["stale_unread_7d"] > 0:
    decision_class = "STALE_UNREAD_7D_SAMPLE_REVIEW"
    operator_reading = "7일 이상 미열람 알림이 남아 있어 digest/deadline 성격을 나눠 sample review가 필요합니다."
else:
    decision_class = "RECENT_UNREAD_ONLY"
    operator_reading = "장기 미열람은 없고 최근 unread 알림만 남아 있습니다."

summary_lines = [
    "notification_backlog_sample_audit=passed",
    f"artifact_dir={artifact_dir}",
    f"unread_total={metrics['unread_total']}",
    f"unique_users_with_unread={metrics['unique_users_with_unread']}",
    f"digest_unread={metrics['digest_unread']}",
    f"deadline_unread={metrics['deadline_unread']}",
    f"system_unread={metrics['system_unread']}",
    f"users_with_2plus_unread={metrics['users_with_2plus_unread']}",
    f"users_with_5plus_unread={metrics['users_with_5plus_unread']}",
    f"stale_unread_7d={metrics['stale_unread_7d']}",
    f"stale_unread_14d={metrics['stale_unread_14d']}",
    f"oldest_unread_created_at={metrics.get('oldest_unread_created_at') or ''}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    "next_action=docs/core/notification-backlog-sample-audit-runbook.md",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": artifact_dir,
    "metrics": metrics,
    "samples": samples,
    "decisionClass": decision_class,
    "operatorReading": operator_reading,
    "nextAction": "docs/core/notification-backlog-sample-audit-runbook.md",
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Notification Backlog Sample Audit",
    "",
    f"- `decision_class`: `{decision_class}`",
    f"- `unread_total`: `{metrics['unread_total']}`",
    f"- `unique_users_with_unread`: `{metrics['unique_users_with_unread']}`",
    f"- `stale_unread_7d`: `{metrics['stale_unread_7d']}`",
    f"- `stale_unread_14d`: `{metrics['stale_unread_14d']}`",
    "",
    "## Operator Reading",
    "",
    operator_reading,
    "",
    "## Top Sample Titles",
    "",
]
for sample in samples[:12]:
    note_lines.append(
        f"- `{sample['ageBucket']}` / `{sample['kind']}` / `{sample['title']}` / "
        f"`rows={sample['rowCount']}` / `users={sample['distinctUsers']}`"
    )
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
