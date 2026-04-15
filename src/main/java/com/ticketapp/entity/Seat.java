package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;

/**
 * Seat entity.
 *
 * @JsonProperty annotations serialize camelCase Java fields as snake_case JSON,
 * matching what seat-selection.js expects (seat.seat_number, seat.event_id).
 * Without this, seat.seat_number[0] throws "Cannot read properties of undefined".
 */
@Entity
@Table(name = "seats")
@Data
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
}
