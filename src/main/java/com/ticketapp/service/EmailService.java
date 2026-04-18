package com.ticketapp.service;

import com.ticketapp.entity.Booking;
import com.ticketapp.entity.Event;
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
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@ticketverse.com}")
    private String fromAddress;

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
    public void sendTicketEmail(User user, Booking booking, Event event) {
        String subject = "Your Ticket – " + event.getTitle();

        String seatsHtml = "";
        if (booking.getSelectedSeats() != null && !booking.getSelectedSeats().equals("[]")) {
            seatsHtml = "<tr><td style='padding:6px 0;color:#888;'>Seats</td>" +
                        "<td style='padding:6px 0;'>" +
                        booking.getSelectedSeats().replace("[","").replace("]","").replace("\"","") +
                        "</td></tr>";
        }

        String dateStr = event.getEventDate() != null
                ? event.getEventDate().format(DATE_FMT) : "TBA";

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
              <p style="margin-top:24px;color:#888;font-size:13px;">
                Log in to TicketVerse to download your ticket PDF.
              </p>
            </div>
            """.formatted(
                user.getName(), event.getTitle(), dateStr,
                event.getLocation() != null ? event.getLocation() : "TBA",
                booking.getTicketsBooked(), seatsHtml, booking.getTotalPaid());

        sendHtml(user.getEmail(), subject, html);
    }

    // ── Organizer approval/rejection emails ───────────────────────────────────

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


    // ── Cancellation Confirmation Email ───────────────────────────────────────

    @Async
    public void sendCancellationEmail(User user, Booking booking, Event event,
                                      java.util.Map<String, Object> result) {
        String subject = "Booking Cancelled – " + event.getTitle();

        double refundAmount    = result.get("refundAmount")    != null ? ((Number) result.get("refundAmount")).doubleValue()    : 0.0;
        double cancFee         = result.get("cancellationFee") != null ? ((Number) result.get("cancellationFee")).doubleValue() : 0.0;
        double cancFeeGst      = result.get("cancellationFeeGst") != null ? ((Number) result.get("cancellationFeeGst")).doubleValue() : 0.0;
        String status          = result.get("cancellationStatus") != null ? (String) result.get("cancellationStatus") : "cancelled";
        boolean isHighTier     = result.get("isHighTier") instanceof Boolean b && b;

        String refundHtml = refundAmount > 0
            ? "<p style=\"color:#4ade80;\">A refund of <strong>₹" + String.format("%.2f", refundAmount) + "</strong> has been initiated to your original payment method.</p>"
            : "<p style=\"color:#f87171;\">No refund is applicable based on the cancellation policy.</p>";

        String tierNote = isHighTier
            ? "High-tier cancellation (≥72 h before event): full ticket amount refunded minus cancellation charges."
            : "Low-tier cancellation: partial refund per organizer policy.";

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
              <p style="color:#888;font-size:12px;">
                Please find your official cancellation invoice attached or download it from My Bookings.
              </p>
            </div>
            """.formatted(
                user.getName(), event.getTitle(), refundHtml,
                booking.getId(), cancFee, cancFeeGst, refundAmount,
                status.toUpperCase().replace("_", " "), tierNote);

        sendHtml(user.getEmail(), subject, html);
    }

    // ── Internal helper ───────────────────────────────────────────────────────

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
}
