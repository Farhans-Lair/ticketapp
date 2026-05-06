package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;

/**
 * Screen / Auditorium within a Cinema.
 *
 * A screen has a fixed physical layout of seats. The screen_type
 * determines which format (2D / 3D / IMAX / 4DX) can be shown here.
 * Total capacity is derived from the seats associated with this screen.
 */
@Entity
@Table(name = "screens")
@Data
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cinema_id", nullable = false)
    @JsonProperty("cinema_id")
    private Long cinemaId;

    /** Human-readable name, e.g. "Audi 1", "IMAX Hall" */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Projection / experience type.
     * Allowed values: 2D | 3D | IMAX | 4DX | SCREENX
     */
    @Column(name = "screen_type", nullable = false, length = 20)
    @JsonProperty("screen_type")
    private String screenType = "2D";

    /** Total seat count (physical rows × seats-per-row for this screen) */
    @Column(name = "total_capacity")
    @JsonProperty("total_capacity")
    private Integer totalCapacity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", insertable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Cinema cinema;
}
