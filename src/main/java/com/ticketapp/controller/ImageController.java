package com.ticketapp.controller;

import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.service.S3Service;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/* ImageController — upload and serve event images. */
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@Slf4j
public class ImageController {

    private final S3Service s3Service;

    /* Empty string when S3_BUCKET_NAME env var is not set — signals "use local storage". */
    @Value("${aws.s3.bucket:}")
    private String s3Bucket;

    private static final Set<String> ALLOWED_CONTENT_TYPES =
        Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;     // 5 MB

    /* Local image directory — only used when S3 is not configured. */
    private static final Path LOCAL_IMAGE_DIR =
        Paths.get(System.getProperty("user.home"), ".ticketapp", "event-images");

    // Upload

    /* POST /api/images/upload (authenticated) Accepts multipart/form-data with field "file". */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadEventImage(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user) {

        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated."));

        // Validate content type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase()))
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Only JPEG, PNG, and WebP images are allowed."));

        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE_BYTES)
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Image must be under 5 MB."));

        String ext = switch (contentType.toLowerCase()) {
            case "image/png"  -> "png";
            case "image/webp" -> "webp";
            default           -> "jpg";
        };

        try {
            byte[] bytes = file.getBytes();
            String key;

            if (isS3Configured()) {
                try {
                    // S3 path
                    key = s3Service.uploadEventImage(bytes, contentType, ext);
                    log.info("Event image uploaded to S3: key={} userId={} size={}B",
                             key, user.getId(), bytes.length);
                } catch (Exception s3Ex) {
                    // S3 configured but failed (bad credentials, network, permissions).
                    log.warn("S3 upload failed ({}), falling back to local disk for userId={}. "
                           + "Check AWS_ACCESS_KEY_ID / IAM role permissions.",
                             s3Ex.getMessage(), user.getId());
                    key = saveLocally(bytes, ext);
                }
            } else {
                // Local disk fallback (S3_BUCKET_NAME not set)
                key = saveLocally(bytes, ext);
                log.warn("S3 not configured — image saved locally: key={} userId={}. "
                       + "Set S3_BUCKET_NAME to enable S3 storage.", key, user.getId());
            }

            return ResponseEntity.ok(Map.of("url", "/api/images/" + key, "key", key));

        } catch (Exception e) {
            // Only reached for errors outside the S3/local path (e.g.
            log.error("Image upload failed (non-S3 error): userId={} error={}", user.getId(), e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "error", "Image upload failed: " + e.getMessage()));
        }
    }

    // Serve / Proxy

    @GetMapping("/**")
    public ResponseEntity<byte[]> serveEventImage(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String key = uri.replaceFirst("^/api/images/", "");

        if (key.isBlank())
            return ResponseEntity.badRequest().build();

        try {
            byte[] bytes;

            if (isS3Configured()) {
                try {
                    // S3 path
                    bytes = s3Service.fetchEventImage(key);
                } catch (Exception s3Ex) {
                    // S3 configured but unreachable/forbidden — fall back to local disk.
                    log.warn("S3 fetch failed ({}), trying local disk for key={}",
                             s3Ex.getMessage(), key);
                    bytes = readLocally(key);
                }
            } else {
                // Local disk (S3_BUCKET_NAME not set)
                bytes = readLocally(key);
            }

            MediaType mediaType = MediaType.IMAGE_JPEG;
            if      (key.endsWith(".png"))  mediaType = MediaType.IMAGE_PNG;
            else if (key.endsWith(".webp")) mediaType = MediaType.parseMediaType("image/webp");

            return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .body(bytes);

        } catch (Exception e) {
            log.warn("Event image not found on S3 or local disk: key={} error={}",
                     key, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    // Local storage helpers

    /* Saves image bytes to LOCAL_IMAGE_DIR and returns the S3-style key. */
    private String saveLocally(byte[] bytes, String ext) throws IOException {
        Files.createDirectories(LOCAL_IMAGE_DIR);
        String filename = UUID.randomUUID() + "." + ext;
        Path   target   = LOCAL_IMAGE_DIR.resolve(filename);
        Files.write(target, bytes);
        return "events/images/" + filename;     // matches S3 key format
    }

    /* Reads an image from LOCAL_IMAGE_DIR by its key. */
    private byte[] readLocally(String key) throws IOException {
        // key = "events/images/abc123.jpg" → filename = "abc123.jpg"
        String filename = Paths.get(key).getFileName().toString();
        Path   file     = LOCAL_IMAGE_DIR.resolve(filename);

        if (!Files.exists(file))
            throw new IOException("Image not found locally: " + filename);

        return Files.readAllBytes(file);
    }

    // Helper

    /* S3 is considered configured when S3_BUCKET_NAME env var is set and non-empty. */
    private boolean isS3Configured() {
        return s3Bucket != null && !s3Bucket.isBlank();
    }
}
