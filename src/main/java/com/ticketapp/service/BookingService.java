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

        Event event = eventRepo.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        if (event.getAvailableTickets() < ticketsBooked)
            throw new RuntimeException("Not enough tickets available");

        if (selectedSeats != null && !selectedSeats.isEmpty())
            seatService.bookSeats(eventId, selectedSeats);

        event.setAvailableTickets(event.getAvailableTickets() - ticketsBooked);
        eventRepo.save(event);

        double ticketAmount   = event.getPrice() * ticketsBooked;
        double convenienceFee = ticketAmount * CONVENIENCE_FEE_RATE;
        double gstAmount      = convenienceFee * GST_RATE;
        double totalPaid      = ticketAmount + convenienceFee + gstAmount;

        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setEventId(eventId);
        booking.setTicketsBooked(ticketsBooked);
        booking.setPricePerTicket(event.getPrice());
        booking.setTicketAmount(ticketAmount);
        booking.setConvenienceFee(convenienceFee);
        booking.setGstAmount(gstAmount);
        booking.setTotalPaid(totalPaid);
        booking.setSelectedSeats(selectedSeats != null ? selectedSeats.toString() : "[]");
        booking.setRazorpayOrderId(razorpayOrderId);
        booking.setRazorpayPaymentId(razorpayPaymentId);
        booking.setPaymentStatus("paid");
        booking.setCancellationStatus("active");

        return bookingRepo.save(booking);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<Booking> getUserBookings(Long userId) {
        return bookingRepo.findByUserIdWithEvent(userId);
    }

    public Optional<Booking> getBookingByIdAndUser(Long bookingId, Long userId) {
        return bookingRepo.findByIdAndUserId(bookingId, userId);
    }

    /** Used by CancellationController after updating S3 key / status */
    public Booking saveBooking(Booking booking) {
        return bookingRepo.save(booking);
    }
}
