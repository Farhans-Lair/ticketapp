package com.ticketapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RateLimitFilter — Token-bucket rate limiting for auth endpoints
 * ═══════════════════════════════════════════════════════════════
 *
 * WHY AUTH ENDPOINTS?
 *   /auth/signup-request, /auth/signup-verify, /auth/login-request,
 *   /auth/login-verify and the organizer equivalents are the most
 *   abuse-prone surface: OTP brute-force, credential stuffing, and
 *   email-bombing attacks all target these paths.
 *
 * STRATEGY: Token Bucket per client IP
 *   Each unique client IP gets its own bucket. The bucket is
 *   refilled at a fixed rate regardless of consumption — burst
 *   allowance lets legitimate users retry quickly, while a sustained
 *   flood is throttled.
 *
 * LIMITS (tunable via application.properties):
 *   • 10 requests per minute per IP (refill: 10 tokens / 60 s)
 *   • Burst capacity: 10 tokens (no extra burst beyond the refill rate)
 *
 *   These values are conservative and safe for normal use:
 *     - A user clicking "resend OTP" 3–4 times in a minute is fine.
 *     - An attacker sending 100 OTP requests/min is blocked after the 10th.
 *
 * RESPONSE ON THROTTLE:
 *   HTTP 429 Too Many Requests with JSON body:
 *     { "error": "Too many requests. Please wait a moment and try again." }
 *   And a Retry-After header (seconds until the bucket refills one token).
 *
 * STORAGE: ConcurrentHashMap (in-memory)
 *   Correct for a single-instance EC2 deployment. For a multi-instance
 *   setup, replace with Bucket4j + Redisson (Redis-backed) — the filter
 *   logic is identical, only the bucket creation changes.
 *
 * ORDER: 2 — runs after CorrelationFilter (order 1) so that correlationId
 *   is already in MDC when the 429 log line is emitted.
 */
@Component
@Order(2)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    /**
     * Paths that are subject to rate limiting.
     * All are under /auth/ — the OTP and login flows.
     */
    private static final String[] RATE_LIMITED_PATHS = {
        "/auth/signup-request",
        "/auth/signup-verify",
        "/auth/login-request",
        "/auth/login-verify",
        "/auth/organizer-signup-request",
        "/auth/organizer-signup-verify",
    };

    /**
     * Per-IP bucket store. ConcurrentHashMap handles concurrent requests
     * from the same IP without synchronisation on the happy path.
     * Memory: each Bucket4j bucket is ~200 bytes; 100 k unique IPs ≈ 20 MB.
     */
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Bucket parameters ─────────────────────────────────────────────────────

    /** Maximum tokens in the bucket (= burst capacity). */
    private static final int    CAPACITY        = 10;

    /** Tokens refilled per period. */
    private static final int    REFILL_TOKENS   = 10;

    /** Refill period in seconds. */
    private static final long   REFILL_PERIOD_S = 60;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only apply to rate-limited paths — all other routes pass through.
        if (!isRateLimited(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        Bucket bucket   = buckets.computeIfAbsent(clientIp, ip -> createBucket());

        if (bucket.tryConsume(1)) {
            // Token consumed — allow the request through.
            filterChain.doFilter(request, response);
        } else {
            // Bucket empty — reject with 429.
            long waitSeconds = bucket.getAvailableTokens() == 0
                ? REFILL_PERIOD_S
                : 1L;

            log.warn("Rate limit exceeded for IP={} path={}", clientIp, path);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(waitSeconds));

            String body = objectMapper.writeValueAsString(Map.of(
                "error", "Too many requests. Please wait a moment and try again."
            ));
            response.getWriter().write(body);
            response.getWriter().flush();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isRateLimited(String path) {
        for (String p : RATE_LIMITED_PATHS) {
            if (path.equals(p)) return true;
        }
        return false;
    }

    /**
     * Resolves the real client IP.
     * Behind an ALB, the original IP is in X-Forwarded-For.
     * Falls back to getRemoteAddr() for local dev / direct connections.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For may be a comma-separated list; the first is the client.
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Creates a new Bucket4j token bucket for one IP address.
     * 10 tokens refilled every 60 seconds, no burst beyond that.
     */
    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(
            CAPACITY,
            Refill.greedy(REFILL_TOKENS, Duration.ofSeconds(REFILL_PERIOD_S))
        );
        return Bucket.builder().addLimit(limit).build();
    }
}
