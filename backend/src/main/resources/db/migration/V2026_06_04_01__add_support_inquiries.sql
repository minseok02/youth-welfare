CREATE TABLE IF NOT EXISTS support_inquiries (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT,
    user_key      VARCHAR(100),
    contact_email VARCHAR(320) NOT NULL,
    category      VARCHAR(50) NOT NULL,
    message       VARCHAR(2000) NOT NULL,
    route_path    VARCHAR(255),
    status        VARCHAR(30) NOT NULL,
    review_note   VARCHAR(1000),
    reviewed_by_user_key VARCHAR(100),
    reviewed_at   TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_support_inquiries_status_created_at
    ON support_inquiries (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_support_inquiries_user_key
    ON support_inquiries (user_key);
