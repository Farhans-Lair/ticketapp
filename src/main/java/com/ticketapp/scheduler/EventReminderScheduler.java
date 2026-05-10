package com.ticketapp.scheduler;

import com.ticketapp.entity.Booking;
import com.ticketapp.entity.Event;
import com.ticketapp.entity.User;
import com.ticketapp.repository.BookingRepository;
import com.ticketapp.repository.UserRepository;
import com.ticketapp.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EventReminderScheduler — Feature 12.
 *
 * Runs every day at 09:00 server time and sends a reminder email to every
 * user with a paid, active booking for an event occurring in the next
 * 23–25 hours.
 *
 * The 2-hour window (rather than a point in time) ensures the email still
 * fires even if the scheduler is delayed by up to an hour, and prevents
 * a second send if the job restarts within the same run window.
 *
 * @EnableScheduling is already on TicketAppApplication — no extra config needed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventReminderScheduler {

    private final BookingRepository bookingRepo;
    private final UserRepository    userRepo;
    private final EmailService      emailService;

    @Scheduled(cron = "0 0 9 * * *")   // 09:00 every day
    @Transactional
    public void sendEventReminders() {
        LocalDateTime now  = LocalDateTime.now();
        LocalDateTime from = now.plusHours(23);
        LocalDateTime to   = now.plusHours(25);

        List<Booking> bookings = bookingRepo.findBookingsForReminder(from, to);
        if (bookings.isEmpty()) {
            log.debug("EventReminderScheduler: no bookings due for reminder in window {}-{}", from, to);
            return;
        }

        log.info("EventReminderScheduler: sending {} reminder email(s) for window {}-{}", bookings.size(), from, to);

        for (Booking booking : bookings) {
            try {
                Event event = booking.getEvent();
                if (event == null) {
                    log.warn("Booking {} has null event — skipping reminder", booking.getId());
                    continue;
                }

                User user = userRepo.findById(booking.getUserId()).orElse(null);
                if (user == null) {
                    log.warn("User {} not found for booking {} — skipping reminder", booking.getUserId(), booking.getId());
                    continue;
                }

                emailService.sendReminderEmail(user, booking, event);

                // Stamp the booking so this reminder is never sent twice
                booking.setReminderSentAt(LocalDateTime.now());
                bookingRepo.save(booking);

                log.info("Reminder sent: bookingId={} userId={} eventId={}",
                        booking.getId(), booking.getUserId(), booking.getEventId());

            } catch (Exception ex) {
                // Non-fatal — log and continue with remaining bookings
                log.error("Failed to send reminder for bookingId={}: {}", booking.getId(), ex.getMessage());
            }
        }
    }
}
