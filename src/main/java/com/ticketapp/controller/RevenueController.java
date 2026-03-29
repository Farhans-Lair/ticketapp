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
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class RevenueController {

    private final EventRepository   eventRepo;
    private final BookingRepository bookingRepo;

    // ── GET /api/revenue (admin only) ─────────────────────────────────────────
    @GetMapping("/revenue")
    @PreAuthorize("@roleCheck.isAdmin(authentication)")
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

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id",          event.getId());
            entry.put("title",       event.getTitle());
            entry.put("event_date",  event.getEventDate());
            entry.put("location",    event.getLocation());
            entry.put("category",    event.getCategory());
            entry.put("Bookings",    bookings);
            result.add(entry);
        }

        log.info("Revenue report generated: {} events, total=₹{}", result.size(),
                 String.format("%.2f", totalRevenue));
        return ResponseEntity.ok(result);
    }
}
