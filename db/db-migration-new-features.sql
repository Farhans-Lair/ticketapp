-- =============================================================================
-- TicketVerse — Feature migration SQL  (Features: Booking Invoice, User Bio,
--               Bank Details, Dynamic Categories, Payout Settlement, SMS)
-- MySQL 5.7 / 8.x compatible — no IF NOT EXISTS syntax.
-- Uses stored procedures to skip columns/indexes that already exist.
-- Run ONCE: mysql -u root -p ticketdb < db/db-migration-new-features.sql
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
-- Feature: Booking Invoice PDF
-- Stores the S3 key of the per-booking A4 invoice PDF.
-- Mirrors TBA2's bookings.booking_invoice_s3_key column.
-- =============================================================================
CALL tv_add_column('bookings', 'booking_invoice_s3_key',
    'booking_invoice_s3_key VARCHAR(512) NULL AFTER ticket_pdf_s3_key');

-- =============================================================================
-- Feature: User bio + bank_details
-- Both mirror TBA2's users.bio and users.bank_details columns.
-- =============================================================================
CALL tv_add_column('users', 'bio',
    'bio          TEXT NULL AFTER date_of_birth');
CALL tv_add_column('users', 'bank_details',
    'bank_details TEXT NULL AFTER bio');

-- =============================================================================
-- Feature: Dynamic Category Management
-- Replaces hard-coded event category ENUM with admin-managed rows.
-- Mirrors TBA2's event_categories table exactly.
-- =============================================================================
CREATE TABLE IF NOT EXISTS event_categories (
    id          INT            NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)   NOT NULL,
    slug        VARCHAR(100)   NOT NULL,
    icon_emoji  VARCHAR(10)    NOT NULL DEFAULT '🎟️',
    image_url   TEXT           NULL,
    is_active   TINYINT(1)     NOT NULL DEFAULT 1,
    sort_order  INT            NOT NULL DEFAULT 0,
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME       NULL     ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_category_slug UNIQUE (slug),
    INDEX idx_categories_active (is_active),
    INDEX idx_categories_sort   (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed default categories (mirrors TBA2 category names exactly).
-- INSERT IGNORE skips rows whose slug already exists — safe to re-run.
INSERT IGNORE INTO event_categories (name, slug, icon_emoji, sort_order) VALUES
    ('Music',      'Music',      '🎵', 1),
    ('Sports',     'Sports',     '⚽', 2),
    ('Comedy',     'Comedy',     '😂', 3),
    ('Theatre',    'Theatre',    '🎭', 4),
    ('Conference', 'Conference', '💼', 5),
    ('Festival',   'Festival',   '🎉', 6),
    ('Workshop',   'Workshop',   '🔧', 7),
    ('Other',      'Other',      '🎟️', 8);

-- Cleanup helpers
DROP PROCEDURE IF EXISTS tv_add_column;
DROP PROCEDURE IF EXISTS tv_add_index;

-- =============================================================================
-- Verify (uncomment to check after running):
-- SELECT COLUMN_NAME, COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS
--   WHERE TABLE_SCHEMA = DATABASE()
--     AND TABLE_NAME   = 'bookings'
--     AND COLUMN_NAME  = 'booking_invoice_s3_key';
-- SELECT COLUMN_NAME, COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS
--   WHERE TABLE_SCHEMA = DATABASE()
--     AND TABLE_NAME   = 'users'
--     AND COLUMN_NAME IN ('bio', 'bank_details');
-- SELECT * FROM event_categories ORDER BY sort_order;
-- =============================================================================
