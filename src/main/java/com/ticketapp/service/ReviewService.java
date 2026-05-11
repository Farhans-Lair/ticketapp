package com.ticketapp.service;

import com.ticketapp.entity.Event;
import com.ticketapp.entity.Review;
import com.ticketapp.repository.BookingRepository;
import com.ticketapp.repository.EventRepository;
import com.ticketapp.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository  reviewRepo;
    private final BookingRepository bookingRepo;
    private final EventRepository   eventRepo;

    // ── Submit a review ───────────────────────────────────────────────────────

    @Transactional
    public Review submitReview(Long userId, Long eventId, int rating, String text) {
        // Idempotent: one review per user per event
        Optional<Review> existing = reviewRepo.findByUserIdAndEventId(userId, eventId);
        if (existing.isPresent())
            throw new RuntimeException("You have already reviewed this event.");

        if (rating < 1 || rating > 5)
            throw new RuntimeException("Rating must be between 1 and 5.");

        // Hard enforcement: user must have a paid, active booking to leave a review.
        // Runs on every submission — direct API calls without a booking are blocked here.
        if (!bookingRepo.hasActivePaidBooking(userId, eventId))
            throw new RuntimeException(
                "You must have a paid booking for this event to leave a review.");

        Review review = new Review();
        review.setUserId(userId);
        review.setEventId(eventId);
        review.setRating(rating);
        review.setText(text);
        review.setVerifiedBooking(true);   // always true — non-bookers blocked above
        Review saved = reviewRepo.save(review);

        // Update cached average on the event
        updateEventAverageRating(eventId);

        log.info("Review submitted: userId={} eventId={} rating={}", userId, eventId, rating);
        return saved;
    }

    public List<Review> getReviewsByEvent(Long eventId) {
        return reviewRepo.findByEventIdOrderByCreatedAtDesc(eventId);
    }

    public Map<String, Object> getEventRatingSummary(Long eventId) {
        Double avg   = reviewRepo.avgVerifiedRatingByEvent(eventId);
        Long   count = reviewRepo.countVerifiedByEvent(eventId);
        return Map.of(
            "average_rating", avg   != null ? Math.round(avg * 10.0) / 10.0 : 0.0,
            "review_count",   count != null ? count : 0L
        );
    }

    /** Recalculates and caches the average rating on the Event row. */
    private void updateEventAverageRating(Long eventId) {
        Event event = eventRepo.findById(eventId).orElse(null);
        if (event == null) return;

        Double avg   = reviewRepo.avgVerifiedRatingByEvent(eventId);
        Long   count = reviewRepo.countVerifiedByEvent(eventId);
        event.setAverageRating(avg != null ? Math.round(avg * 10.0) / 10.0 : null);
        event.setReviewCount(count != null ? count.intValue() : 0);
        eventRepo.save(event);
    }
}
