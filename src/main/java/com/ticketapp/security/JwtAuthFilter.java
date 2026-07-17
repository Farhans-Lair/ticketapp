package com.ticketapp.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // 🔥 SKIP JWT for PUBLIC routes
        // NOTE: /auth/me is intentionally NOT skipped — it requires authentication.
        // /auth/refresh is skipped here because it authenticates via the
        // REFRESH token (a different secret/cookie entirely) — it validates
        // that itself in AuthController, not via this access-token filter.
        // Only skip the public auth endpoints (signup/login flows) and static assets.
        if (
            path.equals("/") ||
            path.equals("/auth/signup-request") ||
            path.equals("/auth/signup-verify") ||
            path.equals("/auth/login-request") ||
            path.equals("/auth/login-verify") ||
            path.equals("/auth/organizer-signup-request") ||
            path.equals("/auth/organizer-signup-verify") ||
            path.equals("/auth/refresh") ||
            path.equals("/auth/logout") ||
            path.startsWith("/js") ||
            path.startsWith("/css") ||
            path.equals("/error") ||
            path.equals("/health")
        ) {
            chain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);

        if (token != null && jwtUtil.isValidAccessToken(token)) {
            Claims claims = jwtUtil.parseAccessToken(token);
            Long userId = claims.get("id", Long.class);
            String role = claims.get("role", String.class);
            String sessionId = claims.get("sid", String.class);

            // Write authenticated userId into MDC so every log line emitted
            // during this request carries the user context automatically.
            // CorrelationFilter clears this in its finally block.
            if (userId != null) {
                MDC.put("userId", String.valueOf(userId));
            }

            AuthenticatedUser auth = new AuthenticatedUser(userId, role, sessionId);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
        }
    }

    private String resolveToken(HttpServletRequest request) {
        // 1. Authorization header
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        // 2. Cookie fallback — access_token (renamed from the old single "token"
        //    cookie now that access/refresh/session are three separate cookies).
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(c -> "access_token".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }
}
