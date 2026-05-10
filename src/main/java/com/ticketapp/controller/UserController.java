package com.ticketapp.controller;

import com.ticketapp.dto.UserProfileDto;
import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * UserController — user profile management (Feature 10).
 *
 * All endpoints require authentication. Role checks use the same manual
 * pattern as OrganizerController to avoid Spring Security filter-level
 * response-stream conflicts.
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    // ── GET /user/profile ─────────────────────────────────────────────────────

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated."));
        return ResponseEntity.ok(userService.getProfileMap(user.getId()));
    }

    // ── PUT /user/profile ─────────────────────────────────────────────────────

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @RequestBody UserProfileDto body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated."));
        try {
            Map<String, Object> updated = userService.updateProfile(
                user.getId(), body.getName(), body.getPhone(), body.getDate_of_birth());
            log.info("Profile updated: userId={}", user.getId());
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── POST /user/avatar (multipart/form-data) ───────────────────────────────

    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated."));
        if (file.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "No file provided."));

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/"))
            return ResponseEntity.badRequest().body(Map.of("error", "Only image files are allowed."));

        String ext = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png"  -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };

        try {
            Map<String, Object> updated = userService.uploadAvatar(
                user.getId(), file.getBytes(), contentType, ext);
            log.info("Avatar uploaded: userId={}", user.getId());
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Avatar upload failed for userId={}: {}", user.getId(), e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Avatar upload failed."));
        }
    }
}
