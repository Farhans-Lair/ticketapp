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
import java.util.ArrayList;
import java.util.List;

/**
 * Generates ticket and invoice PDFs using embedded DejaVu Sans fonts.
 *
 * DejaVu Sans is bundled in src/main/resources/fonts/ and loaded via PDType0Font,
 * which supports the full Unicode range — including ₹ (U+20B9), ✦ (U+2746),
 * — (U+2014), and … (U+2026) that PDType1Font (WinAnsiEncoding) cannot render.
 */
@Service
@Slf4j
public class PdfService {

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

                writeText(cs, fontBold, 9, PURPLE, M, H - 18, "TicketVerse");
                String title = truncate(event.getTitle(), 42);
                writeText(cs, fontBold, 20, WHITE, M, H - 48, title);

                if (event.getCategory() != null) {
                    String cat = event.getCategory().toUpperCase();
                    writeText(cs, fontBold, 8, PURPLE, W - M - 90, H - 22, cat);
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

                fillRect(cs, DARK_HEADER, M, bodyY - bodyH, splitX - M - 8, bodyH);
                fillRect(cs, DARK_HEADER, splitX, bodyY - bodyH, W - splitX - M, bodyH);

                float lx = M + 12;
                float ly  = bodyY - 14;

                String dateStr = event.getEventDate() != null ? event.getEventDate().format(DATE_FMT) : "TBA";
                label(cs, fontRegular, lx, ly, "Date");
                label(cs, fontRegular, lx + 148, ly, "Location");
                ly -= 10;
                value(cs, fontBold, lx, ly, truncate(dateStr, 20));
                value(cs, fontBold, lx + 148, ly, truncate(event.getLocation() != null ? event.getLocation() : "TBA", 18));

                ly -= 34;
                String seatsDisp = parseSeats(booking.getSelectedSeats());
                label(cs, fontRegular, lx, ly, "Seat(s)");
                label(cs, fontRegular, lx + 148, ly, "Tickets");
                ly -= 10;
                value(cs, fontBold, lx, ly, seatsDisp != null ? truncate(seatsDisp, 18) : "N/A");
                value(cs, fontBold, lx + 148, ly, booking.getTicketsBooked() + " ticket(s)");

                ly -= 34;
                cs.setStrokingColor(new PDColor(PERF_LINE, PDDeviceRGB.INSTANCE));
                cs.moveTo(lx, ly); cs.lineTo(splitX - M - 8, ly); cs.stroke();
                ly -= 12;
                label(cs, fontRegular, lx, ly, "Attendee");
                ly -= 10;
                writeText(cs, fontBold, 11, WHITE, lx, ly, truncate(user.getName(), 22));
                ly -= 14;
                writeText(cs, fontRegular, 8, GREY, lx, ly, truncate(user.getEmail(), 30));

                float rx = splitX + 12;
                float ry  = bodyY - 14;

                label(cs, fontRegular, rx, ry, "Booking ID");
                ry -= 10;
                writeText(cs, fontBold, 16, PURPLE, rx, ry, "#" + booking.getId());
                ry -= 40;

                label(cs, fontRegular, rx, ry, "Payment Summary");
                ry -= 14;
                writeText(cs, fontRegular, 8, GREY, rx, ry, "Ticket Amount");
                writeText(cs, fontRegular, 8, WHITE, W - M - 4, ry,
                        String.format("\u20B9%.2f", booking.getTicketAmount() != null ? booking.getTicketAmount() : 0.0));
                ry -= 12;
                writeText(cs, fontRegular, 8, GREY, rx, ry, "Convenience Fee");
                writeText(cs, fontRegular, 8, WHITE, W - M - 4, ry,
                        String.format("\u20B9%.2f", booking.getConvenienceFee() != null ? booking.getConvenienceFee() : 0.0));
                ry -= 12;
                writeText(cs, fontRegular, 8, GREY, rx, ry, "GST (9%)");
                writeText(cs, fontRegular, 8, WHITE, W - M - 4, ry,
                        String.format("\u20B9%.2f", booking.getGstAmount() != null ? booking.getGstAmount() : 0.0));

                ry -= 6;
                cs.setStrokingColor(new PDColor(PERF_LINE, PDDeviceRGB.INSTANCE));
                cs.moveTo(rx, ry); cs.lineTo(W - M, ry); cs.stroke();
                ry -= 10;
                writeText(cs, fontBold, 9, GREY, rx, ry, "Total Paid");
                writeText(cs, fontBold, 9, WHITE, W - M - 4, ry,
                        String.format("\u20B9%.2f", booking.getTotalPaid() != null ? booking.getTotalPaid() : 0.0));

                ry -= 20;
                fillRect(cs, hex("#14532d"), rx, ry - 4, W - splitX - M - 8, 20);
                writeText(cs, fontBold, 8, hex("#4ade80"), rx + 4, ry + 8, "\u2714  CONFIRMED");

                writeText(cs, fontRegular, 8, GREY, M, 20,
                        "This is your official e-ticket. Present this at the venue entry.");
                writeText(cs, fontRegular, 7, PERF_LINE, M, 10,
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

        final float[] BRAND   = hex("#6c63ff");
        final float[] DARK    = hex("#0f0f1a");
        final float[] MID     = hex("#1a1a2e");
        final float[] MUTED   = hex("#6b7280");
        final float[] LIGHT   = hex("#f9fafb");
        final float[] SUCCESS = hex("#16a34a");
        final float[] DIVIDER = hex("#e5e7eb");

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
        double unitPrice = event.getPrice()            != null ? event.getPrice()            : 0.0;
        int    numTix    = booking.getTicketsBooked()  != null ? booking.getTicketsBooked()  : 0;

        String seatsDisplay = parseSeats(booking.getSelectedSeats());
        if (seatsDisplay == null) seatsDisplay = "General Admission";

        try (PDDocument doc = new PDDocument()) {
            PDType0Font fontReg  = loadFont(doc, FONT_REGULAR);
            PDType0Font fontBold = loadFont(doc, FONT_BOLD);

            PDPage page = new PDPage(new PDRectangle(AW, AH));
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                // White background
                fillRect(cs, WHITE, 0, 0, AW, AH);

                // ── Header band ──────────────────────────────────────────────
                fillRect(cs, DARK, 0, AH - 88, AW, 88);
                fillRect(cs, BRAND, 0, AH - 88, 6, 88);
                writeText(cs, fontBold, 18, BRAND, AM, AH - 28, "TicketVerse");
                writeText(cs, fontReg,  8, hex("#a78bfa"), AM, AH - 50, "ticketverse.in  \u2022  support@ticketverse.in");
                writeText(cs, fontBold, 20, WHITE, AW - AM - 130, AH - 26, "INVOICE");
                writeText(cs, fontReg,  8, hex("#a78bfa"), AW - AM - 130, AH - 48, invoiceNumber);

                float y = AH - 110;

                // ── Billed To + Invoice Details ──────────────────────────────
                writeText(cs, fontBold, 7, BRAND, AM, y, "BILLED TO");
                writeText(cs, fontBold, 12, DARK, AM, y - 18, user.getName() != null ? user.getName() : "");
                writeText(cs, fontReg,  9, MUTED, AM, y - 34, user.getEmail() != null ? user.getEmail() : "");

                float rCol = AW - AM - 190;
                writeText(cs, fontBold, 7, BRAND, rCol, y, "INVOICE DETAILS");
                // Meta rows
                metaRow(cs, fontReg, fontBold, MUTED, DARK, rCol, y - 16, "Invoice No.",  invoiceNumber);
                metaRow(cs, fontReg, fontBold, MUTED, DARK, rCol, y - 30, "Issue Date",   issueDate);
                metaRow(cs, fontReg, fontBold, MUTED, DARK, rCol, y - 44, "Booking ID",   "#" + booking.getId());
                metaRow(cs, fontReg, fontBold, MUTED, DARK, rCol, y - 58, "Status",       "PAID");

                y -= 82;
                // Divider
                cs.setStrokingColor(new PDColor(DIVIDER, PDDeviceRGB.INSTANCE));
                cs.setLineWidth(0.8f);
                cs.moveTo(AM, y); cs.lineTo(AW - AM, y); cs.stroke();
                y -= 22;

                // ── Event Details section ────────────────────────────────────
                writeText(cs, fontBold, 7, BRAND, AM, y, "EVENT DETAILS");
                y -= 14;
                fillRect(cs, LIGHT, AM, y - 60, AW - 2 * AM, 60);
                writeText(cs, fontBold, 13, DARK, AM + 16, y - 16, truncate(event.getTitle(), 50));
                String eventMeta = eventDateStr + "  \u2022  "
                        + (event.getLocation() != null ? event.getLocation() : "TBA")
                        + "  \u2022  " + (event.getCategory() != null ? event.getCategory() : "");
                writeText(cs, fontReg, 8, MUTED, AM + 16, y - 36, truncate(eventMeta, 72));
                y -= 78;

                // ── Billing Breakdown table ──────────────────────────────────
                writeText(cs, fontBold, 7, BRAND, AM, y, "BILLING BREAKDOWN");
                y -= 12;
                float rowH = 22f;
                float CW   = AW - 2 * AM;
                float colQty   = AM + 270;
                float colRate  = AM + 345;
                float colTotal = AM + 445;

                // Header row
                fillRect(cs, MID, AM, y - rowH, CW, rowH);
                writeText(cs, fontBold, 8, WHITE, AM + 8,      y - 14, "Description");
                writeText(cs, fontBold, 8, WHITE, colQty,      y - 14, "Qty");
                writeText(cs, fontBold, 8, WHITE, colRate,     y - 14, "Unit Price");
                writeText(cs, fontBold, 8, WHITE, colTotal,    y - 14, "Amount");
                y -= rowH;

                // Row 1: tickets
                fillRect(cs, WHITE, AM, y - rowH, CW, rowH);
                writeText(cs, fontReg, 8, DARK, AM + 8, y - 14,
                        truncate("Event Ticket \u2014 " + event.getTitle(), 45));
                writeText(cs, fontReg, 8, DARK, colQty,  y - 14, String.valueOf(numTix));
                writeText(cs, fontReg, 8, DARK, colRate, y - 14, String.format("\u20B9%.2f", unitPrice));
                writeText(cs, fontReg, 8, DARK, colTotal,y - 14, String.format("\u20B9%.2f", ticketAmt));
                y -= rowH;

                // Row 2: convenience fee
                fillRect(cs, LIGHT, AM, y - rowH, CW, rowH);
                writeText(cs, fontReg, 8, DARK, AM + 8, y - 14, "Convenience Fee (10% of ticket amount)");
                writeText(cs, fontReg, 8, DARK, colQty,  y - 14, "1");
                writeText(cs, fontReg, 8, DARK, colRate, y - 14, String.format("\u20B9%.2f", convFee));
                writeText(cs, fontReg, 8, DARK, colTotal,y - 14, String.format("\u20B9%.2f", convFee));
                y -= rowH;

                // Row 3: GST
                fillRect(cs, WHITE, AM, y - rowH, CW, rowH);
                writeText(cs, fontReg, 8, DARK, AM + 8, y - 14, "GST on Convenience Fee (9%)");
                writeText(cs, fontReg, 8, DARK, colQty,  y - 14, "1");
                writeText(cs, fontReg, 8, DARK, colRate, y - 14, String.format("\u20B9%.2f", gstAmt));
                writeText(cs, fontReg, 8, DARK, colTotal,y - 14, String.format("\u20B9%.2f", gstAmt));
                y -= rowH;

                // ── Totals block (right-aligned) ─────────────────────────────
                y -= 10;
                float totBlockW = 220f;
                float totBlockX = AW - AM - totBlockW;

                totRow(cs, fontReg,  fontBold, MUTED, DARK, totBlockX, totBlockW, y, false, "Ticket Amount",   String.format("\u20B9%.2f", ticketAmt));
                y -= 16;
                totRow(cs, fontReg,  fontBold, MUTED, DARK, totBlockX, totBlockW, y, false, "Convenience Fee", String.format("\u20B9%.2f", convFee));
                y -= 16;
                totRow(cs, fontReg,  fontBold, MUTED, DARK, totBlockX, totBlockW, y, false, "GST (9%)",        String.format("\u20B9%.2f", gstAmt));
                y -= 6;
                cs.setStrokingColor(new PDColor(DIVIDER, PDDeviceRGB.INSTANCE));
                cs.setLineWidth(0.8f);
                cs.moveTo(totBlockX, y); cs.lineTo(AW - AM, y); cs.stroke();
                y -= 10;
                totRow(cs, fontBold, fontBold, MUTED, DARK, totBlockX, totBlockW, y, true, "Total Paid", String.format("\u20B9%.2f", totalPaid));

                // ── Payment info chip ────────────────────────────────────────
                y -= 18;
                fillRect(cs, LIGHT, AM, y - 44, CW, 44);
                writeText(cs, fontBold, 7, BRAND, AM + 16, y - 12, "PAYMENT INFORMATION");
                String payInfo = "Payment ID: " + (booking.getRazorpayPaymentId() != null ? booking.getRazorpayPaymentId() : "N/A")
                        + "   \u2022   Order ID: " + (booking.getRazorpayOrderId() != null ? booking.getRazorpayOrderId() : "N/A")
                        + "   \u2022   Method: Razorpay";
                writeText(cs, fontReg, 8, MUTED, AM + 16, y - 28, truncate(payInfo, 80));

                // ── Status badge ─────────────────────────────────────────────
                y -= 58;
                float badgeW = 170f;
                float badgeX = (AW - badgeW) / 2f;
                fillRect(cs, hex("#dcfce7"), badgeX, y - 20, badgeW, 24);
                writeText(cs, fontBold, 9, SUCCESS, badgeX + 14, y - 11, "\u2714  PAYMENT CONFIRMED");

                // ── Seats + booking date ─────────────────────────────────────
                y -= 36;
                writeText(cs, fontReg, 8, MUTED, AM, y,
                        "Seat(s): " + seatsDisplay + "  \u2022  Booking Date: " + bookingDateStr);

                // ── Footer band ──────────────────────────────────────────────
                fillRect(cs, DARK, 0, 0, AW, 52);
                fillRect(cs, BRAND, 0, 0, 6, 52);
                writeText(cs, fontReg, 7, hex("#6b7280"), AM, 36,
                        "Thank you for booking with TicketVerse! For support, reach us at support@ticketverse.in");
                writeText(cs, fontReg, 7, hex("#374151"), AM, 20,
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
                writeText(cs, fontBold, 10, PURPLE, AM, AH - 25, "TicketVerse");
                writeText(cs, fontBold, 18, WHITE,  AM, AH - 52, "Cancellation Invoice");
                writeText(cs, fontBold, 8,  hex("#a78bfa"), AM, AH - 68, invoiceNumber);

                String statusLabel = status.toUpperCase().replace("_", " ");
                float[] statusColor = "refunded".equals(status) ? GREEN2
                                    : "refund_pending".equals(status) ? ORANGE : RED;
                writeText(cs, fontBold, 10, statusColor, AW - AM - 110, AH - 40, "STATUS: " + statusLabel);

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
                writeText(cs, fontBold, 9, DARK, AW - AM - 80, y + 2, "Amount");

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
                    writeText(cs, fontBold, 9, color, AW - AM - 80, y + 4,
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

    /** Inline helper: label + value pair on same x-offset, right-aligned value. */
    private void metaRow(PDPageContentStream cs,
                         PDType0Font fontReg, PDType0Font fontBold,
                         float[] labelColor, float[] valColor,
                         float x, float y, String label, String value) throws IOException {
        writeText(cs, fontReg,  8, labelColor, x,        y, label);
        writeText(cs, fontBold, 8, valColor,   x + 105,  y, value);
    }

    /** Totals block row: label left-aligned at x, value right-aligned at x+totBlockW. */
    private void totRow(PDPageContentStream cs,
                        PDType0Font labelFont, PDType0Font valFont,
                        float[] labelColor, float[] valColor,
                        float x, float blockW, float y, boolean bold,
                        String label, String value) throws IOException {
        writeText(cs, labelFont, 9, labelColor, x, y, label);
        writeText(cs, valFont,   9, valColor,   x + blockW - 60, y, value);
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

    private String parseSeats(String json) {
        if (json == null || json.equals("[]") || json.isBlank()) return null;
        return json.replace("[", "").replace("]", "").replace("\"", "").replace(",", ",  ");
    }
}
