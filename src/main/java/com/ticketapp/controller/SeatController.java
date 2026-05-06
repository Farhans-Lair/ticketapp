package com.ticketapp.controller;

import com.ticketapp.entity.Seat;
import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/seats")
@RequiredArgsConstructor
@Slf4j
public class SeatController {

    private final SeatService seatService;

    // ── GET /seats/{eventId} ──────────────────────────────────────────────────
    @GetMapping("/{eventId}")
    public ResponseEntity<List<Seat>> getSeats(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Fetching seats for eventId={} userId={}", eventId, user.getId());
        List<Seat> seats = seatService.getSeatsByEvent(eventId);
        log.info("Returned {} seats for eventId={}", seats.size(), eventId);
        return ResponseEntity.ok(seats);
    }

    // ── POST /seats/{eventId}/hold — Feature 4: seat hold timer ──────────────
    /**
     * Transitions the requested seats to 'held' status for 10 minutes.
     * Called by the frontend when the user lands on the payment page,
     * before creating the Razorpay order.
     *
     * Request body: { "seatNumbers": ["A1", "A2"] }
     *
     * The seats are automatically released by SeatHoldScheduler if the
     * user abandons checkout. On successful payment, BookingService calls
     * confirmHeldOrBook which upgrades held → booked atomically.
     */
    @PostMapping("/{eventId}/hold")
    public ResponseEntity<?> holdSeats(
            @PathVariable Long eventId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal AuthenticatedUser user) {

        @SuppressWarnings("unchecked")
        List<String> seatNumbers = (List<String>) body.get("seatNumbers");

        if (seatNumbers == null || seatNumbers.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "seatNumbers is required."));

        try {
            seatService.holdSeats(eventId, seatNumbers, user.getId());
            log.info("Seats held: eventId={} userId={} seats={}", eventId, user.getId(), seatNumbers);
            return ResponseEntity.ok(Map.of(
                "message",     "Seats held for 10 minutes.",
                "seatNumbers", seatNumbers,
                "heldForMins", 10
            ));
        } catch (RuntimeException e) {
            log.warn("Seat hold failed: eventId={} userId={} reason={}", eventId, user.getId(), e.getMessage());
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }
}

