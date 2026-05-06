package com.ticketapp.controller;

import com.ticketapp.entity.Wishlist;
import com.ticketapp.security.AuthenticatedUser;
import com.ticketapp.service.WishlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * WishlistController — Feature 6: Wishlist / "Notify me".
 *
 * POST   /wishlist/{eventId}   body: { "notify": true }   → save event to wishlist
 * DELETE /wishlist/{eventId}                               → remove from wishlist
 * GET    /wishlist                                         → user's saved events
 */
@RestController
@RequestMapping("/wishlist")
@RequiredArgsConstructor
@Slf4j
public class WishlistController {

    private final WishlistService wishlistService;

    // ── Save an event ─────────────────────────────────────────────────────────

    @PostMapping("/{eventId}")
    public ResponseEntity<?> save(
            @PathVariable Long eventId,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal AuthenticatedUser user) {

        boolean notify = body != null
                && Boolean.TRUE.equals(body.get("notify"));

        try {
            Wishlist w = wishlistService.save(user.getId(), eventId, notify);
            return ResponseEntity.ok(w);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Remove from wishlist ──────────────────────────────────────────────────

    @DeleteMapping("/{eventId}")
    public ResponseEntity<?> remove(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        wishlistService.remove(user.getId(), eventId);
        return ResponseEntity.ok(Map.of("message", "Removed from wishlist."));
    }

    // ── Get user's wishlist ───────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<Wishlist>> getMyWishlist(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(wishlistService.getForUser(user.getId()));
    }
}
