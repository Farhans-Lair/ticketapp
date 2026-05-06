package com.ticketapp.controller;

import com.ticketapp.entity.Showtime;
import com.ticketapp.service.ShowtimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/showtimes")
@RequiredArgsConstructor
@Slf4j
public class ShowtimeController {

    private final ShowtimeService showtimeService;

    /**
     * GET /showtimes/{id}
     * Returns full detail including movie + cinema for the booking page.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getDetail(@PathVariable Long id) {
        try {
            Map<String, Object> detail = showtimeService.getShowtimeDetail(id);
            return ResponseEntity.ok(detail);
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /showtimes/movie/{movieId}
     * All upcoming showtimes for a movie (all cities).
     */
    @GetMapping("/movie/{movieId}")
    public ResponseEntity<List<Showtime>> getByMovie(@PathVariable Long movieId) {
        return ResponseEntity.ok(showtimeService.getUpcomingByMovie(movieId));
    }

    /**
     * GET /showtimes/movie/{movieId}/city/{city}
     * Showtimes grouped by cinema for a specific city — drives the "Book Tickets" page.
     */
    @GetMapping("/movie/{movieId}/city/{city}")
    public ResponseEntity<List<Map<String, Object>>> getByMovieAndCity(
            @PathVariable Long movieId,
            @PathVariable String city) {
        return ResponseEntity.ok(showtimeService.getShowtimesByMovieAndCity(movieId, city));
    }

    /** POST /showtimes  (admin/organizer) */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Showtime showtime) {
        try {
            return ResponseEntity.ok(showtimeService.createShowtime(showtime));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
