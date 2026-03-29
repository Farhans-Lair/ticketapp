package com.ticketapp.repository;

import com.ticketapp.entity.OrganizerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrganizerProfileRepository extends JpaRepository<OrganizerProfile, Long> {
    Optional<OrganizerProfile> findByUserId(Long userId);
    List<OrganizerProfile> findByStatusOrderByCreatedAtDesc(String status);
    List<OrganizerProfile> findAllByOrderByCreatedAtDesc();
}
