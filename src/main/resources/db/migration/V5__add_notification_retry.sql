-- V5: bounded retries for notifications whose broker publish failed.
--
-- attempts        how many publish attempts have failed so far
-- next_attempt_at earliest time the dispatcher may try again (null = not due)
-- last_error      last failure message, truncated, for operators
--
-- A row moves PENDING -> FAILED once attempts reaches the configured maximum.
-- FAILED is terminal: the dispatcher never picks it up again, so one poison
-- notification cannot occupy the batch forever.

ALTER TABLE notification
    ADD COLUMN attempts INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN next_attempt_at DATETIME(6) NULL AFTER attempts,
    ADD COLUMN last_error VARCHAR(512) NULL AFTER next_attempt_at;

-- Rows created before this migration are due immediately.
UPDATE notification
SET next_attempt_at = created_at
WHERE status = 'PENDING';

CREATE INDEX idx_notification_due ON notification (status, next_attempt_at);
