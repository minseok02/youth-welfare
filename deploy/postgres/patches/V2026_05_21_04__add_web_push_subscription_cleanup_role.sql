SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'web_push_subscription_cleanup_username', :'web_push_subscription_cleanup_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'web_push_subscription_cleanup_username') \gexec

SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'web_push_subscription_cleanup_username', :'web_push_subscription_cleanup_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'web_push_subscription_cleanup_username') \gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', 'youth_welfare', :'web_push_subscription_cleanup_username') \gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'web_push_subscription_cleanup_username') \gexec
SELECT format('GRANT DELETE ON TABLE web_push_subscriptions TO %I', :'web_push_subscription_cleanup_username') \gexec
SELECT format('GRANT SELECT (id, user_key) ON TABLE web_push_subscriptions TO %I', :'web_push_subscription_cleanup_username') \gexec
