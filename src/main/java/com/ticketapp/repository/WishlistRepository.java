package com.ticketapp.repository;

import com.ticketapp.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    List<Wishlist> findByUserIdOrderBySavedAtDesc(Long userId);

    Optional<Wishlist> findByUserIdAndEventId(Long userId, Long eventId);

    /**
     * All users who want to be notified when tickets open for this event.
     * Called from WaitlistService / CancellationService after a cancellation
     * frees capacity.
     */
    @Query("""
        SELECT w FROM Wishlist w
        WHERE w.eventId = :eventId
          AND w.notifyOnAvailability = true
    """)
    List<Wishlist> findNotifySubscribers(@Param("eventId") Long eventId);

    boolean existsByUserIdAndEventId(Long userId, Long eventId);
}
