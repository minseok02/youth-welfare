CREATE TABLE IF NOT EXISTS policy_duplicate_review_records (
    id BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    host_org_key VARCHAR(255) NOT NULL DEFAULT '',
    host_org_label VARCHAR(255),
    review_note TEXT,
    reviewed_by_user_key VARCHAR(100) NOT NULL,
    reviewed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_policy_duplicate_review_records_group
    ON policy_duplicate_review_records (source_type, title, host_org_key);
