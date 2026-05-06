package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;

/**
 * Cinema / Venue entity.
 *
 * A cinema has many Screens. Each Screen hosts many Showtimes.
 * The city field also drives the Feature-2 city-selector.
 */
@Entity
@Table(name = "cinemas")
@Data
public class Cinema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(nullable = false, length = 100)
    private String city;

    /** Decimal latitude, e.g. 19.0760 */
    @Column(precision = 10)
    private Double latitude;

    /** Decimal longitude, e.g. 72.8777 */
    @Column(precision = 10)
    private Double longitude;

    /**
     * Comma-separated amenities, e.g. "Food Court, Parking, IMAX"
     * Kept as plain text for simplicity; use a JSON array if you need filtering.
     */
    @Column(columnDefinition = "TEXT")
    private String amenities;

    /** active | inactive */
    @Column(length = 20)
    private String status = "active";
}
