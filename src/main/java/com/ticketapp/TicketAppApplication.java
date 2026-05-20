package com.ticketapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
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
 *
 * @EnableAsync activates Spring's @Async proxy so that all EmailService methods
 * annotated with @Async run in a background thread.  Without this annotation,
 * @Async is silently ignored and every email call runs synchronously inside the
 * caller's @Transactional method.  When the mail server is unavailable,
 * JavaMailSender throws MailException (a RuntimeException) which Spring's
 * @Transactional intercepts and rolls back the entire transaction — causing
 * payout and booking records to be discarded even though they were successfully
 * saved moments earlier.  The admin payout dashboard therefore showed
 * "No payouts found" even after an organizer submitted a request.
 */
@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
@EnableScheduling
@EnableAsync
public class TicketAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(TicketAppApplication.class, args);
    }
}
