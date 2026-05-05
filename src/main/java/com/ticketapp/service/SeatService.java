package com.ticketapp.service;

import com.ticketapp.entity.Seat;
import com.ticketapp.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatService {

    private final SeatRepository seatRepo;

    private static final int    SEATS_PER_ROW = 10;
    private static final String ROWS          = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /** Generates seats exactly as the JS generateSeats() function does. */
    public List<Seat> generateSeats(Long eventId, int totalTickets) {
        List<Seat> seats = new ArrayList<>();
        int count = 0;
        outer:
        for (int r = 0; r < ROWS.length(); r++) {
            for (int s = 1; s <= SEATS_PER_ROW; s++) {
                if (count >= totalTickets) break outer;
                seats.add(new Seat(eventId, ROWS.charAt(r) + String.valueOf(s)));
                count++;
            }
        }
        return seats;
    }

    public List<Seat> getSeatsByEvent(Long eventId) {
        return seatRepo.findByEventIdOrderBySeatNumberAsc(eventId);
    }

    /**
     * Atomically books the requested seats using a two-layer concurrency guard:
     *
     * Layer 1 — Pessimistic lock (SELECT ... FOR UPDATE)
     *   findByEventIdAndSeatNumberInAndStatus acquires exclusive row locks on the
     *   requested seats. A concurrent booking attempt for any of the same seats
     *   will block at this SELECT until this transaction commits or rolls back.
     *   After this transaction commits the concurrent thread re-reads 0 available
     *   rows and throws correctly — no double booking.
     *
     * Layer 2 — Conditional UPDATE (AND s.status = 'available')
     *   markSeatsBooked only updates rows that are still 'available'. The returned
     *   count tells us exactly how many rows were actually changed. If it is less
     *   than requested a seat was snatched between layers (extremely unlikely but
     *   handled). The transaction rolls back and the user sees a clear error.
     *
     * Must be called inside an existing transaction (BookingService is @Transactional).
     */
    @Transactional
    public void bookSeats(Long eventId, List<String> seatNumbers) {
        // ── Layer 1: SELECT FOR UPDATE ────────────────────────────────────────
        List<Seat> available = seatRepo.findByEventIdAndSeatNumberInAndStatus(
                eventId, seatNumbers, "available");

        if (available.size() != seatNumbers.size()) {
            log.warn("Seat pre-check failed: eventId={} requested={} available={}",
                     eventId, seatNumbers.size(), available.size());
            throw new RuntimeException(
                "One or more selected seats are no longer available. Please select different seats.");
        }

        // ── Layer 2: Conditional UPDATE — verifies actual row count changed ───
        int updated = seatRepo.markSeatsBooked(eventId, seatNumbers);

        if (updated != seatNumbers.size()) {
            log.warn("Seat conditional UPDATE mismatch: eventId={} requested={} updated={}",
                     eventId, seatNumbers.size(), updated);
            throw new RuntimeException(
                "One or more seats were just taken. Please select different seats.");
        }

        log.debug("Seats booked: eventId={} seats={}", eventId, seatNumbers);
    }

    /** Releases seats back to available — called on cancellation. */
    @Transactional
    public void releaseSeats(Long eventId, List<String> seatNumbers) {
        if (seatNumbers == null || seatNumbers.isEmpty()) return;
        seatRepo.markSeatsAvailable(eventId, seatNumbers);
        log.debug("Seats released: eventId={} seats={}", eventId, seatNumbers);
    }
}
