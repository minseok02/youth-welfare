USE youth_welfare;

CREATE TABLE IF NOT EXISTS raw_api_payloads (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    source_type  ENUM('YOUTH','BOKJIRO_CENTRAL','BOKJIRO_LOCAL') NOT NULL,
    source_id    VARCHAR(50) NOT NULL,
    api_category ENUM('LIST','DETAIL') NOT NULL,
    payload_json LONGTEXT NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    fetched_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_raw_source (source_type, source_id, api_category),
    KEY idx_raw_fetched (fetched_at),
    KEY idx_raw_hash (payload_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
