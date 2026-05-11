package com.ticketapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketapp.entity.Booking;
import com.ticketapp.entity.Event;
import com.ticketapp.entity.Seat;
import com.ticketapp.repository.BookingRepository;
import com.ticketapp.repository.EventRepository;
import com.ticketapp.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepo;
    private final EventRepository   eventRepo;
    private final SeatRepository    seatRepo;     // for per-seat price lookup
    private final SeatService       seatService;
    private final ObjectMapper      objectMapper;
    private final CouponService     couponService;   // Feature 7
    private final QrService         qrService;       // Feature 8

    private static final double CONVENIENCE_FEE_RATE = 0.10;
    private static final double GST_RATE             = 0.09;

    // ── Phase 1 — calculate (no DB write) ────────────────────────────────────

    public Map<String, Object> calculateBookingAmount(Long eventId, int ticketsBooked) {
        return calculateBookingAmount(eventId, ticketsBooked, null, List.of());
    }

    /**
     * Overload with optional coupon code — Feature 7.
     * Returns the same keys as before plus: couponCode, discountAmount, finalAmount.
     */
    public Map<String, Object> calculateBookingAmount(Long eventId, int ticketsBooked,
                                                       String couponCode) {
        return calculateBookingAmount(eventId, ticketsBooked, couponCode, List.of());
    }

    /**
     * Full overload — uses per-seat prices from the DB when seat numbers are provided.
     * Falls back to event.getPrice() only if no seat-tier prices are configured.
     */
    public Map<String, Object> calculateBookingAmount(Long eventId, int ticketsBooked,
                                                       String couponCode,
                                                       List<String> selectedSeats) {
        Event event = eventRepo.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        if (event.getAvailableTickets() < ticketsBooked)
            throw new RuntimeException("Not enough tickets available");

        // Use per-seat prices when seats are selected and have individual prices configured
        double ticketAmount = resolveTicketAmount(eventId, selectedSeats, event.getPrice(), ticketsBooked);

        double convenienceFee = ticketAmount * CONVENIENCE_FEE_RATE;
        double gstAmount      = convenienceFee * GST_RATE;
        double subtotal       = ticketAmount + convenienceFee + gstAmount;

        double discountAmount = 0.0;
        if (couponCode != null && !couponCode.isBlank()) {
            Map<String, Object> couponCheck = couponService.validate(
                    couponCode, /* userId placeholder */ -1L, subtotal);
            if ((boolean) couponCheck.get("valid")) {
                discountAmount = ((Number) couponCheck.get("discountAmount")).doubleValue();
            }
        }

        double totalPaid = subtotal - discountAmount;

        return Map.of(
            "event",          event,
            "ticketAmount",   ticketAmount,
            "convenienceFee", convenienceFee,
            "gstAmount",      gstAmount,
            "discountAmount", discountAmount,
            "couponCode",     couponCode != null ? couponCode : "",
            "totalPaid",      totalPaid
        );
    }

    /**
     * Resolves the ticket amount from per-seat prices if tiers are configured,
     * otherwise falls back to event.price × count.
     *
     * A seat has a tier price when Seat.price is non-null and > 0.
     * If ALL requested seats have prices, we sum them.
     * If any seat is missing a price (e.g. old flat-price event), fall back.
     */
    private double resolveTicketAmount(Long eventId, List<String> selectedSeats,
                                        double fallbackEventPrice, int ticketsBooked) {
        if (selectedSeats == null || selectedSeats.isEmpty())
            return fallbackEventPrice * ticketsBooked;

        List<Seat> seats = seatRepo.findByEventIdAndSeatNumberIn(eventId, selectedSeats);
        if (seats.size() != selectedSeats.size()) {
            log.warn("resolveTicketAmount: could not find all seats (found={} requested={}); using fallback price",
                    seats.size(), selectedSeats.size());
            return fallbackEventPrice * ticketsBooked;
        }

        boolean allHavePrices = seats.stream()
                .allMatch(s -> s.getPrice() != null && s.getPrice() > 0);
        if (!allHavePrices)
            return fallbackEventPrice * ticketsBooked;

        double total = seats.stream().mapToDouble(Seat::getPrice).sum();
        log.debug("resolveTicketAmount: using seat-tier prices for {} seats → total={}", seats.size(), total);
        return total;
    }

    // ── Phase 2 — confirm booking (transactional) ─────────────────────────────

    @Transactional
    public Booking confirmBooking(Long userId, Long eventId, int ticketsBooked,
                                  String razorpayOrderId, String razorpayPaymentId,
                                  List<String> selectedSeats) {
        return confirmBooking(userId, eventId, ticketsBooked,
                razorpayOrderId, razorpayPaymentId, selectedSeats, null, null);
    }

    /**
     * Full overload — Feature 7 (coupon) + Feature 8 (QR).
     */
    @Transactional
    public Booking confirmBooking(Long userId, Long eventId, int ticketsBooked,
                                  String razorpayOrderId, String razorpayPaymentId,
                                  List<String> selectedSeats,
                                  String couponCode,
                                  Long showtimeId) {

        Event event = eventRepo.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        if (event.getAvailableTickets() < ticketsBooked)
            throw new RuntimeException("Not enough tickets available");

        if (selectedSeats != null && !selectedSeats.isEmpty())
            seatService.confirmHeldOrBook(eventId, selectedSeats, userId);

        event.setAvailableTickets(event.getAvailableTickets() - ticketsBooked);
        eventRepo.save(event);

        // Use per-seat prices when tiers are configured, else fall back to event.getPrice()
        double ticketAmount   = resolveTicketAmount(eventId, selectedSeats, event.getPrice(), ticketsBooked);
        double convenienceFee = ticketAmount * CONVENIENCE_FEE_RATE;
        double gstAmount      = convenienceFee * GST_RATE;
        double subtotal       = ticketAmount + convenienceFee + gstAmount;

        // ── Feature 7: Coupon redemption (atomic) ─────────────────────────────
        double discountAmount = 0.0;
        String appliedCoupon  = null;
        if (couponCode != null && !couponCode.isBlank()) {
            try {
                discountAmount = couponService.redeem(couponCode, userId, subtotal);
                appliedCoupon  = couponCode.toUpperCase();
                log.info("Coupon applied: code={} userId={} discount={}", couponCode, userId, discountAmount);
            } catch (Exception ex) {
                log.warn("Coupon redemption failed during booking (proceeding without discount): {}", ex.getMessage());
            }
        }

        double totalPaid = subtotal - discountAmount;

        // Serialize selected seats to valid JSON
        String selectedSeatsJson = "[]";
        if (selectedSeats != null && !selectedSeats.isEmpty()) {
            try {
                selectedSeatsJson = objectMapper.writeValueAsString(selectedSeats);
            } catch (Exception ex) {
                log.error("Failed to serialize selectedSeats: {}", ex.getMessage());
            }
        }

        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setEventId(eventId);
        booking.setShowtimeId(showtimeId);
        booking.setTicketsBooked(ticketsBooked);
        // pricePerTicket = average per seat (correct for both flat-price and tiered events)
        booking.setPricePerTicket(ticketAmount / ticketsBooked);
        booking.setTicketAmount(ticketAmount);
        booking.setConvenienceFee(convenienceFee);
        booking.setGstAmount(gstAmount);
        booking.setDiscountAmount(discountAmount);
        booking.setCouponCode(appliedCoupon);
        booking.setTotalPaid(totalPaid);
        booking.setSelectedSeats(selectedSeatsJson);
        booking.setRazorpayOrderId(razorpayOrderId);
        booking.setRazorpayPaymentId(razorpayPaymentId);
        booking.setPaymentStatus("paid");
        booking.setCancellationStatus("active");
        booking.setCheckedIn(false);

        // ── Feature 8: Generate QR token ──────────────────────────────────────
        // The booking must be saved first to get an ID, then we update the token.
        Booking saved = bookingRepo.save(booking);
        try {
            String qrToken = qrService.generateToken(saved.getId(), userId, eventId);
            saved.setQrToken(qrToken);
            saved = bookingRepo.save(saved);
            log.debug("QR token generated for bookingId={}", saved.getId());
        } catch (Exception ex) {
            log.error("QR token generation failed for bookingId={}: {}", saved.getId(), ex.getMessage());
            // Non-fatal — ticket still valid; QR just won't be on the PDF
        }

        return saved;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<Booking> getUserBookings(Long userId) {
        return bookingRepo.findByUserIdWithEvent(userId);
    }

    public Optional<Booking> getBookingByIdAndUser(Long bookingId, Long userId) {
        return bookingRepo.findByIdAndUserId(bookingId, userId);
    }

    public Booking saveBooking(Booking booking) {
        return bookingRepo.save(booking);
    }
}

