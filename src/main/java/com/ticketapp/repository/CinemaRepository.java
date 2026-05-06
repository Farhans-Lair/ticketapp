package com.ticketapp.repository;

import com.ticketapp.entity.Cinema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CinemaRepository extends JpaRepository<Cinema, Long> {

    List<Cinema> findByCityIgnoreCaseAndStatusOrderByNameAsc(String city, String status);

    @Query("SELECT DISTINCT LOWER(c.city) FROM Cinema c WHERE c.status = 'active' ORDER BY 1 ASC")
    List<String> findDistinctActiveCities();

    @Query("""
        SELECT c FROM Cinema c
        WHERE c.status = 'active'
          AND LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%'))
    """)
    List<Cinema> searchByName(@Param("q") String query);
}
