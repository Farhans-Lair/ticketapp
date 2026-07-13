-- ============================================================
-- V4 — FEATURES 10–14 + BOOKING INVOICE, BIO, CATEGORIES, SMS
-- (from db/db-migration-features-10-14.sql + db/db-migration-new-features.sql)
-- ============================================================

DROP PROCEDURE IF EXISTS tv_add_column;
DROP PROCEDURE IF EXISTS tv_add_index;

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

-- Feature 10: User profile extensions
CALL tv_add_column('users', 'phone',         'phone          VARCHAR(20)  NULL AFTER email');
CALL tv_add_column('users', 'avatar_url',    'avatar_url     VARCHAR(512) NULL AFTER phone');
CALL tv_add_column('users', 'date_of_birth', 'date_of_birth  DATE         NULL AFTER avatar_url');
CALL tv_add_column('users', 'updated_at',    'updated_at     TIMESTAMP    NULL ON UPDATE CURRENT_TIMESTAMP AFTER date_of_birth');

-- Feature 11: Featured / Trending events
CALL tv_add_column('events', 'is_featured',    'is_featured    TINYINT(1) NOT NULL DEFAULT 0 AFTER review_count');
CALL tv_add_column('events', 'featured_until', 'featured_until DATETIME   NULL            AFTER is_featured');
CALL tv_add_index ('events', 'idx_events_featured', '(is_featured)');

-- Feature 12: Reminder emails
CALL tv_add_column('bookings', 'reminder_sent_at', 'reminder_sent_at DATETIME NULL AFTER checked_in_at');

-- Feature 13: Event moderation
CALL tv_add_column('events', 'event_status',
    "event_status VARCHAR(20) NOT NULL DEFAULT 'published' AFTER featured_until");
CALL tv_add_column('events', 'event_rejection_reason',
    'event_rejection_reason TEXT NULL AFTER event_status');
CALL tv_add_index ('events', 'idx_events_status', '(event_status)');

-- Feature 14: Organizer payout
CALL tv_add_column('organizer_profiles', 'bank_account_number',
    'bank_account_number VARCHAR(30)  NULL AFTER address');
CALL tv_add_column('organizer_profiles', 'bank_ifsc',
    'bank_ifsc           VARCHAR(15)  NULL AFTER bank_account_number');
CALL tv_add_column('organizer_profiles', 'upi_id',
    'upi_id              VARCHAR(100) NULL AFTER bank_ifsc');
CALL tv_add_column('organizer_profiles', 'payout_method',
    'payout_method       VARCHAR(10)  NULL AFTER upi_id');

CREATE TABLE IF NOT EXISTS organizer_payouts (
    id                  INT           NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organizer_id        INT           NOT NULL,
    amount              DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    from_date           DATE          NOT NULL,
    to_date             DATE          NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'requested',
    booking_count       INT           NOT NULL DEFAULT 0,
    platform_fee        DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    razorpay_payout_id  VARCHAR(255)  NULL,
    admin_note          TEXT          NULL,
    requested_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settled_at          DATETIME      NULL,
    CONSTRAINT fk_payout_organizer
        FOREIGN KEY (organizer_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_payouts_organizer (organizer_id),
    INDEX idx_payouts_status    (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Booking invoice PDF key
CALL tv_add_column('bookings', 'booking_invoice_s3_key',
    'booking_invoice_s3_key VARCHAR(512) NULL AFTER ticket_pdf_s3_key');

-- Cancellation columns
CALL tv_add_column('bookings', 'cancellation_status',
    "cancellation_status VARCHAR(20) NOT NULL DEFAULT 'active' AFTER booking_invoice_s3_key");
CALL tv_add_column('bookings', 'refund_amount',
    'refund_amount DECIMAL(12,2) NULL AFTER cancellation_status');
CALL tv_add_column('bookings', 'razorpay_refund_id',
    'razorpay_refund_id VARCHAR(255) NULL AFTER refund_amount');
CALL tv_add_column('bookings', 'cancelled_at',
    'cancelled_at DATETIME NULL AFTER razorpay_refund_id');
CALL tv_add_column('bookings', 'cancellation_fee',
    'cancellation_fee DECIMAL(12,2) NULL AFTER cancelled_at');
CALL tv_add_column('bookings', 'cancellation_fee_gst',
    'cancellation_fee_gst DECIMAL(12,2) NULL AFTER cancellation_fee');
CALL tv_add_column('bookings', 'applied_tier_hours',
    'applied_tier_hours INT NULL AFTER cancellation_fee_gst');
CALL tv_add_column('bookings', 'cancellation_invoice_s3_key',
    'cancellation_invoice_s3_key VARCHAR(512) NULL AFTER applied_tier_hours');
CALL tv_add_column('bookings', 'price_per_ticket',
    'price_per_ticket DECIMAL(10,2) NULL AFTER gst_amount');

-- User bio + bank details
CALL tv_add_column('users', 'bio',          'bio          TEXT NULL AFTER date_of_birth');
CALL tv_add_column('users', 'bank_details', 'bank_details TEXT NULL AFTER bio');

-- Dynamic categories
CREATE TABLE IF NOT EXISTS event_categories (
    id          INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(100) NOT NULL,
    icon_emoji  VARCHAR(10)  NOT NULL DEFAULT '🎟️',
    image_url   TEXT         NULL,
    is_active   TINYINT(1)   NOT NULL DEFAULT 1,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_category_slug UNIQUE (slug),
    INDEX idx_categories_active (is_active),
    INDEX idx_categories_sort   (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO event_categories (name, slug, icon_emoji, sort_order) VALUES
    ('Music',      'Music',      '🎵', 1),
    ('Sports',     'Sports',     '⚽', 2),
    ('Comedy',     'Comedy',     '😂', 3),
    ('Theatre',    'Theatre',    '🎭', 4),
    ('Conference', 'Conference', '💼', 5),
    ('Festival',   'Festival',   '🎉', 6),
    ('Workshop',   'Workshop',   '🔧', 7),
    ('Other',      'Other',      '🎟️', 8);

-- Fix emoji charset
ALTER TABLE event_categories
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS tv_add_column;
DROP PROCEDURE IF EXISTS tv_add_index;
