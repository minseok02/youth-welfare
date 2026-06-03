ALTER TABLE policy_error_reports
    ADD COLUMN IF NOT EXISTS review_note VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS reviewed_by_user_key VARCHAR(100),
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;

ALTER TABLE support_inquiries
    ADD COLUMN IF NOT EXISTS review_note VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS reviewed_by_user_key VARCHAR(100),
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;
