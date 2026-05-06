package com.ticketapp.service;

import com.ticketapp.entity.Event;
import com.ticketapp.entity.User;
import com.ticketapp.entity.Wishlist;
import com.ticketapp.repository.EventRepository;
import com.ticketapp.repository.UserRepository;
import com.ticketapp.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WishlistService {

    private final WishlistRepository wishlistRepo;
    private final EventRepository    eventRepo;
    private final UserRepository     userRepo;
    private final EmailService       emailService;

    // ── Save / unsave ─────────────────────────────────────────────────────────

    @Transactional
    public Wishlist save(Long userId, Long eventId, boolean notifyOnAvailability) {
        eventRepo.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        Optional<Wishlist> existing = wishlistRepo.findByUserIdAndEventId(userId, eventId);
        if (existing.isPresent()) {
            Wishlist w = existing.get();
            w.setNotifyOnAvailability(notifyOnAvailability);
            return wishlistRepo.save(w);
        }

        Wishlist w = new Wishlist();
        w.setUserId(userId);
        w.setEventId(eventId);
        w.setNotifyOnAvailability(notifyOnAvailability);
        Wishlist saved = wishlistRepo.save(w);
        log.info("Wishlist saved: userId={} eventId={} notify={}", userId, eventId, notifyOnAvailability);
        return saved;
    }

    @Transactional
    public void remove(Long userId, Long eventId) {
        wishlistRepo.findByUserIdAndEventId(userId, eventId)
                .ifPresent(wishlistRepo::delete);
        log.info("Wishlist removed: userId={} eventId={}", userId, eventId);
    }

    public List<Wishlist> getForUser(Long userId) {
        return wishlistRepo.findByUserIdOrderBySavedAtDesc(userId);
    }

    // ── Notify subscribers when capacity is restored ──────────────────────────

    /**
     * Called by CancellationService after availableTickets is incremented.
     * Emails all users who subscribed to notifications for this event.
     * Does NOT limit to one notification — each subscriber gets one email.
     */
    @Transactional
    public void notifyAvailabilitySubscribers(Long eventId) {
        List<Wishlist> subscribers = wishlistRepo.findNotifySubscribers(eventId);
        if (subscribers.isEmpty()) return;

        Event event = eventRepo.findById(eventId).orElse(null);
        if (event == null) return;

        for (Wishlist w : subscribers) {
            try {
                User user = userRepo.findById(w.getUserId()).orElse(null);
                if (user == null) continue;

                String subject = "Tickets now available: " + event.getTitle();
                String body = String.format(
                    "Hi %s,\n\n" +
                    "Good news! A ticket for \"%s\" on %s has just opened up.\n\n" +
                    "Book your seat before it's gone again:\n" +
                    "  https://yourapp.com/events/%d\n\n" +
                    "Hurry — seats are limited.\n\n" +
                    "Regards,\nTicketApp Team",
                    user.getName(),
                    event.getTitle(),
                    event.getEventDate().toLocalDate(),
                    event.getId()
                );
                emailService.sendSimple(user.getEmail(), subject, body);

                // Stop notifying this subscriber until they re-subscribe
                w.setNotifyOnAvailability(false);
                wishlistRepo.save(w);

                log.info("Availability notification sent: userId={} eventId={}", w.getUserId(), eventId);
            } catch (Exception e) {
                log.error("Failed to notify userId={} eventId={}: {}", w.getUserId(), eventId, e.getMessage());
            }
        }
    }
}
