package com.ticketapp.service;

import com.ticketapp.entity.RefreshToken;
import com.ticketapp.repository.RefreshTokenRepository;
import com.ticketapp.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

/**
 * Refresh-token rotation with stolen-token reuse detection.
 *
 * Rotation: each call to {@link #rotate} consumes the presented refresh
 * token — the matching DB row is marked revoked, and a brand new
 * access+refresh pair is issued and returned. The refresh token the
 * client was just holding becomes permanently unusable the instant it's
 * used, even if it hasn't expired yet.
 *
 * Reuse detection: if a refresh token that's already been marked revoked
 * is ever presented again, that can only mean one of two refresh tokens
 * from the same rotation chain has leaked (e.g. stolen and used by an
 * attacker, or the legitimate client retried after a lost response and
 * both ends now hold "the same" token at different rotation states).
 * Treating this as compromised and revoking the WHOLE session is the
 * standard, conservative response — it costs the legitimate user one
 * re-login in the retry case, in exchange for shutting an attacker out
 * immediately in the theft case.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepo;
    private final JwtUtil jwtUtil;

    public record IssuedTokens(String accessToken, String refreshToken, String sessionToken, String sessionId) {}

    // ── Issue a brand new session (login) ──────────────────────────────────

    @Transactional
    public IssuedTokens issueNewSession(Long userId, String role) {
        String sessionId = jwtUtil.newSessionId();
        return issuePairAndPersist(userId, role, sessionId, true).tokens();
    }

    // ── Rotate an existing refresh token ────────────────────────────────────

    @Transactional
    public IssuedTokens rotate(String presentedRefreshToken, String role) {
        Claims claims;
        try {
            claims = jwtUtil.parseRefreshToken(presentedRefreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new RefreshTokenException("Invalid or expired refresh token.");
        }

        Long userId = claims.get("id", Long.class);
        String sessionId = claims.get("sid", String.class);
        String hash = sha256(presentedRefreshToken);

        RefreshToken existing = refreshTokenRepo.findByTokenHash(hash)
                .orElseThrow(() -> new RefreshTokenException("Refresh token not recognized."));

        if (existing.isRevoked()) {
            // Reuse of an already-rotated token — treat the session as compromised.
            log.warn("Refresh token reuse detected for userId={} sessionId={} — revoking entire session",
                    userId, sessionId);
            refreshTokenRepo.revokeAllBySessionId(sessionId, LocalDateTime.now());
            throw new RefreshTokenException(
                    "This session has been invalidated for your security. Please log in again.");
        }

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RefreshTokenException("Refresh token has expired. Please log in again.");
        }

        // Consume the presented token, then issue a fresh pair under the same session.
        IssuePairResult fresh = issuePairAndPersist(userId, role, sessionId, false);

        existing.setRevoked(true);
        existing.setRevokedAt(LocalDateTime.now());
        existing.setReplacedById(fresh.newRowId());
        refreshTokenRepo.save(existing);

        return fresh.tokens();
    }

    // ── Logout ───────────────────────────────────────────────────────────

    /** Revokes every refresh token in this one session (normal logout). */
    @Transactional
    public void revokeSession(String sessionId) {
        int count = refreshTokenRepo.revokeAllBySessionId(sessionId, LocalDateTime.now());
        log.info("Revoked {} refresh token(s) for sessionId={}", count, sessionId);
    }

    /** Revokes every refresh token for a user across ALL sessions/devices ("log out everywhere"). */
    @Transactional
    public void revokeAllSessionsForUser(Long userId) {
        int count = refreshTokenRepo.revokeAllByUserId(userId, LocalDateTime.now());
        log.info("Revoked {} refresh token(s) for userId={} across all sessions", count, userId);
    }

    // ── Internals ────────────────────────────────────────────────────────

    private record IssuePairResult(IssuedTokens tokens, Long newRowId) {}

    private IssuePairResult issuePairAndPersist(Long userId, String role, String sessionId, boolean freshLogin) {
        String access  = jwtUtil.generateAccessToken(userId, role, sessionId);
        String refresh = jwtUtil.generateRefreshToken(userId, sessionId);
        String session  = freshLogin ? jwtUtil.generateSessionToken(userId, sessionId) : null;

        RefreshToken row = new RefreshToken();
        row.setUserId(userId);
        row.setSessionId(sessionId);
        row.setTokenHash(sha256(refresh));
        row.setExpiresAt(
                LocalDateTime.now().plusSeconds(jwtUtil.getRefreshExpirationMs() / 1000));
        RefreshToken saved = refreshTokenRepo.save(row);

        return new IssuePairResult(new IssuedTokens(access, refresh, session, sessionId), saved.getId());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a JDK-guaranteed algorithm; this can't actually happen.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static class RefreshTokenException extends RuntimeException {
        public RefreshTokenException(String message) {
            super(message);
        }
    }
}
