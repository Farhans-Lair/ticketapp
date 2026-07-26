-- V10 — Fix ENUM vs VARCHAR schema-validation mismatches ROOT CAUSE: V1__baseline_schema.sql defined four columns as SQL

-- organizer_profiles.status (entity: OrganizerProfile.status, length = 20)
ALTER TABLE organizer_profiles
  MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'pending';

-- events.category (entity: Event.category, length = 20)
ALTER TABLE events
  MODIFY COLUMN category VARCHAR(20) NULL DEFAULT 'Other';

-- seats.status (entity: Seat.status, length = 10)
ALTER TABLE seats
  MODIFY COLUMN status VARCHAR(10) NULL DEFAULT 'available';

-- bookings.payment_status (entity: Booking.paymentStatus, length = 20)
ALTER TABLE bookings
  MODIFY COLUMN payment_status VARCHAR(20) NULL DEFAULT 'pending';
