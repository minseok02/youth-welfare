DO $$
DECLARE
    blocker_count bigint;
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_auth_users_user_key'
    ) THEN
        ALTER TABLE public.auth_users
            ADD CONSTRAINT fk_auth_users_user_key
            FOREIGN KEY (user_key)
            REFERENCES public.users(user_key)
            ON DELETE CASCADE
            NOT VALID;
    END IF;

    SELECT count(*)
    INTO blocker_count
    FROM public.auth_users au
    LEFT JOIN public.users u ON u.user_key = au.user_key
    WHERE u.user_key IS NULL;

    IF blocker_count = 0 THEN
        ALTER TABLE public.auth_users VALIDATE CONSTRAINT fk_auth_users_user_key;
    ELSE
        RAISE NOTICE 'fk_auth_users_user_key remains NOT VALID: % blocker rows', blocker_count;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_user_profiles_user_key'
    ) THEN
        ALTER TABLE public.user_profiles
            ADD CONSTRAINT fk_user_profiles_user_key
            FOREIGN KEY (user_key)
            REFERENCES public.users(user_key)
            ON DELETE CASCADE
            NOT VALID;
    END IF;

    SELECT count(*)
    INTO blocker_count
    FROM public.user_profiles up
    LEFT JOIN public.users u ON u.user_key = up.user_key
    WHERE u.user_key IS NULL;

    IF blocker_count = 0 THEN
        ALTER TABLE public.user_profiles VALIDATE CONSTRAINT fk_user_profiles_user_key;
    ELSE
        RAISE NOTICE 'fk_user_profiles_user_key remains NOT VALID: % blocker rows', blocker_count;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_user_pii_user_key'
    ) THEN
        ALTER TABLE youth_welfare_pii.user_pii
            ADD CONSTRAINT fk_user_pii_user_key
            FOREIGN KEY (user_key)
            REFERENCES public.users(user_key)
            ON DELETE CASCADE
            NOT VALID;
    END IF;

    SELECT count(*)
    INTO blocker_count
    FROM youth_welfare_pii.user_pii p
    LEFT JOIN public.users u ON u.user_key = p.user_key
    WHERE u.user_key IS NULL;

    IF blocker_count = 0 THEN
        ALTER TABLE youth_welfare_pii.user_pii VALIDATE CONSTRAINT fk_user_pii_user_key;
    ELSE
        RAISE NOTICE 'fk_user_pii_user_key remains NOT VALID: % blocker rows', blocker_count;
    END IF;
END $$;
