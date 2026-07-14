CREATE INDEX IF NOT EXISTS idx_ws_active_latest_list_sort
    ON welfare_services (
        (COALESCE(last_modified_at, registered_at, created_at)) DESC,
        id DESC
    )
    WHERE status IN ('ACTIVE', 'UPCOMING');

CREATE INDEX IF NOT EXISTS idx_ws_active_deadline_list_sort
    ON welfare_services (
        (COALESCE(apply_end_date, DATE '9999-12-31')) ASC,
        (COALESCE(last_modified_at, registered_at, created_at)) DESC,
        id DESC
    )
    WHERE status IN ('ACTIVE', 'UPCOMING');

CREATE INDEX IF NOT EXISTS idx_ws_active_views_list_sort
    ON welfare_services (
        (COALESCE(api_view_count, 0)) DESC,
        (COALESCE(view_count, 0)) DESC,
        (COALESCE(last_modified_at, registered_at, created_at)) DESC,
        id DESC
    )
    WHERE status IN ('ACTIVE', 'UPCOMING');
