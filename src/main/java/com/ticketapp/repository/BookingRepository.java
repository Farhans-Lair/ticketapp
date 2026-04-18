package com.ticketapp.repository;

import com.ticketapp.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
