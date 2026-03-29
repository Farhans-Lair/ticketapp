package com.ticketapp.service;

import com.ticketapp.entity.Booking;
import com.ticketapp.entity.Event;
import com.ticketapp.entity.OrganizerProfile;
import com.ticketapp.entity.User;
import com.ticketapp.repository.BookingRepository;
import com.ticketapp.repository.EventRepository;
import com.ticketapp.repository.OrganizerProfileRepository;
import com.ticketapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class OrganizerService {

    private final OrganizerProfileRepository profileRepo;
    private final UserRepository             userRepo;
    private final EventRepository            eventRepo;
    private final BookingRepository          bookingRepo;
    private final EmailService               emailService;

    // ── Profile ───────────────────────────────────────────────────────────────

    public Optional<OrganizerProfile> getProfile(Long userId) {
        return profileRepo.findByUserId(userId);
    }

    @Transactional
    public OrganizerProfile updateProfile(Long userId, String businessName,
                                          String contactPhone, String gstNumber,
                                          String address) {
        OrganizerProfile profile = profileRepo.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Organizer profile not found."));

        if (businessName  != null) profile.setBusinessName(businessName);
        if (contactPhone  != null) profile.setContactPhone(contactPhone);
        if (gstNumber     != null) profile.setGstNumber(gstNumber);
        if (address       != null) profile.setAddress(address);

        return profileRepo.save(profile);
    }

    // ── Events (organizer-scoped) ─────────────────────────────────────────────

    public List<Event> getOrganizerEvents(Long organizerId) {
        return eventRepo.findByOrganizerIdOrderByEventDateAsc(organizerId);
    }

    // ── Revenue ───────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getOrganizerRevenue(Long organizerId) {
        List<Event> events = eventRepo.findByOrganizerIdOrderByEventDateAsc(organizerId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Event event : events) {
            List<Booking> bookings = bookingRepo.findByEventIdAndPaymentStatus(event.getId(), "paid");
            if (bookings.isEmpty()) continue;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id",                event.getId());
            entry.put("title",             event.getTitle());
            entry.put("event_date",        event.getEventDate());
            entry.put("location",          event.getLocation());
            entry.put("Bookings",          bookings);
            result.add(entry);
        }
        return result;
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    public Map<String, Object> getOrganizerStats(Long organizerId) {
        List<Event> events = eventRepo.findByOrganizerIdOrderByEventDateAsc(organizerId);

        double totalRevenue  = 0;
        int    totalTickets  = 0;
        int    totalBookings = 0;

        for (Event event : events) {
            List<Booking> bookings = bookingRepo.findByEventIdAndPaymentStatus(event.getId(), "paid");
            for (Booking b : bookings) {
                totalRevenue  += b.getTotalPaid() != null ? b.getTotalPaid() : 0;
                totalTickets  += b.getTicketsBooked() != null ? b.getTicketsBooked() : 0;
                totalBookings++;
            }
        }

        return Map.of(
            "totalEvents",       events.size(),
            "totalBookings",     totalBookings,
            "totalTicketsSold",  totalTickets,
            "totalRevenue",      Math.round(totalRevenue * 100.0) / 100.0
        );
    }

    // ── Attendees ─────────────────────────────────────────────────────────────

    public Map<String, Object> getEventAttendees(Long eventId, Long organizerId) {
        Event event = eventRepo.findByIdAndOrganizerId(eventId, organizerId).orElse(null);
        if (event == null) return null;

        List<Booking> bookings = bookingRepo.findByEventIdAndPaymentStatus(eventId, "paid");

        // Enrich bookings with user info
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Booking b : bookings) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id",            b.getId());
            entry.put("tickets_booked",b.getTicketsBooked());
            entry.put("total_paid",    b.getTotalPaid());
            entry.put("booking_date",  b.getBookingDate());
            entry.put("selected_seats",b.getSelectedSeats());

            userRepo.findById(b.getUserId()).ifPresent(u -> {
                Map<String, Object> userMap = Map.of(
                    "id",    u.getId(),
                    "name",  u.getName(),
                    "email", u.getEmail()
                );
                entry.put("User", userMap);
            });
            enriched.add(entry);
        }

        return Map.of("event", event, "bookings", enriched);
    }

    // ── Admin — organizer management ──────────────────────────────────────────

    public List<OrganizerProfile> getAllOrganizers(String status) {
        if (status != null && !status.isBlank()) {
            return profileRepo.findByStatusOrderByCreatedAtDesc(status);
        }
        return profileRepo.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public OrganizerProfile approveOrganizer(Long profileId) {
        OrganizerProfile profile = profileRepo.findById(profileId).orElse(null);
        if (profile == null) return null;

        profile.setStatus("approved");
        profile.setRejectionReason(null);
        profile = profileRepo.save(profile);

        // Send approval email asynchronously
        userRepo.findById(profile.getUserId()).ifPresent(user ->
            emailService.sendOrganizerApprovedEmail(user.getEmail(), profile.getBusinessName())
        );

        return profile;
    }

    @Transactional
    public OrganizerProfile rejectOrganizer(Long profileId, String reason) {
        OrganizerProfile profile = profileRepo.findById(profileId).orElse(null);
        if (profile == null) return null;

        profile.setStatus("rejected");
        profile.setRejectionReason(reason);
        profile = profileRepo.save(profile);

        // Send rejection email asynchronously
        final OrganizerProfile finalProfile = profile;
        userRepo.findById(profile.getUserId()).ifPresent(user ->
            emailService.sendOrganizerRejectedEmail(user.getEmail(), finalProfile.getBusinessName(), reason)
        );

        return profile;
    }

    @Transactional
    public boolean deleteOrganizer(Long profileId) {
        OrganizerProfile profile = profileRepo.findById(profileId).orElse(null);
        if (profile == null) return false;
        // CASCADE in DB handles OrganizerProfile deletion when User is deleted
        userRepo.deleteById(profile.getUserId());
        return true;
    }
}
