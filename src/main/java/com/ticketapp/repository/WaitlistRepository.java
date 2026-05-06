package com.ticketapp.repository;

import com.ticketapp.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    Optional<WaitlistEntry> findByUserIdAndEventId(Long userId, Long eventId);

    boolean existsByUserIdAndEventId(Long userId, Long eventId);

    /**
     * Oldest waiting entries for this event that haven't been notified yet.
     * Used after a cancellation to email next-in-line users.
     *
     * @param eventId       the freed event
     * @param minTickets    free seats now available
     */
    @Query("""
        SELECT w FROM WaitlistEntry w
        WHERE w.eventId = :eventId
          AND w.status = 'waiting'
          AND w.ticketsWanted <= :minTickets
        ORDER BY w.joinedAt ASC
    """)
    List<WaitlistEntry> findEligibleWaiters(
            @Param("eventId") Long eventId,
            @Param("minTickets") int minTickets);

    List<WaitlistEntry> findByUserIdOrderByJoinedAtDesc(Long userId);

    long countByEventIdAndStatus(Long eventId, String status);
}
