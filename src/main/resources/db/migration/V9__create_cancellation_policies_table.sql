-- ============================================================
-- V9 — Create missing cancellation_policies table
--
-- ROOT CAUSE: CancellationPolicy.java is a fully-mapped @Entity
-- (@Table(name = "cancellation_policies")) but no prior migration
-- (V1-V8) ever created this table. Under ddl-auto=update (dev),
-- Hibernate silently creates missing tables, which is why this was
-- never caught locally. Under ddl-auto=validate (prod), Hibernate
-- will fail fast with a "table not found" schema-validation error
-- the moment this entity is loaded — the same crash-loop / ALB
-- unhealthy-target symptom as the earlier column-type mismatches,
-- but caused by a missing table instead of a wrong column type.
--
-- Column types below are matched exactly to the JPA entity fields:
--   id                      Long      -> BIGINT AUTO_INCREMENT
--   eventId                 Long      -> BIGINT NOT NULL UNIQUE
--   organizerId             Long      -> BIGINT NOT NULL
--   tiers                   String    -> TEXT NOT NULL
--   isCancellationAllowed   Boolean   -> TINYINT(1) NOT NULL DEFAULT 1
--   createdAt               LocalDateTime -> DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
--   updatedAt               LocalDateTime -> DATETIME NULL ON UPDATE CURRENT_TIMESTAMP
--
-- FK constraints mirror the existing pattern used by bookings/
-- organizer_payouts (ON DELETE CASCADE from events and users).
-- ============================================================

CREATE TABLE IF NOT EXISTS cancellation_policies (
    id                       BIGINT     NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id                 BIGINT     NOT NULL,
    organizer_id             BIGINT     NOT NULL,
    tiers                    TEXT       NOT NULL,
    is_cancellation_allowed  TINYINT(1) NOT NULL DEFAULT 1,
    created_at               DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME   NULL ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_cancellation_policies_event UNIQUE (event_id),

    CONSTRAINT fk_cancellation_policy_event
        FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT fk_cancellation_policy_organizer
        FOREIGN KEY (organizer_id) REFERENCES users(id) ON DELETE CASCADE,

    INDEX idx_cancellation_policies_organizer (organizer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
