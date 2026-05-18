package com.ticketapp.service;

import com.ticketapp.entity.Booking;
import com.ticketapp.entity.Event;
import com.ticketapp.entity.User;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SmsService — Twilio SMS with automatic sender caching and Indian carrier retry.
 *
 * Mirrors TBA2's sms.services.js exactly:
 *  - Lazy client init (only when TWILIO_ACCOUNT_SID + TWILIO_AUTH_TOKEN are set)
 *  - In-memory senderCache maps normalised number → sender that worked
 *  - On first send, lets Messaging Service auto-pick sender; caches the winner
 *  - Indian carrier block codes (30044, 21606, 21408) trigger a retry via
 *    Messaging Service with the bad sender evicted from cache
 *  - sendBookingConfirmationSms() and sendCancellationSms() are fire-and-forget
 *    (callers run them on a separate thread — non-fatal on failure)
 *
 * Required env vars:
 *   TWILIO_ACCOUNT_SID           — from https://console.twilio.com
 *   TWILIO_AUTH_TOKEN            — from https://console.twilio.com
 *   TWILIO_MESSAGING_SERVICE_SID — MG... from Messaging Service in Console
 *
 * Optional:
 *   APP_BASE_URL — base URL for deep-links, e.g. https://ticketverse.in
 */
@Service
@Slf4j
public class SmsService {

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.messaging-service-sid:}")
    private String messagingServiceSid;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    /** true once Twilio.init() has been called successfully. */
    private boolean twilioInitialised = false;

    /**
     * In-memory sender cache: normalised destination → sender that worked.
     * e.g. { "+917028178725" → "+15005550006" }
     * Persists for the lifetime of the Spring application context.
     */
    private final ConcurrentHashMap<String, String> senderCache = new ConcurrentHashMap<>();

    /** Twilio error codes where Indian carriers block the sender. */
    private static final Set<Integer> INDIA_BLOCK_CODES = Set.of(30044, 21606, 21408);

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy");

    @PostConstruct
    void init() {
        if (accountSid != null && !accountSid.isBlank()
                && authToken != null && !authToken.isBlank()) {
            Twilio.init(accountSid, authToken);
            twilioInitialised = true;
            log.info("[SMS] Twilio initialised.");
        } else {
            log.warn("[SMS] Twilio credentials not configured — SMS will be skipped.");
        }
    }

    // ── Booking Confirmation SMS ──────────────────────────────────────────────

    /**
     * Sends a booking confirmation SMS to the user's phone.
     * Mirrors TBA2's sendBookingConfirmationSMS().
     * No-op if the user has no phone number.
     */
    public void sendBookingConfirmationSms(User user, Booking booking, Event event) {
        if (user == null || user.getPhone() == null || user.getPhone().isBlank()) {
            log.warn("[SMS] Booking confirmation SMS skipped — no phone for userId={}",
                    user != null ? user.getId() : "null");
            return;
        }

        String dateStr = event.getEventDate() != null
                ? event.getEventDate().format(DATE_FMT) : "TBA";
        String bookingsUrl = appBaseUrl.replaceAll("/$", "") + "/my-bookings";

        String body = "Booking Confirmed!\n"
                + "Event: " + event.getTitle() + "\n"
                + "Date: " + dateStr + "\n"
                + "Venue: " + (event.getLocation() != null ? event.getLocation() : "TBA") + "\n"
                + "Tickets: " + booking.getTicketsBooked() + "\n"
                + "Total Paid: Rs." + String.format("%.2f", booking.getTotalPaid() != null ? booking.getTotalPaid() : 0.0) + "\n"
                + "Booking ID: #" + booking.getId() + "\n"
                + "View ticket: " + bookingsUrl + "\n"
                + "- TicketVerse";

        sendSms(user.getPhone(), body);
    }

    // ── Cancellation SMS ──────────────────────────────────────────────────────

    /**
     * Sends a booking cancellation SMS to the user's phone.
     * Mirrors TBA2's sendCancellationSMS().
     * No-op if the user has no phone number.
     */
    public void sendCancellationSms(User user, Booking booking, Event event,
                                    double refundAmount) {
        if (user == null || user.getPhone() == null || user.getPhone().isBlank()) {
            log.warn("[SMS] Cancellation SMS skipped — no phone for userId={}",
                    user != null ? user.getId() : "null");
            return;
        }

        String bookingsUrl = appBaseUrl.replaceAll("/$", "") + "/my-bookings";

        String body = "Booking Cancelled\n"
                + "Event: " + event.getTitle() + "\n"
                + "Booking ID: #" + booking.getId() + "\n"
                + "Refund: Rs." + String.format("%.2f", refundAmount) + "\n"
                + "Refunds processed within 5-7 business days.\n"
                + "View bookings: " + bookingsUrl + "\n"
                + "- TicketVerse";

        sendSms(user.getPhone(), body);
    }

    // ── Core send with automatic retry and sender caching ────────────────────

    /**
     * Sends an SMS via Twilio Messaging Service with sender caching.
     *
     * Attempt 1: uses the cached sender if available; otherwise lets the
     *            Messaging Service auto-pick and caches the result.
     *
     * Attempt 2 (India carrier block only): evicts the bad cached sender,
     *            retries via Messaging Service so Twilio picks a different
     *            sender from the pool (short code instead of US long code).
     */
    void sendSms(String toPhone, String body) {
        if (!twilioInitialised) {
            log.warn("[SMS] Skipped — Twilio not initialised. toPhone={}", toPhone);
            return;
        }
        if (messagingServiceSid == null || messagingServiceSid.isBlank()) {
            log.warn("[SMS] Skipped — TWILIO_MESSAGING_SERVICE_SID not configured.");
            return;
        }

        String normalised = normalisePhone(toPhone);
        boolean isIndia   = isIndianNumber(normalised);
        String  cached    = senderCache.get(normalised);

        // ── Attempt 1 ─────────────────────────────────────────────────────────
        try {
            Message msg;
            if (cached != null && isIndia) {
                // Re-use the sender that worked before for this number
                msg = Message.creator(
                        new PhoneNumber(normalised),
                        new PhoneNumber(cached),
                        body).create();
            } else {
                // Let Messaging Service pick the best sender
                msg = Message.creator(
                        new PhoneNumber(normalised),
                        messagingServiceSid,
                        body).create();
            }

            // Cache the sender that worked
            if (msg.getFrom() != null && !senderCache.containsKey(normalised)) {
                senderCache.put(normalised, msg.getFrom().toString());
                log.info("[SMS] Sender cached: to={} sender={}", normalised, msg.getFrom());
            }

            log.info("[SMS] Sent: sid={} to={} via={}", msg.getSid(), normalised,
                    cached != null ? "cached:" + cached : "MessagingService");
            return;

        } catch (ApiException e) {
            int code = e.getCode();
            boolean isCarrierBlock = INDIA_BLOCK_CODES.contains(code);

            if (!isIndia || !isCarrierBlock) {
                log.error("[SMS] Send failed: to={} code={} error={}", normalised, code, e.getMessage());
                return;
            }

            // ── Attempt 2: India carrier block — retry via Messaging Service ──
            log.warn("[SMS] India carrier blocked sender — retrying. to={} blockedSender={} code={}",
                    normalised, cached != null ? cached : "MessagingService", code);
            senderCache.remove(normalised);
        }

        try {
            Message msg2 = Message.creator(
                    new PhoneNumber(normalised),
                    messagingServiceSid,
                    body).create();

            if (msg2.getFrom() != null) {
                senderCache.put(normalised, msg2.getFrom().toString());
                log.info("[SMS] Sender cached after retry: to={} sender={}", normalised, msg2.getFrom());
            }
            log.info("[SMS] Sent on retry: sid={} to={}", msg2.getSid(), normalised);

        } catch (ApiException retryErr) {
            log.error("[SMS] Retry also failed: to={} code={} error={}",
                    normalised, retryErr.getCode(), retryErr.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** 10-digit Indian numbers get +91 prepended. Already-formatted numbers pass through. */
    private String normalisePhone(String phone) {
        String n = phone.trim().replaceAll("\\s+", "");
        if (n.matches("^\\d{10}$")) n = "+91" + n;
        return n;
    }

    /** +91XXXXXXXXXX with length 13 is an Indian mobile number. */
    private boolean isIndianNumber(String normalised) {
        return normalised.startsWith("+91") && normalised.length() == 13;
    }
}
