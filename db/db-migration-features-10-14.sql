-- =============================================================================
-- TicketVerse — Feature migration SQL  (Features 10–14)
-- MySQL 5.7 / 8.x compatible — no IF NOT EXISTS syntax.
-- Uses stored procedures to skip columns/indexes that already exist.
-- Run ONCE: mysql -u root -p ticketdb < db/db-migration-features-10-14.sql
-- =============================================================================

DROP PROCEDURE IF EXISTS tv_add_column;
DROP PROCEDURE IF EXISTS tv_add_index;

-- Helper: adds a column only when it does not already exist.
DELIMITER $$
CREATE PROCEDURE tv_add_column(
    IN p_table  VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_ddl    TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = p_table
          AND COLUMN_NAME  = p_column
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

-- Helper: creates an index only when it does not already exist.
CREATE PROCEDURE tv_add_index(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_ddl   TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = p_table
          AND INDEX_NAME   = p_index
    ) THEN
        SET @sql = CONCAT('CREATE INDEX `', p_index, '` ON `', p_table, '` ', p_ddl);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- =============================================================================
-- Feature 10: User Profile — phone, avatar_url, date_of_birth, updated_at
-- =============================================================================
CALL tv_add_column('users', 'phone',         'phone          VARCHAR(20)  NULL AFTER email');
CALL tv_add_column('users', 'avatar_url',    'avatar_url     VARCHAR(512) NULL AFTER phone');
CALL tv_add_column('users', 'date_of_birth', 'date_of_birth  DATE         NULL AFTER avatar_url');
CALL tv_add_column('users', 'updated_at',    'updated_at     TIMESTAMP    NULL ON UPDATE CURRENT_TIMESTAMP AFTER date_of_birth');

-- =============================================================================
-- Feature 11: Featured / Trending Events
-- =============================================================================
CALL tv_add_column('events', 'is_featured',    'is_featured    TINYINT(1)  NOT NULL DEFAULT 0 AFTER review_count');
CALL tv_add_column('events', 'featured_until', 'featured_until DATETIME    NULL             AFTER is_featured');
CALL tv_add_index ('events', 'idx_events_featured', '(is_featured)');

-- =============================================================================
-- Feature 12: Event Reminder Emails
-- =============================================================================
CALL tv_add_column('bookings', 'reminder_sent_at', 'reminder_sent_at DATETIME NULL AFTER cancellation_invoice_s3_key');

-- =============================================================================
-- Feature 13: Event Moderation by Admin
-- Default 'published' preserves all existing events without a data migration.
-- =============================================================================
CALL tv_add_column('events', 'event_status',
    "event_status           VARCHAR(20) NOT NULL DEFAULT 'published' AFTER featured_until");
CALL tv_add_column('events', 'event_rejection_reason',
    'event_rejection_reason TEXT        NULL     AFTER event_status');
CALL tv_add_index ('events', 'idx_events_status', '(event_status)');

-- =============================================================================
-- Feature 14: Organizer Payout / Settlement
-- NOTE: organizer_profiles.user_id and users.id are both INT — FK is safe.
-- =============================================================================
CALL tv_add_column('organizer_profiles', 'bank_account_number',
    'bank_account_number VARCHAR(30)  NULL AFTER address');
CALL tv_add_column('organizer_profiles', 'bank_ifsc',
    'bank_ifsc           VARCHAR(15)  NULL AFTER bank_account_number');
CALL tv_add_column('organizer_profiles', 'upi_id',
    'upi_id              VARCHAR(100) NULL AFTER bank_ifsc');
CALL tv_add_column('organizer_profiles', 'payout_method',
    'payout_method       VARCHAR(10)  NULL AFTER upi_id');

-- organizer_payouts table
-- CREATE TABLE IF NOT EXISTS is valid in all MySQL versions.
-- organizer_id is INT to match users.id INT — fixes the FK type mismatch.
CREATE TABLE IF NOT EXISTS organizer_payouts (
    id                  INT            NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organizer_id        INT            NOT NULL,
    amount              DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    from_date           DATE           NOT NULL,
    to_date             DATE           NOT NULL,
    status              VARCHAR(20)    NOT NULL DEFAULT 'requested',
    booking_count       INT            NOT NULL DEFAULT 0,
    platform_fee        DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    razorpay_payout_id  VARCHAR(255)   NULL,
    admin_note          TEXT           NULL,
    requested_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settled_at          DATETIME       NULL,
    CONSTRAINT fk_payout_organizer
        FOREIGN KEY (organizer_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_payouts_organizer (organizer_id),
    INDEX idx_payouts_status    (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Cleanup helpers
DROP PROCEDURE IF EXISTS tv_add_column;
DROP PROCEDURE IF EXISTS tv_add_index;

-- =============================================================================
-- Optional verify (uncomment to check all columns were added):
-- SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT
--   FROM INFORMATION_SCHEMA.COLUMNS
--   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('users','events','bookings','organizer_profiles','organizer_payouts')
--   ORDER BY TABLE_NAME, ORDINAL_POSITION;
-- SHOW TABLES LIKE 'organizer_payouts';
-- =============================================================================
