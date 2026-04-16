package com.ticketapp.service;

import com.ticketapp.entity.Event;
import com.ticketapp.entity.Seat;
import com.ticketapp.repository.EventRepository;
import com.ticketapp.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepo;
    private final SeatRepository  seatRepo;
    private final SeatService     seatService;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public Event createEvent(String title, String description, String location,
                             String eventDateStr, Double price, Integer totalTickets,
                             String category, String imagesJson, Long organizerId) {
        Event event = new Event();
        event.setTitle(title);
        event.setDescription(description);
        event.setLocation(location);
        event.setEventDate(parseDate(eventDateStr));
        event.setPrice(price != null ? price : 0.0);
        event.setTotalTickets(totalTickets);
        event.setAvailableTickets(totalTickets);
        event.setCategory(category != null ? category : "Other");
        event.setImages(imagesJson);
        event.setOrganizerId(organizerId);
        event = eventRepo.save(event);

        // Auto-generate seats
        seatService.generateSeats(event.getId(), totalTickets);

        return event;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<Event> getAllEvents(String category) {
        if (category != null && !category.isBlank()) {
            return eventRepo.findByCategoryOrderByEventDateAsc(category);
        }
        return eventRepo.findAllByOrderByEventDateAsc();
    }

    public List<Event> getEventsByOrganizer(Long organizerId) {
        return eventRepo.findByOrganizerIdOrderByEventDateAsc(organizerId);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * organizerId = null → admin update (no ownership check)
     * organizerId = value → organizer update (must own the event)
     */
    @Transactional
    public Event updateEvent(Long id, String title, String description, String location,
                             String eventDateStr, Double price, Integer totalTickets,
                             String category, String imagesJson, Long organizerId) {
        Event event;
        if (organizerId != null) {
            event = eventRepo.findByIdAndOrganizerId(id, organizerId).orElse(null);
        } else {
            event = eventRepo.findById(id).orElse(null);
        }
        if (event == null) return null;

        // Business rule: cannot reduce below sold count
        int soldTickets = event.getTotalTickets() - event.getAvailableTickets();
        if (totalTickets != null && totalTickets < soldTickets) {
            throw new RuntimeException(
                "Cannot reduce total tickets below sold count (" + soldTickets + ").");
        }

        if (title        != null) event.setTitle(title);
        if (description  != null) event.setDescription(description);
        if (location     != null) event.setLocation(location);
        if (eventDateStr != null) event.setEventDate(parseDate(eventDateStr));
        if (price        != null) event.setPrice(price);
        if (category     != null) event.setCategory(category);
        if (imagesJson   != null) event.setImages(imagesJson);

        if (totalTickets != null) {
            int diff = totalTickets - event.getTotalTickets();
            event.setAvailableTickets(event.getAvailableTickets() + diff);
            event.setTotalTickets(totalTickets);

            // Add new seats if capacity grew
            if (diff > 0) {
                long existingCount = seatRepo.countByEventId(id);
                List<Seat> allSeats = seatService.generateSeats(id, totalTickets);
                List<Seat> newSeats = allSeats.stream()
                        .skip(existingCount)
                        .toList();
                if (!newSeats.isEmpty()) seatRepo.saveAll(newSeats);
            }
        }

        return eventRepo.save(event);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public boolean deleteEvent(Long id, Long organizerId) {
        if (organizerId != null) {
            // Organizer-scoped: verify ownership
            Event event = eventRepo.findByIdAndOrganizerId(id, organizerId).orElse(null);
            if (event == null) return false;
            eventRepo.delete(event);
            return true;
        }
        if (!eventRepo.existsById(id)) return false;
        eventRepo.deleteById(id);
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LocalDateTime parseDate(String dateStr) {
        // Accept both "2025-12-31T18:00" and "2025-12-31T18:00:00"
        if (dateStr.length() == 16) dateStr += ":00";
        return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
