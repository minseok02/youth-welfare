ALTER TABLE welfare_services
    ADD COLUMN IF NOT EXISTS title_l TEXT
        GENERATED ALWAYS AS (lower(coalesce(title, ''))) STORED,
    ADD COLUMN IF NOT EXISTS keyword_l TEXT
        GENERATED ALWAYS AS (lower(coalesce(keyword, ''))) STORED,
    ADD COLUMN IF NOT EXISTS search_document_vector TSVECTOR
        GENERATED ALWAYS AS (
            to_tsvector('simple'::regconfig, lower(
                coalesce(title, '')
                || ' ' || coalesce(description, '')
                || ' ' || coalesce(support_content, '')
                || ' ' || coalesce(keyword, '')
            ))
        ) STORED;

CREATE INDEX IF NOT EXISTS idx_ws_title_l_trgm
    ON welfare_services
    USING GIN (title_l gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_ws_keyword_l_trgm
    ON welfare_services
    USING GIN (keyword_l gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_ws_search_document_vector_gin
    ON welfare_services
    USING GIN (search_document_vector);
