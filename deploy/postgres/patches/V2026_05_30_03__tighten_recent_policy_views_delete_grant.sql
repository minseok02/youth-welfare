SELECT format('REVOKE DELETE ON TABLE public.recent_policy_views FROM %I', 'app_core_rw')
WHERE to_regclass('public.recent_policy_views') IS NOT NULL \gexec
