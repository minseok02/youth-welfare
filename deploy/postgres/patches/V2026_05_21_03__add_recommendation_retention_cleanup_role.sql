SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'recommendation_retention_cleanup_username', :'recommendation_retention_cleanup_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'recommendation_retention_cleanup_username') \gexec

SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'recommendation_retention_cleanup_username', :'recommendation_retention_cleanup_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'recommendation_retention_cleanup_username') \gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', 'youth_welfare', :'recommendation_retention_cleanup_username') \gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'recommendation_retention_cleanup_username') \gexec
SELECT format('GRANT DELETE ON TABLE user_recommendations TO %I', :'recommendation_retention_cleanup_username') \gexec
SELECT format('GRANT SELECT (recommended_at, is_bookmarked) ON TABLE user_recommendations TO %I', :'recommendation_retention_cleanup_username') \gexec
