package com.ticketapp.scheduler;

import com.ticketapp.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * SeatHoldScheduler — Feature 4: seat hold timer.
 *
 * Runs every 60 seconds and releases any seat whose held_until timestamp
 * has passed. This prevents two users on the payment page from both
 * thinking they have the same seat.
 *
 * The @EnableScheduling annotation is already on TicketAppApplication.
 * No additional configuration is required — this bean is picked up
 * automatically by Spring's @Component scan.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeatHoldScheduler {

    private final SeatRepository seatRepo;

    /**
     * Fires every 60 seconds (fixedDelay measures from job end, not start).
     * Uses a separate transaction so a failure here does not affect any
     * ongoing booking transaction.
     */
    @Scheduled(fixedDelayString = "${seat.hold.sweep.interval.ms:60000}")
    @Transactional
    public void releaseExpiredHolds() {
        int released = seatRepo.releaseExpiredHolds(LocalDateTime.now());
        if (released > 0) {
            log.info("SeatHoldScheduler: released {} expired seat hold(s)", released);
        }
    }
}
