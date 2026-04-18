package com.ticketapp.service;

import com.ticketapp.entity.Seat;
import com.ticketapp.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepo;

    private static final int SEATS_PER_ROW = 10;
    private static final String ROWS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /** Generates seats exactly as the JS generateSeats() function does. */
    public List<Seat> generateSeats(Long eventId, int totalTickets) {
        List<Seat> seats = new ArrayList<>();
        int count = 0;
        outer:
        for (int r = 0; r < ROWS.length(); r++) {
            for (int s = 1; s <= SEATS_PER_ROW; s++) {
                if (count >= totalTickets) break outer;
                seats.add(new Seat(eventId, ROWS.charAt(r) + String.valueOf(s)));
                count++;
            }
        }
        return seats;
    }

    public List<Seat> getSeatsByEvent(Long eventId) {
        return seatRepo.findByEventIdOrderBySeatNumberAsc(eventId);
    }

    /**
     * Atomically lock and mark seats as booked.
     * Must be called inside an existing transaction.
     * Throws if any seat is no longer available (concurrent purchase protection).
     */
    @Transactional
    public void releaseSeats(Long eventId, List<String> seatNumbers) {
        if (seatNumbers == null || seatNumbers.isEmpty()) return;
        seatRepo.markSeatsAvailable(eventId, seatNumbers);
    }

    @Transactional
    public void bookSeats(Long eventId, List<String> seatNumbers) {
        List<Seat> available = seatRepo.findByEventIdAndSeatNumberInAndStatus(
                eventId, seatNumbers, "available");

        if (available.size() != seatNumbers.size()) {
            throw new RuntimeException(
                "One or more selected seats are no longer available. Please select different seats.");
        }

        seatRepo.markSeatsBooked(eventId, seatNumbers);
    }
}
