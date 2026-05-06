	package com.ticketapp.repository;

import com.ticketapp.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    /** All upcoming (not cancelled) showtimes for a given movie, sorted by time. */
    @Query("""
        SELECT s FROM Showtime s
        WHERE s.movieId = :movieId
          AND s.status = 'active'
          AND s.startTime >= :from
        ORDER BY s.startTime ASC
    """)
    List<Showtime> findUpcomingByMovie(
            @Param("movieId") Long movieId,
            @Param("from") LocalDateTime from);

    /**
     * Showtimes for a movie in a specific city, joined through screen → cinema.
     * Used by the city-filtered movie listing page.
     */
    @Query("""
        SELECT st FROM Showtime st
        JOIN Screen sc ON sc.id = st.screenId
        JOIN Cinema ci ON ci.id = sc.cinemaId
        WHERE st.movieId  = :movieId
          AND ci.city     = :city
          AND st.status   = 'active'
          AND st.startTime >= :from
        ORDER BY st.startTime ASC
    """)
    List<Showtime> findByMovieAndCity(
            @Param("movieId") Long movieId,
            @Param("city") String city,
            @Param("from") LocalDateTime from);

    List<Showtime> findByScreenIdAndStatusOrderByStartTimeAsc(Long screenId, String status);

    /**
     * All showtimes for a movie on a given date (midnight → midnight).
     * Used for the date-range filter.
     */
    @Query("""
        SELECT s FROM Showtime s
        WHERE s.movieId = :movieId
          AND s.startTime BETWEEN :dayStart AND :dayEnd
          AND s.status = 'active'
        ORDER BY s.startTime ASC
    """)
    List<Showtime> findByMovieAndDateRange(
            @Param("movieId") Long movieId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd);

    /** Showtimes filtered by format (2D/3D/IMAX/4DX). */
    List<Showtime> findByMovieIdAndFormatAndStatusOrderByStartTimeAsc(
            Long movieId, String format, String status);
}
