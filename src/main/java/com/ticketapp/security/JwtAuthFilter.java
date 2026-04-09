package com.ticketapp.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
        // Only skip the public auth endpoints (signup/login flows) and static assets.
        if (
            path.equals("/") ||
            path.equals("/auth/signup-request") ||
            path.equals("/auth/signup-verify") ||
            path.equals("/auth/login-request") ||
            path.equals("/auth/login-verify") ||
            path.equals("/auth/organizer-signup-request") ||
            path.equals("/auth/organizer-signup-verify") ||
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

        if (token != null && jwtUtil.isValid(token)) {
            Claims claims = jwtUtil.parseToken(token);
            Long userId = claims.get("id", Long.class);
            String role = claims.get("role", String.class);

            AuthenticatedUser auth = new AuthenticatedUser(userId, role);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        // 1. Authorization header
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        // 2. Cookie fallback
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(c -> "token".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }
}