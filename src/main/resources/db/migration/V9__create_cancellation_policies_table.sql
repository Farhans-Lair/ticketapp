
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
