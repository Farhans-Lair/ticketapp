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
