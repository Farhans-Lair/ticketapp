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

    // ── Events ─────────────────────────────────────────────────────────────

    public List<Event> getOrganizerEvents(Long organizerId) {
        return eventRepo.findByOrganizerIdOrderByEventDateAsc(organizerId);
    }

    // ── Revenue ─────────────────────────────────────────────────────────────

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

    // ── Stats ─────────────────────────────────────────────────────────────

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

    // ── Attendees ─────────────────────────────────────────────────────────

    public Map<String, Object> getEventAttendees(Long eventId, Long organizerId) {
        Event event = eventRepo.findByIdAndOrganizerId(eventId, organizerId).orElse(null);
        if (event == null) return null;

        List<Booking> bookings = bookingRepo.findByEventIdAndPaymentStatus(eventId, "paid");

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

    // ── Admin ─────────────────────────────────────────────────────────────

/**
     * Returns all organizer profiles shaped exactly as the Express API returned them,
     * with "User" (capital U, matching Sequelize convention) and snake_case field names.
     *
     * Returns List<Map> instead of List<OrganizerProfile> to avoid two bugs:
     *   1. LazyInitializationException on OrganizerProfile.user (FetchType.LAZY)
     *      which causes ERR_INCOMPLETE_CHUNKED_ENCODING mid-response.
     *   2. Jackson camelCase field names (businessName) vs frontend expecting
     *      snake_case (business_name) and User with capital U.
     */
    public List<Map<String, Object>> getAllOrganizers(String status) {
        List<OrganizerProfile> profiles;
        if (status != null && !status.isBlank()) {
            profiles = profileRepo.findByStatusOrderByCreatedAtDesc(status);
        } else {
            profiles = profileRepo.findAllByOrderByCreatedAtDesc();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (OrganizerProfile profile : profiles) {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("id",               profile.getId());
            map.put("user_id",          profile.getUserId());
            map.put("business_name",    profile.getBusinessName());
            map.put("contact_phone",    profile.getContactPhone());
            map.put("gst_number",       profile.getGstNumber());
            map.put("address",          profile.getAddress());
            map.put("status",           profile.getStatus());
            map.put("rejection_reason", profile.getRejectionReason());
            map.put("created_at",       profile.getCreatedAt());
            map.put("updated_at",       profile.getUpdatedAt());

            // Fetch user eagerly by id — avoids LAZY proxy entirely.
            // Capital "User" key matches the Sequelize/Express response the frontend expects.
            userRepo.findById(profile.getUserId()).ifPresent(u -> {
                Map<String, Object> userMap = new java.util.LinkedHashMap<>();
                userMap.put("id",         u.getId());
                userMap.put("name",       u.getName());
                userMap.put("email",      u.getEmail());
                userMap.put("created_at", u.getCreatedAt());
                map.put("User", userMap);
            });

            result.add(map);
        }
        return result;
    }


    /**
     * Converts an OrganizerProfile to a safe Map without touching the lazy User proxy.
     * Use this whenever returning a profile from admin approve/reject endpoints
     * to prevent Jackson from serializing the Hibernate proxy and causing a 500.
     */
    public Map<String, Object> safeProfileMap(OrganizerProfile profile) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id",               profile.getId());
        map.put("user_id",          profile.getUserId());
        map.put("business_name",    profile.getBusinessName());
        map.put("contact_phone",    profile.getContactPhone());
        map.put("gst_number",       profile.getGstNumber());
        map.put("address",          profile.getAddress());
        map.put("status",           profile.getStatus());
        map.put("rejection_reason", profile.getRejectionReason());
        map.put("created_at",       profile.getCreatedAt());
        map.put("updated_at",       profile.getUpdatedAt());
        return map;
    }

    /**
     * Same as safeProfileMap but also fetches User by id and includes it as
     * capital-"User" key — matches what the organizer-dashboard frontend expects
     * (profile.User?.name, profile.User?.email).
     * Never touches the lazy proxy on OrganizerProfile.user.
     */
    public Map<String, Object> safeProfileMapWithUser(OrganizerProfile profile) {
        Map<String, Object> map = safeProfileMap(profile);
        userRepo.findById(profile.getUserId()).ifPresent(u -> {
            Map<String, Object> userMap = new java.util.LinkedHashMap<>();
            userMap.put("id",    u.getId());
            userMap.put("name",  u.getName());
            userMap.put("email", u.getEmail());
            map.put("User", userMap);
        });
        return map;
    }

    @Transactional
    public OrganizerProfile approveOrganizer(Long profileId) {
        OrganizerProfile profile = profileRepo.findById(profileId).orElse(null);
        if (profile == null) return null;

        profile.setStatus("approved");
        profile.setRejectionReason(null);

        OrganizerProfile savedProfile = profileRepo.save(profile);

        userRepo.findById(savedProfile.getUserId()).ifPresent(user ->
            emailService.sendOrganizerApprovedEmail(
                user.getEmail(),
                savedProfile.getBusinessName()
            )
        );

        return savedProfile;
    }

    // ✅ FIXED METHOD
    @Transactional
    public OrganizerProfile rejectOrganizer(Long profileId, String reason) {
        OrganizerProfile profile = profileRepo.findById(profileId).orElse(null);
        if (profile == null) return null;

        profile.setStatus("rejected");
        profile.setRejectionReason(reason);

        OrganizerProfile savedProfile = profileRepo.save(profile); // ✅ FIX

        userRepo.findById(savedProfile.getUserId()).ifPresent(user ->
            emailService.sendOrganizerRejectedEmail(
                user.getEmail(),
                savedProfile.getBusinessName(),
                reason
            )
        );

        return savedProfile;
    }

    @Transactional
    public boolean deleteOrganizer(Long profileId) {
        OrganizerProfile profile = profileRepo.findById(profileId).orElse(null);
        if (profile == null) return false;

        userRepo.deleteById(profile.getUserId());
        return true;
    }
}