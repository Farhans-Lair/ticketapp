package com.ticketapp.repository;

import com.ticketapp.entity.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    List<Movie> findByStatusOrderByTitleAsc(String status);

    List<Movie> findByGenreAndStatusOrderByTitleAsc(String genre, String status);

    /** Full-text style search across title, cast, director, genre. */
    @Query("""
        SELECT m FROM Movie m
        WHERE m.status = 'active'
          AND (
            LOWER(m.title)    LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(m.cast)      LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(m.director)  LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(m.genre)     LIKE LOWER(CONCAT('%', :q, '%'))
          )
        ORDER BY m.title ASC
    """)
    List<Movie> search(@Param("q") String query);
}
