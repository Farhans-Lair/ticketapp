package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Review entity.
 *
 * A review can be left for either a regular Event (event_id) or a Movie
 * (movie_id). Exactly one of the two must be non-null.
 *
 * The verified_booking flag is set to true when the system confirms the
 * reviewer actually booked the event — only verified reviews count toward
 * the average rating displayed on event/movie cards.
 */
@Entity
@Table(
    name = "reviews",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_review_user_event",
            columnNames = {"user_id", "event_id"}
        ),
        @UniqueConstraint(
            name = "uq_review_user_movie",
            columnNames = {"user_id", "movie_id"}
        )
    }
)
@Data
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    @JsonProperty("user_id")
    private Long userId;

    /** Set for event reviews; null for movie reviews. */
    @Column(name = "event_id")
    @JsonProperty("event_id")
    private Long eventId;

    /** Set for movie reviews; null for event reviews. */
    @Column(name = "movie_id")
    @JsonProperty("movie_id")
    private Long movieId;

    @Column(nullable = false)
    @Min(1) @Max(5)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String text;

    /**
     * true  → this user has a paid, active booking for this event/movie.
     * Only verified reviews are shown in the public average rating.
     */
    @Column(name = "verified_booking")
    @JsonProperty("verified_booking")
    private Boolean verifiedBooking = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
