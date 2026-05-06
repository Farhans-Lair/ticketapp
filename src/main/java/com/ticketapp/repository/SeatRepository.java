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

    /**
     * SELECT ... FOR UPDATE — acquires exclusive row-level locks on all matching seats.
     *
     * WHY: Without a lock, two concurrent booking requests can both execute:
     *   1. Thread A: SELECT → sees A1, A2 available
     *   2. Thread B: SELECT → sees A1, A2 available  ← race window opens here
     *   3. Thread A: UPDATE seats → marks A1, A2 as booked
     *   4. Thread B: UPDATE seats → marks A1, A2 as booked again (double booking!)
     *
     * With PESSIMISTIC_WRITE, Thread B's SELECT blocks until Thread A's transaction
     * commits. After A commits (seats are now 'booked'), B re-reads 0 available rows
     * and SeatService throws the "seats no longer available" error correctly.
     *
     * Must be called inside an active transaction (SeatService.bookSeats is @Transactional).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s " +
           "WHERE s.eventId = :eventId AND s.seatNumber IN :seatNumbers AND s.status = :status")
    List<Seat> findByEventIdAndSeatNumberInAndStatus(
            @Param("eventId") Long eventId,
            @Param("seatNumbers") List<String> seatNumbers,
            @Param("status") String status);

    /**
     * Conditionally marks seats as booked — only rows still 'available' are updated.
     *
     * The AND s.status = 'available' guard is a second layer of safety beyond the
     * pessimistic lock: even if two transactions somehow both passed the SELECT check,
     * only one UPDATE can mark all N rows (the other updates 0 rows). SeatService
     * validates the returned row count and throws if it does not match.
     *
     * Returns the number of rows actually updated — caller must verify == seatNumbers.size().
     */
    @Modifying
    @Query("UPDATE Seat s SET s.status = 'booked' " +
           "WHERE s.eventId = :eventId AND s.seatNumber IN :seatNumbers AND s.status = 'available'")
    int markSeatsBooked(@Param("eventId") Long eventId, @Param("seatNumbers") List<String> seatNumbers);

    @Modifying
    @Query("UPDATE Seat s SET s.status = 'available' " +
           "WHERE s.eventId = :eventId AND s.seatNumber IN :seatNumbers")
    int markSeatsAvailable(@Param("eventId") Long eventId, @Param("seatNumbers") List<String> seatNumbers);

    // ── Feature 4: Seat hold timer ────────────────────────────────────────────

    /**
     * Transitions seats from 'available' → 'held' for a specific user.
     * hold_until is set to now + holdMinutes. Returns rows changed.
     * The caller must validate returned count == seatNumbers.size().
     */
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

    /**
     * Sweeps expired holds — called by @Scheduled every minute.
     * Releases any seat whose held_until < NOW() and status = 'held'.
     */
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

    /**
     * Transitions already-held seats to booked for the same user.
     * Called in SeatService.bookSeats after payment confirmation
     * when seats were pre-held during checkout.
     */
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

    /** Find seats held by a specific user for an event (used in hold validation). */
    List<Seat> findByEventIdAndHeldByUserIdAndStatus(
            Long eventId, Long userId, String status);

    // ── Feature 3: Category queries ───────────────────────────────────────────
    List<Seat> findByEventIdAndCategoryOrderBySeatNumberAsc(Long eventId, String category);
}
