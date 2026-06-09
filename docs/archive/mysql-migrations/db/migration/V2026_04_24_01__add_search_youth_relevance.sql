USE youth_welfare;

SET @search_youth_relevant_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'welfare_services'
      AND column_name = 'search_youth_relevant'
);
SET @search_youth_relevant_sql := IF(
    @search_youth_relevant_exists = 0,
    'ALTER TABLE welfare_services ADD COLUMN search_youth_relevant TINYINT(1) NOT NULL DEFAULT 1 AFTER is_youth_specific',
    'SELECT 1'
);
PREPARE stmt_search_youth_relevant FROM @search_youth_relevant_sql;
EXECUTE stmt_search_youth_relevant;
DEALLOCATE PREPARE stmt_search_youth_relevant;

SET @idx_ws_search_youth_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'welfare_services'
      AND index_name = 'idx_ws_search_youth'
);
SET @idx_ws_search_youth_sql := IF(
    @idx_ws_search_youth_exists = 0,
    'ALTER TABLE welfare_services ADD INDEX idx_ws_search_youth (search_youth_relevant)',
    'SELECT 1'
);
PREPARE stmt_idx_ws_search_youth FROM @idx_ws_search_youth_sql;
EXECUTE stmt_idx_ws_search_youth;
DEALLOCATE PREPARE stmt_idx_ws_search_youth;
