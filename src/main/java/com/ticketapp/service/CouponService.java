package com.ticketapp.service;

import com.ticketapp.entity.Coupon;
import com.ticketapp.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {

    private final CouponRepository couponRepo;

    // ── Admin CRUD ────────────────────────────────────────────────────────────

    @Transactional
    public Coupon create(Coupon coupon) {
        if (couponRepo.findByCodeIgnoreCase(coupon.getCode()).isPresent())
            throw new RuntimeException("Coupon code already exists: " + coupon.getCode());
        Coupon saved = couponRepo.save(coupon);
        log.info("Coupon created: code={} type={} value={}", saved.getCode(), saved.getDiscountType(), saved.getDiscountValue());
        return saved;
    }

    public List<Coupon> getAll() {
        return couponRepo.findAll();
    }

    @Transactional
    public Coupon setStatus(Long id, String status) {
        Coupon c = couponRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Coupon not found"));
        c.setStatus(status);
        return couponRepo.save(c);
    }

    // ── Validate + calculate discount (no DB write yet) ───────────────────────

    /**
     * Returns a breakdown map with keys: valid, discountAmount, finalAmount, reason.
     * Called from the checkout page before creating a Razorpay order.
     *
     * @param code        coupon code from user input
     * @param userId      authenticated user (for per_user_limit check)
     * @param orderAmount total order amount BEFORE the coupon (rupees)
     */
    public Map<String, Object> validate(String code, Long userId, double orderAmount) {
        Coupon coupon = couponRepo.findByCodeIgnoreCase(code).orElse(null);

        if (coupon == null)
            return invalid("Coupon code not found.");

        if (!"active".equals(coupon.getStatus()))
            return invalid("This coupon is " + coupon.getStatus() + ".");

        LocalDateTime now = LocalDateTime.now();
        if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom()))
            return invalid("This coupon is not active yet.");
        if (coupon.getValidTo() != null && now.isAfter(coupon.getValidTo()))
            return invalid("This coupon has expired.");

        if (coupon.getMinAmount() != null && orderAmount < coupon.getMinAmount())
            return invalid(String.format(
                "Minimum order amount for this coupon is ₹%.0f.", coupon.getMinAmount()));

        if (coupon.getUsageLimit() != null && coupon.getUsageLimit() > 0
                && coupon.getUsageCount() >= coupon.getUsageLimit())
            return invalid("This coupon has reached its usage limit.");

        if (coupon.getPerUserLimit() != null && coupon.getPerUserLimit() > 0) {
            long userUses = couponRepo.countUserRedemptions(coupon.getCode(), userId);
            if (userUses >= coupon.getPerUserLimit())
                return invalid("You have already used this coupon the maximum number of times.");
        }

        double discount = calculateDiscount(coupon, orderAmount);
        double finalAmount = Math.max(0, orderAmount - discount);

        log.info("Coupon validated: code={} userId={} discount={} finalAmount={}",
                code, userId, discount, finalAmount);

        return Map.of(
            "valid",          true,
            "code",           coupon.getCode(),
            "discountAmount", Math.round(discount * 100.0) / 100.0,
            "finalAmount",    Math.round(finalAmount * 100.0) / 100.0,
            "discountType",   coupon.getDiscountType(),
            "discountValue",  coupon.getDiscountValue()
        );
    }

    /**
     * Atomically redeems the coupon — increments usage_count inside a transaction.
     * Must be called inside BookingService.confirmBooking (same transaction).
     * Returns the rupee discount applied.
     *
     * @throws RuntimeException if the coupon is no longer redeemable (race condition).
     */
    @Transactional
    public double redeem(String code, Long userId, double orderAmount) {
        Coupon coupon = couponRepo.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new RuntimeException("Coupon not found."));

        // Re-validate inside the transaction (another thread may have exhausted it)
        Map<String, Object> check = validate(code, userId, orderAmount);
        if (!(boolean) check.get("valid"))
            throw new RuntimeException((String) check.get("reason"));

        // Atomic increment — the conditional UPDATE returns 0 if the limit was just hit
        int updated = couponRepo.incrementUsageCount(coupon.getId());
        if (updated == 0)
            throw new RuntimeException("Coupon just ran out. Please try another code.");

        double discount = calculateDiscount(coupon, orderAmount);
        log.info("Coupon redeemed: code={} userId={} discount={}", code, userId, discount);
        return Math.round(discount * 100.0) / 100.0;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private double calculateDiscount(Coupon coupon, double orderAmount) {
        double discount;
        if ("percent".equalsIgnoreCase(coupon.getDiscountType())) {
            discount = orderAmount * (coupon.getDiscountValue() / 100.0);
            if (coupon.getMaxDiscount() != null && discount > coupon.getMaxDiscount())
                discount = coupon.getMaxDiscount();
        } else {
            discount = coupon.getDiscountValue();
        }
        return Math.min(discount, orderAmount); // can't discount more than the order
    }

    private Map<String, Object> invalid(String reason) {
        return Map.of("valid", false, "reason", reason, "discountAmount", 0.0);
    }
}
