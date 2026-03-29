package com.ticketapp.repository;

import com.ticketapp.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findAllByOrderByEventDateAsc();
    List<Event> findByCategoryOrderByEventDateAsc(String category);
    List<Event> findByOrganizerIdOrderByEventDateAsc(Long organizerId);
    Optional<Event> findByIdAndOrganizerId(Long id, Long organizerId);
}
