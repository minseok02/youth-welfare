CREATE TABLE IF NOT EXISTS policy_error_reports (
    id          BIGSERIAL PRIMARY KEY,
    policy_id   BIGINT NOT NULL,
    user_id     BIGINT,
    user_key    VARCHAR(32),
    reason_code VARCHAR(40) NOT NULL,
    note        VARCHAR(1000),
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_per_policy FOREIGN KEY (policy_id) REFERENCES welfare_services(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_per_policy_created ON policy_error_reports (policy_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_per_status_created ON policy_error_reports (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_per_user_key_created ON policy_error_reports (user_key, created_at DESC);
