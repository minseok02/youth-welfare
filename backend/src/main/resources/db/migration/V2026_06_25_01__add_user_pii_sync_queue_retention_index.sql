CREATE INDEX IF NOT EXISTS idx_upsq_status_synced
    ON user_pii_sync_queue (status, last_synced_at);
