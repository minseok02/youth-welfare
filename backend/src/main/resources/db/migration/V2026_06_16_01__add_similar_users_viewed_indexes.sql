CREATE INDEX IF NOT EXISTS idx_users_real_active_user_key
    ON users (user_key)
    WHERE is_active = true
      AND account_origin = 'REAL_USER';

CREATE INDEX IF NOT EXISTS idx_rpv_last_viewed_user_service
    ON recent_policy_views (last_viewed_at DESC, user_key, service_id);

CREATE INDEX IF NOT EXISTS idx_ua_type_value_user_key
    ON user_attributes (attr_type, attr_value, user_key);

CREATE INDEX IF NOT EXISTS idx_up_option_user_key
    ON user_priorities (priority_option_id, user_key);

CREATE INDEX IF NOT EXISTS idx_ur_user_key_recommended_service
    ON user_recommendations (user_key, recommended_at DESC, service_id);
