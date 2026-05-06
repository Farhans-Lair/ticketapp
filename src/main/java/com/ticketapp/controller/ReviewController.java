package com.ticketapp.controller;

import com.ticketapp.entity.Review;
import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ReviewController — Feature 5: Reviews & ratings.
 *
 * POST /reviews/events/{eventId}         → submit a review (auth required)
 * GET  /reviews/events/{eventId}         → all reviews for an event
 * GET  /reviews/events/{eventId}/summary → avg rating + count
 */
@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
@Slf4j
public class ReviewController {

    private final ReviewService reviewService;

    // ── Submit a review ───────────────────────────────────────────────────────

    @PostMapping("/events/{eventId}")
    public ResponseEntity<?> submitReview(
            @PathVariable Long eventId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal AuthenticatedUser user) {

        int rating = ((Number) body.getOrDefault("rating", 0)).intValue();
        String text = (String) body.getOrDefault("text", "");

        try {
            Review review = reviewService.submitReview(user.getId(), eventId, rating, text);
            return ResponseEntity.ok(review);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Get all reviews for an event ──────────────────────────────────────────

    @GetMapping("/events/{eventId}")
    public ResponseEntity<List<Review>> getEventReviews(@PathVariable Long eventId) {
        return ResponseEntity.ok(reviewService.getReviewsByEvent(eventId));
    }

    // ── Rating summary (avg + count) ──────────────────────────────────────────

    @GetMapping("/events/{eventId}/summary")
    public ResponseEntity<Map<String, Object>> getRatingSummary(@PathVariable Long eventId) {
        return ResponseEntity.ok(reviewService.getEventRatingSummary(eventId));
    }
}
