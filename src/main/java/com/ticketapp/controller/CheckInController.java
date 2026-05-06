package com.ticketapp.controller;

import com.ticketapp.entity.Booking;
import com.ticketapp.repository.BookingRepository;
import com.ticketapp.service.QrService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * CheckInController — Feature 8: QR-code tickets + organizer check-in.
 *
 * POST /organizer/checkin
 *   body: { "token": "<JWT from QR code>" }
 *
 * Flow:
 *   1. Decode + verify JWT signature (QrService.verifyToken)
 *   2. Load booking by ID from JWT subject
 *   3. Validate booking is paid, active, and not already checked in
 *   4. Mark booking.checkedIn = true, stamp checkedInAt
 *   5. Return summary so the organizer's scanner app can show a green ✔
 *
 * Auth: In a real deployment, restrict this endpoint to ROLE_ORGANIZER or
 * add a short-lived organizer session token. The JWT in the QR body is
 * signed with the server's secret so it cannot be forged, but the check-in
 * endpoint itself should still require a valid organizer login to prevent
 * arbitrary third-party access.
 */
@RestController
@RequestMapping("/organizer")
@RequiredArgsConstructor
@Slf4j
public class CheckInController {

    private final QrService         qrService;
    private final BookingRepository bookingRepo;

    @PostMapping("/checkin")
    public ResponseEntity<Map<String, Object>> checkIn(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank())
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "reason", "Token is required."));

        // ── Step 1: Verify JWT signature ──────────────────────────────────────
        Claims claims;
        try {
            claims = qrService.verifyToken(token);
        } catch (JwtException e) {
            log.warn("Check-in failed — invalid token: {}", e.getMessage());
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "reason", "Invalid or expired QR code."));
        }

        Long bookingId = qrService.extractBookingId(claims);
        Long userId    = claims.get("userId",  Long.class);
        Long eventId   = claims.get("eventId", Long.class);

        // ── Step 2: Load booking ──────────────────────────────────────────────
        Optional<Booking> opt = bookingRepo.findById(bookingId);
        if (opt.isEmpty()) {
            log.warn("Check-in failed — booking not found: bookingId={}", bookingId);
            return ResponseEntity.status(404)
                    .body(Map.of("success", false, "reason", "Booking not found."));
        }

        Booking booking = opt.get();

        // ── Step 3: Validate state ────────────────────────────────────────────
        if (!"paid".equals(booking.getPaymentStatus())) {
            return ResponseEntity.status(422)
                    .body(Map.of("success", false, "reason", "Booking is not paid."));
        }
        if (!"active".equals(booking.getCancellationStatus())) {
            return ResponseEntity.status(422)
                    .body(Map.of("success", false,
                            "reason", "Booking is " + booking.getCancellationStatus() + "."));
        }
        if (Boolean.TRUE.equals(booking.getCheckedIn())) {
            log.warn("Check-in attempted on already-checked-in booking: bookingId={}", bookingId);
            return ResponseEntity.status(409).body(Map.of(
                "success",        false,
                "reason",         "Ticket already scanned.",
                "checked_in_at",  booking.getCheckedInAt()
            ));
        }

        // ── Step 4: Mark as checked in ────────────────────────────────────────
        booking.setCheckedIn(true);
        booking.setCheckedInAt(LocalDateTime.now());
        bookingRepo.save(booking);

        log.info("Check-in successful: bookingId={} userId={} eventId={}", bookingId, userId, eventId);

        // ── Step 5: Return confirmation ───────────────────────────────────────
        return ResponseEntity.ok(Map.of(
            "success",        true,
            "booking_id",     bookingId,
            "user_id",        userId,
            "event_id",       eventId,
            "tickets_booked", booking.getTicketsBooked(),
            "selected_seats", booking.getSelectedSeats() != null ? booking.getSelectedSeats() : "[]",
            "checked_in_at",  booking.getCheckedInAt()
        ));
    }
}
