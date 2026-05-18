package com.ticketapp.repository;

import com.ticketapp.entity.EventCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventCategoryRepository extends JpaRepository<EventCategory, Long> {

    /** Active categories sorted by sort_order ASC, then name ASC — for the public endpoint. */
    List<EventCategory> findByIsActiveTrueOrderBySortOrderAscNameAsc();

    /** All categories (including inactive) — for the admin endpoint. */
    List<EventCategory> findAllByOrderBySortOrderAscNameAsc();

    /** Slug uniqueness check before create/update. */
    Optional<EventCategory> findBySlug(String slug);
}
