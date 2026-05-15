#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

DB_CONTAINER_NAME="${DB_CONTAINER_NAME:-youth-welfare-db}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_DB="${POSTGRES_DB:-youth_welfare}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker command is required" >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -Fxq "${DB_CONTAINER_NAME}"; then
  echo "db container not running: ${DB_CONTAINER_NAME}" >&2
  exit 1
fi

docker exec -i "${DB_CONTAINER_NAME}" psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -At <<'SQL'
WITH total AS (
    SELECT COUNT(*) AS total_logs,
           COUNT(*) FILTER (WHERE is_clicked) AS clicked_logs,
           ROUND(COUNT(*) FILTER (WHERE is_clicked) * 100.0 / NULLIF(COUNT(*), 0), 2) AS ctr_pct,
           MIN(sent_at) AS first_sent_at,
           MAX(sent_at) AS last_sent_at,
           MAX(clicked_at) AS last_clicked_at
    FROM recommendation_logs
),
recent AS (
    SELECT COUNT(*) AS total_logs_7d,
           COUNT(*) FILTER (WHERE is_clicked) AS clicked_logs_7d,
           ROUND(COUNT(*) FILTER (WHERE is_clicked) * 100.0 / NULLIF(COUNT(*), 0), 2) AS ctr_pct_7d
    FROM recommendation_logs
    WHERE sent_at >= NOW() - INTERVAL '7 days'
),
cardinality AS (
    SELECT COUNT(DISTINCT user_key) AS distinct_users,
           COUNT(DISTINCT service_id) AS distinct_services
    FROM recommendation_logs
),
fallback AS (
    SELECT COALESCE(SUM(CASE WHEN is_fallback THEN 1 ELSE 0 END), 0) AS fallback_sent,
           COALESCE(SUM(CASE WHEN is_fallback AND is_clicked THEN 1 ELSE 0 END), 0) AS fallback_clicked,
           COALESCE(SUM(CASE WHEN NOT is_fallback THEN 1 ELSE 0 END), 0) AS ai_sent,
           COALESCE(SUM(CASE WHEN NOT is_fallback AND is_clicked THEN 1 ELSE 0 END), 0) AS ai_clicked
    FROM recommendation_logs
)
SELECT 'ctr_total_logs=' || total.total_logs FROM total
UNION ALL
SELECT 'ctr_clicked_logs=' || total.clicked_logs FROM total
UNION ALL
SELECT 'ctr_pct=' || total.ctr_pct FROM total
UNION ALL
SELECT 'ctr_first_sent_at=' || COALESCE(total.first_sent_at::text, '') FROM total
UNION ALL
SELECT 'ctr_last_sent_at=' || COALESCE(total.last_sent_at::text, '') FROM total
UNION ALL
SELECT 'ctr_last_clicked_at=' || COALESCE(total.last_clicked_at::text, '') FROM total
UNION ALL
SELECT 'ctr_total_logs_7d=' || recent.total_logs_7d FROM recent
UNION ALL
SELECT 'ctr_clicked_logs_7d=' || recent.clicked_logs_7d FROM recent
UNION ALL
SELECT 'ctr_pct_7d=' || recent.ctr_pct_7d FROM recent
UNION ALL
SELECT 'ctr_distinct_users=' || cardinality.distinct_users FROM cardinality
UNION ALL
SELECT 'ctr_distinct_services=' || cardinality.distinct_services FROM cardinality
UNION ALL
SELECT 'ctr_fallback_sent=' || fallback.fallback_sent FROM fallback
UNION ALL
SELECT 'ctr_fallback_clicked=' || fallback.fallback_clicked FROM fallback
UNION ALL
SELECT 'ctr_ai_sent=' || fallback.ai_sent FROM fallback
UNION ALL
SELECT 'ctr_ai_clicked=' || fallback.ai_clicked FROM fallback;

SELECT '[weight_bucket_ctr]';
SELECT rule_weight_used || '|' || ai_weight_used || '|' || COUNT(*) || '|' ||
       COUNT(*) FILTER (WHERE is_clicked) || '|' ||
       ROUND(COUNT(*) FILTER (WHERE is_clicked) * 100.0 / NULLIF(COUNT(*), 0), 2)
FROM recommendation_logs
GROUP BY rule_weight_used, ai_weight_used
ORDER BY COUNT(*) DESC, rule_weight_used, ai_weight_used;

SELECT '[weight_bucket_ctr_7d]';
SELECT rule_weight_used || '|' || ai_weight_used || '|' || COUNT(*) || '|' ||
       COUNT(*) FILTER (WHERE is_clicked) || '|' ||
       ROUND(COUNT(*) FILTER (WHERE is_clicked) * 100.0 / NULLIF(COUNT(*), 0), 2)
FROM recommendation_logs
WHERE sent_at >= NOW() - INTERVAL '7 days'
GROUP BY rule_weight_used, ai_weight_used
ORDER BY COUNT(*) DESC, rule_weight_used, ai_weight_used;

SELECT '[tuning_readiness]';
WITH agg AS (
    SELECT COUNT(*) AS total_logs,
           COUNT(*) FILTER (WHERE is_clicked) AS clicked_logs,
           COUNT(*) FILTER (WHERE is_clicked AND NOT is_fallback) AS ai_clicked,
           COUNT(DISTINCT CONCAT(rule_weight_used, ':', ai_weight_used)) AS weight_bucket_count
    FROM recommendation_logs
)
SELECT CASE
           WHEN clicked_logs < 20 THEN 'DEFERRED_CLICK_SAMPLE_THIN'
           WHEN ai_clicked < 10 THEN 'DEFERRED_AI_CLICK_SAMPLE_THIN'
           WHEN weight_bucket_count < 2 THEN 'DEFERRED_SINGLE_WEIGHT_BUCKET'
           ELSE 'READY_FOR_WEIGHT_REVIEW'
       END
FROM agg;
SQL
