-- V3: finish the M2 schema that V2 started.
--
-- V2 is already applied in the local database and therefore stays untouched:
-- an applied migration must never change, otherwise Flyway's checksum check
-- fails and the recorded version will not run again. Everything still missing
-- goes here.
--
-- All three tables are empty at this point, so NOT NULL columns and foreign
-- keys can be added directly. If they ever hold rows, add the column as
-- nullable, backfill it, then tighten it in a separate migration.

-- 1) activity: the owner column becomes a real relation and gets an index for
--    "activities created by me".
ALTER TABLE activity
    ADD CONSTRAINT fk_activity_organizer
        FOREIGN KEY (organizer_id) REFERENCES app_user (id),
    ADD KEY idx_activity_organizer (organizer_id);

-- 2) registration: the confirmation step needs a deadline, a confirmation
--    timestamp, and a marker that exists only while the seat is held.
ALTER TABLE registration
    ADD COLUMN confirm_deadline DATETIME(6) NOT NULL AFTER status,
    ADD COLUMN confirmed_at DATETIME(6) NULL AFTER confirm_deadline,
    ADD COLUMN active_marker TINYINT
        GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING', 'CONFIRMED') THEN 1 ELSE NULL END) STORED
        AFTER confirmed_at;

-- 3) Replace the old unique key. (activity_id, user_id) alone would forbid a
--    user from registering again after an expiry or a cancellation.
--    MySQL allows multiple NULLs in a unique index, so a dead row stops
--    participating in the constraint as soon as its status leaves
--    PENDING/CONFIRMED.
ALTER TABLE registration
    DROP INDEX uk_registration_activity_user,
    ADD UNIQUE KEY uk_registration_active (activity_id, user_id, active_marker);

-- 4) Index for the timeout scanner: status plus deadline is exactly how it
--    looks up expired pending registrations.
ALTER TABLE registration
    ADD KEY idx_registration_status_deadline (status, confirm_deadline);

-- 5) Seed the first administrator, otherwise nobody can assign the first
--    organizer (BR-12). Development only: this password is public knowledge
--    once the file is committed. See product document risk R-06.
--    Login: admin / Admin@12345
INSERT INTO app_user (username, password_hash, display_name, role, created_at, updated_at)
SELECT 'admin',
       '$2a$10$WVHrt.X6CNMpFifqUBUxoOA/SfwtpuW0B8/nO4ELrKl4zZYJFpoeC',
       'System Administrator',
       'ADMIN',
       NOW(6),
       NOW(6)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'admin');
