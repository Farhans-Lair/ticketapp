package com.ticketapp.repository;

import com.ticketapp.entity.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByEventIdOrderBySeatNumberAsc(Long eventId);

    long countByEventId(Long eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s " +
           "WHERE s.eventId = :eventId AND s.seatNumber IN :seatNumbers AND s.status = :status")
    List<Seat> findByEventIdAndSeatNumberInAndStatus(
            @Param("eventId") Long eventId,
            @Param("seatNumbers") List<String> seatNumbers,
            @Param("status") String status);

    /* Conditionally marks seats as booked — only rows still 'available' are updated. */
    @Modifying
    @Query("UPDATE Seat s SET s.status = 'booked' " +
           "WHERE s.eventId = :eventId AND s.seatNumber IN :seatNumbers AND s.status = 'available'")
    int markSeatsBooked(@Param("eventId") Long eventId, @Param("seatNumbers") List<String> seatNumbers);

    @Modifying
    @Query("UPDATE Seat s SET s.status = 'available' " +
           "WHERE s.eventId = :eventId AND s.seatNumber IN :seatNumbers")
    int markSeatsAvailable(@Param("eventId") Long eventId, @Param("seatNumbers") List<String> seatNumbers);

    /* Transitions seats from 'available' → 'held' for a specific user. */
    @Modifying
    @Query("""
        UPDATE Seat s
        SET s.status = 'held',
            s.heldUntil = :heldUntil,
            s.heldByUserId = :userId
        WHERE s.eventId = :eventId
          AND s.seatNumber IN :seatNumbers
          AND s.status = 'available'
    """)
    int holdSeats(
            @Param("eventId")   Long eventId,
            @Param("seatNumbers") List<String> seatNumbers,
            @Param("userId")    Long userId,
            @Param("heldUntil") java.time.LocalDateTime heldUntil);

    /* Sweeps expired holds — called by @Scheduled every minute. */
    @Modifying
    @Query("""
        UPDATE Seat s
        SET s.status = 'available',
            s.heldUntil = null,
            s.heldByUserId = null
        WHERE s.status = 'held'
          AND s.heldUntil < :now
    """)
    int releaseExpiredHolds(@Param("now") java.time.LocalDateTime now);

    /* Transitions already-held seats to booked for the same user. */
    @Modifying
    @Query("""
        UPDATE Seat s
        SET s.status = 'booked',
            s.heldUntil = null,
            s.heldByUserId = null
        WHERE s.eventId = :eventId
          AND s.seatNumber IN :seatNumbers
          AND s.heldByUserId = :userId
    """)
    int confirmHeldSeats(
            @Param("eventId")     Long eventId,
            @Param("seatNumbers") List<String> seatNumbers,
            @Param("userId")      Long userId);

    /* Find seats held by a specific user for an event (used in hold validation). */
    List<Seat> findByEventIdAndHeldByUserIdAndStatus(
            Long eventId, Long userId, String status);

    /* Releases all seats currently held by a specific user for a specific event. */
    @Modifying
    @Query("""
        UPDATE Seat s
        SET s.status = 'available',
            s.heldUntil = null,
            s.heldByUserId = null
        WHERE s.eventId = :eventId
          AND s.heldByUserId = :userId
          AND s.status = 'held'
    """)
    int releaseUserHolds(
            @Param("eventId") Long eventId,
            @Param("userId")  Long userId);

    List<Seat> findByEventIdAndCategoryOrderBySeatNumberAsc(Long eventId, String category);

    /* Plain (no lock) lookup of specific seats by number. */
    @Query("SELECT s FROM Seat s WHERE s.eventId = :eventId AND s.seatNumber IN :seatNumbers")
    List<Seat> findByEventIdAndSeatNumberIn(
            @Param("eventId")     Long eventId,
            @Param("seatNumbers") List<String> seatNumbers);

    // Seat reconfiguration (organizer configure tiers)
    /* Deletes ALL seats for an event — called before regenerating tiered seats. */
    @org.springframework.transaction.annotation.Transactional
    void deleteByEventId(Long eventId);
}
