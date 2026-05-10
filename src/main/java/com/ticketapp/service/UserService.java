package com.ticketapp.service;

import com.ticketapp.entity.User;
import com.ticketapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepo;
    private final S3Service      s3Service;

    // ── Profile read ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getProfileMap(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found."));
        return toMap(user);
    }

    // ── Profile update ────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> updateProfile(Long userId, String name, String phone,
                                              String dateOfBirthStr) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (name != null && !name.isBlank()) user.setName(name.trim());
        if (phone != null)                   user.setPhone(phone.isBlank() ? null : phone.trim());
        if (dateOfBirthStr != null && !dateOfBirthStr.isBlank()) {
            try {
                user.setDateOfBirth(LocalDate.parse(dateOfBirthStr));
            } catch (Exception e) {
                throw new RuntimeException("Invalid date_of_birth format. Use YYYY-MM-DD.");
            }
        }

        User saved = userRepo.save(user);
        log.info("User profile updated: userId={}", userId);
        return toMap(saved);
    }

    // ── Avatar upload ─────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> uploadAvatar(Long userId, byte[] imageBytes,
                                             String contentType, String ext) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found."));

        // Upload to S3 under avatars/ prefix
        String s3Key    = s3Service.uploadAvatar(imageBytes, userId, contentType, ext);
        String proxyUrl = "/api/images/" + s3Key;   // served by ImageController

        user.setAvatarUrl(proxyUrl);
        User saved = userRepo.save(user);
        log.info("Avatar uploaded for userId={}: key={}", userId, s3Key);
        return toMap(saved);
    }

    // ── Safe map (no password hash) ───────────────────────────────────────────

    public Map<String, Object> toMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",            user.getId());
        map.put("name",          user.getName());
        map.put("email",         user.getEmail());
        map.put("role",          user.getRole());
        map.put("phone",         user.getPhone());
        map.put("avatar_url",    user.getAvatarUrl());
        map.put("date_of_birth", user.getDateOfBirth());
        map.put("created_at",    user.getCreatedAt());
        map.put("updated_at",    user.getUpdatedAt());
        return map;
    }
}
