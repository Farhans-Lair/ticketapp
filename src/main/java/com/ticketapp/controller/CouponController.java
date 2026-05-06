package com.ticketapp.controller;

import com.ticketapp.entity.Coupon;
import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * CouponController — Feature 7: Coupons & offers.
 *
 * --- User-facing ---
 * POST /coupons/validate   body: { "code": "SUMMER20", "orderAmount": 1200.0 }
 *   → returns { valid, discountAmount, finalAmount } — NO DB write (safe to call on keystroke)
 *
 * --- Admin ---
 * POST   /coupons           body: Coupon JSON   → create coupon
 * GET    /coupons                               → list all coupons
 * PATCH  /coupons/{id}/status  body: { "status": "inactive" }
 */
@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
@Slf4j
public class CouponController {

    private final CouponService couponService;

    // ── Validate (user-facing — no auth needed to preview discount) ──────────

    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal AuthenticatedUser user) {

        String code        = (String) body.get("code");
        double orderAmount = ((Number) body.getOrDefault("orderAmount", 0)).doubleValue();

        if (code == null || code.isBlank())
            return ResponseEntity.badRequest().body(Map.of("valid", false, "reason", "Code is required."));

        // userId drives per_user_limit check; if not authenticated, use -1
        Long userId = user != null ? user.getId() : -1L;
        Map<String, Object> result = couponService.validate(code, userId, orderAmount);
        return ResponseEntity.ok(result);
    }

    // ── Admin: create coupon ──────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Coupon coupon) {
        try {
            return ResponseEntity.ok(couponService.create(coupon));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Admin: list all coupons ───────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<Coupon>> getAll() {
        return ResponseEntity.ok(couponService.getAll());
    }

    // ── Admin: toggle status ──────────────────────────────────────────────────

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> setStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String status = body.get("status");
        if (status == null || status.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Status is required."));

        try {
            return ResponseEntity.ok(couponService.setStatus(id, status));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
