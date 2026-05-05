ALTER TABLE notifications
    MODIFY COLUMN status ENUM('pending','sent','failed') NOT NULL;

ALTER TABLE notifications
    ADD COLUMN dispatch_key VARCHAR(80) NULL AFTER user_key;

CREATE UNIQUE INDEX uq_noti_dispatch_key
    ON notifications (dispatch_key);
