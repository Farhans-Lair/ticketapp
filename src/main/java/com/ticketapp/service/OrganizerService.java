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

    @Transactional(readOnly = true)
    public Optional<OrganizerProfile> getProfile(Long userId) {
        return profileRepo.findByUserId(userId);
    }

    /**
     * Updates organizer profile fields including payout bank/UPI details.
     * Null values are treated as "no change".
     */
    @Transactional
    public OrganizerProfile updateProfile(Long userId, String businessName,
                                          String contactPhone, String gstNumber,
                                          String address,
                                          String bankAccountNumber, String bankIfsc,
                                          String upiId, String payoutMethod) {
        OrganizerProfile profile = profileRepo.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Organizer profile not found."));

        if (businessName       != null) profile.setBusinessName(businessName);
        if (contactPhone       != null) profile.setContactPhone(contactPhone);
        if (gstNumber          != null) profile.setGstNumber(gstNumber);
        if (address            != null) profile.setAddress(address);

        // Feature 14: payout details
        if (bankAccountNumber  != null) profile.setBankAccountNumber(bankAccountNumber);
        if (bankIfsc           != null) profile.setBankIfsc(bankIfsc);
        if (upiId              != null) profile.setUpiId(upiId);
        if (payoutMethod       != null) profile.setPayoutMethod(payoutMethod);

        return profileRepo.save(profile);
    }

    // ── Events ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Event> getOrganizerEvents(Long organizerId) {
        return eventRepo.findByOrganizerIdOrderByEventDateAsc(organizerId);
    }

    // ── Revenue ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getOrganizerRevenue(Long organizerId) {
        List<Event> events = eventRepo.findByOrganizerIdOrderByEventDateAsc(organizerId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Event event : events) {
            List<Booking> bookings = bookingRepo.findByEventIdAndPaymentStatus(event.getId(), "paid");
            if (bookings.isEmpty()) continue;

            List<Map<String, Object>> bookingMaps = new ArrayList<>();
            for (Booking b : bookings) {
                Map<String, Object> bMap = new java.util.LinkedHashMap<>();
                bMap.put("id",              b.getId());
                bMap.put("tickets_booked",  b.getTicketsBooked());
                bMap.put("ticket_amount",   b.getTicketAmount());
                bMap.put("convenience_fee", b.getConvenienceFee());
                bMap.put("gst_amount",      b.getGstAmount());
                bMap.put("total_paid",      b.getTotalPaid());
                bMap.put("payment_status",  b.getPaymentStatus());
                bMap.put("booking_date",    b.getBookingDate());
                bookingMaps.add(bMap);
            }

            double evRevenue = bookings.stream()
                    .mapToDouble(b -> b.getTicketAmount() != null ? b.getTicketAmount() : 0.0).sum();

            Map<String, Object> evMap = new java.util.LinkedHashMap<>();
            evMap.put("event_id",      event.getId());
            evMap.put("event_title",   event.getTitle());
            evMap.put("event_date",    event.getEventDate());
            evMap.put("total_revenue", evRevenue);
            evMap.put("bookings",      bookingMaps);
            result.add(evMap);
        }
        return result;
    }

    // ── Stats ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getOrganizerStats(Long organizerId) {
        List<Event> events = eventRepo.findByOrganizerIdOrderByEventDateAsc(organizerId);
        long totalBookings = 0;
        long totalTickets  = 0;
        double totalRev    = 0.0;

        for (Event ev : events) {
            List<Booking> bookings = bookingRepo.findByEventIdAndPaymentStatus(ev.getId(), "paid");
            totalBookings += bookings.size();
            totalTickets  += bookings.stream().mapToLong(b -> b.getTicketsBooked() != null ? b.getTicketsBooked() : 0).sum();
            totalRev      += bookings.stream().mapToDouble(b -> b.getTicketAmount() != null ? b.getTicketAmount() : 0.0).sum();
        }

        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("total_events",   events.size());
        stats.put("total_bookings", totalBookings);
        stats.put("total_tickets",  totalTickets);
        stats.put("total_revenue",  totalRev);
        return stats;
    }

    // ── Attendees ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getEventAttendees(Long eventId, Long organizerId) {
        Event event = eventRepo.findByIdAndOrganizerId(eventId, organizerId).orElse(null);
        if (event == null) return null;

        List<Booking> bookings = bookingRepo.findByEventIdAndPaymentStatus(eventId, "paid");
        List<Map<String, Object>> attendees = new ArrayList<>();
        for (Booking b : bookings) {
            userRepo.findById(b.getUserId()).ifPresent(u -> {
                Map<String, Object> a = new java.util.LinkedHashMap<>();
                a.put("booking_id",     b.getId());
                a.put("user_name",      u.getName());
                a.put("user_email",     u.getEmail());
                a.put("tickets_booked", b.getTicketsBooked());
                a.put("selected_seats", b.getSelectedSeats());
                a.put("booking_date",   b.getBookingDate());
                a.put("checked_in",     b.getCheckedIn());
                attendees.add(a);
            });
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("event_id",    event.getId());
        result.put("event_title", event.getTitle());
        result.put("attendees",   attendees);
        result.put("total",       attendees.size());
        return result;
    }

    // ── Admin: all organizers ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllOrganizers(String status) {
        List<OrganizerProfile> profiles = (status != null && !status.isBlank())
                ? profileRepo.findByStatusOrderByCreatedAtDesc(status)
                : profileRepo.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (OrganizerProfile profile : profiles) {
            Map<String, Object> map = safeProfileMap(profile);
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

    // ── Safe map helpers ─────────────────────────────────────────────────────

    public Map<String, Object> safeProfileMap(OrganizerProfile profile) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id",                   profile.getId());
        map.put("user_id",              profile.getUserId());
        map.put("business_name",        profile.getBusinessName());
        map.put("contact_phone",        profile.getContactPhone());
        map.put("gst_number",           profile.getGstNumber());
        map.put("address",              profile.getAddress());
        map.put("status",               profile.getStatus());
        map.put("rejection_reason",     profile.getRejectionReason());
        map.put("bank_account_number",  profile.getBankAccountNumber());
        map.put("bank_ifsc",            profile.getBankIfsc());
        map.put("upi_id",               profile.getUpiId());
        map.put("payout_method",        profile.getPayoutMethod());
        map.put("created_at",           profile.getCreatedAt());
        map.put("updated_at",           profile.getUpdatedAt());
        return map;
    }

    @Transactional(readOnly = true)
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

    // ── Approve / Reject / Delete ─────────────────────────────────────────────

    @Transactional
    public OrganizerProfile approveOrganizer(Long profileId) {
        OrganizerProfile profile = profileRepo.findById(profileId).orElse(null);
        if (profile == null) return null;
        profile.setStatus("approved");
        profile.setRejectionReason(null);
        OrganizerProfile saved = profileRepo.save(profile);
        userRepo.findById(saved.getUserId()).ifPresent(user ->
            emailService.sendOrganizerApprovedEmail(user.getEmail(), saved.getBusinessName()));
        return saved;
    }

    @Transactional
    public OrganizerProfile rejectOrganizer(Long profileId, String reason) {
        OrganizerProfile profile = profileRepo.findById(profileId).orElse(null);
        if (profile == null) return null;
        profile.setStatus("rejected");
        profile.setRejectionReason(reason);
        OrganizerProfile saved = profileRepo.save(profile);
        userRepo.findById(saved.getUserId()).ifPresent(user ->
            emailService.sendOrganizerRejectedEmail(user.getEmail(), saved.getBusinessName(), reason));
        return saved;
    }

    @Transactional
    public boolean deleteOrganizer(Long profileId) {
        OrganizerProfile profile = profileRepo.findById(profileId).orElse(null);
        if (profile == null) return false;
        userRepo.deleteById(profile.getUserId());
        return true;
    }
}
