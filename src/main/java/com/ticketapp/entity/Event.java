package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Event entity.
 *
 * @JsonProperty annotations ensure Jackson serializes camelCase Java field names
 * as snake_case JSON keys, which is what all frontend pages expect.
 */
@Entity
@Table(name = "events")
@Data
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organizer_id")
    @JsonProperty("organizer_id")
    private Long organizerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 150)
    private String location;

    @Column(name = "event_date", nullable = false)
    @JsonProperty("event_date")
    private LocalDateTime eventDate;

    @Column(nullable = false)
    private Double price;

    @Column(name = "total_tickets", nullable = false)
    @JsonProperty("total_tickets")
    private Integer totalTickets;

    @Column(name = "available_tickets", nullable = false)
    @JsonProperty("available_tickets")
    private Integer availableTickets;

    @Column(length = 20)
    private String category = "Other";

    @Column(columnDefinition = "TEXT")
    private String images;

    // ── Feature 2: City selector ──────────────────────────────────────────────
    @Column(length = 100)
    private String city;

    // ── Feature 5: Reviews & ratings ─────────────────────────────────────────
    @Column(name = "average_rating")
    @JsonProperty("average_rating")
    private Double averageRating;

    @Column(name = "review_count")
    @JsonProperty("review_count")
    private Integer reviewCount = 0;

    // ── Feature 11: Featured / Trending ──────────────────────────────────────
    /** Admin can mark an event as featured to appear in the hero strip. */
    @Column(name = "is_featured")
    @JsonProperty("is_featured")
    private Boolean isFeatured = false;

    /**
     * Optional expiry for featured status. Null = permanently featured.
     * Checked by EventRepository so expired events drop out automatically.
     */
    @Column(name = "featured_until")
    @JsonProperty("featured_until")
    private LocalDateTime featuredUntil;

    // ── Feature 13: Event Moderation ─────────────────────────────────────────
    /**
     * draft          → organizer created, not yet submitted
     * pending_review → organizer submitted, awaiting admin approval
     * published      → live and visible to the public
     * rejected       → admin rejected with a reason
     *
     * Default: 'published' preserves all existing events without migration.
     * Admin-created events always default to 'published'.
     * Organizer-created events default to 'draft'.
     */
    @Column(name = "event_status", length = 20)
    @JsonProperty("event_status")
    private String eventStatus = "published";

    @Column(name = "event_rejection_reason", columnDefinition = "TEXT")
    @JsonProperty("event_rejection_reason")
    private String eventRejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
