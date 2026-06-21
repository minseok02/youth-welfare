#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"
RUN_TS_UTC="${RUN_TS_UTC:-$(smoke_now_ts_utc)}"
ARTIFACT_ROOT="${ARTIFACT_ROOT:-${ROOT_DIR}/tmp/chat-real-user-quality-sample-audit}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${RUN_TS_UTC}}"
WINDOW_DAYS="${WINDOW_DAYS:-30}"
SAMPLE_LIMIT="${SAMPLE_LIMIT:-20}"
MIN_ASSISTANT_MESSAGES_FOR_ATTENTION="${MIN_ASSISTANT_MESSAGES_FOR_ATTENTION:-20}"

SUMMARY_OUT="${ARTIFACT_DIR}/chat-real-user-quality-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/chat-real-user-quality.json"
NOTE_OUT="${ARTIFACT_DIR}/chat-real-user-quality-note.md"
METRICS_ROWS="${ARTIFACT_DIR}/metrics.tsv"
SAMPLE_ROWS="${ARTIFACT_DIR}/samples.tsv"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" != "true" ]]; then
    rm -rf "${ARTIFACT_DIR}"
  fi
}
trap cleanup EXIT

smoke_require_command python3
mkdir -p "${ARTIFACT_DIR}"

smoke_print_step "real-user chat quality metrics"
smoke_db_query "
  with real_users as (
    select u.user_key
    from users u
    where coalesce(nullif(u.account_origin, ''), 'REAL_USER') = 'REAL_USER'
      and lower(coalesce(u.email, '')) not like '%@example.com'
      and lower(coalesce(u.email, '')) not like '%.local'
      and lower(coalesce(u.email, '')) not like '%.test'
      and lower(coalesce(u.email, '')) not like '%.invalid'
      and lower(coalesce(u.email, '')) not like '%cohortseed.app'
  ),
  sessions as (
    select cs.*
    from chat_sessions cs
    join real_users ru on ru.user_key = cs.user_key
    where cs.created_at >= now() - (${WINDOW_DAYS} || ' days')::interval
  ),
  assistant_messages as (
    select cm.id,
           cm.session_id,
           cm.created_at,
           cm.references_json as references_json_text,
           coalesce(nullif(cm.references_json, ''), '[]')::jsonb as references_json,
           crs.needs_clarification,
           crs.result_count,
           crs.branch_suggestion_keys_json
    from chat_messages cm
    join sessions cs on cs.id = cm.session_id
    left join lateral (
      select snapshot.needs_clarification,
             snapshot.result_count,
             snapshot.branch_suggestion_keys_json
      from chat_retrieval_snapshots snapshot
      where snapshot.session_id = cm.session_id
        and snapshot.created_at <= cm.created_at
      order by snapshot.created_at desc, snapshot.id desc
      limit 1
    ) crs on true
    where cm.role = 'ASSISTANT'
  )
  select
    (select count(*) from real_users) as real_user_total,
    (select count(*) from sessions) as sessions,
    (select count(*) from sessions where context_state_json is not null and length(trim(context_state_json)) > 0) as sessions_with_context,
    (select count(*) from assistant_messages) as assistant_messages,
    (select count(*) from assistant_messages where jsonb_array_length(references_json) > 0) as assistant_messages_with_references,
    (select count(*) from assistant_messages where result_count > 0 and needs_clarification is not true and coalesce(branch_suggestion_keys_json, '') in ('', '[]')) as policy_grounded_messages,
    (select count(*) from assistant_messages where needs_clarification is true) as clarification_messages,
    (select count(*) from assistant_messages where coalesce(branch_suggestion_keys_json, '') not in ('', '[]')) as branch_suggestion_messages,
    (select count(*) from assistant_messages where exists (
      select 1
      from jsonb_array_elements(references_json) ref
      where jsonb_array_length(coalesce(ref -> 'actionLinks', '[]'::jsonb)) > 0
    )) as assistant_messages_with_action_links,
    (select coalesce(sum(jsonb_array_length(references_json)), 0) from assistant_messages) as total_reference_count;
" > "${METRICS_ROWS}"

smoke_print_step "real-user chat quality samples"
smoke_db_query "
  with real_users as (
    select u.user_key
    from users u
    where coalesce(nullif(u.account_origin, ''), 'REAL_USER') = 'REAL_USER'
      and lower(coalesce(u.email, '')) not like '%@example.com'
      and lower(coalesce(u.email, '')) not like '%.local'
      and lower(coalesce(u.email, '')) not like '%.test'
      and lower(coalesce(u.email, '')) not like '%.invalid'
      and lower(coalesce(u.email, '')) not like '%cohortseed.app'
  ),
  sessions as (
    select cs.*
    from chat_sessions cs
    join real_users ru on ru.user_key = cs.user_key
    where cs.created_at >= now() - (${WINDOW_DAYS} || ' days')::interval
  ),
  assistant_messages as (
    select cm.id,
           cm.session_id,
           cm.created_at,
           coalesce(nullif(cm.references_json, ''), '[]')::jsonb as references_json,
           crs.needs_clarification,
           crs.result_count,
           crs.branch_suggestion_keys_json
    from chat_messages cm
    join sessions cs on cs.id = cm.session_id
    left join lateral (
      select snapshot.needs_clarification,
             snapshot.result_count,
             snapshot.branch_suggestion_keys_json
      from chat_retrieval_snapshots snapshot
      where snapshot.session_id = cm.session_id
        and snapshot.created_at <= cm.created_at
      order by snapshot.created_at desc, snapshot.id desc
      limit 1
    ) crs on true
    where cm.role = 'ASSISTANT'
  )
  select
    to_char(am.created_at at time zone 'UTC', 'YYYY-MM-DD HH24:MI:SS') as created_at_utc,
    md5(s.user_key) as user_key_hash,
    am.session_id,
    case
      when coalesce(am.branch_suggestion_keys_json, '') not in ('', '[]') then 'BRANCH_SUGGESTION'
      when am.needs_clarification is true then 'CLARIFICATION'
      when am.result_count > 0 then 'POLICY_GROUNDED'
      else 'UNKNOWN'
    end as answer_mode,
    coalesce(am.needs_clarification::text, '') as needs_clarification,
    jsonb_array_length(am.references_json) as reference_count,
    (
      select string_agg(left(coalesce(ref ->> 'title', ''), 80), ' | ')
      from jsonb_array_elements(am.references_json) ref
    ) as reference_titles,
    (
      select count(*)
      from jsonb_array_elements(am.references_json) ref
      cross join jsonb_array_elements(coalesce(ref -> 'actionLinks', '[]'::jsonb)) link
    ) as action_link_count,
    coalesce(length(prev_user.content), 0) as previous_user_message_chars
  from assistant_messages am
  join sessions s on s.id = am.session_id
  left join lateral (
    select cm.content
    from chat_messages cm
    where cm.session_id = am.session_id
      and cm.role = 'USER'
      and cm.created_at <= am.created_at
    order by cm.created_at desc, cm.id desc
    limit 1
  ) prev_user on true
  order by am.created_at desc, am.id desc
  limit ${SAMPLE_LIMIT};
" > "${SAMPLE_ROWS}"

python3 - \
  "${METRICS_ROWS}" \
  "${SAMPLE_ROWS}" \
  "${SUMMARY_OUT}" \
  "${JSON_OUT}" \
  "${NOTE_OUT}" \
  "${ARTIFACT_DIR}" \
  "${RUN_TS_UTC}" \
  "${WINDOW_DAYS}" \
  "${SAMPLE_LIMIT}" \
  "${MIN_ASSISTANT_MESSAGES_FOR_ATTENTION}" <<'PY'
import json
import sys
from pathlib import Path

metrics_path = Path(sys.argv[1])
samples_path = Path(sys.argv[2])
summary_out = Path(sys.argv[3])
json_out = Path(sys.argv[4])
note_out = Path(sys.argv[5])
artifact_dir = sys.argv[6]
generated_at_utc = sys.argv[7]
window_days = sys.argv[8]
sample_limit = sys.argv[9]
min_assistant_messages_for_attention = int(sys.argv[10])


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


metrics_rows = read_tsv(metrics_path, [
    "real_user_total",
    "sessions",
    "sessions_with_context",
    "assistant_messages",
    "assistant_messages_with_references",
    "policy_grounded_messages",
    "clarification_messages",
    "branch_suggestion_messages",
    "assistant_messages_with_action_links",
    "total_reference_count",
])
metrics = metrics_rows[0] if metrics_rows else {
    "real_user_total": "0",
    "sessions": "0",
    "sessions_with_context": "0",
    "assistant_messages": "0",
    "assistant_messages_with_references": "0",
    "policy_grounded_messages": "0",
    "clarification_messages": "0",
    "branch_suggestion_messages": "0",
    "assistant_messages_with_action_links": "0",
    "total_reference_count": "0",
}
samples = read_tsv(samples_path, [
    "created_at_utc",
    "user_key_hash",
    "session_id",
    "answer_mode",
    "needs_clarification",
    "reference_count",
    "reference_titles",
    "action_link_count",
    "previous_user_message_chars",
])

assistant_messages = int(metrics["assistant_messages"] or 0)
sessions = int(metrics["sessions"] or 0)
reference_count = int(metrics["assistant_messages_with_references"] or 0)
clarification_count = int(metrics["clarification_messages"] or 0)
reference_rate = 0 if assistant_messages == 0 else round(reference_count * 100 / assistant_messages, 2)
clarification_rate = 0 if assistant_messages == 0 else round(clarification_count * 100 / assistant_messages, 2)

if sessions == 0:
    decision_class = "REAL_USER_CHAT_SAMPLE_THIN"
    operator_reading = "No real-user chat sessions were found in the selected window."
elif assistant_messages < min_assistant_messages_for_attention:
    decision_class = "REAL_USER_CHAT_SAMPLE_THIN"
    operator_reading = "Real-user chat sample is too small for product judgment; keep observing while using smoke coverage for regression."
elif reference_rate < 40 or clarification_rate >= 35:
    decision_class = "REAL_USER_CHAT_ATTENTION"
    operator_reading = "Real-user chat quality sample crossed attention thresholds."
else:
    decision_class = "REAL_USER_CHAT_BASELINE_HEALTHY"
    operator_reading = "Real-user chat sample is inside the current baseline."

summary_lines = [
    "chat_real_user_quality_sample_audit=passed",
    f"artifact_dir={artifact_dir}",
    f"generated_at_utc={generated_at_utc}",
    f"window_days={window_days}",
    f"sample_limit={sample_limit}",
    f"min_assistant_messages_for_attention={min_assistant_messages_for_attention}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    f"reference_rate_pct={reference_rate:.2f}",
    f"clarification_rate_pct={clarification_rate:.2f}",
]
for key, value in metrics.items():
    summary_lines.append(f"{key}={value}")

summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")
json_out.write_text(json.dumps({
    "artifact_dir": artifact_dir,
    "generated_at_utc": generated_at_utc,
    "window_days": int(window_days),
    "sample_limit": int(sample_limit),
    "min_assistant_messages_for_attention": min_assistant_messages_for_attention,
    "decision_class": decision_class,
    "operator_reading": operator_reading,
    "reference_rate_pct": reference_rate,
    "clarification_rate_pct": clarification_rate,
    "metrics": metrics,
    "samples": samples,
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Chat Real-user Quality Sample Audit",
    "",
    f"- decision_class: `{decision_class}`",
    f"- operator_reading: {operator_reading}",
    f"- window_days: `{window_days}`",
    f"- real_user_total: `{metrics['real_user_total']}`",
    f"- sessions: `{metrics['sessions']}`",
    f"- assistant_messages: `{metrics['assistant_messages']}`",
    f"- reference_rate: `{reference_rate:.2f}%`",
    f"- clarification_rate: `{clarification_rate:.2f}%`",
    "",
    "## Samples",
]
for row in samples:
    note_lines.append(
        f"- `{row['created_at_utc']}` user=`{row['user_key_hash'][:12]}` session=`{row['session_id']}` "
        f"mode=`{row['answer_mode']}` refs=`{row['reference_count']}` links=`{row['action_link_count']}` "
        f"user_chars=`{row['previous_user_message_chars']}` titles=`{row['reference_titles']}`"
    )
note_out.write_text("\n".join(note_lines) + "\n", encoding="utf-8")
PY

smoke_update_links \
  "${ARTIFACT_DIR}" "${ARTIFACT_ROOT}/latest" \
  "${SUMMARY_OUT}" "${ARTIFACT_ROOT}/latest-chat-real-user-quality-summary.txt" \
  "${JSON_OUT}" "${ARTIFACT_ROOT}/latest-chat-real-user-quality.json" \
  "${NOTE_OUT}" "${ARTIFACT_ROOT}/latest-chat-real-user-quality-note.md"

cat "${SUMMARY_OUT}"
