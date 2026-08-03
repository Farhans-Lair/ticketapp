package com.ticketapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketapp.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private SecureRandom random = new SecureRandom();

    // In-memory fallback — used when Redis is unreachable.
    private final ConcurrentHashMap<String, LocalEntry> localStore = new ConcurrentHashMap<>();

    private record LocalEntry(String otp, Object payload, long expiresAt) {}

    // generate()

    /* Generates a 6-digit OTP and persists it with a 10-min TTL. */
    public String generate(String email, String purpose, Object payload) {
        String otp = String.format("%06d", random.nextInt(1_000_000));

        try {
            String value = objectMapper.writeValueAsString(
                Map.of("otp", otp, "payload", payload));
            redis.opsForValue().set(redisKey(email, purpose), value, OTP_TTL);
            log.debug("OTP stored in Redis: email={} purpose={}", email, purpose);

        } catch (Exception e) {
            // Redis unreachable — store in memory.
            log.warn("Redis unavailable during generate ({}), using in-memory store for email={}",
                     e.getMessage(), email);
            localStore.put(localKey(email, purpose),
                new LocalEntry(otp, payload, System.currentTimeMillis() + OTP_TTL_MS));
        }

        return otp;
    }

    // verify()

    /* Verifies and consumes the OTP. */
    public Object verify(String email, String otp, String purpose) {

        // Step 1: Isolated Redis GET Any exception here (connection refused, timeout, auth failure) sets redisAvailable=false and
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

        // Step 2: Validate from Redis (if Redis responded and key was present)
        if (redisAvailable && redisValue != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> stored = objectMapper.readValue(redisValue, Map.class);

                if (!otp.equals((String) stored.get("otp")))
                    throw new UnauthorizedException("Invalid OTP. Please try again.");

                redis.delete(redisKey(email, purpose));     // One-time use
                log.debug("OTP verified from Redis: email={} purpose={}", email, purpose);
                return stored.get("payload");

            } catch (RuntimeException e) {
                throw e;      // "Invalid OTP" — re-throw directly to caller
            } catch (Exception e) {
                log.error("OTP JSON parse error: email={} error={}", email, e.getMessage());
                throw new UnauthorizedException("OTP verification failed. Please request a new one.");
            }
        }

        // Step 3: In-memory fallback Reached when: a) Redis was down during generate() → OTP was stored
        String     lk    = localKey(email, purpose);
        LocalEntry entry = localStore.get(lk);

        if (entry == null)
            throw new UnauthorizedException("OTP not found or has expired. Please request a new one.");

        if (System.currentTimeMillis() > entry.expiresAt()) {
            localStore.remove(lk);
            throw new UnauthorizedException("OTP has expired. Please request a new one.");
        }

        if (!otp.equals(entry.otp()))
            throw new UnauthorizedException("Invalid OTP. Please try again.");

        localStore.remove(lk);     // One-time use
        log.debug("OTP verified from in-memory store: email={} purpose={}", email, purpose);
        return entry.payload();
    }

    // Scheduled sweep

    /* Removes expired entries from the in-memory fallback map every 5 minutes. */
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

    // Helpers

    private String redisKey(String email, String purpose) {
        return KEY_PREFIX + email + ":" + purpose;
    }

    private String localKey(String email, String purpose) {
        return email + ":" + purpose;
    }
}
