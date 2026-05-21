SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'collect_execution_lock_cleanup_username', :'collect_execution_lock_cleanup_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'collect_execution_lock_cleanup_username') \gexec

SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'collect_execution_lock_cleanup_username', :'collect_execution_lock_cleanup_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'collect_execution_lock_cleanup_username') \gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', 'youth_welfare', :'collect_execution_lock_cleanup_username') \gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'collect_execution_lock_cleanup_username') \gexec
SELECT format('GRANT DELETE ON TABLE collect_execution_locks TO %I', :'collect_execution_lock_cleanup_username') \gexec
SELECT format('GRANT SELECT (lock_name, owner_token) ON TABLE collect_execution_locks TO %I', :'collect_execution_lock_cleanup_username') \gexec
