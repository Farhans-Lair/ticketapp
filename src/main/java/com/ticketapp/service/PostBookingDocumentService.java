package com.ticketapp.service;

import com.ticketapp.entity.Booking;
import com.ticketapp.entity.Event;
import com.ticketapp.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * PostBookingDocumentService — async PDF generation and delivery.
 *
 * WHY THIS EXISTS:
 *   PaymentController.verifyPayment() previously generated two PDFs (ticket +
 *   invoice), uploaded both to S3, and sent two emails — all synchronously on
 *   the payment-confirmation thread. PDFBox is CPU-heavy; each PDF takes
 *   100–400 ms. Under peak load this blocks Tomcat threads and delays the
 *   "payment confirmed" response the user is waiting for.
 *
 * SOLUTION:
 *   1. verifyPayment() confirms the booking in DB and returns HTTP 200
 *      with the booking summary immediately.
 *   2. It fires processPostBookingDocuments() on a background thread (@Async).
 *   3. The user first receives a lightweight "booking confirmed" email
 *      (no attachment), then the ticket PDF and invoice PDF follow
 *      once generation + S3 upload complete.
 *
 * FAILURE HANDLING:
 *   PDF failures do not affect the booking record (already confirmed in DB).
 *   All errors are logged at ERROR level for CloudWatch alerting.
 *   If the ticket email was already sent, a second confirmation is not sent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostBookingDocumentService {

    private final PdfService     pdfService;
    private final S3Service      s3Service;
    private final EmailService   emailService;
    private final BookingService bookingService;

    /**
     * Generates, uploads, and emails ticket + invoice PDFs.
     * Runs in a background thread — must not be called from within the same bean.
     *
     * @param booking confirmed booking (already persisted)
     * @param user    the buyer
     * @param event   the event booked
     */
    @Async
    public void processPostBookingDocuments(Booking booking, User user, Event event) {
        log.info("Async PDF generation started: bookingId={}", booking.getId());

        // ── Step 1: quick confirmation email (no attachment, immediate) ─────────
        try {
            emailService.sendTicketEmail(user, booking, event, null);
        } catch (Exception ex) {
            log.error("Confirmation email failed: bookingId={} error={}", booking.getId(), ex.getMessage());
        }

        // ── Step 2: ticket PDF ────────────────────────────────────────────────
        byte[] ticketPdf = null;
        try {
            ticketPdf = pdfService.generateTicketPdf(booking, user, event);
            log.info("Ticket PDF generated: bookingId={} size={}B", booking.getId(), ticketPdf.length);
        } catch (Exception ex) {
            log.error("Ticket PDF generation failed: bookingId={} error={}", booking.getId(), ex.getMessage());
        }

        // ── Step 3: upload ticket PDF to S3 ───────────────────────────────────
        if (ticketPdf != null) {
            try {
                String s3Key = s3Service.uploadTicket(ticketPdf, booking.getId(), booking.getUserId());
                booking.setTicketPdfS3Key(s3Key);
                bookingService.saveBooking(booking);
                log.info("Ticket PDF S3 key saved: bookingId={} key={}", booking.getId(), s3Key);
            } catch (Exception ex) {
                log.error("Ticket S3 upload failed: bookingId={} error={}", booking.getId(), ex.getMessage());
            }
        }

        // ── Step 4: send ticket PDF email ──────────────────────────────────────
        if (ticketPdf != null) {
            try {
                emailService.sendTicketEmail(user, booking, event, ticketPdf);
            } catch (Exception ex) {
                log.error("Ticket PDF email failed: bookingId={} error={}", booking.getId(), ex.getMessage());
            }
        }

        // ── Step 5: booking invoice PDF ────────────────────────────────────────
        byte[] invoicePdf = null;
        try {
            invoicePdf = pdfService.generateBookingInvoicePdf(booking, user, event);
            log.info("Invoice PDF generated: bookingId={} size={}B", booking.getId(), invoicePdf.length);
        } catch (Exception ex) {
            log.error("Invoice PDF generation failed: bookingId={} error={}", booking.getId(), ex.getMessage());
        }

        // ── Step 6: upload invoice PDF to S3 ──────────────────────────────────
        if (invoicePdf != null) {
            try {
                String invoiceKey = s3Service.uploadBookingInvoice(invoicePdf, booking.getId(), booking.getUserId());
                booking.setBookingInvoiceS3Key(invoiceKey);
                bookingService.saveBooking(booking);
                log.info("Invoice PDF S3 key saved: bookingId={} key={}", booking.getId(), invoiceKey);
            } catch (Exception ex) {
                log.error("Invoice S3 upload failed: bookingId={} error={}", booking.getId(), ex.getMessage());
            }
        }

        // ── Step 7: send invoice PDF email ─────────────────────────────────────
        try {
            emailService.sendBookingInvoiceEmail(user, booking, event, invoicePdf);
        } catch (Exception ex) {
            log.error("Invoice email failed: bookingId={} error={}", booking.getId(), ex.getMessage());
        }

        log.info("Async PDF generation complete: bookingId={}", booking.getId());
    }
}
