ALTER TABLE chat_sessions
    ADD COLUMN IF NOT EXISTS context_state_json TEXT;
