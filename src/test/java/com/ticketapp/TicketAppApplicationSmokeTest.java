package com.ticketapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/* Boots the full Spring application context in CI, against a fresh, empty MySQL container (see docker-build.yml */
@SpringBootTest
class TicketAppApplicationSmokeTest {

    @Test
    void contextLoads() {
        // Intentionally empty.
    }
}
