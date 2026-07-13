package com.ticketapp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketapp.entity.Booking;
import com.ticketapp.entity.Event;
import com.ticketapp.entity.Seat;
import com.ticketapp.entity.User;
import com.ticketapp.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates ticket and invoice PDFs using embedded DejaVu Sans fonts.
 *
 * DejaVu Sans is bundled in src/main/resources/fonts/ and loaded via PDType0Font,
 * which supports the full Unicode range — including ₹ (U+20B9), ✦ (U+2746),
 * — (U+2014), and … (U+2026) that PDType1Font (WinAnsiEncoding) cannot render.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PdfService {

    private final SeatRepository seatRepo;

    private static final float W = 595.28f;
    private static final float H = 520f;   // taller to accommodate payment breakdown
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

    // ── Ticket PDF ────────────────────────────────────────────────────────────

    public byte[] generateTicketPdf(Booking booking, User user, Event event) throws IOException {
        try (PDDocument doc = new PDDocument()) {

            PDType0Font fontRegular = loadFont(doc, FONT_REGULAR);
            PDType0Font fontBold    = loadFont(doc, FONT_BOLD);
            PDType0Font fontItalic  = loadFont(doc, FONT_ITALIC);

            PDPage page = new PDPage(new PDRectangle(W, H));
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                fillRect(cs, DARK_BG, 0, 0, W, H);
                fillRect(cs, PURPLE, 0, 0, 6, H);
                fillRect(cs, DARK_HEADER, 6, H - 70, W - 6, 70);
                fillRect(cs, PURPLE, 6, H - 70, W - 6, 1.6f);

                writeText(cs, fontBold, 9, PURPLE, M, H - 18, "TicketVerse");
                String title = sanitize(event.getTitle(), 40);
                writeText(cs, fontBold, 20, WHITE, M, H - 48, title);

                if (event.getCategory() != null) {
                    String cat = event.getCategory().toUpperCase();
                    float catW = fontBold.getStringWidth(cat) / 1000f * 8 + 16;
                    fillRoundedRect(cs, hex("#2a2455"), W - M - catW, H - 30, catW, 16, 8f);
                    centerText(cs, fontBold, 8, LAVENDER, W - M - catW / 2f, H - 19, cat);
                }

                fillRect(cs, hex("#16213e"), 6, H - 106, W - 6, 36);
                writeText(cs, fontItalic, 8, LAVENDER, M, H - 86,
                        "\u2746  Every great memory begins with a single ticket.");
                writeText(cs, fontItalic, 7, GREY, M, H - 98,
                        "Tonight you\u2019re not just attending an event \u2014 you\u2019re becoming part of a story.");

                float perfY = H - 116;
                cs.setStrokingColor(new PDColor(PERF_LINE, PDDeviceRGB.INSTANCE));
                cs.setLineWidth(1f);
                cs.moveTo(M, perfY);
                cs.lineTo(W - M, perfY);
                cs.stroke();
                writeText(cs, fontBold, 9, GREY, M + 4, perfY - 8, "\u2702");

                float bodyY  = H - 130;
                float bodyH  = bodyY - 50;
                float splitX = W * 0.60f;

                fillRoundedRect(cs, DARK_HEADER, M, bodyY - bodyH, splitX - M - 8, bodyH, 8f);
                strokeRoundedRect(cs, PERF_LINE, M, bodyY - bodyH, splitX - M - 8, bodyH, 8f, 0.7f);
                fillRoundedRect(cs, DARK_HEADER, splitX, bodyY - bodyH, W - splitX - M, bodyH, 8f);
                strokeRoundedRect(cs, PERF_LINE, splitX, bodyY - bodyH, W - splitX - M, bodyH, 8f, 0.7f);

                float lx = M + 12;
                float ly  = bodyY - 14;

                String dateStr = event.getEventDate() != null ? event.getEventDate().format(DATE_FMT) : "TBA";
                label(cs, fontRegular, lx, ly, "Date");
                label(cs, fontRegular, lx + 148, ly, "Location");
                ly -= 10;
                value(cs, fontBold, lx, ly, truncate(dateStr, 20));
                value(cs, fontBold, lx + 148, ly, sanitize(event.getLocation() != null ? event.getLocation() : "TBA", 18));

                ly -= 34;
                String seatsDisp = parseSeats(booking.getSelectedSeats());
                label(cs, fontRegular, lx, ly, "Seat(s)");
                label(cs, fontRegular, lx + 148, ly, "Tickets");
                ly -= 10;
                value(cs, fontBold, lx, ly, seatsDisp != null ? truncate(seatsDisp, 18) : "N/A");
                value(cs, fontBold, lx + 148, ly, booking.getTicketsBooked() + " ticket(s)");

                ly -= 34;
                cs.setStrokingColor(new PDColor(PERF_LINE, PDDeviceRGB.INSTANCE));
                cs.moveTo(lx, ly); cs.lineTo(splitX - 20, ly); cs.stroke();
                ly -= 12;
                label(cs, fontRegular, lx, ly, "Attendee");
                ly -= 10;
                writeText(cs, fontBold, 11, WHITE, lx, ly, sanitize(user.getName(), 22));
                ly -= 14;
                writeText(cs, fontRegular, 8, GREY, lx, ly, sanitize(user.getEmail(), 30));

                float rx = splitX + 12;
                float rRight = W - M - 12;   // right edge of the right-hand box, inset to match left padding
                float ry  = bodyY - 14;

                label(cs, fontRegular, rx, ry, "Booking ID");
                ry -= 10;
                writeText(cs, fontBold, 16, PURPLE, rx, ry, "#" + booking.getId());
                ry -= 40;

                label(cs, fontRegular, rx, ry, "Payment Summary");
                ry -= 14;
                writeText(cs, fontRegular, 8, GREY, rx, ry, "Ticket Amount");
                rightText(cs, fontRegular, 8, WHITE, rRight, ry,
                        String.format("\u20B9%.2f", booking.getTicketAmount() != null ? booking.getTicketAmount() : 0.0));
                ry -= 12;
                writeText(cs, fontRegular, 8, GREY, rx, ry, "Convenience Fee");
                rightText(cs, fontRegular, 8, WHITE, rRight, ry,
                        String.format("\u20B9%.2f", booking.getConvenienceFee() != null ? booking.getConvenienceFee() : 0.0));
                ry -= 12;
                writeText(cs, fontRegular, 8, GREY, rx, ry, "GST (9%)");
                rightText(cs, fontRegular, 8, WHITE, rRight, ry,
                        String.format("\u20B9%.2f", booking.getGstAmount() != null ? booking.getGstAmount() : 0.0));

                ry -= 6;
                cs.setStrokingColor(new PDColor(PERF_LINE, PDDeviceRGB.INSTANCE));
                cs.moveTo(rx, ry); cs.lineTo(rRight, ry); cs.stroke();
                ry -= 10;
                writeText(cs, fontBold, 9, LAVENDER, rx, ry, "Total Paid");
                rightText(cs, fontBold, 9, WHITE, rRight, ry,
                        String.format("\u20B9%.2f", booking.getTotalPaid() != null ? booking.getTotalPaid() : 0.0));

                ry -= 22;
                float confW = rRight - rx;
                fillRoundedRect(cs, hex("#14532d"), rx, ry - 4, confW, 20, 10f);
                centerText(cs, fontBold, 8, hex("#4ade80"), rx + confW / 2f, ry + 8, "\u2714  CONFIRMED");

                centerText(cs, fontRegular, 8, GREY, W / 2f, 20,
                        "This is your official e-ticket. Present this at the venue entry.");
                centerText(cs, fontRegular, 7, PERF_LINE, W / 2f, 10,
                        "Payment ID: " + (booking.getRazorpayPaymentId() != null ? booking.getRazorpayPaymentId() : "N/A"));

                fillRect(cs, PURPLE, 0, 0, W, 6);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ── Booking Invoice PDF ───────────────────────────────────────────────────

    /**
     * Generates an A4 professional billing invoice PDF for a confirmed booking.
     *
     * Mirrors TBA2's generateBookingInvoicePDF() exactly:
     *  - Header band (dark) with TicketVerse brand + "INVOICE" right-aligned
     *  - "BILLED TO" + "INVOICE DETAILS" meta section
     *  - Event details box
     *  - Billing breakdown table (ticket, convenience fee, GST)
     *  - Totals block
     *  - Payment info section
     *  - "PAYMENT CONFIRMED" status badge
     *  - Footer band
     *
     * Triggered by verifyPayment: generated, uploaded to S3, and emailed after
     * the booking is confirmed.
     */
    public byte[] generateBookingInvoicePdf(Booking booking, User user, Event event) throws IOException {
        final float AW = 595.28f;
        final float AH = 841.89f;
        final float AM = 48f;

        final float[] BRAND      = hex("#6c63ff");
        final float[] BRAND_DARK = hex("#574fd6");
        final float[] DARK       = hex("#0f0f1a");
        final float[] MID        = hex("#1a1a2e");
        final float[] MUTED      = hex("#6b7280");
        final float[] LIGHT      = hex("#f9fafb");
        final float[] TINT       = hex("#f3f1ff");
        final float[] SUCCESS    = hex("#16a34a");
        final float[] SUCCESS_BG = hex("#dcfce7");
        final float[] DIVIDER    = hex("#e5e7eb");
        final float[] BORDER     = hex("#e9e8f5");

        String invoiceNumber = "INV-BKG-" + String.format("%06d", booking.getId());
        String issueDate     = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        String eventDateStr  = event.getEventDate() != null
                ? event.getEventDate().format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm")) : "TBA";
        String bookingDateStr = booking.getBookingDate() != null
                ? booking.getBookingDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : "N/A";

        double ticketAmt = booking.getTicketAmount()   != null ? booking.getTicketAmount()   : 0.0;
        double convFee   = booking.getConvenienceFee() != null ? booking.getConvenienceFee() : 0.0;
        double gstAmt    = booking.getGstAmount()      != null ? booking.getGstAmount()      : 0.0;
        double totalPaid = booking.getTotalPaid()      != null ? booking.getTotalPaid()      : 0.0;
        int    numTix    = booking.getTicketsBooked()  != null ? booking.getTicketsBooked()  : 0;

        String seatsDisplay = parseSeats(booking.getSelectedSeats());
        if (seatsDisplay == null) seatsDisplay = "General Admission";

        List<TierLine> tierLines = buildTierLines(event.getId(), booking.getSelectedSeats(), ticketAmt, numTix);
        boolean multiTier = tierLines.size() > 1;

        try (PDDocument doc = new PDDocument()) {
            PDType0Font fontReg  = loadFont(doc, FONT_REGULAR);
            PDType0Font fontBold = loadFont(doc, FONT_BOLD);

            PDPage page = new PDPage(new PDRectangle(AW, AH));
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                // White background
                fillRect(cs, WHITE, 0, 0, AW, AH);

                // ── Header band (two-tone for depth + brand accent) ──────────
                fillRect(cs, DARK, 0, AH - 92, AW, 92);
                fillRect(cs, MID,  0, AH - 92, AW, 30);
                fillRect(cs, BRAND, 0, AH - 92, 6, 92);
                fillRect(cs, BRAND, 0, AH - 92, AW, 2.4f);
                writeText(cs, fontBold, 19, WHITE, AM, AH - 30, "TicketVerse");
                writeText(cs, fontReg,  8, hex("#c4baff"), AM, AH - 52, "ticketverse.in  \u2022  support@ticketverse.in");
                rightText(cs, fontBold, 21, WHITE, AW - AM, AH - 28, "INVOICE");
                rightText(cs, fontReg,  8, hex("#c4baff"), AW - AM, AH - 50, invoiceNumber);

                float y = AH - 114;

                // ── Billed To + Invoice Details ──────────────────────────────
                writeText(cs, fontBold, 7, BRAND, AM, y, "BILLED TO");
                writeText(cs, fontBold, 12, DARK, AM, y - 18, user.getName() != null ? user.getName() : "");
                writeText(cs, fontReg,  9, MUTED, AM, y - 34, user.getEmail() != null ? user.getEmail() : "");

                float rCol = AW - AM - 190;
                writeText(cs, fontBold, 7, BRAND, rCol, y, "INVOICE DETAILS");
                metaRow(cs, fontReg, fontBold, MUTED, DARK, rCol, y - 16, "Invoice No.",  invoiceNumber);
                metaRow(cs, fontReg, fontBold, MUTED, DARK, rCol, y - 30, "Issue Date",   issueDate);
                metaRow(cs, fontReg, fontBold, MUTED, DARK, rCol, y - 44, "Booking ID",   "#" + booking.getId());
                metaRow(cs, fontReg, fontBold, SUCCESS, SUCCESS, rCol, y - 58, "Status",  "PAID");

                y -= 82;
                cs.setStrokingColor(new PDColor(DIVIDER, PDDeviceRGB.INSTANCE));
                cs.setLineWidth(0.8f);
                cs.moveTo(AM, y); cs.lineTo(AW - AM, y); cs.stroke();
                y -= 22;

                // ── Event Details card (rounded, brand-accented) ─────────────
                writeText(cs, fontBold, 7, BRAND, AM, y, "EVENT DETAILS");
                y -= 14;
                fillRoundedRect(cs, TINT, AM, y - 60, AW - 2 * AM, 60, 8f);
                strokeRoundedRect(cs, BORDER, AM, y - 60, AW - 2 * AM, 60, 8f, 0.7f);
                fillRect(cs, BRAND, AM, y - 60, 4f, 60);
                writeText(cs, fontBold, 13, DARK, AM + 16, y - 16, sanitize(event.getTitle(), 50));
                String eventMeta = eventDateStr + "  \u2022  "
                        + (event.getLocation() != null ? event.getLocation() : "TBA")
                        + "  \u2022  " + (event.getCategory() != null ? event.getCategory() : "");
                writeText(cs, fontReg, 8, MUTED, AM + 16, y - 36, sanitize(eventMeta, 72));
                rightText(cs, fontBold, 8, BRAND_DARK, AW - AM - 16, y - 16, numTix + " ticket(s)");
                y -= 78;

                // ── Billing Breakdown table (one row per seat tier) ───────────
                writeText(cs, fontBold, 7, BRAND, AM, y, "BILLING BREAKDOWN");
                y -= 12;
                float rowH    = 22f;
                float CW      = AW - 2 * AM;
                float colQtyR  = AM + 320;   // right edge of Qty column
                float colRateR = AM + 410;   // right edge of Unit Price column
                float colAmtR  = AW - AM - 8; // right edge of Amount column

                // Header row
                fillRect(cs, MID, AM, y - rowH, CW, rowH);
                writeText(cs, fontBold, 8, WHITE, AM + 8, y - 14, "Description");
                rightText(cs, fontBold, 8, WHITE, colQtyR,  y - 14, "Qty");
                rightText(cs, fontBold, 8, WHITE, colRateR, y - 14, "Unit Price");
                rightText(cs, fontBold, 8, WHITE, colAmtR,  y - 14, "Amount");
                y -= rowH;

                // One row per distinct seat tier
                boolean shade = false;
                for (TierLine t : tierLines) {
                    fillRect(cs, shade ? LIGHT : WHITE, AM, y - rowH, CW, rowH);
                    String desc = multiTier
                            ? sanitize(t.category + " Seat \u2014 " + event.getTitle(), 42)
                            : sanitize("Event Ticket \u2014 " + event.getTitle(), 42);
                    writeText(cs, fontReg, 8, DARK, AM + 8, y - 14, desc);
                    rightText(cs, fontReg, 8, DARK, colQtyR,  y - 14, String.valueOf(t.count));
                    rightText(cs, fontReg, 8, DARK, colRateR, y - 14, String.format("\u20B9%.2f", t.pricePerSeat));
                    rightText(cs, fontReg, 8, DARK, colAmtR,  y - 14, String.format("\u20B9%.2f", t.subtotal));
                    y -= rowH;
                    shade = !shade;
                }

                // Convenience fee row
                fillRect(cs, shade ? LIGHT : WHITE, AM, y - rowH, CW, rowH);
                writeText(cs, fontReg, 8, DARK, AM + 8, y - 14, "Convenience Fee (10% of ticket amount)");
                rightText(cs, fontReg, 8, DARK, colQtyR,  y - 14, "1");
                rightText(cs, fontReg, 8, DARK, colRateR, y - 14, String.format("\u20B9%.2f", convFee));
                rightText(cs, fontReg, 8, DARK, colAmtR,  y - 14, String.format("\u20B9%.2f", convFee));
                y -= rowH;
                shade = !shade;

                // GST row
                fillRect(cs, shade ? LIGHT : WHITE, AM, y - rowH, CW, rowH);
                writeText(cs, fontReg, 8, DARK, AM + 8, y - 14, "GST on Convenience Fee (9%)");
                rightText(cs, fontReg, 8, DARK, colQtyR,  y - 14, "1");
                rightText(cs, fontReg, 8, DARK, colRateR, y - 14, String.format("\u20B9%.2f", gstAmt));
                rightText(cs, fontReg, 8, DARK, colAmtR,  y - 14, String.format("\u20B9%.2f", gstAmt));
                y -= rowH;

                // Bottom border under the table
                cs.setStrokingColor(new PDColor(DIVIDER, PDDeviceRGB.INSTANCE));
                cs.setLineWidth(0.8f);
                cs.moveTo(AM, y); cs.lineTo(AW - AM, y); cs.stroke();

                // ── Totals block (right-aligned) ─────────────────────────────
                y -= 16;
                float totBlockW = 200f;
                float totBlockX = AW - AM - totBlockW;

                totRow(cs, fontReg, fontBold, MUTED, DARK, totBlockX, totBlockW, y, false, "Ticket Amount",   String.format("\u20B9%.2f", ticketAmt));
                y -= 16;
                totRow(cs, fontReg, fontBold, MUTED, DARK, totBlockX, totBlockW, y, false, "Convenience Fee", String.format("\u20B9%.2f", convFee));
                y -= 16;
                totRow(cs, fontReg, fontBold, MUTED, DARK, totBlockX, totBlockW, y, false, "GST (9%)",        String.format("\u20B9%.2f", gstAmt));
                y -= 14;

                // Highlighted "Total Paid" band
                fillRoundedRect(cs, TINT, totBlockX - 12, y - 22, totBlockW + 12, 28, 6f);
                totRow(cs, fontBold, fontBold, BRAND_DARK, BRAND_DARK, totBlockX, totBlockW, y - 6, true, "Total Paid", String.format("\u20B9%.2f", totalPaid));

                // ── Payment info card ─────────────────────────────────────────
                y -= 48;
                fillRoundedRect(cs, LIGHT, AM, y - 44, CW, 44, 8f);
                strokeRoundedRect(cs, BORDER, AM, y - 44, CW, 44, 8f, 0.7f);
                writeText(cs, fontBold, 7, BRAND, AM + 16, y - 12, "PAYMENT INFORMATION");
                String payInfo = "Payment ID: " + (booking.getRazorpayPaymentId() != null ? booking.getRazorpayPaymentId() : "N/A")
                        + "   \u2022   Order ID: " + (booking.getRazorpayOrderId() != null ? booking.getRazorpayOrderId() : "N/A")
                        + "   \u2022   Method: Razorpay";
                writeText(cs, fontReg, 8, MUTED, AM + 16, y - 28, sanitize(payInfo, 80));

                // ── Status badge (rounded pill, centered) ─────────────────────
                y -= 58;
                float badgeW = 180f;
                float badgeX = (AW - badgeW) / 2f;
                fillRoundedRect(cs, SUCCESS_BG, badgeX, y - 20, badgeW, 24, 12f);
                centerText(cs, fontBold, 9, SUCCESS, AW / 2f, y - 11, "\u2714  PAYMENT CONFIRMED");

                // ── Seats + booking date ─────────────────────────────────────
                y -= 36;
                centerText(cs, fontReg, 8, MUTED, AW / 2f, y,
                        "Seat(s): " + seatsDisplay + "  \u2022  Booking Date: " + bookingDateStr);

                // ── Footer band ──────────────────────────────────────────────
                fillRect(cs, DARK, 0, 0, AW, 52);
                fillRect(cs, BRAND, 0, 0, 6, 52);
                fillRect(cs, BRAND, 0, 49.6f, AW, 2.4f);
                centerText(cs, fontReg, 7, hex("#9ca3af"), AW / 2f, 36,
                        "Thank you for booking with TicketVerse! For support, reach us at support@ticketverse.in");
                centerText(cs, fontReg, 7, hex("#6b7280"), AW / 2f, 20,
                        invoiceNumber + "  \u2022  This is a computer-generated invoice and requires no signature.");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ── Cancellation Invoice PDF ──────────────────────────────────────────────

    /**
     * Generates an A4 cancellation invoice PDF showing the refund breakdown.
     * result map keys: refundAmount, cancellationFee, cancellationFeeGst,
     *                  isHighTier, cancellationStatus
     */
    public byte[] generateCancellationInvoicePdf(
            Booking booking, User user, Event event,
            java.util.Map<String, Object> result) throws IOException {

        double refundAmount    = result.get("refundAmount")       != null ? ((Number) result.get("refundAmount")).doubleValue()       : 0.0;
        double refundPercent   = result.get("refundPercent")      != null ? ((Number) result.get("refundPercent")).doubleValue()      : 0.0;
        double cancFee         = result.get("cancellationFee")    != null ? ((Number) result.get("cancellationFee")).doubleValue()    : 0.0;
        double cancFeeGst      = result.get("cancellationFeeGst") != null ? ((Number) result.get("cancellationFeeGst")).doubleValue() : 0.0;
        boolean isHighTier     = result.get("isHighTier") instanceof Boolean b && b;
        String status          = result.get("cancellationStatus") != null ? (String) result.get("cancellationStatus") : "cancelled";

        float AW = 595.28f, AH = 841.89f, AM = 40f;

        float[] RED    = hex("#dc2626");
        float[] ORANGE = hex("#f59e0b");
        float[] GREEN2 = hex("#16a34a");
        float[] LIGHT  = hex("#f9fafb");
        float[] BRAND  = hex("#6c63ff");
        float[] DARK   = hex("#0f0f1a");
        float[] MUTED  = hex("#6b7280");
        float[] DIVIDER= hex("#e5e7eb");

        String invoiceNumber = "INV-CXL-" + String.format("%06d", booking.getId());
        String cancelledAt   = booking.getCancelledAt() != null
                ? booking.getCancelledAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : "N/A";

        double totalCancCharge = cancFee + cancFeeGst;

        try (PDDocument doc = new PDDocument()) {
            PDType0Font fontReg  = loadFont(doc, FONT_REGULAR);
            PDType0Font fontBold = loadFont(doc, FONT_BOLD);

            PDPage page = new PDPage(new PDRectangle(AW, AH));
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                fillRect(cs, WHITE, 0, 0, AW, AH);

                // Header bar
                fillRect(cs, DARK_BG, 0, AH - 80, AW, 80);
                fillRect(cs, PURPLE, 0, AH - 80, 6, 80);
                fillRect(cs, PURPLE, 0, AH - 80, AW, 2f);
                writeText(cs, fontBold, 10, PURPLE, AM, AH - 25, "TicketVerse");
                writeText(cs, fontBold, 18, WHITE,  AM, AH - 52, "Cancellation Invoice");
                writeText(cs, fontBold, 8,  hex("#a78bfa"), AM, AH - 68, invoiceNumber);

                String statusLabel = status.toUpperCase().replace("_", " ");
                float[] statusColor = "refunded".equals(status) ? GREEN2
                                    : "refund_pending".equals(status) ? ORANGE : RED;
                rightText(cs, fontBold, 10, statusColor, AW - AM, AH - 40, "STATUS: " + statusLabel);

                // Event + attendee info block
                float y = AH - 110;
                writeText(cs, fontBold, 7, BRAND, AM, y, "BILLED TO");
                writeText(cs, fontBold, 13, DARK, AM, y - 18, event.getTitle() != null ? event.getTitle() : "");
                y -= 36;
                writeText(cs, fontReg, 9, MUTED, AM, y, "Attendee: " + (user.getName() != null ? user.getName() : ""));
                y -= 14;
                writeText(cs, fontReg, 9, MUTED, AM, y, "Booking #" + booking.getId()
                        + "  |  Cancelled: " + cancelledAt);
                y -= 14;
                writeText(cs, fontReg, 9, MUTED, AM, y, "Payment ID: "
                        + (booking.getRazorpayPaymentId() != null ? booking.getRazorpayPaymentId() : "N/A"));

                // Divider
                y -= 18;
                cs.setStrokingColor(new PDColor(DIVIDER, PDDeviceRGB.INSTANCE));
                cs.setLineWidth(0.5f);
                cs.moveTo(AM, y); cs.lineTo(AW - AM, y); cs.stroke();

                // Breakdown table header
                y -= 20;
                fillRect(cs, hex("#e5e7eb"), AM, y - 4, AW - 2 * AM, 18);
                writeText(cs, fontBold, 9, DARK, AM + 4, y + 2, "Description");
                rightText(cs, fontBold, 9, DARK, AW - AM - 4, y + 2, "Amount");

                float rowH = 22f;

                double pricePerTicket = booking.getPricePerTicket()   != null ? booking.getPricePerTicket()   : 0.0;
                int    numTickets     = booking.getTicketsBooked()     != null ? booking.getTicketsBooked()    : 0;
                double ticketAmt      = booking.getTicketAmount()      != null ? booking.getTicketAmount()     : 0.0;
                double convFeeAmt     = booking.getConvenienceFee()    != null ? booking.getConvenienceFee()   : 0.0;
                double gstOnConv      = booking.getGstAmount()         != null ? booking.getGstAmount()        : 0.0;
                double discountAmt    = booking.getDiscountAmount()    != null ? booking.getDiscountAmount()   : 0.0;
                double totalPaidAmt   = booking.getTotalPaid()         != null ? booking.getTotalPaid()        : 0.0;

                List<Object[]> tableRows = new ArrayList<>();
                tableRows.add(new Object[]{
                    String.format("\u20B9%.2f \u00D7 %d ticket%s (ticket subtotal)",
                        pricePerTicket, numTickets, numTickets > 1 ? "s" : ""),
                    ticketAmt, DARK});
                tableRows.add(new Object[]{"Convenience Fee (10%)",       convFeeAmt,  DARK});
                tableRows.add(new Object[]{"GST on Convenience Fee (9%)", gstOnConv,   DARK});
                if (discountAmt > 0) {
                    tableRows.add(new Object[]{
                        "Coupon Discount" + (booking.getCouponCode() != null ? " (" + booking.getCouponCode() + ")" : ""),
                        -discountAmt, hex("#16a34a")});
                }
                tableRows.add(new Object[]{"Total Paid",               totalPaidAmt,  DARK});
                tableRows.add(new Object[]{"Cancellation Fee (5% of ticket+conv.)", cancFee, RED});
                tableRows.add(new Object[]{"GST on Cancellation Fee (5%)",          cancFeeGst, RED});
                tableRows.add(new Object[]{"Refund to You",
                    refundAmount, refundAmount > 0 ? GREEN2 : MUTED});

                for (int i = 0; i < tableRows.size(); i++) {
                    Object[] row  = tableRows.get(i);
                    String   desc = (String)  row[0];
                    double   amt  = (double)   row[1];
                    float[]  color= (float[])  row[2];
                    y -= rowH;
                    if (i % 2 == 0) fillRect(cs, LIGHT, AM, y - 4, AW - 2 * AM, rowH);
                    writeText(cs, fontReg,  9, color, AM + 4, y + 4, desc);
                    rightText(cs, fontBold, 9, color, AW - AM - 4, y + 4,
                            String.format("\u20B9%.2f", Math.abs(amt)));
                }

                // Policy note
                y -= 30;
                String note = isHighTier
                    ? "High-tier cancellation (>= 72 h before event): full ticket amount refunded, only cancellation charge retained."
                    : "Low-tier cancellation: partial refund per organizer policy. Convenience fee retained by platform.";
                writeText(cs, fontReg, 8, MUTED, AM, y, note);

                // Footer
                writeText(cs, fontReg, 8, MUTED, AM, 20,
                    "This is an official TicketVerse cancellation invoice. Refunds are processed via Razorpay.");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** A single line item in the billing breakdown — one per distinct seat tier/category. */
    private static final class TierLine {
        final String category;
        final int    count;
        final double pricePerSeat;
        final double subtotal;
        TierLine(String category, int count, double pricePerSeat, double subtotal) {
            this.category = category; this.count = count;
            this.pricePerSeat = pricePerSeat; this.subtotal = subtotal;
        }
    }

    /**
     * Builds one billing line per distinct seat category/tier for this booking.
     *
     * Looks up the actual Seat rows for booking.selectedSeats (a JSON array string
     * like ["A1","A2","B3"]) and groups them by category (Silver/Gold/Platinum/...),
     * so a booking spanning multiple tiers shows a separate row + unit price per tier
     * instead of one row with a single averaged "unit price".
     *
     * Falls back to a single "General Admission" line (using the event's flat price)
     * when there are no seats on the booking, the seats can't be matched, or the
     * event predates tiered seating (seats without a per-seat price set).
     */
    private List<TierLine> buildTierLines(Long eventId, String selectedSeatsJson,
                                          double fallbackTicketAmount, int ticketsBooked) {
        List<TierLine> lines = new ArrayList<>();
        List<String> seatNumbers = parseSeatNumbers(selectedSeatsJson);

        if (!seatNumbers.isEmpty()) {
            List<Seat> seats = seatRepo.findByEventIdAndSeatNumberIn(eventId, seatNumbers);
            boolean allMatched   = seats.size() == seatNumbers.size();
            boolean allHavePrice = allMatched && seats.stream().allMatch(s -> s.getPrice() != null && s.getPrice() > 0);

            if (allHavePrice) {
                Map<String, List<Seat>> byCategory = new LinkedHashMap<>();
                for (Seat s : seats) {
                    String cat = (s.getCategory() != null && !s.getCategory().isBlank()) ? s.getCategory() : "General";
                    byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(s);
                }
                for (Map.Entry<String, List<Seat>> e : byCategory.entrySet()) {
                    List<Seat> group = e.getValue();
                    double pricePerSeat = group.get(0).getPrice();
                    lines.add(new TierLine(e.getKey(), group.size(), pricePerSeat, pricePerSeat * group.size()));
                }
                return lines;
            }
        }

        // Fallback: flat single-price line (non-tiered event, or seats not resolvable)
        double unit = ticketsBooked > 0 ? fallbackTicketAmount / ticketsBooked : fallbackTicketAmount;
        lines.add(new TierLine("General Admission", ticketsBooked, unit, fallbackTicketAmount));
        return lines;
    }

    /** Parses a JSON array string like ["A1","A2"] into a clean List<String>. Never throws. */
    private List<String> parseSeatNumbers(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.isBlank() || json.equals("[]")) return out;
        String stripped = json.replace("[", "").replace("]", "").replace("\"", "");
        for (String part : stripped.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    /** Inline helper: label left-aligned at x, value truly right-aligned at x + colWidth. */
    private void metaRow(PDPageContentStream cs,
                         PDType0Font fontReg, PDType0Font fontBold,
                         float[] labelColor, float[] valColor,
                         float x, float y, String label, String value) throws IOException {
        writeText(cs, fontReg, 8, labelColor, x, y, label);
        rightText(cs, fontBold, 8, valColor, x + 190, y, value);
    }

    /** Totals block row: label left-aligned at x, value truly right-aligned at x + blockW. */
    private void totRow(PDPageContentStream cs,
                        PDType0Font labelFont, PDType0Font valFont,
                        float[] labelColor, float[] valColor,
                        float x, float blockW, float y, boolean bold,
                        String label, String value) throws IOException {
        writeText(cs, labelFont, bold ? 10.5f : 9, labelColor, x, y, label);
        rightText(cs, valFont, bold ? 10.5f : 9, valColor, x + blockW, y, value);
    }

    /** Draws text ending exactly at rightX — true right-alignment via measured string width. */
    private void rightText(PDPageContentStream cs, PDType0Font font, float size,
                           float[] rgb, float rightX, float y, String text) throws IOException {
        if (text == null || text.isBlank()) return;
        float width = font.getStringWidth(text) / 1000f * size;
        writeText(cs, font, size, rgb, rightX - width, y, text);
    }

    /** Draws text horizontally centered on centerX — used for badges/headings. */
    private void centerText(PDPageContentStream cs, PDType0Font font, float size,
                            float[] rgb, float centerX, float y, String text) throws IOException {
        if (text == null || text.isBlank()) return;
        float width = font.getStringWidth(text) / 1000f * size;
        writeText(cs, font, size, rgb, centerX - width / 2f, y, text);
    }

    private PDType0Font loadFont(PDDocument doc, String classpathResource) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(classpathResource)) {
            if (is == null) throw new IOException("Font not found: " + classpathResource);
            return PDType0Font.load(doc, is);
        }
    }

    private void fillRect(PDPageContentStream cs, float[] rgb,
                          float x, float y, float w, float h) throws IOException {
        cs.setNonStrokingColor(new PDColor(rgb, PDDeviceRGB.INSTANCE));
        cs.addRect(x, y, w, h);
        cs.fill();
    }

    /** Traces a rounded-rectangle path (does not paint it — caller fills/strokes). */
    private void roundedRectPath(PDPageContentStream cs, float x, float y, float w, float h, float r) throws IOException {
        float k = 0.5523f * r;
        cs.moveTo(x + r, y);
        cs.lineTo(x + w - r, y);
        cs.curveTo(x + w - r + k, y, x + w, y + r - k, x + w, y + r);
        cs.lineTo(x + w, y + h - r);
        cs.curveTo(x + w, y + h - r + k, x + w - r + k, y + h, x + w - r, y + h);
        cs.lineTo(x + r, y + h);
        cs.curveTo(x + r - k, y + h, x, y + h - r + k, x, y + h - r);
        cs.lineTo(x, y + r);
        cs.curveTo(x, y + r - k, x + r - k, y, x + r, y);
        cs.closePath();
    }

    /** Fills a rounded rectangle — used for cards, totals highlight, and status badges. */
    private void fillRoundedRect(PDPageContentStream cs, float[] rgb,
                                 float x, float y, float w, float h, float r) throws IOException {
        cs.setNonStrokingColor(new PDColor(rgb, PDDeviceRGB.INSTANCE));
        roundedRectPath(cs, x, y, w, h, r);
        cs.fill();
    }

    /** Strokes a thin border around a rounded rectangle (call after fillRoundedRect). */
    private void strokeRoundedRect(PDPageContentStream cs, float[] rgb,
                                   float x, float y, float w, float h, float r, float lineWidth) throws IOException {
        cs.setStrokingColor(new PDColor(rgb, PDDeviceRGB.INSTANCE));
        cs.setLineWidth(lineWidth);
        roundedRectPath(cs, x, y, w, h, r);
        cs.stroke();
    }

    private void writeText(PDPageContentStream cs, PDType0Font font, float size,
                           float[] rgb, float x, float y, String text) throws IOException {
        if (text == null || text.isBlank()) return;
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(new PDColor(rgb, PDDeviceRGB.INSTANCE));
        cs.newLineAtOffset(x, y);
        cs.showText(text);
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
        return s.length() > max ? s.substring(0, max - 1) + "\u2026" : s;
    }

    /**
     * Sanitizes a free-text string before it enters a PDF cell.
     *
     * Protects against:
     *  - Overlong values that overflow fixed-width PDF cells (truncated to max)
     *  - Control characters (tab, CR, LF) that confuse PDFBox text rendering
     *  - Leading/trailing whitespace
     *
     * @param s    raw input (may be null)
     * @param max  maximum visible characters; text beyond this is replaced with …
     */
    private String sanitize(String s, int max) {
        if (s == null || s.isBlank()) return "";
        // Strip control characters (\u0000–\u001F, \u007F) that PDFBox cannot render
        String cleaned = s.replaceAll("[\\p{Cntrl}]", " ").trim();
        return truncate(cleaned, max);
    }

    /**
     * Parses the selectedSeats JSON array into a human-readable comma-separated string.
     *
     * Uses Jackson for proper JSON parsing instead of string replacement hacks.
     * Input examples: null, "[]", "[\"A1\",\"A2\"]", "[\"Gold-1\"]"
     * Output:         null  →  null (caller shows "General Admission")
     *                 data  →  "A1,  A2"
     */
    private static final ObjectMapper SEAT_MAPPER = new ObjectMapper();

    private String parseSeats(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return null;
        try {
            List<String> seats = SEAT_MAPPER.readValue(json, new TypeReference<List<String>>() {});
            if (seats == null || seats.isEmpty()) return null;
            return String.join(",  ", seats);
        } catch (Exception e) {
            log.warn("parseSeats: failed to parse JSON '{}', falling back to strip: {}", json, e.getMessage());
            // Safe fallback: strip JSON syntax characters, never throw
            String stripped = json.replaceAll("[\\[\\]\"\\\\]", "").trim();
            return stripped.isBlank() ? null : stripped;
        }
    }
}
