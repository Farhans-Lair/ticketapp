package com.ticketapp.config;

import com.ticketapp.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
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


            // Exception handlers: return clean JSON for 401/403.
            // These fire BEFORE GlobalExceptionHandler (which can't intercept
            // Spring Security's filter-level exceptions).
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

                // ── All HTML pages (Spring Security blocks forwarded requests
                //    to /index.html, /events.html etc. unless explicitly listed)
                .requestMatchers("/*.html", "/index.html").permitAll()

                // ── All frontend routes (SPA pages served by WebConfig) ─────
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