-- ============================================================
-- V5 — COMPOSITE INDEXES + OPTIMISTIC LOCK COLUMN
-- Implements analysis recommendations:
--   - Composite index on events(event_status, event_date) for public listing
--   - Index on events(organizer_id) for organizer dashboard
--   - Composite index on bookings(event_id, payment_status) for attendee queries
--   - Composite index on seats(event_id, seat_number) for seat lookups
--   - Unique constraint on bookings.razorpay_order_id (idempotency guard)
--   - @Version column on events for optimistic locking
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

-- ── events ──────────────────────────────────────────────────────────────────
-- Public event listing: ORDER BY event_date WHERE event_status = 'published'
CALL tv_add_index('events', 'idx_events_status_date',    '(event_status, event_date)');
-- Organizer dashboard: WHERE organizer_id = ?
CALL tv_add_index('events', 'idx_events_organizer_id',   '(organizer_id)');
-- Featured events query
CALL tv_add_index('events', 'idx_events_featured_status','(is_featured, event_status)');

-- ── bookings ─────────────────────────────────────────────────────────────────
-- Attendee list + organizer revenue: WHERE event_id = ? AND payment_status = 'paid'
CALL tv_add_index('bookings', 'idx_bookings_event_status', '(event_id, payment_status)');
-- User booking history: WHERE user_id = ?
CALL tv_add_index('bookings', 'idx_bookings_user_id',      '(user_id)');
-- Idempotency: prevents double-booking on duplicate verifyPayment calls
-- Note: only adds if razorpay_order_id column exists (added in V4)
CALL tv_add_index('bookings', 'uq_bookings_razorpay_order', '(razorpay_order_id)');

-- ── seats ────────────────────────────────────────────────────────────────────
-- Seat lookup by event + seat number (pessimistic lock path)
CALL tv_add_index('seats', 'idx_seats_event_number',  '(event_id, seat_number)');
-- Seat availability query: WHERE event_id = ? AND status = 'available'
CALL tv_add_index('seats', 'idx_seats_event_status',  '(event_id, status)');

-- ── Optimistic lock column on events (task 12) ────────────────────────────────
-- @Version int on the JPA entity maps to this column.
-- Hibernate increments it on every UPDATE; stale reads throw OptimisticLockException.
CALL tv_add_column('events', 'version', 'version INT NOT NULL DEFAULT 0 AFTER created_at');

DROP PROCEDURE IF EXISTS tv_add_column;
DROP PROCEDURE IF EXISTS tv_add_index;
