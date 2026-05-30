CREATE INDEX IF NOT EXISTS idx_ws_title_trgm
    ON welfare_services
    USING GIN (lower(title) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_ws_keyword_trgm
    ON welfare_services
    USING GIN (lower(keyword) gin_trgm_ops);
