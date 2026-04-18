package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Stores the refund tiers an organizer sets for each event.
 *
 * tiers — JSON column, e.g.:
 *   [
 *     {"hours_before": 72, "refund_percent": 100},
 *     {"hours_before": 24, "refund_percent": 50},
 *     {"hours_before": 0,  "refund_percent": 0}
 *   ]
 *
 * Logic: sort tiers DESC by hours_before; take the first tier where
 *        hoursUntilEvent >= hours_before. That tier's refund_percent applies.
 */
@Entity
@Table(name = "cancellation_policies")
@Data
public class CancellationPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    @JsonProperty("event_id")
    private Long eventId;

    @Column(name = "organizer_id", nullable = false)
    @JsonProperty("organizer_id")
    private Long organizerId;

    /**
     * Stored as JSON text. Spring Boot's Jackson integration handles
     * serialization/deserialization automatically via the String column.
     * We keep it as a raw String and parse it in the service layer so we
     * don't need a separate JSON-column Hibernate type.
     */
    @Column(name = "tiers", columnDefinition = "TEXT", nullable = false)
    private String tiers;

    @Column(name = "is_cancellation_allowed", nullable = false)
    @JsonProperty("is_cancellation_allowed")
    private Boolean isCancellationAllowed = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
