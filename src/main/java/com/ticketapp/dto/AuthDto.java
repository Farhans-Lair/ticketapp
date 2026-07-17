package com.ticketapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AuthDto {

    // ── Signup Step 1 ──────────────────────────────────────────
    @Data
    public static class SignupRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @NotBlank(message = "Email is required")
        @Email(message = "Valid email required")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String password;
    }

    // ── Signup / Login OTP Verify ──────────────────────────────
    @Data
    public static class OtpVerifyRequest {
        @NotBlank(message = "Email is required")
        @Email
        private String email;

        @NotBlank(message = "OTP is required")
        @Size(min = 6, max = 6, message = "OTP must be 6 digits")
        private String otp;
    }

    // ── Login Step 1 ───────────────────────────────────────────
    @Data
    public static class LoginRequest {
        @NotBlank(message = "Email is required")
        @Email
        private String email;

        @NotBlank(message = "Password is required")
        private String password;
    }

    // ── Login Step 2 response ──────────────────────────────────
    // refreshToken/sessionToken/sessionId are included here (not just set as
    // httpOnly cookies) because the frontend keeps a PER-TAB copy of each in
    // sessionStorage — see auth.js/api.js. Cookies alone can't support two
    // different users logged in on two different tabs of the same browser,
    // since a cookie is shared per origin, not scoped per tab.
    @Data
    public static class LoginResponse {
        private String role;
        private Long   userId;
        private String token;          // access token
        private String refreshToken;
        private String sessionToken;
        private String sessionId;

        public LoginResponse(String role, Long userId, String token,
                              String refreshToken, String sessionToken, String sessionId) {
            this.role         = role;
            this.userId       = userId;
            this.token        = token;
            this.refreshToken = refreshToken;
            this.sessionToken = sessionToken;
            this.sessionId    = sessionId;
        }
    }

    // ── POST /auth/refresh request body ─────────────────────────
    // Optional: if present, this tab's own sessionStorage refresh token is
    // used instead of the (possibly-belongs-to-a-different-tab) cookie.
    // No validation annotations — the whole body is optional; AuthController
    // falls back to the cookie when it's absent, for non-JS callers.
    @Data
    public static class RefreshRequest {
        private String refreshToken;
    }

    // ── POST /auth/logout request body ──────────────────────────
    // Optional, same reasoning as RefreshRequest — identifies exactly which
    // tab's session to revoke instead of guessing from a shared cookie.
    @Data
    public static class LogoutRequest {
        private String sessionId;
    }

    // ── Organizer Signup Step 1 ────────────────────────────────
    @Data
    public static class OrganizerSignupRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @NotBlank(message = "Email is required")
        @Email
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 6)
        private String password;

        @NotBlank(message = "Business name is required")
        private String business_name;

        private String contact_phone;
        private String gst_number;
        private String address;
    }
}
