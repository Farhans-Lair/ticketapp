package com.ticketapp.controller;

import com.ticketapp.entity.Review;
import com.ticketapp.exception.ValidationException;
import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
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
@Validated
public class ReviewController {

    private final ReviewService reviewService;

    // ── Typed request DTO (replaces raw Map<String,Object>) ──────────────────
    @Data
    public static class ReviewRequest {
        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating must be at most 5")
        private Integer rating;

        /** Limit review text to 2 000 characters — prevents db TEXT overflow attacks. */
        @Size(max = 2000, message = "Review text must be 2 000 characters or fewer")
        private String text;
    }

    // ── Submit a review ───────────────────────────────────────────────────────

    @PostMapping("/events/{eventId}")
    public ResponseEntity<?> submitReview(
            @PathVariable Long eventId,
            @Valid @RequestBody ReviewRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {

        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated."));

        Review review = reviewService.submitReview(
            user.getId(), eventId, body.getRating(), body.getText());
        return ResponseEntity.ok(review);
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
