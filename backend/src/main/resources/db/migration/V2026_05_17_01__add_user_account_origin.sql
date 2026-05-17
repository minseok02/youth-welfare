ALTER TABLE users
    ADD COLUMN IF NOT EXISTS account_origin VARCHAR(40) NOT NULL DEFAULT 'REAL_USER';

UPDATE users
SET account_origin = CASE
    WHEN lower(split_part(coalesce(email, ''), '@', 2)) = 'example.com' THEN 'EXAMPLE_SMOKE'
    WHEN lower(split_part(coalesce(email, ''), '@', 2)) = 'smoke.local'
        OR lower(split_part(coalesce(email, ''), '@', 2)) = 'localhost'
        OR lower(split_part(coalesce(email, ''), '@', 2)) LIKE '%.local'
        OR lower(split_part(coalesce(email, ''), '@', 2)) LIKE '%.test'
        OR lower(split_part(coalesce(email, ''), '@', 2)) LIKE '%.invalid'
        THEN 'BOUNDED_LOCAL'
    WHEN lower(split_part(coalesce(email, ''), '@', 2)) = 'cohortseed.app' THEN 'LOCAL_REAL_NON_EXAMPLE_SEED'
    ELSE 'REAL_USER'
END
WHERE account_origin IS NULL
   OR account_origin = ''
   OR account_origin = 'REAL_USER';
