package com.ticketapp.repository;

import com.ticketapp.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findAllByOrderByEventDateAsc();
    List<Event> findByCategoryOrderByEventDateAsc(String category);
    List<Event> findByOrganizerIdOrderByEventDateAsc(Long organizerId);
    Optional<Event> findByIdAndOrganizerId(Long id, Long organizerId);

    // ── Feature 2: City selector ──────────────────────────────────────────────
    List<Event> findByCityIgnoreCaseOrderByEventDateAsc(String city);

    List<Event> findByCityIgnoreCaseAndCategoryOrderByEventDateAsc(String city, String category);

    @Query("SELECT DISTINCT LOWER(e.city) FROM Event e WHERE e.city IS NOT NULL ORDER BY 1 ASC")
    List<String> findDistinctCities();

    /**
     * Global search across title, description, location, and city.
     * Used by GET /search?q=...  (Feature 2).
     */
    @Query("""
        SELECT e FROM Event e
        WHERE LOWER(e.title)       LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(e.description) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(e.location)    LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(e.city)        LIKE LOWER(CONCAT('%', :q, '%'))
        ORDER BY e.eventDate ASC
    """)
    List<Event> search(@Param("q") String query);

    /**
     * Filtered search for the events listing page.
     * All parameters are optional — pass null to skip a filter.
     */
    @Query("""
        SELECT e FROM Event e
        WHERE (:city     IS NULL OR LOWER(e.city)     = LOWER(:city))
          AND (:category IS NULL OR LOWER(e.category) = LOWER(:category))
          AND (:minPrice IS NULL OR e.price >= :minPrice)
          AND (:maxPrice IS NULL OR e.price <= :maxPrice)
          AND (:dateFrom IS NULL OR e.eventDate >= :dateFrom)
          AND (:dateTo   IS NULL OR e.eventDate <= :dateTo)
        ORDER BY e.eventDate ASC
    """)
    List<Event> findFiltered(
            @Param("city")     String city,
            @Param("category") String category,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("dateFrom") java.time.LocalDateTime dateFrom,
            @Param("dateTo")   java.time.LocalDateTime dateTo);
}

