SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'chat_session_cleanup_username', :'chat_session_cleanup_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'chat_session_cleanup_username') \gexec

SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'chat_session_cleanup_username', :'chat_session_cleanup_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'chat_session_cleanup_username') \gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', 'youth_welfare', :'chat_session_cleanup_username') \gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'chat_session_cleanup_username') \gexec
SELECT format('GRANT DELETE ON TABLE chat_sessions TO %I', :'chat_session_cleanup_username') \gexec
SELECT format('GRANT SELECT (id, user_key) ON TABLE chat_sessions TO %I', :'chat_session_cleanup_username') \gexec

SELECT format('REVOKE DELETE ON TABLE public.chat_sessions FROM %I', 'app_core_rw')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_core_rw')
  AND to_regclass('public.chat_sessions') IS NOT NULL \gexec
