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
        putObject(key, pdfBytes);
        log.info("Ticket PDF uploaded to S3: {}", key);
        return key;
    }

    public byte[] fetchTicket(String s3Key) throws IOException {
        return getObject(s3Key);
    }

    // ── Cancellation Invoice PDF ──────────────────────────────────────────────

    public String uploadCancellationInvoice(byte[] pdfBytes, Long bookingId, Long userId) {
        String key = "cancellations/invoice-booking-" + bookingId + "-user-" + userId + ".pdf";
        putObject(key, pdfBytes);
        log.info("Cancellation invoice uploaded to S3: {}", key);
        return key;
    }

    public byte[] fetchCancellationInvoice(String s3Key) throws IOException {
        return getObject(s3Key);
    }

    // ── Event Images ──────────────────────────────────────────────────────────

    /**
     * Uploads an event image to S3 under events/images/{uuid}.{ext}.
     * Returns the S3 key (not a full URL) so callers build the proxy URL:
     *   "/api/images/" + key  →  served by ImageController
     *
     * @param imageBytes  raw bytes of the image file
     * @param contentType MIME type (image/jpeg, image/png, image/webp)
     * @param ext         file extension without dot (jpg, png, webp)
     * @return            S3 key e.g. "events/images/abc123.jpg"
     */
    public String uploadEventImage(byte[] imageBytes, String contentType, String ext) {
        String key = "events/images/" + UUID.randomUUID() + "." + ext;
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                // Allow browsers to cache event images aggressively (1 year).
                // Keys are UUID-based so old URLs naturally expire when re-uploaded.
                .cacheControl("public, max-age=31536000, immutable")
                .build(),
            RequestBody.fromBytes(imageBytes));
        log.info("Event image uploaded to S3: {}", key);
        return key;
    }

    /**
     * Fetches an event image from S3 by its key.
     * Used by the ImageController proxy endpoint so the S3 bucket does not need
     * to be publicly accessible.
     *
     * @param key  S3 key returned by uploadEventImage (e.g. "events/images/abc123.jpg")
     * @return     raw image bytes
     */
    public byte[] fetchEventImage(String key) throws IOException {
        return s3Client.getObjectAsBytes(
            GetObjectRequest.builder().bucket(bucket).key(key).build()
        ).asByteArray();
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private void putObject(String key, byte[] bytes) {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket).key(key)
                .contentType("application/pdf")
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
