package com.ticketapp.service;

import com.ticketapp.entity.OrganizerProfile;
import com.ticketapp.entity.User;
import com.ticketapp.repository.OrganizerProfileRepository;
import com.ticketapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository             userRepo;
    private final OrganizerProfileRepository profileRepo;
    private final PasswordEncoder            passwordEncoder;
    private final OtpStore                   otpStore;
    private final EmailService               emailService;

    // ── User Signup ───────────────────────────────────────────────────────────

    public void initiateSignup(String name, String email, String password) {
        if (userRepo.existsByEmail(email))
            throw new RuntimeException("An account with this email already exists.");

        String hash = passwordEncoder.encode(password);
        Map<String, Object> payload = Map.of("name", name, "passwordHash", hash);
        String otp = otpStore.generate(email, "signup", payload);
        emailService.sendOtpEmail(email, otp, "signup");
    }

    @Transactional
    public void completeSignup(String email, String otp) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) otpStore.verify(email, otp, "signup");

        if (userRepo.existsByEmail(email))
            throw new RuntimeException("An account with this email already exists.");

        User user = new User();
        user.setName((String) payload.get("name"));
        user.setEmail(email);
        user.setPasswordHash((String) payload.get("passwordHash"));
        user.setRole("user");
        userRepo.save(user);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    public void initiateLogin(String email, String password) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash()))
            throw new RuntimeException("Invalid credentials");

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getId());
        payload.put("role",   user.getRole());
        String otp = otpStore.generate(email, "login", payload);
        emailService.sendOtpEmail(email, otp, "login");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> completeLogin(String email, String otp) {
        return (Map<String, Object>) otpStore.verify(email, otp, "login");
        // returns { userId: Long, role: String }
    }

    // ── Organizer Signup ──────────────────────────────────────────────────────

    public void initiateOrganizerSignup(String name, String email, String password,
                                        String businessName, String contactPhone,
                                        String gstNumber, String address) {
        if (userRepo.existsByEmail(email))
            throw new RuntimeException("An account with this email already exists.");

        String hash = passwordEncoder.encode(password);
        Map<String, Object> payload = new HashMap<>();
        payload.put("name",          name);
        payload.put("passwordHash",  hash);
        payload.put("business_name", businessName);
        payload.put("contact_phone", contactPhone);
        payload.put("gst_number",    gstNumber);
        payload.put("address",       address);

        String otp = otpStore.generate(email, "organizer-signup", payload);
        emailService.sendOtpEmail(email, otp, "signup");
    }

    @Transactional
    public Map<String, Object> completeOrganizerSignup(String email, String otp) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) otpStore.verify(email, otp, "organizer-signup");

        if (userRepo.existsByEmail(email))
            throw new RuntimeException("An account with this email already exists.");

        User user = new User();
        user.setName((String) payload.get("name"));
        user.setEmail(email);
        user.setPasswordHash((String) payload.get("passwordHash"));
        user.setRole("organizer");
        user = userRepo.save(user);

        OrganizerProfile profile = new OrganizerProfile();
        profile.setUserId(user.getId());
        profile.setBusinessName((String) payload.get("business_name"));
        profile.setContactPhone((String) payload.get("contact_phone"));
        profile.setGstNumber((String) payload.get("gst_number"));
        profile.setAddress((String) payload.get("address"));
        profile.setStatus("pending");
        profile = profileRepo.save(profile);

        return Map.of("user", user, "profile", profile);
    }
}
