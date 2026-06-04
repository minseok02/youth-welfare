#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/notification-stale-target-audit}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

SUMMARY_OUT="${ARTIFACT_DIR}/notification-stale-target-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/notification-stale-target-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/notification-stale-target-note.md"
RAW_METRICS_OUT="${ARTIFACT_DIR}/notification-stale-target-metrics.tsv"
RAW_SAMPLES_OUT="${ARTIFACT_DIR}/notification-stale-target-samples.tsv"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-notification-stale-target-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-notification-stale-target-summary.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-notification-stale-target-note.md"

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
with stale as (
  select *
  from user_alerts
  where status = 'UNREAD'
    and created_at < now() - interval '14 days'
), grouped as (
  select kind,
         coalesce(deeplink_url, '') as deeplink_url,
         title,
         count(*) as row_count,
         count(distinct user_key) as distinct_users,
         min(created_at) as oldest_created_at,
         max(created_at) as newest_created_at
  from stale
  group by kind, coalesce(deeplink_url, ''), title
)
select 'stale_14d_total', coalesce(sum(row_count), 0)::text from grouped
union all
select 'stale_14d_groups', count(*)::text from grouped
union all
select 'deadline_groups', count(*) filter (where kind = 'DEADLINE_REMINDER')::text from grouped
union all
select 'digest_groups', count(*) filter (where kind = 'RECOMMENDATION_DIGEST')::text from grouped
union all
select 'max_rows_single_target', coalesce(max(row_count), 0)::text from grouped
union all
select 'max_users_single_target', coalesce(max(distinct_users), 0)::text from grouped;
" > "${RAW_METRICS_OUT}"

smoke_db_query "
with stale as (
  select id,
         user_key,
         kind,
         title,
         coalesce(deeplink_url, '') as deeplink_url,
         coalesce(metadata_json, '') as metadata_json,
         created_at
  from user_alerts
  where status = 'UNREAD'
    and created_at < now() - interval '14 days'
), grouped as (
  select kind,
         title,
         deeplink_url,
         count(*) as row_count,
         count(distinct user_key) as distinct_users,
         min(created_at) as oldest_created_at,
         max(created_at) as newest_created_at
  from stale
  group by kind, title, deeplink_url
)
select kind,
       replace(title, E'\t', ' '),
       replace(deeplink_url, E'\t', ' '),
       row_count,
       distinct_users,
       to_char(oldest_created_at, 'YYYY-MM-DD\"T\"HH24:MI:SS'),
       to_char(newest_created_at, 'YYYY-MM-DD\"T\"HH24:MI:SS')
from grouped
order by row_count desc, distinct_users desc, kind, title
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
        metrics[row[0]] = int(row[1])

samples = []
with samples_path.open(encoding="utf-8") as f:
    for row in csv.reader(f, delimiter="\t"):
        if not row:
            continue
        samples.append({
            "kind": row[0],
            "title": row[1],
            "deeplinkUrl": row[2],
            "rowCount": int(row[3]),
            "distinctUsers": int(row[4]),
            "oldestCreatedAt": row[5],
            "newestCreatedAt": row[6],
        })

top = samples[0] if samples else None
if top and top["kind"] == "DEADLINE_REMINDER":
    decision_class = "STALE_DEADLINE_TARGET_CLUSTER_REVIEW"
    operator_reading = "장기 unread가 특정 deadline target에 몰려 있어, 해당 정책 reminder를 먼저 triage 하는 편이 맞습니다."
elif top:
    decision_class = "STALE_DIGEST_TARGET_CLUSTER_REVIEW"
    operator_reading = "장기 unread가 digest target에 몰려 있어 digest cadence를 먼저 review 해야 합니다."
else:
    decision_class = "NO_STALE_TARGETS"
    operator_reading = "2주 이상 unread target cluster가 없습니다."

summary_lines = [
    "notification_stale_target_audit=passed",
    f"artifact_dir={artifact_dir}",
    f"stale_14d_total={metrics['stale_14d_total']}",
    f"stale_14d_groups={metrics['stale_14d_groups']}",
    f"deadline_groups={metrics['deadline_groups']}",
    f"digest_groups={metrics['digest_groups']}",
    f"max_rows_single_target={metrics['max_rows_single_target']}",
    f"max_users_single_target={metrics['max_users_single_target']}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    "next_action=docs/core/notification-stale-target-audit-runbook.md",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": artifact_dir,
    "metrics": metrics,
    "samples": samples,
    "decisionClass": decision_class,
    "operatorReading": operator_reading,
    "nextAction": "docs/core/notification-stale-target-audit-runbook.md",
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Notification Stale Target Audit",
    "",
    f"- `decision_class`: `{decision_class}`",
    f"- `stale_14d_total`: `{metrics['stale_14d_total']}`",
    f"- `deadline_groups`: `{metrics['deadline_groups']}`",
    f"- `digest_groups`: `{metrics['digest_groups']}`",
    "",
    "## Operator Reading",
    "",
    operator_reading,
    "",
    "## Target Clusters",
    "",
]
for sample in samples[:10]:
    note_lines.append(
        f"- `{sample['kind']}` / `{sample['title']}` / `{sample['deeplinkUrl'] or '(empty)'}` / "
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
