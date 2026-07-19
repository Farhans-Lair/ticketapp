package com.ticketapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the full Spring application context in CI, against a fresh, empty
 * MySQL container (see docker-build.yml — no more db/schema.sql pre-load).
 *
 * This single test is deliberately minimal — the point isn't the assertion,
 * it's what happens on the way to it:
 *
 *   1. Flyway runs V1 through the latest migration in order, from scratch,
 *      building the schema exactly as a brand-new production environment
 *      would see it.
 *   2. Hibernate then validates every JPA entity against that freshly-built
 *      schema (spring.jpa.hibernate.ddl-auto=validate in
 *      application-prod.properties — CI runs with the base profile, which
 *      currently defaults to ddl-auto=update; see the note below).
 *
 * This is the exact sequence that failed in production when an INT/BIGINT
 * mismatch (V1-era columns vs. entity Long fields) was caught for the
 * first time by ddl-auto=validate. A test like this, run on every push,
 * would have caught it here — in under two minutes, before merge — instead
 * of after a live deploy.
 *
 * NOTE: for this test to actually exercise ddl-auto=validate (not just
 * ddl-auto=update, which never fails), activate the "prod" Spring profile
 * for the test run — see the -Dspring.profiles.active=prod flag on the
 * Maven command in docker-build.yml.
 */
@SpringBootTest
class TicketAppApplicationSmokeTest {

    @Test
    void contextLoads() {
        // Intentionally empty. If Flyway or Hibernate schema validation
        // fails, this test fails during context startup — before the
        // @Test method body ever runs — which is exactly what we want.
    }
}
