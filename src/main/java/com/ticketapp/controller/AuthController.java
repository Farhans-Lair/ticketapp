package com.ticketapp.controller;

import com.ticketapp.dto.AuthDto;
import com.ticketapp.entity.User;
import com.ticketapp.repository.UserRepository;
import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.security.JwtUtil;
import com.ticketapp.service.AuthService;
import com.ticketapp.service.RefreshTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService          authService;
    private final JwtUtil              jwtUtil;
    private final UserRepository       userRepo;
    private final RefreshTokenService  refreshTokenService;

    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    private static final int ACCESS_COOKIE_MAX_AGE  = 15 * 60;               // 15 minutes
    private static final int REFRESH_COOKIE_MAX_AGE = 7 * 24 * 60 * 60;      // 7 days
    private static final int SESSION_COOKIE_MAX_AGE = 30 * 24 * 60 * 60;     // 30 days

    @PostMapping("/signup-request")
    public ResponseEntity<?> signupRequest(@Valid @RequestBody AuthDto.SignupRequest body) {
        log.info("Signup OTP requested for {}", body.getEmail());
        authService.initiateSignup(body.getName(), body.getEmail(), body.getPassword());
        return ResponseEntity.ok(Map.of("message",
            "Verification code sent to your email. Please enter it to complete registration."));
    }

    @PostMapping("/signup-verify")
    public ResponseEntity<?> signupVerify(@Valid @RequestBody AuthDto.OtpVerifyRequest body) {
        log.info("Signup OTP verify for {}", body.getEmail());
        authService.completeSignup(body.getEmail(), body.getOtp());
        return ResponseEntity.status(201).body(Map.of("message",
            "Registration successful. You can now log in."));
    }

    @PostMapping("/login-request")
    public ResponseEntity<?> loginRequest(@Valid @RequestBody AuthDto.LoginRequest body) {
        log.info("Login OTP requested for {}", body.getEmail());
        authService.initiateLogin(body.getEmail(), body.getPassword());
        return ResponseEntity.ok(Map.of("message",
            "Verification code sent to your email. Please enter it to complete login."));
    }

    @PostMapping("/login-verify")
    public ResponseEntity<?> loginVerify(@Valid @RequestBody AuthDto.OtpVerifyRequest body,
                                          HttpServletResponse response) {
        log.info("Login OTP verify for {}", body.getEmail());
        Map<String, Object> payload = authService.completeLogin(body.getEmail(), body.getOtp());

        Long   userId = ((Number) payload.get("userId")).longValue();
        String role   = (String) payload.get("role");

        RefreshTokenService.IssuedTokens tokens = refreshTokenService.issueNewSession(userId, role);

        setAuthCookies(response, tokens.accessToken(), tokens.refreshToken(), tokens.sessionToken());

        log.info("User logged in: userId={} role={} sessionId={}", userId, role, tokens.sessionId());
        // Cookies are set above as a fallback/default, but the PRIMARY path for a multi-tab-different-users browser is
        return ResponseEntity.ok(new AuthDto.LoginResponse(
                role, userId, tokens.accessToken(),
                tokens.refreshToken(), tokens.sessionToken(), tokens.sessionId()));
    }

    /* POST /auth/refresh — exchanges a valid, not-yet-used refresh token for a brand new access+refresh pair. */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody(required = false) AuthDto.RefreshRequest body,
                                      HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = (body != null && body.getRefreshToken() != null && !body.getRefreshToken().isBlank())
                ? body.getRefreshToken()
                : readCookie(request, "refresh_token");

        if (refreshToken == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No refresh token present. Please log in."));
        }

        Claims claims;
        try {
            claims = jwtUtil.parseRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired refresh token. Please log in again."));
        }

        Long userId = claims.get("id", Long.class);
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Account no longer exists."));
        }

        try {
            // Fetch the CURRENT role from the DB rather than trusting a claim from a token that
            RefreshTokenService.IssuedTokens tokens = refreshTokenService.rotate(refreshToken, user.getRole());
            setAuthCookies(response, tokens.accessToken(), tokens.refreshToken(), null);

            log.info("Access token refreshed for userId={} sessionId={}", userId, tokens.sessionId());
            // Both tokens are returned in the body, not just set as cookies.
            return ResponseEntity.ok(Map.of(
                    "message", "Token refreshed.",
                    "token", tokens.accessToken(),
                    "refreshToken", tokens.refreshToken()
            ));
        } catch (RefreshTokenService.RefreshTokenException e) {
            // Reuse-detected or expired/unknown token — clear cookies so the client doesn't keep retrying with a dead
            clearAuthCookies(response);
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) AuthDto.LogoutRequest body,
                                     HttpServletRequest request, HttpServletResponse response,
                                     @AuthenticationPrincipal AuthenticatedUser user) {
        // JwtAuthFilter intentionally does NOT authenticate this path (it's in the public skip-list, so /auth/logout still works
        String sessionId = (body != null && body.getSessionId() != null && !body.getSessionId().isBlank())
                ? body.getSessionId()
                : extractSessionId(request);

        if (sessionId != null) {
            refreshTokenService.revokeSession(sessionId);
        }

        clearAuthCookies(response);
        log.info("User logged out: userId={} sessionId={}",
                user != null ? user.getId() : "anonymous", sessionId);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /* POST /auth/logout-all — revokes every refresh token for this user across every device/session, not just the */
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(HttpServletResponse response,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated."));
        }
        refreshTokenService.revokeAllSessionsForUser(user.getId());
        clearAuthCookies(response);
        log.info("User logged out of all sessions: userId={}", user.getId());
        return ResponseEntity.ok(Map.of("message", "Logged out of all devices."));
    }

    /* GET /auth/me — extended to return name and avatar_url so every page can render the user's */
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated."));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("userId", user.getId());
        resp.put("role",   user.getRole());

        // Enrich with display name and avatar from the users table
        userRepo.findById(user.getId()).ifPresent(u -> {
            resp.put("name",       u.getName());
            resp.put("avatar_url", u.getAvatarUrl());
        });

        return ResponseEntity.ok(resp);
    }

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

    // Cookie helpers

    /* Sets the three auth cookies. */
    private void setAuthCookies(HttpServletResponse response, String accessToken,
                                 String refreshToken, String sessionToken) {
        addCookie(response, "access_token", accessToken, ACCESS_COOKIE_MAX_AGE);
        addCookie(response, "refresh_token", refreshToken, REFRESH_COOKIE_MAX_AGE);
        if (sessionToken != null) {
            addCookie(response, "session_token", sessionToken, SESSION_COOKIE_MAX_AGE);
        }
    }

    private void clearAuthCookies(HttpServletResponse response) {
        addCookie(response, "access_token", "", 0);
        addCookie(response, "refresh_token", "", 0);
        addCookie(response, "session_token", "", 0);
    }

    private void addCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        response.addCookie(cookie);
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    /* Tries the session token first (longest-lived, most likely still valid at logout time), then falls back */
    private String extractSessionId(HttpServletRequest request) {
        String sessionToken = readCookie(request, "session_token");
        if (sessionToken != null) {
            try {
                return jwtUtil.parseSessionToken(sessionToken).get("sid", String.class);
            } catch (JwtException | IllegalArgumentException ignored) {
                // fall through to access token
            }
        }
        String accessToken = readCookie(request, "access_token");
        if (accessToken != null) {
            try {
                return jwtUtil.parseAccessToken(accessToken).get("sid", String.class);
            } catch (JwtException | IllegalArgumentException ignored) {
                // no valid cookie carried a session id — nothing to revoke server-side
            }
        }
        return null;
    }
}
