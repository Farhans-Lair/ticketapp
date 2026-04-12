package com.ticketapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Exclude UserDetailsServiceAutoConfiguration to prevent Spring Boot from
 * auto-generating a random password and registering a default InMemoryUserDetailsManager.
 *
 * Without this exclusion, Spring Security's auto-configuration:
 *  1. Prints "Using generated security password: ..." in logs
 *  2. Registers a UserDetailsService that conflicts with our JWT-based setup
 *  3. Can cause @AuthenticationPrincipal to resolve as null in controllers
 *     because the default security infrastructure interferes with our
 *     custom SecurityContext population in JwtAuthFilter
 */
@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
@EnableScheduling
public class TicketAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(TicketAppApplication.class, args);
    }
}
