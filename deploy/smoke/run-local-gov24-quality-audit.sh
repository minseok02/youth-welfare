#!/usr/bin/env bash
set -euo pipefail

DB_CONTAINER="${DB_CONTAINER:-youth-welfare-db}"
APP_HEALTH_URL="${APP_HEALTH_URL:-http://127.0.0.1:8082/actuator/health}"

query() {
  local sql="$1"
  docker exec -i "$DB_CONTAINER" psql -U postgres -d youth_welfare -At -F $'\t' -c "$sql"
}

metric() {
  local key="$1"
  local value="$2"
  printf 'METRIC %s=%s\n' "$key" "$value"
}

section() {
  printf '\n[%s]\n' "$1"
}

if command -v curl >/dev/null 2>&1; then
  section "health"
  curl -fsS "$APP_HEALTH_URL" >/dev/null
  echo "OK app_health=$APP_HEALTH_URL"
fi

section "coverage"
coverage_row="$(query "
WITH totals AS (
  SELECT
    (SELECT COUNT(*) FROM welfare_services WHERE source_type = 'GOV24') AS total_services,
    (SELECT COUNT(*)
       FROM welfare_service_details wsd
       JOIN welfare_services ws ON ws.id = wsd.service_id
      WHERE ws.source_type = 'GOV24') AS detail_rows,
    (SELECT COUNT(*)
       FROM raw_api_payloads
      WHERE source_type = 'GOV24'
        AND api_category = 'SUPPORT') AS support_raw,
    (SELECT COUNT(*)
       FROM service_facts sf
       JOIN welfare_services ws ON ws.id = sf.service_id
      WHERE ws.source_type = 'GOV24'
        AND sf.fact_code_set_key = 'GOV24_SUPPORT_CONDITION') AS support_fact_rows,
    (SELECT COUNT(DISTINCT sf.service_id)
       FROM service_facts sf
       JOIN welfare_services ws ON ws.id = sf.service_id
      WHERE ws.source_type = 'GOV24'
        AND sf.fact_code_set_key = 'GOV24_SUPPORT_CONDITION') AS support_fact_services
)
SELECT
  total_services,
  detail_rows,
  support_raw,
  support_fact_rows,
  support_fact_services,
  total_services - support_fact_services
FROM totals;
")"
IFS=$'\t' read -r total_services detail_rows support_raw support_fact_rows support_fact_services missing_fact_services <<<"$coverage_row"
metric "gov24_total_services" "$total_services"
metric "gov24_detail_rows" "$detail_rows"
metric "gov24_support_raw" "$support_raw"
metric "gov24_support_fact_rows" "$support_fact_rows"
metric "gov24_support_fact_services" "$support_fact_services"
metric "gov24_support_missing_fact_services" "$missing_fact_services"

section "raw_shape"
raw_shape_row="$(query "
SELECT
  COUNT(*) FILTER (
    WHERE rap.source_type = 'GOV24'
      AND rap.api_category = 'SUPPORT'
      AND (rap.payload_json::jsonb ? 'conditions')
  ) AS nested_shape,
  COUNT(*) FILTER (
    WHERE rap.source_type = 'GOV24'
      AND rap.api_category = 'SUPPORT'
      AND NOT (rap.payload_json::jsonb ? 'conditions')
  ) AS flat_shape
FROM raw_api_payloads rap;
")"
IFS=$'\t' read -r nested_shape flat_shape <<<"$raw_shape_row"
metric "gov24_support_nested_shape" "$nested_shape"
metric "gov24_support_flat_shape" "$flat_shape"

section "missing_fact_breakdown"
missing_fact_breakdown_row="$(query "
WITH mapped_codes AS (
  SELECT unnest(ARRAY[
    'JA0101','JA0102',
    'JA0201','JA0202','JA0203','JA0204','JA0205',
    'JA0317','JA0318','JA0319','JA0320',
    'JA0326','JA0327',
    'JA0328','JA0329','JA0330',
    'JA0401','JA0402','JA0403','JA0404',
    'JA0411','JA0412','JA0413','JA0414'
  ]) AS code
),
missing_services AS (
  SELECT ws.id, ws.source_id
  FROM welfare_services ws
  WHERE ws.source_type = 'GOV24'
    AND NOT EXISTS (
      SELECT 1
      FROM service_facts sf
      WHERE sf.service_id = ws.id
        AND sf.fact_code_set_key = 'GOV24_SUPPORT_CONDITION'
    )
),
classified AS (
  SELECT
    ms.source_id,
    (rap.payload_json IS NOT NULL) AS has_support_raw,
    COALESCE((
      SELECT COUNT(*)
      FROM jsonb_each(COALESCE((rap.payload_json::jsonb)->'conditions', '{}'::jsonb)) kv
      WHERE kv.value IS NOT NULL
        AND kv.value <> 'null'::jsonb
        AND btrim(trim(both '\"' from kv.value::text)) <> ''
        AND kv.value::text <> '0'
    ), 0) AS effective_signal_count,
    COALESCE((
      SELECT COUNT(*)
      FROM jsonb_each(COALESCE((rap.payload_json::jsonb)->'conditions', '{}'::jsonb)) kv
      JOIN mapped_codes mc ON mc.code = kv.key
      WHERE kv.value IS NOT NULL
        AND kv.value <> 'null'::jsonb
        AND btrim(trim(both '\"' from kv.value::text)) <> ''
        AND kv.value::text <> '0'
    ), 0)
    + CASE
        WHEN COALESCE(NULLIF(((rap.payload_json::jsonb)->'conditions'->>'JA0110'), ''), '0') <> '0'
          OR COALESCE(NULLIF(((rap.payload_json::jsonb)->'conditions'->>'JA0111'), ''), '0') <> '0'
        THEN 1
        ELSE 0
      END AS mapped_signal_count
  FROM missing_services ms
  LEFT JOIN raw_api_payloads rap
    ON rap.source_type = 'GOV24'
   AND rap.api_category = 'SUPPORT'
   AND rap.source_id = ms.source_id
)
SELECT
  COUNT(*) FILTER (WHERE NOT has_support_raw) AS missing_no_support_raw,
  COUNT(*) FILTER (WHERE has_support_raw AND effective_signal_count = 0) AS missing_all_null_payload,
  COUNT(*) FILTER (WHERE has_support_raw AND effective_signal_count > 0 AND mapped_signal_count = 0) AS missing_unmapped_only_payload,
  COUNT(*) FILTER (WHERE has_support_raw AND mapped_signal_count > 0) AS missing_mapped_signal_payload
FROM classified;
")"
IFS=$'\t' read -r missing_no_support_raw missing_all_null_payload missing_unmapped_only_payload missing_mapped_signal_payload <<<"$missing_fact_breakdown_row"
metric "gov24_missing_no_support_raw" "$missing_no_support_raw"
metric "gov24_missing_all_null_payload" "$missing_all_null_payload"
metric "gov24_missing_unmapped_only_payload" "$missing_unmapped_only_payload"
metric "gov24_missing_mapped_signal_payload" "$missing_mapped_signal_payload"

section "detail_fill"
detail_fill_row="$(query "
SELECT
  COUNT(*) FILTER (WHERE COALESCE(wsd.target_detail, '') <> ''),
  COUNT(*) FILTER (WHERE wsd.contact_list IS NOT NULL),
  COUNT(*) FILTER (WHERE wsd.related_law IS NOT NULL),
  COUNT(*) FILTER (WHERE wsd.form_files IS NOT NULL),
  COUNT(*) FILTER (WHERE NULLIF(wsd.homepage_url, '') IS NOT NULL),
  COUNT(*) FILTER (WHERE NULLIF(wsd.selection_criteria, '') IS NOT NULL)
FROM welfare_service_details wsd
JOIN welfare_services ws ON ws.id = wsd.service_id
WHERE ws.source_type = 'GOV24';
")"
IFS=$'\t' read -r target_detail_count contact_list_count related_law_count form_files_count homepage_url_count selection_criteria_count <<<"$detail_fill_row"
metric "gov24_detail_target_detail_count" "$target_detail_count"
metric "gov24_detail_contact_list_count" "$contact_list_count"
metric "gov24_detail_related_law_count" "$related_law_count"
metric "gov24_detail_form_files_count" "$form_files_count"
metric "gov24_detail_homepage_url_count" "$homepage_url_count"
metric "gov24_detail_selection_criteria_count" "$selection_criteria_count"

section "missing_fact_samples"
query "
WITH mapped_codes AS (
  SELECT unnest(ARRAY[
    'JA0101','JA0102',
    'JA0201','JA0202','JA0203','JA0204','JA0205',
    'JA0317','JA0318','JA0319','JA0320',
    'JA0326','JA0327',
    'JA0328','JA0329','JA0330',
    'JA0401','JA0402','JA0403','JA0404',
    'JA0411','JA0412','JA0413','JA0414'
  ]) AS code
)
SELECT
  ws.source_id,
  ws.title,
  COALESCE((
    SELECT COUNT(*)
    FROM jsonb_each(COALESCE((rap.payload_json::jsonb)->'conditions', '{}'::jsonb)) kv
    WHERE kv.value IS NOT NULL
      AND kv.value <> 'null'::jsonb
      AND btrim(trim(both '\"' from kv.value::text)) <> ''
      AND kv.value::text <> '0'
  ), 0) AS effective_signal_count,
  COALESCE((
    SELECT COUNT(*)
    FROM jsonb_each(COALESCE((rap.payload_json::jsonb)->'conditions', '{}'::jsonb)) kv
    JOIN mapped_codes mc ON mc.code = kv.key
    WHERE kv.value IS NOT NULL
      AND kv.value <> 'null'::jsonb
      AND btrim(trim(both '\"' from kv.value::text)) <> ''
      AND kv.value::text <> '0'
  ), 0)
  + CASE
      WHEN COALESCE(NULLIF(((rap.payload_json::jsonb)->'conditions'->>'JA0110'), ''), '0') <> '0'
        OR COALESCE(NULLIF(((rap.payload_json::jsonb)->'conditions'->>'JA0111'), ''), '0') <> '0'
      THEN 1
      ELSE 0
    END AS mapped_signal_count
FROM welfare_services ws
LEFT JOIN raw_api_payloads rap
  ON rap.source_id = ws.source_id
 AND rap.source_type = ws.source_type
 AND rap.api_category = 'SUPPORT'
WHERE ws.source_type = 'GOV24'
  AND NOT EXISTS (
    SELECT 1
    FROM service_facts sf
    WHERE sf.service_id = ws.id
      AND sf.fact_code_set_key = 'GOV24_SUPPORT_CONDITION'
  )
ORDER BY ws.source_id
LIMIT 5;
"
