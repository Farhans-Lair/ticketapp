package com.ticketapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTP store — Redis primary, in-memory fallback.
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Redis running (production / Docker Compose)                    │
 * │    → OTPs stored in Redis with 10-min TTL.                      │
 * │    → Survives restarts. Shared across all running instances.    │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  Redis NOT running (local dev without Docker)                   │
 * │    → generate() catches the connection error silently.          │
 * │    → OTP stored in ConcurrentHashMap with the same TTL.         │
 * │    → verify() checks Redis first, then in-memory.               │
 * │    → Full OTP flow works with NO extra setup needed locally.    │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * Key format  :  otp:{email}:{purpose}
 * Value format:  JSON {"otp":"123456","payload":{...}}
 * TTL         :  10 minutes (Redis key expiry or in-memory sweep)
 *
 * Public API is identical to the original ConcurrentHashMap version —
 * no callers (AuthService) need to change.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OtpStore {

    private static final Duration OTP_TTL    = Duration.ofMinutes(10);
    private static final long     OTP_TTL_MS = 10 * 60 * 1000L;
    private static final String   KEY_PREFIX = "otp:";

    // Spring-injected — included in Lombok constructor (final, no initializer)
    private final StringRedisTemplate redis;
    private final ObjectMapper        objectMapper;

    // Field-initialised — NOT injected by Spring.
    // Non-final so Lombok's @RequiredArgsConstructor does not include it in
    // the generated constructor (Lombok only picks up uninitialized final fields).
    private SecureRandom random = new SecureRandom();

    // ── In-memory fallback ────────────────────────────────────────────────────
    // Used when Redis is unreachable (local dev). Keeps the same email:purpose
    // key scheme as Redis so both stores are always consistent.
    private record LocalEntry(String otp, Object payload, long expiresAt) {}

    // final + initializer: Lombok correctly excludes this from the constructor.
    private final ConcurrentHashMap<String, LocalEntry> localStore = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generates a 6-digit OTP and stores it with a 10-minute TTL.
     * Tries Redis first; silently falls back to in-memory on any connection error.
     */
    public String generate(String email, String purpose, Object payload) {
        String otp = String.format("%06d", random.nextInt(1_000_000));

        try {
            String value = objectMapper.writeValueAsString(
                Map.of("otp", otp, "payload", payload));
            redis.opsForValue().set(redisKey(email, purpose), value, OTP_TTL);
            log.debug("OTP stored in Redis: email={} purpose={}", email, purpose);

        } catch (Exception e) {
            // Redis unreachable — store in memory instead.
            // verify() checks in-memory as a fallback, so the flow works end-to-end.
            log.warn("Redis unavailable ({}), using in-memory OTP store for email={}",
                     e.getMessage(), email);
            localStore.put(localKey(email, purpose),
                new LocalEntry(otp, payload, System.currentTimeMillis() + OTP_TTL_MS));
        }

        return otp;
    }

    /**
     * Verifies and consumes the OTP for the given email + purpose.
     * Checks Redis first, then the in-memory fallback.
     * Throws RuntimeException on failure — message is shown directly to the user.
     */
    public Object verify(String email, String otp, String purpose) {

        // ── 1. Try Redis ──────────────────────────────────────────────────────
        try {
            String key   = redisKey(email, purpose);
            String value = redis.opsForValue().get(key);

            if (value != null) {
                // OTP found in Redis — validate and consume it
                @SuppressWarnings("unchecked")
                Map<String, Object> stored = objectMapper.readValue(value, Map.class);

                if (!otp.equals((String) stored.get("otp")))
                    throw new RuntimeException("Invalid OTP. Please try again.");

                redis.delete(key);
                log.debug("OTP verified from Redis: email={} purpose={}", email, purpose);
                return stored.get("payload");
            }
            // Key absent from Redis — fall through to in-memory check.
            // Handles: Redis was down during generate() so OTP is in localStore.

        } catch (RuntimeException e) {
            throw e;   // Re-throw our own validation errors (wrong OTP, etc.) as-is
        } catch (Exception e) {
            // Redis connection error during verify — fall through to in-memory
            log.warn("Redis unavailable during verify ({}), checking in-memory store", e.getMessage());
        }

        // ── 2. Try in-memory fallback ─────────────────────────────────────────
        String     lk    = localKey(email, purpose);
        LocalEntry entry = localStore.get(lk);

        if (entry == null)
            throw new RuntimeException("OTP not found or has expired. Please request a new one.");

        if (System.currentTimeMillis() > entry.expiresAt()) {
            localStore.remove(lk);
            throw new RuntimeException("OTP has expired. Please request a new one.");
        }

        if (!otp.equals(entry.otp()))
            throw new RuntimeException("Invalid OTP. Please try again.");

        localStore.remove(lk);   // One-time use
        log.debug("OTP verified from in-memory store: email={} purpose={}", email, purpose);
        return entry.payload();
    }

    // ── Maintenance ───────────────────────────────────────────────────────────

    /**
     * Sweeps expired entries from the in-memory store every 5 minutes.
     * Redis handles its own TTL — this sweep only cleans the fallback map.
     */
    @Scheduled(fixedRate = 300_000)
    public void sweepExpiredLocal() {
        long now     = System.currentTimeMillis();
        int  removed = 0;
        var  it      = localStore.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAt() < now) { it.remove(); removed++; }
        }
        if (removed > 0) log.debug("Swept {} expired in-memory OTP entries", removed);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String redisKey(String email, String purpose) {
        return KEY_PREFIX + email + ":" + purpose;
    }

    private String localKey(String email, String purpose) {
        return email + ":" + purpose;
    }
}
