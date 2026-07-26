package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/* Stores the refund tiers an organizer sets for each event. */
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

    /* Stored as JSON text. */
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
