package com.ticketapp.repository;

import com.ticketapp.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCodeIgnoreCase(String code);

    /**
     * Atomically increments usage_count for a coupon.
     * Called inside a transaction in CouponService.applyCoupon.
     * Returns the number of rows updated (1 = success, 0 = limit reached by another thread).
     *
     * The AND c.usage_count < c.usage_limit guard prevents over-redeeming
     * under concurrent load. The calling service checks the return value.
     * usageLimit = 0 is treated as unlimited (handled in service layer).
     */
    @Modifying
    @Query("""
        UPDATE Coupon c
        SET c.usageCount = c.usageCount + 1
        WHERE c.id = :id
          AND (c.usageLimit = 0 OR c.usageCount < c.usageLimit)
    """)
    int incrementUsageCount(@Param("id") Long id);

    /**
     * Per-user redemption count for a specific coupon.
     * Joined through Booking.coupon_code. Used to enforce per_user_limit.
     */
    @Query("""
        SELECT COUNT(b) FROM Booking b
        WHERE b.couponCode = :code AND b.userId = :userId
          AND b.paymentStatus = 'paid'
          AND b.cancellationStatus = 'active'
    """)
    Long countUserRedemptions(@Param("code") String code, @Param("userId") Long userId);
}
