package com.ticketapp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.ticketapp.entity.Booking;
import com.ticketapp.entity.CancellationPolicy;
import com.ticketapp.entity.Event;
import com.ticketapp.repository.BookingRepository;
import com.ticketapp.repository.CancellationPolicyRepository;
import com.ticketapp.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Cancellation + refund business logic.
 *
 * Tier matching rules:
 *   Sort tiers DESC by hours_before.
 *   Take the first tier where hoursUntilEvent >= tier.hours_before.
 *   >= 72 h  → HIGH tier : full refund minus 5% cancellation charge (+5% GST on charge)
 *   <  72 h  → LOW  tier : partial gross refund based on refund_percent, minus charge
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CancellationService {

    private static final double CANCELLATION_FEE_RATE     = 0.05;
    private static final double CANCELLATION_FEE_GST_RATE = 0.05;
    private static final int    HIGH_TIER_CUTOFF_HOURS    = 72;

    private final BookingRepository            bookingRepo;
    private final EventRepository              eventRepo;
    private final CancellationPolicyRepository policyRepo;
    private final SeatService                  seatService;
    private final ObjectMapper                 objectMapper;

    @Value("${razorpay.key-id:}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret:}")
    private String razorpayKeySecret;

    // ── Policy CRUD ───────────────────────────────────────────────────────────

    @Transactional
    public CancellationPolicy upsertPolicy(Long organizerId, Long eventId,
                                           List<Map<String, Object>> tiers,
                                           boolean isCancellationAllowed) {
        Event event = eventRepo.findById(eventId).orElse(null);
        if (event == null || !organizerId.equals(event.getOrganizerId()))
            throw new RuntimeException("Event not found or you do not own this event.");

        if (tiers == null || tiers.isEmpty())
            throw new RuntimeException("At least one refund tier is required.");

        for (Map<String, Object> tier : tiers) {
            Object hb = tier.get("hours_before");
            Object rp = tier.get("refund_percent");
            if (!(hb instanceof Number) || !(rp instanceof Number))
                throw new RuntimeException("Each tier must have hours_before and refund_percent.");
            double hours  = ((Number) hb).doubleValue();
            double refPct = ((Number) rp).doubleValue();
            if (hours < 0 || refPct < 0 || refPct > 100)
                throw new RuntimeException("hours_before must be >= 0 and refund_percent 0-100.");
        }

        String tiersJson;
        try { tiersJson = objectMapper.writeValueAsString(tiers); }
        catch (Exception e) { throw new RuntimeException("Failed to serialize tiers."); }

        CancellationPolicy policy = policyRepo.findByEventId(eventId)
                .orElseGet(CancellationPolicy::new);
        policy.setEventId(eventId);
        policy.setOrganizerId(organizerId);
        policy.setTiers(tiersJson);
        policy.setIsCancellationAllowed(isCancellationAllowed);
        policyRepo.save(policy);
        log.info("Cancellation policy upserted: organizerId={} eventId={}", organizerId, eventId);
        return policy;
    }

    public Optional<CancellationPolicy> getPolicy(Long eventId) {
        return policyRepo.findByEventId(eventId);
    }

    // ── Preview ───────────────────────────────────────────────────────────────

    public Map<String, Object> previewCancellation(Long bookingId, Long userId) {
        Booking booking = bookingRepo.findByIdAndUserId(bookingId, userId).orElse(null);
        if (booking == null)
            throw new RuntimeException("Booking not found.");
        if (!"paid".equals(booking.getPaymentStatus()))
            throw new RuntimeException("Only paid bookings can be cancelled.");
        if (!"active".equals(booking.getCancellationStatus()))
            throw new RuntimeException("Booking is already " + booking.getCancellationStatus() + ".");

        CancellationPolicy policy = policyRepo.findByEventId(booking.getEventId()).orElse(null);
        if (policy == null || Boolean.FALSE.equals(policy.getIsCancellationAllowed()))
            return Map.of("cancellationAllowed", false,
                "reason", "The organizer has not enabled cancellations for this event.",
                "refundAmount", 0.0, "refundPercent", 0.0);

        Event event = eventRepo.findById(booking.getEventId()).orElse(null);
        if (event == null)
            throw new RuntimeException("Event not found.");

        double hoursUntilEvent = Duration.between(
                LocalDateTime.now(), event.getEventDate()).toMinutes() / 60.0;

        if (hoursUntilEvent <= 0)
            return Map.of("cancellationAllowed", false,
                "reason", "The event has already started or passed.",
                "refundAmount", 0.0, "refundPercent", 0.0);

        List<Map<String, Object>> tiers = parseTiers(policy.getTiers());
        Map<String, Object> tier = matchTier(tiers, hoursUntilEvent);
        if (tier == null)
            return Map.of("cancellationAllowed", false,
                "reason", "No matching refund tier for the current time.",
                "refundAmount", 0.0, "refundPercent", 0.0);

        Map<String, Object> bd = computeBreakdown(booking, tier);

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("cancellationAllowed",  true);
        preview.put("refundAmount",         bd.get("refund_to_user"));
        preview.put("refundPercent",        ((Number) tier.get("refund_percent")).doubleValue());
        preview.put("cancellationFee",      bd.get("cancellation_fee"));
        preview.put("cancellationFeeGst",   bd.get("cancellation_fee_gst"));
        preview.put("isHighTier",           bd.get("isHighTier"));
        preview.put("appliedTierHours",     bd.get("applied_tier_hours"));
        preview.put("hoursUntilEvent",      (int) hoursUntilEvent);
        preview.put("totalPaid",            booking.getTotalPaid());
        preview.put("ticketAmount",         booking.getTicketAmount());
        preview.put("convenienceFee",       booking.getConvenienceFee());
        preview.put("policy",               tiers);
        return preview;
    }

    // ── Cancel + Refund ───────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> cancelBooking(Long bookingId, Long userId) {
        Map<String, Object> preview = previewCancellation(bookingId, userId);
        if (Boolean.FALSE.equals(preview.get("cancellationAllowed")))
            throw new RuntimeException((String) preview.get("reason"));

        Booking booking = bookingRepo.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new RuntimeException("Booking not found."));
        if (!"active".equals(booking.getCancellationStatus()))
            throw new RuntimeException("Booking already " + booking.getCancellationStatus() + ".");

        // ── 1. Restore event available tickets ────────────────────────────────
        Event event = eventRepo.findById(booking.getEventId())
                .orElseThrow(() -> new RuntimeException("Event not found."));
        event.setAvailableTickets(event.getAvailableTickets() + booking.getTicketsBooked());
        eventRepo.save(event);

        // ── 2. Release seats back to 'available' ──────────────────────────────
        // parseSelectedSeats() handles BOTH formats:
        //   NEW  (correct) : ["E4","E6","E5"]  — from fixed BookingService
        //   LEGACY (broken): [E4, E6, E5]      — from old List.toString()
        // This ensures existing DB rows stored with the old broken format
        // also get their seats released correctly when cancelled.
        List<String> seats = parseSelectedSeats(booking.getSelectedSeats(), bookingId);
        if (!seats.isEmpty()) {
            seatService.releaseSeats(booking.getEventId(), seats);
            log.info("Seats released for bookingId={} seats={}", bookingId, seats);
        }

        double refundAmount  = ((Number) preview.get("refundAmount")).doubleValue();
        double cancFee       = ((Number) preview.get("cancellationFee")).doubleValue();
        double cancFeeGst    = ((Number) preview.get("cancellationFeeGst")).doubleValue();
        int    appliedHours  = ((Number) preview.get("appliedTierHours")).intValue();
        double refundPercent = ((Number) preview.get("refundPercent")).doubleValue();
        boolean isHighTier   = (boolean) preview.get("isHighTier");

        String refundId   = null;
        String cancStatus = "cancelled";

        if (refundAmount > 0 && booking.getRazorpayPaymentId() != null) {
            try {
                JSONObject refundReq = new JSONObject();
                refundReq.put("amount", (long) Math.round(refundAmount * 100));
                refundReq.put("notes", new JSONObject(Map.of(
                    "booking_id", String.valueOf(bookingId),
                    "reason", "Customer cancellation")));

                RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
                com.razorpay.Refund refund = client.payments.refund(
                        booking.getRazorpayPaymentId(), refundReq);
                refundId   = refund.get("id");
                cancStatus = "refund_pending";
                log.info("Razorpay refund initiated: bookingId={} refundId={} amount={}",
                        bookingId, refundId, refundAmount);
            } catch (RazorpayException e) {
                log.error("Razorpay refund failed (booking still cancelled): bookingId={} error={}",
                        bookingId, e.getMessage());
            }
        }

        booking.setCancellationStatus(cancStatus);
        booking.setRefundAmount(refundAmount);
        booking.setRazorpayRefundId(refundId);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancellationFee(cancFee);
        booking.setCancellationFeeGst(cancFeeGst);
        booking.setAppliedTierHours(appliedHours);
        bookingRepo.save(booking);

        log.info("Booking cancelled: bookingId={} userId={} refundAmount={} status={}",
                bookingId, userId, refundAmount, cancStatus);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("booking",            booking);
        result.put("refundAmount",       refundAmount);
        result.put("refundPercent",      refundPercent);
        result.put("cancellationFee",    cancFee);
        result.put("cancellationFeeGst", cancFeeGst);
        result.put("isHighTier",         isHighTier);
        result.put("cancellationStatus", cancStatus);
        result.put("razorpay_refund_id", refundId != null ? refundId : "");
        return result;
    }

    /** Called by Razorpay refund webhook to mark refund as complete */
    @Transactional
    public Optional<Booking> markRefundComplete(String razorpayRefundId) {
        Optional<Booking> opt = bookingRepo.findByRazorpayRefundId(razorpayRefundId);
        opt.ifPresent(b -> {
            b.setCancellationStatus("refunded");
            bookingRepo.save(b);
            log.info("Refund marked complete: bookingId={} refundId={}", b.getId(), razorpayRefundId);
        });
        return opt;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Parses the selected_seats column into a List<String>, handling two formats:
     *
     * Format 1 — CORRECT JSON (new bookings after BookingService fix):
     *   ["E4","E6","E5"]  → ["E4", "E6", "E5"]
     *
     * Format 2 — LEGACY broken format (old bookings stored via List.toString()):
     *   [E4, E6, E5]  → Jackson throws "Unrecognized token 'E4'"
     *   Fallback: strip brackets, split on comma, trim whitespace → ["E4","E6","E5"]
     *
     * This ensures existing DB rows stored with the broken format also get
     * their seats released correctly when cancelled.
     */
    private List<String> parseSelectedSeats(String raw, Long bookingId) {
        if (raw == null || raw.isBlank() || raw.equals("[]")) return List.of();

        // ── Attempt 1: standard JSON parse ────────────────────────────────────
        try {
            List<String> seats = objectMapper.readValue(
                    raw, new TypeReference<List<String>>() {});
            if (!seats.isEmpty()) return seats;
        } catch (Exception jsonEx) {
            log.debug("JSON parse failed for selectedSeats bookingId={}, trying legacy fallback: {}",
                    bookingId, jsonEx.getMessage());
        }

        // ── Attempt 2: legacy fallback — strip [ ], split on comma, trim ─────
        // Handles: [E4, E6, E5]  or  [B4, B5]  etc.
        try {
            String stripped = raw.trim();
            if (stripped.startsWith("[")) stripped = stripped.substring(1);
            if (stripped.endsWith("]"))   stripped = stripped.substring(0, stripped.length() - 1);

            if (stripped.isBlank()) return List.of();

            List<String> seats = Arrays.stream(stripped.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toList());

            if (!seats.isEmpty()) {
                log.info("Parsed legacy selectedSeats for bookingId={} seats={}", bookingId, seats);
                return seats;
            }
        } catch (Exception fallbackEx) {
            log.error("Legacy seat parse also failed for bookingId={}: {}",
                    bookingId, fallbackEx.getMessage());
        }

        log.warn("Could not parse selectedSeats for bookingId={} raw='{}' — seats will NOT be released",
                bookingId, raw);
        return List.of();
    }

    private List<Map<String, Object>> parseTiers(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse cancellation tiers.");
        }
    }

    private Map<String, Object> matchTier(List<Map<String, Object>> tiers, double hoursUntilEvent) {
        return tiers.stream()
            .sorted(Comparator.comparingDouble(
                (Map<String, Object> t) -> ((Number) t.get("hours_before")).doubleValue())
                .reversed())
            .filter(t -> hoursUntilEvent >= ((Number) t.get("hours_before")).doubleValue())
            .findFirst()
            .orElse(null);
    }

    private Map<String, Object> computeBreakdown(Booking booking, Map<String, Object> tier) {
        double ticketAmt   = booking.getTicketAmount();
        double convFee     = booking.getConvenienceFee();
        double totalPaid   = booking.getTotalPaid();
        double cancFee     = round2((ticketAmt + convFee) * CANCELLATION_FEE_RATE);
        double cancFeeGst  = round2(cancFee * CANCELLATION_FEE_GST_RATE);
        double totalCharge = cancFee + cancFeeGst;
        int    tierHours   = ((Number) tier.get("hours_before")).intValue();
        boolean isHighTier = tierHours >= HIGH_TIER_CUTOFF_HOURS;

        double refundToUser;
        if (isHighTier) {
            refundToUser = round2(Math.max(0, totalPaid - totalCharge));
        } else {
            double refPct      = ((Number) tier.get("refund_percent")).doubleValue();
            double grossRefund = round2(ticketAmt * (refPct / 100.0));
            refundToUser = round2(Math.max(0, grossRefund - totalCharge));
        }

        return Map.of(
            "cancellation_fee",     cancFee,
            "cancellation_fee_gst", cancFeeGst,
            "refund_to_user",       refundToUser,
            "applied_tier_hours",   tierHours,
            "isHighTier",           isHighTier
        );
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
