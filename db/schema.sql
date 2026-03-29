-- ============================================================
-- FULL SCHEMA — Ticket Booking Application
-- Includes organizer / venue partner support
-- ============================================================

CREATE TABLE users (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(100) NOT NULL,
  email         VARCHAR(150) UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  role          VARCHAR(20)  NOT NULL DEFAULT 'user',  -- 'user' | 'organizer' | 'admin'
  created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- Organizer business profile (one row per organizer user)
-- status 'pending'  → awaiting admin approval
-- status 'approved' → can create and manage events
-- status 'rejected' → blocked with reason
CREATE TABLE organizer_profiles (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  user_id          INT NOT NULL UNIQUE,
  business_name    VARCHAR(200) NOT NULL,
  contact_phone    VARCHAR(20)  DEFAULT NULL,
  gst_number       VARCHAR(20)  DEFAULT NULL,
  address          TEXT         DEFAULT NULL,
  status           ENUM('pending','approved','rejected') NOT NULL DEFAULT 'pending',
  rejection_reason TEXT         DEFAULT NULL,
  created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  CONSTRAINT fk_organizer_profile_user
    FOREIGN KEY (user_id)
    REFERENCES users(id)
    ON DELETE CASCADE
);

CREATE TABLE events (
  id                INT AUTO_INCREMENT PRIMARY KEY,
  organizer_id      INT          DEFAULT NULL,   -- NULL = platform/admin event
  title             VARCHAR(200) NOT NULL,
  description       TEXT,
  location          VARCHAR(150),
  event_date        DATETIME     NOT NULL,
  price             FLOAT        NOT NULL,
  total_tickets     INT          NOT NULL,
  available_tickets INT          NOT NULL,
  category          ENUM('Music','Sports','Comedy','Theatre','Conference','Festival','Workshop','Other') DEFAULT 'Other',
  images            LONGTEXT     DEFAULT NULL,
  created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_event_organizer
    FOREIGN KEY (organizer_id)
    REFERENCES users(id)
    ON DELETE SET NULL
);

CREATE TABLE seats (
  id           INT AUTO_INCREMENT PRIMARY KEY,
  event_id     INT         NOT NULL,
  seat_number  VARCHAR(10) NOT NULL,
  status       ENUM('available','booked') DEFAULT 'available',

  CONSTRAINT fk_seat_event
    FOREIGN KEY (event_id)
    REFERENCES events(id)
    ON DELETE CASCADE
);

CREATE TABLE bookings (
  id                   INT AUTO_INCREMENT PRIMARY KEY,
  user_id              INT   NOT NULL,
  event_id             INT   NOT NULL,
  tickets_booked       INT   NOT NULL,
  ticket_amount        FLOAT NOT NULL,
  convenience_fee      FLOAT NOT NULL,
  gst_amount           FLOAT NOT NULL,
  total_paid           FLOAT NOT NULL,
  selected_seats       TEXT         DEFAULT NULL,
  razorpay_order_id    VARCHAR(255) DEFAULT NULL,
  razorpay_payment_id  VARCHAR(255) DEFAULT NULL,
  payment_status       ENUM('pending','paid','failed') DEFAULT 'pending',
  ticket_pdf_s3_key    VARCHAR(512) DEFAULT NULL,
  booking_date         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_booking_user
    FOREIGN KEY (user_id)
    REFERENCES users(id)
    ON DELETE CASCADE,

  CONSTRAINT fk_booking_event
    FOREIGN KEY (event_id)
    REFERENCES events(id)
    ON DELETE CASCADE
);
