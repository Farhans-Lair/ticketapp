package com.ticketapp.controller;

import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.service.S3Service;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;

/**
 * ImageController — two responsibilities:
 *
 * 1. POST /api/images/upload (authenticated)
 *    Accepts a single image file (multipart/form-data), uploads it to S3
 *    under events/images/{uuid}.{ext}, and returns the proxy URL.
 *    Only authenticated users (organizers + admins) can upload.
 *
 * 2. GET /api/images/** (public)
 *    Fetches an event image from S3 by its key and streams it to the browser.
 *    This proxy removes the need for a publicly accessible S3 bucket and adds
 *    strong caching headers so the browser caches images for a full year.
 *
 * Why a proxy instead of direct S3 URLs?
 *   The existing S3 bucket is private (tickets are served the same way).
 *   Keeping one consistent access pattern is simpler than mixing public/private.
 *   In production you can layer CloudFront in front of these proxy URLs with
 *   zero changes to the stored keys or the frontend.
 */
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@Slf4j
public class ImageController {

    private final S3Service s3Service;

    private static final Set<String> ALLOWED_CONTENT_TYPES =
        Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");

    /** 5 MB — generous enough for any reasonable event photo after browser resize. */
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * POST /api/images/upload
     *
     * Request:  multipart/form-data with field "file"
     * Response: { "url": "/api/images/events/images/{uuid}.jpg",
     *             "key": "events/images/{uuid}.jpg" }
     *
     * The "url" is what the frontend stores in its image list and what gets
     * persisted in events.images as part of the JSON array. The "key" is
     * returned for completeness (useful if you later add direct S3 signed URLs).
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadEventImage(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user) {

        // ── Auth check (belt-and-suspenders; SecurityConfig also enforces this) ─
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated."));

        // ── Validate content type ─────────────────────────────────────────────
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Only JPEG, PNG, and WebP images are allowed."));
        }

        // ── Validate file size ────────────────────────────────────────────────
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Image must be under 5 MB."));
        }

        // ── Derive extension from content type ────────────────────────────────
        String ext = switch (contentType.toLowerCase()) {
            case "image/png"  -> "png";
            case "image/webp" -> "webp";
            default           -> "jpg";    // image/jpeg, image/jpg
        };

        try {
            byte[] bytes = file.getBytes();
            String key   = s3Service.uploadEventImage(bytes, contentType, ext);
            String url   = "/api/images/" + key;

            log.info("Event image uploaded: key={} userId={} size={}B",
                     key, user.getId(), bytes.length);

            return ResponseEntity.ok(Map.of("url", url, "key", key));

        } catch (Exception e) {
            log.error("Event image upload failed: userId={} error={}", user.getId(), e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to upload image. Please check your S3 configuration."));
        }
    }

    // ── Serve / Proxy ─────────────────────────────────────────────────────────

    /**
     * GET /api/images/events/images/{uuid}.jpg   (and any sub-path under /api/images/)
     *
     * Fetches the image from S3 and streams it to the browser.
     * Sends a 1-year immutable cache header — since keys are UUID-based,
     * each uploaded image has a unique, permanent URL.
     *
     * Permitted without authentication (SecurityConfig): event images need to be
     * viewable by any logged-in user on the events page, and sharing an image URL
     * should not leak any sensitive data.
     */
    @GetMapping("/**")
    public ResponseEntity<byte[]> serveEventImage(HttpServletRequest request) {
        // Strip the controller prefix to get the raw S3 key.
        // URI:  /api/images/events/images/abc123.jpg
        // Key:  events/images/abc123.jpg
        String uri = request.getRequestURI();
        String key = uri.replaceFirst("^/api/images/", "");

        if (key.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            byte[] bytes = s3Service.fetchEventImage(key);

            // Determine response media type from file extension
            MediaType mediaType = MediaType.IMAGE_JPEG;
            if      (key.endsWith(".png"))  mediaType = MediaType.IMAGE_PNG;
            else if (key.endsWith(".webp")) mediaType = MediaType.parseMediaType("image/webp");

            return ResponseEntity.ok()
                .contentType(mediaType)
                // 1-year immutable cache — UUID keys never collide so there is no
                // stale-content risk. Browsers skip the network entirely on repeat views.
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .body(bytes);

        } catch (Exception e) {
            log.warn("Event image not found or S3 error: key={} error={}", key, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
