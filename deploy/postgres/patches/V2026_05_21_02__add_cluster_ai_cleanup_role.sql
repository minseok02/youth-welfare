SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'cluster_ai_cleanup_username', :'cluster_ai_cleanup_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'cluster_ai_cleanup_username') \gexec

SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'cluster_ai_cleanup_username', :'cluster_ai_cleanup_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'cluster_ai_cleanup_username') \gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', 'youth_welfare', :'cluster_ai_cleanup_username') \gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'cluster_ai_cleanup_username') \gexec
SELECT format('GRANT DELETE ON TABLE cluster_ai_results TO %I', :'cluster_ai_cleanup_username') \gexec
SELECT format('GRANT SELECT (created_at) ON TABLE cluster_ai_results TO %I', :'cluster_ai_cleanup_username') \gexec
