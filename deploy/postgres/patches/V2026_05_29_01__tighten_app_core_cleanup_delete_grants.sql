SELECT format('REVOKE DELETE ON TABLE public.cluster_ai_results FROM %I', 'app_core_rw')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_core_rw')
  AND to_regclass('public.cluster_ai_results') IS NOT NULL \gexec

SELECT format('REVOKE DELETE ON TABLE public.collect_execution_locks FROM %I', 'app_core_rw')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_core_rw')
  AND to_regclass('public.collect_execution_locks') IS NOT NULL \gexec

SELECT format('REVOKE DELETE ON TABLE public.web_push_subscriptions FROM %I', 'app_core_rw')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_core_rw')
  AND to_regclass('public.web_push_subscriptions') IS NOT NULL \gexec
