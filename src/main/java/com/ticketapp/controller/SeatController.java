package com.ticketapp.controller;

import com.ticketapp.entity.Seat;
import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.service.SeatService;
import com.ticketapp.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/seats")
@RequiredArgsConstructor
@Slf4j
public class SeatController {

    private final SeatService    seatService;
    private final SeatRepository seatRepo;

    @GetMapping("/{eventId}")
    public ResponseEntity<List<Seat>> getSeats(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Fetching seats for eventId={} userId={}", eventId, user.getId());
        List<Seat> seats = seatService.getSeatsByEvent(eventId);
        return ResponseEntity.ok(seats);
    }

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
            return ResponseEntity.ok(Map.of("message", "Seats held for 10 minutes.", "seatNumbers", seatNumbers, "heldForMins", 10));
        } catch (RuntimeException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /seats/{eventId}/configure
     * Organizer configures tiered seat categories for an event.
     * Deletes existing seats and regenerates with the provided category/count/price config.
     *
     * Body: [ { "category": "Silver", "count": 60, "price": 100 }, ... ]
     */
    @PostMapping("/{eventId}/configure")
    @Transactional
    public ResponseEntity<?> configureSeats(
            @PathVariable Long eventId,
            @RequestBody List<Map<String, Object>> categoryConfig,
            @AuthenticationPrincipal AuthenticatedUser user) {

        if (categoryConfig == null || categoryConfig.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "categoryConfig is required."));

        for (Map<String, Object> cfg : categoryConfig) {
            if (cfg.get("category") == null || cfg.get("count") == null || cfg.get("price") == null)
                return ResponseEntity.badRequest().body(Map.of("error", "Each entry needs: category, count, price"));
            if (((Number) cfg.get("count")).intValue() < 1)
                return ResponseEntity.badRequest().body(Map.of("error", "Count must be >= 1"));
        }

        try {
            seatRepo.deleteByEventId(eventId);
            List<Seat> newSeats = seatService.generateSeatsWithCategories(eventId, categoryConfig);
            seatRepo.saveAll(newSeats);
            int total = newSeats.size();
            log.info("Seat tiers configured: eventId={} total={} userId={}", eventId, total, user.getId());
            return ResponseEntity.ok(Map.of(
                "message",    "Seat tiers saved successfully.",
                "totalSeats", total
            ));
        } catch (RuntimeException e) {
            log.error("Seat configure error: eventId={}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
