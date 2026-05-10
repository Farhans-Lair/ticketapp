package com.ticketapp.service;

import com.ticketapp.entity.Booking;
import com.ticketapp.entity.Event;
import com.ticketapp.entity.OrganizerPayout;
import com.ticketapp.entity.OrganizerProfile;
import com.ticketapp.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@ticketverse.com}")
    private String fromAddress;

    @Value("${admin.email:}")
    private String adminEmail;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy hh:mm a");

    // ── OTP Emails ────────────────────────────────────────────────────────────

    @Async
    public void sendOtpEmail(String toEmail, String otp, String purpose) {
        boolean isSignup = "signup".equals(purpose);
        String subject   = isSignup ? "TicketVerse – Verify your email" : "TicketVerse – Your login code";
        String action    = isSignup ? "complete your registration" : "log in";

        String html = """
            <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:32px;
                        background:#0f0f1a;color:#ffffff;border-radius:12px;">
              <div style="font-size:22px;font-weight:700;color:#6c63ff;margin-bottom:8px;">TicketVerse</div>
              <p style="margin:0 0 16px;">Use the code below to %s:</p>
              <div style="font-size:36px;font-weight:700;letter-spacing:12px;text-align:center;
                          background:#1a1a2e;padding:20px;border-radius:8px;color:#a78bfa;">
                %s
              </div>
              <p style="margin:16px 0 0;color:#888;font-size:13px;">
                This code expires in <strong>10 minutes</strong>. Do not share it with anyone.
              </p>
            </div>
            """.formatted(action, otp);

        sendHtml(toEmail, subject, html);
    }

    // ── Ticket Confirmation Email ─────────────────────────────────────────────

    @Async
    public void sendTicketEmail(User user, Booking booking, Event event,
                                byte[] ticketPdfBytes) {
        String subject = "Your Ticket – " + event.getTitle();

        String seatsHtml = "";
        if (booking.getSelectedSeats() != null
                && !booking.getSelectedSeats().equals("[]")
                && !booking.getSelectedSeats().isBlank()) {
            String seats = booking.getSelectedSeats()
                    .replace("[", "").replace("]", "").replace("\"", "");
            seatsHtml = "<tr><td style='padding:6px 0;color:#888;'>Seats</td>"
                      + "<td style='padding:6px 0;'>" + seats + "</td></tr>";
        }

        String dateStr = event.getEventDate() != null
                ? event.getEventDate().format(DATE_FMT) : "TBA";
        String attachmentNote = ticketPdfBytes != null
            ? "<p style=\"color:#a78bfa;font-size:13px;\">📎 Your ticket PDF is attached to this email.</p>"
            : "<p style=\"color:#888;font-size:13px;\">Log in to TicketVerse to download your ticket PDF.</p>";

        String html = """
            <div style="font-family:Arial,sans-serif;max-width:560px;margin:auto;padding:32px;
                        background:#0f0f1a;color:#ffffff;border-radius:12px;">
              <div style="font-size:22px;font-weight:700;color:#6c63ff;margin-bottom:4px;">TicketVerse</div>
              <h2 style="margin:0 0 24px;color:#a78bfa;">Booking Confirmed! 🎉</h2>
              <p>Hi <strong>%s</strong>, your booking is confirmed.</p>
              <table style="width:100%%;border-collapse:collapse;margin-top:16px;">
                <tr><td style="padding:6px 0;color:#888;">Event</td>
                    <td style="padding:6px 0;"><strong>%s</strong></td></tr>
                <tr><td style="padding:6px 0;color:#888;">Date</td>
                    <td style="padding:6px 0;">%s</td></tr>
                <tr><td style="padding:6px 0;color:#888;">Location</td>
                    <td style="padding:6px 0;">%s</td></tr>
                <tr><td style="padding:6px 0;color:#888;">Tickets</td>
                    <td style="padding:6px 0;">%d</td></tr>
                %s
                <tr><td style="padding:6px 0;color:#888;">Total Paid</td>
                    <td style="padding:6px 0;font-weight:700;color:#6c63ff;">₹%.2f</td></tr>
              </table>
              %s
            </div>
            """.formatted(
                user.getName(), event.getTitle(), dateStr,
                event.getLocation() != null ? event.getLocation() : "TBA",
                booking.getTicketsBooked(), seatsHtml,
                booking.getTotalPaid(), attachmentNote);

        if (ticketPdfBytes != null) {
            sendHtmlWithAttachment(user.getEmail(), subject, html, ticketPdfBytes,
                "ticket-booking-" + booking.getId() + ".pdf", "application/pdf");
        } else {
            sendHtml(user.getEmail(), subject, html);
        }
    }

    // ── Feature 12: Event Reminder Email ─────────────────────────────────────

    /**
     * Sent 24 hours before the event to all paid, active booking holders.
     * Includes a Google Calendar link so users can add the event to their calendar.
     */
    @Async
    public void sendReminderEmail(User user, Booking booking, Event event) {
        String dateStr = event.getEventDate() != null
                ? event.getEventDate().format(DATE_FMT) : "TBA";

        String seatsNote = "";
        if (booking.getSelectedSeats() != null && !booking.getSelectedSeats().equals("[]")) {
            String seats = booking.getSelectedSeats()
                    .replace("[","").replace("]","").replace("\"","");
            seatsNote = "<p style='color:#888;font-size:13px;'>Your seats: <strong style='color:#f0eee8;'>" + seats + "</strong></p>";
        }

        // Build Google Calendar URL
        String gcalTitle = java.net.URLEncoder.encode(event.getTitle(), java.nio.charset.StandardCharsets.UTF_8);
        String gcalDate  = event.getEventDate() != null
                ? event.getEventDate().format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                : "";
        String gcalEnd   = event.getEventDate() != null
                ? event.getEventDate().plusHours(2).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                : "";
        String gcalLocation = event.getLocation() != null
                ? java.net.URLEncoder.encode(event.getLocation(), java.nio.charset.StandardCharsets.UTF_8) : "";
        String calLink = "https://calendar.google.com/calendar/render?action=TEMPLATE"
                + "&text=" + gcalTitle
                + "&dates=" + gcalDate + "/" + gcalEnd
                + "&location=" + gcalLocation;

        String html = """
            <div style="font-family:Arial,sans-serif;max-width:560px;margin:auto;padding:32px;
                        background:#0f0f1a;color:#ffffff;border-radius:12px;">
              <div style="font-size:22px;font-weight:700;color:#6c63ff;margin-bottom:4px;">TicketVerse</div>
              <h2 style="margin:0 0 16px;color:#a78bfa;">Your event is tomorrow! 🎟️</h2>
              <p>Hi <strong>%s</strong>, this is your reminder for:</p>
              <div style="background:#1a1a2e;border-radius:10px;padding:20px;margin:16px 0;">
                <div style="font-size:1.2rem;font-weight:700;margin-bottom:8px;">%s</div>
                <div style="color:#888;font-size:0.9rem;">📅 %s</div>
                <div style="color:#888;font-size:0.9rem;margin-top:4px;">📍 %s</div>
                <div style="color:#888;font-size:0.9rem;margin-top:4px;">🎟 %d ticket(s) · Booking #%d</div>
              </div>
              %s
              <div style="margin-top:20px;display:flex;gap:12px;flex-wrap:wrap;">
                <a href="%s" target="_blank"
                   style="display:inline-block;padding:10px 20px;background:#6c63ff;color:#fff;
                          border-radius:8px;text-decoration:none;font-size:0.85rem;font-weight:600;">
                  + Add to Google Calendar
                </a>
                <a href="/my-bookings"
                   style="display:inline-block;padding:10px 20px;background:#1a1a2e;color:#a78bfa;
                          border:1px solid rgba(124,106,247,0.4);border-radius:8px;
                          text-decoration:none;font-size:0.85rem;font-weight:600;">
                  Download Ticket
                </a>
              </div>
              <p style="margin-top:20px;color:#888;font-size:12px;">
                See you there! — The TicketVerse Team
              </p>
            </div>
            """.formatted(
                user.getName(), event.getTitle(), dateStr,
                event.getLocation() != null ? event.getLocation() : "TBA",
                booking.getTicketsBooked(), booking.getId(),
                seatsNote, calLink);

        sendHtml(user.getEmail(), "Reminder: " + event.getTitle() + " is tomorrow!", html);
    }

    // ── Organizer approval/rejection emails ───────────────────────────────────

    @Async
    public void sendOrganizerApplicationReceivedEmail(String toEmail,
                                                       String organizerName,
                                                       String businessName) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:32px;
                        background:#0f0f1a;color:#ffffff;border-radius:12px;">
              <div style="font-size:22px;font-weight:700;color:#6c63ff;margin-bottom:8px;">TicketVerse</div>
              <h2 style="color:#a78bfa;">Application Received 🎉</h2>
              <p>Hi <strong>%s</strong>,</p>
              <p style="margin-top:12px;">
                Thank you for registering <strong>%s</strong> as an organizer on TicketVerse.
                Your application is now <strong>pending admin review</strong>.
              </p>
              <p style="margin-top:12px;">What happens next:</p>
              <ol style="margin-top:8px;padding-left:20px;line-height:1.9;">
                <li>Our team will review your business details.</li>
                <li>You will receive an approval or rejection email within 1–2 business days.</li>
                <li>Once approved you can log in and start creating events immediately.</li>
              </ol>
            </div>
            """.formatted(organizerName, businessName);

        sendHtml(toEmail, "TicketVerse – Organizer Application Received", html);
    }

    @Async
    public void sendAdminNewOrganizerNotification(String adminEmailAddr,
                                                   String organizerName,
                                                   String organizerEmail,
                                                   String businessName) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:32px;
                        background:#0f0f1a;color:#ffffff;border-radius:12px;">
              <div style="font-size:22px;font-weight:700;color:#6c63ff;margin-bottom:8px;">TicketVerse Admin</div>
              <h2 style="color:#f5c842;">New Organizer Application ⏳</h2>
              <table style="width:100%%;border-collapse:collapse;margin-top:16px;">
                <tr><td style="padding:6px 0;color:#888;">Business</td><td><strong>%s</strong></td></tr>
                <tr><td style="padding:6px 0;color:#888;">Contact</td><td>%s</td></tr>
                <tr><td style="padding:6px 0;color:#888;">Email</td><td>%s</td></tr>
              </table>
              <p style="margin-top:16px;">Log in to the admin dashboard to approve or reject.</p>
            </div>
            """.formatted(businessName, organizerName, organizerEmail);

        sendHtml(adminEmailAddr, "TicketVerse – New Organizer Application: " + businessName, html);
    }

    @Async
    public void sendOrganizerApprovedEmail(String toEmail, String businessName) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:32px;
                        background:#0f0f1a;color:#ffffff;border-radius:12px;">
              <div style="font-size:22px;font-weight:700;color:#6c63ff;margin-bottom:8px;">TicketVerse</div>
              <h2 style="color:#4ade80;">Your Organizer Account is Approved! ✅</h2>
              <p>Congratulations! <strong>%s</strong> has been approved.</p>
              <p>You can now log in and start creating events on TicketVerse.</p>
            </div>
            """.formatted(businessName);

        sendHtml(toEmail, "TicketVerse – Organizer Account Approved", html);
    }

    @Async
    public void sendOrganizerRejectedEmail(String toEmail, String businessName, String reason) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:32px;
                        background:#0f0f1a;color:#ffffff;border-radius:12px;">
              <div style="font-size:22px;font-weight:700;color:#6c63ff;margin-bottom:8px;">TicketVerse</div>
              <h2 style="color:#f87171;">Organizer Application Update</h2>
              <p>Unfortunately, <strong>%s</strong> has not been approved at this time.</p>
              %s
              <p style="color:#888;font-size:13px;">Contact support if you have questions.</p>
            </div>
            """.formatted(businessName,
                reason != null && !reason.isBlank()
                    ? "<p><strong>Reason:</strong> " + reason + "</p>" : "");

        sendHtml(toEmail, "TicketVerse – Organizer Application Update", html);
    }

    // ── Feature 13: Event Moderation Emails ──────────────────────────────────

    @Async
    public void sendEventApprovedEmail(User organizer, Event event) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:560px;margin:auto;padding:32px;
                        background:#0f0f1a;color:#ffffff;border-radius:12px;">
              <div style="font-size:22px;font-weight:700;color:#6c63ff;margin-bottom:8px;">TicketVerse</div>
              <h2 style="color:#4ade80;">Your Event is Now Live! 🎉</h2>
              <p>Hi <strong>%s</strong>,</p>
              <p style="margin-top:12px;">
                Your event <strong>"%s"</strong> has been reviewed and is now published on TicketVerse.
                Attendees can discover and book tickets right away.
              </p>
              <p style="margin-top:16px;color:#888;font-size:13px;">
                Log in to your organizer dashboard to monitor bookings and revenue.
              </p>
            </div>
            """.formatted(organizer.getName(), event.getTitle());

        sendHtml(organizer.getEmail(), "TicketVerse – Event Approved: " + event.getTitle(), html);
    }

    @Async
    public void sendEventRejectedEmail(User organizer, Event event, String reason) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:560px;margin:auto;padding:32px;
                        background:#0f0f1a;color:#ffffff;border-radius:12px;">
              <div style="font-size:22px;font-weight:700;color:#6c63ff;margin-bottom:8px;">TicketVerse</div>
              <h2 style="color:#f87171;">Event Review Update</h2>
              <p>Hi <strong>%s</strong>,</p>
              <p style="margin-top:12px;">
                Your event <strong>"%s"</strong> was not approved at this time.
              </p>
              %s
              <p style="margin-top:16px;">
                You can edit your event in the organizer dashboard and re-submit it for review.
              </p>
            </div>
            """.formatted(organizer.getName(), event.getTitle(),
                reason != null && !reason.isBlank()
                    ? "<p><strong>Reason:</strong> " + reason + "</p>" : "");

        sendHtml(organizer.getEmail(), "TicketVerse – Event Needs Changes: " + event.getTitle(), html);
    }

    // ── Cancellation Confirmation Email ───────────────────────────────────────

    @Async
    public void sendCancellationEmail(User user, Booking booking, Event event,
                                      Map<String, Object> result,
                                      byte[] invoicePdfBytes) {
        String subject = "Booking Cancelled – " + event.getTitle();

        double refundAmount = result.get("refundAmount")       != null
                ? ((Number) result.get("refundAmount")).doubleValue()       : 0.0;
        double cancFee      = result.get("cancellationFee")    != null
                ? ((Number) result.get("cancellationFee")).doubleValue()    : 0.0;
        double cancFeeGst   = result.get("cancellationFeeGst") != null
                ? ((Number) result.get("cancellationFeeGst")).doubleValue() : 0.0;
        String status       = result.get("cancellationStatus") != null
                ? (String) result.get("cancellationStatus") : "cancelled";
        boolean isHighTier  = result.get("isHighTier") instanceof Boolean b && b;

        String refundHtml = refundAmount > 0
            ? "<p style=\"color:#4ade80;\">A refund of <strong>₹"
                + String.format("%.2f", refundAmount)
                + "</strong> has been initiated to your original payment method.</p>"
            : "<p style=\"color:#f87171;\">No refund is applicable based on the cancellation policy.</p>";

        String tierNote = isHighTier
            ? "High-tier cancellation (≥72 h before event): full ticket amount refunded minus cancellation charges."
            : "Low-tier cancellation: partial refund per organizer policy.";

        String attachmentNote = invoicePdfBytes != null
            ? "<p style=\"color:#a78bfa;font-size:13px;\">📎 Your cancellation invoice is attached to this email.</p>"
            : "<p style=\"color:#888;font-size:13px;\">You can download your cancellation invoice from My Bookings.</p>";

        String html = """
            <div style="font-family:Arial,sans-serif;max-width:560px;margin:auto;padding:32px;
                        background:#0f0f1a;color:#ffffff;border-radius:12px;">
              <div style="font-size:22px;font-weight:700;color:#6c63ff;margin-bottom:4px;">TicketVerse</div>
              <h2 style="margin:0 0 16px;color:#f87171;">Booking Cancelled</h2>
              <p>Hi <strong>%s</strong>, your booking for <strong>%s</strong> has been cancelled.</p>
              %s
              <table style="width:100%%;border-collapse:collapse;margin-top:16px;">
                <tr><td style="padding:6px 0;color:#888;">Booking #</td>
                    <td style="padding:6px 0;">%d</td></tr>
                <tr><td style="padding:6px 0;color:#888;">Cancellation Fee</td>
                    <td style="padding:6px 0;color:#f87171;">₹%.2f</td></tr>
                <tr><td style="padding:6px 0;color:#888;">GST on Fee</td>
                    <td style="padding:6px 0;color:#f87171;">₹%.2f</td></tr>
                <tr><td style="padding:6px 0;color:#888;">Refund Amount</td>
                    <td style="padding:6px 0;font-weight:700;color:#4ade80;">₹%.2f</td></tr>
                <tr><td style="padding:6px 0;color:#888;">Status</td>
                    <td style="padding:6px 0;">%s</td></tr>
              </table>
              <p style="margin-top:16px;color:#888;font-size:12px;">%s</p>
              %s
            </div>
            """.formatted(
                user.getName(), event.getTitle(), refundHtml,
                booking.getId(), cancFee, cancFeeGst, refundAmount,
                status.toUpperCase().replace("_", " "), tierNote, attachmentNote);

        if (invoicePdfBytes != null) {
            sendHtmlWithAttachment(user.getEmail(), subject, html, invoicePdfBytes,
                "cancellation-invoice-" + booking.getId() + ".pdf", "application/pdf");
        } else {
            sendHtml(user.getEmail(), subject, html);
        }
    }

    // ── Feature 14: Payout Emails ─────────────────────────────────────────────

    @Async
    public void sendPayoutRequestedEmail(User organizer, OrganizerProfile profile,
                                          OrganizerPayout payout) {
        // Notify the organizer that their request is received
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:560px;margin:auto;padding:32px;
                        background:#0f0f1a;color:#ffffff;border-radius:12px;">
              <div style="font-size:22px;font-weight:700;color:#6c63ff;margin-bottom:8px;">TicketVerse</div>
              <h2 style="color:#a78bfa;">Payout Request Received 💰</h2>
              <p>Hi <strong>%s</strong>, we have received your payout request.</p>
              <table style="width:100%%;border-collapse:collapse;margin-top:16px;">
                <tr><td style="padding:6px 0;color:#888;">Amount</td>
                    <td style="padding:6px 0;font-weight:700;color:#4ade80;">₹%.2f</td></tr>
                <tr><td style="padding:6px 0;color:#888;">Period</td>
                    <td style="padding:6px 0;">%s to %s</td></tr>
                <tr><td style="padding:6px 0;color:#888;">Bookings</td>
                    <td style="padding:6px 0;">%d</td></tr>
                <tr><td style="padding:6px 0;color:#888;">Payout Method</td>
                    <td style="padding:6px 0;">%s</td></tr>
              </table>
              <p style="margin-top:16px;color:#888;font-size:13px;">
                Our team will process your request within 3–5 business days.
              </p>
            </div>
            """.formatted(
                organizer.getName(), payout.getAmount(),
                payout.getFromDate(), payout.getToDate(),
                payout.getBookingCount(),
                profile.getPayoutMethod() != null ? profile.getPayoutMethod().toUpperCase() : "N/A");

        sendHtml(organizer.getEmail(), "TicketVerse – Payout Request #" + payout.getId() + " Received", html);

        // Also notify admin if configured
        if (adminEmail != null && !adminEmail.isBlank()) {
            String adminHtml = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:32px;
                            background:#0f0f1a;color:#ffffff;border-radius:12px;">
                  <div style="font-size:22px;font-weight:700;color:#6c63ff;margin-bottom:8px;">TicketVerse Admin</div>
                  <h2 style="color:#f5c842;">New Payout Request ⏳</h2>
                  <p>Organizer: <strong>%s</strong> (%s)</p>
                  <p>Amount: <strong style="color:#4ade80;">₹%.2f</strong> | Period: %s – %s</p>
                  <p>Log in to admin dashboard to process.</p>
                </div>
                """.formatted(profile.getBusinessName(), organizer.getEmail(),
                    payout.getAmount(), payout.getFromDate(), payout.getToDate());
            sendHtml(adminEmail, "TicketVerse – Payout Request from " + profile.getBusinessName(), adminHtml);
        }
    }

    @Async
    public void sendPayoutProcessedEmail(User organizer, OrganizerPayout payout) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:560px;margin:auto;padding:32px;
                        background:#0f0f1a;color:#ffffff;border-radius:12px;">
              <div style="font-size:22px;font-weight:700;color:#6c63ff;margin-bottom:8px;">TicketVerse</div>
              <h2 style="color:#4ade80;">Payout Processed! ✅</h2>
              <p>Hi <strong>%s</strong>, your payout has been processed.</p>
              <table style="width:100%%;border-collapse:collapse;margin-top:16px;">
                <tr><td style="padding:6px 0;color:#888;">Amount</td>
                    <td style="padding:6px 0;font-weight:700;color:#4ade80;">₹%.2f</td></tr>
                <tr><td style="padding:6px 0;color:#888;">Period</td>
                    <td style="padding:6px 0;">%s to %s</td></tr>
                <tr><td style="padding:6px 0;color:#888;">Reference</td>
                    <td style="padding:6px 0;">%s</td></tr>
              </table>
              <p style="margin-top:16px;color:#888;font-size:13px;">
                Please allow 1–2 business days for the amount to reflect in your account.
              </p>
            </div>
            """.formatted(
                organizer.getName(), payout.getAmount(),
                payout.getFromDate(), payout.getToDate(),
                payout.getRazorpayPayoutId() != null ? payout.getRazorpayPayoutId() : "N/A");

        sendHtml(organizer.getEmail(), "TicketVerse – Payout of ₹" + String.format("%.2f", payout.getAmount()) + " Processed", html);
    }

    @Async
    public void sendPayoutRejectedEmail(User organizer, OrganizerPayout payout) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:560px;margin:auto;padding:32px;
                        background:#0f0f1a;color:#ffffff;border-radius:12px;">
              <div style="font-size:22px;font-weight:700;color:#6c63ff;margin-bottom:8px;">TicketVerse</div>
              <h2 style="color:#f87171;">Payout Request Update</h2>
              <p>Hi <strong>%s</strong>, your payout request #%d could not be processed at this time.</p>
              %s
              <p style="margin-top:16px;">
                You can submit a new payout request from your revenue dashboard.
              </p>
            </div>
            """.formatted(
                organizer.getName(), payout.getId(),
                payout.getAdminNote() != null && !payout.getAdminNote().isBlank()
                    ? "<p><strong>Reason:</strong> " + payout.getAdminNote() + "</p>" : "");

        sendHtml(organizer.getEmail(), "TicketVerse – Payout Request #" + payout.getId() + " Rejected", html);
    }

    // ── Plain-text simple email ───────────────────────────────────────────────

    @Async
    public void sendSimple(String to, String subject, String text) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);
            mailSender.send(msg);
            log.info("Simple email sent to {}: {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send simple email to {}: {}", to, e.getMessage());
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
            log.info("Email sent to {}: {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private void sendHtmlWithAttachment(String to, String subject, String html,
                                        byte[] attachmentBytes,
                                        String attachmentFilename,
                                        String attachmentMimeType) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            helper.addAttachment(attachmentFilename,
                new ByteArrayDataSource(attachmentBytes, attachmentMimeType));
            mailSender.send(msg);
            log.info("Email with attachment '{}' sent to {}: {}", attachmentFilename, to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email with attachment to {}: {}", to, e.getMessage());
        }
    }
}
