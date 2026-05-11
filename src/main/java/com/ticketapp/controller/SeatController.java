package com.ticketapp.controller;

import com.ticketapp.entity.Event;
import com.ticketapp.entity.Seat;
import com.ticketapp.repository.EventRepository;
import com.ticketapp.repository.SeatRepository;
import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/seats")
@RequiredArgsConstructor
@Slf4j
public class SeatController {

    private final SeatService    seatService;
    private final SeatRepository seatRepo;
    private final EventRepository eventRepo;

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

            // Update event.price to the minimum tier price so the listing shows the
            // correct "starts from" price and flat-price fallback works correctly.
            double minTierPrice = categoryConfig.stream()
                    .mapToDouble(cfg -> ((Number) cfg.get("price")).doubleValue())
                    .min()
                    .orElse(0.0);
            eventRepo.findById(eventId).ifPresent(ev -> {
                ev.setPrice(minTierPrice);
                eventRepo.save(ev);
            });

            // Build tier summary so frontend can reload the modal without a second call
            List<Map<String, Object>> tierSummary = new ArrayList<>();
            categoryConfig.forEach(cfg -> {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("category", cfg.get("category"));
                t.put("count",    cfg.get("count"));
                t.put("price",    cfg.get("price"));
                tierSummary.add(t);
            });

            log.info("Seat tiers configured: eventId={} total={} minPrice={} userId={}",
                    eventId, total, minTierPrice, user.getId());
            return ResponseEntity.ok(Map.of(
                "message",    "Seat tiers saved successfully.",
                "totalSeats", total,
                "minPrice",   minTierPrice,
                "tiers",      tierSummary
            ));
        } catch (RuntimeException e) {
            log.error("Seat configure error: eventId={}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
