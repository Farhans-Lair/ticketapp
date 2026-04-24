-- ============================================================
-- MIGRATION: Add Organizer / Venue Partner Support
-- Run this against your existing database.
-- The full schema.sql has also been updated to reflect these
-- changes for fresh installs.
--
-- This file is idempotent — safe to run even if schema.sql
-- was already applied (uses IF NOT EXISTS guards throughout).
-- ============================================================

-- 1. ── organizer_profiles ─────────────────────────────────
--    One row per organizer user.  Stores business details
--    and admin approval status.
--    IF NOT EXISTS: no-op if schema.sql already created it.
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS organizer_profiles (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  user_id          INT NOT NULL UNIQUE,
  business_name    VARCHAR(200) NOT NULL,
  contact_phone    VARCHAR(20)  DEFAULT NULL,
  gst_number       VARCHAR(20)  DEFAULT NULL,
  address          TEXT         DEFAULT NULL,
  status           ENUM('pending','approved','rejected') NOT NULL DEFAULT 'pending',
  rejection_reason TEXT         DEFAULT NULL,
  created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  CONSTRAINT fk_organizer_profile_user
    FOREIGN KEY (user_id)
    REFERENCES users(id)
    ON DELETE CASCADE
);

-- 2. ── Add organizer_id to events ────────────────────────
--    NULL  → event created by super-admin (platform event)
--    INT   → event created by an organizer
--    Guarded with a stored procedure so it is a no-op if the
--    column already exists (schema.sql adds it on fresh installs).
-- -------------------------------------------------------------
DROP PROCEDURE IF EXISTS add_organizer_id_if_missing;

DELIMITER $$
CREATE PROCEDURE add_organizer_id_if_missing()
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM   information_schema.COLUMNS
    WHERE  TABLE_SCHEMA = DATABASE()
      AND  TABLE_NAME   = 'events'
      AND  COLUMN_NAME  = 'organizer_id'
  ) THEN
    ALTER TABLE events
      ADD COLUMN organizer_id INT DEFAULT NULL AFTER id,
      ADD CONSTRAINT fk_event_organizer
        FOREIGN KEY (organizer_id)
        REFERENCES users(id)
        ON DELETE SET NULL;
  END IF;
END$$
DELIMITER ;

CALL add_organizer_id_if_missing();
DROP PROCEDURE IF EXISTS add_organizer_id_if_missing;
