package com.ticketapp.controller;

import com.razorpay.Order;
import com.ticketapp.dto.PaymentDto;
import com.ticketapp.entity.Booking;
import com.ticketapp.entity.Event;
import com.ticketapp.entity.User;
import com.ticketapp.repository.EventRepository;
import com.ticketapp.repository.UserRepository;
import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.service.BookingService;
import com.ticketapp.service.EmailService;
import com.ticketapp.service.PaymentService;
import com.ticketapp.service.PdfService;
import com.ticketapp.service.S3Service;
import com.ticketapp.service.SmsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService  paymentService;
    private final BookingService  bookingService;
    private final PdfService      pdfService;
    private final S3Service       s3Service;
    private final EmailService    emailService;
    private final SmsService      smsService;
    private final UserRepository  userRepo;
    private final EventRepository eventRepo;

    @Value("${razorpay.key-id:}")
    private String razorpayKeyId;

    // ── POST /payments/create-order ───────────────────────────────────────────
    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(
            @Valid @RequestBody PaymentDto.CreateOrderRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {

        Long         userId        = user.getId();
        Long         eventId       = body.getEvent_id();
        int          ticketsBooked = body.getTickets_booked();
        List<String> seats         = body.getSelected_seats() != null
                                        ? body.getSelected_seats() : List.of();

        if (ticketsBooked <= 0)
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Valid event_id and tickets_booked required"));

        if (seats.size() != ticketsBooked)
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Please select exactly " + ticketsBooked + " seat(s)"));

        log.info("Creating Razorpay order: userId={} eventId={} tickets={}", userId, eventId, ticketsBooked);

        Map<String, Object> calc = bookingService.calculateBookingAmount(eventId, ticketsBooked, null, seats);
        Event  event        = (Event)  calc.get("event");
        double ticketAmount = (double) calc.get("ticketAmount");
        double convFee      = (double) calc.get("convenienceFee");
        double gstAmount    = (double) calc.get("gstAmount");
        double totalPaid    = (double) calc.get("totalPaid");

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("event_title",     event.getTitle());
        breakdown.put("tickets_booked",  ticketsBooked);
        breakdown.put("ticket_amount",   ticketAmount);
        breakdown.put("convenience_fee", convFee);
        breakdown.put("gst_amount",      gstAmount);
        breakdown.put("total_paid",      totalPaid);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("user_id",        userId);
        meta.put("event_id",       eventId);
        meta.put("tickets_booked", ticketsBooked);
        meta.put("selected_seats", seats);

        // ── Free event: skip Razorpay entirely (min order is ₹1) ─────────────
        if (totalPaid <= 0.0) {
            Map<String, Object> freeResp = new LinkedHashMap<>();
            freeResp.put("order_id",   "free_" + System.currentTimeMillis());
            freeResp.put("amount",     0);
            freeResp.put("currency",   "INR");
            freeResp.put("key_id",     razorpayKeyId);
            freeResp.put("free_order", true);
            freeResp.put("breakdown",  breakdown);
            freeResp.put("meta",       meta);
            log.info("Free event order created: eventId={} userId={}", eventId, userId);
            return ResponseEntity.ok(freeResp);
        }

        String receipt = "rcpt_u" + userId + "_e" + eventId + "_" + System.currentTimeMillis();
        Order order;
        try {
            order = paymentService.createOrder(totalPaid, receipt);
        } catch (Exception e) {
            log.error("Razorpay order creation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("order_id",  order.get("id"));
        response.put("amount",    order.get("amount"));
        response.put("currency",  order.get("currency"));
        response.put("key_id",    razorpayKeyId);
        response.put("breakdown", breakdown);
        response.put("meta",      meta);

        log.info("Razorpay order created: orderId={} total={}", order.get("id"), totalPaid);
        return ResponseEntity.ok(response);
    }

    // ── POST /payments/verify ─────────────────────────────────────────────────
    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(
            @RequestBody PaymentDto.VerifyPaymentRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {

        Long   userId    = user.getId();
        String orderId   = body.getRazorpay_order_id();
        String paymentId = body.getRazorpay_payment_id();
        String signature = body.getRazorpay_signature();

        if (orderId == null || paymentId == null || signature == null)
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing Razorpay payment fields"));

        if (body.getEvent_id() == null || body.getTickets_booked() == null)
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing booking meta fields"));

        log.info("Verifying payment: userId={} orderId={} paymentId={}", userId, orderId, paymentId);

        // ── Free event: skip Razorpay signature check ─────────────────────────
        boolean isFreeBooking = paymentId != null && paymentId.startsWith("free_");
        if (!isFreeBooking && !paymentService.verifySignature(orderId, paymentId, signature)) {
            log.error("Payment signature invalid: userId={} orderId={}", userId, orderId);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Payment verification failed. Invalid signature."));
        }

        // ── Confirm booking in DB ─────────────────────────────────────────────
        Booking booking = bookingService.confirmBooking(
            userId, body.getEvent_id(), body.getTickets_booked(),
            orderId, paymentId,
            body.getSelected_seats() != null ? body.getSelected_seats() : List.of());

        log.info("Booking confirmed: bookingId={} userId={} total={}", booking.getId(), userId, booking.getTotalPaid());

        User  u = userRepo.findById(userId).orElse(null);
        Event e = eventRepo.findById(body.getEvent_id()).orElse(null);

        if (u != null && e != null) {

            // ── 1. Generate ticket PDF ────────────────────────────────────────
            byte[] ticketPdf = null;
            try {
                ticketPdf = pdfService.generateTicketPdf(booking, u, e);
                log.info("Ticket PDF generated: bookingId={} size={}B", booking.getId(), ticketPdf.length);
            } catch (Exception ex) {
                log.error("Ticket PDF generation failed: bookingId={} error={}", booking.getId(), ex.getMessage());
            }

            // ── 2. Upload ticket PDF to S3 ────────────────────────────────────
            if (ticketPdf != null) {
                try {
                    String s3Key = s3Service.uploadTicket(ticketPdf, booking.getId(), userId);
                    booking.setTicketPdfS3Key(s3Key);
                    bookingService.saveBooking(booking);
                    log.info("Ticket PDF uploaded to S3 and key saved: {}", s3Key);
                } catch (Exception ex) {
                    log.error("Ticket S3 upload failed (booking still confirmed): {}", ex.getMessage());
                }
            }

            // ── 3. Send booking confirmation email (with ticket PDF attached) ─
            try {
                emailService.sendTicketEmail(u, booking, e, ticketPdf);
            } catch (Exception ex) {
                log.error("Ticket email failed (booking still confirmed): {}", ex.getMessage());
            }

            // ── 4. Generate booking invoice PDF ──────────────────────────────
            // Mirrors TBA2 verifyPayment: generate → upload to S3 → send separate email.
            // Failure does NOT roll back the booking.
            byte[] invoicePdf = null;
            try {
                invoicePdf = pdfService.generateBookingInvoicePdf(booking, u, e);
                log.info("Booking invoice PDF generated: bookingId={} size={}B",
                        booking.getId(), invoicePdf.length);
            } catch (Exception ex) {
                log.error("Booking invoice PDF generation failed: bookingId={} error={}",
                        booking.getId(), ex.getMessage());
            }

            // ── 5. Upload booking invoice PDF to S3 ──────────────────────────
            if (invoicePdf != null) {
                try {
                    String invoiceKey = s3Service.uploadBookingInvoice(invoicePdf, booking.getId(), userId);
                    booking.setBookingInvoiceS3Key(invoiceKey);
                    bookingService.saveBooking(booking);
                    log.info("Booking invoice uploaded to S3 and key saved: {}", invoiceKey);
                } catch (Exception ex) {
                    log.error("Booking invoice S3 upload failed (booking still confirmed): {}", ex.getMessage());
                }
            }

            // ── 6. Send booking invoice email ─────────────────────────────────
            // Separate email with A4 billing PDF — mirrors TBA2 sendBookingInvoiceEmail.
            try {
                emailService.sendBookingInvoiceEmail(u, booking, e, invoicePdf);
            } catch (Exception ex) {
                log.error("Booking invoice email failed (booking still confirmed): {}", ex.getMessage());
            }

            // ── 7. SMS booking confirmation ───────────────────────────────────
            // Fire-and-forget: non-fatal. Mirrors TBA2 sendBookingConfirmationSMS.
            // Skipped if user has no phone number on their profile.
            if (u.getPhone() != null && !u.getPhone().isBlank()) {
                final User  finalU = u;
                final Event finalE = e;
                final Booking finalB = booking;
                new Thread(() -> {
                    try {
                        smsService.sendBookingConfirmationSms(finalU, finalB, finalE);
                    } catch (Exception ex) {
                        log.error("Booking SMS failed: bookingId={} error={}", finalB.getId(), ex.getMessage());
                    }
                }).start();
            } else {
                log.warn("Booking SMS skipped — no phone on user profile: userId={}", userId);
            }
        }

        return ResponseEntity.status(201).body(Map.of(
            "message", "Payment verified and booking confirmed!",
            "booking", booking));
    }
}
