package com.ticketapp.controller;

import com.ticketapp.entity.OrganizerPayout;
import com.ticketapp.repository.OrganizerProfileRepository;
import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.service.OrganizerService;
import com.ticketapp.service.PayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * PayoutController — organizer payout / settlement (Feature 14).
 *
 * Organizer routes:
 *   GET  /payouts/organizer      — list own payout history
 *   POST /payouts/request        — request a new payout
 *
 * Admin routes:
 *   GET  /payouts/admin          — list all payouts (all organizers)
 *   PUT  /payouts/{id}/process   — mark as paid (supply Razorpay payout ID)
 *   PUT  /payouts/{id}/reject    — reject request with note
 */
@RestController
@RequestMapping("/payouts")
@RequiredArgsConstructor
@Slf4j
public class PayoutController {

    private final PayoutService              payoutService;
    private final OrganizerService           organizerService;
    private final OrganizerProfileRepository profileRepo;

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

    // ── Organizer: list own payouts ───────────────────────────────────────────

    @GetMapping("/organizer")
    public ResponseEntity<?> getMyPayouts(@AuthenticationPrincipal AuthenticatedUser user) {
        if (!isApprovedOrganizer(user))
            return ResponseEntity.status(403).body(Map.of("error",
                "Organizer account not approved or insufficient role."));
        List<OrganizerPayout> payouts = payoutService.getOrganizerPayouts(user.getId());
        return ResponseEntity.ok(payouts);
    }

    // ── Organizer: request payout ─────────────────────────────────────────────

    @PostMapping("/request")
    public ResponseEntity<?> requestPayout(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!isApprovedOrganizer(user))
            return ResponseEntity.status(403).body(Map.of("error",
                "Organizer account not approved or insufficient role."));

        String fromStr = body.get("from_date");
        String toStr   = body.get("to_date");
        if (fromStr == null || toStr == null)
            return ResponseEntity.badRequest().body(Map.of("error", "from_date and to_date are required."));

        try {
            LocalDate from = LocalDate.parse(fromStr);
            LocalDate to   = LocalDate.parse(toStr);
            OrganizerPayout payout = payoutService.requestPayout(user.getId(), from, to);
            log.info("Payout requested: organizerId={} payoutId={}", user.getId(), payout.getId());
            return ResponseEntity.status(201).body(payout);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Admin: list all payouts ───────────────────────────────────────────────

    @GetMapping("/admin")
    public ResponseEntity<?> getAllPayouts(
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!isAdmin(user))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required."));
        return ResponseEntity.ok(payoutService.getAllPayoutsForAdmin());
    }

    // ── Admin: process (mark as paid) ─────────────────────────────────────────

    @PutMapping("/{id}/process")
    public ResponseEntity<?> processPayout(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!isAdmin(user))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required."));
        try {
            String razorpayPayoutId = body != null ? body.get("razorpay_payout_id") : null;
            String adminNote        = body != null ? body.get("admin_note")        : null;
            OrganizerPayout payout  = payoutService.processPayout(id, razorpayPayoutId, adminNote);
            log.info("Admin processed payout id={}", id);
            return ResponseEntity.ok(payout);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Admin: reject a payout request ───────────────────────────────────────

    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectPayout(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!isAdmin(user))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required."));
        try {
            String adminNote       = body != null ? body.get("admin_note") : null;
            OrganizerPayout payout = payoutService.rejectPayout(id, adminNote);
            log.info("Admin rejected payout id={}", id);
            return ResponseEntity.ok(payout);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
