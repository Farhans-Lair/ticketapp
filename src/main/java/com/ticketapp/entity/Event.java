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
 * Without these, Jackson sends "eventDate" but the frontend reads "event_date" → undefined.
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

    @Column(columnDefinition = "LONGTEXT")
    private String images;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
