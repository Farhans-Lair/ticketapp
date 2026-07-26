package com.ticketapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/* Exclude UserDetailsServiceAutoConfiguration to prevent Spring Boot from auto-generating a random password and registering a default InMemoryUserDetailsManager. */
@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
@EnableScheduling
@EnableAsync
public class TicketAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(TicketAppApplication.class, args);
    }
}
