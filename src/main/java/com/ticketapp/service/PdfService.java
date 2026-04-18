package com.ticketapp.service;

import com.ticketapp.entity.Booking;
import com.ticketapp.entity.Event;
import com.ticketapp.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;

/**
 * Generates a ticket-sized PDF (595 x 420 pt) using embedded DejaVu Sans fonts.
 *
 * DejaVu Sans is bundled in src/main/resources/fonts/ and loaded via PDType0Font,
 * which supports the full Unicode range — including ₹ (U+20B9), ✦ (U+2746),
 * — (U+2014), and … (U+2026) that PDType1Font (WinAnsiEncoding) cannot render.
 */
@Service
@Slf4j
public class PdfService {

    private static final float W = 595.28f;
    private static final float H = 420f;
    private static final float M = 28f;

    // Colour constants
    private static final float[] DARK_BG     = hex("#0f0f1a");
    private static final float[] DARK_HEADER = hex("#1a1a2e");
    private static final float[] PURPLE      = hex("#6c63ff");
    private static final float[] LAVENDER    = hex("#a78bfa");
    private static final float[] GREY        = hex("#888888");
    private static final float[] WHITE       = {1f, 1f, 1f};
    private static final float[] PERF_LINE   = hex("#2e2e50");

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm");

    // Font resource paths (inside src/main/resources/fonts/)
    private static final String FONT_REGULAR = "/fonts/DejaVuSans.ttf";
    private static final String FONT_BOLD    = "/fonts/DejaVuSans-Bold.ttf";
    private static final String FONT_ITALIC  = "/fonts/DejaVuSans-Oblique.ttf";

    public byte[] generateTicketPdf(Booking booking, User user, Event event) throws IOException {
        try (PDDocument doc = new PDDocument()) {

            // Load Unicode fonts from classpath — embedded into the PDF so they
            // render correctly on every OS without any installed fonts on the server.
            PDType0Font fontRegular = loadFont(doc, FONT_REGULAR);
            PDType0Font fontBold    = loadFont(doc, FONT_BOLD);
            PDType0Font fontItalic  = loadFont(doc, FONT_ITALIC);

            PDPage page = new PDPage(new PDRectangle(W, H));
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                // ── Background ──────────────────────────────────────────────
                fillRect(cs, DARK_BG, 0, 0, W, H);

                // ── Left accent strip ────────────────────────────────────────
                fillRect(cs, PURPLE, 0, 0, 6, H);

                // ── Header bar ───────────────────────────────────────────────
                fillRect(cs, DARK_HEADER, 6, H - 70, W - 6, 70);

                // Brand name
                writeText(cs, fontBold, 9, PURPLE, M, H - 18, "TicketVerse");

                // Event title
                String title = truncate(event.getTitle(), 42);
                writeText(cs, fontBold, 20, WHITE, M, H - 48, title);

                // Category badge (top right)
                if (event.getCategory() != null) {
                    String cat = event.getCategory().toUpperCase();
                    writeText(cs, fontBold, 8, PURPLE, W - M - 90, H - 22, cat);
                }

                // ── Verse strip ──────────────────────────────────────────────
                fillRect(cs, hex("#16213e"), 6, H - 106, W - 6, 36);
                // ✦ and — now render correctly with DejaVu Sans Unicode support
                writeText(cs, fontItalic, 8, LAVENDER, M, H - 86,
                        "✦  Every great memory begins with a single ticket.");
                writeText(cs, fontItalic, 7, GREY, M, H - 98,
                        "Tonight you\u2019re not just attending an event \u2014 you\u2019re becoming part of a story.");

                // ── Perforated divider ───────────────────────────────────────
                float perfY = H - 116;
                cs.setStrokingColor(new PDColor(PERF_LINE, PDDeviceRGB.INSTANCE));
                cs.setLineWidth(1f);
                cs.moveTo(M, perfY);
                cs.lineTo(W - M, perfY);
                cs.stroke();

                // ── Details grid ─────────────────────────────────────────────
                float rowY   = perfY - 24;
                float labelX = M + 10;
                float valueX = M + 130;

                // Attendee
                label(cs, fontRegular, labelX, rowY, "Attendee");
                value(cs, fontBold, valueX, rowY, user.getName());
                rowY -= 22;

                // Date
                String dateStr = event.getEventDate() != null
                        ? event.getEventDate().format(DATE_FMT) : "TBA";
                label(cs, fontRegular, labelX, rowY, "Date & Time");
                value(cs, fontBold, valueX, rowY, dateStr);
                rowY -= 22;

                // Location
                label(cs, fontRegular, labelX, rowY, "Venue");
                value(cs, fontBold, valueX, rowY,
                        event.getLocation() != null ? event.getLocation() : "TBA");
                rowY -= 22;

                // Tickets
                label(cs, fontRegular, labelX, rowY, "Tickets");
                value(cs, fontBold, valueX, rowY, String.valueOf(booking.getTicketsBooked()));
                rowY -= 22;

                // Seats (if any)
                String seatsStr = parseSeats(booking.getSelectedSeats());
                if (seatsStr != null) {
                    label(cs, fontRegular, labelX, rowY, "Seats");
                    value(cs, fontBold, valueX, rowY, seatsStr);
                    rowY -= 22;
                }

                // Total paid — ₹ now renders correctly
                label(cs, fontRegular, labelX, rowY, "Total Paid");
                writeText(cs, fontBold, 11, PURPLE, valueX, rowY,
                        String.format("\u20B9%.2f", booking.getTotalPaid()));

                // ── Footer ────────────────────────────────────────────────────
                writeText(cs, fontRegular, 8, GREY, M, 14,
                        "Booking #" + booking.getId()
                        + "  |  Payment: " + booking.getRazorpayPaymentId());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ── Font loader ───────────────────────────────────────────────────────────

    private PDType0Font loadFont(PDDocument doc, String classpathResource) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(classpathResource)) {
            if (is == null) {
                throw new IOException("Font not found on classpath: " + classpathResource);
            }
            // embed=true embeds the font subset into the PDF so it displays
            // correctly on any machine regardless of installed fonts.
            return PDType0Font.load(doc, is, true);
        }
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private void fillRect(PDPageContentStream cs, float[] rgb,
                          float x, float y, float width, float height) throws IOException {
        cs.setNonStrokingColor(new PDColor(rgb, PDDeviceRGB.INSTANCE));
        cs.addRect(x, y, width, height);
        cs.fill();
    }

    private void writeText(PDPageContentStream cs, PDType0Font font, float size,
                           float[] rgb, float x, float y, String text) throws IOException {
        if (text == null || text.isEmpty()) return;
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(new PDColor(rgb, PDDeviceRGB.INSTANCE));
        cs.newLineAtOffset(x, y);
        cs.showText(text);   // No sanitise() needed — PDType0Font handles full Unicode
        cs.endText();
    }

    private void label(PDPageContentStream cs, PDType0Font font,
                       float x, float y, String text) throws IOException {
        writeText(cs, font, 9, GREY, x, y, text);
    }

    private void value(PDPageContentStream cs, PDType0Font font,
                       float x, float y, String text) throws IOException {
        writeText(cs, font, 10, WHITE, x, y, text);
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    private static float[] hex(String hex) {
        hex = hex.replace("#", "");
        return new float[]{
                Integer.parseInt(hex.substring(0, 2), 16) / 255f,
                Integer.parseInt(hex.substring(2, 4), 16) / 255f,
                Integer.parseInt(hex.substring(4, 6), 16) / 255f
        };
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        // … (U+2026) now renders correctly with DejaVu Sans
        return s.length() > max ? s.substring(0, max - 1) + "\u2026" : s;
    }

    private String parseSeats(String json) {
        if (json == null || json.equals("[]") || json.isBlank()) return null;
        return json.replace("[", "").replace("]", "").replace("\"", "").replace(",", ",  ");
    }
    /**
     * Generates an A4 cancellation invoice PDF showing the refund breakdown.
     * result map keys: refundAmount, cancellationFee, cancellationFeeGst,
     *                  isHighTier, cancellationStatus
     */
    public byte[] generateCancellationInvoicePdf(
            Booking booking, User user, Event event,
            java.util.Map<String, Object> result) throws IOException {

        double refundAmount    = result.get("refundAmount")    != null ? ((Number) result.get("refundAmount")).doubleValue()    : 0.0;
        double cancFee         = result.get("cancellationFee") != null ? ((Number) result.get("cancellationFee")).doubleValue() : 0.0;
        double cancFeeGst      = result.get("cancellationFeeGst") != null ? ((Number) result.get("cancellationFeeGst")).doubleValue() : 0.0;
        boolean isHighTier     = result.get("isHighTier") instanceof Boolean b && b;
        String status          = result.get("cancellationStatus") != null ? (String) result.get("cancellationStatus") : "cancelled";

        // A4 portrait
        float AW = 595.28f, AH = 841.89f, AM = 40f;

        float[] RED    = hex("#dc2626");
        float[] ORANGE = hex("#f59e0b");
        float[] GREEN2 = hex("#16a34a");
        float[] LIGHT  = hex("#f9fafb");

        try (PDDocument doc = new PDDocument()) {
            PDType0Font fontReg  = loadFont(doc, FONT_REGULAR);
            PDType0Font fontBold = loadFont(doc, FONT_BOLD);

            PDPage page = new PDPage(new PDRectangle(AW, AH));
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // White background
                fillRect(cs, WHITE, 0, 0, AW, AH);

                // Header bar
                fillRect(cs, DARK_BG, 0, AH - 80, AW, 80);
                writeText(cs, fontBold, 10, PURPLE, AM, AH - 25, "TicketVerse");
                writeText(cs, fontBold, 18, WHITE,  AM, AH - 52, "Cancellation Invoice");

                String statusLabel = status.toUpperCase().replace("_", " ");
                float[] statusColor = "refunded".equals(status) ? GREEN2
                                    : "refund_pending".equals(status) ? ORANGE : RED;
                writeText(cs, fontBold, 10, statusColor, AW - AM - 110, AH - 40, "STATUS: " + statusLabel);

                // Event + attendee info block
                float y = AH - 110;
                writeText(cs, fontBold, 13, DARK_BG, AM, y, event.getTitle() != null ? event.getTitle() : "");
                y -= 18;
                writeText(cs, fontReg, 9, GREY, AM, y, "Attendee: " + (user.getName() != null ? user.getName() : ""));
                y -= 14;
                writeText(cs, fontReg, 9, GREY, AM, y, "Booking #" + booking.getId()
                        + "  |  Cancelled: "
                        + (booking.getCancelledAt() != null
                            ? booking.getCancelledAt().format(DATE_FMT) : "N/A"));
                y -= 14;
                writeText(cs, fontReg, 9, GREY, AM, y, "Payment ID: "
                        + (booking.getRazorpayPaymentId() != null ? booking.getRazorpayPaymentId() : "N/A"));

                // Divider
                y -= 18;
                cs.setStrokingColor(new PDColor(PERF_LINE, PDDeviceRGB.INSTANCE));
                cs.setLineWidth(0.5f);
                cs.moveTo(AM, y); cs.lineTo(AW - AM, y); cs.stroke();

                // Breakdown table header
                y -= 20;
                fillRect(cs, hex("#e5e7eb"), AM, y - 4, AW - 2*AM, 18);
                writeText(cs, fontBold, 9, DARK_BG, AM + 4, y + 2, "Description");
                writeText(cs, fontBold, 9, DARK_BG, AW - AM - 80, y + 2, "Amount");

                // Helper: table row
                float rowH = 22f;

                // Rows
                float[][] rows = {
                    {booking.getTicketAmount()   != null ? booking.getTicketAmount()   : 0.0},
                    {booking.getConvenienceFee() != null ? booking.getConvenienceFee() : 0.0},
                    {booking.getGstAmount()      != null ? booking.getGstAmount()      : 0.0},
                    {booking.getTotalPaid()      != null ? booking.getTotalPaid()      : 0.0},
                    {cancFee},
                    {cancFeeGst},
                    {refundAmount}
                };
                String[] descs = {
                    "Ticket Amount", "Convenience Fee (10%)", "GST on Conv. Fee (9%)",
                    "Total Paid", "Cancellation Fee (5% of ticket+conv.)",
                    "GST on Cancellation Fee (5%)", "Refund to You"
                };
                float[][] colors = {DARK_BG, DARK_BG, DARK_BG, DARK_BG, RED, RED,
                        refundAmount > 0 ? GREEN2 : GREY};

                for (int i = 0; i < descs.length; i++) {
                    y -= rowH;
                    if (i % 2 == 0) fillRect(cs, LIGHT, AM, y - 4, AW - 2*AM, rowH);
                    writeText(cs, fontReg,  9, colors[i], AM + 4, y + 4, descs[i]);
                    writeText(cs, fontBold, 9, colors[i], AW - AM - 80, y + 4,
                            String.format("₹%.2f", rows[i][0]));
                }

                // Policy note
                y -= 30;
                String note = isHighTier
                    ? "High-tier cancellation (>= 72 h before event): full ticket amount refunded, only cancellation charge retained."
                    : "Low-tier cancellation: partial refund per organizer policy. Convenience fee retained by platform.";
                writeText(cs, fontReg, 8, GREY, AM, y, note);

                // Footer
                writeText(cs, fontReg, 8, GREY, AM, 20,
                    "This is an official TicketVerse cancellation invoice. Refunds are processed via Razorpay.");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }


}