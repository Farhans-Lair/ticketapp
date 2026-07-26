package com.ticketapp.scheduler;

import com.ticketapp.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeatHoldScheduler {

    private final SeatRepository seatRepo;

    /* Fires every 60 seconds (fixedDelay measures from job end, not start). */
    @Scheduled(fixedDelayString = "${seat.hold.sweep.interval.ms:60000}")
    @Transactional
    public void releaseExpiredHolds() {
        int released = seatRepo.releaseExpiredHolds(LocalDateTime.now());
        if (released > 0) {
            log.info("SeatHoldScheduler: released {} expired seat hold(s)", released);
        }
    }
}
