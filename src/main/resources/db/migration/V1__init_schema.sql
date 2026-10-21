-- SeatFlow initial schema.
-- Every change after this file must be a new versioned script (V2__, V3__, ...).
-- Never edit a migration that has already been applied.

CREATE TABLE app_user
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(64)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE activity
(
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    title              VARCHAR(128)  NOT NULL,
    description        VARCHAR(1024) DEFAULT NULL,
    location           VARCHAR(128)  NOT NULL,
    capacity           INT           NOT NULL,
    registered_count   INT           NOT NULL DEFAULT 0,
    registration_start DATETIME(6)   NOT NULL,
    registration_end   DATETIME(6)   NOT NULL,
    status             VARCHAR(32)   NOT NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_activity_status_start (status, registration_start)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- The unique key on (activity_id, user_id) is the last line of defence against
-- duplicate registrations. Application level locking alone is not enough.
CREATE TABLE registration
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    activity_id BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    status      VARCHAR(32) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_registration_activity_user (activity_id, user_id),
    KEY idx_registration_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;