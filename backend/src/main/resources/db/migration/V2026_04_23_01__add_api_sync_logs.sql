USE youth_welfare;

CREATE TABLE IF NOT EXISTS api_sync_logs (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    job_name        VARCHAR(50) NOT NULL,
    status          VARCHAR(30) NOT NULL,
    started_at      DATETIME    NOT NULL,
    finished_at     DATETIME,
    requested_count INT         NOT NULL DEFAULT 0,
    saved_count     INT         NOT NULL DEFAULT 0,
    skipped_count   INT         NOT NULL DEFAULT 0,
    filtered_count  INT         NOT NULL DEFAULT 0,
    failed_count    INT         NOT NULL DEFAULT 0,
    error_code      VARCHAR(50),
    error_message   VARCHAR(1000),
    metadata_json   LONGTEXT,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_api_sync_job_started (job_name, started_at),
    KEY idx_api_sync_status_started (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
