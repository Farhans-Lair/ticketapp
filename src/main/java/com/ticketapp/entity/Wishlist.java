package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Wishlist entry — a user saves an event for later.
 *
 * Also doubles as the "Notify me when tickets open" subscription when
 * the event is sold-out at the time of saving. The notify_on_availability
 * flag is set to true in that case, and WishlistService emails the user
 * when availableTickets > 0 again (after a cancellation).
 */
@Entity
@Table(
    name = "wishlists",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_wishlist_user_event",
        columnNames = {"user_id", "event_id"}
    )
)
@Data
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    @JsonProperty("user_id")
    private Long userId;

    @Column(name = "event_id", nullable = false)
    @JsonProperty("event_id")
    private Long eventId;

    /**
     * When true, an email is sent to the user the next time
     * a cancellation frees a ticket for this event.
     */
    @Column(name = "notify_on_availability")
    @JsonProperty("notify_on_availability")
    private Boolean notifyOnAvailability = false;

    @CreationTimestamp
    @Column(name = "saved_at", updatable = false)
    @JsonProperty("saved_at")
    private LocalDateTime savedAt;
}
