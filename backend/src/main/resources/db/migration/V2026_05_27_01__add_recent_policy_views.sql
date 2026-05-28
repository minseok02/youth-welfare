CREATE TABLE IF NOT EXISTS recent_policy_views (
    id             BIGSERIAL PRIMARY KEY,
    user_key       VARCHAR(32) NOT NULL,
    service_id     BIGINT NOT NULL,
    last_viewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_recent_policy_views_user_service UNIQUE (user_key, service_id),
    CONSTRAINT fk_rpv_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_rpv_user_key_last_viewed ON recent_policy_views (user_key, last_viewed_at);
CREATE INDEX IF NOT EXISTS idx_rpv_service ON recent_policy_views (service_id);
