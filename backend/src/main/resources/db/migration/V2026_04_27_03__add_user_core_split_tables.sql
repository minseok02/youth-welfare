SET SESSION sql_log_bin = 0;

CREATE DATABASE IF NOT EXISTS youth_welfare_pii
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS auth_users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_key          CHAR(32)     NOT NULL,
    email_lookup_hash CHAR(64)     NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    is_active         TINYINT(1)   NOT NULL DEFAULT 1,
    login_fail_count  INT          NOT NULL DEFAULT 0,
    locked_until      DATETIME,
    withdrawn_at      DATETIME,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_au_user_key (user_key),
    UNIQUE KEY uq_au_email_lookup_hash (email_lookup_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_profiles (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    user_key                CHAR(32)     NOT NULL,
    age                     INT,
    age_band                VARCHAR(20),
    age_calculated_at       DATETIME,
    sido                    VARCHAR(50),
    sgg                     VARCHAR(50),
    region_code             VARCHAR(20),
    income_level            TINYINT UNSIGNED,
    household_type          VARCHAR(30),
    employment_status       VARCHAR(30),
    notification_yn         TINYINT(1)   NOT NULL DEFAULT 0,
    notification_period     ENUM('DAILY','WEEKLY','NONE') DEFAULT 'NONE',
    notification_min_score  DOUBLE       DEFAULT 0.5,
    notification_consent_at DATETIME,
    display_count           INT          NOT NULL DEFAULT 10,
    profile_completeness    INT          DEFAULT 0,
    has_name                TINYINT(1)   NOT NULL DEFAULT 0,
    has_birth_date          TINYINT(1)   NOT NULL DEFAULT 0,
    has_phone               TINYINT(1)   NOT NULL DEFAULT 0,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_upf_user_key (user_key),
    KEY idx_upf_notification (notification_yn, notification_period),
    KEY idx_upf_region (sido, sgg),
    KEY idx_upf_income_employment (income_level, employment_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS youth_welfare_pii.user_pii (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_key       CHAR(32)     NOT NULL,
    email_enc      VARCHAR(512),
    name_enc       VARCHAR(512),
    birth_date_enc VARCHAR(128),
    phone_enc      VARCHAR(512),
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_upii_user_key (user_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO auth_users (
    user_key,
    email_lookup_hash,
    password_hash,
    is_active,
    login_fail_count,
    locked_until,
    withdrawn_at,
    created_at,
    updated_at
)
SELECT u.user_key,
       SHA2(LOWER(TRIM(u.email)), 256),
       u.password_hash,
       u.is_active,
       u.login_fail_count,
       u.locked_until,
       u.withdrawn_at,
       u.created_at,
       u.updated_at
FROM users u
ON DUPLICATE KEY UPDATE
    email_lookup_hash = VALUES(email_lookup_hash),
    password_hash = VALUES(password_hash),
    is_active = VALUES(is_active),
    login_fail_count = VALUES(login_fail_count),
    locked_until = VALUES(locked_until),
    withdrawn_at = VALUES(withdrawn_at),
    updated_at = VALUES(updated_at);

INSERT INTO user_profiles (
    user_key,
    age,
    age_band,
    age_calculated_at,
    sido,
    sgg,
    region_code,
    income_level,
    household_type,
    employment_status,
    notification_yn,
    notification_period,
    notification_min_score,
    notification_consent_at,
    display_count,
    profile_completeness,
    has_name,
    has_birth_date,
    has_phone,
    created_at,
    updated_at
)
SELECT u.user_key,
       CASE
           WHEN u.birth_date IS NULL THEN NULL
           ELSE TIMESTAMPDIFF(YEAR, u.birth_date, CURDATE())
       END,
       CASE
           WHEN u.birth_date IS NULL THEN NULL
           WHEN TIMESTAMPDIFF(YEAR, u.birth_date, CURDATE()) < 19 THEN 'UNDER_19'
           WHEN TIMESTAMPDIFF(YEAR, u.birth_date, CURDATE()) <= 24 THEN '19_24'
           WHEN TIMESTAMPDIFF(YEAR, u.birth_date, CURDATE()) <= 29 THEN '25_29'
           WHEN TIMESTAMPDIFF(YEAR, u.birth_date, CURDATE()) <= 34 THEN '30_34'
           WHEN TIMESTAMPDIFF(YEAR, u.birth_date, CURDATE()) <= 39 THEN '35_39'
           ELSE '40_PLUS'
       END,
       CASE
           WHEN u.birth_date IS NULL THEN NULL
           ELSE CURRENT_TIMESTAMP
       END,
       u.sido,
       u.sgg,
       u.region_code,
       u.income_level,
       u.household_type,
       u.employment_status,
       u.notification_yn,
       u.notification_period,
       u.notification_min_score,
       u.notification_consent_at,
       u.display_count,
       u.profile_completeness,
       CASE
           WHEN u.name IS NULL OR TRIM(u.name) = '' THEN 0
           ELSE 1
       END,
       CASE
           WHEN u.birth_date IS NULL THEN 0
           ELSE 1
       END,
       CASE
           WHEN u.phone_enc IS NULL OR TRIM(u.phone_enc) = '' THEN 0
           ELSE 1
       END,
       u.created_at,
       u.updated_at
FROM users u
ON DUPLICATE KEY UPDATE
    age = VALUES(age),
    age_band = VALUES(age_band),
    age_calculated_at = VALUES(age_calculated_at),
    sido = VALUES(sido),
    sgg = VALUES(sgg),
    region_code = VALUES(region_code),
    income_level = VALUES(income_level),
    household_type = VALUES(household_type),
    employment_status = VALUES(employment_status),
    notification_yn = VALUES(notification_yn),
    notification_period = VALUES(notification_period),
    notification_min_score = VALUES(notification_min_score),
    notification_consent_at = VALUES(notification_consent_at),
    display_count = VALUES(display_count),
    profile_completeness = VALUES(profile_completeness),
    has_name = VALUES(has_name),
    has_birth_date = VALUES(has_birth_date),
    has_phone = VALUES(has_phone),
    updated_at = VALUES(updated_at);

INSERT INTO youth_welfare_pii.user_pii (
    user_key,
    phone_enc,
    created_at,
    updated_at
)
SELECT u.user_key,
       u.phone_enc,
       u.created_at,
       u.updated_at
FROM users u
ON DUPLICATE KEY UPDATE
    phone_enc = VALUES(phone_enc),
    updated_at = VALUES(updated_at);
