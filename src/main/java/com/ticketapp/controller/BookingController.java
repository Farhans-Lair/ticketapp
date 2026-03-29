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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    public ResponseEntity<List<Booking>> getMyBookings(
            @AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Fetching bookings for userId={}", user.getId());
        List<Booking> bookings = bookingService.getUserBookings(user.getId());
        log.info("Returned {} bookings for userId={}", bookings.size(), user.getId());
        return ResponseEntity.ok(bookings);
    }

    // ── GET /bookings/:id/download-ticket ─────────────────────────────────────
    @GetMapping("/{id}/download-ticket")
    public ResponseEntity<?> downloadTicket(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Ticket download requested: bookingId={} userId={}", id, user.getId());

        Booking booking = bookingService.getBookingByIdAndUser(id, user.getId())
                .orElse(null);

        if (booking == null) {
            log.warn("Ticket download failed — not found: bookingId={} userId={}", id, user.getId());
            return ResponseEntity.status(404).body(Map.of("error", "Booking not found"));
        }

        try {
            byte[] pdfBytes;

            if (booking.getTicketPdfS3Key() != null && !booking.getTicketPdfS3Key().isBlank()) {
                // Fast path: serve from S3
                log.info("Serving ticket from S3 key={}", booking.getTicketPdfS3Key());
                pdfBytes = s3Service.fetchTicket(booking.getTicketPdfS3Key());
            } else {
                // Fallback: generate on-the-fly
                log.warn("S3 key missing, generating PDF on-the-fly for bookingId={}", id);
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
}
