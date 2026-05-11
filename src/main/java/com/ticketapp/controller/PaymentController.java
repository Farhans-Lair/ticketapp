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

        log.info("Creating Razorpay order: userId={} eventId={} tickets={}",
                userId, eventId, ticketsBooked);

        // Pass selectedSeats so BookingService can use per-seat tier prices
        Map<String, Object> calc = bookingService.calculateBookingAmount(eventId, ticketsBooked, null, seats);
        Event  event        = (Event)  calc.get("event");
        double ticketAmount = (double) calc.get("ticketAmount");
        double convFee      = (double) calc.get("convenienceFee");
        double gstAmount    = (double) calc.get("gstAmount");
        double totalPaid    = (double) calc.get("totalPaid");

        String receipt = "rcpt_u" + userId + "_e" + eventId + "_" + System.currentTimeMillis();
        Order order;
        try {
            order = paymentService.createOrder(totalPaid, receipt);
        } catch (Exception e) {
            log.error("Razorpay order creation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

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

        log.info("Verifying payment: userId={} orderId={} paymentId={}",
                userId, orderId, paymentId);

        if (!paymentService.verifySignature(orderId, paymentId, signature)) {
            log.error("Payment signature invalid: userId={} orderId={}", userId, orderId);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Payment verification failed. Invalid signature."));
        }

        // ── Confirm booking in DB ─────────────────────────────────────────────
        Booking booking = bookingService.confirmBooking(
            userId, body.getEvent_id(), body.getTickets_booked(),
            orderId, paymentId,
            body.getSelected_seats() != null ? body.getSelected_seats() : List.of());

        log.info("Booking confirmed: bookingId={} userId={} total={}",
                booking.getId(), userId, booking.getTotalPaid());

        User  u = userRepo.findById(userId).orElse(null);
        Event e = eventRepo.findById(body.getEvent_id()).orElse(null);

        if (u != null && e != null) {

            // ── 1. Generate booking ticket PDF ────────────────────────────────
            // Bytes are generated once and reused for both S3 upload and email
            // attachment — no double generation.
            byte[] ticketPdf = null;
            try {
                ticketPdf = pdfService.generateTicketPdf(booking, u, e);
                log.info("Ticket PDF generated: bookingId={} size={}B",
                        booking.getId(), ticketPdf.length);
            } catch (Exception ex) {
                log.error("Ticket PDF generation failed: bookingId={} error={}",
                        booking.getId(), ex.getMessage());
            }

            // ── 2. Upload ticket PDF to S3 ────────────────────────────────────
            // FIX: the old code set the S3 key on the booking object but never
            // called bookingService.saveBooking(), so the key was never persisted
            // to the DB. The download-ticket endpoint would then find no key and
            // re-generate on every request instead of serving from S3.
            if (ticketPdf != null) {
                try {
                    String s3Key = s3Service.uploadTicket(ticketPdf, booking.getId(), userId);
                    booking.setTicketPdfS3Key(s3Key);
                    bookingService.saveBooking(booking);   // ← persists S3 key to DB
                    log.info("Ticket PDF uploaded to S3 and key saved: {}", s3Key);
                } catch (Exception ex) {
                    log.error("Ticket S3 upload failed (booking still confirmed): {}",
                            ex.getMessage());
                    // ticketPdf bytes still available for email attachment below
                }
            }

            // ── 3. Send booking confirmation email with PDF attached ───────────
            // ticketPdf may be null if generation failed — EmailService handles
            // null gracefully by sending the HTML email without an attachment.
            try {
                emailService.sendTicketEmail(u, booking, e, ticketPdf);
            } catch (Exception ex) {
                log.error("Ticket email failed (booking still confirmed): {}", ex.getMessage());
            }
        }

        return ResponseEntity.status(201).body(Map.of(
            "message", "Payment verified and booking confirmed!",
            "booking", booking));
    }
}
