USE youth_welfare;

ALTER TABLE user_recommendations
    DROP FOREIGN KEY fk_ur_user,
    DROP INDEX uq_ur_user_service_time,
    DROP INDEX idx_ur_user_score,
    DROP INDEX idx_ur_bookmark,
    MODIFY COLUMN user_key CHAR(32) NOT NULL,
    ADD CONSTRAINT uq_ur_user_key_service_time UNIQUE (user_key, service_id, recommended_at),
    DROP COLUMN user_id;

ALTER TABLE recommendation_logs
    DROP FOREIGN KEY fk_rl_user,
    DROP INDEX idx_rl_user,
    MODIFY COLUMN user_key CHAR(32) NOT NULL,
    DROP COLUMN user_id;

ALTER TABLE notifications
    DROP FOREIGN KEY fk_noti_user,
    DROP INDEX idx_noti_user_created,
    MODIFY COLUMN user_key CHAR(32) NOT NULL,
    DROP COLUMN user_id;

ALTER TABLE chat_sessions
    DROP FOREIGN KEY fk_cs_user,
    DROP INDEX idx_cs_user_last_message,
    DROP INDEX idx_cs_user_created,
    MODIFY COLUMN user_key CHAR(32) NOT NULL,
    DROP COLUMN user_id;

ALTER TABLE service_view_logs
    DROP INDEX idx_svl_user_service_viewed,
    DROP COLUMN user_id;
