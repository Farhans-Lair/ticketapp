package com.ticketapp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory OTP store.
 * Exact port of otp.services.js: 6-digit numeric OTP, 10-min TTL, one-time use.
 */
@Component
@Slf4j
public class OtpStore {

    private static final long OTP_TTL_MS = 10 * 60 * 1000L;
    private final SecureRandom random = new SecureRandom();

    private record OtpEntry(String otp, String purpose, Object payload, long expiresAt) {}

    private final Map<String, OtpEntry> store = new ConcurrentHashMap<>();

    /** Generate a 6-digit OTP, store it, and return it. */
    public String generate(String email, String purpose, Object payload) {
        String otp = String.format("%06d", random.nextInt(1_000_000));
        store.put(email, new OtpEntry(otp, purpose, payload, System.currentTimeMillis() + OTP_TTL_MS));
        return otp;
    }

    /**
     * Verify and consume the OTP.
     * @return the stored payload on success
     * @throws RuntimeException on failure (expired, wrong purpose, wrong code)
     */
    public Object verify(String email, String otp, String purpose) {
        OtpEntry entry = store.get(email);

        if (entry == null)
            throw new RuntimeException("OTP not found. Please request a new one.");

        if (!entry.purpose().equals(purpose))
            throw new RuntimeException("Invalid OTP purpose.");

        if (System.currentTimeMillis() > entry.expiresAt()) {
            store.remove(email);
            throw new RuntimeException("OTP has expired. Please request a new one.");
        }

        if (!entry.otp().equals(otp))
            throw new RuntimeException("Invalid OTP. Please try again.");

        // Consume (one-time use)
        store.remove(email);
        return entry.payload();
    }

    /** Sweep expired entries every 5 minutes */
    @Scheduled(fixedRate = 300_000)
    public void sweepExpired() {
        long now = System.currentTimeMillis();
        int removed = (int) store.entrySet().removeIf(e -> e.getValue().expiresAt() < now) ?
                store.size() : 0;
        log.debug("OTP sweep complete");
    }
}
