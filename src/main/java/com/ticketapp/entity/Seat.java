package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seats")
@Data
@NoArgsConstructor // ✅ Required for JPA
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

    // ✅ FIX: custom constructor matching SeatService usage
    public Seat(Long eventId, String seatNumber) {
        this.eventId = eventId;
        this.seatNumber = seatNumber;
        this.status = "available";
    }
}