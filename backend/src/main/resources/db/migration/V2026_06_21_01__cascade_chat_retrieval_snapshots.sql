DELETE FROM chat_retrieval_snapshots crs
WHERE crs.session_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM chat_sessions cs
      WHERE cs.id = crs.session_id
  );

CREATE INDEX IF NOT EXISTS idx_crs_session_id
    ON chat_retrieval_snapshots (session_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_crs_session'
    ) THEN
        ALTER TABLE chat_retrieval_snapshots
            ADD CONSTRAINT fk_crs_session
            FOREIGN KEY (session_id)
            REFERENCES chat_sessions(id)
            ON DELETE CASCADE;
    END IF;
END $$;
