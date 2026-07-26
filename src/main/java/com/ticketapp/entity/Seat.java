package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seats")
@Data
@NoArgsConstructor   // ✅ Required for JPA
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    @JsonProperty("event_id")
    private Long eventId;

    @Column(name = "seat_number", nullable = false, length = 10)
    @JsonProperty("seat_number")
    private String seatNumber;

    @Column(length = 10)
    private String status = "available";

    /* Silver | Gold | Platinum | Recliner | Wheelchair */
    @Column(length = 20)
    private String category = "Silver";

    /* Per-seat price. */
    @Column(name = "price")
    private Double price;

    /* Timestamp until which this seat is held for a specific user. */
    @Column(name = "held_until")
    @JsonProperty("held_until")
    private java.time.LocalDateTime heldUntil;

    /* User who holds this seat during checkout. */
    @Column(name = "held_by_user_id")
    @JsonProperty("held_by_user_id")
    private Long heldByUserId;

    /* For movie seats: which showtime this seat belongs to. */
    @Column(name = "showtime_id")
    @JsonProperty("showtime_id")
    private Long showtimeId;

    // Constructors

    /* Backward-compatible constructor used by existing SeatService. */
    public Seat(Long eventId, String seatNumber) {
        this.eventId = eventId;
        this.seatNumber = seatNumber;
        this.status = "available";
    }

    /* Extended constructor with category and price for tiered seating. */
    public Seat(Long eventId, String seatNumber, String category, Double price) {
        this.eventId = eventId;
        this.seatNumber = seatNumber;
        this.status = "available";
        this.category = category;
        this.price = price;
    }
}