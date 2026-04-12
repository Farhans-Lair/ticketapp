package com.ticketapp.config;

import com.ticketapp.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── CRITICAL: Disable request caching ─────────────────────────────────
            // By default, Spring Security's ExceptionTranslationFilter tries to save
            // the failed request into a RequestCache (HttpSessionRequestCache) before
            // calling the accessDeniedHandler or authenticationEntryPoint.
            // This cache write happens BEFORE our custom handler runs, meaning the
            // response stream is already partially written when our handler tries to
            // write the JSON body. The result is a double-write that corrupts the
            // HTTP response stream, causing ERR_INCOMPLETE_CHUNKED_ENCODING in the
            // browser even though the status code shows 403 correctly.
            // NullRequestCache disables this entirely — for a stateless JWT API,
            // we never need to replay saved requests.
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))

            // ── Custom 401/403 JSON responses ──────────────────────────────────────
            // Spring Security's filter-level exceptions bypass @ControllerAdvice /
            // GlobalExceptionHandler entirely. Without these handlers, 401/403
            // responses have no body, which causes res.json() in the frontend to
            // throw a SyntaxError and show "Network error" instead of the real cause.
            .exceptionHandling(ex -> ex
                .accessDeniedHandler((request, response, exception) -> {
                    if (!response.isCommitted()) {
                        response.setContentType("application/json;charset=UTF-8");
                        response.setStatus(403);
                        response.getWriter().write(
                            "{\"error\":\"You do not have permission to access this resource.\"}");
                        response.getWriter().flush();
                    }
                })
                .authenticationEntryPoint((request, response, exception) -> {
                    if (!response.isCommitted()) {
                        response.setContentType("application/json;charset=UTF-8");
                        response.setStatus(401);
                        response.getWriter().write(
                            "{\"error\":\"Not authenticated. Please log in.\"}");
                        response.getWriter().flush();
                    }
                })
            )
            .authorizeHttpRequests(auth -> auth

                // ── Health & error ─────────────────────────────────────────
                .requestMatchers("/health", "/error").permitAll()

                // ── All HTML pages ─────────────────────────────────────────
                .requestMatchers("/*.html", "/index.html").permitAll()

                // ── All frontend routes ────────────────────────────────────
                .requestMatchers(
                    "/", "/events-page", "/my-bookings", "/payment",
                    "/seat-selection", "/organizer-register",
                    "/organizer-dashboard", "/organizer-events",
                    "/organizer-revenue", "/admin", "/admin/**"
                ).permitAll()

                // ── Static assets ──────────────────────────────────────────
                .requestMatchers("/js/**", "/css/**", "/images/**", "/favicon.ico").permitAll()

                // ── Auth endpoints (public) ────────────────────────────────
                .requestMatchers(HttpMethod.POST, "/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/auth/me").authenticated()

                // ── Everything else requires login ─────────────────────────
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",
                "http://localhost:8080",
                frontendUrl
        ));

        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
