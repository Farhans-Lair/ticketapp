package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Waitlist entry for sold-out events.
 *
 * When a booking is cancelled, CancellationService restores
 * availableTickets. It then calls WaitlistService which picks the
 * oldest un-notified entry for that event and sends an email.
 *
 * tickets_wanted lets users specify how many seats they need so the
 * notification is only sent when enough seats open up at once.
 *
 * notified_at is stamped when the email is dispatched so that the
 * same user is not emailed twice for the same opening.
 */
@Entity
@Table(
    name = "waitlist",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_waitlist_user_event",
        columnNames = {"user_id", "event_id"}
    )
)
@Data
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    @JsonProperty("user_id")
    private Long userId;

    @Column(name = "event_id", nullable = false)
    @JsonProperty("event_id")
    private Long eventId;

    @Column(name = "tickets_wanted")
    @JsonProperty("tickets_wanted")
    private Integer ticketsWanted = 1;

    /** Timestamp set when the availability email is sent. */
    @Column(name = "notified_at")
    @JsonProperty("notified_at")
    private LocalDateTime notifiedAt;

    /** waiting | notified | converted (booked after notification) | expired */
    @Column(length = 20)
    private String status = "waiting";

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    @JsonProperty("joined_at")
    private LocalDateTime joinedAt;
}
