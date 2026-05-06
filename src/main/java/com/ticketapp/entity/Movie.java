package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Movie entity for the cinema vertical.
 *
 * One Movie can be screened across many Cinemas / Screens / Showtimes.
 * The title, cast, director, runtime, genre, language, certification,
 * trailer URL, and poster URL are all stored here.
 */
@Entity
@Table(name = "movies")
@Data
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Comma-separated list of cast members, e.g. "Actor A, Actor B" */
    @Column(length = 500)
    private String cast;

    @Column(length = 150)
    private String director;

    /** Runtime in minutes */
    @Column(name = "runtime_minutes")
    @JsonProperty("runtime_minutes")
    private Integer runtimeMinutes;

    /** Drama, Action, Comedy, etc. */
    @Column(length = 100)
    private String genre;

    /** Primary language, e.g. "English" */
    @Column(length = 50)
    private String language;

    /** UA, A, U, PG-13, etc. */
    @Column(length = 20)
    private String certification;

    @Column(name = "trailer_url", length = 512)
    @JsonProperty("trailer_url")
    private String trailerUrl;

    /** S3 proxy URL or absolute URL for the poster image */
    @Column(name = "poster_url", length = 512)
    @JsonProperty("poster_url")
    private String posterUrl;

    /** active | coming_soon | archived */
    @Column(length = 20)
    private String status = "active";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
