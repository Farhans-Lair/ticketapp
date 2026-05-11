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

    // ── Review eligibility check ───────────────────────────────────────────────
    /**
     * Returns true when the user has at least one paid, non-cancelled booking
     * for the given event. Used by ReviewService to gate review submission.
     * A single COUNT query is more efficient than loading all event bookings.
     */
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
    /**
     * Finds paid, active bookings whose event is 23–25 hours away and
     * where the reminder email has not yet been sent.
     * Scheduler runs daily at 9 AM; the 2-hour window prevents duplicates
     * if the job restarts within the same day.
     */
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
    /**
     * Returns all paid, active bookings in the given event-ID set and date range.
     * Used by PayoutService to compute the organizer's net earnings.
     */
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
}
