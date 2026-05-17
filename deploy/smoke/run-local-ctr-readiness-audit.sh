#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

DB_CONTAINER_NAME="${DB_CONTAINER_NAME:-youth-welfare-db}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_DB="${POSTGRES_DB:-youth_welfare}"
USER_COHORT="${USER_COHORT:-all}"

case "${USER_COHORT}" in
  all|example|bounded_local|real_non_example|real_user|local_real_non_example_seed|non_example)
    ;;
  *)
    echo "USER_COHORT must be one of: all, example, bounded_local, real_non_example, real_user, local_real_non_example_seed, non_example" >&2
    exit 1
    ;;
esac

if ! command -v docker >/dev/null 2>&1; then
  echo "docker command is required" >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -Fxq "${DB_CONTAINER_NAME}"; then
  echo "db container not running: ${DB_CONTAINER_NAME}" >&2
  exit 1
fi

docker exec -i "${DB_CONTAINER_NAME}" psql -v cohort="${USER_COHORT}" -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -At <<'SQL'
WITH base_logs AS (
    SELECT rl.*,
           u.email,
           COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS user_origin,
           CASE
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'EXAMPLE_SMOKE' THEN 'EXAMPLE'
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'BOUNDED_LOCAL' THEN 'BOUNDED_LOCAL'
               ELSE 'REAL_NON_EXAMPLE'
           END AS user_cohort
    FROM recommendation_logs rl
    LEFT JOIN users u
      ON u.user_key = rl.user_key
),
scoped_logs AS (
    SELECT *
    FROM base_logs
    WHERE :'cohort' = 'all'
       OR (:'cohort' = 'example' AND user_cohort = 'EXAMPLE')
       OR (:'cohort' = 'bounded_local' AND user_cohort = 'BOUNDED_LOCAL')
       OR (:'cohort' = 'real_non_example' AND user_cohort = 'REAL_NON_EXAMPLE')
       OR (:'cohort' = 'real_user' AND user_origin = 'REAL_USER')
       OR (:'cohort' = 'local_real_non_example_seed' AND user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED')
       OR (:'cohort' = 'non_example' AND user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE'))
),
total AS (
    SELECT COUNT(*) AS total_logs,
           COUNT(*) FILTER (WHERE is_clicked) AS clicked_logs,
           ROUND(COUNT(*) FILTER (WHERE is_clicked) * 100.0 / NULLIF(COUNT(*), 0), 2) AS ctr_pct,
           MIN(sent_at) AS first_sent_at,
           MAX(sent_at) AS last_sent_at,
           MAX(clicked_at) AS last_clicked_at
    FROM scoped_logs
),
recent AS (
    SELECT COUNT(*) AS total_logs_7d,
           COUNT(*) FILTER (WHERE is_clicked) AS clicked_logs_7d,
           ROUND(COUNT(*) FILTER (WHERE is_clicked) * 100.0 / NULLIF(COUNT(*), 0), 2) AS ctr_pct_7d
    FROM scoped_logs
    WHERE sent_at >= NOW() - INTERVAL '7 days'
),
cardinality AS (
    SELECT COUNT(DISTINCT user_key) AS distinct_users,
           COUNT(DISTINCT service_id) AS distinct_services,
           COUNT(DISTINCT user_key) FILTER (WHERE is_clicked) AS clicked_users,
           COUNT(DISTINCT service_id) FILTER (WHERE is_clicked) AS clicked_services
    FROM scoped_logs
),
fallback AS (
    SELECT COALESCE(SUM(CASE WHEN is_fallback THEN 1 ELSE 0 END), 0) AS fallback_sent,
           COALESCE(SUM(CASE WHEN is_fallback AND is_clicked THEN 1 ELSE 0 END), 0) AS fallback_clicked,
           COALESCE(SUM(CASE WHEN NOT is_fallback THEN 1 ELSE 0 END), 0) AS ai_sent,
           COALESCE(SUM(CASE WHEN NOT is_fallback AND is_clicked THEN 1 ELSE 0 END), 0) AS ai_clicked
    FROM scoped_logs
),
traffic_mix AS (
    SELECT COUNT(*) FILTER (WHERE user_cohort = 'EXAMPLE') AS example_logs,
           COUNT(*) FILTER (WHERE user_cohort = 'BOUNDED_LOCAL') AS bounded_local_logs,
           COUNT(*) FILTER (WHERE user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED') AS local_real_non_example_seed_logs,
           COUNT(*) FILTER (WHERE user_origin = 'REAL_USER') AS real_user_logs,
           COUNT(*) FILTER (WHERE user_cohort = 'REAL_NON_EXAMPLE') AS real_non_example_logs,
           COUNT(*) FILTER (WHERE user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE')) AS non_example_logs,
           COUNT(DISTINCT user_key) FILTER (WHERE user_cohort = 'EXAMPLE') AS example_users,
           COUNT(DISTINCT user_key) FILTER (WHERE user_cohort = 'BOUNDED_LOCAL') AS bounded_local_users,
           COUNT(DISTINCT user_key) FILTER (WHERE user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED') AS local_real_non_example_seed_users,
           COUNT(DISTINCT user_key) FILTER (WHERE user_origin = 'REAL_USER') AS real_user_users,
           COUNT(DISTINCT user_key) FILTER (WHERE user_cohort = 'REAL_NON_EXAMPLE') AS real_non_example_users,
           COUNT(DISTINCT user_key) FILTER (WHERE user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE')) AS non_example_users,
           COUNT(DISTINCT user_key) FILTER (WHERE is_clicked AND user_cohort = 'EXAMPLE') AS example_clicked_users,
           COUNT(DISTINCT user_key) FILTER (WHERE is_clicked AND user_cohort = 'BOUNDED_LOCAL') AS bounded_local_clicked_users,
           COUNT(DISTINCT user_key) FILTER (WHERE is_clicked AND user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED') AS local_real_non_example_seed_clicked_users,
           COUNT(DISTINCT user_key) FILTER (WHERE is_clicked AND user_origin = 'REAL_USER') AS real_user_clicked_users,
           COUNT(DISTINCT user_key) FILTER (WHERE is_clicked AND user_cohort = 'REAL_NON_EXAMPLE') AS real_non_example_clicked_users,
           COUNT(DISTINCT user_key) FILTER (WHERE is_clicked AND user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE')) AS non_example_clicked_users
    FROM scoped_logs
)
SELECT 'audit_user_cohort=' || :'cohort'
UNION ALL
SELECT 'audit_scope_logs=' || COUNT(*) FROM scoped_logs
UNION ALL
SELECT 'audit_scope_users=' || COUNT(DISTINCT user_key) FROM scoped_logs
UNION ALL
SELECT 'ctr_total_logs=' || total.total_logs FROM total
UNION ALL
SELECT 'ctr_clicked_logs=' || total.clicked_logs FROM total
UNION ALL
SELECT 'ctr_pct=' || COALESCE(total.ctr_pct::text, '') FROM total
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
SELECT 'ctr_pct_7d=' || COALESCE(recent.ctr_pct_7d::text, '') FROM recent
UNION ALL
SELECT 'ctr_distinct_users=' || cardinality.distinct_users FROM cardinality
UNION ALL
SELECT 'ctr_distinct_services=' || cardinality.distinct_services FROM cardinality
UNION ALL
SELECT 'ctr_clicked_users=' || cardinality.clicked_users FROM cardinality
UNION ALL
SELECT 'ctr_clicked_services=' || cardinality.clicked_services FROM cardinality
UNION ALL
SELECT 'ctr_fallback_sent=' || fallback.fallback_sent FROM fallback
UNION ALL
SELECT 'ctr_fallback_clicked=' || fallback.fallback_clicked FROM fallback
UNION ALL
SELECT 'ctr_ai_sent=' || fallback.ai_sent FROM fallback
UNION ALL
SELECT 'ctr_ai_clicked=' || fallback.ai_clicked FROM fallback
UNION ALL
SELECT 'ctr_example_logs=' || traffic_mix.example_logs FROM traffic_mix
UNION ALL
SELECT 'ctr_bounded_local_logs=' || traffic_mix.bounded_local_logs FROM traffic_mix
UNION ALL
SELECT 'ctr_local_real_non_example_seed_logs=' || traffic_mix.local_real_non_example_seed_logs FROM traffic_mix
UNION ALL
SELECT 'ctr_real_user_logs=' || traffic_mix.real_user_logs FROM traffic_mix
UNION ALL
SELECT 'ctr_real_non_example_logs=' || traffic_mix.real_non_example_logs FROM traffic_mix
UNION ALL
SELECT 'ctr_non_example_logs=' || traffic_mix.non_example_logs FROM traffic_mix
UNION ALL
SELECT 'ctr_example_users=' || traffic_mix.example_users FROM traffic_mix
UNION ALL
SELECT 'ctr_bounded_local_users=' || traffic_mix.bounded_local_users FROM traffic_mix
UNION ALL
SELECT 'ctr_local_real_non_example_seed_users=' || traffic_mix.local_real_non_example_seed_users FROM traffic_mix
UNION ALL
SELECT 'ctr_real_user_users=' || traffic_mix.real_user_users FROM traffic_mix
UNION ALL
SELECT 'ctr_real_non_example_users=' || traffic_mix.real_non_example_users FROM traffic_mix
UNION ALL
SELECT 'ctr_non_example_users=' || traffic_mix.non_example_users FROM traffic_mix
UNION ALL
SELECT 'ctr_example_clicked_users=' || traffic_mix.example_clicked_users FROM traffic_mix
UNION ALL
SELECT 'ctr_bounded_local_clicked_users=' || traffic_mix.bounded_local_clicked_users FROM traffic_mix
UNION ALL
SELECT 'ctr_local_real_non_example_seed_clicked_users=' || traffic_mix.local_real_non_example_seed_clicked_users FROM traffic_mix
UNION ALL
SELECT 'ctr_real_user_clicked_users=' || traffic_mix.real_user_clicked_users FROM traffic_mix
UNION ALL
SELECT 'ctr_real_non_example_clicked_users=' || traffic_mix.real_non_example_clicked_users FROM traffic_mix
UNION ALL
SELECT 'ctr_non_example_clicked_users=' || traffic_mix.non_example_clicked_users FROM traffic_mix;

SELECT '[weight_bucket_ctr]';
WITH base_logs AS (
    SELECT rl.*,
           COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS user_origin,
           CASE
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'EXAMPLE_SMOKE' THEN 'EXAMPLE'
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'BOUNDED_LOCAL' THEN 'BOUNDED_LOCAL'
               ELSE 'REAL_NON_EXAMPLE'
           END AS user_cohort
    FROM recommendation_logs rl
    LEFT JOIN users u
      ON u.user_key = rl.user_key
),
scoped_logs AS (
    SELECT *
    FROM base_logs
    WHERE :'cohort' = 'all'
       OR (:'cohort' = 'example' AND user_cohort = 'EXAMPLE')
       OR (:'cohort' = 'bounded_local' AND user_cohort = 'BOUNDED_LOCAL')
       OR (:'cohort' = 'real_non_example' AND user_cohort = 'REAL_NON_EXAMPLE')
       OR (:'cohort' = 'real_user' AND user_origin = 'REAL_USER')
       OR (:'cohort' = 'local_real_non_example_seed' AND user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED')
       OR (:'cohort' = 'non_example' AND user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE'))
)
SELECT rule_weight_used || '|' || ai_weight_used || '|' || COUNT(*) || '|' ||
       COUNT(*) FILTER (WHERE is_clicked) || '|' ||
       ROUND(COUNT(*) FILTER (WHERE is_clicked) * 100.0 / NULLIF(COUNT(*), 0), 2)
FROM scoped_logs
GROUP BY rule_weight_used, ai_weight_used
ORDER BY COUNT(*) DESC, rule_weight_used, ai_weight_used;

SELECT '[top_clicked_services]';
WITH base_logs AS (
    SELECT rl.*,
           COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS user_origin,
           CASE
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'EXAMPLE_SMOKE' THEN 'EXAMPLE'
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'BOUNDED_LOCAL' THEN 'BOUNDED_LOCAL'
               ELSE 'REAL_NON_EXAMPLE'
           END AS user_cohort
    FROM recommendation_logs rl
    LEFT JOIN users u
      ON u.user_key = rl.user_key
),
scoped_logs AS (
    SELECT *
    FROM base_logs
    WHERE :'cohort' = 'all'
       OR (:'cohort' = 'example' AND user_cohort = 'EXAMPLE')
       OR (:'cohort' = 'bounded_local' AND user_cohort = 'BOUNDED_LOCAL')
       OR (:'cohort' = 'real_non_example' AND user_cohort = 'REAL_NON_EXAMPLE')
       OR (:'cohort' = 'real_user' AND user_origin = 'REAL_USER')
       OR (:'cohort' = 'local_real_non_example_seed' AND user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED')
       OR (:'cohort' = 'non_example' AND user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE'))
),
clicked AS (
    SELECT service_id,
           COUNT(*) AS clicks
    FROM scoped_logs
    WHERE is_clicked
    GROUP BY service_id
),
total AS (
    SELECT COUNT(*) AS clicked_logs
    FROM scoped_logs
    WHERE is_clicked
)
SELECT clicked.service_id || '|' || clicked.clicks || '|' ||
       ROUND(clicked.clicks * 100.0 / NULLIF(total.clicked_logs, 0), 2)
FROM clicked
CROSS JOIN total
ORDER BY clicked.clicks DESC, clicked.service_id
LIMIT 10;

SELECT '[top_clicked_users]';
WITH base_logs AS (
    SELECT rl.*,
           COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS user_origin,
           CASE
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'EXAMPLE_SMOKE' THEN 'EXAMPLE'
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'BOUNDED_LOCAL' THEN 'BOUNDED_LOCAL'
               ELSE 'REAL_NON_EXAMPLE'
           END AS user_cohort
    FROM recommendation_logs rl
    LEFT JOIN users u
      ON u.user_key = rl.user_key
),
scoped_logs AS (
    SELECT *
    FROM base_logs
    WHERE :'cohort' = 'all'
       OR (:'cohort' = 'example' AND user_cohort = 'EXAMPLE')
       OR (:'cohort' = 'bounded_local' AND user_cohort = 'BOUNDED_LOCAL')
       OR (:'cohort' = 'real_non_example' AND user_cohort = 'REAL_NON_EXAMPLE')
       OR (:'cohort' = 'real_user' AND user_origin = 'REAL_USER')
       OR (:'cohort' = 'local_real_non_example_seed' AND user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED')
       OR (:'cohort' = 'non_example' AND user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE'))
),
clicked AS (
    SELECT user_key,
           COUNT(*) AS clicks
    FROM scoped_logs
    WHERE is_clicked
    GROUP BY user_key
)
SELECT user_key || '|' || clicks
FROM clicked
ORDER BY clicks DESC, user_key
LIMIT 10;

SELECT '[weight_bucket_ctr_7d]';
WITH base_logs AS (
    SELECT rl.*,
           COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS user_origin,
           CASE
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'EXAMPLE_SMOKE' THEN 'EXAMPLE'
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'BOUNDED_LOCAL' THEN 'BOUNDED_LOCAL'
               ELSE 'REAL_NON_EXAMPLE'
           END AS user_cohort
    FROM recommendation_logs rl
    LEFT JOIN users u
      ON u.user_key = rl.user_key
),
scoped_logs AS (
    SELECT *
    FROM base_logs
    WHERE :'cohort' = 'all'
       OR (:'cohort' = 'example' AND user_cohort = 'EXAMPLE')
       OR (:'cohort' = 'bounded_local' AND user_cohort = 'BOUNDED_LOCAL')
       OR (:'cohort' = 'real_non_example' AND user_cohort = 'REAL_NON_EXAMPLE')
       OR (:'cohort' = 'real_user' AND user_origin = 'REAL_USER')
       OR (:'cohort' = 'local_real_non_example_seed' AND user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED')
       OR (:'cohort' = 'non_example' AND user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE'))
)
SELECT rule_weight_used || '|' || ai_weight_used || '|' || COUNT(*) || '|' ||
       COUNT(*) FILTER (WHERE is_clicked) || '|' ||
       ROUND(COUNT(*) FILTER (WHERE is_clicked) * 100.0 / NULLIF(COUNT(*), 0), 2)
FROM scoped_logs
WHERE sent_at >= NOW() - INTERVAL '7 days'
GROUP BY rule_weight_used, ai_weight_used
ORDER BY COUNT(*) DESC, rule_weight_used, ai_weight_used;

SELECT '[tuning_readiness]';
WITH base_logs AS (
    SELECT rl.*,
           COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS user_origin,
           CASE
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'EXAMPLE_SMOKE' THEN 'EXAMPLE'
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'BOUNDED_LOCAL' THEN 'BOUNDED_LOCAL'
               ELSE 'REAL_NON_EXAMPLE'
           END AS user_cohort
    FROM recommendation_logs rl
    LEFT JOIN users u
      ON u.user_key = rl.user_key
),
scoped_logs AS (
    SELECT *
    FROM base_logs
    WHERE :'cohort' = 'all'
       OR (:'cohort' = 'example' AND user_cohort = 'EXAMPLE')
       OR (:'cohort' = 'bounded_local' AND user_cohort = 'BOUNDED_LOCAL')
       OR (:'cohort' = 'real_non_example' AND user_cohort = 'REAL_NON_EXAMPLE')
       OR (:'cohort' = 'real_user' AND user_origin = 'REAL_USER')
       OR (:'cohort' = 'local_real_non_example_seed' AND user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED')
       OR (:'cohort' = 'non_example' AND user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE'))
),
agg AS (
    SELECT COUNT(*) AS total_logs,
           COUNT(*) FILTER (WHERE is_clicked) AS clicked_logs,
           COUNT(*) FILTER (WHERE is_clicked AND NOT is_fallback) AS ai_clicked,
           COUNT(DISTINCT CONCAT(rule_weight_used, ':', ai_weight_used)) AS weight_bucket_count,
           COUNT(*) FILTER (WHERE user_cohort = 'BOUNDED_LOCAL') AS bounded_local_logs,
           COUNT(*) FILTER (WHERE user_cohort = 'REAL_NON_EXAMPLE') AS real_non_example_logs,
           COUNT(DISTINCT user_key) FILTER (WHERE user_cohort = 'REAL_NON_EXAMPLE') AS real_non_example_users,
           COUNT(DISTINCT user_key) FILTER (WHERE user_cohort = 'REAL_NON_EXAMPLE' AND is_clicked) AS real_non_example_clicked_users,
           COUNT(*) FILTER (WHERE user_origin = 'REAL_USER') AS real_user_logs,
           COUNT(DISTINCT user_key) FILTER (WHERE user_origin = 'REAL_USER') AS real_user_users,
           COUNT(DISTINCT user_key) FILTER (WHERE user_origin = 'REAL_USER' AND is_clicked) AS real_user_clicked_users
    FROM scoped_logs
)
SELECT CASE
           WHEN total_logs = 0 AND :'cohort' = 'real_non_example' THEN 'DEFERRED_EMPTY_REAL_NON_EXAMPLE_COHORT'
           WHEN total_logs = 0 AND :'cohort' = 'real_user' THEN 'DEFERRED_EMPTY_REAL_USER_COHORT'
           WHEN total_logs = 0 AND :'cohort' = 'local_real_non_example_seed' THEN 'DEFERRED_EMPTY_LOCAL_REAL_NON_EXAMPLE_SEED_COHORT'
           WHEN total_logs = 0 AND :'cohort' = 'bounded_local' THEN 'DEFERRED_EMPTY_BOUNDED_LOCAL_COHORT'
           WHEN total_logs = 0 AND :'cohort' = 'non_example' THEN 'DEFERRED_EMPTY_NON_EXAMPLE_COHORT'
           WHEN total_logs = 0 THEN 'DEFERRED_EMPTY_COHORT'
           WHEN :'cohort' = 'example' THEN 'DIAGNOSTIC_EXAMPLE_ONLY_TRAFFIC'
           WHEN :'cohort' = 'bounded_local' THEN 'DIAGNOSTIC_BOUNDED_LOCAL_TRAFFIC'
           WHEN :'cohort' = 'real_user' THEN 'DIAGNOSTIC_REAL_USER_ONLY_TRAFFIC'
           WHEN :'cohort' = 'local_real_non_example_seed' THEN 'DIAGNOSTIC_LOCAL_REAL_NON_EXAMPLE_SEED_TRAFFIC'
           WHEN :'cohort' = 'non_example' AND real_non_example_logs = 0 AND bounded_local_logs > 0
               THEN 'DIAGNOSTIC_BOUNDED_LOCAL_ONLY_TRAFFIC'
           WHEN :'cohort' = 'all' AND real_user_logs = 0
               THEN 'DEFERRED_NO_REAL_USER_TRAFFIC'
           WHEN :'cohort' = 'all' AND real_user_users < 3
               THEN 'DEFERRED_REAL_USER_SAMPLE_THIN'
           WHEN :'cohort' = 'all' AND real_user_clicked_users < 3
               THEN 'DEFERRED_REAL_USER_CLICK_SAMPLE_THIN'
           WHEN clicked_logs < 20 THEN 'DEFERRED_CLICK_SAMPLE_THIN'
           WHEN ai_clicked < 10 THEN 'DEFERRED_AI_CLICK_SAMPLE_THIN'
           WHEN weight_bucket_count < 2 THEN 'DEFERRED_SINGLE_WEIGHT_BUCKET'
           ELSE 'READY_FOR_WEIGHT_REVIEW'
       END
FROM agg;
SQL
