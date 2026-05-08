package com.ticketapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketapp.dto.EventDto;
import com.ticketapp.entity.Event;
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
    private final ObjectMapper   objectMapper;

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

    // ── POST /events (admin only) ─────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<?> createEvent(
            @Valid @RequestBody EventDto body,
            @AuthenticationPrincipal AuthenticatedUser user) {

        if (user == null || !"admin".equals(user.getRole()))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required."));
        validateEventDate(body.getEvent_date());
        String imagesJson = serializeImages(body.getImages());

        Event event = eventService.createEvent(
            body.getTitle(), body.getDescription(), body.getLocation(),
            body.getCity(),
            body.getEvent_date(), body.getPrice(), body.getTotal_tickets(),
            body.getCategory(), imagesJson, null);   // null organizerId = admin event

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
            body.getCategory(), imagesJson, null);   // null = admin bypass

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
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            return null;
        }
    }
}
