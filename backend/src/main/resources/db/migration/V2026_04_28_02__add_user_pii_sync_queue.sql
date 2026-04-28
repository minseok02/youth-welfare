USE youth_welfare;

CREATE TABLE IF NOT EXISTS user_pii_sync_queue (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_key         CHAR(32)     NOT NULL,
    email_enc        VARCHAR(512),
    name_enc         VARCHAR(512),
    birth_date_enc   VARCHAR(128),
    phone_enc        VARCHAR(512),
    status           ENUM('PENDING','SYNCED','FAILED') NOT NULL DEFAULT 'PENDING',
    attempt_count    INT          NOT NULL DEFAULT 0,
    last_enqueued_at DATETIME,
    last_attempt_at  DATETIME,
    last_synced_at   DATETIME,
    last_error       VARCHAR(500),
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_upsq_user_key (user_key),
    KEY idx_upsq_status_enqueued (status, last_enqueued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
