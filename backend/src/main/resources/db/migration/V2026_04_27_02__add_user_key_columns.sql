SET SESSION sql_log_bin = 0;

ALTER TABLE users
    ADD COLUMN user_key CHAR(32) NULL DEFAULT (REPLACE(UUID(), '-', '')) AFTER id;

UPDATE users
SET user_key = SUBSTRING(SHA2(CONCAT('user:', id), 256), 1, 32)
WHERE user_key IS NULL;

ALTER TABLE users
    MODIFY COLUMN user_key CHAR(32) NOT NULL DEFAULT (REPLACE(UUID(), '-', '')),
    ADD UNIQUE KEY uq_users_user_key (user_key);

ALTER TABLE user_attributes
    ADD COLUMN user_key CHAR(32) NULL AFTER user_id,
    ADD KEY idx_ua_user_key (user_key),
    ADD KEY idx_ua_user_key_attr_type (user_key, attr_type);

UPDATE user_attributes ua
JOIN users u ON u.id = ua.user_id
SET ua.user_key = u.user_key
WHERE ua.user_key IS NULL;

ALTER TABLE user_priorities
    ADD COLUMN user_key CHAR(32) NULL AFTER user_id,
    ADD KEY idx_up_user_key_rank (user_key, priority_rank);

UPDATE user_priorities up2
JOIN users u ON u.id = up2.user_id
SET up2.user_key = u.user_key
WHERE up2.user_key IS NULL;

ALTER TABLE user_recommendations
    ADD COLUMN user_key CHAR(32) NULL AFTER user_id,
    ADD KEY idx_ur_user_key_score (user_key, final_score),
    ADD KEY idx_ur_user_key_bookmark (user_key, is_bookmarked);

UPDATE user_recommendations ur
JOIN users u ON u.id = ur.user_id
SET ur.user_key = u.user_key
WHERE ur.user_key IS NULL;

ALTER TABLE recommendation_logs
    ADD COLUMN user_key CHAR(32) NULL AFTER user_id,
    ADD KEY idx_rl_user_key_sent (user_key, sent_at);

UPDATE recommendation_logs rl
JOIN users u ON u.id = rl.user_id
SET rl.user_key = u.user_key
WHERE rl.user_key IS NULL;

ALTER TABLE service_view_logs
    ADD COLUMN user_key CHAR(32) NULL AFTER user_id,
    ADD KEY idx_svl_user_key_service_viewed (user_key, service_id, viewed_at);

UPDATE service_view_logs svl
JOIN users u ON u.id = svl.user_id
SET svl.user_key = u.user_key
WHERE svl.user_id IS NOT NULL
  AND svl.user_key IS NULL;

ALTER TABLE notifications
    ADD COLUMN user_key CHAR(32) NULL AFTER user_id,
    ADD KEY idx_noti_user_key_created (user_key, created_at);

UPDATE notifications n
JOIN users u ON u.id = n.user_id
SET n.user_key = u.user_key
WHERE n.user_key IS NULL;

ALTER TABLE chat_sessions
    ADD COLUMN user_key CHAR(32) NULL AFTER user_id,
    ADD KEY idx_cs_user_key_last_message (user_key, last_message_at),
    ADD KEY idx_cs_user_key_created (user_key, created_at);

UPDATE chat_sessions cs
JOIN users u ON u.id = cs.user_id
SET cs.user_key = u.user_key
WHERE cs.user_key IS NULL;
