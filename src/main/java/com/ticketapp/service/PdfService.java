package com.ticketapp.service;

import com.ticketapp.entity.Booking;
import com.ticketapp.entity.Event;
import com.ticketapp.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * Generates a ticket-sized PDF (595 x 420 pt) that mirrors the pdfkit design
 * from email.services.js: dark background, purple accent strip, event details grid.
 */
@Service
@Slf4j
public class PdfService {

    private static final float W = 595.28f;
    private static final float H = 420f;
    private static final float M = 28f;   // margin

    // Colour helpers
    private static final float[] DARK_BG     = hex("#0f0f1a");
    private static final float[] DARK_HEADER = hex("#1a1a2e");
    private static final float[] PURPLE      = hex("#6c63ff");
    private static final float[] LAVENDER    = hex("#a78bfa");
    private static final float[] GREY        = hex("#888888");
    private static final float[] WHITE       = {1f, 1f, 1f};
    private static final float[] PERF_LINE   = hex("#2e2e50");

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm");

    public byte[] generateTicketPdf(Booking booking, User user, Event event) throws IOException {
        try (PDDocument doc = new PDDocument()) {
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
                writeText(cs, PDType1Font.HELVETICA_BOLD,
                        9, PURPLE, M, H - 18, "TicketVerse");

                // Event title
                String title = truncate(event.getTitle(), 42);
                writeText(cs, PDType1Font.HELVETICA_BOLD,
                        20, WHITE, M, H - 48, title);

                // Category badge (top right)
                if (event.getCategory() != null) {
                    String cat = event.getCategory().toUpperCase();
                    writeText(cs, PDType1Font.HELVETICA_BOLD,
                            8, PURPLE, W - M - 90, H - 22, cat);
                }

                // ── Verse strip ──────────────────────────────────────────────
                fillRect(cs, hex("#16213e"), 6, H - 106, W - 6, 36);
                writeText(cs, PDType1Font.HELVETICA_OBLIQUE,
                        8, LAVENDER, M, H - 86,
                        "*  Every great memory begins with a single ticket.");
                writeText(cs, PDType1Font.HELVETICA_OBLIQUE,
                        7, GREY, M, H - 98,
                        "Tonight you're not just attending an event - you're becoming part of a story.");

                // ── Perforated divider ───────────────────────────────────────
                float perfY = H - 116;
                cs.setStrokingColor(new PDColor(PERF_LINE, PDDeviceRGB.INSTANCE));
                cs.setLineWidth(1f);
                cs.moveTo(M, perfY);
                cs.lineTo(W - M, perfY);
                cs.stroke();

                // ── Details grid ─────────────────────────────────────────────
                float rowY = perfY - 24;
                float labelX = M + 10;
                float valueX = M + 130;

                // Attendee
                label(cs, labelX, rowY, "Attendee");
                value(cs, valueX, rowY, user.getName());
                rowY -= 22;

                // Date
                String dateStr = event.getEventDate() != null
                        ? event.getEventDate().format(DATE_FMT) : "TBA";
                label(cs, labelX, rowY, "Date & Time");
                value(cs, valueX, rowY, dateStr);
                rowY -= 22;

                // Location
                label(cs, labelX, rowY, "Venue");
                value(cs, valueX, rowY,
                        event.getLocation() != null ? event.getLocation() : "TBA");
                rowY -= 22;

                // Tickets
                label(cs, labelX, rowY, "Tickets");
                value(cs, valueX, rowY, String.valueOf(booking.getTicketsBooked()));
                rowY -= 22;

                // Seats (if any)
                String seatsStr = parseSeats(booking.getSelectedSeats());
                if (seatsStr != null) {
                    label(cs, labelX, rowY, "Seats");
                    value(cs, valueX, rowY, seatsStr);
                    rowY -= 22;
                }

                // Total paid (highlighted)
                label(cs, labelX, rowY, "Total Paid");
                writeText(cs, PDType1Font.HELVETICA_BOLD,
                        11, PURPLE, valueX, rowY,
                        String.format("Rs. %.2f", booking.getTotalPaid()));

                // ── Booking ID footer ─────────────────────────────────────────
                writeText(cs, PDType1Font.HELVETICA,
                        8, GREY, M, 14,
                        "Booking #" + booking.getId() + "  |  Payment: " + booking.getRazorpayPaymentId());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private void fillRect(PDPageContentStream cs, float[] rgb,
                          float x, float y, float width, float height) throws IOException {
        cs.setNonStrokingColor(new PDColor(rgb, PDDeviceRGB.INSTANCE));
        cs.addRect(x, y, width, height);
        cs.fill();
    }

    private void writeText(PDPageContentStream cs, PDType1Font font, float size,
                           float[] rgb, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(new PDColor(rgb, PDDeviceRGB.INSTANCE));
        cs.newLineAtOffset(x, y);
        cs.showText(sanitise(text));
        cs.endText();
    }

    private void label(PDPageContentStream cs, float x, float y, String text) throws IOException {
        writeText(cs, PDType1Font.HELVETICA,
                9, GREY, x, y, text);
    }

    private void value(PDPageContentStream cs, float x, float y, String text) throws IOException {
        writeText(cs, PDType1Font.HELVETICA_BOLD,
                10, WHITE, x, y, text);
    }

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
        return s.length() > max ? s.substring(0, max - 1) + "..." : s;
    }

    /** Strip chars outside the PDFBox WinAnsiEncoding range */
    private String sanitise(String s) {
        if (s == null) return "";
        return s.replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", "?");
    }

    private String parseSeats(String json) {
        if (json == null || json.equals("[]") || json.isBlank()) return null;
        // ["A1","A2"] → "A1, A2"
        return json.replace("[", "").replace("]", "").replace("\"", "").replace(",", ", ");
    }
}
