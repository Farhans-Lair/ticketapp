package com.ticketapp.controller;

import com.ticketapp.entity.WaitlistEntry;
import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * WaitlistController — Feature 9: Waiting list for sold-out shows.
 *
 * POST   /waitlist/{eventId}    body: { "tickets_wanted": 2 }  → join waitlist
 * DELETE /waitlist/{eventId}                                    → leave waitlist
 * GET    /waitlist                                              → user's waitlist entries
 * GET    /waitlist/{eventId}/stats                              → queue depth (public)
 */
@RestController
@RequestMapping("/waitlist")
@RequiredArgsConstructor
@Slf4j
public class WaitlistController {

    private final WaitlistService waitlistService;

    // ── Join ──────────────────────────────────────────────────────────────────

    @PostMapping("/{eventId}")
    public ResponseEntity<?> join(
            @PathVariable Long eventId,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal AuthenticatedUser user) {

        int ticketsWanted = (body != null && body.containsKey("tickets_wanted"))
                ? ((Number) body.get("tickets_wanted")).intValue()
                : 1;

        try {
            WaitlistEntry entry = waitlistService.join(user.getId(), eventId, ticketsWanted);
            return ResponseEntity.ok(entry);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Leave ─────────────────────────────────────────────────────────────────

    @DeleteMapping("/{eventId}")
    public ResponseEntity<?> leave(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        waitlistService.leave(user.getId(), eventId);
        return ResponseEntity.ok(Map.of("message", "Removed from waitlist."));
    }

    // ── My waitlist ───────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<WaitlistEntry>> myWaitlist(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(waitlistService.getForUser(user.getId()));
    }

    // ── Queue stats (public) ──────────────────────────────────────────────────

    @GetMapping("/{eventId}/stats")
    public ResponseEntity<Map<String, Object>> stats(@PathVariable Long eventId) {
        return ResponseEntity.ok(waitlistService.getQueueStats(eventId));
    }
}
