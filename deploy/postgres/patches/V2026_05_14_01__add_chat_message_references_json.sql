ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS references_json TEXT;
