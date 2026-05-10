package com.ticketapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketapp.dto.EventDto;
import com.ticketapp.entity.Event;
import com.ticketapp.repository.EventRepository;
import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Slf4j
public class EventController {

    private final EventService   eventService;
    private final EventRepository eventRepo;
    private final ObjectMapper   objectMapper;

    // ── GET /events/admin/stats (admin only) ──────────────────────────────────
    @GetMapping("/admin/stats")
    public ResponseEntity<?> getAdminEventStats(
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null || !"admin".equals(user.getRole()))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required."));
        Map<String, Long> stats = new java.util.LinkedHashMap<>();
        stats.put("pending_review", eventRepo.countByEventStatus("pending_review"));
        stats.put("published",      eventRepo.countByEventStatus("published"));
        stats.put("draft",          eventRepo.countByEventStatus("draft"));
        stats.put("rejected",       eventRepo.countByEventStatus("rejected"));
        return ResponseEntity.ok(stats);
    }

    // ── GET /events?category=Music ────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<List<Event>> getEvents(
            @RequestParam(required = false) String category,
            @AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Fetching events, category={}", category != null ? category : "all");
        return ResponseEntity.ok(eventService.getAllEvents(category));
    }

    // ── GET /events/{id} ──────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<?> getEventById(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return eventService.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(Map.of("error", "Event not found.")));
    }

    // ── Feature 11: GET /events/featured (public) ─────────────────────────────
    @GetMapping("/featured")
    public ResponseEntity<List<Event>> getFeaturedEvents() {
        return ResponseEntity.ok(eventService.getFeaturedEvents());
    }

    // ── Feature 11: GET /events/trending (public) ─────────────────────────────
    @GetMapping("/trending")
    public ResponseEntity<List<Event>> getTrendingEvents() {
        return ResponseEntity.ok(eventService.getTrendingEvents());
    }

    // ── Feature 13: GET /events/pending (admin only) ──────────────────────────
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingEvents(
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null || !"admin".equals(user.getRole()))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required."));
        List<Event> pending = eventService.getPendingEvents();
        log.info("Admin fetching pending events: count={}", pending.size());
        return ResponseEntity.ok(pending);
    }

    // ── Feature 11: PUT /events/{id}/feature (admin only) ────────────────────
    @PutMapping("/{id}/feature")
    public ResponseEntity<?> featureEvent(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null || !"admin".equals(user.getRole()))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required."));
        try {
            LocalDateTime until = null;
            if (body != null && body.get("featured_until") != null) {
                String s = body.get("featured_until");
                until = LocalDateTime.parse(s.length() == 16 ? s + ":00" : s,
                        java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            Event event = eventService.featureEvent(id, until);
            log.info("Admin featured event id={}", id);
            return ResponseEntity.ok(event);
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Feature 11: PUT /events/{id}/unfeature (admin only) ──────────────────
    @PutMapping("/{id}/unfeature")
    public ResponseEntity<?> unfeatureEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null || !"admin".equals(user.getRole()))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required."));
        try {
            Event event = eventService.unfeatureEvent(id);
            log.info("Admin unfeatured event id={}", id);
            return ResponseEntity.ok(event);
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Feature 13: PUT /events/{id}/approve (admin only) ────────────────────
    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approveEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null || !"admin".equals(user.getRole()))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required."));
        try {
            Event event = eventService.approveEvent(id);
            log.info("Admin approved event id={}", id);
            return ResponseEntity.ok(Map.of("message", "Event approved and published.", "event", event));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Feature 13: PUT /events/{id}/reject (admin only) ─────────────────────
    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectEvent(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null || !"admin".equals(user.getRole()))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required."));
        String reason = body != null ? body.get("reason") : null;
        try {
            Event event = eventService.rejectEvent(id, reason);
            log.info("Admin rejected event id={}", id);
            return ResponseEntity.ok(Map.of("message", "Event rejected.", "event", event));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    // ── POST /events (admin only) ─────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<?> createEvent(
            @Valid @RequestBody EventDto body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null || !"admin".equals(user.getRole()))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required."));
        validateEventDate(body.getEvent_date());
        String imagesJson = serializeImages(body.getImages());

        // Admin events bypass moderation (organizerId = null → published)
        Event event = eventService.createEvent(
            body.getTitle(), body.getDescription(), body.getLocation(),
            body.getCity(),
            body.getEvent_date(), body.getPrice(), body.getTotal_tickets(),
            body.getCategory(), imagesJson, null);

        log.info("Admin created event id={} title={}", event.getId(), event.getTitle());
        return ResponseEntity.status(201).body(event);
    }

    // ── PUT /events/:id (admin only) ──────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<?> updateEvent(
            @PathVariable Long id,
            @RequestBody EventDto body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null || !"admin".equals(user.getRole()))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required."));
        String imagesJson = serializeImages(body.getImages());
        Event updated = eventService.updateEvent(
            id, body.getTitle(), body.getDescription(), body.getLocation(),
            body.getCity(),
            body.getEvent_date(), body.getPrice(), body.getTotal_tickets(),
            body.getCategory(), imagesJson, null);

        if (updated == null) return ResponseEntity.status(404).body(Map.of("error", "Event not found."));
        log.info("Admin updated event id={}", id);
        return ResponseEntity.ok(updated);
    }

    // ── DELETE /events/:id (admin only) ───────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null || !"admin".equals(user.getRole()))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required."));
        boolean deleted = eventService.deleteEvent(id, null);
        if (!deleted) return ResponseEntity.status(404).body(Map.of("error", "Event not found."));
        log.info("Admin deleted event id={}", id);
        return ResponseEntity.ok(Map.of("message", "Event deleted successfully."));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateEventDate(String eventDateStr) {
        if (eventDateStr == null) return;
        try {
            LocalDateTime date = LocalDateTime.parse(
                eventDateStr.length() == 16 ? eventDateStr + ":00" : eventDateStr,
                java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            if (date.toLocalDate().isBefore(LocalDate.now()))
                throw new RuntimeException("Event date must be today or a future date.");
        } catch (java.time.format.DateTimeParseException e) {
            throw new RuntimeException("Invalid event date format.");
        }
    }

    private String serializeImages(List<String> images) {
        if (images == null || images.isEmpty()) return null;
        try { return objectMapper.writeValueAsString(images); }
        catch (Exception e) { return null; }
    }
}
