package com.ticketapp.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and validates three DISTINCT JWT types, each signed with its own
 * secret:
 *
 *   ACCESS  — short-lived (default 15 min). Sent on every API request.
 *             Claims: id, role, sid (session id).
 *   REFRESH — longer-lived (default 7 days). Used ONLY at POST /auth/refresh
 *             to mint a new access+refresh pair. Single-use — rotated on
 *             every use (see RefreshTokenService). Claims: id, sid, jti.
 *   SESSION — longest-lived (default 30 days), non-rotating. A stable
 *             identifier for the login session itself (device/browser),
 *             independent of the access token's short lifetime. Used to
 *             group and revoke every refresh token issued in one session
 *             at once. Claims: id, sid.
 *
 * Using a separate secret per type means a leaked/cracked secret for one
 * token type can never be used to forge a token of a different type —
 * e.g. compromising the long-lived session secret alone still can't mint
 * a valid access token, and vice versa.
 */
@Component
public class JwtUtil {

    @Value("${jwt.access-secret}")
    private String accessSecret;

    @Value("${jwt.access-expiration-ms:900000}")           // 15 minutes
    private long accessExpirationMs;

    @Value("${jwt.refresh-secret}")
    private String refreshSecret;

    @Value("${jwt.refresh-expiration-ms:604800000}")       // 7 days
    private long refreshExpirationMs;

    @Value("${jwt.session-secret}")
    private String sessionSecret;

    @Value("${jwt.session-expiration-ms:2592000000}")      // 30 days
    private long sessionExpirationMs;

    // ── Key derivation ──────────────────────────────────────────────────

    private SecretKey keyFrom(String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // Pad to 32 bytes minimum for HMAC-SHA256
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            return Keys.hmacShaKeyFor(padded);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private SecretKey accessKey()  { return keyFrom(accessSecret); }
    private SecretKey refreshKey() { return keyFrom(refreshSecret); }
    private SecretKey sessionKey() { return keyFrom(sessionSecret); }

    // ── Session id ───────────────────────────────────────────────────────

    /** Call once per login (login-verify) — the same value is embedded in the access, refresh, and session tokens issued together. */
    public String newSessionId() {
        return UUID.randomUUID().toString();
    }

    // ── Access token ─────────────────────────────────────────────────────

    public String generateAccessToken(Long userId, String role, String sessionId) {
        return Jwts.builder()
                .claim("id", userId)
                .claim("role", role)
                .claim("sid", sessionId)
                .claim("typ", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpirationMs))
                .signWith(accessKey())
                .compact();
    }

    public Claims parseAccessToken(String token) {
        return Jwts.parser().verifyWith(accessKey()).build()
                .parseSignedClaims(token).getPayload();
    }

    public boolean isValidAccessToken(String token) {
        try {
            parseAccessToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ── Refresh token ────────────────────────────────────────────────────

    /** jti makes each refresh token unique even if issued in the same millisecond for the same session — useful for audit/debugging alongside the DB row's own id. */
    public String generateRefreshToken(Long userId, String sessionId) {
        return Jwts.builder()
                .claim("id", userId)
                .claim("sid", sessionId)
                .claim("typ", "refresh")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpirationMs))
                .signWith(refreshKey())
                .compact();
    }

    public Claims parseRefreshToken(String token) {
        return Jwts.parser().verifyWith(refreshKey()).build()
                .parseSignedClaims(token).getPayload();
    }

    public boolean isValidRefreshToken(String token) {
        try {
            parseRefreshToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    // ── Session token ────────────────────────────────────────────────────

    public String generateSessionToken(Long userId, String sessionId) {
        return Jwts.builder()
                .claim("id", userId)
                .claim("sid", sessionId)
                .claim("typ", "session")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + sessionExpirationMs))
                .signWith(sessionKey())
                .compact();
    }

    public Claims parseSessionToken(String token) {
        return Jwts.parser().verifyWith(sessionKey()).build()
                .parseSignedClaims(token).getPayload();
    }

    public boolean isValidSessionToken(String token) {
        try {
            parseSessionToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
