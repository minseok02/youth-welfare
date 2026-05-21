UPDATE users u
SET email = 'shadow_' || au.email_lookup_hash
FROM auth_users au
JOIN youth_welfare_pii.user_pii upii ON upii.user_key = au.user_key
WHERE u.user_key = au.user_key
  AND upii.email_enc IS NOT NULL
  AND u.withdrawn_at IS NULL
  AND u.email NOT LIKE 'withdrawn_%'
  AND split_part(lower(u.email), '@', 2) NOT IN (
    'example.com',
    'example.org',
    'example.net',
    'youth-welfare.dev',
    'realuser.app',
    'smoke.local',
    'localhost'
  )
  AND split_part(lower(u.email), '@', 2) !~ '(\\.local|\\.test|\\.invalid)$'
  AND u.email <> 'shadow_' || au.email_lookup_hash;
