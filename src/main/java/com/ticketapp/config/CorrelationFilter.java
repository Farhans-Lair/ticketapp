package com.ticketapp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * CorrelationFilter — Structured Logging MDC population
 * ═══════════════════════════════════════════════════════
 *
 * Runs once per HTTP request (before all other filters) and writes
 * the following fields into Logback's MDC (Mapped Diagnostic Context):
 *
 *   correlationId — a UUID generated per request, OR forwarded from the
 *                   "X-Correlation-ID" request header (so a gateway or
 *                   frontend can inject its own trace identifier).
 *   method        — HTTP method (GET, POST, …)
 *   path          — request URI (/auth/login-request, /bookings/my-bookings, …)
 *
 * The MDC fields are picked up automatically by LogstashEncoder when the
 * "json" Spring profile is active, producing log lines like:
 *
 *   {
 *     "timestamp": "2025-05-01T14:32:11.042Z",
 *     "level": "INFO",
 *     "logger": "BookingService",
 *     "message": "Booking confirmed for eventId=42",
 *     "correlationId": "a3f2c1d0-…",
 *     "userId": "17",
 *     "method": "POST",
 *     "path": "/bookings/confirm"
 *   }
 *
 * The "userId" field is written separately by JwtAuthFilter once the JWT
 * is decoded — it is intentionally absent on public (unauthenticated) routes.
 *
 * MDC is cleared in the finally block to prevent field leakage across
 * requests on the same Tomcat thread (thread-pool reuse).
 *
 * The X-Correlation-ID header is also written back to the response so
 * the frontend / API consumer can correlate logs to a specific request.
 *
 * Order(1) — runs before JwtAuthFilter (Order 2, via Spring Security's
 * filter chain) so that correlationId is set in MDC before any security
 * log lines are emitted.
 */
@Component
@Order(1)
public class CorrelationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME   = "X-Correlation-ID";
    private static final String MDC_CORR_ID   = "correlationId";
    private static final String MDC_METHOD    = "method";
    private static final String MDC_PATH      = "path";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Re-use an upstream correlation ID if the caller provided one,
        // otherwise generate a new UUID for this request.
        String correlationId = request.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        try {
            MDC.put(MDC_CORR_ID, correlationId);
            MDC.put(MDC_METHOD,  request.getMethod());
            MDC.put(MDC_PATH,    request.getRequestURI());

            // Echo the correlation ID back so the caller can match their
            // request to a specific log trace.
            response.setHeader(HEADER_NAME, correlationId);

            filterChain.doFilter(request, response);

        } finally {
            // Always clear MDC — threads are reused from a pool.
            MDC.remove(MDC_CORR_ID);
            MDC.remove(MDC_METHOD);
            MDC.remove(MDC_PATH);
            // userId is cleared by JwtAuthFilter, but also clear here as safety net.
            MDC.remove("userId");
        }
    }
}
