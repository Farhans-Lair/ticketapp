package com.ticketapp.service;

import com.ticketapp.entity.Booking;
import com.ticketapp.entity.User;
import com.ticketapp.repository.BookingRepository;
import com.ticketapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository    userRepo;
    private final BookingRepository bookingRepo;
    private final S3Service         s3Service;

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(12);

    // ── Profile read ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getProfileMap(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found."));

        // Booking summary counts — mirrors TBA2 getProfile()
        long total     = bookingRepo.countByUserId(userId);
        long active    = bookingRepo.countByUserIdAndCancellationStatusAndPaymentStatus(
                             userId, "active", "paid");
        long cancelled = bookingRepo.countByUserIdAndCancellationStatus(userId, "cancelled");

        Map<String, Object> map = toMap(user);
        map.put("booking_summary", Map.of(
                "total",     total,
                "active",    active,
                "cancelled", cancelled));
        return map;
    }

    // ── Profile update ────────────────────────────────────────────────────────

    /**
     * Updates name, phone, date_of_birth, bio, and bank_details.
     * Mirrors TBA2's updateProfile() which accepts all profile fields.
     */
    @Transactional
    public Map<String, Object> updateProfile(Long userId, String name, String phone,
                                              String dateOfBirthStr, String bio,
                                              String bankDetails) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (name != null && !name.isBlank())  user.setName(name.trim());
        if (phone != null)                    user.setPhone(phone.isBlank() ? null : phone.trim());
        if (bio   != null)                    user.setBio(bio.isBlank()   ? null : bio.trim());
        if (bankDetails != null)              user.setBankDetails(bankDetails.isBlank() ? null : bankDetails.trim());
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

    // ── Change password ───────────────────────────────────────────────────────

    /**
     * Validates current_password, then hashes and saves new_password.
     * Mirrors TBA2's changePassword() controller logic exactly:
     *  - Both fields required
     *  - New password must be >= 8 characters
     *  - current_password verified with bcrypt before update
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        if (currentPassword == null || currentPassword.isBlank()
                || newPassword == null || newPassword.isBlank()) {
            throw new RuntimeException("current_password and new_password are required.");
        }
        if (newPassword.length() < 8) {
            throw new RuntimeException("New password must be at least 8 characters.");
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (!BCRYPT.matches(currentPassword, user.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect.");
        }

        user.setPasswordHash(BCRYPT.encode(newPassword));
        userRepo.save(user);
        log.info("Password changed: userId={}", userId);
    }

    // ── Avatar upload ─────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> uploadAvatar(Long userId, byte[] imageBytes,
                                             String contentType, String ext) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found."));

        String s3Key    = s3Service.uploadAvatar(imageBytes, userId, contentType, ext);
        String proxyUrl = "/api/images/" + s3Key;

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
        map.put("bio",           user.getBio());
        map.put("bank_details",  user.getBankDetails());
        map.put("date_of_birth", user.getDateOfBirth());
        map.put("created_at",    user.getCreatedAt());
        map.put("updated_at",    user.getUpdatedAt());
        return map;
    }
}
