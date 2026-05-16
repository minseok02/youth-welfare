ALTER TABLE user_recommendations
    ADD COLUMN IF NOT EXISTS ai_status VARCHAR(30);

UPDATE user_recommendations
SET ai_status = CASE
    WHEN ai_score IS NULL THEN 'NOT_REQUESTED'
    ELSE 'SCORED'
END
WHERE ai_status IS NULL;

ALTER TABLE user_recommendations
    ALTER COLUMN ai_status SET DEFAULT 'NOT_REQUESTED';

ALTER TABLE user_recommendations
    ALTER COLUMN ai_status SET NOT NULL;
