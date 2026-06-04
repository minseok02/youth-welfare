CREATE TABLE IF NOT EXISTS policy_link_review_records (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT NOT NULL UNIQUE REFERENCES welfare_services(id) ON DELETE CASCADE,
    review_note TEXT,
    reviewed_by_user_key VARCHAR(100) NOT NULL,
    reviewed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plrr_reviewed_at ON policy_link_review_records(reviewed_at DESC);
