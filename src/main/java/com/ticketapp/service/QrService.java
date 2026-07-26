package com.ticketapp.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.EnumMap;
import java.util.Map;

@Service
@Slf4j
public class QrService {

    @Value("${qr.jwt.secret:changeme-replace-in-production-min-32-chars}")
    private String jwtSecret;

    /* QR validity window — generous to cover long festival gaps. */
    private static final long TOKEN_VALIDITY_DAYS = 365;
    private static final int  QR_SIZE_PX          = 250;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        // HMAC-SHA256 requires at least 256 bits (32 bytes)
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("QrService initialized — signing key loaded.");
    }

    /* Creates a signed JWT for the given booking. */
    public String generateToken(Long bookingId, Long userId, Long eventId) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + TOKEN_VALIDITY_DAYS * 24L * 3600L * 1000L);

        return Jwts.builder()
                .subject(String.valueOf(bookingId))
                .claim("userId",  userId)
                .claim("eventId", eventId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /* Encodes a JWT string as a QR-code PNG. */
    public byte[] generateQrPng(String token) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(token, BarcodeFormat.QR_CODE,
                    QR_SIZE_PX, QR_SIZE_PX, hints);

            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "PNG", bos);
            return bos.toByteArray();

        } catch (Exception e) {
            log.error("QR generation failed: {}", e.getMessage());
            throw new RuntimeException("Failed to generate QR code: " + e.getMessage());
        }
    }

    /* Generates QR PNG and returns it as a Base64 Data URI. */
    public String generateQrDataUri(String token) {
        byte[] png = generateQrPng(token);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }

    /* Verifies a QR token scanned by the organiser check-in device. */
    public Claims verifyToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /* Convenience: extract bookingId from a raw token without full validation. */
    public Long extractBookingId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }
}
