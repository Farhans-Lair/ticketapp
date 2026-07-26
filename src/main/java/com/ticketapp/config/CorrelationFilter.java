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

/* CorrelationFilter — Structured Logging MDC population Runs once per HTTP request (before all other filters) and */
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

        // Re-use an upstream correlation ID if the caller provided one, otherwise generate a new UUID for
        String correlationId = request.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        try {
            MDC.put(MDC_CORR_ID, correlationId);
            MDC.put(MDC_METHOD,  request.getMethod());
            MDC.put(MDC_PATH,    request.getRequestURI());

            // Echo the correlation ID back so the caller can match their request to a specific log
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
