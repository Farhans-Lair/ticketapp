package com.ticketapp.controller;

import com.ticketapp.entity.Booking;
import com.ticketapp.entity.Event;
import com.ticketapp.entity.User;
import com.ticketapp.repository.EventRepository;
import com.ticketapp.repository.UserRepository;
import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.service.BookingService;
import com.ticketapp.service.PdfService;
import com.ticketapp.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final BookingService  bookingService;
    private final PdfService      pdfService;
    private final S3Service       s3Service;
    private final UserRepository  userRepo;
    private final EventRepository eventRepo;

    // ── GET /bookings/my-bookings ─────────────────────────────────────────────
    @GetMapping("/my-bookings")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyBookings(
            @AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Fetching bookings for userId={}", user.getId());
        List<Booking> bookings = bookingService.getUserBookings(user.getId());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Booking b : bookings) {
            Map<String, Object> bMap = new LinkedHashMap<>();
            bMap.put("id",                b.getId());
            bMap.put("event_id",          b.getEventId());
            bMap.put("tickets_booked",    b.getTicketsBooked());
            bMap.put("ticket_amount",     b.getTicketAmount());
            bMap.put("convenience_fee",   b.getConvenienceFee());
            bMap.put("gst_amount",        b.getGstAmount());
            bMap.put("total_paid",        b.getTotalPaid());
            bMap.put("selected_seats",    b.getSelectedSeats());
            bMap.put("payment_status",    b.getPaymentStatus());
            bMap.put("booking_date",      b.getBookingDate());
            bMap.put("ticket_pdf_s3_key", b.getTicketPdfS3Key());
            bMap.put("booking_invoice_s3_key",      b.getBookingInvoiceS3Key());
            bMap.put("cancellation_status",         b.getCancellationStatus() != null ? b.getCancellationStatus() : "active");
            bMap.put("refund_amount",               b.getRefundAmount());
            bMap.put("razorpay_refund_id",          b.getRazorpayRefundId());
            bMap.put("cancelled_at",                b.getCancelledAt());
            bMap.put("cancellation_fee",            b.getCancellationFee());
            bMap.put("cancellation_fee_gst",        b.getCancellationFeeGst());
            bMap.put("applied_tier_hours",          b.getAppliedTierHours());
            bMap.put("cancellation_invoice_s3_key", b.getCancellationInvoiceS3Key());

            try {
                Event e = b.getEvent();
                if (e != null) {
                    Map<String, Object> eventMap = new LinkedHashMap<>();
                    eventMap.put("id",         e.getId());
                    eventMap.put("title",      e.getTitle());
                    eventMap.put("event_date", e.getEventDate());
                    eventMap.put("location",   e.getLocation());
                    eventMap.put("category",   e.getCategory());
                    eventMap.put("images",     e.getImages());
                    eventMap.put("price",      e.getPrice());
                    bMap.put("Event", eventMap);
                }
            } catch (Exception ex) {
                log.warn("Could not load event for bookingId={}: {}", b.getId(), ex.getMessage());
            }

            result.add(bMap);
        }

        log.info("Returned {} bookings for userId={}", result.size(), user.getId());
        return ResponseEntity.ok(result);
    }

    // ── GET /bookings/:id/download-ticket ─────────────────────────────────────
    @GetMapping("/{id}/download-ticket")
    public ResponseEntity<?> downloadTicket(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Ticket download requested: bookingId={} userId={}", id, user.getId());

        Booking booking = bookingService.getBookingByIdAndUser(id, user.getId()).orElse(null);
        if (booking == null) {
            log.warn("Ticket download failed — not found: bookingId={} userId={}", id, user.getId());
            return ResponseEntity.status(404).body(Map.of("error", "Booking not found"));
        }

        try {
            byte[] pdfBytes;
            if (booking.getTicketPdfS3Key() != null && !booking.getTicketPdfS3Key().isBlank()) {
                log.info("Serving ticket from S3 key={}", booking.getTicketPdfS3Key());
                pdfBytes = s3Service.fetchTicket(booking.getTicketPdfS3Key());
            } else {
                log.warn("S3 key missing, generating ticket PDF on-the-fly for bookingId={}", id);
                User  u = userRepo.findById(user.getId()).orElseThrow();
                Event e = eventRepo.findById(booking.getEventId()).orElseThrow();
                pdfBytes = pdfService.generateTicketPdf(booking, u, e);
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"ticket-" + booking.getId() + ".pdf\"")
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("Ticket download error: bookingId={} error={}", id, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to generate ticket."));
        }
    }

    // ── GET /bookings/:id/download-invoice ────────────────────────────────────
    /**
     * Downloads the booking invoice PDF for a confirmed booking.
     *
     * Mirrors TBA2's downloadBookingInvoice() exactly:
     *  - Serves from S3 if booking_invoice_s3_key is set
     *  - Generates on-the-fly if S3 key is missing (fallback)
     *  - Returns 404 if booking not found or doesn't belong to user
     */
    @GetMapping("/{id}/download-invoice")
    public ResponseEntity<?> downloadBookingInvoice(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Booking invoice download requested: bookingId={} userId={}", id, user.getId());

        Booking booking = bookingService.getBookingByIdAndUser(id, user.getId()).orElse(null);
        if (booking == null) {
            log.warn("Booking invoice download failed — not found: bookingId={} userId={}", id, user.getId());
            return ResponseEntity.status(404).body(Map.of("error", "Booking not found"));
        }

        try {
            byte[] pdfBytes;
            if (booking.getBookingInvoiceS3Key() != null && !booking.getBookingInvoiceS3Key().isBlank()) {
                log.info("Serving booking invoice from S3 key={}", booking.getBookingInvoiceS3Key());
                pdfBytes = s3Service.fetchBookingInvoice(booking.getBookingInvoiceS3Key());
            } else {
                log.warn("booking_invoice_s3_key missing — generating on-the-fly: bookingId={}", id);
                User  u = userRepo.findById(user.getId()).orElseThrow();
                Event e = eventRepo.findById(booking.getEventId()).orElseThrow();
                pdfBytes = pdfService.generateBookingInvoicePdf(booking, u, e);
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"invoice-booking-" + booking.getId() + ".pdf\"")
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("Booking invoice download error: bookingId={} error={}", id, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Failed to generate booking invoice."));
        }
    }
}
