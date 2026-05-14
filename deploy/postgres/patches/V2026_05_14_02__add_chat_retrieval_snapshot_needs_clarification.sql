ALTER TABLE chat_retrieval_snapshots
    ADD COLUMN IF NOT EXISTS needs_clarification BOOLEAN;
