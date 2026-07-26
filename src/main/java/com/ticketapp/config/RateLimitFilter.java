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

/* RateLimitFilter — Token-bucket rate limiting for auth endpoints WHY AUTH ENDPOINTS? /auth/signup-request, /auth/signup-verify, /auth/login-request, /auth/login-verify and */
@Component
@Order(2)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    /* Paths that are subject to rate limiting. */
    private static final String[] RATE_LIMITED_PATHS = {
        "/auth/signup-request",
        "/auth/signup-verify",
        "/auth/login-request",
        "/auth/login-verify",
        "/auth/organizer-signup-request",
        "/auth/organizer-signup-verify",
    };

    /* Per-IP bucket store. */
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Bucket parameters

    /* Maximum tokens in the bucket (= burst capacity). */
    private static final int    CAPACITY        = 10;

    /* Tokens refilled per period. */
    private static final int    REFILL_TOKENS   = 10;

    /* Refill period in seconds. */
    private static final long   REFILL_PERIOD_S = 60;

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

    // Helpers

    private boolean isRateLimited(String path) {
        for (String p : RATE_LIMITED_PATHS) {
            if (path.equals(p)) return true;
        }
        return false;
    }

    /* Resolves the real client IP. */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For may be a comma-separated list; the first is the client.
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /* Creates a new Bucket4j token bucket for one IP address. */
    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(
            CAPACITY,
            Refill.greedy(REFILL_TOKENS, Duration.ofSeconds(REFILL_PERIOD_S))
        );
        return Bucket.builder().addLimit(limit).build();
    }
}
