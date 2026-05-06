package com.ticketapp.service;

import com.ticketapp.entity.Event;
import com.ticketapp.entity.User;
import com.ticketapp.entity.WaitlistEntry;
import com.ticketapp.repository.EventRepository;
import com.ticketapp.repository.UserRepository;
import com.ticketapp.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaitlistService {

    private final WaitlistRepository waitlistRepo;
    private final EventRepository    eventRepo;
    private final UserRepository     userRepo;
    private final EmailService       emailService;

    // ── Join / leave ──────────────────────────────────────────────────────────

    @Transactional
    public WaitlistEntry join(Long userId, Long eventId, int ticketsWanted) {
        eventRepo.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        if (waitlistRepo.existsByUserIdAndEventId(userId, eventId))
            throw new RuntimeException("You are already on the waitlist for this event.");

        WaitlistEntry entry = new WaitlistEntry();
        entry.setUserId(userId);
        entry.setEventId(eventId);
        entry.setTicketsWanted(ticketsWanted);
        entry.setStatus("waiting");
        WaitlistEntry saved = waitlistRepo.save(entry);

        log.info("Waitlist joined: userId={} eventId={} ticketsWanted={}",
                userId, eventId, ticketsWanted);
        return saved;
    }

    @Transactional
    public void leave(Long userId, Long eventId) {
        waitlistRepo.findByUserIdAndEventId(userId, eventId)
                .ifPresent(waitlistRepo::delete);
        log.info("Waitlist left: userId={} eventId={}", userId, eventId);
    }

    public List<WaitlistEntry> getForUser(Long userId) {
        return waitlistRepo.findByUserIdOrderByJoinedAtDesc(userId);
    }

    public Map<String, Object> getQueueStats(Long eventId) {
        long count = waitlistRepo.countByEventIdAndStatus(eventId, "waiting");
        return Map.of("waitlist_count", count, "event_id", eventId);
    }

    // ── Notify on cancellation ────────────────────────────────────────────────

    /**
     * Called by CancellationService after availableTickets is restored.
     * Emails the first eligible waiter (one per cancellation) and
     * marks their entry as 'notified'.
     *
     * @param eventId       the event that just got a free seat
     * @param freedSeats    how many seats became available (from the cancellation)
     */
    @Transactional
    public void notifyNextWaiter(Long eventId, int freedSeats) {
        List<WaitlistEntry> eligible = waitlistRepo.findEligibleWaiters(eventId, freedSeats);
        if (eligible.isEmpty()) return;

        Event event = eventRepo.findById(eventId).orElse(null);
        if (event == null) return;

        // Notify only the first in line (FIFO)
        WaitlistEntry first = eligible.get(0);

        try {
            User user = userRepo.findById(first.getUserId()).orElse(null);
            if (user == null) return;

            String subject = "Your waitlist spot is ready: " + event.getTitle();
            String body = String.format(
                "Hi %s,\n\n" +
                "Great news! %d ticket(s) for \"%s\" on %s just became available.\n\n" +
                "You were first in line on our waitlist. Book now before someone else does:\n" +
                "  https://yourapp.com/events/%d\n\n" +
                "Note: This seat is not reserved for you. First come, first served.\n\n" +
                "Regards,\nTicketApp Team",
                user.getName(),
                freedSeats,
                event.getTitle(),
                event.getEventDate().toLocalDate(),
                event.getId()
            );

            emailService.sendSimple(user.getEmail(), subject, body);

            first.setStatus("notified");
            first.setNotifiedAt(LocalDateTime.now());
            waitlistRepo.save(first);

            log.info("Waitlist notification sent: userId={} eventId={}", first.getUserId(), eventId);

        } catch (Exception e) {
            log.error("Failed to notify waitlister userId={} eventId={}: {}",
                    first.getUserId(), eventId, e.getMessage());
        }
    }
}
