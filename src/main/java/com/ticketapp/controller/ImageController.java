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

/**
 * ImageController — upload and serve event images.
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  S3 configured  (S3_BUCKET_NAME env var set)                        │
 * │    → Images uploaded to S3 under events/images/{uuid}.{ext}        │
 * │    → GET /api/images/** proxies from S3                             │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │  S3 NOT configured  (local dev / bucket name empty)                 │
 * │    → Images saved to LOCAL_IMAGE_DIR on disk                        │
 * │    → GET /api/images/** reads from same directory                   │
 * │    → Full image upload and display works with zero AWS setup        │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * LOCAL_IMAGE_DIR = {user.home}/.ticketapp/event-images/
 *   Survives app restarts (unlike in-memory).
 *   Safe on Windows (no permission issues unlike /tmp on some systems).
 *   Ignored by .gitignore — never committed accidentally.
 */
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@Slf4j
public class ImageController {

    private final S3Service s3Service;

    /** Empty string when S3_BUCKET_NAME env var is not set — signals "use local storage". */
    @Value("${aws.s3.bucket:}")
    private String s3Bucket;

    private static final Set<String> ALLOWED_CONTENT_TYPES =
        Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;   // 5 MB

    /**
     * Local image directory — only used when S3 is not configured.
     * Stored in the user's home directory so it works on Windows, macOS, and Linux
     * without needing elevated permissions.
     */
    private static final Path LOCAL_IMAGE_DIR =
        Paths.get(System.getProperty("user.home"), ".ticketapp", "event-images");

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * POST /api/images/upload  (authenticated)
     *
     * Accepts multipart/form-data with field "file".
     * Returns: { "url": "/api/images/events/images/{uuid}.ext",
     *            "key": "events/images/{uuid}.ext" }
     *
     * Tries S3 first when configured; silently falls back to local disk otherwise.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadEventImage(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user) {

        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated."));

        // ── Validate content type ─────────────────────────────────────────────
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase()))
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Only JPEG, PNG, and WebP images are allowed."));

        // ── Validate file size ────────────────────────────────────────────────
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
                // ── S3 path ───────────────────────────────────────────────────
                key = s3Service.uploadEventImage(bytes, contentType, ext);
                log.info("Event image uploaded to S3: key={} userId={} size={}B",
                         key, user.getId(), bytes.length);
            } else {
                // ── Local disk fallback ───────────────────────────────────────
                key = saveLocally(bytes, ext);
                log.warn("S3 not configured — image saved locally: key={} userId={}. "
                       + "Set S3_BUCKET_NAME to enable S3 storage.", key, user.getId());
            }

            return ResponseEntity.ok(Map.of("url", "/api/images/" + key, "key", key));

        } catch (Exception e) {
            log.error("Event image upload failed: userId={} s3Configured={} error={}",
                      user.getId(), isS3Configured(), e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "error", "Image upload failed: " + e.getMessage()));
        }
    }

    // ── Serve / Proxy ─────────────────────────────────────────────────────────

    /**
     * GET /api/images/events/images/{uuid}.ext  (public — no auth required)
     *
     * Serves from S3 when configured, local disk otherwise.
     * 1-year immutable cache header on both paths — UUID keys never repeat.
     */
    @GetMapping("/**")
    public ResponseEntity<byte[]> serveEventImage(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String key = uri.replaceFirst("^/api/images/", "");

        if (key.isBlank())
            return ResponseEntity.badRequest().build();

        try {
            byte[] bytes;

            if (isS3Configured()) {
                bytes = s3Service.fetchEventImage(key);
            } else {
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
            log.warn("Event image not found: key={} s3Configured={} error={}",
                     key, isS3Configured(), e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    // ── Local storage helpers ─────────────────────────────────────────────────

    /**
     * Saves image bytes to LOCAL_IMAGE_DIR and returns the S3-style key.
     * The key format matches the S3 path: "events/images/{uuid}.{ext}"
     * so the same URL structure works for both storage backends.
     */
    private String saveLocally(byte[] bytes, String ext) throws IOException {
        Files.createDirectories(LOCAL_IMAGE_DIR);
        String filename = UUID.randomUUID() + "." + ext;
        Path   target   = LOCAL_IMAGE_DIR.resolve(filename);
        Files.write(target, bytes);
        return "events/images/" + filename;   // matches S3 key format
    }

    /**
     * Reads an image from LOCAL_IMAGE_DIR by its key.
     * key format: "events/images/{filename}.{ext}"
     * We only need the filename portion — strip the directory prefix.
     */
    private byte[] readLocally(String key) throws IOException {
        // key = "events/images/abc123.jpg"  →  filename = "abc123.jpg"
        String filename = Paths.get(key).getFileName().toString();
        Path   file     = LOCAL_IMAGE_DIR.resolve(filename);

        if (!Files.exists(file))
            throw new IOException("Image not found locally: " + filename);

        return Files.readAllBytes(file);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * S3 is considered configured when S3_BUCKET_NAME env var is set and non-empty.
     * This is the same signal the original EventService uses to decide whether
     * to call S3 for ticket PDF storage.
     */
    private boolean isS3Configured() {
        return s3Bucket != null && !s3Bucket.isBlank();
    }
}
