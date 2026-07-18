#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"
AUDIT_ROOT="${AUDIT_ROOT:-${ROOT_DIR}/tmp/chat-explicit-region-candidate-audit}"
RUN_TS_UTC="${RUN_TS_UTC:-$(smoke_now_ts_utc)}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${AUDIT_ROOT}/${RUN_TS_UTC}}"
SESSION_ID="${SESSION_ID:-}"
QUESTION_LIKE="${QUESTION_LIKE:-%인천%중구%}"
EXPECTED_REGION_CODE="${EXPECTED_REGION_CODE:-28110}"
EXPECTED_SIDO="${EXPECTED_SIDO:-인천광역시}"
EXPECTED_SGG="${EXPECTED_SGG:-중구}"
SNAPSHOT_LIMIT="${SNAPSHOT_LIMIT:-8}"

SUMMARY_OUT="${ARTIFACT_DIR}/chat-explicit-region-candidate-summary.txt"
CANDIDATES_TSV="${ARTIFACT_DIR}/chat-explicit-region-candidates.tsv"
NOTE_OUT="${ARTIFACT_DIR}/chat-explicit-region-candidate-note.md"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
  if [[ "${KEEP_ARTIFACTS}" != "true" ]]; then
    rm -rf "${ARTIFACT_DIR}"
  fi
}
trap cleanup EXIT

mkdir -p "${ARTIFACT_DIR}"

if [[ ! "${SNAPSHOT_LIMIT}" =~ ^[0-9]+$ ]] || (( SNAPSHOT_LIMIT <= 0 || SNAPSHOT_LIMIT > 50 )); then
  echo "SNAPSHOT_LIMIT must be between 1 and 50" >&2
  exit 1
fi

if [[ -n "${SESSION_ID}" && ! "${SESSION_ID}" =~ ^[0-9]+$ ]]; then
  echo "SESSION_ID must be numeric when provided" >&2
  exit 1
fi

SESSION_FILTER_SQL=""
if [[ -n "${SESSION_ID}" ]]; then
  SESSION_FILTER_SQL="and crs.session_id = ${SESSION_ID}"
else
  SESSION_FILTER_SQL="and crs.question ilike '${QUESTION_LIKE//\'/''}'"
fi

smoke_db_query "
with selected_snapshots as (
    select crs.id,
           crs.session_id,
           crs.created_at,
           crs.question,
           crs.search_keyword,
           crs.normalized_keyword,
           crs.fts_service_ids_json,
           crs.semantic_service_ids_json,
           crs.merged_service_ids_json
    from chat_retrieval_snapshots crs
    where crs.snapshot_type = 'INTERACTIVE'
      ${SESSION_FILTER_SQL}
    order by crs.created_at desc, crs.id desc
    limit ${SNAPSHOT_LIMIT}
),
candidate_ids as (
    select ss.id as snapshot_id,
           ss.session_id,
           ss.created_at,
           ss.question,
           ss.search_keyword,
           ss.normalized_keyword,
           source.source_name,
           candidate.ordinality::int as candidate_rank,
           candidate.service_id::bigint as service_id
    from selected_snapshots ss
    cross join lateral (
        values
          ('fts', coalesce(nullif(ss.fts_service_ids_json, ''), '[]')::jsonb),
          ('semantic', coalesce(nullif(ss.semantic_service_ids_json, ''), '[]')::jsonb),
          ('merged', coalesce(nullif(ss.merged_service_ids_json, ''), '[]')::jsonb)
    ) as source(source_name, ids_json)
    cross join lateral jsonb_array_elements_text(source.ids_json) with ordinality as candidate(service_id, ordinality)
),
regions as (
    select sr.service_id,
           count(*) as region_rows,
           bool_or(
             ('${EXPECTED_REGION_CODE}' <> '' and sr.region_code = '${EXPECTED_REGION_CODE}')
             or (
                '${EXPECTED_SIDO}' <> ''
                and sr.sido_name = '${EXPECTED_SIDO}'
                and ('${EXPECTED_SGG}' = '' or sr.sgg_name = '${EXPECTED_SGG}')
             )
           ) as expected_region_match,
           string_agg(
             distinct trim(coalesce(sr.region_code, '') || ' ' || coalesce(sr.sido_name, '') || ' ' || coalesce(sr.sgg_name, '')),
             ', '
             order by trim(coalesce(sr.region_code, '') || ' ' || coalesce(sr.sido_name, '') || ' ' || coalesce(sr.sgg_name, ''))
           ) as region_labels
    from service_regions sr
    where sr.service_id in (select service_id from candidate_ids)
    group by sr.service_id
)
select ci.snapshot_id,
       ci.session_id,
       to_char(ci.created_at at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') as created_at_utc,
       ci.source_name,
       ci.candidate_rank,
       ci.service_id,
       ws.title,
       ws.source_type,
       coalesce(ws.unified_category, '') as unified_category,
       case
         when coalesce(r.region_rows, 0) = 0 then 'NO_REGION_ROW'
         when r.expected_region_match then 'REGION_MATCH'
         else 'REGION_MISMATCH'
       end as region_match_class,
       coalesce(r.region_labels, '') as regions,
       ci.question,
       coalesce(ci.search_keyword, '') as search_keyword
from candidate_ids ci
join welfare_services ws on ws.id = ci.service_id
left join regions r on r.service_id = ci.service_id
order by ci.created_at desc, ci.snapshot_id desc, ci.source_name, ci.candidate_rank;
" > "${CANDIDATES_TSV}"

{
  echo "chat_explicit_region_candidate_audit=completed"
  echo "artifact_dir=${ARTIFACT_DIR}"
  echo "session_id=${SESSION_ID:-latest_by_question}"
  echo "question_like=${QUESTION_LIKE}"
  echo "expected_region_code=${EXPECTED_REGION_CODE}"
  echo "expected_sido=${EXPECTED_SIDO}"
  echo "expected_sgg=${EXPECTED_SGG}"
  echo "snapshot_limit=${SNAPSHOT_LIMIT}"
  echo "candidate_rows=$(tail -n +1 "${CANDIDATES_TSV}" | wc -l | tr -d ' ')"
} | tee "${SUMMARY_OUT}"

{
  echo "# Chat explicit region candidate audit"
  echo
  echo "- artifact: \`${ARTIFACT_DIR}\`"
  echo "- expected region: \`${EXPECTED_REGION_CODE} ${EXPECTED_SIDO} ${EXPECTED_SGG}\`"
  echo "- candidates: \`${CANDIDATES_TSV}\`"
} > "${NOTE_OUT}"

echo "candidates_tsv=${CANDIDATES_TSV}"
