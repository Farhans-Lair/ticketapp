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
import java.util.LinkedHashMap;
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

    private static final double PLATFORM_FEE_RATE = 0.10;

    // ── Settlement Calculation ────────────────────────────────────────────────

    /**
     * Calculates outstanding gross revenue, platform fee (10%), and net payout
     * for an organizer — optionally scoped to a single event.
     *
     * Mirrors TBA2's calculateSettlement(organizerId, eventId):
     *  - Counts only paid bookings where cancellation_status IN ('active','refund_pending')
     *  - gross       = SUM(ticket_amount) across those bookings
     *  - platform_fee = 10% of gross
     *  - net          = gross − platform_fee
     *
     * Used by:
     *  - GET /payouts/admin/settlement/{organizerId}?eventId=
     *  - requestPayout() (preview before creating the payout record)
     *
     * @param organizerId the organizer's user ID
     * @param eventId     optional; null means all events owned by this organizer
     * @return map with keys: gross, platform_fee, net, bookings (count)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> calculateSettlement(Long organizerId, Long eventId) {
        // Collect event IDs owned by this organizer (optionally filtered)
        List<Long> eventIds;
        if (eventId != null) {
            eventIds = List.of(eventId);
        } else {
            eventIds = eventRepo.findByOrganizerIdOrderByEventDateAsc(organizerId)
                    .stream().map(Event::getId).toList();
        }

        if (eventIds.isEmpty()) {
            return settlementMap(0.0, 0.0, 0.0, 0);
        }

        // Paid bookings with active or refund_pending cancellation status
        List<Booking> bookings = bookingRepo.findSettlementBookings(
                eventIds, List.of("active", "refund_pending"));

        double gross      = bookings.stream()
                .mapToDouble(b -> b.getTicketAmount() != null ? b.getTicketAmount() : 0.0)
                .sum();
        gross = round2(gross);

        double platformFee = round2(gross * PLATFORM_FEE_RATE);
        double net         = round2(gross - platformFee);

        log.info("Settlement calculated: organizerId={} eventId={} gross={} platformFee={} net={} bookings={}",
                organizerId, eventId, gross, platformFee, net, bookings.size());

        return settlementMap(gross, platformFee, net, bookings.size());
    }

    private Map<String, Object> settlementMap(double gross, double platformFee,
                                              double net, int bookingCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gross",        gross);
        m.put("platform_fee", platformFee);
        m.put("net",          net);
        m.put("bookings",     bookingCount);
        return m;
    }

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

        if (payoutRepo.existsOverlappingPayout(organizerId, fromDate, toDate))
            throw new RuntimeException("An overlapping payout request already exists for this date range.");

        List<Long> eventIds = eventRepo.findByOrganizerIdOrderByEventDateAsc(organizerId)
                .stream().map(Event::getId).toList();
        if (eventIds.isEmpty())
            throw new RuntimeException("No events found for your account.");

        List<Booking> bookings = bookingRepo.findBookingsForPayout(
                eventIds,
                fromDate.atStartOfDay(),
                toDate.plusDays(1).atStartOfDay());
        if (bookings.isEmpty())
            throw new RuntimeException("No paid bookings found in the selected date range.");

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
            Map<String, Object> map = new LinkedHashMap<>();
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
            profileRepo.findByUserId(p.getOrganizerId()).ifPresent(prof ->
                map.put("business_name", prof.getBusinessName()));
            userRepo.findById(p.getOrganizerId()).ifPresent(u ->
                map.put("organizer_email", u.getEmail()));
            return map;
        }).toList();
    }

    // ── Admin: mark as paid ───────────────────────────────────────────────────

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

        userRepo.findById(saved.getOrganizerId()).ifPresent(u ->
            emailService.sendPayoutRejectedEmail(u, saved));

        return saved;
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
