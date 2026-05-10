package com.ticketapp.service;

import com.ticketapp.entity.Event;
import com.ticketapp.entity.Seat;
import com.ticketapp.entity.User;
import com.ticketapp.repository.EventRepository;
import com.ticketapp.repository.SeatRepository;
import com.ticketapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepo;
    private final SeatRepository  seatRepo;
    private final SeatService     seatService;
    private final UserRepository  userRepo;
    private final EmailService    emailService;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public Event createEvent(String title, String description, String location,
                             String city,
                             String eventDateStr, Double price, Integer totalTickets,
                             String category, String imagesJson, Long organizerId) {
        Event event = new Event();
        event.setTitle(title);
        event.setDescription(description);
        event.setLocation(location);
        event.setCity(city != null ? city.trim() : null);
        event.setEventDate(parseDate(eventDateStr));
        event.setPrice(price != null ? price : 0.0);
        event.setTotalTickets(totalTickets);
        event.setAvailableTickets(totalTickets);
        event.setCategory(category != null ? category : "Other");
        event.setImages(imagesJson);
        event.setOrganizerId(organizerId);

        // Feature 13: admin events are immediately published;
        // organizer events start as drafts awaiting review.
        event.setEventStatus(organizerId == null ? "published" : "draft");

        event = eventRepo.save(event);

        List<Seat> seats = seatService.generateSeats(event.getId(), totalTickets);
        seatRepo.saveAll(seats);

        return event;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public Optional<Event> findById(Long id) {
        return eventRepo.findById(id);
    }

    /** Public listing — only published events. */
    public List<Event> getAllEvents(String category) {
        if (category != null && !category.isBlank()) {
            return eventRepo.findByCategoryAndEventStatusOrderByEventDateAsc(category, "published");
        }
        return eventRepo.findByEventStatusOrderByEventDateAsc("published");
    }

    public List<Event> getEventsByOrganizer(Long organizerId) {
        return eventRepo.findByOrganizerIdOrderByEventDateAsc(organizerId);
    }

    // ── Feature 11: Featured & Trending ───────────────────────────────────────

    public List<Event> getFeaturedEvents() {
        return eventRepo.findActiveFeaturedEvents(LocalDateTime.now());
    }

    /** Top 6 events by bookings in the last 7 days. */
    public List<Event> getTrendingEvents() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<Event> trending = eventRepo.findTrendingEvents(since);
        return trending.size() > 6 ? trending.subList(0, 6) : trending;
    }

    @Transactional
    public Event featureEvent(Long id, LocalDateTime featuredUntil) {
        Event event = eventRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found."));
        event.setIsFeatured(true);
        event.setFeaturedUntil(featuredUntil);   // null = permanently featured
        return eventRepo.save(event);
    }

    @Transactional
    public Event unfeatureEvent(Long id) {
        Event event = eventRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found."));
        event.setIsFeatured(false);
        event.setFeaturedUntil(null);
        return eventRepo.save(event);
    }

    // ── Feature 13: Moderation ────────────────────────────────────────────────

    public List<Event> getPendingEvents() {
        return eventRepo.findByEventStatusOrderByCreatedAtDesc("pending_review");
    }

    /**
     * Organizer submits a draft event for admin review.
     * Only transitions draft → pending_review; all other statuses are rejected.
     */
    @Transactional
    public Event submitForReview(Long eventId, Long organizerId) {
        Event event = eventRepo.findByIdAndOrganizerId(eventId, organizerId)
                .orElseThrow(() -> new RuntimeException("Event not found or you do not own this event."));
        if (!"draft".equals(event.getEventStatus()) && !"rejected".equals(event.getEventStatus())) {
            throw new RuntimeException("Only draft or rejected events can be submitted for review.");
        }
        event.setEventStatus("pending_review");
        event.setEventRejectionReason(null);
        return eventRepo.save(event);
    }

    @Transactional
    public Event approveEvent(Long eventId) {
        Event event = eventRepo.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found."));
        event.setEventStatus("published");
        event.setEventRejectionReason(null);
        Event saved = eventRepo.save(event);

        // Notify organizer
        if (saved.getOrganizerId() != null) {
            userRepo.findById(saved.getOrganizerId()).ifPresent(organizer ->
                emailService.sendEventApprovedEmail(organizer, saved));
        }
        return saved;
    }

    @Transactional
    public Event rejectEvent(Long eventId, String reason) {
        Event event = eventRepo.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found."));
        event.setEventStatus("rejected");
        event.setEventRejectionReason(reason);
        Event saved = eventRepo.save(event);

        // Notify organizer
        if (saved.getOrganizerId() != null) {
            userRepo.findById(saved.getOrganizerId()).ifPresent(organizer ->
                emailService.sendEventRejectedEmail(organizer, saved, reason));
        }
        return saved;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public Event updateEvent(Long id, String title, String description, String location,
                             String city,
                             String eventDateStr, Double price, Integer totalTickets,
                             String category, String imagesJson, Long organizerId) {
        Event event;
        if (organizerId != null) {
            event = eventRepo.findByIdAndOrganizerId(id, organizerId).orElse(null);
        } else {
            event = eventRepo.findById(id).orElse(null);
        }
        if (event == null) return null;

        int soldTickets = event.getTotalTickets() - event.getAvailableTickets();
        if (totalTickets != null && totalTickets < soldTickets) {
            throw new RuntimeException(
                "Cannot reduce total tickets below sold count (" + soldTickets + ").");
        }

        if (title        != null) event.setTitle(title);
        if (description  != null) event.setDescription(description);
        if (location     != null) event.setLocation(location);
        if (city         != null) event.setCity(city.trim());
        if (eventDateStr != null) event.setEventDate(parseDate(eventDateStr));
        if (price        != null) event.setPrice(price);
        if (category     != null) event.setCategory(category);
        if (imagesJson   != null) event.setImages(imagesJson);

        if (totalTickets != null) {
            int diff = totalTickets - event.getTotalTickets();
            event.setAvailableTickets(event.getAvailableTickets() + diff);
            event.setTotalTickets(totalTickets);

            if (diff > 0) {
                long existingCount = seatRepo.countByEventId(id);
                List<Seat> allSeats = seatService.generateSeats(id, totalTickets);
                List<Seat> newSeats = allSeats.stream().skip(existingCount).toList();
                if (!newSeats.isEmpty()) seatRepo.saveAll(newSeats);
            }
        }

        return eventRepo.save(event);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public boolean deleteEvent(Long id, Long organizerId) {
        if (organizerId != null) {
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
        if (dateStr.length() == 16) dateStr += ":00";
        return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
