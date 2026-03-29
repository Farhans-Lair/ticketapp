package com.ticketapp.repository;

import com.ticketapp.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByEventIdOrderBySeatNumberAsc(Long eventId);
    long countByEventId(Long eventId);
    List<Seat> findByEventIdAndSeatNumberInAndStatus(Long eventId, List<String> seatNumbers, String status);

    @Modifying
    @Query("UPDATE Seat s SET s.status = 'booked' WHERE s.eventId = :eventId AND s.seatNumber IN :seatNumbers")
    int markSeatsBooked(@Param("eventId") Long eventId, @Param("seatNumbers") List<String> seatNumbers);
}
