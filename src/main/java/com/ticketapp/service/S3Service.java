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

    public String buildTicketKey(Long bookingId, Long userId) {
        return "tickets/booking-" + bookingId + "-user-" + userId + ".pdf";
    }

    public String uploadTicket(byte[] pdfBytes, Long bookingId, Long userId) {
        String key = buildTicketKey(bookingId, userId);
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/pdf")
                .serverSideEncryption("AES256")
                .build();

        s3Client.putObject(req, RequestBody.fromBytes(pdfBytes));
        log.info("Ticket PDF uploaded to S3: {}", key);
        return key;
    }

    public byte[] fetchTicket(String s3Key) throws IOException {
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        return s3Client.getObjectAsBytes(req).asByteArray();
    }
}
