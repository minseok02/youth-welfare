CREATE TABLE IF NOT EXISTS web_push_subscriptions (
    id                 BIGSERIAL PRIMARY KEY,
    user_key           VARCHAR(32) NOT NULL,
    endpoint           VARCHAR(500) NOT NULL,
    p256dh             VARCHAR(255) NOT NULL,
    auth_secret        VARCHAR(255) NOT NULL,
    user_agent         VARCHAR(500),
    device_label       VARCHAR(100),
    enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at       TIMESTAMP,
    last_sent_at       TIMESTAMP,
    last_error_at      TIMESTAMP,
    last_error_message VARCHAR(500),
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_wps_endpoint UNIQUE (endpoint)
);

CREATE INDEX IF NOT EXISTS idx_wps_user_key_enabled_created
    ON web_push_subscriptions (user_key, enabled, created_at DESC);
