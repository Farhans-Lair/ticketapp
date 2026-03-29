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

@RestController
@RequestMapping("/seats")
@RequiredArgsConstructor
@Slf4j
public class SeatController {

    private final SeatService seatService;

    // ── GET /seats/:eventId ───────────────────────────────────────────────────
    @GetMapping("/{eventId}")
    public ResponseEntity<List<Seat>> getSeats(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Fetching seats for eventId={} userId={}", eventId, user.getId());
        List<Seat> seats = seatService.getSeatsByEvent(eventId);
        log.info("Returned {} seats for eventId={}", seats.size(), eventId);
        return ResponseEntity.ok(seats);
    }
}
