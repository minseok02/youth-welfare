CREATE INDEX IF NOT EXISTS idx_svl_viewed_service ON service_view_logs (viewed_at, service_id);

CREATE INDEX IF NOT EXISTS idx_ws_description_trgm
    ON welfare_services
    USING GIN (lower(description) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_ws_support_content_trgm
    ON welfare_services
    USING GIN (lower(support_content) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_ws_search_document_trgm
    ON welfare_services
    USING GIN (lower(
        coalesce(title, '')
        || ' ' || coalesce(description, '')
        || ' ' || coalesce(support_content, '')
        || ' ' || coalesce(keyword, '')
    ) gin_trgm_ops);
