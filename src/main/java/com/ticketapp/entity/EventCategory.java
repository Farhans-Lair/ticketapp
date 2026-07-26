package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_categories")
@Data
@NoArgsConstructor
public class EventCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* Display name, e.g. */
    @Column(nullable = false, length = 100)
    private String name;

    /* URL-safe identifier auto-generated from name (spaces → underscores). */
    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    /* Optional emoji icon for the category pill, e.g. */
    @Column(name = "icon_emoji", length = 10)
    @JsonProperty("icon_emoji")
    private String iconEmoji = "🎟️";

    /* Optional image URL for a category card on the homepage. */
    @Column(name = "image_url", columnDefinition = "TEXT")
    @JsonProperty("image_url")
    private String imageUrl;

    /* Controls visibility on the public categories endpoint. */
    @Column(name = "is_active")
    @JsonProperty("is_active")
    private Boolean isActive = true;

    /* Lower = appears earlier in sorted listings. */
    @Column(name = "sort_order")
    @JsonProperty("sort_order")
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
