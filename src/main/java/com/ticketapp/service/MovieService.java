package com.ticketapp.service;

import com.ticketapp.entity.Movie;
import com.ticketapp.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MovieService {

    private final MovieRepository movieRepo;

    public List<Movie> getAllActive() {
        return movieRepo.findByStatusOrderByTitleAsc("active");
    }

    public Optional<Movie> getById(Long id) {
        return movieRepo.findById(id);
    }

    public List<Movie> search(String q) {
        return movieRepo.search(q);
    }

    public List<Movie> getByGenre(String genre) {
        return movieRepo.findByGenreAndStatusOrderByTitleAsc(genre, "active");
    }

    @Transactional
    public Movie create(Movie movie) {
        Movie saved = movieRepo.save(movie);
        log.info("Movie created: id={} title={}", saved.getId(), saved.getTitle());
        return saved;
    }

    @Transactional
    public Movie update(Long id, Movie updates) {
        Movie movie = movieRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Movie not found"));
        if (updates.getTitle()         != null) movie.setTitle(updates.getTitle());
        if (updates.getDescription()   != null) movie.setDescription(updates.getDescription());
        if (updates.getCast()          != null) movie.setCast(updates.getCast());
        if (updates.getDirector()      != null) movie.setDirector(updates.getDirector());
        if (updates.getRuntimeMinutes()!= null) movie.setRuntimeMinutes(updates.getRuntimeMinutes());
        if (updates.getGenre()         != null) movie.setGenre(updates.getGenre());
        if (updates.getLanguage()      != null) movie.setLanguage(updates.getLanguage());
        if (updates.getCertification() != null) movie.setCertification(updates.getCertification());
        if (updates.getTrailerUrl()    != null) movie.setTrailerUrl(updates.getTrailerUrl());
        if (updates.getPosterUrl()     != null) movie.setPosterUrl(updates.getPosterUrl());
        if (updates.getStatus()        != null) movie.setStatus(updates.getStatus());
        return movieRepo.save(movie);
    }
}
