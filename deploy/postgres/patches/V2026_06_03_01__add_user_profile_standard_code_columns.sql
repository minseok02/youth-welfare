ALTER TABLE users
    ADD COLUMN IF NOT EXISTS house_tenure_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS housing_type_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS basic_living_recipient_type_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS disability_grade_code VARCHAR(20);

ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS house_tenure_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS housing_type_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS basic_living_recipient_type_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS disability_grade_code VARCHAR(20);
