SELECT format('GRANT SELECT ON TABLE public.policy_error_reports TO %I', :'admin_ro_username')
WHERE to_regclass('public.policy_error_reports') IS NOT NULL \gexec

SELECT format('GRANT SELECT ON TABLE public.support_inquiries TO %I', :'admin_ro_username')
WHERE to_regclass('public.support_inquiries') IS NOT NULL \gexec

SELECT format('GRANT SELECT ON TABLE public.policy_duplicate_review_records TO %I', :'admin_ro_username')
WHERE to_regclass('public.policy_duplicate_review_records') IS NOT NULL \gexec

SELECT format('GRANT SELECT ON TABLE public.policy_link_review_records TO %I', :'admin_ro_username')
WHERE to_regclass('public.policy_link_review_records') IS NOT NULL \gexec

SELECT format(
    'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.policy_error_reports TO %I',
    :'app_username'
)
WHERE to_regclass('public.policy_error_reports') IS NOT NULL \gexec

SELECT format(
    'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.support_inquiries TO %I',
    :'app_username'
)
WHERE to_regclass('public.support_inquiries') IS NOT NULL \gexec

SELECT format(
    'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.policy_duplicate_review_records TO %I',
    :'app_username'
)
WHERE to_regclass('public.policy_duplicate_review_records') IS NOT NULL \gexec

SELECT format(
    'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.policy_link_review_records TO %I',
    :'app_username'
)
WHERE to_regclass('public.policy_link_review_records') IS NOT NULL \gexec

SELECT format(
    'GRANT USAGE, SELECT ON SEQUENCE public.policy_error_reports_id_seq TO %I',
    :'app_username'
)
WHERE to_regclass('public.policy_error_reports_id_seq') IS NOT NULL \gexec

SELECT format(
    'GRANT USAGE, SELECT ON SEQUENCE public.support_inquiries_id_seq TO %I',
    :'app_username'
)
WHERE to_regclass('public.support_inquiries_id_seq') IS NOT NULL \gexec

SELECT format(
    'GRANT USAGE, SELECT ON SEQUENCE public.policy_duplicate_review_records_id_seq TO %I',
    :'app_username'
)
WHERE to_regclass('public.policy_duplicate_review_records_id_seq') IS NOT NULL \gexec

SELECT format(
    'GRANT USAGE, SELECT ON SEQUENCE public.policy_link_review_records_id_seq TO %I',
    :'app_username'
)
WHERE to_regclass('public.policy_link_review_records_id_seq') IS NOT NULL \gexec
