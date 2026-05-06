package com.ticketapp.service;

import com.ticketapp.entity.Cinema;
import com.ticketapp.entity.Movie;
import com.ticketapp.entity.Screen;
import com.ticketapp.entity.Showtime;
import com.ticketapp.repository.CinemaRepository;
import com.ticketapp.repository.MovieRepository;
import com.ticketapp.repository.ScreenRepository;
import com.ticketapp.repository.ShowtimeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShowtimeService {

    private final ShowtimeRepository  showtimeRepo;
    private final MovieRepository     movieRepo;
    private final ScreenRepository    screenRepo;
    private final CinemaRepository    cinemaRepo;
    private final SeatService         seatService;

    public Optional<Showtime> getById(Long id) {
        return showtimeRepo.findById(id);
    }

    public List<Showtime> getUpcomingByMovie(Long movieId) {
        return showtimeRepo.findUpcomingByMovie(movieId, LocalDateTime.now());
    }

    /**
     * Returns a grouped view: for each cinema in the given city that
     * screens this movie, lists all upcoming showtimes.
     * This is the data structure the "Book Tickets" page needs.
     */
    public List<Map<String, Object>> getShowtimesByMovieAndCity(Long movieId, String city) {
        List<Showtime> showtimes = showtimeRepo.findByMovieAndCity(
                movieId, city, LocalDateTime.now());

        // Group by screen → cinema for the response
        Map<Long, List<Showtime>> byScreen = showtimes.stream()
                .collect(Collectors.groupingBy(Showtime::getScreenId));

        return byScreen.entrySet().stream().map(entry -> {
            Long screenId = entry.getKey();
            Screen screen = screenRepo.findById(screenId).orElse(null);
            Cinema cinema = screen != null ? cinemaRepo.findById(screen.getCinemaId()).orElse(null) : null;

            Map<String, Object> group = new LinkedHashMap<>();
            group.put("screen_id",   screenId);
            group.put("screen_name", screen != null ? screen.getName() : "");
            group.put("screen_type", screen != null ? screen.getScreenType() : "");
            group.put("cinema_id",   cinema != null ? cinema.getId() : null);
            group.put("cinema_name", cinema != null ? cinema.getName() : "");
            group.put("cinema_address", cinema != null ? cinema.getAddress() : "");
            group.put("showtimes",   entry.getValue());
            return group;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Showtime createShowtime(Showtime showtime) {
        // Validate movie and screen exist
        movieRepo.findById(showtime.getMovieId())
                .orElseThrow(() -> new RuntimeException("Movie not found"));
        Screen screen = screenRepo.findById(showtime.getScreenId())
                .orElseThrow(() -> new RuntimeException("Screen not found"));

        // Seed available/total seats from screen capacity
        if (showtime.getTotalSeats() == null && screen.getTotalCapacity() != null) {
            showtime.setTotalSeats(screen.getTotalCapacity());
            showtime.setAvailableSeats(screen.getTotalCapacity());
        }

        Showtime saved = showtimeRepo.save(showtime);
        log.info("Showtime created: id={} movieId={} screenId={} start={}",
                saved.getId(), saved.getMovieId(), saved.getScreenId(), saved.getStartTime());
        return saved;
    }

    /** Enrich a showtime with full movie + cinema details for the booking page. */
    public Map<String, Object> getShowtimeDetail(Long showtimeId) {
        Showtime st = showtimeRepo.findById(showtimeId)
                .orElseThrow(() -> new RuntimeException("Showtime not found"));
        Movie  movie  = movieRepo.findById(st.getMovieId()).orElse(null);
        Screen screen = screenRepo.findById(st.getScreenId()).orElse(null);
        Cinema cinema = screen != null ? cinemaRepo.findById(screen.getCinemaId()).orElse(null) : null;

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("showtime", st);
        detail.put("movie",    movie);
        detail.put("screen",   screen);
        detail.put("cinema",   cinema);
        return detail;
    }
}
