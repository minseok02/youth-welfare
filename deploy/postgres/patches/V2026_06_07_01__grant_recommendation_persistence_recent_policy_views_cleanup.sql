SELECT format('GRANT DELETE ON TABLE public.recent_policy_views TO %I', :'recommendation_persistence_command_username')
WHERE to_regclass('public.recent_policy_views') IS NOT NULL \gexec

SELECT format('GRANT SELECT (user_key) ON TABLE public.recent_policy_views TO %I', :'recommendation_persistence_command_username')
WHERE to_regclass('public.recent_policy_views') IS NOT NULL \gexec
