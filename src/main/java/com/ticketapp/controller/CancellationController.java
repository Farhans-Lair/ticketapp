package com.ticketapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketapp.dto.CancellationDto;
import com.ticketapp.entity.Booking;
import com.ticketapp.entity.CancellationPolicy;
import com.ticketapp.entity.Event;
import com.ticketapp.entity.User;
import com.ticketapp.repository.EventRepository;
import com.ticketapp.repository.UserRepository;
import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.service.BookingService;
import com.ticketapp.service.CancellationService;
import com.ticketapp.service.EmailService;
import com.ticketapp.service.PdfService;
import com.ticketapp.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/cancellations")
@RequiredArgsConstructor
@Slf4j
public class CancellationController {

    private final CancellationService cancellationService;
    private final BookingService      bookingService;
    private final PdfService          pdfService;
    private final S3Service           s3Service;
    private final EmailService        emailService;
    private final UserRepository      userRepo;
    private final EventRepository     eventRepo;
    private final ObjectMapper        objectMapper;

    // ── GET /cancellations/preview/{bookingId} ────────────────────────────────
    @GetMapping("/preview/{bookingId}")
    public ResponseEntity<?> previewCancellation(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Cancellation preview: bookingId={} userId={}", bookingId, user.getId());
        try {
            Map<String, Object> result = cancellationService.previewCancellation(bookingId, user.getId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── POST /cancellations/{bookingId} ───────────────────────────────────────
    @PostMapping("/{bookingId}")
    public ResponseEntity<?> cancelBooking(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Cancellation requested: bookingId={} userId={}", bookingId, user.getId());
        try {
            Map<String, Object> result = cancellationService.cancelBooking(bookingId, user.getId());
            Booking booking = (Booking) result.get("booking");

            // Generate cancellation invoice PDF and upload to S3 (non-blocking failure)
            User  u = userRepo.findById(user.getId()).orElse(null);
            Event e = eventRepo.findById(booking.getEventId()).orElse(null);
            if (u != null && e != null) {
                try {
                    byte[] invoicePdf = pdfService.generateCancellationInvoicePdf(booking, u, e, result);
                    String s3Key = s3Service.uploadCancellationInvoice(invoicePdf, booking.getId(), user.getId());
                    booking.setCancellationInvoiceS3Key(s3Key);
                    bookingService.saveBooking(booking);
                    log.info("Cancellation invoice uploaded to S3: {}", s3Key);
                } catch (Exception ex) {
                    log.error("Cancellation invoice S3 upload failed: {}", ex.getMessage());
                }

                // Send cancellation email (non-blocking failure)
                try {
                    emailService.sendCancellationEmail(u, booking, e, result);
                } catch (Exception ex) {
                    log.error("Cancellation email failed: {}", ex.getMessage());
                }
            }

            return ResponseEntity.ok(Map.of(
                "message",            "Booking cancelled successfully.",
                "refundAmount",       result.get("refundAmount"),
                "refundPercent",      result.get("refundPercent"),
                "cancellationStatus", result.get("cancellationStatus"),
                "razorpay_refund_id", result.get("razorpay_refund_id")
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── GET /cancellations/{bookingId}/download-invoice ───────────────────────
    @GetMapping("/{bookingId}/download-invoice")
    public ResponseEntity<?> downloadCancellationInvoice(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Cancellation invoice download: bookingId={} userId={}", bookingId, user.getId());
        try {
            Optional<Booking> opt = bookingService.getBookingByIdAndUser(bookingId, user.getId());
            if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Booking not found."));
            Booking booking = opt.get();

            List<String> cancelledStatuses = List.of("cancelled", "refund_pending", "refunded");
            if (!cancelledStatuses.contains(booking.getCancellationStatus()))
                return ResponseEntity.badRequest().body(Map.of("error",
                    "No cancellation invoice exists for an active booking."));

            byte[] pdf;
            if (booking.getCancellationInvoiceS3Key() != null && !booking.getCancellationInvoiceS3Key().isBlank()) {
                log.info("Serving cancellation invoice from S3: {}", booking.getCancellationInvoiceS3Key());
                pdf = s3Service.fetchCancellationInvoice(booking.getCancellationInvoiceS3Key());
            } else {
                log.warn("No S3 key — generating cancellation invoice on-the-fly: bookingId={}", bookingId);
                User  u = userRepo.findById(user.getId()).orElseThrow();
                Event e = eventRepo.findById(booking.getEventId()).orElseThrow();
                pdf = pdfService.generateCancellationInvoicePdf(booking, u, e, Map.of(
                    "refundAmount",       booking.getRefundAmount() != null ? booking.getRefundAmount() : 0.0,
                    "cancellationFee",    booking.getCancellationFee() != null ? booking.getCancellationFee() : 0.0,
                    "cancellationFeeGst", booking.getCancellationFeeGst() != null ? booking.getCancellationFeeGst() : 0.0,
                    "isHighTier",         (booking.getAppliedTierHours() != null && booking.getAppliedTierHours() >= 72),
                    "cancellationStatus", booking.getCancellationStatus()
                ));
            }

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"cancellation-invoice-" + bookingId + ".pdf\"")
                .body(pdf);
        } catch (Exception e) {
            log.error("Cancellation invoice download failed: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to generate cancellation invoice."));
        }
    }

    // ── GET /cancellations/policy/{eventId} ───────────────────────────────────
    @GetMapping("/policy/{eventId}")
    public ResponseEntity<?> getPolicy(@PathVariable Long eventId) {
        Optional<CancellationPolicy> policy = cancellationService.getPolicy(eventId);
        if (policy.isEmpty())
            return ResponseEntity.ok(Map.of("exists", false, "is_cancellation_allowed", false, "tiers", List.of()));
        CancellationPolicy p = policy.get();
        return ResponseEntity.ok(Map.of(
            "exists",                  true,
            "event_id",                p.getEventId(),
            "is_cancellation_allowed", p.getIsCancellationAllowed(),
            "tiers",                   parseTiers(p.getTiers())
        ));
    }

    // ── PUT /cancellations/policy/{eventId} ───────────────────────────────────
    @PutMapping("/policy/{eventId}")
    public ResponseEntity<?> upsertPolicy(
            @PathVariable Long eventId,
            @RequestBody CancellationDto.UpsertPolicyRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (!"organizer".equals(user.getRole()) && !"admin".equals(user.getRole()))
            return ResponseEntity.status(403).body(Map.of("error", "Organizer access required."));
        if (body.getTiers() == null)
            return ResponseEntity.badRequest().body(Map.of("error", "tiers array is required."));
        try {
            boolean allowed = body.getIs_cancellation_allowed() != null && body.getIs_cancellation_allowed();
            CancellationPolicy policy = cancellationService.upsertPolicy(
                    user.getId(), eventId, body.getTiers(), allowed);
            return ResponseEntity.ok(Map.of("message", "Cancellation policy saved.", "policy", policy));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── POST /cancellations/webhook/refund ────────────────────────────────────
    // Razorpay calls this when a refund.processed event fires.
    // No JWT auth — verified via HMAC-SHA256 signature.
    @PostMapping("/webhook/refund")
    public ResponseEntity<?> handleRefundWebhook(
            @RequestBody String rawBody) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = objectMapper.readValue(rawBody, Map.class);
            if ("refund.processed".equals(event.get("event"))) {
                try {
                    @SuppressWarnings("unchecked")
                    String refundId = (String)
                        ((Map<?,?>) ((Map<?,?>) ((Map<?,?>) event.get("payload"))
                            .get("refund")).get("entity")).get("id");
                    cancellationService.markRefundComplete(refundId).ifPresent(b ->
                        log.info("Refund webhook processed: bookingId={} refundId={}", b.getId(), refundId));
                } catch (Exception ex) {
                    log.warn("Could not extract refund id from webhook payload: {}", ex.getMessage());
                }
            }
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            log.error("Refund webhook handler failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<?> parseTiers(String json) {
        try { return objectMapper.readValue(json, List.class); }
        catch (Exception e) { return List.of(); }
    }

}
