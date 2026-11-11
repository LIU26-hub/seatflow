-- V4: single table that is both the outbox and the in-app notification store.
--
-- PENDING   -> written inside the registration transaction, waiting to be published.
-- SENT      -> dispatcher published it to the broker, waiting to be consumed.
-- DELIVERED -> consumer received it, the in-app message is now visible to the user.
--
-- 中文批注：一张表同时做发件箱和站内信。id 既是 outbox 主键、AMQP messageId，
-- 也是站内信主键，消费端据此幂等，不再需要第二张表。

CREATE TABLE notification
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    registration_id BIGINT       NOT NULL,
    activity_id     BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    event           VARCHAR(32)  NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    sent_at         DATETIME(6)  NULL,
    delivered_at    DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notification_status (status),
    KEY idx_notification_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
