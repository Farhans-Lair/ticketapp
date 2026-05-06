package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Showtime entity.
 *
 * One Movie × one Screen × one start_time = one Showtime.
 * A Showtime tracks available_seats (decremented on booking) so concurrent
 * bookings can be guarded the same way as Event.availableTickets.
 *
 * language and format can differ per showtime (same movie can show in
 * Hindi + 2D in one screen, and English + IMAX in another).
 */
@Entity
@Table(name = "showtimes")
@Data
public class Showtime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "movie_id", nullable = false)
    @JsonProperty("movie_id")
    private Long movieId;

    @Column(name = "screen_id", nullable = false)
    @JsonProperty("screen_id")
    private Long screenId;

    @Column(name = "start_time", nullable = false)
    @JsonProperty("start_time")
    private LocalDateTime startTime;

    /**
     * Screening language — may differ from the movie's primary language
     * for dubbed/subtitle showings.
     */
    @Column(length = 50)
    private String language;

    /** 2D | 3D | IMAX | 4DX | SCREENX */
    @Column(length = 20)
    private String format = "2D";

    /** Base price in INR (before seat-category premium) */
    @Column(name = "base_price", nullable = false)
    @JsonProperty("base_price")
    private Double basePrice;

    @Column(name = "available_seats")
    @JsonProperty("available_seats")
    private Integer availableSeats;

    @Column(name = "total_seats")
    @JsonProperty("total_seats")
    private Integer totalSeats;

    /** active | cancelled | housefull */
    @Column(length = 20)
    private String status = "active";

    // ── Read-only joins (for API responses) ──────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", insertable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", insertable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Screen screen;
}
