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

    @Query("SELECT b FROM Booking b JOIN FETCH b.event WHERE b.id = :id AND b.userId = :userId")
    Optional<Booking> findByIdAndUserIdWithEvent(@Param("id") Long id, @Param("userId") Long userId);

    List<Booking> findByEventIdAndPaymentStatus(Long eventId, String paymentStatus);

    @Query("SELECT b FROM Booking b JOIN FETCH b.event e WHERE e.organizerId = :organizerId AND b.paymentStatus = 'paid'")
    List<Booking> findPaidBookingsByOrganizerId(@Param("organizerId") Long organizerId);
}
