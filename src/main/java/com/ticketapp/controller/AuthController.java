package com.ticketapp.controller;

import com.ticketapp.dto.AuthDto;
import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.security.JwtUtil;
import com.ticketapp.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final JwtUtil     jwtUtil;

    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    // ── POST /auth/signup-request ─────────────────────────────────────────────
    @PostMapping("/signup-request")
    public ResponseEntity<?> signupRequest(@Valid @RequestBody AuthDto.SignupRequest body) {
        log.info("Signup OTP requested for {}", body.getEmail());
        authService.initiateSignup(body.getName(), body.getEmail(), body.getPassword());
        return ResponseEntity.ok(Map.of("message",
            "Verification code sent to your email. Please enter it to complete registration."));
    }

    // ── POST /auth/signup-verify ──────────────────────────────────────────────
    @PostMapping("/signup-verify")
    public ResponseEntity<?> signupVerify(@Valid @RequestBody AuthDto.OtpVerifyRequest body) {
        log.info("Signup OTP verify for {}", body.getEmail());
        authService.completeSignup(body.getEmail(), body.getOtp());
        return ResponseEntity.status(201).body(Map.of("message",
            "Registration successful. You can now log in."));
    }

    // ── POST /auth/login-request ──────────────────────────────────────────────
    @PostMapping("/login-request")
    public ResponseEntity<?> loginRequest(@Valid @RequestBody AuthDto.LoginRequest body) {
        log.info("Login OTP requested for {}", body.getEmail());
        authService.initiateLogin(body.getEmail(), body.getPassword());
        return ResponseEntity.ok(Map.of("message",
            "Verification code sent to your email. Please enter it to complete login."));
    }

    // ── POST /auth/login-verify ───────────────────────────────────────────────
    @PostMapping("/login-verify")
    public ResponseEntity<?> loginVerify(@Valid @RequestBody AuthDto.OtpVerifyRequest body,
                                          HttpServletResponse response) {
        log.info("Login OTP verify for {}", body.getEmail());
        Map<String, Object> payload = authService.completeLogin(body.getEmail(), body.getOtp());

        Long   userId = ((Number) payload.get("userId")).longValue();
        String role   = (String) payload.get("role");
        String token  = jwtUtil.generateToken(userId, role);

        // Set HttpOnly cookie (same as Express res.cookie)
        Cookie cookie = new Cookie("token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge(3600);
        response.addCookie(cookie);

        log.info("User logged in: userId={} role={}", userId, role);
        return ResponseEntity.ok(new AuthDto.LoginResponse(role, userId, token));
    }

    // ── POST /auth/logout ─────────────────────────────────────────────────────
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response,
                                     @AuthenticationPrincipal AuthenticatedUser user) {
        // Always clear the cookie regardless of auth state.
        // user can be null if the token is missing or expired — still clear the cookie.
        Cookie cookie = new Cookie("token", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        log.info("User logged out: userId={}", user != null ? user.getId() : "anonymous");
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    // ── GET /auth/me ──────────────────────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal AuthenticatedUser user) {
        // user is null when no valid JWT is present (no token or expired).
        // Return 401 so the frontend redirects to login rather than crashing.
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated."));
        }
        return ResponseEntity.ok(Map.of("userId", user.getId(), "role", user.getRole()));
    }

    // ── POST /auth/organizer-signup-request ───────────────────────────────────
    @PostMapping("/organizer-signup-request")
    public ResponseEntity<?> organizerSignupRequest(
            @Valid @RequestBody AuthDto.OrganizerSignupRequest body) {
        log.info("Organizer signup OTP requested for {}", body.getEmail());
        authService.initiateOrganizerSignup(
            body.getName(), body.getEmail(), body.getPassword(),
            body.getBusiness_name(), body.getContact_phone(),
            body.getGst_number(), body.getAddress());
        return ResponseEntity.ok(Map.of("message",
            "Verification code sent to your email. Please enter it to complete organizer registration."));
    }

    // ── POST /auth/organizer-signup-verify ────────────────────────────────────
    @PostMapping("/organizer-signup-verify")
    public ResponseEntity<?> organizerSignupVerify(
            @Valid @RequestBody AuthDto.OtpVerifyRequest body) {
        log.info("Organizer signup OTP verify for {}", body.getEmail());
        authService.completeOrganizerSignup(body.getEmail(), body.getOtp());
        return ResponseEntity.status(201).body(Map.of(
            "message", "Registration successful. Your organizer account is pending admin approval. " +
                       "You will receive an email once reviewed.",
            "status",  "pending"));
    }
}
