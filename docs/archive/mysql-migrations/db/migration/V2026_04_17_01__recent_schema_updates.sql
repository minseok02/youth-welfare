-- Youth Welfare recent schema updates
-- Target: existing databases initialized before 2026-04-17
-- Run manually against the target schema before deploying the current backend.

USE youth_welfare;

-- service_view_logs
CREATE TABLE IF NOT EXISTS service_view_logs (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    service_id         BIGINT       NOT NULL,
    user_id            BIGINT,
    client_fingerprint VARCHAR(64)  NOT NULL,
    viewed_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_svl_service_viewed (service_id, viewed_at),
    KEY idx_svl_user_service_viewed (user_id, service_id, viewed_at),
    KEY idx_svl_fp_service_viewed (client_fingerprint, service_id, viewed_at),
    CONSTRAINT fk_svl_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- notifications
CREATE TABLE IF NOT EXISTS notifications (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    channel        ENUM('email','kakao') NOT NULL,
    period_type    ENUM('daily','weekly','manual') NOT NULL,
    status         ENUM('sent','failed') NOT NULL,
    subject        VARCHAR(200) NOT NULL,
    message_text   TEXT,
    total_services INT          NOT NULL DEFAULT 0,
    sent_at        DATETIME,
    error_message  VARCHAR(500),
    retry_count    INT          NOT NULL DEFAULT 0,
    next_retry_at  DATETIME,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_noti_user_created (user_id, created_at),
    KEY idx_noti_status_created (status, created_at),
    KEY idx_noti_retry (status, next_retry_at),
    CONSTRAINT fk_noti_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- notification_services
CREATE TABLE IF NOT EXISTS notification_services (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    notification_id       BIGINT       NOT NULL,
    service_id            BIGINT       NOT NULL,
    recommendation_log_id BIGINT,
    rank_order            INT          NOT NULL,
    final_score           DECIMAL(6,5),
    service_title         VARCHAR(255),
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ns_notification (notification_id),
    KEY idx_ns_service (service_id),
    KEY idx_ns_log (recommendation_log_id),
    CONSTRAINT fk_ns_notification FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE,
    CONSTRAINT fk_ns_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE,
    CONSTRAINT fk_ns_log FOREIGN KEY (recommendation_log_id) REFERENCES recommendation_logs(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- notifications retry columns for databases that already have the table
SET @retry_count_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'notifications'
      AND column_name = 'retry_count'
);
SET @retry_count_sql := IF(
    @retry_count_exists = 0,
    'ALTER TABLE notifications ADD COLUMN retry_count INT NOT NULL DEFAULT 0 AFTER error_message',
    'SELECT 1'
);
PREPARE stmt_retry_count FROM @retry_count_sql;
EXECUTE stmt_retry_count;
DEALLOCATE PREPARE stmt_retry_count;

SET @next_retry_at_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'notifications'
      AND column_name = 'next_retry_at'
);
SET @next_retry_at_sql := IF(
    @next_retry_at_exists = 0,
    'ALTER TABLE notifications ADD COLUMN next_retry_at DATETIME AFTER retry_count',
    'SELECT 1'
);
PREPARE stmt_next_retry_at FROM @next_retry_at_sql;
EXECUTE stmt_next_retry_at;
DEALLOCATE PREPARE stmt_next_retry_at;

SET @idx_noti_retry_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'notifications'
      AND index_name = 'idx_noti_retry'
);
SET @idx_noti_retry_sql := IF(
    @idx_noti_retry_exists = 0,
    'ALTER TABLE notifications ADD INDEX idx_noti_retry (status, next_retry_at)',
    'SELECT 1'
);
PREPARE stmt_idx_noti_retry FROM @idx_noti_retry_sql;
EXECUTE stmt_idx_noti_retry;
DEALLOCATE PREPARE stmt_idx_noti_retry;
