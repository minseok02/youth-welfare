#!/usr/bin/env bash
set -euo pipefail

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
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
base_rows AS (
    SELECT ur.*,
           u.email,
           COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS user_origin,
           CASE
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'EXAMPLE_SMOKE' THEN 'EXAMPLE'
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'BOUNDED_LOCAL' THEN 'BOUNDED_LOCAL'
               ELSE 'REAL_NON_EXAMPLE'
           END AS user_cohort
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
    JOIN users u
      ON u.user_key = ur.user_key
),
scoped_rows AS (
    SELECT *
    FROM base_rows
    WHERE :'cohort' = 'all'
       OR (:'cohort' = 'example' AND user_cohort = 'EXAMPLE')
       OR (:'cohort' = 'bounded_local' AND user_cohort = 'BOUNDED_LOCAL')
       OR (:'cohort' = 'real_non_example' AND user_cohort = 'REAL_NON_EXAMPLE')
       OR (:'cohort' = 'real_user' AND user_origin = 'REAL_USER')
       OR (:'cohort' = 'local_real_non_example_seed' AND user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED')
       OR (:'cohort' = 'non_example' AND user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE'))
),
priority_profiles AS (
    SELECT up.user_key,
           STRING_AGG(po.code, '>' ORDER BY up.priority_rank) AS profile
    FROM user_priorities up
    JOIN priority_options po
      ON po.id = up.priority_option_id
    GROUP BY up.user_key
),
priority_states AS (
    SELECT DISTINCT sr.user_key,
           CASE WHEN pp.user_key IS NULL THEN 'NO_PRIORITY' ELSE 'HAS_PRIORITY' END AS priority_state
    FROM scoped_rows sr
    LEFT JOIN priority_profiles pp
      ON pp.user_key = sr.user_key
),
top1 AS (
    SELECT *
    FROM (
        SELECT sr.user_key,
               sr.service_id,
               sr.final_score,
               ROW_NUMBER() OVER (
                   PARTITION BY sr.user_key
                   ORDER BY sr.final_score DESC, sr.id DESC
               ) AS rn
        FROM scoped_rows sr
    ) ranked
    WHERE rn = 1
),
top1_summary AS (
    SELECT t.service_id,
           ws.title,
           ws.source_type,
           COALESCE(ws.unified_category, '기타') AS category,
           COUNT(*) AS users_as_top1
    FROM top1 t
    JOIN welfare_services ws
      ON ws.id = t.service_id
    GROUP BY t.service_id, ws.title, ws.source_type, COALESCE(ws.unified_category, '기타')
),
top1_leader AS (
    SELECT service_id,
           title,
           source_type,
           category,
           users_as_top1,
           ROUND(users_as_top1 * 100.0 / NULLIF((SELECT COUNT(*) FROM top1), 0), 2) AS share_pct
    FROM top1_summary
    ORDER BY users_as_top1 DESC, service_id
    LIMIT 1
),
diversity AS (
    SELECT ROUND(AVG(rec_count)::numeric, 2) AS avg_rec_count,
           ROUND(AVG(distinct_services)::numeric, 2) AS avg_distinct_services,
           ROUND(AVG(distinct_categories)::numeric, 2) AS avg_distinct_categories,
           ROUND(AVG(distinct_sources)::numeric, 2) AS avg_distinct_sources,
           MIN(distinct_categories) AS min_distinct_categories,
           MAX(distinct_categories) AS max_distinct_categories,
           MIN(distinct_sources) AS min_distinct_sources,
           MAX(distinct_sources) AS max_distinct_sources
    FROM (
        SELECT sr.user_key,
               COUNT(*) AS rec_count,
               COUNT(DISTINCT sr.service_id) AS distinct_services,
               COUNT(DISTINCT COALESCE(ws.unified_category, '기타')) AS distinct_categories,
               COUNT(DISTINCT ws.source_type) AS distinct_sources
        FROM scoped_rows sr
        JOIN welfare_services ws
          ON ws.id = sr.service_id
        GROUP BY sr.user_key
    ) per_user
)
SELECT 'audit_user_cohort=' || :'cohort'
UNION ALL
SELECT 'latest_batch_rows=' || COUNT(*) FROM scoped_rows
UNION ALL
SELECT 'latest_batch_users=' || COUNT(DISTINCT user_key) FROM scoped_rows
UNION ALL
SELECT 'latest_batch_example_users=' || COUNT(DISTINCT user_key) FILTER (WHERE user_cohort = 'EXAMPLE') FROM scoped_rows
UNION ALL
SELECT 'latest_batch_bounded_local_users=' || COUNT(DISTINCT user_key) FILTER (WHERE user_cohort = 'BOUNDED_LOCAL') FROM scoped_rows
UNION ALL
SELECT 'latest_batch_local_real_non_example_seed_users=' || COUNT(DISTINCT user_key) FILTER (WHERE user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED') FROM scoped_rows
UNION ALL
SELECT 'latest_batch_real_user_users=' || COUNT(DISTINCT user_key) FILTER (WHERE user_origin = 'REAL_USER') FROM scoped_rows
UNION ALL
SELECT 'latest_batch_real_non_example_users=' || COUNT(DISTINCT user_key) FILTER (WHERE user_cohort = 'REAL_NON_EXAMPLE') FROM scoped_rows
UNION ALL
SELECT 'latest_batch_non_example_users=' || COUNT(DISTINCT user_key) FILTER (WHERE user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE')) FROM scoped_rows
UNION ALL
SELECT 'latest_batch_distinct_services=' || COUNT(DISTINCT service_id) FROM scoped_rows
UNION ALL
SELECT 'latest_batch_priority_users=' || COUNT(*) FILTER (WHERE priority_state = 'HAS_PRIORITY') FROM priority_states
UNION ALL
SELECT 'latest_batch_no_priority_users=' || COUNT(*) FILTER (WHERE priority_state = 'NO_PRIORITY') FROM priority_states
UNION ALL
SELECT 'top1_leader_service_id=' || COALESCE((SELECT service_id::text FROM top1_leader), '')
UNION ALL
SELECT 'top1_leader_title=' || COALESCE((SELECT title FROM top1_leader), '')
UNION ALL
SELECT 'top1_leader_source=' || COALESCE((SELECT source_type FROM top1_leader), '')
UNION ALL
SELECT 'top1_leader_category=' || COALESCE((SELECT category FROM top1_leader), '')
UNION ALL
SELECT 'top1_leader_users=' || COALESCE((SELECT users_as_top1::text FROM top1_leader), '')
UNION ALL
SELECT 'top1_leader_share_pct=' || COALESCE((SELECT share_pct::text FROM top1_leader), '')
UNION ALL
SELECT 'avg_recommendations_per_user=' || COALESCE(avg_rec_count::text, '') FROM diversity
UNION ALL
SELECT 'avg_distinct_services_per_user=' || COALESCE(avg_distinct_services::text, '') FROM diversity
UNION ALL
SELECT 'avg_distinct_categories_per_user=' || COALESCE(avg_distinct_categories::text, '') FROM diversity
UNION ALL
SELECT 'avg_distinct_sources_per_user=' || COALESCE(avg_distinct_sources::text, '') FROM diversity
UNION ALL
SELECT 'min_distinct_categories_per_user=' || COALESCE(min_distinct_categories::text, '') FROM diversity
UNION ALL
SELECT 'max_distinct_categories_per_user=' || COALESCE(max_distinct_categories::text, '') FROM diversity
UNION ALL
SELECT 'min_distinct_sources_per_user=' || COALESCE(min_distinct_sources::text, '') FROM diversity
UNION ALL
SELECT 'max_distinct_sources_per_user=' || COALESCE(max_distinct_sources::text, '') FROM diversity;

SELECT '[latest_source_distribution]';
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
base_rows AS (
    SELECT ur.*,
           COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS user_origin,
           CASE
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'EXAMPLE_SMOKE' THEN 'EXAMPLE'
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'BOUNDED_LOCAL' THEN 'BOUNDED_LOCAL'
               ELSE 'REAL_NON_EXAMPLE'
           END AS user_cohort
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
    JOIN users u
      ON u.user_key = ur.user_key
),
scoped_rows AS (
    SELECT *
    FROM base_rows
    WHERE :'cohort' = 'all'
       OR (:'cohort' = 'example' AND user_cohort = 'EXAMPLE')
       OR (:'cohort' = 'bounded_local' AND user_cohort = 'BOUNDED_LOCAL')
       OR (:'cohort' = 'real_non_example' AND user_cohort = 'REAL_NON_EXAMPLE')
       OR (:'cohort' = 'real_user' AND user_origin = 'REAL_USER')
       OR (:'cohort' = 'local_real_non_example_seed' AND user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED')
       OR (:'cohort' = 'non_example' AND user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE'))
)
SELECT ws.source_type || '|' || COUNT(*) || '|' || COUNT(DISTINCT sr.user_key)
FROM scoped_rows sr
JOIN welfare_services ws
  ON ws.id = sr.service_id
GROUP BY ws.source_type
ORDER BY COUNT(*) DESC, ws.source_type;

SELECT '[latest_category_distribution]';
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
base_rows AS (
    SELECT ur.*,
           COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS user_origin,
           CASE
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'EXAMPLE_SMOKE' THEN 'EXAMPLE'
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'BOUNDED_LOCAL' THEN 'BOUNDED_LOCAL'
               ELSE 'REAL_NON_EXAMPLE'
           END AS user_cohort
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
    JOIN users u
      ON u.user_key = ur.user_key
),
scoped_rows AS (
    SELECT *
    FROM base_rows
    WHERE :'cohort' = 'all'
       OR (:'cohort' = 'example' AND user_cohort = 'EXAMPLE')
       OR (:'cohort' = 'bounded_local' AND user_cohort = 'BOUNDED_LOCAL')
       OR (:'cohort' = 'real_non_example' AND user_cohort = 'REAL_NON_EXAMPLE')
       OR (:'cohort' = 'real_user' AND user_origin = 'REAL_USER')
       OR (:'cohort' = 'local_real_non_example_seed' AND user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED')
       OR (:'cohort' = 'non_example' AND user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE'))
)
SELECT COALESCE(ws.unified_category, '기타') || '|' || COUNT(*) || '|' || COUNT(DISTINCT sr.user_key)
FROM scoped_rows sr
JOIN welfare_services ws
  ON ws.id = sr.service_id
GROUP BY COALESCE(ws.unified_category, '기타')
ORDER BY COUNT(*) DESC, COALESCE(ws.unified_category, '기타');

SELECT '[top_repeated_services]';
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
base_rows AS (
    SELECT ur.*,
           COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS user_origin,
           CASE
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'EXAMPLE_SMOKE' THEN 'EXAMPLE'
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'BOUNDED_LOCAL' THEN 'BOUNDED_LOCAL'
               ELSE 'REAL_NON_EXAMPLE'
           END AS user_cohort
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
    JOIN users u
      ON u.user_key = ur.user_key
),
scoped_rows AS (
    SELECT *
    FROM base_rows
    WHERE :'cohort' = 'all'
       OR (:'cohort' = 'example' AND user_cohort = 'EXAMPLE')
       OR (:'cohort' = 'bounded_local' AND user_cohort = 'BOUNDED_LOCAL')
       OR (:'cohort' = 'real_non_example' AND user_cohort = 'REAL_NON_EXAMPLE')
       OR (:'cohort' = 'real_user' AND user_origin = 'REAL_USER')
       OR (:'cohort' = 'local_real_non_example_seed' AND user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED')
       OR (:'cohort' = 'non_example' AND user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE'))
)
SELECT sr.service_id || '|' || ws.title || '|' || ws.source_type || '|' ||
       COALESCE(ws.unified_category, '기타') || '|' || COUNT(*) || '|' || COUNT(DISTINCT sr.user_key)
FROM scoped_rows sr
JOIN welfare_services ws
  ON ws.id = sr.service_id
GROUP BY sr.service_id, ws.title, ws.source_type, COALESCE(ws.unified_category, '기타')
ORDER BY COUNT(*) DESC, sr.service_id
LIMIT 15;

SELECT '[top1_services]';
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
base_rows AS (
    SELECT ur.*,
           COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS user_origin,
           CASE
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'EXAMPLE_SMOKE' THEN 'EXAMPLE'
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'BOUNDED_LOCAL' THEN 'BOUNDED_LOCAL'
               ELSE 'REAL_NON_EXAMPLE'
           END AS user_cohort
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
    JOIN users u
      ON u.user_key = ur.user_key
),
scoped_rows AS (
    SELECT *
    FROM base_rows
    WHERE :'cohort' = 'all'
       OR (:'cohort' = 'example' AND user_cohort = 'EXAMPLE')
       OR (:'cohort' = 'bounded_local' AND user_cohort = 'BOUNDED_LOCAL')
       OR (:'cohort' = 'real_non_example' AND user_cohort = 'REAL_NON_EXAMPLE')
       OR (:'cohort' = 'real_user' AND user_origin = 'REAL_USER')
       OR (:'cohort' = 'local_real_non_example_seed' AND user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED')
       OR (:'cohort' = 'non_example' AND user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE'))
),
top1 AS (
    SELECT *
    FROM (
        SELECT sr.user_key,
               sr.service_id,
               sr.final_score,
               ROW_NUMBER() OVER (
                   PARTITION BY sr.user_key
                   ORDER BY sr.final_score DESC, sr.id DESC
               ) AS rn
        FROM scoped_rows sr
    ) ranked
    WHERE rn = 1
),
top1_summary AS (
    SELECT t.service_id,
           ws.title,
           ws.source_type,
           COALESCE(ws.unified_category, '기타') AS category,
           COUNT(*) AS users_as_top1
    FROM top1 t
    JOIN welfare_services ws
      ON ws.id = t.service_id
    GROUP BY t.service_id, ws.title, ws.source_type, COALESCE(ws.unified_category, '기타')
)
SELECT service_id || '|' || title || '|' || source_type || '|' || category || '|' || users_as_top1
FROM top1_summary
ORDER BY users_as_top1 DESC, service_id
LIMIT 15;

SELECT '[top1_by_priority_state]';
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
base_rows AS (
    SELECT ur.*,
           COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS user_origin,
           CASE
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'EXAMPLE_SMOKE' THEN 'EXAMPLE'
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'BOUNDED_LOCAL' THEN 'BOUNDED_LOCAL'
               ELSE 'REAL_NON_EXAMPLE'
           END AS user_cohort
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
    JOIN users u
      ON u.user_key = ur.user_key
),
scoped_rows AS (
    SELECT *
    FROM base_rows
    WHERE :'cohort' = 'all'
       OR (:'cohort' = 'example' AND user_cohort = 'EXAMPLE')
       OR (:'cohort' = 'bounded_local' AND user_cohort = 'BOUNDED_LOCAL')
       OR (:'cohort' = 'real_non_example' AND user_cohort = 'REAL_NON_EXAMPLE')
       OR (:'cohort' = 'real_user' AND user_origin = 'REAL_USER')
       OR (:'cohort' = 'local_real_non_example_seed' AND user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED')
       OR (:'cohort' = 'non_example' AND user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE'))
),
priority_profiles AS (
    SELECT up.user_key,
           STRING_AGG(po.code, '>' ORDER BY up.priority_rank) AS profile
    FROM user_priorities up
    JOIN priority_options po
      ON po.id = up.priority_option_id
    GROUP BY up.user_key
),
priority_states AS (
    SELECT DISTINCT sr.user_key,
           CASE WHEN pp.user_key IS NULL THEN 'NO_PRIORITY' ELSE 'HAS_PRIORITY' END AS priority_state
    FROM scoped_rows sr
    LEFT JOIN priority_profiles pp
      ON pp.user_key = sr.user_key
),
top1 AS (
    SELECT *
    FROM (
        SELECT sr.user_key,
               sr.service_id,
               sr.final_score,
               ROW_NUMBER() OVER (
                   PARTITION BY sr.user_key
                   ORDER BY sr.final_score DESC, sr.id DESC
               ) AS rn
        FROM scoped_rows sr
    ) ranked
    WHERE rn = 1
)
SELECT ps.priority_state || '|' || ws.title || '|' || ws.source_type || '|' ||
       COALESCE(ws.unified_category, '기타') || '|' || COUNT(*)
FROM top1 t
JOIN priority_states ps
  ON ps.user_key = t.user_key
JOIN welfare_services ws
  ON ws.id = t.service_id
GROUP BY ps.priority_state, ws.title, ws.source_type, COALESCE(ws.unified_category, '기타')
ORDER BY COUNT(*) DESC, ps.priority_state, ws.title
LIMIT 20;

SELECT '[priority_profile_counts]';
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
base_rows AS (
    SELECT ur.user_key,
           COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS user_origin,
           CASE
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'EXAMPLE_SMOKE' THEN 'EXAMPLE'
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'BOUNDED_LOCAL' THEN 'BOUNDED_LOCAL'
               ELSE 'REAL_NON_EXAMPLE'
           END AS user_cohort
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
    JOIN users u
      ON u.user_key = ur.user_key
),
latest_users AS (
    SELECT DISTINCT user_key
    FROM base_rows
    WHERE :'cohort' = 'all'
       OR (:'cohort' = 'example' AND user_cohort = 'EXAMPLE')
       OR (:'cohort' = 'bounded_local' AND user_cohort = 'BOUNDED_LOCAL')
       OR (:'cohort' = 'real_non_example' AND user_cohort = 'REAL_NON_EXAMPLE')
       OR (:'cohort' = 'real_user' AND user_origin = 'REAL_USER')
       OR (:'cohort' = 'local_real_non_example_seed' AND user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED')
       OR (:'cohort' = 'non_example' AND user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE'))
),
priority_profiles AS (
    SELECT up.user_key,
           STRING_AGG(po.code, '>' ORDER BY up.priority_rank) AS profile
    FROM user_priorities up
    JOIN priority_options po
      ON po.id = up.priority_option_id
    JOIN latest_users lu
      ON lu.user_key = up.user_key
    GROUP BY up.user_key
)
SELECT profile || '|' || COUNT(*)
FROM priority_profiles
GROUP BY profile
ORDER BY COUNT(*) DESC, profile
LIMIT 15;

SELECT '[priority_profile_top1]';
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
base_rows AS (
    SELECT ur.*,
           COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS user_origin,
           CASE
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'EXAMPLE_SMOKE' THEN 'EXAMPLE'
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'BOUNDED_LOCAL' THEN 'BOUNDED_LOCAL'
               ELSE 'REAL_NON_EXAMPLE'
           END AS user_cohort
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
    JOIN users u
      ON u.user_key = ur.user_key
),
scoped_rows AS (
    SELECT *
    FROM base_rows
    WHERE :'cohort' = 'all'
       OR (:'cohort' = 'example' AND user_cohort = 'EXAMPLE')
       OR (:'cohort' = 'bounded_local' AND user_cohort = 'BOUNDED_LOCAL')
       OR (:'cohort' = 'real_non_example' AND user_cohort = 'REAL_NON_EXAMPLE')
       OR (:'cohort' = 'real_user' AND user_origin = 'REAL_USER')
       OR (:'cohort' = 'local_real_non_example_seed' AND user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED')
       OR (:'cohort' = 'non_example' AND user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE'))
),
priority_profiles AS (
    SELECT up.user_key,
           STRING_AGG(po.code, '>' ORDER BY up.priority_rank) AS profile
    FROM user_priorities up
    JOIN priority_options po
      ON po.id = up.priority_option_id
    GROUP BY up.user_key
),
top1 AS (
    SELECT *
    FROM (
        SELECT sr.user_key,
               sr.service_id,
               sr.final_score,
               ROW_NUMBER() OVER (
                   PARTITION BY sr.user_key
                   ORDER BY sr.final_score DESC, sr.id DESC
               ) AS rn
        FROM scoped_rows sr
    ) ranked
    WHERE rn = 1
)
SELECT pp.profile || '|' || ws.title || '|' || ws.source_type || '|' ||
       COALESCE(ws.unified_category, '기타') || '|' || COUNT(*)
FROM top1 t
JOIN priority_profiles pp
  ON pp.user_key = t.user_key
JOIN welfare_services ws
  ON ws.id = t.service_id
GROUP BY pp.profile, ws.title, ws.source_type, COALESCE(ws.unified_category, '기타')
ORDER BY COUNT(*) DESC, pp.profile, ws.title
LIMIT 20;

SELECT '[concentration_readiness]';
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
base_rows AS (
    SELECT ur.*,
           COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS user_origin,
           CASE
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'EXAMPLE_SMOKE' THEN 'EXAMPLE'
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'BOUNDED_LOCAL' THEN 'BOUNDED_LOCAL'
               ELSE 'REAL_NON_EXAMPLE'
           END AS user_cohort
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
    JOIN users u
      ON u.user_key = ur.user_key
),
scoped_rows AS (
    SELECT *
    FROM base_rows
    WHERE :'cohort' = 'all'
       OR (:'cohort' = 'example' AND user_cohort = 'EXAMPLE')
       OR (:'cohort' = 'bounded_local' AND user_cohort = 'BOUNDED_LOCAL')
       OR (:'cohort' = 'real_non_example' AND user_cohort = 'REAL_NON_EXAMPLE')
       OR (:'cohort' = 'real_user' AND user_origin = 'REAL_USER')
       OR (:'cohort' = 'local_real_non_example_seed' AND user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED')
       OR (:'cohort' = 'non_example' AND user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE'))
),
priority_profiles AS (
    SELECT up.user_key,
           STRING_AGG(po.code, '>' ORDER BY up.priority_rank) AS profile
    FROM user_priorities up
    JOIN priority_options po
      ON po.id = up.priority_option_id
    GROUP BY up.user_key
),
priority_states AS (
    SELECT DISTINCT sr.user_key,
           CASE WHEN pp.user_key IS NULL THEN 'NO_PRIORITY' ELSE 'HAS_PRIORITY' END AS priority_state
    FROM scoped_rows sr
    LEFT JOIN priority_profiles pp
      ON pp.user_key = sr.user_key
),
top1 AS (
    SELECT *
    FROM (
        SELECT sr.user_key,
               sr.service_id,
               sr.final_score,
               ROW_NUMBER() OVER (
                   PARTITION BY sr.user_key
                   ORDER BY sr.final_score DESC, sr.id DESC
               ) AS rn
        FROM scoped_rows sr
    ) ranked
    WHERE rn = 1
),
top1_summary AS (
    SELECT t.service_id,
           COUNT(*) AS users_as_top1
    FROM top1 t
    GROUP BY t.service_id
),
top1_leader AS (
    SELECT service_id, users_as_top1
    FROM top1_summary
    ORDER BY users_as_top1 DESC, service_id
    LIMIT 1
),
base AS (
    SELECT (SELECT COUNT(*) FROM top1) AS top1_users,
           (SELECT users_as_top1 FROM top1_leader) AS top1_leader_users,
           (SELECT COUNT(*) FROM priority_states WHERE priority_state = 'HAS_PRIORITY') AS priority_users,
           (SELECT COUNT(*) FROM priority_states WHERE priority_state = 'NO_PRIORITY') AS no_priority_users
)
SELECT CASE
           WHEN top1_users = 0 THEN 'DEFERRED_EMPTY_COHORT'
           WHEN top1_leader_users * 100.0 / NULLIF(top1_users, 0) >= 50 THEN 'CONCENTRATED_TOP1'
           WHEN no_priority_users > priority_users * 2 THEN 'NO_PRIORITY_DOMINANT'
           ELSE 'BALANCED_ENOUGH_FOR_LOGIC_REVIEW'
       END
FROM base;

SELECT '[signal_quality]';
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
base_rows AS (
    SELECT ur.user_key,
           COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') AS user_origin,
           CASE
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'EXAMPLE_SMOKE' THEN 'EXAMPLE'
               WHEN COALESCE(NULLIF(u.account_origin, ''), 'REAL_USER') = 'BOUNDED_LOCAL' THEN 'BOUNDED_LOCAL'
               ELSE 'REAL_NON_EXAMPLE'
           END AS user_cohort
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
    JOIN users u
      ON u.user_key = ur.user_key
),
scoped_rows AS (
    SELECT *
    FROM base_rows
    WHERE :'cohort' = 'all'
       OR (:'cohort' = 'example' AND user_cohort = 'EXAMPLE')
       OR (:'cohort' = 'bounded_local' AND user_cohort = 'BOUNDED_LOCAL')
       OR (:'cohort' = 'real_non_example' AND user_cohort = 'REAL_NON_EXAMPLE')
       OR (:'cohort' = 'real_user' AND user_origin = 'REAL_USER')
       OR (:'cohort' = 'local_real_non_example_seed' AND user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED')
       OR (:'cohort' = 'non_example' AND user_cohort IN ('BOUNDED_LOCAL', 'REAL_NON_EXAMPLE'))
),
mix AS (
    SELECT COUNT(DISTINCT user_key) AS total_users,
           COUNT(DISTINCT user_key) FILTER (WHERE user_cohort = 'EXAMPLE') AS example_users,
           COUNT(DISTINCT user_key) FILTER (WHERE user_cohort = 'BOUNDED_LOCAL') AS bounded_local_users,
           COUNT(DISTINCT user_key) FILTER (WHERE user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED') AS local_real_non_example_seed_users,
           COUNT(DISTINCT user_key) FILTER (WHERE user_origin = 'REAL_USER') AS real_user_users,
           COUNT(DISTINCT user_key) FILTER (WHERE user_cohort = 'REAL_NON_EXAMPLE') AS real_non_example_users
    FROM scoped_rows
)
SELECT CASE
           WHEN total_users = 0 AND :'cohort' = 'real_non_example'
               THEN 'EMPTY_REAL_NON_EXAMPLE_COHORT'
           WHEN total_users = 0 AND :'cohort' = 'real_user'
               THEN 'EMPTY_REAL_USER_COHORT'
           WHEN total_users = 0 AND :'cohort' = 'local_real_non_example_seed'
               THEN 'EMPTY_LOCAL_REAL_NON_EXAMPLE_SEED_COHORT'
           WHEN total_users = 0 AND :'cohort' = 'bounded_local'
               THEN 'EMPTY_BOUNDED_LOCAL_COHORT'
           WHEN total_users = 0 AND :'cohort' = 'non_example'
               THEN 'EMPTY_NON_EXAMPLE_COHORT'
           WHEN total_users = 0
               THEN 'EMPTY_COHORT'
           WHEN :'cohort' = 'example'
               THEN 'EXAMPLE_ONLY_COHORT'
           WHEN :'cohort' = 'bounded_local'
               THEN 'BOUNDED_LOCAL_ONLY_COHORT'
           WHEN :'cohort' = 'real_user'
               THEN 'REAL_USER_ONLY_COHORT'
           WHEN :'cohort' = 'local_real_non_example_seed'
               THEN 'LOCAL_REAL_NON_EXAMPLE_SEED_ONLY_COHORT'
           WHEN :'cohort' = 'real_non_example'
               THEN 'REAL_NON_EXAMPLE_ONLY_COHORT'
           WHEN :'cohort' = 'non_example' AND bounded_local_users > 0 AND real_non_example_users = 0
               THEN 'BOUNDED_LOCAL_ONLY_NON_EXAMPLE_COHORT'
           WHEN :'cohort' = 'non_example' AND bounded_local_users = 0 AND local_real_non_example_seed_users > 0 AND real_user_users = 0
               THEN 'LOCAL_REAL_NON_EXAMPLE_SEED_ONLY_NON_EXAMPLE_COHORT'
           WHEN :'cohort' = 'non_example' AND bounded_local_users = 0 AND local_real_non_example_seed_users = 0 AND real_user_users > 0
               THEN 'REAL_USER_ONLY_NON_EXAMPLE_COHORT'
           WHEN :'cohort' = 'non_example'
               THEN 'MIXED_NON_EXAMPLE_COHORT'
           WHEN real_non_example_users = 0 AND bounded_local_users = 0
               THEN 'SYNTHETIC_ONLY_LATEST_BATCH'
           WHEN real_user_users = 0 AND local_real_non_example_seed_users = 0 AND bounded_local_users > 0
               THEN 'BOUNDED_LOCAL_WITH_SYNTHETIC_BATCH'
           WHEN real_user_users = 0 AND local_real_non_example_seed_users > 0 AND (example_users > 0 OR bounded_local_users > 0)
               THEN 'LOCAL_REAL_NON_EXAMPLE_SEED_WITH_NON_REAL_BATCH'
           WHEN real_user_users = 0 AND local_real_non_example_seed_users > 0
               THEN 'LOCAL_REAL_NON_EXAMPLE_SEED_ONLY_BATCH'
           WHEN real_user_users > 0 AND (example_users > 0 OR bounded_local_users > 0 OR local_real_non_example_seed_users > 0)
               THEN 'MIXED_WITH_NON_REAL_BATCH'
           ELSE 'REAL_USER_ONLY_BATCH'
       END
FROM mix;
SQL
