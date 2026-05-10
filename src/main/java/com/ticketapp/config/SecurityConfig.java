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
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
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
                    "/organizer-revenue", "/admin", "/admin/**",
                    "/my-profile"            // Feature 10: user profile page
                ).permitAll()

                // ── Static assets ──────────────────────────────────────────
                .requestMatchers("/js/**", "/css/**", "/images/**", "/favicon.ico").permitAll()

                // ── Event image proxy (public) ─────────────────────────────
                .requestMatchers(HttpMethod.GET, "/api/images/**").permitAll()

                // ── Auth endpoints (public) ────────────────────────────────
                .requestMatchers(HttpMethod.POST, "/auth/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/cancellations/webhook/refund").permitAll()
                .requestMatchers(HttpMethod.GET,  "/auth/me").authenticated()

                // ── Feature 2: Search + city-picker (public) ───────────────
                .requestMatchers(HttpMethod.GET, "/search/**").permitAll()

                // ── Feature 5: Reviews (public read) ──────────────────────
                .requestMatchers(HttpMethod.GET, "/reviews/**").permitAll()

                // ── Feature 9: Waitlist stats (public) ────────────────────
                .requestMatchers(HttpMethod.GET, "/waitlist/*/stats").permitAll()

                // ── Feature 11: Featured + Trending events (public read) ───
                .requestMatchers(HttpMethod.GET, "/events/featured").permitAll()
                .requestMatchers(HttpMethod.GET, "/events/trending").permitAll()
                .requestMatchers(HttpMethod.GET, "/events").permitAll()
                .requestMatchers(HttpMethod.GET, "/events/*").permitAll()

                // ── Feature 10: User profile (authenticated) ──────────────
                .requestMatchers("/user/**").authenticated()

                // ── Feature 14: Payouts (authenticated) ───────────────────
                .requestMatchers("/payouts/**").authenticated()

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
                "https://localhost:8443",
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
