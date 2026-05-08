package com.ticketapp.controller;

import com.ticketapp.entity.Event;
import com.ticketapp.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * SearchController — global search + filters for events.
 *
 * GET /search?q=...               → search across event title, description, location, city
 * GET /search/events?city=...     → filtered event listing (all params optional)
 * GET /search/cities              → distinct city list for the city-picker dropdown
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Slf4j
public class SearchController {

    private final EventRepository eventRepo;

    // ── GET /search?q=... ─────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<Map<String, Object>> globalSearch(@RequestParam String q) {
        if (q == null || q.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Query param 'q' is required."));

        String trimmed = q.trim();
        log.info("Global search: q={}", trimmed);

        List<Event> events = eventRepo.search(trimmed);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query",  trimmed);
        result.put("events", events);
        result.put("total",  events.size());
        return ResponseEntity.ok(result);
    }

    // ── GET /search/events (filtered) ────────────────────────────────────────

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
                minPrice, maxPrice, from, to);

        log.info("Filtered search: city={} category={} price={}-{} → {} results",
                city, category, minPrice, maxPrice, events.size());

        return ResponseEntity.ok(events);
    }

    // ── GET /search/cities ────────────────────────────────────────────────────

    @GetMapping("/cities")
    public ResponseEntity<List<String>> cities() {
        Set<String> citySet = new TreeSet<>();
        eventRepo.findDistinctCities().stream()
                .filter(c -> c != null && !c.isBlank())
                .forEach(citySet::add);
        return ResponseEntity.ok(new ArrayList<>(citySet));
    }
}
