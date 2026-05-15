CREATE TABLE IF NOT EXISTS user_alerts (
    id            BIGSERIAL PRIMARY KEY,
    user_key      VARCHAR(32) NOT NULL,
    event_key     VARCHAR(120),
    kind          VARCHAR(32) NOT NULL,
    status        VARCHAR(16) NOT NULL,
    title         VARCHAR(200) NOT NULL,
    body          TEXT,
    deeplink_url  VARCHAR(500),
    metadata_json TEXT,
    read_at       TIMESTAMP,
    hidden_at     TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_alert_event_key UNIQUE (event_key)
);

CREATE INDEX IF NOT EXISTS idx_ua_user_key_created
    ON user_alerts (user_key, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ua_user_key_status_created
    ON user_alerts (user_key, status, created_at DESC);
