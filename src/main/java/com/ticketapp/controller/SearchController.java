package com.ticketapp.controller;

import com.ticketapp.entity.Cinema;
import com.ticketapp.entity.Event;
import com.ticketapp.entity.Movie;
import com.ticketapp.repository.CinemaRepository;
import com.ticketapp.repository.EventRepository;
import com.ticketapp.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * SearchController — Feature 2: Global search + filters.
 *
 * GET /search?q=avengers
 *   → hits title, description, cast, venue name across events, movies, cinemas
 *
 * GET /search/events?city=Mumbai&category=Music&minPrice=0&maxPrice=500&dateFrom=2025-12-01&dateTo=2025-12-31
 *   → filtered event listing (all params optional)
 *
 * GET /search/cities
 *   → distinct city list for the city-picker dropdown
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Slf4j
public class SearchController {

    private final EventRepository  eventRepo;
    private final MovieRepository  movieRepo;
    private final CinemaRepository cinemaRepo;

    // ── GET /search?q=... ─────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<Map<String, Object>> globalSearch(@RequestParam String q) {
        if (q == null || q.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Query param 'q' is required."));

        String trimmed = q.trim();
        log.info("Global search: q={}", trimmed);

        List<Event>  events  = eventRepo.search(trimmed);
        List<Movie>  movies  = movieRepo.search(trimmed);
        List<Cinema> cinemas = cinemaRepo.searchByName(trimmed);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query",   trimmed);
        result.put("events",  events);
        result.put("movies",  movies);
        result.put("cinemas", cinemas);
        result.put("total",   events.size() + movies.size() + cinemas.size());
        return ResponseEntity.ok(result);
    }

    // ── GET /search/events (filtered) ────────────────────────────────────────

    /**
     * All params are optional. Absent params are treated as "no filter".
     *
     * @param city      exact city match (case-insensitive)
     * @param category  event category
     * @param minPrice  minimum ticket price (inclusive)
     * @param maxPrice  maximum ticket price (inclusive)
     * @param dateFrom  earliest event date (ISO: 2025-12-01)
     * @param dateTo    latest event date   (ISO: 2025-12-31)
     */
    @GetMapping("/events")
    public ResponseEntity<List<Event>> filteredEvents(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        LocalDateTime from = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime to   = dateTo   != null ? dateTo.atTime(23, 59, 59) : null;

        List<Event> events = eventRepo.findFiltered(
                (city     != null && !city.isBlank())     ? city     : null,
                (category != null && !category.isBlank()) ? category : null,
                minPrice,
                maxPrice,
                from,
                to
        );

        log.info("Filtered event search: city={} category={} price={}-{} date={}-{} → {} results",
                city, category, minPrice, maxPrice, dateFrom, dateTo, events.size());

        return ResponseEntity.ok(events);
    }

    // ── GET /search/cities ────────────────────────────────────────────────────

    /**
     * Aggregated list of all cities that have at least one event or cinema.
     * Drives the "Which city?" picker on the home page.
     */
    @GetMapping("/cities")
    public ResponseEntity<List<String>> cities() {
        Set<String> citySet = new TreeSet<>();
        eventRepo.findDistinctCities().stream()
                .filter(c -> c != null && !c.isBlank())
                .forEach(citySet::add);
        cinemaRepo.findDistinctActiveCities().stream()
                .filter(c -> c != null && !c.isBlank())
                .forEach(citySet::add);
        return ResponseEntity.ok(new ArrayList<>(citySet));
    }
}
