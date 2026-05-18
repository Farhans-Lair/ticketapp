package com.ticketapp.repository;

import com.ticketapp.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("SELECT b FROM Booking b JOIN FETCH b.event WHERE b.userId = :userId ORDER BY b.bookingDate DESC")
    List<Booking> findByUserIdWithEvent(@Param("userId") Long userId);

    @Query("SELECT b FROM Booking b WHERE b.id = :id AND b.userId = :userId")
    Optional<Booking> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT b FROM Booking b WHERE b.eventId = :eventId AND b.paymentStatus = :status")
    List<Booking> findByEventIdAndPaymentStatus(@Param("eventId") Long eventId,
                                                @Param("status") String status);

    Optional<Booking> findByRazorpayRefundId(String razorpayRefundId);

    // ── User profile booking summary counts (added for UserService.getProfileMap) ──

    long countByUserId(Long userId);

    long countByUserIdAndCancellationStatusAndPaymentStatus(
            Long userId, String cancellationStatus, String paymentStatus);

    long countByUserIdAndCancellationStatus(Long userId, String cancellationStatus);

    // ── Review eligibility check ───────────────────────────────────────────────

    @Query("""
        SELECT COUNT(b) > 0 FROM Booking b
        WHERE b.userId             = :userId
          AND b.eventId            = :eventId
          AND b.paymentStatus      = 'paid'
          AND b.cancellationStatus = 'active'
    """)
    boolean hasActivePaidBooking(
            @Param("userId")  Long userId,
            @Param("eventId") Long eventId);

    // ── Feature 12: Event reminder emails ─────────────────────────────────────

    @Query("""
        SELECT b FROM Booking b JOIN FETCH b.event e
        WHERE e.eventDate >= :from
          AND e.eventDate <= :to
          AND b.paymentStatus      = 'paid'
          AND b.cancellationStatus = 'active'
          AND b.reminderSentAt    IS NULL
    """)
    List<Booking> findBookingsForReminder(
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to);

    // ── Feature 14: Organizer payout ─────────────────────────────────────────

    @Query("""
        SELECT b FROM Booking b
        WHERE b.eventId IN :eventIds
          AND b.bookingDate        >= :from
          AND b.bookingDate        <  :to
          AND b.paymentStatus      = 'paid'
          AND b.cancellationStatus = 'active'
    """)
    List<Booking> findBookingsForPayout(
            @Param("eventIds") List<Long> eventIds,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to);

    // ── Payout settlement calculation (added — mirrors TBA2 calculateSettlement) ─

    /**
     * Returns paid bookings for a set of events where cancellation_status is
     * in the provided list ('active' or 'refund_pending').
     * Used by PayoutService.calculateSettlement() to compute outstanding gross revenue.
     */
    @Query("""
        SELECT b FROM Booking b
        WHERE b.eventId             IN :eventIds
          AND b.paymentStatus        = 'paid'
          AND b.cancellationStatus   IN :cancellationStatuses
    """)
    List<Booking> findSettlementBookings(
            @Param("eventIds")            List<Long>   eventIds,
            @Param("cancellationStatuses") List<String> cancellationStatuses);
}
