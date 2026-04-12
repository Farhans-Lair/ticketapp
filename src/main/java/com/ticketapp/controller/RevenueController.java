package com.ticketapp.controller;

import com.ticketapp.entity.Booking;
import com.ticketapp.entity.Event;
import com.ticketapp.repository.BookingRepository;
import com.ticketapp.repository.EventRepository;
import com.ticketapp.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * RevenueController — manual role check replaces @PreAuthorize.
 * See OrganizerController for the explanation of why @PreAuthorize causes
 * ERR_INCOMPLETE_CHUNKED_ENCODING via Spring Security's ExceptionTranslationFilter.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class RevenueController {

    private final EventRepository   eventRepo;
    private final BookingRepository bookingRepo;

    @GetMapping("/revenue")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getRevenue(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null || !"admin".equals(user.getRole()))
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required."));

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
