package com.ticketapp.repository;

import com.ticketapp.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    // ── Backward-compat alias used by RevenueController ───────────────────────
    /** Returns ALL events regardless of status — admin-only use. */
    List<Event> findAllByOrderByEventDateAsc();

    // ── Organizer / Admin (no status filter) ──────────────────────────────────
    List<Event> findByOrganizerIdOrderByEventDateAsc(Long organizerId);
    Optional<Event> findByIdAndOrganizerId(Long id, Long organizerId);

    // ── Public listing (published only) ──────────────────────────────────────
    List<Event> findByEventStatusOrderByEventDateAsc(String eventStatus);
    List<Event> findByCategoryAndEventStatusOrderByEventDateAsc(String category, String eventStatus);

    // ── Feature 11: Featured events ───────────────────────────────────────────
    /**
     * Returns featured events that are published and not yet expired.
     * featuredUntil IS NULL means permanently featured.
     */
    @Query("""
        SELECT e FROM Event e
        WHERE e.isFeatured = true
          AND e.eventStatus = 'published'
          AND (e.featuredUntil IS NULL OR e.featuredUntil > :now)
        ORDER BY e.eventDate ASC
    """)
    List<Event> findActiveFeaturedEvents(@Param("now") LocalDateTime now);

    /**
     * Trending = top 6 events by booking count in the last 7 days.
     */
    @Query("""
        SELECT e FROM Event e
        WHERE e.id IN (
            SELECT b.eventId FROM Booking b
            WHERE b.bookingDate >= :since
              AND b.paymentStatus = 'paid'
            GROUP BY b.eventId
            ORDER BY COUNT(b) DESC
        )
          AND e.eventStatus = 'published'
        ORDER BY e.eventDate ASC
    """)
    List<Event> findTrendingEvents(@Param("since") LocalDateTime since);

    // ── Feature 13: Moderation ────────────────────────────────────────────────
    List<Event> findByEventStatusOrderByCreatedAtDesc(String eventStatus);

    // ── Feature 2: City selector ──────────────────────────────────────────────
    List<Event> findByCityIgnoreCaseOrderByEventDateAsc(String city);
    List<Event> findByCityIgnoreCaseAndCategoryOrderByEventDateAsc(String city, String category);

    /** Only cities from published events appear in the dropdown. */
    @Query("SELECT DISTINCT LOWER(e.city) FROM Event e WHERE e.city IS NOT NULL AND e.eventStatus = 'published' ORDER BY 1 ASC")
    List<String> findDistinctCities();

    // ── Global search (published only) ────────────────────────────────────────
    @Query("""
        SELECT e FROM Event e
        WHERE e.eventStatus = 'published'
          AND (
               LOWER(e.title)       LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(e.description) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(e.location)    LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(e.city)        LIKE LOWER(CONCAT('%', :q, '%'))
          )
        ORDER BY e.eventDate ASC
    """)
    List<Event> search(@Param("q") String query);

    // ── Filtered search (status parameter lets public pass 'published', admin pass null) ──
    @Query("""
        SELECT e FROM Event e
        WHERE (:status   IS NULL OR e.eventStatus  = :status)
          AND (:city     IS NULL OR LOWER(e.city)     = LOWER(:city))
          AND (:category IS NULL OR LOWER(e.category) = LOWER(:category))
          AND (:minPrice IS NULL OR e.price >= :minPrice)
          AND (:maxPrice IS NULL OR e.price <= :maxPrice)
          AND (:dateFrom IS NULL OR e.eventDate >= :dateFrom)
          AND (:dateTo   IS NULL OR e.eventDate <= :dateTo)
        ORDER BY e.eventDate ASC
    """)
    List<Event> findFiltered(
            @Param("status")   String status,
            @Param("city")     String city,
            @Param("category") String category,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo")   LocalDateTime dateTo);
}
