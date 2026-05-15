#!/usr/bin/env bash
set -euo pipefail

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
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
latest_rows AS (
    SELECT ur.*
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
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
    SELECT DISTINCT lr.user_key,
           CASE WHEN pp.user_key IS NULL THEN 'NO_PRIORITY' ELSE 'HAS_PRIORITY' END AS priority_state
    FROM latest_rows lr
    LEFT JOIN priority_profiles pp
      ON pp.user_key = lr.user_key
),
top1 AS (
    SELECT *
    FROM (
        SELECT lr.user_key,
               lr.service_id,
               lr.final_score,
               ROW_NUMBER() OVER (
                   PARTITION BY lr.user_key
                   ORDER BY lr.final_score DESC, lr.id DESC
               ) AS rn
        FROM latest_rows lr
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
        SELECT lr.user_key,
               COUNT(*) AS rec_count,
               COUNT(DISTINCT lr.service_id) AS distinct_services,
               COUNT(DISTINCT COALESCE(ws.unified_category, '기타')) AS distinct_categories,
               COUNT(DISTINCT ws.source_type) AS distinct_sources
        FROM latest_rows lr
        JOIN welfare_services ws
          ON ws.id = lr.service_id
        GROUP BY lr.user_key
    ) per_user
)
SELECT 'latest_batch_rows=' || COUNT(*) FROM latest_rows
UNION ALL
SELECT 'latest_batch_users=' || COUNT(DISTINCT user_key) FROM latest_rows
UNION ALL
SELECT 'latest_batch_distinct_services=' || COUNT(DISTINCT service_id) FROM latest_rows
UNION ALL
SELECT 'latest_batch_priority_users=' || COUNT(*) FILTER (WHERE priority_state = 'HAS_PRIORITY') FROM priority_states
UNION ALL
SELECT 'latest_batch_no_priority_users=' || COUNT(*) FILTER (WHERE priority_state = 'NO_PRIORITY') FROM priority_states
UNION ALL
SELECT 'top1_leader_service_id=' || service_id FROM top1_leader
UNION ALL
SELECT 'top1_leader_title=' || title FROM top1_leader
UNION ALL
SELECT 'top1_leader_source=' || source_type FROM top1_leader
UNION ALL
SELECT 'top1_leader_category=' || category FROM top1_leader
UNION ALL
SELECT 'top1_leader_users=' || users_as_top1 FROM top1_leader
UNION ALL
SELECT 'top1_leader_share_pct=' || share_pct FROM top1_leader
UNION ALL
SELECT 'avg_recommendations_per_user=' || avg_rec_count FROM diversity
UNION ALL
SELECT 'avg_distinct_services_per_user=' || avg_distinct_services FROM diversity
UNION ALL
SELECT 'avg_distinct_categories_per_user=' || avg_distinct_categories FROM diversity
UNION ALL
SELECT 'avg_distinct_sources_per_user=' || avg_distinct_sources FROM diversity
UNION ALL
SELECT 'min_distinct_categories_per_user=' || min_distinct_categories FROM diversity
UNION ALL
SELECT 'max_distinct_categories_per_user=' || max_distinct_categories FROM diversity
UNION ALL
SELECT 'min_distinct_sources_per_user=' || min_distinct_sources FROM diversity
UNION ALL
SELECT 'max_distinct_sources_per_user=' || max_distinct_sources FROM diversity;

SELECT '[latest_source_distribution]';
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
latest_rows AS (
    SELECT ur.*
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
)
SELECT ws.source_type || '|' || COUNT(*) || '|' || COUNT(DISTINCT lr.user_key)
FROM latest_rows lr
JOIN welfare_services ws
  ON ws.id = lr.service_id
GROUP BY ws.source_type
ORDER BY COUNT(*) DESC, ws.source_type;

SELECT '[latest_category_distribution]';
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
latest_rows AS (
    SELECT ur.*
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
)
SELECT COALESCE(ws.unified_category, '기타') || '|' || COUNT(*) || '|' || COUNT(DISTINCT lr.user_key)
FROM latest_rows lr
JOIN welfare_services ws
  ON ws.id = lr.service_id
GROUP BY COALESCE(ws.unified_category, '기타')
ORDER BY COUNT(*) DESC, COALESCE(ws.unified_category, '기타');

SELECT '[top_repeated_services]';
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
latest_rows AS (
    SELECT ur.*
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
)
SELECT lr.service_id || '|' || ws.title || '|' || ws.source_type || '|' ||
       COALESCE(ws.unified_category, '기타') || '|' || COUNT(*) || '|' || COUNT(DISTINCT lr.user_key)
FROM latest_rows lr
JOIN welfare_services ws
  ON ws.id = lr.service_id
GROUP BY lr.service_id, ws.title, ws.source_type, COALESCE(ws.unified_category, '기타')
ORDER BY COUNT(*) DESC, lr.service_id
LIMIT 15;

SELECT '[top1_services]';
WITH latest AS (
    SELECT user_key, MAX(recommended_at) AS recommended_at
    FROM user_recommendations
    GROUP BY user_key
),
latest_rows AS (
    SELECT ur.*
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
),
top1 AS (
    SELECT *
    FROM (
        SELECT lr.user_key,
               lr.service_id,
               lr.final_score,
               ROW_NUMBER() OVER (
                   PARTITION BY lr.user_key
                   ORDER BY lr.final_score DESC, lr.id DESC
               ) AS rn
        FROM latest_rows lr
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
latest_rows AS (
    SELECT ur.*
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
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
    SELECT DISTINCT lr.user_key,
           CASE WHEN pp.user_key IS NULL THEN 'NO_PRIORITY' ELSE 'HAS_PRIORITY' END AS priority_state
    FROM latest_rows lr
    LEFT JOIN priority_profiles pp
      ON pp.user_key = lr.user_key
),
top1 AS (
    SELECT *
    FROM (
        SELECT lr.user_key,
               lr.service_id,
               lr.final_score,
               ROW_NUMBER() OVER (
                   PARTITION BY lr.user_key
                   ORDER BY lr.final_score DESC, lr.id DESC
               ) AS rn
        FROM latest_rows lr
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
WITH priority_profiles AS (
    SELECT up.user_key,
           STRING_AGG(po.code, '>' ORDER BY up.priority_rank) AS profile
    FROM user_priorities up
    JOIN priority_options po
      ON po.id = up.priority_option_id
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
latest_rows AS (
    SELECT ur.*
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
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
        SELECT lr.user_key,
               lr.service_id,
               lr.final_score,
               ROW_NUMBER() OVER (
                   PARTITION BY lr.user_key
                   ORDER BY lr.final_score DESC, lr.id DESC
               ) AS rn
        FROM latest_rows lr
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
latest_rows AS (
    SELECT ur.*
    FROM user_recommendations ur
    JOIN latest l
      ON l.user_key = ur.user_key
     AND l.recommended_at = ur.recommended_at
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
    SELECT DISTINCT lr.user_key,
           CASE WHEN pp.user_key IS NULL THEN 'NO_PRIORITY' ELSE 'HAS_PRIORITY' END AS priority_state
    FROM latest_rows lr
    LEFT JOIN priority_profiles pp
      ON pp.user_key = lr.user_key
),
top1 AS (
    SELECT *
    FROM (
        SELECT lr.user_key,
               lr.service_id,
               lr.final_score,
               ROW_NUMBER() OVER (
                   PARTITION BY lr.user_key
                   ORDER BY lr.final_score DESC, lr.id DESC
               ) AS rn
        FROM latest_rows lr
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
           WHEN top1_leader_users * 100.0 / NULLIF(top1_users, 0) >= 50 THEN 'CONCENTRATED_TOP1'
           WHEN no_priority_users > priority_users * 2 THEN 'NO_PRIORITY_DOMINANT'
           ELSE 'BALANCED_ENOUGH_FOR_LOGIC_REVIEW'
       END
FROM base;
SQL
