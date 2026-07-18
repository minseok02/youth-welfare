#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"
CHAT_SEMANTIC_QUERY_AUDIT_ROOT="${CHAT_SEMANTIC_QUERY_AUDIT_ROOT:-${ROOT_DIR}/tmp/chat-semantic-query-duplicate-audit}"
RUN_TS_UTC="${RUN_TS_UTC:-$(smoke_now_ts_utc)}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${CHAT_SEMANTIC_QUERY_AUDIT_ROOT}/${RUN_TS_UTC}}"
LATEST_ARTIFACT_LINK="${LATEST_ARTIFACT_LINK:-${CHAT_SEMANTIC_QUERY_AUDIT_ROOT}/latest}"
WINDOW_DAYS_CSV="${WINDOW_DAYS_CSV:-1,7,30}"
TOP_GROUP_LIMIT="${TOP_GROUP_LIMIT:-20}"
MIN_DECISION_SAMPLE_COUNT="${MIN_DECISION_SAMPLE_COUNT:-30}"

SUMMARY_OUT="${ARTIFACT_DIR}/chat-semantic-query-duplicate-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/chat-semantic-query-duplicate.json"
NOTE_OUT="${ARTIFACT_DIR}/chat-semantic-query-duplicate-note.md"
WINDOW_ROWS="${ARTIFACT_DIR}/chat-semantic-query-window.tsv"
GROUP_ROWS="${ARTIFACT_DIR}/chat-semantic-query-duplicate-groups.tsv"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  smoke_publish_dir_snapshot "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  smoke_update_links \
    "${SUMMARY_OUT}" "${CHAT_SEMANTIC_QUERY_AUDIT_ROOT}/latest-chat-semantic-query-duplicate-summary.txt" \
    "${JSON_OUT}" "${CHAT_SEMANTIC_QUERY_AUDIT_ROOT}/latest-chat-semantic-query-duplicate.json" \
    "${NOTE_OUT}" "${CHAT_SEMANTIC_QUERY_AUDIT_ROOT}/latest-chat-semantic-query-duplicate-note.md"
  if [[ "${KEEP_ARTIFACTS}" != "true" ]]; then
    rm -rf "${ARTIFACT_DIR}"
  fi
}
trap cleanup EXIT

smoke_require_command python3
mkdir -p "${ARTIFACT_DIR}"

WINDOW_VALUES="$(
  python3 - "${WINDOW_DAYS_CSV}" <<'PY'
import sys

values = []
for token in sys.argv[1].split(","):
    token = token.strip()
    if not token:
        continue
    value = int(token)
    if value <= 0:
        raise SystemExit("WINDOW_DAYS_CSV values must be positive")
    values.append(value)

if not values:
    raise SystemExit("WINDOW_DAYS_CSV must contain at least one positive value")

print(",".join(f"({value})" for value in sorted(set(values))))
PY
)"

smoke_print_step "chat semantic query duplicate window metrics"
smoke_db_query "
    with windows(days) as (
      values ${WINDOW_VALUES}
    ),
    bucket_defs(bucket) as (
      values ('ALL'), ('EVALUATION'), ('NON_EVALUATION'), ('INTERACTIVE')
    ),
    base_snapshots as (
      select w.days,
             crs.id,
             crs.snapshot_type,
             crs.created_at,
             md5(
               lower(regexp_replace(trim(coalesce(crs.question, '')), '[[:space:]]+', ' ', 'g'))
               || '|'
               || coalesce(nullif(crs.preferred_terms_json, ''), '[]')
             ) as query_key
      from windows w
      join chat_retrieval_snapshots crs
        on crs.created_at >= now() - (w.days || ' days')::interval
      where coalesce(nullif(crs.question, ''), '') <> ''
        and coalesce(crs.semantic_service_ids_json, '') not in ('', '[]')
    ),
    semantic_snapshots as (
      select days, 'ALL' as bucket, id, snapshot_type, created_at, query_key
      from base_snapshots
      union all
      select days, 'EVALUATION' as bucket, id, snapshot_type, created_at, query_key
      from base_snapshots
      where snapshot_type = 'EVALUATION'
      union all
      select days, 'NON_EVALUATION' as bucket, id, snapshot_type, created_at, query_key
      from base_snapshots
      where snapshot_type <> 'EVALUATION'
      union all
      select days, 'INTERACTIVE' as bucket, id, snapshot_type, created_at, query_key
      from base_snapshots
      where snapshot_type = 'INTERACTIVE'
    ),
    grouped as (
      select days,
             bucket,
             query_key,
             count(*) as snapshot_count,
             min(created_at) as first_seen_at,
             max(created_at) as last_seen_at
      from semantic_snapshots
      group by days, bucket, query_key
    ),
    group_metrics as (
      select days,
             bucket,
             count(*) filter (where snapshot_count >= 2) as repeated_query_group_count,
             coalesce(max(snapshot_count), 0) as max_repeated_group_count
      from grouped
      group by days, bucket
    )
    select w.days,
           b.bucket,
           count(ss.id) as semantic_snapshot_count,
           count(distinct ss.query_key) as distinct_query_count,
           greatest(count(ss.id) - count(distinct ss.query_key), 0) as duplicate_snapshot_count,
           coalesce(gm.repeated_query_group_count, 0) as repeated_query_group_count,
           coalesce(gm.max_repeated_group_count, 0) as max_repeated_group_count,
           case when count(ss.id) = 0 then 0
                else round(((count(ss.id) - count(distinct ss.query_key))::numeric * 100) / count(ss.id), 2)
           end as duplicate_snapshot_rate_pct
    from windows w
    cross join bucket_defs b
    left join semantic_snapshots ss on ss.days = w.days and ss.bucket = b.bucket
    left join group_metrics gm on gm.days = w.days and gm.bucket = b.bucket
    group by w.days, b.bucket, gm.repeated_query_group_count, gm.max_repeated_group_count
    order by w.days,
             case b.bucket
               when 'ALL' then 1
               when 'NON_EVALUATION' then 2
               when 'INTERACTIVE' then 3
               when 'EVALUATION' then 4
               else 9
             end;
  " > "${WINDOW_ROWS}"

smoke_print_step "chat semantic query duplicate top groups"
smoke_db_query "
    with base_snapshots as (
      select snapshot_type,
             session_id,
             user_key,
             coalesce(nullif(scenario_key, ''), '(none)') as scenario_key,
             md5(
               lower(regexp_replace(trim(coalesce(question, '')), '[[:space:]]+', ' ', 'g'))
               || '|'
               || coalesce(nullif(preferred_terms_json, ''), '[]')
             ) as query_key,
             created_at,
             coalesce(nullif(preferred_terms_json, ''), '[]') as preferred_terms_json
      from chat_retrieval_snapshots
      where created_at >= now() - '30 days'::interval
        and coalesce(nullif(question, ''), '') <> ''
        and coalesce(semantic_service_ids_json, '') not in ('', '[]')
    ),
    semantic_snapshots as (
      select 'ALL' as bucket, *
      from base_snapshots
      union all
      select 'EVALUATION' as bucket, *
      from base_snapshots
      where snapshot_type = 'EVALUATION'
      union all
      select 'NON_EVALUATION' as bucket, *
      from base_snapshots
      where snapshot_type <> 'EVALUATION'
      union all
      select 'INTERACTIVE' as bucket, *
      from base_snapshots
      where snapshot_type = 'INTERACTIVE'
    ),
    grouped as (
      select bucket,
             query_key,
             count(*) as snapshot_count,
             count(distinct session_id) as session_count,
             count(distinct user_key) as user_count,
             count(*) filter (where scenario_key <> '(none)') as scenario_row_count,
             to_char(min(created_at) at time zone 'UTC', 'YYYY-MM-DD HH24:MI:SS') as first_seen_utc,
             to_char(max(created_at) at time zone 'UTC', 'YYYY-MM-DD HH24:MI:SS') as last_seen_utc,
             count(distinct preferred_terms_json) as preferred_terms_variants
      from semantic_snapshots
      group by bucket, query_key
      having count(*) >= 2
    ),
    ranked as (
      select *,
             row_number() over (
               partition by bucket
               order by snapshot_count desc, last_seen_utc desc, query_key
             ) as row_number
      from grouped
    )
    select bucket,
           query_key,
           snapshot_count,
           session_count,
           user_count,
           scenario_row_count,
           first_seen_utc,
           last_seen_utc,
           preferred_terms_variants
    from ranked
    where row_number <= ${TOP_GROUP_LIMIT}
    order by case bucket
               when 'ALL' then 1
               when 'NON_EVALUATION' then 2
               when 'INTERACTIVE' then 3
               when 'EVALUATION' then 4
               else 9
             end,
             snapshot_count desc,
             last_seen_utc desc,
             query_key;
  " > "${GROUP_ROWS}"

python3 - \
  "${WINDOW_ROWS}" \
  "${GROUP_ROWS}" \
  "${SUMMARY_OUT}" \
  "${JSON_OUT}" \
  "${NOTE_OUT}" \
  "${ARTIFACT_DIR}" \
  "${RUN_TS_UTC}" \
  "${WINDOW_DAYS_CSV}" \
  "${MIN_DECISION_SAMPLE_COUNT}" <<'PY'
import csv
import json
import sys
from pathlib import Path

window_rows_path = Path(sys.argv[1])
group_rows_path = Path(sys.argv[2])
summary_out = Path(sys.argv[3])
json_out = Path(sys.argv[4])
note_out = Path(sys.argv[5])
artifact_dir = sys.argv[6]
generated_at_utc = sys.argv[7]
window_days_csv = sys.argv[8]
min_decision_sample_count = int(sys.argv[9])


def read_tsv(path, fields):
    rows = []
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        if not raw_line.strip():
            continue
        values = raw_line.split("\t")
        if len(values) < len(fields):
            values = values + [""] * (len(fields) - len(values))
        rows.append(dict(zip(fields, values)))
    return rows


window_rows = read_tsv(window_rows_path, [
    "days",
    "bucket",
    "semantic_snapshot_count",
    "distinct_query_count",
    "duplicate_snapshot_count",
    "repeated_query_group_count",
    "max_repeated_group_count",
    "duplicate_snapshot_rate_pct",
])
group_rows = read_tsv(group_rows_path, [
    "bucket",
    "query_key",
    "snapshot_count",
    "session_count",
    "user_count",
    "scenario_row_count",
    "first_seen_utc",
    "last_seen_utc",
    "preferred_terms_variants",
])

window_by_key = {(row["days"], row["bucket"]): row for row in window_rows}
available_days = sorted({row["days"] for row in window_rows}, key=lambda value: int(value))
primary_days = "7" if ("7", "NON_EVALUATION") in window_by_key else (available_days[-1] if available_days else "")
decision_bucket = "NON_EVALUATION"
primary = window_by_key.get((primary_days, decision_bucket), {})
if int(primary.get("semantic_snapshot_count") or 0) == 0:
    decision_bucket = "INTERACTIVE"
    primary = window_by_key.get((primary_days, decision_bucket), {})
if int(primary.get("semantic_snapshot_count") or 0) == 0:
    decision_bucket = "ALL"
    primary = window_by_key.get((primary_days, decision_bucket), {})
semantic_snapshot_count = int(primary.get("semantic_snapshot_count") or 0)
duplicate_rate = float(primary.get("duplicate_snapshot_rate_pct") or 0)

decision_class = "CACHE_NOT_JUSTIFIED_YET"
operator_reading = "Observed duplicate semantic-query proxy traffic is too small to justify a cache change."
if semantic_snapshot_count == 0:
    decision_class = "SEMANTIC_TRAFFIC_EMPTY"
    operator_reading = "No non-empty semantic snapshot traffic was observed in the primary window."
elif semantic_snapshot_count < min_decision_sample_count:
    if duplicate_rate >= 25:
        decision_class = "CACHE_WATCHLIST"
        operator_reading = (
            "Non-evaluation duplicate semantic-query proxy traffic is high, "
            "but the primary sample is still too small for a cache rollout decision."
        )
    elif duplicate_rate >= 10:
        decision_class = "CACHE_WATCHLIST"
        operator_reading = "Repeated semantic-query proxy traffic exists, but the primary sample is thin."
elif duplicate_rate >= 25:
    decision_class = "CACHE_CANDIDATE"
    operator_reading = "Non-evaluation repeated semantic-query proxy traffic is high enough to evaluate a bounded embedding cache."
elif duplicate_rate >= 10:
    decision_class = "CACHE_WATCHLIST"
    operator_reading = "Repeated semantic-query proxy traffic exists, but should be watched before changing runtime behavior."

summary_lines = [
    "chat_semantic_query_duplicate_audit=passed",
    f"artifact_dir={artifact_dir}",
    f"generated_at_utc={generated_at_utc}",
    f"window_days_csv={window_days_csv}",
    f"primary_window_days={primary_days}",
    f"decision_basis_bucket={decision_bucket}",
    f"min_decision_sample_count={min_decision_sample_count}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    "query_key_source=md5(normalized_question|preferred_terms_json)",
    "raw_question_exported=false",
    "limitations=DB snapshot proxy includes only rows with non-empty semantic_service_ids_json; deployed app logs now emit exact semantic queryHash for future runs",
]
for row in window_rows:
    bucket_key = row["bucket"].lower()
    prefix = f"window_{row['days']}d_{bucket_key}"
    for key, value in row.items():
        if key in {"days", "bucket"}:
            continue
        summary_lines.append(f"{prefix}_{key}={value}")

summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": artifact_dir,
    "generated_at_utc": generated_at_utc,
    "window_days_csv": window_days_csv,
    "primary_window_days": primary_days,
    "decision_basis_bucket": decision_bucket,
    "min_decision_sample_count": min_decision_sample_count,
    "decision_class": decision_class,
    "operator_reading": operator_reading,
    "query_key_source": "md5(normalized_question|preferred_terms_json)",
    "raw_question_exported": False,
    "limitations": [
        "DB snapshot proxy includes only rows with non-empty semantic_service_ids_json.",
        "The app now emits exact semantic queryHash in ChatSemanticSearchTiming logs for future runs.",
    ],
    "window_metrics": window_rows,
    "top_duplicate_groups": group_rows,
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Chat Semantic Query Duplicate Audit",
    "",
    f"- decision_class: `{decision_class}`",
    f"- primary_window_days: `{primary_days}`",
    f"- decision_basis_bucket: `{decision_bucket}`",
    f"- min_decision_sample_count: `{min_decision_sample_count}`",
    f"- operator_reading: {operator_reading}",
    "- raw_question_exported: `false`",
    "- query_key_source: `md5(normalized_question|preferred_terms_json)`",
    "- limitation: DB snapshot proxy misses semantic calls that returned empty IDs; exact `queryHash` is available from app logs after this observability deploy.",
    "",
    "## Window Metrics",
    "",
]
for row in window_rows:
    note_lines.append(
        "- {days}d {bucket}: semantic_snapshots={semantic_snapshot_count}, distinct={distinct_query_count}, "
        "duplicates={duplicate_snapshot_count}, duplicate_rate={duplicate_snapshot_rate_pct}%, "
        "repeated_groups={repeated_query_group_count}, max_group={max_repeated_group_count}".format(**row)
    )

note_out.write_text("\n".join(note_lines) + "\n", encoding="utf-8")
PY

cat "${SUMMARY_OUT}"
