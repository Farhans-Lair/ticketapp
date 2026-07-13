-- ============================================================
-- V2 — ORGANIZER SUPPORT (from db/organizer_migration.sql)
-- Idempotent: ALTER TABLE uses stored procedure guards.
-- ============================================================

DROP PROCEDURE IF EXISTS tv_add_column;

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
DELIMITER ;

CALL tv_add_column('events', 'organizer_id',
    'organizer_id INT DEFAULT NULL AFTER id');

DROP PROCEDURE IF EXISTS tv_add_column;
