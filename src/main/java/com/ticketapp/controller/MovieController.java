package com.ticketapp.controller;

import com.ticketapp.entity.Movie;
import com.ticketapp.service.MovieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/movies")
@RequiredArgsConstructor
@Slf4j
public class MovieController {

    private final MovieService movieService;

    /** GET /movies — all active movies */
    @GetMapping
    public ResponseEntity<List<Movie>> getAll() {
        return ResponseEntity.ok(movieService.getAllActive());
    }

    /** GET /movies/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return movieService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET /movies/search?q=avengers */
    @GetMapping("/search")
    public ResponseEntity<List<Movie>> search(@RequestParam String q) {
        return ResponseEntity.ok(movieService.search(q));
    }

    /** GET /movies/genre/{genre} */
    @GetMapping("/genre/{genre}")
    public ResponseEntity<List<Movie>> byGenre(@PathVariable String genre) {
        return ResponseEntity.ok(movieService.getByGenre(genre));
    }

    /** POST /movies  (admin only — add @PreAuthorize if using method security) */
    @PostMapping
    public ResponseEntity<Movie> create(@RequestBody Movie movie) {
        return ResponseEntity.ok(movieService.create(movie));
    }

    /** PUT /movies/{id} */
    @PutMapping("/{id}")
    public ResponseEntity<Movie> update(@PathVariable Long id, @RequestBody Movie updates) {
        return ResponseEntity.ok(movieService.update(id, updates));
    }
}
