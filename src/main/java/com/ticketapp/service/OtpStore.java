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
 * │  Redis NOT running (local dev / AWS without ElastiCache)        │
 * │    → generate() catches connection error, stores in-memory.     │
 * │    → verify() isolates the Redis GET, falls back to in-memory.  │
 * │    → Full OTP flow works with NO extra setup needed.            │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * Key   :  otp:{email}:{purpose}
 * Value :  JSON {"otp":"123456","payload":{...}}
 * TTL   :  10 minutes (Redis key expiry or in-memory sweep)
 *
 * BUG FIXED (verify):
 *   Previous version had a single try-catch block in verify() that caught
 *   RuntimeException to re-throw our own validation errors (e.g. "Invalid OTP").
 *   But RedisConnectionFailureException also extends RuntimeException, so it was
 *   caught and re-thrown instead of falling through to the in-memory store.
 *   Fix: isolate the Redis GET in its own try-catch so connection errors can
 *   never escape past the fallback logic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OtpStore {

    private static final Duration OTP_TTL    = Duration.ofMinutes(10);
    private static final long     OTP_TTL_MS = 10 * 60 * 1000L;
    private static final String   KEY_PREFIX = "otp:";

    // Spring-injected (final, no initializer → picked up by @RequiredArgsConstructor)
    private final StringRedisTemplate redis;
    private final ObjectMapper        objectMapper;

    // Field-initialised — NOT injected by Spring.
    // Non-final so Lombok's @RequiredArgsConstructor ignores it.
    private SecureRandom random = new SecureRandom();

    // In-memory fallback — used when Redis is unreachable.
    // final + initializer: Lombok correctly excludes this from the constructor.
    private final ConcurrentHashMap<String, LocalEntry> localStore = new ConcurrentHashMap<>();

    private record LocalEntry(String otp, Object payload, long expiresAt) {}

    // ── generate() ───────────────────────────────────────────────────────────

    /**
     * Generates a 6-digit OTP and persists it with a 10-min TTL.
     * Tries Redis first; on any connection failure silently stores in-memory.
     */
    public String generate(String email, String purpose, Object payload) {
        String otp = String.format("%06d", random.nextInt(1_000_000));

        try {
            String value = objectMapper.writeValueAsString(
                Map.of("otp", otp, "payload", payload));
            redis.opsForValue().set(redisKey(email, purpose), value, OTP_TTL);
            log.debug("OTP stored in Redis: email={} purpose={}", email, purpose);

        } catch (Exception e) {
            // Redis unreachable — store in memory. verify() checks in-memory as fallback.
            log.warn("Redis unavailable during generate ({}), using in-memory store for email={}",
                     e.getMessage(), email);
            localStore.put(localKey(email, purpose),
                new LocalEntry(otp, payload, System.currentTimeMillis() + OTP_TTL_MS));
        }

        return otp;
    }

    // ── verify() ─────────────────────────────────────────────────────────────

    /**
     * Verifies and consumes the OTP. Throws RuntimeException on any failure.
     *
     * KEY DESIGN: The Redis GET is fully isolated in its own try-catch.
     * This means RedisConnectionFailureException (which extends RuntimeException)
     * can NEVER escape past the fallback logic. The validation code that throws
     * our own RuntimeExceptions ("Invalid OTP" etc.) runs AFTER the isolated GET,
     * so those errors are never swallowed by the connection-error handler.
     *
     *   Step 1  →  try { redisValue = redis.get(key) }
     *                catch(any) { redisAvailable = false }   ← connection errors stop here
     *
     *   Step 2  →  if (redisAvailable && redisValue != null) validate from Redis
     *                throws "Invalid OTP" directly to caller — NOT caught anywhere
     *
     *   Step 3  →  if (!redisAvailable || redisValue == null) check localStore
     *                throws "OTP not found / expired / invalid" directly to caller
     */
    public Object verify(String email, String otp, String purpose) {

        // ── Step 1: Isolated Redis GET ────────────────────────────────────────
        // Any exception here (connection refused, timeout, auth failure) sets
        // redisAvailable=false and execution continues to the in-memory check.
        // No validation RuntimeExceptions are thrown inside this block.
        String  redisValue    = null;
        boolean redisAvailable = true;

        try {
            redisValue = redis.opsForValue().get(redisKey(email, purpose));
            log.debug("Redis GET: email={} purpose={} found={}", email, purpose, redisValue != null);
        } catch (Exception e) {
            redisAvailable = false;
            log.warn("Redis unavailable during verify ({}), falling back to in-memory for email={}",
                     e.getMessage(), email);
        }

        // ── Step 2: Validate from Redis (if Redis responded and key was present) ─
        if (redisAvailable && redisValue != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> stored = objectMapper.readValue(redisValue, Map.class);

                if (!otp.equals((String) stored.get("otp")))
                    throw new RuntimeException("Invalid OTP. Please try again.");

                redis.delete(redisKey(email, purpose));   // One-time use
                log.debug("OTP verified from Redis: email={} purpose={}", email, purpose);
                return stored.get("payload");

            } catch (RuntimeException e) {
                throw e;    // "Invalid OTP" — re-throw directly to caller
            } catch (Exception e) {
                log.error("OTP JSON parse error: email={} error={}", email, e.getMessage());
                throw new RuntimeException("OTP verification failed. Please request a new one.");
            }
        }

        // ── Step 3: In-memory fallback ─────────────────────────────────────────
        // Reached when:
        //   a) Redis was down during generate() → OTP was stored in localStore
        //   b) Redis was down during verify()   → redisAvailable = false
        // Both cases land here and work identically.
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

    // ── Scheduled sweep ───────────────────────────────────────────────────────

    /**
     * Removes expired entries from the in-memory fallback map every 5 minutes.
     * Redis handles its own TTL — this only cleans the ConcurrentHashMap.
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
