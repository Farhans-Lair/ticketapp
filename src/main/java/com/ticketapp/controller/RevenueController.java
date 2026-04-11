package com.ticketapp.controller;

import com.ticketapp.entity.Booking;
import com.ticketapp.entity.Event;
import com.ticketapp.repository.BookingRepository;
import com.ticketapp.repository.EventRepository;
import com.ticketapp.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class RevenueController {

    private final EventRepository   eventRepo;
    private final BookingRepository bookingRepo;

    /**
     * GET /api/revenue (admin only)
     *
     * Builds a plain Map response — never serializes Hibernate entity objects directly.
     * Returning raw @Entity objects that have lazy associations causes Jackson to touch
     * the Hibernate proxy after the session is closed, which throws
     * LazyInitializationException and cuts the HTTP response stream mid-way, producing
     * ERR_INCOMPLETE_CHUNKED_ENCODING in the browser even though the status was 200.
     *
     * @Transactional keeps the Hibernate session open for the entire method so that
     * even if a proxy is accidentally accessed, it won't throw.
     */
    @GetMapping("/revenue")
    @PreAuthorize("@roleCheck.isAdmin(authentication)")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getRevenue(@AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Revenue report requested by adminId={}", user.getId());

        List<Event> events = eventRepo.findAllByOrderByEventDateAsc();
        List<Map<String, Object>> result = new ArrayList<>();

        double totalRevenue = 0;
        for (Event event : events) {
            List<Booking> bookings = bookingRepo.findByEventIdAndPaymentStatus(event.getId(), "paid");
            if (bookings.isEmpty()) continue;

            double eventRevenue = bookings.stream()
                    .mapToDouble(b -> b.getTotalPaid() != null ? b.getTotalPaid() : 0)
                    .sum();
            totalRevenue += eventRevenue;

            // Build a safe list of booking maps — never serialize the Booking entity directly
            // because it has a lazy Event proxy (@JsonIgnore prevents Jackson from touching it
            // but Lombok @ToString/@EqualsAndHashCode can still trigger it in some code paths).
            List<Map<String, Object>> bookingMaps = new ArrayList<>();
            for (Booking b : bookings) {
                Map<String, Object> bMap = new LinkedHashMap<>();
                bMap.put("id",               b.getId());
                bMap.put("user_id",          b.getUserId());
                bMap.put("tickets_booked",   b.getTicketsBooked());
                bMap.put("ticket_amount",    b.getTicketAmount());
                bMap.put("convenience_fee",  b.getConvenienceFee());
                bMap.put("gst_amount",       b.getGstAmount());
                bMap.put("total_paid",       b.getTotalPaid());
                bMap.put("payment_status",   b.getPaymentStatus());
                bMap.put("booking_date",     b.getBookingDate());
                bookingMaps.add(bMap);
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id",         event.getId());
            entry.put("title",      event.getTitle());
            entry.put("event_date", event.getEventDate());
            entry.put("location",   event.getLocation());
            entry.put("category",   event.getCategory());
            entry.put("Bookings",   bookingMaps);
            result.add(entry);
        }

        log.info("Revenue report generated: {} events, total=₹{}", result.size(),
                 String.format("%.2f", totalRevenue));
        return ResponseEntity.ok(result);
    }
}
