package com.ticketapp.repository;

import com.ticketapp.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByEventIdOrderByCreatedAtDesc(Long eventId);

    List<Review> findByMovieIdOrderByCreatedAtDesc(Long movieId);

    Optional<Review> findByUserIdAndEventId(Long userId, Long eventId);

    Optional<Review> findByUserIdAndMovieId(Long userId, Long movieId);

    /** Average rating from verified reviews only. Returns null if no verified reviews. */
    @Query("""
        SELECT AVG(r.rating) FROM Review r
        WHERE r.eventId = :eventId AND r.verifiedBooking = true
    """)
    Double avgVerifiedRatingByEvent(@Param("eventId") Long eventId);

    @Query("""
        SELECT COUNT(r) FROM Review r
        WHERE r.eventId = :eventId AND r.verifiedBooking = true
    """)
    Long countVerifiedByEvent(@Param("eventId") Long eventId);

    @Query("""
        SELECT AVG(r.rating) FROM Review r
        WHERE r.movieId = :movieId AND r.verifiedBooking = true
    """)
    Double avgVerifiedRatingByMovie(@Param("movieId") Long movieId);
}
