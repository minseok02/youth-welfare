CREATE TABLE IF NOT EXISTS user_consents (
    id           BIGSERIAL PRIMARY KEY,
    user_key     VARCHAR(32) NOT NULL,
    consent_type VARCHAR(40) NOT NULL,
    agreed_at    TIMESTAMP NOT NULL,
    withdrawn_at TIMESTAMP,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_consents_user_type UNIQUE (user_key, consent_type),
    CONSTRAINT fk_user_consents_user_key FOREIGN KEY (user_key) REFERENCES users(user_key) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_consents_user_key_active
    ON user_consents (user_key, consent_type)
    WHERE withdrawn_at IS NULL;

INSERT INTO user_consents (user_key, consent_type, agreed_at)
SELECT u.user_key, 'PRIVACY_NOTICE', COALESCE(u.created_at, CURRENT_TIMESTAMP)
FROM users u
WHERE u.user_key IS NOT NULL
ON CONFLICT (user_key, consent_type) DO NOTHING;

INSERT INTO user_consents (user_key, consent_type, agreed_at)
SELECT u.user_key, 'OPTIONAL_PROFILE', COALESCE(u.created_at, CURRENT_TIMESTAMP)
FROM users u
WHERE u.user_key IS NOT NULL
  AND (
        NULLIF(BTRIM(COALESCE(u.sido, '')), '') IS NOT NULL
     OR NULLIF(BTRIM(COALESCE(u.sgg, '')), '') IS NOT NULL
     OR u.income_level IS NOT NULL
     OR NULLIF(BTRIM(COALESCE(u.household_type, '')), '') IS NOT NULL
     OR NULLIF(BTRIM(COALESCE(u.employment_status, '')), '') IS NOT NULL
     OR NULLIF(BTRIM(COALESCE(u.house_tenure_code, '')), '') IS NOT NULL
     OR NULLIF(BTRIM(COALESCE(u.housing_type_code, '')), '') IS NOT NULL
     OR NULLIF(BTRIM(COALESCE(u.basic_living_recipient_type_code, '')), '') IS NOT NULL
     OR EXISTS (
            SELECT 1
            FROM user_attributes ua
            WHERE ua.user_key = u.user_key
              AND ua.attr_type IN ('INTEREST_FIELD', 'TARGET_TYPE')
        )
     OR EXISTS (
            SELECT 1
            FROM user_priorities upr
            WHERE upr.user_key = u.user_key
        )
  )
ON CONFLICT (user_key, consent_type) DO NOTHING;

INSERT INTO user_consents (user_key, consent_type, agreed_at)
SELECT u.user_key, 'SENSITIVE_INFO', COALESCE(u.created_at, CURRENT_TIMESTAMP)
FROM users u
WHERE u.user_key IS NOT NULL
  AND NULLIF(BTRIM(COALESCE(u.disability_grade_code, '')), '') IS NOT NULL
ON CONFLICT (user_key, consent_type) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_core_rw') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.user_consents TO app_core_rw;
        GRANT USAGE, SELECT ON SEQUENCE public.user_consents_id_seq TO app_core_rw;
    END IF;
END
$$;
