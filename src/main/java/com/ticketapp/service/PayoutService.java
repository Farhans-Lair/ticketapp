package com.ticketapp.service;

import com.ticketapp.entity.Booking;
import com.ticketapp.entity.Event;
import com.ticketapp.entity.OrganizerPayout;
import com.ticketapp.entity.OrganizerProfile;
import com.ticketapp.entity.User;
import com.ticketapp.repository.BookingRepository;
import com.ticketapp.repository.EventRepository;
import com.ticketapp.repository.OrganizerPayoutRepository;
import com.ticketapp.repository.OrganizerProfileRepository;
import com.ticketapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {

    private final OrganizerPayoutRepository payoutRepo;
    private final OrganizerProfileRepository profileRepo;
    private final EventRepository           eventRepo;
    private final BookingRepository         bookingRepo;
    private final UserRepository            userRepo;
    private final EmailService              emailService;

    // ── Organizer: request a payout ───────────────────────────────────────────

    @Transactional
    public OrganizerPayout requestPayout(Long organizerId, LocalDate fromDate, LocalDate toDate) {
        if (fromDate.isAfter(toDate))
            throw new RuntimeException("from_date must be before or equal to to_date.");

        OrganizerProfile profile = profileRepo.findByUserId(organizerId)
                .orElseThrow(() -> new RuntimeException("Organizer profile not found."));
        if (!"approved".equals(profile.getStatus()))
            throw new RuntimeException("Only approved organizers can request payouts.");
        if (profile.getPayoutMethod() == null)
            throw new RuntimeException("Please add bank or UPI details in your profile before requesting a payout.");

        // Guard: no overlapping pending/processing payout
        if (payoutRepo.existsOverlappingPayout(organizerId, fromDate, toDate))
            throw new RuntimeException("An overlapping payout request already exists for this date range.");

        // Collect event IDs owned by this organizer
        List<Long> eventIds = eventRepo.findByOrganizerIdOrderByEventDateAsc(organizerId)
                .stream().map(Event::getId).toList();
        if (eventIds.isEmpty())
            throw new RuntimeException("No events found for your account.");

        // Query bookings for the period
        List<Booking> bookings = bookingRepo.findBookingsForPayout(
                eventIds,
                fromDate.atStartOfDay(),
                toDate.plusDays(1).atStartOfDay());
        if (bookings.isEmpty())
            throw new RuntimeException("No paid bookings found in the selected date range.");

        // Net formula:
        //   organizer earns  = ticket_amount − discount_amount
        //   platform keeps   = convenience_fee + gst_amount
        double totalTicketAmt = bookings.stream()
                .mapToDouble(b -> b.getTicketAmount()   != null ? b.getTicketAmount()   : 0.0).sum();
        double totalDiscount  = bookings.stream()
                .mapToDouble(b -> b.getDiscountAmount() != null ? b.getDiscountAmount() : 0.0).sum();
        double platformFee    = bookings.stream()
                .mapToDouble(b -> (b.getConvenienceFee() != null ? b.getConvenienceFee() : 0.0)
                                + (b.getGstAmount()      != null ? b.getGstAmount()      : 0.0)).sum();
        double netAmount = totalTicketAmt - totalDiscount;

        if (netAmount <= 0)
            throw new RuntimeException("Net payout amount must be greater than zero.");

        OrganizerPayout payout = new OrganizerPayout();
        payout.setOrganizerId(organizerId);
        payout.setAmount(netAmount);
        payout.setFromDate(fromDate);
        payout.setToDate(toDate);
        payout.setStatus("requested");
        payout.setBookingCount(bookings.size());
        payout.setPlatformFee(platformFee);

        OrganizerPayout saved = payoutRepo.save(payout);
        log.info("Payout requested: organizerId={} amount={} bookings={}", organizerId, netAmount, bookings.size());

        // Notify organizer
        userRepo.findById(organizerId).ifPresent(u ->
            emailService.sendPayoutRequestedEmail(u, profile, saved));

        return saved;
    }

    // ── Organizer: list own payouts ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<OrganizerPayout> getOrganizerPayouts(Long organizerId) {
        return payoutRepo.findByOrganizerIdOrderByRequestedAtDesc(organizerId);
    }

    // ── Admin: list payouts ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllPayoutsForAdmin() {
        List<OrganizerPayout> all = payoutRepo.findAllByOrderByRequestedAtDesc();
        return all.stream().map(p -> {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("id",           p.getId());
            map.put("organizer_id", p.getOrganizerId());
            map.put("amount",       p.getAmount());
            map.put("from_date",    p.getFromDate());
            map.put("to_date",      p.getToDate());
            map.put("status",       p.getStatus());
            map.put("booking_count",p.getBookingCount());
            map.put("platform_fee", p.getPlatformFee());
            map.put("admin_note",   p.getAdminNote());
            map.put("requested_at", p.getRequestedAt());
            map.put("settled_at",   p.getSettledAt());
            // Enrich with organizer's business name and email
            profileRepo.findByUserId(p.getOrganizerId()).ifPresent(prof ->
                map.put("business_name", prof.getBusinessName()));
            userRepo.findById(p.getOrganizerId()).ifPresent(u ->
                map.put("organizer_email", u.getEmail()));
            return map;
        }).toList();
    }

    // ── Admin: mark as processing / paid ─────────────────────────────────────

    @Transactional
    public OrganizerPayout processPayout(Long payoutId, String razorpayPayoutId, String adminNote) {
        OrganizerPayout payout = payoutRepo.findById(payoutId)
                .orElseThrow(() -> new RuntimeException("Payout not found."));
        if (!"requested".equals(payout.getStatus()) && !"processing".equals(payout.getStatus()))
            throw new RuntimeException("Only requested or processing payouts can be marked as paid.");

        payout.setStatus("paid");
        payout.setRazorpayPayoutId(razorpayPayoutId);
        payout.setAdminNote(adminNote);
        payout.setSettledAt(LocalDateTime.now());

        OrganizerPayout saved = payoutRepo.save(payout);
        log.info("Payout processed: payoutId={} razorpayPayoutId={}", payoutId, razorpayPayoutId);

        // Notify organizer
        userRepo.findById(saved.getOrganizerId()).ifPresent(u ->
            emailService.sendPayoutProcessedEmail(u, saved));

        return saved;
    }

    // ── Admin: reject a payout request ───────────────────────────────────────

    @Transactional
    public OrganizerPayout rejectPayout(Long payoutId, String adminNote) {
        OrganizerPayout payout = payoutRepo.findById(payoutId)
                .orElseThrow(() -> new RuntimeException("Payout not found."));
        if (!"requested".equals(payout.getStatus()))
            throw new RuntimeException("Only requested payouts can be rejected.");

        payout.setStatus("rejected");
        payout.setAdminNote(adminNote);

        OrganizerPayout saved = payoutRepo.save(payout);
        log.info("Payout rejected: payoutId={}", payoutId);

        // Notify organizer
        userRepo.findById(saved.getOrganizerId()).ifPresent(u ->
            emailService.sendPayoutRejectedEmail(u, saved));

        return saved;
    }
}
