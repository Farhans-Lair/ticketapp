package com.ticketapp.service;

import com.ticketapp.entity.Booking;
import com.ticketapp.entity.Event;
import com.ticketapp.repository.BookingRepository;
import com.ticketapp.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepo;
    private final EventRepository   eventRepo;
    private final SeatService       seatService;

    private static final double CONVENIENCE_FEE_RATE = 0.10;
    private static final double GST_RATE             = 0.09;

    // ── Phase 1 — calculate (no DB write) ────────────────────────────────────

    public Map<String, Object> calculateBookingAmount(Long eventId, int ticketsBooked) {
        Event event = eventRepo.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        if (event.getAvailableTickets() < ticketsBooked)
            throw new RuntimeException("Not enough tickets available");

        double ticketAmount   = event.getPrice() * ticketsBooked;
        double convenienceFee = ticketAmount * CONVENIENCE_FEE_RATE;
        double gstAmount      = convenienceFee * GST_RATE;
        double totalPaid      = ticketAmount + convenienceFee + gstAmount;

        return Map.of(
            "event",          event,
            "ticketAmount",   ticketAmount,
            "convenienceFee", convenienceFee,
            "gstAmount",      gstAmount,
            "totalPaid",      totalPaid
        );
    }

    // ── Phase 2 — confirm booking (transactional) ─────────────────────────────

    @Transactional
    public Booking confirmBooking(Long userId, Long eventId, int ticketsBooked,
                                  String razorpayOrderId, String razorpayPaymentId,
                                  List<String> selectedSeats) {

        // Re-check availability inside transaction
        Event event = eventRepo.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        if (event.getAvailableTickets() < ticketsBooked)
            throw new RuntimeException("Not enough tickets available");

        // Lock & book selected seats atomically
        if (selectedSeats != null && !selectedSeats.isEmpty()) {
            seatService.bookSeats(eventId, selectedSeats);
        }

        // Deduct available tickets
        event.setAvailableTickets(event.getAvailableTickets() - ticketsBooked);
        eventRepo.save(event);

        // Calculate amounts (same formula as Phase 1)
        double ticketAmount   = event.getPrice() * ticketsBooked;
        double convenienceFee = ticketAmount * CONVENIENCE_FEE_RATE;
        double gstAmount      = convenienceFee * GST_RATE;
        double totalPaid      = ticketAmount + convenienceFee + gstAmount;

        // Persist booking
        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setEventId(eventId);
        booking.setTicketsBooked(ticketsBooked);

        // ✅ FIX: add this line
        booking.setPricePerTicket(event.getPrice());

        booking.setTicketAmount(ticketAmount);
        booking.setConvenienceFee(convenienceFee);
        booking.setGstAmount(gstAmount);
        booking.setTotalPaid(totalPaid);
        booking.setSelectedSeats(selectedSeats != null ? selectedSeats.toString() : "[]");
        booking.setRazorpayOrderId(razorpayOrderId);
        booking.setRazorpayPaymentId(razorpayPaymentId);
        booking.setPaymentStatus("paid");

        return bookingRepo.save(booking);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<Booking> getUserBookings(Long userId) {
        return bookingRepo.findByUserIdWithEvent(userId);
    }

    public Optional<Booking> getBookingByIdAndUser(Long bookingId, Long userId) {
        return bookingRepo.findByIdAndUserIdWithEvent(bookingId, userId);
    }
}
