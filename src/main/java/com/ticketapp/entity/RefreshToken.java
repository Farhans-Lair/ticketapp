package com.ticketapp.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A single issued refresh token, tracked server-side so it can be rotated
 * and revoked. We never store the raw JWT — only its SHA-256 hash
 * ({@link #tokenHash}) — so a database leak alone can't be replayed as a
 * working refresh token.
 *
 * Rotation: every time a refresh token is used at POST /auth/refresh, this
 * row is marked revoked and {@link #replacedById} points at the new row.
 * If a revoked token is ever presented again (a stolen/replayed token),
 * that's a signal the whole session may be compromised — see
 * RefreshTokenService#rotate for the reuse-detection handling.
 */
@Entity
@Table(name = "refresh_tokens")
@Data
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Shared across every refresh token issued in one login session (also embedded in the access + session token claims). */
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "replaced_by_id")
    private Long replacedById;

    @PrePersist
    void onCreate() {
        if (issuedAt == null) {
            issuedAt = LocalDateTime.now();
        }
    }
}
