package com.ticketapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketapp.dto.EventDto;
import com.ticketapp.dto.OrganizerProfileDto;
import com.ticketapp.entity.Event;
import com.ticketapp.entity.OrganizerProfile;
import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.service.EventService;
import com.ticketapp.service.OrganizerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/organizer")
@RequiredArgsConstructor
@Slf4j
public class OrganizerController {

    private final OrganizerService organizerService;
    private final EventService     eventService;
    private final ObjectMapper     objectMapper;

    // ═══════════════════════════════════════════════════════════════════════════
    // ORGANIZER — PROFILE
    // ═══════════════════════════════════════════════════════════════════════════

    // ── GET /organizer/profile ────────────────────────────────────────────────
    @GetMapping("/profile")
    @PreAuthorize("@roleCheck.isOrganizer(authentication)")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal AuthenticatedUser user) {
        OrganizerProfile profile = organizerService.getProfile(user.getId())
                .orElse(null);
        if (profile == null)
            return ResponseEntity.status(404).body(Map.of("error", "Profile not found."));
        return ResponseEntity.ok(profile);
    }

    // ── PUT /organizer/profile ────────────────────────────────────────────────
    @PutMapping("/profile")
    @PreAuthorize("@roleCheck.isOrganizer(authentication)")
    public ResponseEntity<?> updateProfile(
            @RequestBody OrganizerProfileDto body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        OrganizerProfile profile = organizerService.updateProfile(
            user.getId(), body.getBusiness_name(), body.getContact_phone(),
            body.getGst_number(), body.getAddress());
        log.info("Organizer profile updated: userId={}", user.getId());
        return ResponseEntity.ok(profile);
    }

    // ── GET /organizer/stats ──────────────────────────────────────────────────
    @GetMapping("/stats")
    @PreAuthorize("@roleCheck.isOrganizer(authentication)")
    public ResponseEntity<?> getStats(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(organizerService.getOrganizerStats(user.getId()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ORGANIZER — EVENTS
    // ═══════════════════════════════════════════════════════════════════════════

    // ── GET /organizer/events ─────────────────────────────────────────────────
    @GetMapping("/events")
    @PreAuthorize("@roleCheck.isOrganizer(authentication)")
    public ResponseEntity<?> getMyEvents(@AuthenticationPrincipal AuthenticatedUser user) {
        List<Event> events = organizerService.getOrganizerEvents(user.getId());
        log.info("Organizer events fetched: organizerId={} count={}", user.getId(), events.size());
        return ResponseEntity.ok(events);
    }

    // ── POST /organizer/events ────────────────────────────────────────────────
    @PostMapping("/events")
    @PreAuthorize("@roleCheck.isOrganizer(authentication)")
    public ResponseEntity<?> createEvent(
            @RequestBody EventDto body,
            @AuthenticationPrincipal AuthenticatedUser user) {

        if (body.getTitle() == null || body.getEvent_date() == null || body.getTotal_tickets() == null)
            return ResponseEntity.badRequest().body(Map.of("error",
                "Title, event date and total tickets are required."));

        if (body.getTotal_tickets() <= 0)
            return ResponseEntity.badRequest().body(Map.of("error",
                "Total tickets must be greater than zero."));

        validateFutureDate(body.getEvent_date());
        String imagesJson = serializeImages(body.getImages());

        Event event = eventService.createEvent(
            body.getTitle(), body.getDescription(), body.getLocation(),
            body.getEvent_date(), body.getPrice(), body.getTotal_tickets(),
            body.getCategory(), imagesJson,
            user.getId());   // ← organizer_id = this organizer

        log.info("Organizer created event: organizerId={} eventId={}", user.getId(), event.getId());
        return ResponseEntity.status(201).body(event);
    }

    // ── PUT /organizer/events/:id ─────────────────────────────────────────────
    @PutMapping("/events/{id}")
    @PreAuthorize("@roleCheck.isOrganizer(authentication)")
    public ResponseEntity<?> updateEvent(
            @PathVariable Long id,
            @RequestBody EventDto body,
            @AuthenticationPrincipal AuthenticatedUser user) {

        String imagesJson = serializeImages(body.getImages());
        Event updated = eventService.updateEvent(
            id, body.getTitle(), body.getDescription(), body.getLocation(),
            body.getEvent_date(), body.getPrice(), body.getTotal_tickets(),
            body.getCategory(), imagesJson,
            user.getId());   // ← organizer-scoped ownership check

        if (updated == null)
            return ResponseEntity.status(404).body(Map.of("error",
                "Event not found or you do not own this event."));

        log.info("Organizer updated event: organizerId={} eventId={}", user.getId(), id);
        return ResponseEntity.ok(updated);
    }

    // ── DELETE /organizer/events/:id ──────────────────────────────────────────
    @DeleteMapping("/events/{id}")
    @PreAuthorize("@roleCheck.isOrganizer(authentication)")
    public ResponseEntity<?> deleteEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {

        boolean deleted = eventService.deleteEvent(id, user.getId());
        if (!deleted)
            return ResponseEntity.status(404).body(Map.of("error",
                "Event not found or you do not own this event."));

        log.info("Organizer deleted event: organizerId={} eventId={}", user.getId(), id);
        return ResponseEntity.ok(Map.of("message", "Event deleted successfully."));
    }

    // ── GET /organizer/events/:id/attendees ───────────────────────────────────
    @GetMapping("/events/{id}/attendees")
    @PreAuthorize("@roleCheck.isOrganizer(authentication)")
    public ResponseEntity<?> getEventAttendees(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {

        Map<String, Object> result = organizerService.getEventAttendees(id, user.getId());
        if (result == null)
            return ResponseEntity.status(404).body(Map.of("error",
                "Event not found or you do not own this event."));

        return ResponseEntity.ok(result);
    }

    // ── GET /organizer/revenue ────────────────────────────────────────────────
    @GetMapping("/revenue")
    @PreAuthorize("@roleCheck.isOrganizer(authentication)")
    public ResponseEntity<?> getRevenue(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(organizerService.getOrganizerRevenue(user.getId()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ADMIN — ORGANIZER APPLICATION MANAGEMENT
    // Mounted at /organizer/admin/... (mirrors Express organizer.routes.js)
    // ═══════════════════════════════════════════════════════════════════════════

    // ── GET /organizer/admin/organizers?status=pending ────────────────────────
    @GetMapping("/admin/organizers")
    @PreAuthorize("@roleCheck.isAdmin(authentication)")
    public ResponseEntity<?> listOrganizers(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Admin fetching organizers: adminId={} status={}", user.getId(), status);
        return ResponseEntity.ok(organizerService.getAllOrganizers(status));
    }

    // ── PUT /organizer/admin/organizers/:id/approve ───────────────────────────
    @PutMapping("/admin/organizers/{id}/approve")
    @PreAuthorize("@roleCheck.isAdmin(authentication)")
    public ResponseEntity<?> approveOrganizer(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        OrganizerProfile profile = organizerService.approveOrganizer(id);
        if (profile == null)
            return ResponseEntity.status(404).body(Map.of("error", "Organizer profile not found."));
        log.info("Organizer approved: adminId={} profileId={}", user.getId(), id);
        return ResponseEntity.ok(Map.of("message", "Organizer approved successfully.", "profile", profile));
    }

    // ── PUT /organizer/admin/organizers/:id/reject ────────────────────────────
    @PutMapping("/admin/organizers/{id}/reject")
    @PreAuthorize("@roleCheck.isAdmin(authentication)")
    public ResponseEntity<?> rejectOrganizer(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        String reason = body != null ? body.get("reason") : null;
        OrganizerProfile profile = organizerService.rejectOrganizer(id, reason);
        if (profile == null)
            return ResponseEntity.status(404).body(Map.of("error", "Organizer profile not found."));
        log.info("Organizer rejected: adminId={} profileId={}", user.getId(), id);
        return ResponseEntity.ok(Map.of("message", "Organizer rejected.", "profile", profile));
    }

    // ── DELETE /organizer/admin/organizers/:id ────────────────────────────────
    @DeleteMapping("/admin/organizers/{id}")
    @PreAuthorize("@roleCheck.isAdmin(authentication)")
    public ResponseEntity<?> deleteOrganizer(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        boolean deleted = organizerService.deleteOrganizer(id);
        if (!deleted)
            return ResponseEntity.status(404).body(Map.of("error", "Organizer profile not found."));
        log.info("Organizer deleted: adminId={} profileId={}", user.getId(), id);
        return ResponseEntity.ok(Map.of("message", "Organizer account deleted successfully."));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateFutureDate(String dateStr) {
        try {
            LocalDateTime d = LocalDateTime.parse(
                dateStr.length() == 16 ? dateStr + ":00" : dateStr,
                java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            if (d.toLocalDate().isBefore(LocalDate.now()))
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
