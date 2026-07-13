package com.ticketapp.controller;

import com.ticketapp.dto.UserProfileDto;
import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * UserController — user profile management.
 *
 * GET  /user/profile                — fetch profile (with booking summary counts)
 * PUT  /user/profile                — update name, phone, bio, bank_details, date_of_birth
 * PUT  /user/profile/password       — change password
 * POST /user/avatar                 — upload/replace avatar image
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Slf4j
@Validated
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
            @Valid @RequestBody UserProfileDto body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated."));
        Map<String, Object> updated = userService.updateProfile(
            user.getId(),
            body.getName(),
            body.getPhone(),
            body.getDate_of_birth(),
            body.getBio(),
            body.getBank_details());
        log.info("Profile updated: userId={}", user.getId());
        return ResponseEntity.ok(updated);
    }

    // ── PUT /user/profile/password ────────────────────────────────────────────

    @PutMapping("/profile/password")
    public ResponseEntity<?> changePassword(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated."));

        String current = body.get("current_password");
        String next    = body.get("new_password");

        if (current == null || current.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is required."));
        if (next == null || next.length() < 8)
            return ResponseEntity.badRequest().body(Map.of("error", "New password must be at least 8 characters."));

        userService.changePassword(user.getId(), current, next);
        log.info("Password changed: userId={}", user.getId());
        return ResponseEntity.ok(Map.of("message", "Password updated successfully."));
    }

    // ── POST /user/avatar ─────────────────────────────────────────────────────

    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated."));
        if (file.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "No file provided."));
        if (file.getSize() > 5 * 1024 * 1024)
            return ResponseEntity.badRequest().body(Map.of("error", "Avatar must be 5 MB or smaller."));

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
