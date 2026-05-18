package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(unique = true, nullable = false, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, columnDefinition = "TEXT")
    @JsonIgnore
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role = "user";   // "user" | "organizer" | "admin"

    // ── Feature 10: User profile fields ──────────────────────────────────────

    @Column(length = 20)
    private String phone;

    @Column(name = "avatar_url", length = 512)
    @JsonProperty("avatar_url")
    private String avatarUrl;

    @Column(name = "date_of_birth")
    @JsonProperty("date_of_birth")
    private LocalDate dateOfBirth;

    // ── Added: bio and bank_details (mirrors TBA2 user profile) ──────────────

    /** Short user biography / description. */
    @Column(columnDefinition = "TEXT")
    private String bio;

    /**
     * Bank / payment details stored as free-form text or JSON string.
     * e.g. "{"bank":"HDFC","account":"XXXX","ifsc":"HDFC0001234"}"
     * Kept as TEXT so the client can store any structure it needs.
     */
    @Column(name = "bank_details", columnDefinition = "TEXT")
    @JsonProperty("bank_details")
    private String bankDetails;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
