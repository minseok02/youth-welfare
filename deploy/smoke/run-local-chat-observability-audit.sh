#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"
CHAT_OBSERVABILITY_ROOT="${CHAT_OBSERVABILITY_ROOT:-${ROOT_DIR}/tmp/chat-observability-audit}"
RUN_TS_UTC="${RUN_TS_UTC:-$(smoke_now_ts_utc)}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${CHAT_OBSERVABILITY_ROOT}/${RUN_TS_UTC}}"
WINDOW_DAYS_CSV="${WINDOW_DAYS_CSV:-1,7,30}"
RECENT_SAMPLE_LIMIT="${RECENT_SAMPLE_LIMIT:-12}"

SUMMARY_OUT="${ARTIFACT_DIR}/chat-observability-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/chat-observability.json"
NOTE_OUT="${ARTIFACT_DIR}/chat-observability-note.md"
WINDOW_ROWS="${ARTIFACT_DIR}/chat-window-metrics.tsv"
SNAPSHOT_ROWS="${ARTIFACT_DIR}/chat-snapshot-metrics.tsv"
FALLBACK_ROWS="${ARTIFACT_DIR}/chat-fallback-strategy.tsv"
RECENT_SAMPLES="${ARTIFACT_DIR}/chat-recent-samples.tsv"

cleanup() {
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

smoke_print_step "chat window metrics"
smoke_db_query "
    with windows(days) as (
      values ${WINDOW_VALUES}
    ),
    assistant_messages as (
      select cm.id,
             cm.session_id,
             cm.created_at,
             coalesce(nullif(cm.references_json, ''), '[]')::jsonb as references_json
      from chat_messages cm
      where cm.role = 'ASSISTANT'
    ),
    assistant_metrics as (
      select w.days,
             count(am.id) as assistant_messages,
             coalesce(sum(jsonb_array_length(am.references_json)), 0) as total_reference_count,
             count(*) filter (where jsonb_array_length(am.references_json) > 0) as assistant_messages_with_references,
             count(*) filter (
               where exists (
                 select 1
                 from jsonb_array_elements(am.references_json) ref
                 where jsonb_array_length(coalesce(ref -> 'actionLinks', '[]'::jsonb)) > 0
               )
             ) as assistant_messages_with_action_links
      from windows w
      left join assistant_messages am
        on am.created_at >= now() - (w.days || ' days')::interval
      group by w.days
    ),
    session_metrics as (
      select w.days,
             count(cs.id) as sessions,
             count(cs.id) filter (where cs.context_state_json is not null and length(trim(cs.context_state_json)) > 0) as sessions_with_context
      from windows w
      left join chat_sessions cs
        on cs.created_at >= now() - (w.days || ' days')::interval
      group by w.days
    )
    select am.days,
           sm.sessions,
           sm.sessions_with_context,
           am.assistant_messages,
           am.assistant_messages_with_references,
           am.assistant_messages_with_action_links,
           am.total_reference_count,
           case when am.assistant_messages = 0 then 0 else round((am.assistant_messages_with_references::numeric * 100) / am.assistant_messages, 2) end as reference_rate_pct,
           case when am.assistant_messages = 0 then 0 else round((am.assistant_messages_with_action_links::numeric * 100) / am.assistant_messages, 2) end as action_link_rate_pct,
           case when am.assistant_messages = 0 then 0 else round(am.total_reference_count::numeric / am.assistant_messages, 2) end as avg_references_per_assistant
    from assistant_metrics am
    join session_metrics sm on sm.days = am.days
    order by am.days;
  " > "${WINDOW_ROWS}"

smoke_print_step "chat retrieval snapshot metrics"
smoke_db_query "
    with windows(days) as (
      values ${WINDOW_VALUES}
    )
    select w.days,
           count(crs.id) as retrieval_snapshots,
           count(crs.id) filter (where coalesce(crs.branch_suggestion_keys_json, '') not in ('', '[]')) as branch_suggestion_snapshots,
           count(crs.id) filter (where crs.needs_clarification is true) as clarification_snapshots,
           count(crs.id) filter (where crs.result_count = 0) as zero_result_snapshots,
           count(crs.id) filter (
             where crs.result_count = 0
               and coalesce(crs.branch_suggestion_keys_json, '') not in ('', '[]')
           ) as zero_result_branch_suggestion_snapshots,
           count(crs.id) filter (
             where crs.result_count = 0
               and coalesce(crs.branch_suggestion_keys_json, '') in ('', '[]')
           ) as zero_result_non_branch_snapshots,
           coalesce(round(avg(crs.result_count)::numeric, 2), 0) as avg_result_count,
           coalesce(max(crs.result_count), 0) as max_result_count,
           case when count(crs.id) = 0 then 0 else round((count(crs.id) filter (where crs.needs_clarification is true)::numeric * 100) / count(crs.id), 2) end as clarification_rate_pct,
           case when count(crs.id) = 0 then 0 else round((count(crs.id) filter (where crs.result_count = 0)::numeric * 100) / count(crs.id), 2) end as zero_result_rate_pct,
           case
             when count(crs.id) filter (where coalesce(crs.branch_suggestion_keys_json, '') in ('', '[]')) = 0 then 0
             else round(
               (count(crs.id) filter (
                 where crs.result_count = 0
                   and coalesce(crs.branch_suggestion_keys_json, '') in ('', '[]')
               )::numeric * 100)
               / (count(crs.id) filter (where coalesce(crs.branch_suggestion_keys_json, '') in ('', '[]'))),
               2
             )
           end as zero_result_non_branch_rate_pct
    from windows w
    left join chat_retrieval_snapshots crs
      on crs.created_at >= now() - (w.days || ' days')::interval
    group by w.days
    order by w.days;
  " > "${SNAPSHOT_ROWS}"

smoke_print_step "chat fallback strategy metrics"
smoke_db_query "
    select coalesce(fallback_strategy, '(none)') as fallback_strategy,
           count(*) as snapshot_count,
           coalesce(round(avg(result_count)::numeric, 2), 0) as avg_result_count
    from chat_retrieval_snapshots
    where created_at >= now() - '7 days'::interval
    group by coalesce(fallback_strategy, '(none)')
    order by snapshot_count desc, fallback_strategy
    limit 20;
  " > "${FALLBACK_ROWS}"

smoke_print_step "chat recent samples"
smoke_db_query "
    select to_char(created_at at time zone 'UTC', 'YYYY-MM-DD HH24:MI:SS') as created_at_utc,
           left(replace(replace(replace(question, E'\t', ' '), E'\n', ' '), E'\r', ' '), 120) as question,
           coalesce(branch_key, '') as branch_key,
           coalesce(preferred_terms_json, '') as preferred_terms_json,
           coalesce(fallback_strategy, '') as fallback_strategy,
           needs_clarification,
           result_count,
           left(coalesce(merged_service_ids_json, ''), 120) as merged_service_ids_json
    from chat_retrieval_snapshots
    order by created_at desc
    limit ${RECENT_SAMPLE_LIMIT};
  " > "${RECENT_SAMPLES}"

python3 - \
  "${WINDOW_ROWS}" \
  "${SNAPSHOT_ROWS}" \
  "${FALLBACK_ROWS}" \
  "${RECENT_SAMPLES}" \
  "${SUMMARY_OUT}" \
  "${JSON_OUT}" \
  "${NOTE_OUT}" \
  "${ARTIFACT_DIR}" \
  "${RUN_TS_UTC}" \
  "${WINDOW_DAYS_CSV}" <<'PY'
import csv
import json
import sys
from pathlib import Path

window_rows_path = Path(sys.argv[1])
snapshot_rows_path = Path(sys.argv[2])
fallback_rows_path = Path(sys.argv[3])
recent_samples_path = Path(sys.argv[4])
summary_out = Path(sys.argv[5])
json_out = Path(sys.argv[6])
note_out = Path(sys.argv[7])
artifact_dir = sys.argv[8]
generated_at_utc = sys.argv[9]
window_days_csv = sys.argv[10]


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
    "sessions",
    "sessions_with_context",
    "assistant_messages",
    "assistant_messages_with_references",
    "assistant_messages_with_action_links",
    "total_reference_count",
    "reference_rate_pct",
    "action_link_rate_pct",
    "avg_references_per_assistant",
])
snapshot_rows = read_tsv(snapshot_rows_path, [
    "days",
    "retrieval_snapshots",
    "branch_suggestion_snapshots",
    "clarification_snapshots",
    "zero_result_snapshots",
    "zero_result_branch_suggestion_snapshots",
    "zero_result_non_branch_snapshots",
    "avg_result_count",
    "max_result_count",
    "clarification_rate_pct",
    "zero_result_rate_pct",
    "zero_result_non_branch_rate_pct",
])
fallback_rows = read_tsv(fallback_rows_path, [
    "fallback_strategy",
    "snapshot_count",
    "avg_result_count",
])
recent_samples = read_tsv(recent_samples_path, [
    "created_at_utc",
    "question",
    "branch_key",
    "preferred_terms_json",
    "fallback_strategy",
    "needs_clarification",
    "result_count",
    "merged_service_ids_json",
])

window_by_days = {row["days"]: row for row in window_rows}
snapshot_by_days = {row["days"]: row for row in snapshot_rows}
primary_days = "7" if "7" in snapshot_by_days else (snapshot_rows[-1]["days"] if snapshot_rows else "")
primary_snapshot = snapshot_by_days.get(primary_days, {})
primary_window = window_by_days.get(primary_days, {})

clarification_rate = float(primary_snapshot.get("clarification_rate_pct") or 0)
zero_result_rate = float(primary_snapshot.get("zero_result_rate_pct") or 0)
zero_result_non_branch_rate = float(primary_snapshot.get("zero_result_non_branch_rate_pct") or 0)
reference_rate = float(primary_window.get("reference_rate_pct") or 0)
assistant_messages = int(primary_window.get("assistant_messages") or 0)

decision_class = "CHAT_BASELINE_HEALTHY"
operator_reading = "Chatbot mode/reference/retrieval observations are inside the current baseline."
if assistant_messages == 0:
    decision_class = "CHAT_TRAFFIC_EMPTY"
    operator_reading = "No assistant messages were observed in the primary window."
elif clarification_rate >= 35 or zero_result_non_branch_rate >= 25 or reference_rate < 40:
    decision_class = "CHAT_ATTENTION"
    operator_reading = "Chatbot observation rates crossed attention thresholds; inspect recent samples and scenario audit."

summary_lines = [
    "chat_observability_audit=passed",
    f"artifact_dir={artifact_dir}",
    f"generated_at_utc={generated_at_utc}",
    f"window_days_csv={window_days_csv}",
    f"primary_window_days={primary_days}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
]
for row in window_rows:
    prefix = f"window_{row['days']}d"
    for key, value in row.items():
        if key == "days":
            continue
        summary_lines.append(f"{prefix}_{key}={value}")
for row in snapshot_rows:
    prefix = f"window_{row['days']}d"
    for key, value in row.items():
        if key == "days":
            continue
        summary_lines.append(f"{prefix}_{key}={value}")

summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": artifact_dir,
    "generated_at_utc": generated_at_utc,
    "window_days_csv": window_days_csv,
    "primary_window_days": primary_days,
    "decision_class": decision_class,
    "operator_reading": operator_reading,
    "window_metrics": window_rows,
    "snapshot_metrics": snapshot_rows,
    "fallback_strategies": fallback_rows,
    "recent_samples": recent_samples,
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Chat Observability Audit",
    "",
    f"- decision_class: `{decision_class}`",
    f"- primary_window_days: `{primary_days}`",
    f"- operator_reading: {operator_reading}",
    "",
    "## Window Metrics",
]
for row in window_rows:
    note_lines.append(
        f"- `{row['days']}d`: sessions=`{row['sessions']}`, assistant=`{row['assistant_messages']}`, "
        f"reference_rate=`{row['reference_rate_pct']}%`, action_link_rate=`{row['action_link_rate_pct']}%`, "
        f"avg_refs=`{row['avg_references_per_assistant']}`"
    )
for row in snapshot_rows:
    note_lines.append(
        f"- `{row['days']}d snapshots`: total=`{row['retrieval_snapshots']}`, branch=`{row['branch_suggestion_snapshots']}`, "
        f"clarification=`{row['clarification_snapshots']}` (`{row['clarification_rate_pct']}%`), "
        f"zero_result=`{row['zero_result_snapshots']}` (`{row['zero_result_rate_pct']}%`), "
        f"zero_result_non_branch=`{row['zero_result_non_branch_snapshots']}` (`{row['zero_result_non_branch_rate_pct']}%`), "
        f"avg_result=`{row['avg_result_count']}`"
    )

note_lines.extend(["", "## Fallback Strategies"])
for row in fallback_rows:
    note_lines.append(f"- `{row['fallback_strategy']}`: count=`{row['snapshot_count']}`, avg_result=`{row['avg_result_count']}`")

note_lines.extend(["", "## Recent Samples"])
for row in recent_samples:
    note_lines.append(
        f"- `{row['created_at_utc']}` result=`{row['result_count']}` clarification=`{row['needs_clarification']}` "
        f"branch=`{row['branch_key']}` question=`{row['question']}`"
    )
note_out.write_text("\n".join(note_lines) + "\n", encoding="utf-8")
PY

smoke_update_links \
  "${ARTIFACT_DIR}" "${CHAT_OBSERVABILITY_ROOT}/latest" \
  "${SUMMARY_OUT}" "${CHAT_OBSERVABILITY_ROOT}/latest-chat-observability-summary.txt" \
  "${JSON_OUT}" "${CHAT_OBSERVABILITY_ROOT}/latest-chat-observability.json" \
  "${NOTE_OUT}" "${CHAT_OBSERVABILITY_ROOT}/latest-chat-observability-note.md"

cat "${SUMMARY_OUT}"
