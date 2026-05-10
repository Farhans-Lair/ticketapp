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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * OrganizerController — all role checks done manually.
 * See SecurityConfig for why @PreAuthorize is avoided here.
 */
@RestController
@RequestMapping("/organizer")
@RequiredArgsConstructor
@Slf4j
public class OrganizerController {

    private final OrganizerService organizerService;
    private final EventService     eventService;
    private final ObjectMapper     objectMapper;

    private static final Map<String, Object> FORBIDDEN_ORGANIZER =
        Map.of("error", "Organizer account not approved or insufficient role.");
    private static final Map<String, Object> FORBIDDEN_ADMIN =
        Map.of("error", "Admin access required.");

    private boolean isApprovedOrganizer(AuthenticatedUser user) {
        if (user == null) return false;
        if ("admin".equals(user.getRole())) return true;
        if (!"organizer".equals(user.getRole())) return false;
        return organizerService.getProfile(user.getId())
                .map(p -> "approved".equals(p.getStatus()))
                .orElse(false);
    }

    private boolean isAdmin(AuthenticatedUser user) {
        return user != null && "admin".equals(user.getRole());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ORGANIZER — PROFILE
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal AuthenticatedUser user) {
        if (!isApprovedOrganizer(user))
            return ResponseEntity.status(403).body(FORBIDDEN_ORGANIZER);
        OrganizerProfile profile = organizerService.getProfile(user.getId()).orElse(null);
        if (profile == null)
            return ResponseEntity.status(404).body(Map.of("error", "Profile not found."));
        return ResponseEntity.ok(organizerService.safeProfileMapWithUser(profile));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @RequestBody OrganizerProfileDto body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!isApprovedOrganizer(user))
            return ResponseEntity.status(403).body(FORBIDDEN_ORGANIZER);
        OrganizerProfile profile = organizerService.updateProfile(
            user.getId(), body.getBusiness_name(), body.getContact_phone(),
            body.getGst_number(), body.getAddress(),
            body.getBank_account_number(), body.getBank_ifsc(),
            body.getUpi_id(), body.getPayout_method());
        log.info("Organizer profile updated: userId={}", user.getId());
        return ResponseEntity.ok(organizerService.safeProfileMapWithUser(profile));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@AuthenticationPrincipal AuthenticatedUser user) {
        if (!isApprovedOrganizer(user))
            return ResponseEntity.status(403).body(FORBIDDEN_ORGANIZER);
        return ResponseEntity.ok(organizerService.getOrganizerStats(user.getId()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ORGANIZER — EVENTS
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/events")
    public ResponseEntity<?> getMyEvents(@AuthenticationPrincipal AuthenticatedUser user) {
        if (!isApprovedOrganizer(user))
            return ResponseEntity.status(403).body(FORBIDDEN_ORGANIZER);
        List<Event> events = organizerService.getOrganizerEvents(user.getId());
        log.info("Organizer events fetched: organizerId={} count={}", user.getId(), events.size());
        return ResponseEntity.ok(events);
    }

    /**
     * Creates a new event as a DRAFT.
     * The organizer must explicitly call POST /organizer/events/{id}/submit
     * to move it to 'pending_review' for admin approval.
     */
    @PostMapping("/events")
    public ResponseEntity<?> createEvent(
            @RequestBody EventDto body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!isApprovedOrganizer(user))
            return ResponseEntity.status(403).body(FORBIDDEN_ORGANIZER);
        if (body.getTitle() == null || body.getEvent_date() == null || body.getTotal_tickets() == null)
            return ResponseEntity.badRequest().body(Map.of("error",
                "Title, event date and total tickets are required."));
        if (body.getTotal_tickets() <= 0)
            return ResponseEntity.badRequest().body(Map.of("error",
                "Total tickets must be greater than zero."));
        validateFutureDate(body.getEvent_date());
        String imagesJson = serializeImages(body.getImages());

        // organizerId != null → EventService sets status to 'draft'
        Event event = eventService.createEvent(
            body.getTitle(), body.getDescription(), body.getLocation(),
            body.getCity(),
            body.getEvent_date(), body.getPrice(), body.getTotal_tickets(),
            body.getCategory(), imagesJson, user.getId());
        log.info("Organizer created draft event: organizerId={} eventId={}", user.getId(), event.getId());
        return ResponseEntity.status(201).body(event);
    }

    /**
     * Feature 13: Organizer submits a draft or rejected event for admin review.
     * Transitions: draft → pending_review, rejected → pending_review.
     */
    @PostMapping("/events/{id}/submit")
    public ResponseEntity<?> submitEventForReview(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!isApprovedOrganizer(user))
            return ResponseEntity.status(403).body(FORBIDDEN_ORGANIZER);
        try {
            Event event = eventService.submitForReview(id, user.getId());
            log.info("Organizer submitted event for review: organizerId={} eventId={}", user.getId(), id);
            return ResponseEntity.ok(Map.of("message", "Event submitted for admin review.", "event", event));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/events/{id}")
    public ResponseEntity<?> updateEvent(
            @PathVariable Long id,
            @RequestBody EventDto body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!isApprovedOrganizer(user))
            return ResponseEntity.status(403).body(FORBIDDEN_ORGANIZER);
        String imagesJson = serializeImages(body.getImages());
        Event updated = eventService.updateEvent(
            id, body.getTitle(), body.getDescription(), body.getLocation(),
            body.getCity(),
            body.getEvent_date(), body.getPrice(), body.getTotal_tickets(),
            body.getCategory(), imagesJson, user.getId());
        if (updated == null)
            return ResponseEntity.status(404).body(Map.of("error",
                "Event not found or you do not own this event."));
        log.info("Organizer updated event: organizerId={} eventId={}", user.getId(), id);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/events/{id}")
    public ResponseEntity<?> deleteEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!isApprovedOrganizer(user))
            return ResponseEntity.status(403).body(FORBIDDEN_ORGANIZER);
        boolean deleted = eventService.deleteEvent(id, user.getId());
        if (!deleted)
            return ResponseEntity.status(404).body(Map.of("error",
                "Event not found or you do not own this event."));
        log.info("Organizer deleted event: organizerId={} eventId={}", user.getId(), id);
        return ResponseEntity.ok(Map.of("message", "Event deleted successfully."));
    }

    @GetMapping("/events/{id}/attendees")
    public ResponseEntity<?> getEventAttendees(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!isApprovedOrganizer(user))
            return ResponseEntity.status(403).body(FORBIDDEN_ORGANIZER);
        Map<String, Object> result = organizerService.getEventAttendees(id, user.getId());
        if (result == null)
            return ResponseEntity.status(404).body(Map.of("error",
                "Event not found or you do not own this event."));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/revenue")
    public ResponseEntity<?> getRevenue(@AuthenticationPrincipal AuthenticatedUser user) {
        if (!isApprovedOrganizer(user))
            return ResponseEntity.status(403).body(FORBIDDEN_ORGANIZER);
        return ResponseEntity.ok(organizerService.getOrganizerRevenue(user.getId()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ADMIN — ORGANIZER APPLICATION MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/admin/organizers")
    public ResponseEntity<?> listOrganizers(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!isAdmin(user))
            return ResponseEntity.status(403).body(FORBIDDEN_ADMIN);
        log.info("Admin fetching organizers: adminId={} status={}", user.getId(), status);
        return ResponseEntity.ok(organizerService.getAllOrganizers(status));
    }

    @PutMapping("/admin/organizers/{id}/approve")
    public ResponseEntity<?> approveOrganizer(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!isAdmin(user))
            return ResponseEntity.status(403).body(FORBIDDEN_ADMIN);
        OrganizerProfile profile = organizerService.approveOrganizer(id);
        if (profile == null)
            return ResponseEntity.status(404).body(Map.of("error", "Organizer profile not found."));
        log.info("Organizer approved: adminId={} profileId={}", user.getId(), id);
        return ResponseEntity.ok(Map.of(
            "message", "Organizer approved successfully.",
            "profile", organizerService.safeProfileMap(profile)));
    }

    @PutMapping("/admin/organizers/{id}/reject")
    public ResponseEntity<?> rejectOrganizer(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!isAdmin(user))
            return ResponseEntity.status(403).body(FORBIDDEN_ADMIN);
        String reason = body != null ? body.get("reason") : null;
        OrganizerProfile profile = organizerService.rejectOrganizer(id, reason);
        if (profile == null)
            return ResponseEntity.status(404).body(Map.of("error", "Organizer profile not found."));
        log.info("Organizer rejected: adminId={} profileId={}", user.getId(), id);
        return ResponseEntity.ok(Map.of(
            "message", "Organizer rejected.",
            "profile", organizerService.safeProfileMap(profile)));
    }

    @DeleteMapping("/admin/organizers/{id}")
    public ResponseEntity<?> deleteOrganizer(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!isAdmin(user))
            return ResponseEntity.status(403).body(FORBIDDEN_ADMIN);
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
