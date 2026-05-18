package com.ticketapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    // ── Ticket PDF ────────────────────────────────────────────────────────────

    public String uploadTicket(byte[] pdfBytes, Long bookingId, Long userId) {
        String key = "tickets/booking-" + bookingId + "-user-" + userId + ".pdf";
        putObject(key, pdfBytes, "application/pdf");
        log.info("Ticket PDF uploaded to S3: {}", key);
        return key;
    }

    public byte[] fetchTicket(String s3Key) throws IOException {
        return getObject(s3Key);
    }

    // ── Booking Invoice PDF (added — mirrors TBA2 uploadInvoiceToS3 "booking") ─

    /**
     * Uploads a booking invoice PDF to S3 under invoices/booking-{bookingId}-user-{userId}.pdf.
     * Mirrors TBA2's uploadInvoiceToS3(buffer, bookingId, userId, "booking").
     */
    public String uploadBookingInvoice(byte[] pdfBytes, Long bookingId, Long userId) {
        String key = "invoices/booking-" + bookingId + "-user-" + userId + ".pdf";
        putObject(key, pdfBytes, "application/pdf");
        log.info("Booking invoice PDF uploaded to S3: {}", key);
        return key;
    }

    public byte[] fetchBookingInvoice(String s3Key) throws IOException {
        return getObject(s3Key);
    }

    // ── Cancellation Invoice PDF ──────────────────────────────────────────────

    public String uploadCancellationInvoice(byte[] pdfBytes, Long bookingId, Long userId) {
        String key = "cancellations/invoice-booking-" + bookingId + "-user-" + userId + ".pdf";
        putObject(key, pdfBytes, "application/pdf");
        log.info("Cancellation invoice uploaded to S3: {}", key);
        return key;
    }

    public byte[] fetchCancellationInvoice(String s3Key) throws IOException {
        return getObject(s3Key);
    }

    // ── Event Images ──────────────────────────────────────────────────────────

    public String uploadEventImage(byte[] imageBytes, String contentType, String ext) {
        String key = "events/images/" + UUID.randomUUID() + "." + ext;
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket).key(key).contentType(contentType)
                .cacheControl("public, max-age=31536000, immutable")
                .build(),
            RequestBody.fromBytes(imageBytes));
        log.info("Event image uploaded to S3: {}", key);
        return key;
    }

    public byte[] fetchEventImage(String key) throws IOException {
        return s3Client.getObjectAsBytes(
            GetObjectRequest.builder().bucket(bucket).key(key).build()
        ).asByteArray();
    }

    // ── Feature 10: User Avatars ──────────────────────────────────────────────

    public String uploadAvatar(byte[] imageBytes, Long userId, String contentType, String ext) {
        String key = "avatars/user-" + userId + "." + ext;
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket).key(key).contentType(contentType)
                .cacheControl("public, max-age=86400")
                .build(),
            RequestBody.fromBytes(imageBytes));
        log.info("Avatar uploaded to S3 for userId={}: key={}", userId, key);
        return key;
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private void putObject(String key, byte[] bytes, String contentType) {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket).key(key)
                .contentType(contentType)
                .serverSideEncryption("AES256")
                .build(),
            RequestBody.fromBytes(bytes));
    }

    private byte[] getObject(String key) throws IOException {
        return s3Client.getObjectAsBytes(
            GetObjectRequest.builder().bucket(bucket).key(key).build()
        ).asByteArray();
    }
}
