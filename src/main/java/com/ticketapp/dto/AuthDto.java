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
    @Data
    public static class LoginResponse {
        private String role;
        private Long   userId;
        private String token;

        public LoginResponse(String role, Long userId, String token) {
            this.role   = role;
            this.userId = userId;
            this.token  = token;
        }
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
