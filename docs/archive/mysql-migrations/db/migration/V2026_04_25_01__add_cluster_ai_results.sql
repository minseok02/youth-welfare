-- 군집별 AI 점수 캐시 테이블
-- 같은 군집에 속한 두 번째 사용자부터는 AI 호출 없이 캐시 사용
CREATE TABLE IF NOT EXISTS cluster_ai_results (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    cluster_id    VARCHAR(50)     NOT NULL,
    service_id    BIGINT          NOT NULL,
    ai_score      DECIMAL(5,2)    NOT NULL,
    ai_reason     VARCHAR(500)    NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_car (cluster_id, service_id),
    KEY idx_car_cluster (cluster_id),
    CONSTRAINT fk_car_service FOREIGN KEY (service_id) REFERENCES welfare_services(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
