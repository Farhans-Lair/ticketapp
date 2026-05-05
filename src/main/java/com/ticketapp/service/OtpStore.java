package com.ticketapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;

/**
 * Redis-backed OTP store.
 *
 * Replaces the previous ConcurrentHashMap implementation which had two problems:
 *  1. All OTPs were lost on every application restart.
 *  2. Multiple running instances (AWS ASG) each had their own isolated map —
 *     a user requesting an OTP on instance A could not verify it on instance B.
 *
 * Redis fixes both: state is external, shared, and persistent across restarts.
 * TTL is delegated to Redis via SETEX so no scheduled sweep thread is needed.
 *
 * Key design
 * ──────────
 * Key  :  otp:{email}:{purpose}
 *   e.g.  otp:alice@example.com:signup
 *         otp:alice@example.com:login
 *         otp:alice@example.com:organizer-signup
 *
 * One key per email+purpose pair allows a user to have simultaneous pending
 * flows (e.g. login on one tab, organizer-signup on another) without collision.
 * Storing the purpose in the key — rather than inside the JSON value — also
 * means we never accidentally consume an OTP for the wrong flow.
 *
 * Value:  JSON string — { "otp": "123456", "payload": { ... } }
 * TTL:    10 minutes (Redis expires the key automatically; no manual sweep needed)
 *
 * Public API is identical to the old ConcurrentHashMap version so no callers change.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OtpStore {

    private static final Duration OTP_TTL    = Duration.ofMinutes(10);
    private static final String   KEY_PREFIX = "otp:";

    private final StringRedisTemplate redis;
    private final ObjectMapper        objectMapper;
    private final SecureRandom        random = new SecureRandom();

    // ── Public API (unchanged from the old in-memory version) ─────────────────

    /**
     * Generates a 6-digit OTP, stores it in Redis with a 10-minute TTL, and returns it.
     * Any previous pending OTP for the same email+purpose is silently overwritten —
     * this matches the old behaviour and means "resend OTP" just replaces the old one.
     */
    public String generate(String email, String purpose, Object payload) {
        String otp = String.format("%06d", random.nextInt(1_000_000));

        try {
            // Store otp + payload together so a single Redis GET retrieves everything needed for verify()
            String value = objectMapper.writeValueAsString(Map.of("otp", otp, "payload", payload));
            redis.opsForValue().set(redisKey(email, purpose), value, OTP_TTL);
            log.debug("OTP generated: email={} purpose={}", email, purpose);
        } catch (Exception e) {
            log.error("Redis OTP store failed: {}", e.getMessage());
            throw new RuntimeException("Could not store verification code. Please try again.");
        }

        return otp;
    }

    /**
     * Verifies and consumes the OTP.
     * Throws RuntimeException (message shown to user) on any failure.
     * Returns the stored payload on success.
     *
     * Callers cast the return value to Map<String, Object> as before:
     *   @SuppressWarnings("unchecked")
     *   Map<String, Object> payload = (Map<String, Object>) otpStore.verify(email, otp, "login");
     *
     * Numeric values (e.g. userId stored as Long) deserialize as Integer after the
     * JSON round-trip for small numbers. All existing callers already handle this via
     * ((Number) payload.get("userId")).longValue() so no changes are needed there.
     */
    public Object verify(String email, String otp, String purpose) {
        String key   = redisKey(email, purpose);
        String value = redis.opsForValue().get(key);

        // Key missing → either never generated, or already expired, or already consumed
        if (value == null)
            throw new RuntimeException("OTP not found or has expired. Please request a new one.");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> stored    = objectMapper.readValue(value, Map.class);
            String              storedOtp = (String) stored.get("otp");

            if (!otp.equals(storedOtp))
                throw new RuntimeException("Invalid OTP. Please try again.");

            // One-time use: delete the key immediately after a successful match.
            // If the DELETE fails (e.g. Redis blip), the key will still expire via TTL —
            // the risk of reuse within the TTL window is acceptable for a booking app.
            redis.delete(key);
            log.debug("OTP verified and consumed: email={} purpose={}", email, purpose);

            return stored.get("payload");

        } catch (RuntimeException e) {
            throw e;    // Re-throw our own validation errors unchanged
        } catch (Exception e) {
            log.error("OTP verification deserialization error: email={} error={}", email, e.getMessage());
            throw new RuntimeException("OTP verification failed. Please request a new one.");
        }
    }

    // ── Private helper ────────────────────────────────────────────────────────

    private String redisKey(String email, String purpose) {
        return KEY_PREFIX + email + ":" + purpose;
    }
}
