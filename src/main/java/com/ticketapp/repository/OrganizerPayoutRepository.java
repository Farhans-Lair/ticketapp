package com.ticketapp.repository;

import com.ticketapp.entity.OrganizerPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface OrganizerPayoutRepository extends JpaRepository<OrganizerPayout, Long> {

    List<OrganizerPayout> findByOrganizerIdOrderByRequestedAtDesc(Long organizerId);

    List<OrganizerPayout> findByStatusOrderByRequestedAtDesc(String status);

    List<OrganizerPayout> findAllByOrderByRequestedAtDesc();

    /**
     * Detects whether a pending or processing payout already covers any part
     * of the requested date range for this organizer.
     * Prevents double-requesting payouts for overlapping periods.
     */
    @Query("""
        SELECT COUNT(p) > 0 FROM OrganizerPayout p
        WHERE p.organizerId = :organizerId
          AND p.status IN ('requested', 'processing')
          AND p.fromDate <= :toDate
          AND p.toDate   >= :fromDate
    """)
    boolean existsOverlappingPayout(
            @Param("organizerId") Long organizerId,
            @Param("fromDate")    LocalDate fromDate,
            @Param("toDate")      LocalDate toDate);
}
