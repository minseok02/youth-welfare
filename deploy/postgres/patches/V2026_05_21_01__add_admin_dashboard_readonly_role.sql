SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'admin_ro_username', :'admin_ro_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'admin_ro_username') \gexec

SELECT format('ALTER ROLE %I LOGIN PASSWORD %L', :'admin_ro_username', :'admin_ro_password')
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'admin_ro_username') \gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', 'youth_welfare', :'admin_ro_username') \gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'admin_ro_username') \gexec
SELECT format('GRANT SELECT ON ALL TABLES IN SCHEMA public TO %I', :'admin_ro_username') \gexec
SELECT format(
    'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT SELECT ON TABLES TO %I',
    :'migration_username',
    :'admin_ro_username'
) \gexec
