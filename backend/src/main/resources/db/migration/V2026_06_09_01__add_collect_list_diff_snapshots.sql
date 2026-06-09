CREATE TABLE IF NOT EXISTS collect_list_snapshots (
    id            BIGSERIAL PRIMARY KEY,
    source_type   VARCHAR(30) NOT NULL,
    job_name      VARCHAR(50) NOT NULL,
    collected_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_count   INTEGER NOT NULL DEFAULT 0,
    new_count     INTEGER NOT NULL DEFAULT 0,
    changed_count INTEGER NOT NULL DEFAULT 0,
    missing_count INTEGER NOT NULL DEFAULT 0,
    metadata_json TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cls_source_collected
    ON collect_list_snapshots (source_type, collected_at DESC);

CREATE TABLE IF NOT EXISTS collect_list_snapshot_items (
    snapshot_id      BIGINT NOT NULL,
    source_id        VARCHAR(255) NOT NULL,
    fingerprint_hash VARCHAR(64) NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (snapshot_id, source_id),
    CONSTRAINT fk_clsi_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES collect_list_snapshots(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_clsi_source_id
    ON collect_list_snapshot_items (source_id);
